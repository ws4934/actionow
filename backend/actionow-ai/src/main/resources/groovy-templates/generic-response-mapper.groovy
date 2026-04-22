/**
 * 通用响应映射模板 v3.0 — 标准响应格式
 * 使用 resp (ResponseHelper) 消除重复的字段名猜测逻辑。
 *
 * 可用变量:
 * - inputs: Map<String, Object> 原始请求输入
 * - config: Map<String, Object> 提供商配置
 * - response: Object 原始响应数据
 * - resp: ResponseHelper 响应处理辅助工具
 * - json: JsonBinding JSON工具
 *
 * 返回格式 (StandardResponse):
 * {
 *   "outputType": "MEDIA_SINGLE" | "MEDIA_BATCH" | "TEXT_CONTENT",
 *   "status": "SUCCEEDED" | "FAILED" | "PENDING" | "RUNNING",
 *   "media": { mediaType, items: [{fileUrl, mimeType, ...}] },
 *   "textContent": "...",
 *   "error": { code, message, retryable },
 *   "metadata": { externalTaskId, externalRunId, raw }
 * }
 */

// 1. 错误检查（空响应、API error 字段）
def error = resp.checkError(response)
if (error) return error

// 2. 尝试构建媒体响应（自动提取 URL / 推断类型 / 上传 OSS）
def mediaResult = resp.buildMediaResponse(response)
if (mediaResult) return mediaResult

// 3. 尝试构建文本响应
def textResult = resp.buildTextResponse(response)
if (textResult) return textResult

// 4. 无法识别的输出格式，返回状态 + 原始数据
return [
    outputType: "MEDIA_SINGLE",
    status: resp.resolveStatus(response),
    metadata: [
        externalTaskId: resp.extractTaskId(response),
        externalRunId: resp.extractRunId(response),
        raw: response
    ]
]
