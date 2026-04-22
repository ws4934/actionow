package com.actionow.collab.notification.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.collab.notification.entity.NotificationPreference;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationPreferenceMapper extends BaseMapper<NotificationPreference> {

    default NotificationPreference selectByUserAndWorkspace(String userId, String workspaceId) {
        return selectOne(new LambdaQueryWrapper<NotificationPreference>()
                .eq(NotificationPreference::getUserId, userId)
                .eq(workspaceId != null, NotificationPreference::getWorkspaceId, workspaceId)
                .isNull(workspaceId == null, NotificationPreference::getWorkspaceId));
    }
}
