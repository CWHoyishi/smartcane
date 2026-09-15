package com.smartcane.backend.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartcane.backend.config.onenet.OneNetMqttProperties;
import com.smartcane.backend.config.onenet.OneNetTokenUtil;
import com.smartcane.backend.entity.po.CrutchDevice;
import com.smartcane.backend.entity.po.CrutchSensorData;
import com.smartcane.backend.mapper.CrutchDeviceMapper;
import com.smartcane.backend.mapper.CrutchSensorDataMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * CMIOT 物模型 API 数据拉取服务
 *
 * 官方文档端点（iot-api.heclouds.com）：
 *   1. GET  /thingmodel/query-device-property          → 查询设备最新属性功能点数据
 *   2. GET  /thingmodel/query-device-property-history   → 查询设备属性历史功能点数据
 *   3. POST /thingmodel/query-device-property-detail    → 下发物模型属性获取命令（需设备在线）
 *
 * 物模型字段映射：
 *   HR      → 心率 (int32, 0-200)
 *   SP02    → 血氧 (int32, 0-200)
 *   Lat     → 纬度 (double, -90~90)
 *   Lon     → 经度 (double, -180~180)
 *   MPU6050 → 倾角/摔倒检测 (bool, true=1/false=0)
 */
@Service
public class OneNetApiService {

    private static final Logger log = LoggerFactory.getLogger(OneNetApiService.class);

    /** 物模型标识符（与平台物模型定义一致） */
    private static final String PROP_HEART_RATE = "HR";
    private static final String PROP_BLOOD_OXYGEN = "SP02";
    private static final String PROP_LAT = "Lat";
    private static final String PROP_LON = "Lon";
    private static final String PROP_FALL = "MPU6050";

    /** 需要查询的属性标识符列表 */
    private static final String[] PROPERTY_IDENTIFIERS = {
            PROP_HEART_RATE, PROP_BLOOD_OXYGEN, PROP_LAT, PROP_LON, PROP_FALL
    };

    @Autowired
    private OneNetMqttProperties properties;

    @Autowired
    private CrutchDeviceMapper deviceMapper;

    @Autowired
    private CrutchSensorDataMapper sensorDataMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 拉取指定设备的传感器数据并入库
     *
     * 策略：按配置的端点顺序尝试，第一个成功返回有效数据即使用。
     * 优先使用 query-device-property（最新数据），其次用 history（历史数据）。
     */
    public boolean fetchDeviceProperties(String deviceName) {
        OneNetMqttProperties.Api api = properties.getApi();
        List<OneNetMqttProperties.ApiEndpoint> endpoints = api.getEffectiveEndpoints();

        log.info("[API拉取] 开始拉取设备 {} 的数据（共 {} 个端点）", deviceName, endpoints.size());

        for (int i = 0; i < endpoints.size(); i++) {
            OneNetMqttProperties.ApiEndpoint endpoint = endpoints.get(i);
            log.info("[API拉取] [{}/{}] 尝试: {} {}", i + 1, endpoints.size(),
                    endpoint.getMethod(), endpoint.getPath());

            try {
                FetchResult result = doFetch(deviceName, endpoint, api);

                if (result.success && result.props != null && !result.props.isEmpty()) {
                    // 构建传感器数据并入库
                    ensureDeviceExists(deviceName);
                    CrutchSensorData data = buildSensorData(deviceName, result.props);
                    if (data != null) {
                        sensorDataMapper.insert(data);
                        log.info("[API拉取] 入库成功! 设备: {}, 心率: {}, 血氧: {}, 纬度: {}, 经度: {}, 摔倒: {}",
                                deviceName,
                                data.getHeartRate(), data.getBloodOxygen(),
                                data.getLat(), data.getLon(), data.getFallStatus());
                        return true;
                    } else {
                        log.warn("[API拉取] 响应解析成功但无法组装传感器数据，字段: {}", result.props.keySet());
                    }
                }

                // 失败或无数据 → 继续下一个端点
                if (!result.success) {
                    log.warn("[API拉取] 端点失败: {}", result.message);
                } else {
                    log.info("[API拉取] 端点成功但无可提取的传感器字段");
                }
            } catch (Exception e) {
                log.error("[API拉取] 端点异常: {}", e.getMessage(), e);
            }
        }

        log.error("[API拉取] 所有端点均未获取到有效传感器数据，设备: {}", deviceName);
        return false;
    }

