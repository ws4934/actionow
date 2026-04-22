package com.actionow.project.service.version;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.ResultCode;
import com.actionow.project.dto.version.RestoreVersionRequest;
import com.actionow.project.dto.version.VersionInfoResponse;
import com.actionow.project.entity.version.EntityVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 版本服务抽象基类
 * 提供通用的版本控制操作实现
 *
 * @param <E> 主实体类型
 * @param <V> 版本实体类型
 * @param <D> 版本详情响应类型
 * @author Actionow
 */
@Slf4j
public abstract class AbstractVersionServiceImpl<E, V extends EntityVersion, D> implements VersionService<E, D> {

    // ==================== 抽象方法 - 子类必须实现 ====================

    /**
     * 获取实体类型名称（用于日志和响应）
     */
    protected abstract String getEntityType();

    /**
     * 获取实体类型名称（中文，用于日志）
     */
    protected abstract String getEntityTypeName();

    /**
     * 获取主实体 Mapper
     */
    protected abstract BaseMapper<E> getEntityMapper();

    /**
     * 根据 ID 查询主实体（默认走 {@link #getEntityMapper()}.selectById；有特殊查询需求的子类可覆盖）
     */
    protected E findEntityById(String entityId) {
        return getEntityMapper().selectById(entityId);
    }

    /**
     * 获取主实体的 currentVersionId
     */
    protected abstract String getEntityCurrentVersionId(E entity);

    /**
     * 根据实体 ID 查询所有版本（按版本号降序）
     */
    protected abstract List<V> findVersionsByEntityId(String entityId);

    /**
     * 根据实体 ID 和版本号查询特定版本
     */
    protected abstract V findVersionByNumber(String entityId, Integer versionNumber);

    /**
     * 根据版本 ID 查询版本
     */
    protected abstract V findVersionById(String versionId);

    /**
     * 插入版本记录（自动计算版本号）
     */
    protected abstract int insertVersionWithAutoNumber(V version);

    /**
     * 根据版本 ID 查询版本号
     */
    protected abstract Integer findVersionNumberById(String versionId);

    /**
     * 版本字段声明：create/restore/buildFieldDiffs 三处的字段列表由此驱动。
     * 仅需声明业务字段；FK 链接（如 version.setPropId）、workspaceId 与审计字段由其他 hook 负责。
     */
    protected abstract List<VersionFieldSpec<E, V>> getFieldSpecs();

    /**
     * 构造空的版本实体
     */
    protected abstract V newVersion();

    /**
     * 把版本实体连接到主实体：设置 FK（version.setXxxId(entity.getId())）与 workspaceId 等
     */
    protected abstract void linkVersionToEntity(V version, E entity);

    /**
     * 构建 restore 时使用的 LambdaUpdateWrapper 基础（eq(id) + 审计字段 updatedBy/updatedAt）
     */
    protected abstract LambdaUpdateWrapper<E> buildRestoreWrapperBase(
            String entityId, String userId, LocalDateTime now);

    /**
     * 构建 updateEntityVersionMeta 时使用的 LambdaUpdateWrapper 基础（eq(id) + currentVersionId + versionNumber）
     */
    protected abstract LambdaUpdateWrapper<E> buildVersionMetaWrapperBase(
            String entityId, String versionId, Integer versionNumber);

    /**
     * 构建版本详情响应（DTO 异构，保留在子类）
     */
    protected abstract D buildDetailResponse(V version, String currentVersionId);

    /**
     * 读取主实体的 versionNumber（用于 CAS 比对）
     */
    protected abstract Integer getEntityVersionNumber(E entity);

    /**
     * 主实体 versionNumber 字段的 SFunction（用于 WHERE CAS 条件）
     */
    protected abstract SFunction<E, Integer> entityVersionNumberSGetter();

    // ==================== 由 FieldSpec 驱动的默认实现 ====================

