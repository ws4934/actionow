package com.actionow.user.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.actionow.common.core.constant.RedisKeyConstants;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.PageResult;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.redis.service.RedisCacheService;
import com.actionow.user.dto.request.BatchCreateInvitationCodeRequest;
import com.actionow.user.dto.request.CreateInvitationCodeRequest;
import com.actionow.user.dto.request.UpdateInvitationCodeRequest;
import com.actionow.user.dto.response.*;
import com.actionow.user.entity.InvitationCode;
import com.actionow.user.entity.InvitationCodeUsage;
import com.actionow.user.entity.User;
import com.actionow.user.enums.InvitationCodeStatus;
import com.actionow.user.enums.InvitationCodeType;
import com.actionow.user.mapper.InvitationCodeMapper;
import com.actionow.user.mapper.InvitationCodeUsageMapper;
import com.actionow.user.mapper.UserMapper;
import com.actionow.user.service.InvitationCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 邀请码服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvitationCodeServiceImpl extends ServiceImpl<InvitationCodeMapper, InvitationCode>
        implements InvitationCodeService {

    private final InvitationCodeMapper invitationCodeMapper;
    private final InvitationCodeUsageMapper usageMapper;
    private final UserMapper userMapper;
    private final RedisCacheService redisCacheService;

    // 配置键常量
    private static final String CONFIG_INVITATION_REQUIRED = "registration.invitation_code.required";
    private static final String CONFIG_ALLOW_USER_CODE = "registration.invitation_code.allow_user_code";
    private static final String CONFIG_DEFAULT_MAX_USES = "registration.invitation_code.default_max_uses";
    private static final String CONFIG_DEFAULT_VALID_DAYS = "registration.invitation_code.default_valid_days";
    private static final String CONFIG_USER_CODE_ENABLED = "user.invitation_code.enabled";
    private static final String CONFIG_USER_CODE_MAX_USES = "user.invitation_code.max_uses";
    private static final String CONFIG_USER_CODE_VALID_DAYS = "user.invitation_code.valid_days";

    // ==================== 管理员接口 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvitationCodeResponse create(CreateInvitationCodeRequest request, String operatorId) {
        // 生成或验证邀请码
        String code = request.getCode();
        if (!StringUtils.hasText(code)) {
            code = generateRandomCode(null);
        } else {
            code = code.toUpperCase();
            if (existsByCode(code)) {
                throw new BusinessException(ResultCode.ALREADY_EXISTS, "邀请码已存在");
            }
        }

        // 获取默认配置
        int defaultMaxUses = getIntConfig(CONFIG_DEFAULT_MAX_USES, 1);
        int defaultValidDays = getIntConfig(CONFIG_DEFAULT_VALID_DAYS, 30);

        InvitationCode invitationCode = new InvitationCode();
        invitationCode.setCode(code);
        invitationCode.setName(request.getName());
        invitationCode.setType(InvitationCodeType.SYSTEM.getCode());
        invitationCode.setMaxUses(request.getMaxUses() != null ? request.getMaxUses() : defaultMaxUses);
        invitationCode.setUsedCount(0);
        invitationCode.setValidFrom(request.getValidFrom());
        invitationCode.setValidUntil(request.getValidUntil() != null ? request.getValidUntil()
                : LocalDateTime.now().plusDays(defaultValidDays));
        invitationCode.setStatus(InvitationCodeStatus.ACTIVE.getCode());
        invitationCode.setCreatedBy(operatorId);

        save(invitationCode);

        log.info("创建邀请码: code={}, operatorId={}", code, operatorId);
        return InvitationCodeResponse.fromEntity(invitationCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchCreateInvitationCodeResult batchCreate(BatchCreateInvitationCodeRequest request, String operatorId) {
        String batchId = UuidGenerator.generateShortId();
        int failedCount = 0;
        List<InvitationCode> batchList = new ArrayList<>();

        int defaultMaxUses = getIntConfig(CONFIG_DEFAULT_MAX_USES, 1);
        int defaultValidDays = getIntConfig(CONFIG_DEFAULT_VALID_DAYS, 30);

        for (int i = 0; i < request.getCount(); i++) {
            try {
                String code = generateRandomCode(request.getPrefix());

                InvitationCode invitationCode = new InvitationCode();
                invitationCode.setCode(code);
                invitationCode.setName(request.getName());
                invitationCode.setType(InvitationCodeType.SYSTEM.getCode());
                invitationCode.setMaxUses(request.getMaxUses() != null ? request.getMaxUses() : defaultMaxUses);
                invitationCode.setUsedCount(0);
                invitationCode.setValidFrom(request.getValidFrom());
                invitationCode.setValidUntil(request.getValidUntil() != null ? request.getValidUntil()
                        : LocalDateTime.now().plusDays(defaultValidDays));
                invitationCode.setStatus(InvitationCodeStatus.ACTIVE.getCode());
                invitationCode.setBatchId(batchId);
                invitationCode.setCreatedBy(operatorId);

                batchList.add(invitationCode);
            } catch (Exception e) {
                log.warn("批量创建邀请码失败: index={}, error={}", i, e.getMessage());
                failedCount++;
            }
        }

        // 批量插入
        if (!batchList.isEmpty()) {
            saveBatch(batchList);
        }

        List<InvitationCodeResponse> codes = batchList.stream()
                .map(InvitationCodeResponse::fromEntity)
                .collect(Collectors.toList());

        log.info("批量创建邀请码完成: batchId={}, success={}, failed={}, operatorId={}",
                batchId, batchList.size(), failedCount, operatorId);

        return BatchCreateInvitationCodeResult.builder()
                .batchId(batchId)
                .successCount(batchList.size())
                .failedCount(failedCount)
                .codes(codes)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvitationCodeResponse update(String id, UpdateInvitationCodeRequest request, String operatorId) {
        InvitationCode invitationCode = super.getById(id);
        if (invitationCode == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "邀请码不存在");
        }

        if (StringUtils.hasText(request.getName())) {
            invitationCode.setName(request.getName());
        }
        if (request.getMaxUses() != null) {
            invitationCode.setMaxUses(request.getMaxUses());
        }
        if (request.getValidUntil() != null) {
            invitationCode.setValidUntil(request.getValidUntil());
        }
        if (StringUtils.hasText(request.getStatus())) {
            InvitationCodeStatus status = InvitationCodeStatus.fromCode(request.getStatus());
            if (status != null) {
                invitationCode.setStatus(status.getCode());
            }
        }

        updateById(invitationCode);

        log.info("更新邀请码: id={}, operatorId={}", id, operatorId);
        return InvitationCodeResponse.fromEntity(invitationCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String id, String status, String operatorId) {
        InvitationCodeStatus codeStatus = InvitationCodeStatus.fromCode(status);
        if (codeStatus == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "无效的状态");
        }

        invitationCodeMapper.updateStatus(id, codeStatus.getCode());
        log.info("更新邀请码状态: id={}, status={}, operatorId={}", id, status, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id, String operatorId) {
        InvitationCode invitationCode = super.getById(id);
        if (invitationCode == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "邀请码不存在");
        }

        removeById(id);
        log.info("删除邀请码: id={}, operatorId={}", id, operatorId);
    }

    @Override
    public InvitationCodeResponse getById(String id) {
        InvitationCode invitationCode = super.getById(id);
        if (invitationCode == null) {
            return null;
        }

        InvitationCodeResponse response = InvitationCodeResponse.fromEntity(invitationCode);

        // 填充创建者信息
        if (StringUtils.hasText(invitationCode.getCreatedBy())) {
            User creator = userMapper.selectById(invitationCode.getCreatedBy());
            if (creator != null) {
                response.setCreatorName(creator.getNickname() != null ? creator.getNickname() : creator.getUsername());
            }
        }

        // 填充所有者信息
        if (StringUtils.hasText(invitationCode.getOwnerId())) {
            User owner = userMapper.selectById(invitationCode.getOwnerId());
            if (owner != null) {
                response.setOwnerName(owner.getNickname() != null ? owner.getNickname() : owner.getUsername());
            }
        }

        return response;
    }

    @Override
    public PageResult<InvitationCodeResponse> listPage(int page, int size, String type, String status, String keyword) {
        LambdaQueryWrapper<InvitationCode> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(type)) {
            wrapper.eq(InvitationCode::getType, type);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(InvitationCode::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(InvitationCode::getCode, keyword)
                    .or().like(InvitationCode::getName, keyword));
        }

        wrapper.orderByDesc(InvitationCode::getCreatedAt);

        IPage<InvitationCode> pageResult = page(new Page<>(page, size), wrapper);

        List<InvitationCodeResponse> list = pageResult.getRecords().stream()
                .map(InvitationCodeResponse::fromEntity)
                .collect(Collectors.toList());

        return PageResult.of((long) page, (long) size, pageResult.getTotal(), list);
    }

    @Override
    public PageResult<InvitationCodeUsageResponse> getUsages(String id, int page, int size) {
        IPage<InvitationCodeUsage> pageResult = usageMapper.selectByCodeId(new Page<>(page, size), id);

        List<InvitationCodeUsageResponse> list = pageResult.getRecords().stream()
                .map(InvitationCodeUsageResponse::fromEntity)
                .collect(Collectors.toList());

        return PageResult.of((long) page, (long) size, pageResult.getTotal(), list);
    }

    @Override
    public InvitationCodeStatisticsResponse getStatistics() {
        Map<String, Object> stats = invitationCodeMapper.selectAggregatedStatistics();
        long totalUsed = usageMapper.selectCount(null);

        return InvitationCodeStatisticsResponse.builder()
                .total(toLong(stats.get("total")))
                .activeCount(toLong(stats.get("active_count")))
                .disabledCount(toLong(stats.get("disabled_count")))
                .exhaustedCount(toLong(stats.get("exhausted_count")))
                .expiredCount(toLong(stats.get("expired_count")))
                .systemCodeCount(toLong(stats.get("system_count")))
                .userCodeCount(toLong(stats.get("user_count")))
                .totalUsed(totalUsed)
                .build();
    }

    private static long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    // ==================== 用户接口 ====================

    @Override
    public InvitationCode getUserActiveCode(String userId) {
        return invitationCodeMapper.selectActiveByOwnerAndType(userId, InvitationCodeType.USER.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvitationCode generateUserCode(String userId) {
        int maxUses = getIntConfig(CONFIG_USER_CODE_MAX_USES, 5);
        int validDays = getIntConfig(CONFIG_USER_CODE_VALID_DAYS, 90);

        InvitationCode invitationCode = new InvitationCode();
        invitationCode.setCode(generateRandomCode(null));
        invitationCode.setType(InvitationCodeType.USER.getCode());
        invitationCode.setOwnerId(userId);
        invitationCode.setMaxUses(maxUses);
        invitationCode.setUsedCount(0);
        invitationCode.setValidFrom(LocalDateTime.now());
        invitationCode.setValidUntil(LocalDateTime.now().plusDays(validDays));
        invitationCode.setStatus(InvitationCodeStatus.ACTIVE.getCode());
        invitationCode.setCreatedBy(userId);

        save(invitationCode);

        log.info("为用户生成邀请码: userId={}, code={}", userId, invitationCode.getCode());
        return invitationCode;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvitationCode refreshUserCode(String userId) {
        // 将旧邀请码标记为已替换
        invitationCodeMapper.updateStatusToReplacedByOwner(userId, InvitationCodeType.USER.getCode());

        // 生成新邀请码
        InvitationCode newCode = generateUserCode(userId);

        log.info("刷新用户邀请码: userId={}, newCode={}", userId, newCode.getCode());
        return newCode;
    }

    @Override
    public UserInvitationCodeResponse getUserCodeResponse(String userId) {
        InvitationCode code = getUserActiveCode(userId);
        if (code == null) {
            return null;
        }

        int totalInvited = invitationCodeMapper.sumUsedCountByOwner(userId);
        return UserInvitationCodeResponse.fromEntity(code, totalInvited);
    }

    @Override
    public PageResult<InviteeResponse> getInvitees(String userId, int page, int size) {
        IPage<InvitationCodeUsage> pageResult = usageMapper.selectByInviterId(new Page<>(page, size), userId);

        // 批量获取用户信息
        List<String> userIds = pageResult.getRecords().stream()
                .map(InvitationCodeUsage::getInviteeId)
                .collect(Collectors.toList());

        Map<String, User> userMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        List<InviteeResponse> list = pageResult.getRecords().stream()
                .map(usage -> {
                    User user = userMap.get(usage.getInviteeId());
                    return InviteeResponse.builder()
                            .userId(usage.getInviteeId())
                            .username(usage.getInviteeUsername())
                            .nickname(user != null ? user.getNickname() : null)
                            .avatar(user != null ? user.getAvatar() : null)
                            .registeredAt(usage.getUsedAt())
                            .build();
                })
                .collect(Collectors.toList());

        return PageResult.of((long) page, (long) size, pageResult.getTotal(), list);
    }

    @Override
    public InviterResponse getInviter(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getInvitedBy())) {
            return null;
        }

        User inviter = userMapper.selectById(user.getInvitedBy());
        if (inviter == null) {
            return null;
        }

        return InviterResponse.builder()
                .userId(inviter.getId())
                .username(inviter.getUsername())
                .nickname(inviter.getNickname())
                .avatar(inviter.getAvatar())
                .build();
    }

    // ==================== 公共接口 ====================

    @Override
    public InvitationCode findByCode(String code) {
        return invitationCodeMapper.selectByCode(code);
    }

    @Override
    public InvitationCodeValidateResponse validateCode(String code) {
        InvitationCode invitationCode = findByCode(code);

        if (invitationCode == null) {
            return InvitationCodeValidateResponse.invalid("邀请码不存在");
        }

        // 检查是否允许使用用户邀请码
        if (InvitationCodeType.USER.getCode().equals(invitationCode.getType())) {
            if (!isUserCodeAllowed()) {
                return InvitationCodeValidateResponse.invalid("当前不支持使用用户邀请码注册");
            }
        }

        // 检查状态
        InvitationCodeStatus status = InvitationCodeStatus.fromCode(invitationCode.getStatus());
        if (status == null || !status.isUsable()) {
            String message = switch (invitationCode.getStatus()) {
                case "DISABLED" -> "邀请码已被禁用";
                case "EXHAUSTED" -> "邀请码已被使用完";
                case "EXPIRED" -> "邀请码已过期";
                case "REPLACED" -> "邀请码已失效";
                default -> "邀请码无效";
            };
            return InvitationCodeValidateResponse.invalid(message);
        }

        LocalDateTime now = LocalDateTime.now();

        // 检查生效时间
        if (invitationCode.getValidFrom() != null && now.isBefore(invitationCode.getValidFrom())) {
            return InvitationCodeValidateResponse.invalid("邀请码尚未生效");
        }

        // 检查失效时间
        if (invitationCode.getValidUntil() != null && now.isAfter(invitationCode.getValidUntil())) {
            // 更新状态为已过期
            invitationCodeMapper.updateStatus(invitationCode.getId(), InvitationCodeStatus.EXPIRED.getCode());
            return InvitationCodeValidateResponse.invalid("邀请码已过期");
        }

        // 检查使用次数
        int remainingUses = -1;
        if (invitationCode.getMaxUses() != null && invitationCode.getMaxUses() != -1) {
            int usedCount = invitationCode.getUsedCount() != null ? invitationCode.getUsedCount() : 0;
            remainingUses = invitationCode.getMaxUses() - usedCount;
            if (remainingUses <= 0) {
                invitationCodeMapper.updateStatus(invitationCode.getId(), InvitationCodeStatus.EXHAUSTED.getCode());
                return InvitationCodeValidateResponse.invalid("邀请码已被使用完");
            }
        }

        // 获取邀请人信息
        String inviterName = null;
        if (InvitationCodeType.USER.getCode().equals(invitationCode.getType())
                && StringUtils.hasText(invitationCode.getOwnerId())) {
            User inviter = userMapper.selectById(invitationCode.getOwnerId());
            if (inviter != null) {
                inviterName = inviter.getNickname() != null ? inviter.getNickname() : inviter.getUsername();
            }
        }

        return InvitationCodeValidateResponse.valid(
                invitationCode.getType(),
                inviterName,
                remainingUses,
                invitationCode.getValidUntil()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void useCode(String code, User invitee, String ipAddress, String userAgent) {
        InvitationCode invitationCode = findByCode(code);
        if (invitationCode == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "邀请码不存在");
        }

        // 再次验证
        InvitationCodeValidateResponse validateResult = validateCode(code);
        if (!validateResult.getValid()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, validateResult.getMessage());
        }

        // 增加使用计数
        invitationCodeMapper.incrementUsedCount(invitationCode.getId());

        // 检查是否耗尽
        int newUsedCount = (invitationCode.getUsedCount() != null ? invitationCode.getUsedCount() : 0) + 1;
        if (invitationCode.getMaxUses() != null && invitationCode.getMaxUses() != -1
                && newUsedCount >= invitationCode.getMaxUses()) {
            invitationCodeMapper.updateStatus(invitationCode.getId(), InvitationCodeStatus.EXHAUSTED.getCode());
        }

        // 记录使用详情
        InvitationCodeUsage usage = new InvitationCodeUsage();
        usage.setInvitationCodeId(invitationCode.getId());
        usage.setCode(code);
        usage.setInviterId(invitationCode.getOwnerId());
        usage.setInviteeId(invitee.getId());
        usage.setInviteeUsername(invitee.getUsername());
        usage.setInviteeEmail(invitee.getEmail());
        usage.setUsedAt(LocalDateTime.now());
        usage.setIpAddress(ipAddress);
        usage.setUserAgent(userAgent);

        usageMapper.insert(usage);

        log.info("使用邀请码: code={}, inviteeId={}, inviterId={}",
                code, invitee.getId(), invitationCode.getOwnerId());
    }

    @Override
    public boolean existsByCode(String code) {
        return invitationCodeMapper.countByCode(code) > 0;
    }

    @Override
    public RegistrationConfigResponse getRegistrationConfig() {
        return RegistrationConfigResponse.builder()
                .invitationCodeRequired(isInvitationCodeRequired())
                .allowUserCode(isUserCodeAllowed())
                .build();
    }

    @Override
    public boolean isInvitationCodeRequired() {
        return getBooleanConfig(CONFIG_INVITATION_REQUIRED, false);
    }

    @Override
    public boolean isUserCodeAllowed() {
        return getBooleanConfig(CONFIG_ALLOW_USER_CODE, true);
    }

    @Override
    public boolean isUserCodeEnabled() {
        return getBooleanConfig(CONFIG_USER_CODE_ENABLED, true);
    }

    // ==================== 私有方法 ====================

    /**
     * 生成随机邀请码
     */
    private String generateRandomCode(String prefix) {
        String randomPart = RandomUtil.randomString("ABCDEFGHJKLMNPQRSTUVWXYZ23456789", 6);
        String code;
        if (StringUtils.hasText(prefix)) {
            code = prefix.toUpperCase() + "-" + randomPart;
        } else {
            code = "AC-" + randomPart;
        }

        // 确保唯一性
        int maxRetry = 10;
        while (existsByCode(code) && maxRetry-- > 0) {
            randomPart = RandomUtil.randomString("ABCDEFGHJKLMNPQRSTUVWXYZ23456789", 6);
            if (StringUtils.hasText(prefix)) {
                code = prefix.toUpperCase() + "-" + randomPart;
            } else {
                code = "AC-" + randomPart;
            }
        }

        return code;
    }

    /**
     * 获取布尔配置
     */
    private boolean getBooleanConfig(String key, boolean defaultValue) {
        String value = redisCacheService.get(RedisKeyConstants.SYSTEM_CONFIG + key);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    /**
     * 获取整数配置
     */
    private int getIntConfig(String key, int defaultValue) {
        String value = redisCacheService.get(RedisKeyConstants.SYSTEM_CONFIG + key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