    /**
     * 执行单次 HTTP 请求
     *
     * 所有 iot-api.heclouds.com 端点统一使用 Token 鉴权（HmacSHA256）
     *
     * 端点适配：
     *   GET  /thingmodel/query-device-property          → Query 参数
     *   GET  /thingmodel/query-device-property-history   → Query + 时间范围 + identifier
     *   POST /thingmodel/query-device-property-detail    → Body JSON
     */
    private FetchResult doFetch(String deviceName,
                                 OneNetMqttProperties.ApiEndpoint endpoint,
                                 OneNetMqttProperties.Api api) throws Exception {

        String baseUrl = endpoint.getBaseUrl();
        String path = endpoint.getPath();
        HttpMethod httpMethod = HttpMethod.valueOf(endpoint.getMethod().toUpperCase());

        // ===== 统一 Token 鉴权 =====
        String token = OneNetTokenUtil.generateApiToken(api.getProductId(), api.getAccessKey());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        headers.set("Authorization", token);

        String url;
        HttpEntity<?> entity;

        if (httpMethod == HttpMethod.POST && path.contains("property-detail")) {
            // ===== POST /thingmodel/query-device-property-detail =====
            headers.setContentType(MediaType.APPLICATION_JSON);
            url = baseUrl + path;

            JSONObject body = new JSONObject();
            body.put("product_id", api.getProductId());
            body.put("device_name", deviceName);
            body.put("params", PROPERTY_IDENTIFIERS);
            entity = new HttpEntity<>(body.toJSONString(), headers);

        } else {
            // ===== GET 请求：Query 参数 =====
            StringBuilder urlBuilder = new StringBuilder(baseUrl).append(path);
            urlBuilder.append("?product_id=").append(api.getProductId());
            urlBuilder.append("&device_name=").append(deviceName);

            // history 端点需要额外参数
            if (path.contains("property-history")) {
                long endTime = System.currentTimeMillis();
                long startTime = endTime - 86400000L; // 最近24小时（毫秒）
                urlBuilder.append("&identifier="); // 不传identifier查全部属性
                urlBuilder.append("&start_time=").append(startTime);
                urlBuilder.append("&end_time=").append(endTime);
                urlBuilder.append("&limit=10");
            }

            url = urlBuilder.toString();
            entity = new HttpEntity<>(headers);
        }

        log.info("[API拉取] {} {}", httpMethod.name(), url);

        ResponseEntity<byte[]> response = restTemplate.exchange(url, httpMethod, entity, byte[].class);
        FetchResult result = new FetchResult();
        result.statusCode = response.getStatusCode().toString();

        // 检查响应体
        byte[] bodyBytes = response.getBody();
        if (bodyBytes == null || bodyBytes.length == 0) {
            result.success = false;
            result.message = "响应体为空";
            return result;
        }

        String bodyStr = new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8);
        log.info("[API拉取] 响应({}B): {}", bodyBytes.length,
                bodyStr.length() > 600 ? bodyStr.substring(0, 600) + "...[截断]" : bodyStr);

        // 解析 JSON
        JSONObject json;
        try {
            json = JSON.parseObject(bodyStr);
        } catch (Exception e) {
            result.success = false;
            result.message = "JSON解析失败: " + bodyStr.substring(0, Math.min(bodyStr.length(), 200));
            return result;
        }

        // 检查错误码
        Integer code = json.getInteger("code");
        String msg = json.getString("msg");

        if (code != null && code != 0) {
            result.success = false;
            result.isAuthError = (code == 10001 || code == 10003 || code == 401 || code == 403);
            result.isNotFoundError = (code == 10006 || code == 404);
            result.message = "code=" + code + ", msg=" + msg;
            return result;
        }

