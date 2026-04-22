# 参与贡献

> [English](CONTRIBUTING.en.md)

欢迎以缺陷修复、功能开发、文档完善、Skill 包贡献等形式加入 Actionow 社区。

## Issue 规范

| 前缀         | 用途                                                                       |
|--------------|----------------------------------------------------------------------------|
| `[bug]`      | 缺陷反馈，需附复现步骤、期望行为、实际行为、环境与日志                     |
| `[feat]`     | 功能请求，需描述使用场景（user story）                                     |
| `[docs]`     | 文档改进                                                                   |
| `[question]` | 使用问题，请优先检索 Discussions 与历史 Issue                              |
| `[rfc]`      | 架构或接口级提案，建议在 PR 前先发起 RFC 收集意见                          |

示例：`[bug][agent] SSE session 超时后未释放 Redis 锁`。

## 分支与提交

- 分支：`main`、`feature/<scope>-<short>`、`fix/<scope>-<short>`、`docs/<topic>`、`refactor/<scope>`。
- 提交遵循 Conventional Commits：`<type>(<scope>): <subject>`。scope 取模块名，例如 `agent`、`web`、`gateway`、`common-core`、`docker`。

```
feat(agent/hitl): ask_user_multi_choice 支持默认选项

当 prompt 中包含 defaultChoice 字段时，若用户超时未回复则自动采用默认值。
该改动可以在剧本创编场景下减少中断，同时保留人类覆盖能力。

Closes #123
```

## Pull Request

1. Fork 仓库并从 `main` 创建特性分支。
2. 编写代码并补充单元或集成测试。
3. 本地验证：`./mvnw -pl <module> -am verify` 或 `npm run lint && npm run build`。
4. 使用以下描述模板：

```markdown
## Summary
<一句话说明本 PR 做了什么>

## Motivation / Related Issue
Closes #<issue-id>

## Changes
- 改动点 1
- 改动点 2

## Testing
- [ ] 新增或更新单元测试
- [ ] 本地构建通过
- [ ] 手动验证步骤：...

## Checklist
- [ ] 遵循 Conventional Commits
- [ ] 代码通过 lint 与 format
- [ ] 文档已更新（README、模块文档、OpenAPI）
- [ ] 无破坏性 API 改动；若有请在描述中明确 BREAKING CHANGE
```

## 安全披露

请勿公开提交安全相关 Issue。请发送邮件至 `security@actionow.ai`，72 小时内响应。
