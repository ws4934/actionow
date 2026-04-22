package com.actionow.common.data.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.id.UuidGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis Plus 自动填充处理器
 * 自动填充审计字段（创建人、创建时间、更新人、更新时间等）
 *
 * @author Actionow
 */
@Slf4j
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("开始填充插入字段...");

        LocalDateTime now = LocalDateTime.now();
        String userId = getCurrentUserId();

        // ID 使用 UUIDv7
        this.strictInsertFill(metaObject, "id", String.class, UuidGenerator.generateUuidV7());

        // 创建信息
        this.strictInsertFill(metaObject, "createdBy", String.class, userId);
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);

        // 更新信息
        this.strictInsertFill(metaObject, "updatedBy", String.class, userId);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);

        // 软删除标识
        this.strictInsertFill(metaObject, "deleted", Integer.class, CommonConstants.NOT_DELETED);

        // 乐观锁版本号
        this.strictInsertFill(metaObject, "version", Integer.class, 1);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("开始填充更新字段...");

        // 更新信息
        this.strictUpdateFill(metaObject, "updatedBy", String.class, getCurrentUserId());
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }

    private String getCurrentUserId() {
        String userId = UserContextHolder.getUserId();
        return userId != null ? userId : CommonConstants.SYSTEM_USER_ID;
    }
}
