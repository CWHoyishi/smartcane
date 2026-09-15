package com.smartcane.backend.controller;

import com.smartcane.backend.config.onenet.OneNetMqttProperties;
import com.smartcane.backend.config.onenet.OneNetTokenUtil;
import com.smartcane.backend.entity.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Api(tags = "OneNet 云平台管理")
@RestController
@RequestMapping("/api/onenet")
public class OneNetController {

    @Autowired
    private OneNetMqttProperties properties;

    @ApiOperation("查看 OneNet 配置信息")
    @GetMapping("/config")
    public Result<Map<String, String>> getConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("mqttHost", properties.getMqtt().getHost());
        config.put("mqttProductId", properties.getMqtt().getProductId());
        config.put("mqttSubscribeTopic", properties.getMqtt().getSubscribeTopic());
        config.put("mqttQos", String.valueOf(properties.getMqtt().getQos()));
        config.put("apiEndpoints", properties.getApi().getEffectiveEndpoints().toString());
        config.put("apiProductId", properties.getApi().getProductId());
        config.put("apiDeviceNames", String.join(",", properties.getApi().getDeviceNames()));
        config.put("apiFetchInterval", properties.getApi().getFetchInterval() + "秒");
        config.put("dynamicClientId", "smartcane_backend_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        return Result.success(config);
    }

    @ApiOperation("生成 OneNet Token (用于调试)")
    @GetMapping("/token")
    public Result<Map<String, String>> generateToken() throws UnsupportedEncodingException {
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