/**
 * 轮询响应映射模板 v3.0 — 标准响应格式
 * 适用于异步 AI 模型 API 的轮询状态检查。
 * 使用 resp (ResponseHelper) 消除与 generic-response-mapper 的代码重复。
 *
 * 可用变量:
 * - inputs: Map<String, Object> 原始请求输入
 * - config: Map<String, Object> 提供商配置（含 pollingConfig）
 * - response: Object 轮询响应数据
 * - resp: ResponseHelper 响应处理辅助工具
 * - json: JsonBinding JSON工具
 * - extras.pollMode: true
 * - extras.externalTaskId: 外部任务ID
 * - extras.externalRunId: 外部运行ID
 *
 * 返回格式 (StandardResponse)
 */

// 1. 错误检查
def error = resp.checkError(response)
if (error) {
    error.metadata = [
        externalTaskId: extras?.externalTaskId,
        externalRunId: extras?.externalRunId,
        raw: response
    ]
    return error
}

// 2. 解析状态（支持 pollingConfig 自定义状态映射）
def pollingConfig = config.pollingConfig ?: [:]
def status = resp.resolveStatus(
    response,
    pollingConfig.successStatuses?.toList() ?: ['succeeded'],
    pollingConfig.failedStatuses?.toList() ?: ['failed'],
    pollingConfig.statusPath ?: '$.status'
)

def taskId = resp.extractTaskId(response) ?: extras?.externalTaskId
def runId = resp.extractRunId(response) ?: extras?.externalRunId

// 3. 非终态：直接返回等待/运行中状态
if (status in ["PENDING", "RUNNING"]) {
    return [
        outputType: "MEDIA_SINGLE",
        status: status,
        metadata: [externalTaskId: taskId, externalRunId: runId, raw: response]
    ]
}

// 4. 失败状态：提取错误信息
if (status == "FAILED") {
    return [
        outputType: "MEDIA_SINGLE",
        status: "FAILED",
        error: [
            code: resp.extractErrorCode(response),
            message: resp.extractErrorMessage(response) ?: "任务执行失败",
            retryable: false
        ],
        metadata: [externalTaskId: taskId, externalRunId: runId, raw: response]
    ]
}

// 5. 成功状态：构建媒体响应（自动提取 URL / 推断类型 / 上传 OSS）
def mediaResult = resp.buildMediaResponse(response)
if (mediaResult) {
    // 补充任务元数据
    mediaResult.metadata = mediaResult.metadata ?: [:]
    mediaResult.metadata.externalTaskId = taskId
    mediaResult.metadata.externalRunId = runId
    return mediaResult
}

// 6. 成功但无媒体：尝试文本
def textResult = resp.buildTextResponse(response)
if (textResult) {
    textResult.metadata = textResult.metadata ?: [:]
    textResult.metadata.externalTaskId = taskId
    textResult.metadata.externalRunId = runId
    return textResult
}

// 7. Fallback
return [
    outputType: "MEDIA_SINGLE",
    status: "SUCCEEDED",
    metadata: [externalTaskId: taskId, externalRunId: runId, raw: response]
]