    protected V createVersionFromEntity(E entity) {
        V version = newVersion();
        linkVersionToEntity(version, entity);
        for (VersionFieldSpec<E, V> spec : getFieldSpecs()) {
            spec.versionSetter().accept(version, spec.readFromEntity(entity));
        }
        return version;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void restoreEntityFromVersion(String entityId, V version, String userId, Integer expectedVersionNumber) {
        LambdaUpdateWrapper<E> wrapper = buildRestoreWrapperBase(entityId, userId, LocalDateTime.now());
        for (VersionFieldSpec<E, V> spec : getFieldSpecs()) {
            wrapper.set(true,
                    (SFunction) spec.entitySGetter(),
                    spec.versionGetter().apply(version));
        }
        applyVersionCas(wrapper, expectedVersionNumber);
        int rows = getEntityMapper().update(null, wrapper);
        if (rows == 0) {
            throw new BusinessException(ResultCode.CONCURRENT_OPERATION);
        }
    }

    protected void updateEntityVersionMeta(String entityId, String versionId, Integer versionNumber,
                                           Integer expectedVersionNumber) {
        updateEntityVersionMeta(entityId, versionId, versionNumber, expectedVersionNumber, true);
    }

    protected void updateEntityVersionMeta(String entityId, String versionId, Integer versionNumber,
                                           Integer expectedVersionNumber, boolean applyCas) {
        LambdaUpdateWrapper<E> wrapper = buildVersionMetaWrapperBase(entityId, versionId, versionNumber);
        if (applyCas) {
            applyVersionCas(wrapper, expectedVersionNumber);
        }
        int rows = getEntityMapper().update(null, wrapper);
        if (rows == 0) {
            throw new BusinessException(ResultCode.CONCURRENT_OPERATION);
        }
    }

    private void applyVersionCas(LambdaUpdateWrapper<E> wrapper, Integer expectedVersionNumber) {
        SFunction<E, Integer> col = entityVersionNumberSGetter();
        if (expectedVersionNumber == null) {
            wrapper.isNull(col);
        } else {
            wrapper.eq(col, expectedVersionNumber);
        }
    }

    protected List<VersionDiffResponse.FieldDiff> buildFieldDiffs(V v1, V v2) {
        List<VersionDiffResponse.FieldDiff> diffs = new ArrayList<>();
        for (VersionFieldSpec<E, V> spec : getFieldSpecs()) {
            addFieldDiff(diffs, spec.fieldName(), spec.fieldLabel(),
                    spec.versionGetter().apply(v1), spec.versionGetter().apply(v2));
        }
        return diffs;
    }

    // ==================== 通用实现 ====================

    @Override
    public List<VersionInfoResponse> listVersions(String entityId) {
        List<V> versions = findVersionsByEntityId(entityId);
        E entity = findEntityById(entityId);
        String currentVersionId = entity != null ? getEntityCurrentVersionId(entity) : null;

        return versions.stream()
                .map(v -> VersionInfoResponse.builder()
                        .id(v.getId())
                        .entityId(entityId)
                        .entityType(getEntityType())
                        .versionNumber(v.getVersionNumber())
                        .changeSummary(v.getChangeSummary())
                        .createdBy(v.getCreatedBy())
                        .createdAt(v.getCreatedAt())
                        .isCurrent(v.getId().equals(currentVersionId))
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public D getVersion(String entityId, Integer versionNumber) {
        V version = findVersionByNumber(entityId, versionNumber);
        if (version == null) {
            return null;
        }

        E entity = findEntityById(entityId);
        String currentVersionId = entity != null ? getEntityCurrentVersionId(entity) : null;

        return buildDetailResponse(version, currentVersionId);
    }

    @Override
    public D getCurrentVersion(String entityId) {
        E entity = findEntityById(entityId);
        if (entity == null) {
            return null;
        }

        String currentVersionId = getEntityCurrentVersionId(entity);
        if (currentVersionId == null) {
            return null;
        }

        V version = findVersionById(currentVersionId);
        if (version == null) {
            return null;
        }

        return buildDetailResponse(version, currentVersionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer restoreVersion(String entityId, RestoreVersionRequest request, String userId) {
        V targetVersion = findVersionByNumber(entityId, request.getVersionNumber());
        if (targetVersion == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "目标版本不存在");
        }

        E entity = findEntityById(entityId);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, getEntityTypeName() + "不存在");
        }

        Integer expectedVersionNumber = getEntityVersionNumber(entity);

        // 直接从目标版本恢复数据，不再创建新的版本快照
        restoreEntityFromVersion(entityId, targetVersion, userId, expectedVersionNumber);

        // 更新版本指针指向目标历史版本；restore 已经把 versionNumber 设成目标号，这里用目标号做 CAS
        updateEntityVersionMeta(entityId, targetVersion.getId(), targetVersion.getVersionNumber(),
                targetVersion.getVersionNumber());

        log.info("Restored {} {} to version {}", getEntityType().toLowerCase(), entityId, request.getVersionNumber());
        return targetVersion.getVersionNumber();
    }

    @Override
    public VersionDiffResponse compareVersions(String entityId, Integer versionNumber1, Integer versionNumber2) {
        V v1 = findVersionByNumber(entityId, versionNumber1);
        V v2 = findVersionByNumber(entityId, versionNumber2);

        if (v1 == null || v2 == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "版本不存在");
        }

        // 语义：versionNumber1 是目标版本（要恢复到的版本），versionNumber2 是当前版本
        // 所以 oldValue = v2（当前状态），newValue = v1（目标状态）
        List<VersionDiffResponse.FieldDiff> diffs = buildFieldDiffs(v2, v1);

        return VersionDiffResponse.builder()
                .entityId(entityId)
                .versionNumber1(versionNumber1)
                .versionNumber2(versionNumber2)
                .fieldDiffs(diffs)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createVersionSnapshot(E entity, String changeSummary, String userId) {
        boolean firstSnapshot = getEntityCurrentVersionId(entity) == null;
        Integer previousVersionNumber = getEntityVersionNumber(entity);

        V version = createVersionFromEntity(entity);
        version.setId(UuidGenerator.generateUuidV7());
        version.setChangeSummary(changeSummary);
        version.setCreatedBy(userId);
        version.setCreatedAt(LocalDateTime.now());

        int rows = insertVersionWithAutoNumber(version);
        if (rows == 0) {
            throw new BusinessException(ResultCode.CONCURRENT_OPERATION);
        }

        Integer actualVersionNumber = findVersionNumberById(version.getId());

        // 首次快照时实体刚刚 INSERT，DB 的 version_number 可能由 DEFAULT 填充而内存实体为 null，
        // 对此场景跳过 CAS（并发冲突概率近 0）。后续快照仍走 CAS。
        updateEntityVersionMeta(getEntityId(entity), version.getId(), actualVersionNumber,
                firstSnapshot ? null : previousVersionNumber, !firstSnapshot);

        log.info("Created version {} for {} {}", actualVersionNumber, getEntityType().toLowerCase(), getEntityId(entity));
        return version.getId();
    }

    /**
     * 获取实体 ID（由子类实现）
     */
    protected abstract String getEntityId(E entity);

    // ==================== 通用工具方法 ====================

    /**
     * 添加字段差异（改进版：支持 Map 深度比较）
     */
    protected void addFieldDiff(List<VersionDiffResponse.FieldDiff> diffs,
                                String fieldName, String fieldLabel,
                                Object oldValue, Object newValue) {
        String changeType = determineChangeType(oldValue, newValue);

        if (!"UNCHANGED".equals(changeType)) {
            diffs.add(VersionDiffResponse.FieldDiff.builder()
                    .fieldName(fieldName)
                    .fieldLabel(fieldLabel)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .changeType(changeType)
                    .build());
        }
    }

    /**
     * 判断变更类型（改进版：正确处理 Map 和集合类型）
     */
    protected String determineChangeType(Object oldValue, Object newValue) {
        // 都为 null 或都为空
        if (isNullOrEmpty(oldValue) && isNullOrEmpty(newValue)) {
            return "UNCHANGED";
        }

        // 一个为空，一个不为空
        if (isNullOrEmpty(oldValue)) {
            return "ADDED";
        }
        if (isNullOrEmpty(newValue)) {
            return "REMOVED";
        }

        // 两个都有值，进行深度比较
        if (deepEquals(oldValue, newValue)) {
            return "UNCHANGED";
        }

        return "MODIFIED";
    }

    /**
     * 判断值是否为 null 或空
     */
    protected boolean isNullOrEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String && ((String) value).isEmpty()) {
            return true;
        }
        if (value instanceof Map && ((Map<?, ?>) value).isEmpty()) {
            return true;
        }
        if (value instanceof Collection && ((Collection<?>) value).isEmpty()) {
            return true;
        }
        return false;
    }

