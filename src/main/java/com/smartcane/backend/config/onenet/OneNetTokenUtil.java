package com.smartcane.backend.config.onenet;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * OneNet 鉴权工具类
 *
 * 两种鉴权方式：
 * 1. generateToken()   — MQTT 连接鉴权（version=2018-10-31, method=sha1）
 * 2. generateApiToken()— HTTP API 鉴权（version=2022-05-01, method=sha256）
 *
 * API Token 格式（官方文档）：
 *   authorization: version=2022-05-01&res=products/{productId}&et={expireTime}&method=sha256&sign={sign}
 *
 * sign 生成算法：
 *   sign = base64( hmac_sha256( base64decode(accessKey), utf8(et + "\n" + method + "\n" + res + "\n" + version) ) )
 *
 * 参数说明：
 *   version    = "2022-05-01"
 *   res        = "products/{productId}"       （产品级访问资源）
 *   et         = 过期时间（秒级Unix时间戳，10位）
 *   method     = "sha256"                    （支持 md5/sha1/sha256）
 *   sign       = 签名结果字符串
 */
public class OneNetTokenUtil {

    // ==================== MQTT Token（原有逻辑不变） ====================

    public static String generateToken(String productId, String accessKey) throws UnsupportedEncodingException {
        return generateToken(productId, accessKey, System.currentTimeMillis() / 1000 + 86400 * 30);
    }

    public static String generateToken(String productId, String accessKey, long expireTime) throws UnsupportedEncodingException {
        String version = "2018-10-31";
        String res = "products/" + productId;
        String method = "sha1";
        long et = expireTime;
        String toSign = et + "\n" + method + "\n" + res + "\n" + version;
        String sign = hmacSign(toSign, accessKey, method);

        StringBuilder sb = new StringBuilder();
        sb.append("version=").append(version)
          .append("&res=").append(urlEncode(res))
          .append("&et=").append(et)
          .append("&method=").append(method)
          .append("&sign=").append(urlEncode(sign));
        return sb.toString();
    }

    // ==================== HTTP API Token（CMIOT DMP 平台标准） ====================

    /**
     * 生成 HTTP API 调用的 Authorization Token
     *
     * @param productId  产品ID
     * @param accessKey  产品/用户 access_key（Base64编码的密钥）
     * @return 完整的 authorization token 字符串
     */
    public static String generateApiToken(String productId, String accessKey) {
        return generateApiToken(productId, accessKey, System.currentTimeMillis() / 1000 + 3600);
    }

    /**
     * 生成 HTTP API 调用的 Authorization Token（指定过期时间）
     *
     * @param productId   产品ID
     * @param accessKey   产品 access_key
     * @param expireSeconds 过期时间（秒级 Unix 时间戳，10位）
     * @return 完整的 authorization token 字符串
     */
    public static String generateApiToken(String productId, String accessKey, long expireSeconds) {
        String version = "2022-05-01";
        String res = "products/" + productId;           // 产品级资源
        String method = "sha256";                       // 签名算法
        String et = String.valueOf(expireSeconds);      // 秒级过期时间

        // 签名原文：et\nmethod\nres\nversion
        String stringForSignature = et + "\n" + method + "\n" + res + "\n" + version;

        // sign = base64(hmac_sha256(base64decode(accessKey), stringForSignature))
        String sign = hmacSign(stringForSignature, accessKey, method);

        // 拼装完整 token，value 部分需要 URL 编码
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("version=").append(version)
              .append("&res=").append(urlEncode(res))
              .append("&et=").append(et)
              .append("&method=").append(method)
              .append("&sign=").append(urlEncode(sign));
            return sb.toString();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("URL 编码失败", e);
        }
    }

    // ==================== 底层 HMAC 签名方法 ====================

    /**
     * HMAC 签名核心方法
     *
     * 关键：accessKey 需要 **先 base64 decode** 再作为 HMAC 密钥！
     *
     * @param data      待签名字符串
     * @param accessKey Base64 编码的密钥
     * @param method    签名算法：sha1 / sha256 / md5
     * @return Base64 编码的签名结果
     */
    private static String hmacSign(String data, String accessKey, String method) {
        try {
            String algo = "Hmac" + method.toUpperCase();

            // 核心：accessKey 先 base64 decode
            byte[] decodedKey = Base64.getDecoder().decode(accessKey);
            SecretKeySpec signingKey = new SecretKeySpec(decodedKey, algo);

            Mac mac = Mac.getInstance(algo);
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-" + method.toUpperCase() + " 签名计算失败: " + e.getMessage(), e);
        }
    }

    private static String urlEncode(String str) throws UnsupportedEncodingException {
        return URLEncoder.encode(str, "UTF-8")
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }
}