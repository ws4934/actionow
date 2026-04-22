package com.actionow.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.billing.entity.ProviderEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 支付回调事件 Mapper
 */
@Mapper
public interface ProviderEventMapper extends BaseMapper<ProviderEvent> {

    @Select("SELECT * FROM public.t_provider_event WHERE provider = #{provider} AND event_id = #{eventId}")
    ProviderEvent selectByProviderAndEventId(@Param("provider") String provider,
                                             @Param("eventId") String eventId);

    @Insert("INSERT INTO public.t_provider_event (id, provider, event_id, event_type, resource_id, event_created_at, " +
            "signature_verified, process_status, payload_raw, created_at, updated_at) " +
            "VALUES (#{id}, #{provider}, #{eventId}, #{eventType}, #{resourceId}, #{eventCreatedAt}, " +
            "#{signatureVerified}, #{processStatus}, CAST(#{payloadRaw} AS jsonb), NOW(), NOW()) " +
            "ON CONFLICT (provider, event_id) DO NOTHING")
    int insertIgnore(ProviderEvent event);

    @Update("UPDATE public.t_provider_event SET process_status = 'PROCESSED', process_result = #{result}, " +
            "processed_at = NOW(), updated_at = NOW() WHERE provider = #{provider} AND event_id = #{eventId}")
    int markProcessed(@Param("provider") String provider,
                      @Param("eventId") String eventId,
                      @Param("result") String result);

    @Update("UPDATE public.t_provider_event SET process_status = 'FAILED', process_result = #{result}, " +
            "updated_at = NOW() WHERE provider = #{provider} AND event_id = #{eventId}")
    int markFailed(@Param("provider") String provider,
                   @Param("eventId") String eventId,
                   @Param("result") String result);
}
