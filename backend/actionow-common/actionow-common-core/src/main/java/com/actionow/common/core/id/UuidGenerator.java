package com.actionow.common.core.id;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * UUIDv7 生成器
 * 基于时间戳的UUID，保证时间有序性和全局唯一性
 *
 * @author Actionow
 */
public final class UuidGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidGenerator() {
    }

    /**
     * 生成 UUIDv7
     * 格式: xxxxxxxx-xxxx-7xxx-yxxx-xxxxxxxxxxxx
     * 其中前48位为毫秒时间戳，版本号为7，变体为RFC 4122
     *
     * @return UUIDv7 字符串
     */
    public static String generateUuidV7() {
        return generateUuidV7AsUuid().toString();
    }

    /**
     * 生成 UUIDv7 并返回 UUID 对象
     *
     * @return UUID 对象
     */
    public static UUID generateUuidV7AsUuid() {
        long timestamp = Instant.now().toEpochMilli();

        // 生成随机字节
        byte[] randomBytes = new byte[10];
        RANDOM.nextBytes(randomBytes);

        // 构建 most significant bits (高64位)
        // 48位时间戳 + 4位版本号(0111) + 12位随机数
        long msb = (timestamp << 16) | 0x7000L | ((randomBytes[0] & 0xFF) << 8 | (randomBytes[1] & 0x0F));

        // 构建 least significant bits (低64位)
        // 2位变体(10) + 62位随机数
        long lsb = ((randomBytes[2] & 0x3FL) | 0x80L) << 56
                | (randomBytes[3] & 0xFFL) << 48
                | (randomBytes[4] & 0xFFL) << 40
                | (randomBytes[5] & 0xFFL) << 32
                | (randomBytes[6] & 0xFFL) << 24
                | (randomBytes[7] & 0xFFL) << 16
                | (randomBytes[8] & 0xFFL) << 8
                | (randomBytes[9] & 0xFFL);

        return new UUID(msb, lsb);
    }

    /**
     * 从 UUIDv7 中提取时间戳
     *
     * @param uuid UUID字符串
     * @return 时间戳（毫秒）
     */
    public static long extractTimestamp(String uuid) {
        return extractTimestamp(UUID.fromString(uuid));
    }

    /**
     * 从 UUIDv7 中提取时间戳
     *
     * @param uuid UUID对象
     * @return 时间戳（毫秒）
     */
    public static long extractTimestamp(UUID uuid) {
        return uuid.getMostSignificantBits() >>> 16;
    }

    /**
     * 验证是否为有效的 UUIDv7
     *
     * @param uuid UUID字符串
     * @return 是否为UUIDv7
     */
    public static boolean isValidUuidV7(String uuid) {
        try {
            UUID u = UUID.fromString(uuid);
            return (u.version() == 7);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 生成简短ID（去除横线）
     *
     * @return 32位字符串
     */
    public static String generateShortId() {
        return generateUuidV7().replace("-", "");
    }
}
