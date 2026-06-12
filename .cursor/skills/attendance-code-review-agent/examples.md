# Code Review Examples

## Example 1 — REQUEST CHANGES (data scope leak)

**Context**: New endpoint lists notifications without user filter.

```markdown
# Code Review — feature/notifications

## Verdict
**REQUEST CHANGES** — one blocker: missing user scope on list query allows cross-user data access.

## Summary
| Severity | Count |
|----------|-------|
| blocker  | 1 |
| major    | 1 |
| minor    | 0 |
| nit      | 0 |

## Findings

### blocker
| Location | Finding | Suggestion |
|----------|---------|------------|
| `NotificationController.java:45` | `listAll()` returns every user's notifications without `userId` filter | Add `WHERE user_id = #{userId}` in mapper; verify admin-only path uses explicit `allUsers` flag like tasks API |

### major
| Location | Finding | Suggestion |
|----------|---------|------------|
| `NotificationDrawer.vue:88` | Fetches list but never refreshes unread badge after mark-read | Call `fetchUnreadCount()` in `onMarkRead` success handler |

## What went well
- DTO shape is consistent with existing `Result<Page<T>>` pattern
- Migration `012_notification_content_vars.sql` is backward compatible (nullable column)

## Test plan
- [ ] Login as user A, verify cannot see user B notifications
- [ ] Admin scope (if applicable) shows all users
- [ ] Mark read updates badge without full page reload
```

---

## Example 2 — APPROVE WITH COMMENTS

```markdown
# Code Review — fix/summary-refresh

## Verdict
**APPROVE WITH COMMENTS** — correct fix for summary drift; one major i18n gap.

## Summary
| Severity | Count |
|----------|-------|
| blocker  | 0 |
| major    | 1 |
| minor    | 2 |
| nit      | 1 |

## Findings

### major
| Location | Finding | Suggestion |
|----------|---------|------------|
| `frontend/src/locales/zh-CN.js` | Key `notifications.empty` added only in zh-CN | Mirror in `en-US.js` |

### minor
| Location | Finding | Suggestion |
|----------|---------|------------|
| `TaskList.vue:212` | `loadSummary` duplicated in two watchers | Extract `refreshTaskData()` composable |

### nit
| Location | Finding | Suggestion |
|----------|---------|------------|
| `UserNotificationService.java:67` | Log message mixes Chinese and English | Use English for server logs per existing convention |

## What went well
- Correctly uses `GET /tasks/summary` after confirm — aligns with `docs/data-consistency.md`
- Transaction boundary on batch mark-read is appropriate

## Test plan
- [ ] Confirm task → home CTA count decrements without manual refresh
- [ ] Switch locale EN → empty state shows English copy
```

---

## Example 3 — Subagent merge (bugbot + security)

When subagents return findings, dedupe and present unified table:

```markdown
## Findings (includes Bugbot + Security Review)

### blocker
| Location | Finding | Suggestion |
|----------|---------|------------|
| `UserNotificationMapper.xml:28` | `${orderBy}` allows SQL injection | Whitelist allowed columns; use `#{}` only |
| `ReminderSchedulerService.java:156` | Race: duplicate delivery if two pods run same cron | Use DB unique constraint on (rule_id, user_id, window_start) or distributed lock |

### major
| Location | Finding | Suggestion |
|----------|---------|------------|
| `NotificationLocalizationService.java:42` | Null `contentVars` crashes render for legacy rows | Fallback to `LegacyNotificationVarsRebuilder` (partially implemented — complete migration path) |
```

---

## Example 4 — Scoped file review (no git diff)

User: "Review `backend/.../ReminderSchedulerService.java`"

Skip git commands. Read the file and its direct callers/tests. Report still uses same severity table but **Verdict** references file scope only.

---

## Anti-patterns in review output

**Bad** (vague):
> The notification code could be improved.

**Good** (actionable):
> `NotificationController.markRead` does not validate `notification.userId == principal.id` before update — add ownership check or return 403.

**Bad** (style-only as blocker):
> Variable `n` should be `notification`.

**Good** (nit):
> Rename `n` → `notification` for readability (`NotificationDrawer.vue:34`).
