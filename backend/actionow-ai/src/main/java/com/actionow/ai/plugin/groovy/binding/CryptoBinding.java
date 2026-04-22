package com.actionow.ai.plugin.groovy.binding;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * 加密工具绑定
 * 提供脚本中可用的加密/编码方法
 *
 * @author Actionow
 */
@Slf4j
public class CryptoBinding {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * HMAC-SHA256签名
     *
     * @param data 要签名的数据
     * @param key  密钥
     * @return 签名结果（十六进制）
     */
    public String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            log.error("HMAC-SHA256 failed: {}", e.getMessage());
            throw new RuntimeException("HMAC-SHA256 failed", e);
        }
    }

    /**
     * HMAC-SHA256签名（返回Base64）
     *
     * @param data 要签名的数据
     * @param key  密钥
     * @return 签名结果（Base64）
     */
    public String hmacSha256Base64(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("HMAC-SHA256 Base64 failed: {}", e.getMessage());
            throw new RuntimeException("HMAC-SHA256 failed", e);
        }
    }

    /**
     * HMAC-SHA1签名
     *
     * @param data 要签名的数据
     * @param key  密钥
     * @return 签名结果（十六进制）
     */
    public String hmacSha1(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            log.error("HMAC-SHA1 failed: {}", e.getMessage());
            throw new RuntimeException("HMAC-SHA1 failed", e);
        }
    }

    /**
     * MD5哈希
     *
     * @param data 要哈希的数据
     * @return MD5哈希值（十六进制）
     */
    public String md5(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 failed: {}", e.getMessage());
            throw new RuntimeException("MD5 failed", e);
        }
    }

    /**
     * SHA256哈希
     *
     * @param data 要哈希的数据
     * @return SHA256哈希值（十六进制）
     */
    public String sha256(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA256 failed: {}", e.getMessage());
            throw new RuntimeException("SHA256 failed", e);
        }
    }

    /**
     * SHA1哈希
     *
     * @param data 要哈希的数据
     * @return SHA1哈希值（十六进制）
     */
    public String sha1(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA1 failed: {}", e.getMessage());
            throw new RuntimeException("SHA1 failed", e);
        }
    }

    /**
     * Base64编码
     *
     * @param data 要编码的数据
     * @return Base64编码结果
     */
    public String base64Encode(String data) {
        if (data == null) {
            return "";
        }
        return Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Base64编码（字节数组）
     *
     * @param data 要编码的数据
     * @return Base64编码结果
     */
    public String base64Encode(byte[] data) {
        if (data == null) {
            return "";
        }
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Base64解码
     *
     * @param data Base64编码的数据
     * @return 解码后的字符串
     */
    public String base64Decode(String data) {
        if (data == null) {
            return "";
        }
        return new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8);
    }

    /**
     * Base64解码为字节数组
     *
     * @param data Base64编码的数据
     * @return 解码后的字节数组
     */
    public byte[] base64DecodeBytes(String data) {
        if (data == null) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(data);
    }

    /**
     * 生成UUID
     *
     * @return UUID字符串
     */
    public String uuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成不带横线的UUID
     *
     * @return UUID字符串（无横线）
     */
    public String uuidSimple() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成随机字符串
     *
     * @param length 长度
     * @return 随机字符串
     */
    public String randomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 生成时间戳（秒）
     *
     * @return 当前时间戳
     */
    public long timestamp() {
        return System.currentTimeMillis() / 1000;
    }

    /**
     * 生成时间戳（毫秒）
     *
     * @return 当前时间戳
     */
    public long timestampMs() {
        return System.currentTimeMillis();
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
