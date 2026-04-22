package com.actionow.common.core.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 时间工具类
 *
 * @author Actionow
 */
public final class TimeUtils {

    public static final String PATTERN_DATETIME = "yyyy-MM-dd HH:mm:ss";
    public static final String PATTERN_DATE = "yyyy-MM-dd";
    public static final String PATTERN_TIME = "HH:mm:ss";
    public static final String PATTERN_DATETIME_COMPACT = "yyyyMMddHHmmss";

    public static final DateTimeFormatter FORMATTER_DATETIME = DateTimeFormatter.ofPattern(PATTERN_DATETIME);
    public static final DateTimeFormatter FORMATTER_DATE = DateTimeFormatter.ofPattern(PATTERN_DATE);
    public static final DateTimeFormatter FORMATTER_TIME = DateTimeFormatter.ofPattern(PATTERN_TIME);

    public static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private TimeUtils() {
    }

    /**
     * 获取当前时间戳（毫秒）
     */
    public static long currentTimeMillis() {
        return Instant.now().toEpochMilli();
    }

    /**
     * 获取当前时间戳（秒）
     */
    public static long currentTimeSeconds() {
        return Instant.now().getEpochSecond();
    }

    /**
     * 获取当前日期时间
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE_SHANGHAI);
    }

    /**
     * 获取当前日期时间（指定时区）
     */
    public static LocalDateTime now(ZoneId zone) {
        return LocalDateTime.now(zone);
    }

    /**
     * 获取当前日期
     */
    public static LocalDate today() {
        return LocalDate.now(ZONE_SHANGHAI);
    }

    /**
     * 获取当前日期（指定时区）
     */
    public static LocalDate today(ZoneId zone) {
        return LocalDate.now(zone);
    }

    /**
     * 格式化日期时间
     */
    public static String format(LocalDateTime dateTime) {
        return dateTime.format(FORMATTER_DATETIME);
    }

    /**
     * 格式化日期时间
     */
    public static String format(LocalDateTime dateTime, String pattern) {
        return dateTime.format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * 格式化日期
     */
    public static String format(LocalDate date) {
        return date.format(FORMATTER_DATE);
    }

    /**
     * 解析日期时间
     */
    public static LocalDateTime parseDateTime(String text) {
        return LocalDateTime.parse(text, FORMATTER_DATETIME);
    }

    /**
     * 解析日期
     */
    public static LocalDate parseDate(String text) {
        return LocalDate.parse(text, FORMATTER_DATE);
    }

    /**
     * LocalDateTime 转时间戳（毫秒）
     */
    public static long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(ZONE_SHANGHAI).toInstant().toEpochMilli();
    }

    /**
     * 时间戳（毫秒）转 LocalDateTime
     */
    public static LocalDateTime fromEpochMilli(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZONE_SHANGHAI);
    }

    /**
     * 时间戳（秒）转 LocalDateTime
     */
    public static LocalDateTime fromEpochSecond(long epochSecond) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZONE_SHANGHAI);
    }

    /**
     * 计算两个日期时间之间的间隔（天数）
     */
    public static long daysBetween(LocalDateTime start, LocalDateTime end) {
        return ChronoUnit.DAYS.between(start, end);
    }

    /**
     * 计算两个日期时间之间的间隔（小时）
     */
    public static long hoursBetween(LocalDateTime start, LocalDateTime end) {
        return ChronoUnit.HOURS.between(start, end);
    }

    /**
     * 获取今天开始时间
     */
    public static LocalDateTime startOfDay() {
        return today().atStartOfDay();
    }

    /**
     * 获取今天结束时间
     */
    public static LocalDateTime endOfDay() {
        return today().atTime(LocalTime.MAX);
    }

    /**
     * 获取本周开始时间（周一）
     */
    public static LocalDateTime startOfWeek() {
        return today().with(DayOfWeek.MONDAY).atStartOfDay();
    }

    /**
     * 获取本月开始时间
     */
    public static LocalDateTime startOfMonth() {
        return today().withDayOfMonth(1).atStartOfDay();
    }

    /**
     * 判断是否过期
     */
    public static boolean isExpired(LocalDateTime expireTime) {
        return expireTime != null && now().isAfter(expireTime);
    }

    /**
     * 判断是否过期（时间戳毫秒）
     */
    public static boolean isExpired(long expireTimeMillis) {
        return currentTimeMillis() > expireTimeMillis;
    }
}
