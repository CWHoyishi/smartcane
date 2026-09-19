package com.smartcane.backend.service.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 口令哈希工具（PBKDF2WithHmacSHA256）。
 *
 * 为什么不用 BCrypt：本地 Maven 仓库只有 Spring Security 5.x 的 crypto 包，与本项目
 * Boot 3.4.5（Spring Security 6 世代）不同代，混用有风险；PBKDF2 是 JDK 自带实现，
 * 不引新依赖也没有版本冲突，强度对内网演示场景足够。
 *
 * 存储格式：pbkdf2$迭代次数$盐(Base64)$哈希(Base64)。迭代次数随哈希一起存，
 * 以后提高强度不会让已有密码失效。
 */
public final class PasswordUtil {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2";
    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    /** 生成可入库的密码哈希 */
    public static String encode(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = derive(rawPassword, salt, ITERATIONS);
        return PREFIX + "$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);
    }

    /** 校验明文密码与库中哈希是否匹配 */
    public static boolean matches(String rawPassword, String stored) {
        if (rawPassword == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            // 定长比较：避免逐字节提前返回带来的时序差异
            return MessageDigest.isEqual(expected, derive(rawPassword, salt, iterations));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] derive(String rawPassword, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("口令哈希计算失败", e);
        }
    }
}
