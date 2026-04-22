package com.actionow.project.service.version;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 版本字段声明。一次声明即覆盖 createVersionFromEntity / restoreEntityFromVersion / buildFieldDiffs 三处字段列表。
 * buildDetailResponse 因 DTO 类型异构保留在子类。
 *
 * <p>getter/setter 使用原始类型（rawtype）以绕过字段值类型异构的泛型方差限制：
 * 同一 FIELD_SPECS 列表中的字段可能分别返回 String/Integer/Map 等不同类型，
 * 无法用单一 {@code T} 参数化。运行时安全由 MyBatis-Plus 的 SFunction 与实体
 * getter/setter 的真实签名保证。
 *
 * @param <E> 主实体类型
 * @param <V> 版本实体类型
 */
@SuppressWarnings("rawtypes")
public record VersionFieldSpec<E, V>(
        String fieldName,
        String fieldLabel,
        SFunction<E, ?> entitySGetter,
        Function versionGetter,
        BiConsumer versionSetter,
        boolean copyMap) {

    @SuppressWarnings("unchecked")
    public Object readFromEntity(E entity) {
        Object v = entitySGetter.apply(entity);
        if (copyMap && v instanceof Map<?, ?> m) {
            return new HashMap<>(m);
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    public Object readFromVersion(V version) {
        return versionGetter.apply(version);
    }

    @SuppressWarnings("unchecked")
    public void writeToVersion(V version, Object value) {
        versionSetter.accept(version, value);
    }

    public static <E, V, T> VersionFieldSpec<E, V> of(
            String fieldName, String fieldLabel,
            SFunction<E, T> entityGetter,
            Function<V, T> versionGetter,
            BiConsumer<V, T> versionSetter) {
        return new VersionFieldSpec<>(fieldName, fieldLabel,
                entityGetter, versionGetter, versionSetter, false);
    }

    public static <E, V, T extends Map<?, ?>> VersionFieldSpec<E, V> ofMap(
            String fieldName, String fieldLabel,
            SFunction<E, T> entityGetter,
            Function<V, T> versionGetter,
            BiConsumer<V, T> versionSetter) {
        return new VersionFieldSpec<>(fieldName, fieldLabel,
                entityGetter, versionGetter, versionSetter, true);
    }
}
