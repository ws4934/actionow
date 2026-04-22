package com.actionow.ai.service;

import com.actionow.ai.dto.AssetInfo;
import com.actionow.ai.dto.schema.InputFileConfig;
import com.actionow.ai.dto.schema.InputParamDefinition;

import java.util.List;
import java.util.Map;

/**
 * 素材输入解析服务
 * 用于预处理 AI 模型执行请求中的素材输入
 * 将素材ID转换为URL或Base64格式
 *
 * @author Actionow
 */
public interface AssetInputResolver {

    /**
     * 解析并转换输入中的素材引用
     *
     * @param inputs 原始输入参数（包含素材ID）
     * @param inputSchema 输入参数定义列表
     * @return 解析后的输入参数（素材ID已转换为URL或Base64）
     */
    Map<String, Object> resolveAssetInputs(Map<String, Object> inputs, List<InputParamDefinition> inputSchema);

    /**
     * 解析单个素材ID为URL
     *
     * @param assetId 素材ID
     * @param fileConfig 文件配置
     * @return 素材URL
     */
    String resolveAssetToUrl(String assetId, InputFileConfig fileConfig);

    /**
     * 解析单个素材ID为Base64
     *
     * @param assetId 素材ID
     * @param fileConfig 文件配置
     * @return Base64编码的数据
     */
    String resolveAssetToBase64(String assetId, InputFileConfig fileConfig);

    /**
     * 批量获取素材信息
     *
     * @param assetIds 素材ID列表
     * @return 素材信息Map（assetId -> AssetInfo）
     */
    Map<String, AssetInfo> batchGetAssetInfo(List<String> assetIds);

    /**
     * 解析单个素材
     * 根据 InputFileConfig 配置决定返回 URL 或 Base64
     *
     * @param assetId 素材ID
     * @param fileConfig 文件配置
     * @return 解析后的值（URL字符串或Base64字符串）
     */
    Object resolveAsset(String assetId, InputFileConfig fileConfig);

    /**
     * 解析素材列表
     * 根据 InputFileConfig 配置决定返回 URL列表 或 Base64列表
     *
     * @param assetIds 素材ID列表
     * @param fileConfig 文件配置
     * @return 解析后的列表
     */
    List<Object> resolveAssetList(List<String> assetIds, InputFileConfig fileConfig);
}
