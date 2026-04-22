package com.actionow.collab.notification.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.collab.notification.entity.Notification;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    default IPage<Notification> selectPageByUser(Page<Notification> page, String userId,
                                                   String workspaceId, String type, Boolean isRead) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(workspaceId != null, Notification::getWorkspaceId, workspaceId)
                .eq(type != null, Notification::getType, type)
                .eq(isRead != null, Notification::getIsRead, isRead)
                .orderByDesc(Notification::getCreatedAt);
        return selectPage(page, wrapper);
    }

    @Select("SELECT COUNT(*) FROM t_notification WHERE user_id = #{userId} AND is_read = false")
    int countUnread(@Param("userId") String userId);

    @Select("SELECT type, COUNT(*) as cnt FROM t_notification WHERE user_id = #{userId} AND is_read = false GROUP BY type")
    List<Map<String, Object>> countUnreadByType(@Param("userId") String userId);

    @Update("UPDATE t_notification SET is_read = true, read_at = now() WHERE user_id = #{userId} AND is_read = false")
    int markAllAsRead(@Param("userId") String userId);

    @Update("UPDATE t_notification SET is_read = true, read_at = now() WHERE id = #{id} AND user_id = #{userId}")
    int markAsRead(@Param("id") String id, @Param("userId") String userId);

    @Delete("DELETE FROM t_notification WHERE ctid IN (SELECT ctid FROM t_notification WHERE expire_at IS NOT NULL AND expire_at < now() LIMIT #{batchSize})")
    int deleteExpiredBatch(@Param("batchSize") int batchSize);

    @Delete("DELETE FROM t_notification WHERE ctid IN (SELECT ctid FROM t_notification WHERE is_read = true AND created_at < #{before} LIMIT #{batchSize})")
    int deleteReadBeforeBatch(@Param("before") java.time.LocalDateTime before, @Param("batchSize") int batchSize);
}
