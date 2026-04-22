/**
 * 通用请求构建模板 v3.0
 * 基于 InputSchema 自动构建请求体，无需硬编码字段名映射。
 *
 * 可用变量:
 * - inputs: Map<String, Object> 用户输入参数
 * - config: Map<String, Object> 提供商配置
 * - headers: Map<String, String> 自定义请求头
 * - req: RequestHelper Schema 驱动的请求构建辅助工具
 *
 * req.buildBody() 自动完成:
 * - 遍历 inputSchema 定义，按 apiFieldName / name 构建输出 key
 * - 自动应用 defaultValue
 * - 类型转换（NUMBER, BOOLEAN）
 * - 透传 schema 中未定义但 inputs 中存在的参数
 */

return req.buildBody()
