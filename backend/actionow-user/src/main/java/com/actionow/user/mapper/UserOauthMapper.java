package com.actionow.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.user.entity.UserOauth;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户 OAuth 绑定 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface UserOauthMapper extends BaseMapper<UserOauth> {

    /**
     * 根据提供商和提供商用户ID查询
     */
    @Select("SELECT * FROM t_user_oauth WHERE provider = #{provider} AND provider_user_id = #{providerUserId} AND deleted = 0")
    UserOauth selectByProviderAndProviderId(@Param("provider") String provider, @Param("providerUserId") String providerUserId);

    /**
     * 根据用户ID查询所有绑定
     */
    @Select("SELECT * FROM t_user_oauth WHERE user_id = #{userId} AND deleted = 0")
    List<UserOauth> selectByUserId(@Param("userId") String userId);

    /**
     * 根据用户ID和提供商查询
     */
    @Select("SELECT * FROM t_user_oauth WHERE user_id = #{userId} AND provider = #{provider} AND deleted = 0")
    UserOauth selectByUserIdAndProvider(@Param("userId") String userId, @Param("provider") String provider);

    /**
     * 统计用户绑定数量
     */
    @Select("SELECT COUNT(*) FROM t_user_oauth WHERE user_id = #{userId} AND deleted = 0")
    int countByUserId(@Param("userId") String userId);
}
