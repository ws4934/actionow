package com.actionow.common.data.config;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.actionow.common.core.id.UuidGenerator;

/**
 * UUIDv7 ID 生成器
 * 用于 MyBatis Plus 的主键生成
 *
 * @author Actionow
 */
public class UuidV7IdentifierGenerator implements IdentifierGenerator {

    @Override
    public Number nextId(Object entity) {
        // 不支持 Number 类型的 ID
        throw new UnsupportedOperationException("UUIDv7 generator does not support numeric IDs");
    }

    @Override
    public String nextUUID(Object entity) {
        return UuidGenerator.generateUuidV7();
    }
}
