/**
 * 图片生成响应映射模板（带 OSS 上传）v3.0 — 标准响应格式
 * 适用场景：调用外部 AI API 生成图片后，将结果上传到 OSS。
 *
 * 可用变量:
 * - response: API 原始响应
 * - inputs: 用户输入参数
 * - config: 提供商配置
 * - resp: ResponseHelper 响应处理辅助工具
 * - oss: OSS 操作绑定
 * - notify: 通知绑定
 * - json: JSON 工具
 * - log: 日志工具
 */

// 解析响应
def data = response?.data?[0] ?: response
def imageUrl = data?.url
def base64Data = data?.b64_json
def revisedPrompt = data?.revised_prompt

if (!imageUrl && !base64Data) {
    return [
        outputType: "MEDIA_SINGLE",
        status: "FAILED",
        error: [code: "NO_IMAGE", message: "No image URL or base64 data in response", retryable: false]
    ]
}

// 发送进度通知
notify.taskProgress(60, "正在上传生成结果...")

def ossUrl
def fileKey

if (imageUrl) {
    def ossResult = resp.uploadToOss(imageUrl, "IMAGE")
    ossUrl = ossResult.url
    fileKey = ossResult.fileKey
    log.info("Uploaded image from URL to: {}, fileKey: {}", ossUrl, fileKey)
} else {
    def fileName = oss.generatePath("images", "png")
    def uploadResult = oss.uploadBase64WithKey(base64Data, fileName, "image/png")
    ossUrl = uploadResult?.url ?: oss.uploadBase64(base64Data, fileName, "image/png")
    fileKey = uploadResult?.fileKey
    log.info("Uploaded image from base64 to: {}", ossUrl)
}

// 发送进度通知
notify.taskProgress(90, "生成完成，正在更新状态...")

// 构建标准响应
return [
    outputType: "MEDIA_SINGLE",
    status: "SUCCEEDED",
    media: [
        mediaType: "IMAGE",
        items: [[
            fileUrl: ossUrl,
            fileKey: fileKey,
            mimeType: "image/png",
            thumbnailUrl: ossUrl
        ]]
    ],
    metadata: [
        extra: [
            revisedPrompt: revisedPrompt,
            originalUrl: imageUrl
        ]
    ]
]