        // 提取属性数据
        result.props = extractThingModelProperties(json, path);
        result.success = true;

        if (result.props == null || result.props.isEmpty()) {
            result.message = "响应中无传感器属性数据";
        }

        return result;
    }

    /**
     * 从 thingmodel API 响应中提取属性值
     *
     * 预期的响应格式（根据官方文档）：
     *   query-device-property 返回: { "code":0, "data":{ "HR":{ "value":75,... }, "SP02":{... }, ... } }
     *   或:                           { "code":0, "data":{ "properties":{ "HR":{ "value":75 }, ... } } }
     *   property-history 返回:       { "code":0, "data":{ ...历史记录数组... } }
     */
    private JSONObject extractThingModelProperties(JSONObject json, String path) {
        JSONObject flat = new JSONObject();

        // ===== 安全获取 data 字段（兼容对象和数组两种格式）=====
        // CMIOT API 的 data 可能是：
        //   1. 对象: { "data": { "HR": { "value": 75 }, ... } }
        //   2. 数组: { "data": [ { "HR": { "value": 75 }, ... } ] } 或空数组 []
        Object dataRaw = json.get("data");
        if (dataRaw == null) {
            log.warn("[解析] 响应中无 data 字段");
            return null;
        }

        JSONObject dataObj;
        if (dataRaw instanceof JSONArray) {
            JSONArray arr = (JSONArray) dataRaw;
            log.info("[解析] data 是数组格式，共 {} 条", arr.size());
            if (arr.isEmpty()) {
                log.info("[解析] data 数组为空，无设备属性数据");
                return null;
            }

            // ===== CMIOT thingmodel 标准格式：数组每条是一个属性 { identifier, value, time } =====
            // 遍历所有元素，按 identifier 聚合为 key-value 映射
            boolean foundProps = false;
            for (int i = 0; i < arr.size(); i++) {
                Object item = arr.get(i);
                if (!(item instanceof JSONObject)) continue;
                JSONObject propItem = (JSONObject) item;

                String identifier = propItem.getString("identifier");
                Object value = propItem.get("value");
                Long time = propItem.getLong("time");

                if (identifier != null && value != null) {
                    flat.put(identifier, value);
                    if (time != null) {
                        flat.put("_" + identifier + "_time", time);
                    }
                    log.info("[解析] [{}] identifier={}, value={}, type={}",
                            i, identifier, value, propItem.getString("data_type"));
                    foundProps = true;
                }
            }

            if (foundProps) {
                log.info("[解析] 从数组提取 {} 个属性: {}", flat.size(), flat.keySet());
                return flat;
            }

            // 如果没有 identifier/value 结构，回退到取第一条
            Object first = arr.get(0);
            if (first instanceof JSONObject) {
                dataObj = (JSONObject) first;
                log.info("[解析] 无 identifier 结构，取第1条作为属性源");
            } else {
                log.warn("[解析] 数组首元素非对象类型: {}", first.getClass());
                return null;
            }
        } else if (dataRaw instanceof JSONObject) {
            dataObj = (JSONObject) dataRaw;
        } else {
            log.warn("[解析] data 类型异常: {}", dataRaw.getClass());
            return null;
        }

        log.info("[解析] data keys: {}, 路径: {}", dataObj.keySet(), path);

        // ===== 格式1: data 直接包含属性对象 { "HR": { "value": 75 }, ... } =====
        // 这是最常见的 thingmodel 响应格式
        for (String identifier : PROPERTY_IDENTIFIERS) {
            Object propObj = dataObj.get(identifier);
            if (propObj instanceof JSONObject) {
                JSONObject prop = (JSONObject) propObj;
                if (prop.containsKey("value")) {
                    flat.put(identifier, prop.get("value"));
                    if (prop.containsKey("updateTime")) {
                        flat.put("_" + identifier + "_time", prop.get("updateTime"));
                    }
                    log.debug("[解析] 提取 {}: value={}", identifier, prop.get("value"));
                }
            } else if (propObj != null && !(propObj instanceof JSONArray)) {
                // 扁平格式: { "HR": 75 }
                flat.put(identifier, propObj);
                log.debug("[解析] 提取 {} (扁平): {}", identifier, propObj);
            }
        }

        if (!flat.isEmpty()) {
            log.info("[解析] 成功提取 {} 个属性: {}", flat.size(), flat.keySet());
            return flat;
        }

        // ===== 格式2: data.properties 包含属性 =====
        JSONObject propsContainer = dataObj.getJSONObject("properties");
        if (propsContainer != null) {
            log.info("[解析] 检测到 data.properties 结构, keys: {}", propsContainer.keySet());
            for (String identifier : PROPERTY_IDENTIFIERS) {
                Object propObj = propsContainer.get(identifier);
                if (propObj instanceof JSONObject) {
                    JSONObject prop = (JSONObject) propObj;
                    if (prop.containsKey("value")) {
                        flat.put(identifier, prop.get("value"));
                    }
                } else if (propObj != null && !(propObj instanceof JSONArray)) {
                    flat.put(identifier, propObj);
                }
            }
            if (!flat.isEmpty()) {
                log.info("[解析] 从 properties 提取 {} 个属性: {}", flat.size(), flat.keySet());
                return flat;
            }
        }

        // ===== 格式3: property-history 返回的历史记录列表 =====
        if (path.contains("property-history")) {
            Object listObj = dataObj.get("list");
            if (listObj instanceof List) {
                List<?> list = (List<?>) listObj;
                log.info("[解析] history 返回 {} 条记录", list.size());
                // 取最后一条（最新的）记录
                for (int i = list.size() - 1; i >= 0; i--) {
                    Object item = list.get(i);
                    if (item instanceof JSONObject) {
                        JSONObject record = (JSONObject) item;
                        // 尝试 identifier/value 结构
                        String id = record.getString("identifier");
                        Object val = record.get("value");
                        if (id != null && val != null) {
                            flat.put(id, val);
                        }
                        // 尝试 params 结构
                        Object params = record.get("params");
                        if (params instanceof JSONObject) {
                            flattenLeafNodes((JSONObject) params, flat);
                        }
                    }
                }
                if (!flat.isEmpty()) {
                    log.info("[解析] 从 history 提取 {} 个属性: {}", flat.size(), flat.keySet());
                    return flat;
                }
            }
        }

        // ===== 兜底：扁平化提取所有叶子节点 =====
        log.info("[解析] 兜底：扁平化提取 data 叶子节点...");
        flattenLeafNodes(dataObj, flat);

        if (flat.isEmpty()) {
            log.warn("[解析] 未能提取任何有效属性。data 内容预览: {}",
                    dataObj.toJSONString().length() > 400 ?
                            dataObj.toJSONString().substring(0, 400) + "..." :
                            dataObj.toJSONString());
        } else {
            log.info("[解析] 兜底提取到 {} 个字段: {}", flat.size(), flat.keySet());
        }

        return flat.isEmpty() ? null : flat;
    }

    /**
     * 将 JSON 对象中的叶子节点提取到目标 Map（跳过嵌套对象和数组）
     */
    private void flattenLeafNodes(JSONObject source, JSONObject target) {
        if (source == null) return;
        for (String key : source.keySet()) {
            Object val = source.get(key);
            if (val == null) continue;
            if (!(val instanceof JSONObject) && !(val instanceof JSONArray)) {
                target.put(key, val);
            }
        }
    }

    /**
     * 根据提取到的属性构建 CrutchSensorData 实体
     *
     * 字段映射（物模型标识符 → 数据库字段）：
     *   HR      → heartRate  (Integer)
     *   SP02    → bloodOxygen (Integer)
     *   Lat     → lat        (Double)
     *   Lon     → lon        (Double)
     *   MPU6050 → fallStatus (Integer, bool→0/1)
     */
    private CrutchSensorData buildSensorData(String deviceSn, JSONObject props) {
        Integer heartRate = safeGetInt(props, PROP_HEART_RATE);
        Integer bloodOxygen = safeGetInt(props, PROP_BLOOD_OXYGEN);
        Double lat = safeGetDouble(props, PROP_LAT);
        Double lon = safeGetDouble(props, PROP_LON);
        Integer fallStatus = parseBoolToInt(props, PROP_FALL);

        // 至少有一个传感器字段有值才构建实体
        if (heartRate == null && bloodOxygen == null && lat == null && lon == null && fallStatus == null) {
            return null;
        }

        CrutchSensorData data = new CrutchSensorData();
        data.setDeviceSn(deviceSn);
        data.setHeartRate(heartRate);
        data.setBloodOxygen(bloodOxygen);
        data.setLat(lat);
        data.setLon(lon);
        data.setFallStatus(fallStatus != null ? fallStatus : 0);
        data.setReportTime(extractTimestamp(props));

        return data;
    }

    private LocalDateTime extractTimestamp(JSONObject props) {
        // 从各属性的 updateTime 中取最新的
        long latestTs = 0;
        for (String key : PROPERTY_IDENTIFIERS) {
            Object tsObj = props.get("_" + key + "_time");
            if (tsObj instanceof Number) {
                long ts = ((Number) tsObj).longValue();
                if (ts > latestTs) latestTs = ts;
            }
        }
        if (latestTs > 0) {
            // 判断是秒还是毫秒
            if (latestTs < 10000000000L) latestTs *= 1000;
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(latestTs), ZoneId.systemDefault());
        }
        return LocalDateTime.now();
    }

    // ==================== 工具方法 ====================

    private Integer safeGetInt(JSONObject json, String key) {
        Object val = json.get(key);
        if (val == null) return null;
        try {
            if (val instanceof Number) return ((Number) val).intValue();
            if (val instanceof Boolean) return ((Boolean) val) ? 1 : 0;
            String s = val.toString().trim();
            if (s.isEmpty()) return null;
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double safeGetDouble(JSONObject json, String key) {
        Object val = json.get(key);
        if (val == null) return null;
        try {
            if (val instanceof Number) return ((Number) val).doubleValue();
            String s = val.toString().trim();
            if (s.isEmpty()) return null;
            return Double.parseDouble(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 解析布尔类型属性为整数（MPU6050 是 bool 类型）
     * true/"true"/"1"/非零数字 → 1
     * false/"false"/"0"/零 → 0
     */
    private Integer parseBoolToInt(JSONObject json, String key) {
        Object val = json.get(key);
        if (val == null) return null;
        if (val instanceof Boolean) return ((Boolean) val) ? 1 : 0;
        if (val instanceof Number) {
            int n = ((Number) val).intValue();
            return n != 0 ? 1 : 0;
        }
        if (val instanceof String) {
            String s = ((String) val).trim().toLowerCase();
            switch (s) {
                case "true":
                case "1":
                    return 1;
                case "false":
                case "0":
                    return 0;
                default:
                    try {
                        int n = Integer.parseInt(s);
                        return n != 0 ? 1 : 0;
                    } catch (Exception ignored) {
                        return null;
                    }
            }
        }
        return null;
    }

    private void ensureDeviceExists(String deviceSn) {
        try {
            QueryWrapper<CrutchDevice> wrapper = new QueryWrapper<>();
            wrapper.eq("device_sn", deviceSn);
            if (deviceMapper.selectOne(wrapper) == null) {
                CrutchDevice device = new CrutchDevice();
                device.setDeviceSn(deviceSn);
                device.setDeviceStatus(1);
                deviceMapper.insert(device);
                log.info("[API拉取] 自动注册新设备: {}", deviceSn);
            }
        } catch (Exception e) {
            log.warn("[API拉取] 设备注册异常: {}", e.getMessage());
        }
    }

    /** 单次请求结果 */
    private static class FetchResult {
        boolean success;
        String statusCode;
        String message;
        boolean isAuthError;
        boolean isNotFoundError;
        JSONObject props;  // 提取到的属性键值对
    }
}