    /**
     * 深度比较两个对象
     */
    protected boolean deepEquals(Object obj1, Object obj2) {
        if (obj1 == obj2) {
            return true;
        }
        if (obj1 == null || obj2 == null) {
            return false;
        }

        // Map 类型深度比较
        if (obj1 instanceof Map && obj2 instanceof Map) {
            return mapsEqual((Map<?, ?>) obj1, (Map<?, ?>) obj2);
        }

        // Collection 类型深度比较
        if (obj1 instanceof Collection && obj2 instanceof Collection) {
            return collectionsEqual((Collection<?>) obj1, (Collection<?>) obj2);
        }

        // 其他类型使用 Objects.equals
        return Objects.equals(obj1, obj2);
    }

    /**
     * 比较两个 Map 是否相等
     */
    @SuppressWarnings("unchecked")
    protected boolean mapsEqual(Map<?, ?> map1, Map<?, ?> map2) {
        if (map1.size() != map2.size()) {
            return false;
        }

        for (Map.Entry<?, ?> entry : map1.entrySet()) {
            Object key = entry.getKey();
            Object value1 = entry.getValue();
            Object value2 = map2.get(key);

            if (!map2.containsKey(key)) {
                return false;
            }

            if (!deepEquals(value1, value2)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 比较两个 Collection 是否相等
     */
    protected boolean collectionsEqual(Collection<?> col1, Collection<?> col2) {
        if (col1.size() != col2.size()) {
            return false;
        }

        // 转换为 List 进行元素级比较
        List<?> list1 = new ArrayList<>(col1);
        List<?> list2 = new ArrayList<>(col2);

        for (int i = 0; i < list1.size(); i++) {
            if (!deepEquals(list1.get(i), list2.get(i))) {
                return false;
            }
        }

        return true;
    }
}
