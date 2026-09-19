package com.smartcane.backend.controller;

import com.smartcane.backend.config.onenet.OneNetMqttProperties;
import com.smartcane.backend.config.onenet.OneNetTokenUtil;
import com.smartcane.backend.entity.vo.Result;
import com.smartcane.backend.service.auth.DataScopeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Tag(name = "OneNet 云平台管理")
@RestController
@RequestMapping("/api/onenet")
public class OneNetController {

    @Autowired
    private OneNetMqttProperties properties;

    @Autowired
    private DataScopeService dataScopeService;

    @Operation(summary = "查看 OneNet 配置信息")
    @GetMapping("/config")
    public Result<Map<String, String>> getConfig() {
        // 配置里含 accessKey，属于密钥信息
        dataScopeService.assertAdmin();
        Map<String, String> config = new HashMap<>();
        config.put("mqttHost", properties.getMqtt().getHost());
        config.put("mqttProductId", properties.getMqtt().getProductId());
        config.put("mqttSubscribeTopic", properties.getMqtt().getSubscribeTopic());
        config.put("mqttQos", String.valueOf(properties.getMqtt().getQos()));
        config.put("apiEndpoints", properties.getApi().getEffectiveEndpoints().toString());
        config.put("apiProductId", properties.getApi().getProductId());
        config.put("apiDeviceNames", String.join(",", properties.getApi().getDeviceNames()));
        config.put("apiFetchIntervalMs", properties.getApi().getFetchIntervalMs() + "毫秒");
        config.put("dynamicClientId", "smartcane_backend_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        return Result.success(config);
    }

    @Operation(summary = "生成 OneNet Token (用于调试)")
    @GetMapping("/token")
    public Result<Map<String, String>> generateToken() throws UnsupportedEncodingException {
        // 生成的 token 可直接接进云平台，只允许管理员获取
        dataScopeService.assertAdmin();
        Map<String, String> result = new HashMap<>();
        String token = OneNetTokenUtil.generateToken(
                properties.getMqtt().getProductId(),
                properties.getMqtt().getAccessKey()
        );
        result.put("username", properties.getMqtt().getProductId());
        result.put("password/token", token);
        result.put("mqttHost", properties.getMqtt().getHost());
        result.put("subscribeTopic", properties.getMqtt().getSubscribeTopic());
        return Result.success(result);
    }
}