# Contributing

> [中文](CONTRIBUTING.md)

Bug fixes, feature work, documentation improvements, and Skill contributions are all welcome.

## Issue conventions

| Prefix       | Use                                                                  |
|--------------|----------------------------------------------------------------------|
| `[bug]`      | Bug report. Include reproduction steps, expected and actual behavior, environment, and logs. |
| `[feat]`     | Feature request. Describe the use case (user story).                 |
| `[docs]`     | Documentation changes.                                               |
| `[question]` | Usage questions. Search Discussions and existing issues first.       |
| `[rfc]`      | Architecture or interface-level proposal. Open an RFC before the PR. |

Example: `[bug][agent] SSE session does not release the Redis lock after timeout`.

## Branches and commits

- Branching: `main`, `feature/<scope>-<short>`, `fix/<scope>-<short>`, `docs/<topic>`, `refactor/<scope>`.
- Commits follow Conventional Commits: `<type>(<scope>): <subject>`. Use the module name as the scope, for example `agent`, `web`, `gateway`, `common-core`, `docker`.

```
feat(agent/hitl): support default option in ask_user_multi_choice

When the prompt includes a defaultChoice field and the user does not respond
within the timeout, the default value is used automatically. This reduces
interruptions in screenwriting workflows while preserving human override.

Closes #123
```

## Pull requests

1. Fork the repository and create a feature branch from `main`.
2. Write code and add unit or integration tests.
3. Verify locally: `./mvnw -pl <module> -am verify` or `npm run lint && npm run build`.
4. Use the following description template:

```markdown
## Summary
<one sentence describing what this PR does>

## Motivation / Related Issue
Closes #<issue-id>

## Changes
- Change 1
- Change 2

## Testing
- [ ] Added or updated unit tests
- [ ] Local build passes
- [ ] Manual verification steps: ...

## Checklist
- [ ] Follows Conventional Commits
- [ ] Code passes lint and format
- [ ] Documentation updated (README, module docs, OpenAPI)
- [ ] No breaking API changes; if any, mark BREAKING CHANGE in the description
```

## Security disclosure

Please do not file public issues for security vulnerabilities. Email `security@actionow.ai`; we respond within 72 hours.
