package com.smartcane.backend.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartcane.backend.entity.po.CrutchDevice;
import com.smartcane.backend.entity.po.CrutchSensorData;
import com.smartcane.backend.mapper.CrutchDeviceMapper;
import com.smartcane.backend.mapper.CrutchSensorDataMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class OneNetDataProcessor {

    private static final Logger log = LoggerFactory.getLogger(OneNetDataProcessor.class);

    @Autowired
    private CrutchDeviceMapper deviceMapper;

    @Autowired
    private CrutchSensorDataMapper sensorDataMapper;

    /**
     * 处理 MQTT 推送的传感器数据
     */
    public void process(String topic, String payload) {
        log.debug("[MQTT-Step1] 开始处理消息 - Topic: {}", topic);

        String deviceSn = extractDeviceSn(topic);
        if (deviceSn == null) {
            log.warn("[MQTT-Step1-失败] 无法从 Topic 解析设备序列号: {}", topic);
            return;
        }
        log.debug("[MQTT-Step2] 解析设备序列号: {}", deviceSn);

        ensureDeviceExists(deviceSn);

        JSONObject data = parsePayload(payload);
        if (data == null) {
            log.warn("[MQTT-Step3-失败] 无法解析传感器数据，原始消息: {}", payload);
            return;
        }
        log.debug("[MQTT-Step3] 解析出传感器数据: {}", data.toJSONString());

        saveSensorData(deviceSn, data);
    }

    /**
     * 处理 HTTP API 拉取的传感器数据（供 OneNetApiService 调用）
     */
    public void processApiData(String deviceSn, JSONObject data) {
        log.debug("[API-Step] 处理API拉取数据 - 设备: {}", deviceSn);
        ensureDeviceExists(deviceSn);
        saveSensorData(deviceSn, data);
    }

    private void saveSensorData(String deviceSn, JSONObject data) {
        CrutchSensorData sensorData = buildSensorData(deviceSn, data);
        if (sensorData == null) {
            log.warn("[保存-失败] 未能提取任何有效字段，跳过保存");
            return;
        }

        try {
            sensorDataMapper.insert(sensorData);
            log.debug("[保存-成功] 传感器数据已写入数据库 - ID: {}, 设备: {}, 心率: {}, 血氧: {}, 位置: {}/{}, 摔倒: {}",
                    sensorData.getId(),
                    deviceSn,
                    sensorData.getHeartRate(),
                    sensorData.getBloodOxygen(),
                    sensorData.getLat(),
                    sensorData.getLon(),
                    sensorData.getFallStatus());
        } catch (Exception e) {
            log.error("[保存-失败] 写入数据库异常: {}", e.getMessage(), e);
        }
    }

    private String extractDeviceSn(String topic) {
        if (topic == null) return null;
        String[] parts = topic.split("/");
        if (parts.length >= 3) {
            return parts[2];
        }
        return null;
    }

    private JSONObject parsePayload(String payload) {
        try {
            JSONObject json = JSON.parseObject(payload);
            if (json == null) return null;

            // 策略1: msg.params (物模型属性上报)
            if (json.containsKey("msg") && json.get("msg") instanceof JSONObject) {
                JSONObject msg = json.getJSONObject("msg");
                if (msg.containsKey("params") && msg.get("params") instanceof JSONObject) {
                    JSONObject params = msg.getJSONObject("params");
                    if (!params.isEmpty()) {
                        log.debug("匹配策略1: msg.params 物模型格式");
                        return mergeReportTime(params, msg);
                    }
                }
                if (msg.containsKey("value")) {
                    Object val = msg.get("value");
                    if (val instanceof String) {
                        try {
                            JSONObject innerJson = JSON.parseObject((String) val);
                            if (innerJson != null && !innerJson.isEmpty()) {
                                log.debug("匹配策略2: msg.value JSON字符串格式");
                                return mergeReportTime(innerJson, msg);
                            }
                        } catch (Exception ignored) {}
                    }
                    if (val instanceof JSONObject) {
                        return mergeReportTime((JSONObject) val, msg);
                    }
                }
                if (hasSensorField(msg)) {
                    return mergeReportTime(msg, msg);
                }
            }

            // 策略3: params 顶层
            if (json.containsKey("params") && json.get("params") instanceof JSONObject) {
                JSONObject params = json.getJSONObject("params");
                if (!params.isEmpty()) {
                    return mergeReportTime(params, json);
                }
            }

            // 策略4: data 顶层
            if (json.containsKey("data") && json.get("data") instanceof JSONObject) {
                JSONObject data = json.getJSONObject("data");
                if (!data.isEmpty()) {
                    return mergeReportTime(data, json);
                }
            }

            // 策略5: 根对象直接包含字段
            if (hasSensorField(json)) {
                return json;
            }

            log.warn("无法匹配任何消息格式，JSON 顶层 keys: {}", json.keySet());
            return null;
        } catch (Exception e) {
            log.warn("JSON 解析异常: {}", e.getMessage());
            return null;
        }
    }

    private boolean hasSensorField(JSONObject json) {
        String[] keys = {
                "心率", "血氧", "纬度", "经度", "倾角",
                "heartRate", "heart_rate", "hr",
                "bloodOxygen", "blood_oxygen", "spo2", "SpO2",
                "lat", "latitude",
                "lon", "lng", "longitude",
                "fallStatus", "fall_status", "fall", "isFall"
        };
        for (String key : keys) {
            if (json.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    private JSONObject mergeReportTime(JSONObject data, JSONObject source) {
        if (data == null) return null;
        if (source != null) {
            if (source.containsKey("at") && !data.containsKey("at")) {
                data.put("at", source.get("at"));
            }
            if (source.containsKey("timestamp") && !data.containsKey("at")) {
                data.put("at", source.get("timestamp"));
            }
        }
        return data;
    }

    private CrutchSensorData buildSensorData(String deviceSn, JSONObject data) {
        Integer heartRate = getInteger(data, "心率", "heartRate", "heart_rate", "hr", "HR");
        Integer bloodOxygen = getInteger(data, "血氧", "bloodOxygen", "blood_oxygen", "spo2", "SpO2");
        Double lat = getDouble(data, "纬度", "lat", "latitude");
        Double lon = getDouble(data, "经度", "lon", "lng", "longitude");
        Integer fallStatus = parseFallStatus(data);

        if (heartRate == null && bloodOxygen == null && lat == null && lon == null && fallStatus == null) {
            return null;
        }

        CrutchSensorData sensorData = new CrutchSensorData();
        sensorData.setDeviceSn(deviceSn);
        sensorData.setHeartRate(heartRate);
        sensorData.setBloodOxygen(bloodOxygen);
        sensorData.setLat(lat);
        sensorData.setLon(lon);
        sensorData.setFallStatus(fallStatus != null ? fallStatus : 0);

        LocalDateTime reportTime = extractReportTime(data);
        sensorData.setReportTime(reportTime);

        return sensorData;
    }

    private Integer parseFallStatus(JSONObject data) {
        if (data.containsKey("倾角")) {
            Object val = data.get("倾角");
            if (val instanceof Boolean) return ((Boolean) val) ? 1 : 0;
            if (val instanceof Number) return ((Number) val).intValue() != 0 ? 1 : 0;
            if (val instanceof String) {
                String s = ((String) val).toLowerCase().trim();
                if ("true".equals(s) || "1".equals(s) || "是".equals(s)) return 1;
                if ("false".equals(s) || "0".equals(s) || "否".equals(s)) return 0;
            }
        }
        Integer intVal = getInteger(data, "fallStatus", "fall_status", "fall", "isFall", "FallStatus");
        if (intVal != null) return intVal != 0 ? 1 : 0;
        for (String key : new String[]{"fallStatus", "fall_status", "fall", "isFall"}) {
            if (data.containsKey(key) && data.get(key) instanceof Boolean) {
                return ((Boolean) data.get(key)) ? 1 : 0;
            }
        }
        return null;
    }

    private Integer getInteger(JSONObject data, String... keys) {
        for (String key : keys) {
            if (data.containsKey(key)) {
                Object val = data.get(key);
                if (val == null) continue;
                try {
                    if (val instanceof Number) return ((Number) val).intValue();
                    return Integer.parseInt(val.toString().trim());
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private Double getDouble(JSONObject data, String... keys) {
        for (String key : keys) {
            if (data.containsKey(key)) {
                Object val = data.get(key);
                if (val == null) continue;
                try {
                    if (val instanceof Number) return ((Number) val).doubleValue();
                    return Double.parseDouble(val.toString().trim());
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private LocalDateTime extractReportTime(JSONObject data) {
        String[] timeKeys = {"at", "timestamp", "time", "reportTime", "report_time"};
        for (String key : timeKeys) {
            if (data.containsKey(key)) {
                Object at = data.get(key);
                if (at instanceof Number) {
                    long ts = ((Number) at).longValue();
                    if (ts < 10000000000L) ts *= 1000;
                    return LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault());
                }
            }
        }
        return LocalDateTime.now();
    }

    private void ensureDeviceExists(String deviceSn) {
        try {
            QueryWrapper<CrutchDevice> wrapper = new QueryWrapper<>();
            wrapper.eq("device_sn", deviceSn);
            CrutchDevice device = deviceMapper.selectOne(wrapper);
            if (device == null) {
                CrutchDevice newDevice = new CrutchDevice();
                newDevice.setDeviceSn(deviceSn);
                newDevice.setDeviceStatus(1);
                newDevice.setElderName("");
                newDevice.setElderPhone("");
                newDevice.setGuardianPhone("");
                deviceMapper.insert(newDevice);
                log.info("自动注册新设备: {}", deviceSn);
            }
        } catch (Exception e) {
            log.warn("检查/注册设备失败: {}", e.getMessage());
        }
    }
}