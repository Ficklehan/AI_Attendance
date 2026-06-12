# Review Dimensions — AttendanceAgent

Load only the sections relevant to the current diff.

## Backend (Spring Boot / MyBatis)

### Correctness
- [ ] Task status transitions: only valid edges (`processing`→`processed`|`failed`, `processed`→`confirmed`|`cancelled`, etc.)
- [ ] `TaskAccessService` / role checks on every task mutation and read-by-id
- [ ] `@Transactional` on multi-table writes (confirm, sync, export job creation)
- [ ] Recognition queue: concurrency guard, duplicate job handling, failure → `failed` status
- [ ] Feishu sync: field mapping per country, partial failure handling, retry idempotency
- [ ] Date/time: consistent timezone handling for attendance dates vs `LocalDateTime` storage

### Security
- [ ] Controller methods: `@PreAuthorize` or explicit permission service call
- [ ] MyBatis XML: `#{}` not `${}` for user input; dynamic ORDER BY whitelisted
- [ ] File upload: size limits, content-type validation, path under `uploads/` sandbox
- [ ] No secrets, API keys, or JWT defaults in committed YAML/SQL
- [ ] Admin bootstrap (`bootstrap-default-admin`) disabled in prod profiles
- [ ] Rate limiting on auth endpoints (`AuthRateLimitFilter`) not bypassed

### API design
- [ ] Response wrapper `Result<T>` with consistent `code` / `message`
- [ ] Error messages suitable for frontend `translateError` mapping
- [ ] Pagination: `current`, `size`, `total` aligned with `summary` counts when filtered
- [ ] New endpoints documented in controller JavaDoc or OpenAPI if project uses it
- [ ] Breaking field renames: migration + frontend + mini-program together

### Data & migrations
- [ ] New migration file numbered sequentially in `backend/config/migration/`
- [ ] Bootstrap runners (`*DatabaseBootstrap`) safe on re-run
- [ ] Index on foreign keys and frequent filter columns (`user_id`, `status`, `created_at`)
- [ ] JSON columns: schema documented; backward compatible parsing

### JDK 8 compatibility
- [ ] No `var`, records, `switch` expressions, text blocks
- [ ] No `List.of`, `Map.of`, `Optional.orElseThrow()` (no-arg), `String.isBlank()`
- [ ] Streams/lambdas OK; verify target `maven.compiler.source=1.8`

### Observability
- [ ] Errors logged with context (taskId, userId) without PII leakage
- [ ] Long-running jobs (recognition, export) have progress / status query path

---

## Frontend (Vue 3 / Element Plus)

### UX states
- [ ] Loading skeleton or spinner on async fetch
- [ ] Empty state with actionable CTA (not blank table)
- [ ] Error state with retry; uses `translateError` for API messages
- [ ] Destructive actions confirmed (delete task, cancel, bulk ops)

### Data consistency
- [ ] Task counts from `getTaskSummary()` / `tasks/summary`, not list `records.length`
- [ ] After confirm/delete/cancel: refresh summary **and** current list tab
- [ ] Admin banner when `allUsersScope === true`

### i18n
- [ ] New UI strings in `zh-CN.js` **and** `en-US.js` (same key path)
- [ ] Dates/numbers use locale-aware formatting where user-visible
- [ ] Status labels match backend enum semantics

### Design system
- [ ] Primary `#2563EB`; no purple AI-template gradients
- [ ] Business pages: dense cards, semantic status tags
- [ ] Follow `docs/design-system.md` component patterns

### API layer
- [ ] New calls in `frontend/src/api/*.js` with consistent error handling
- [ ] Pinia store updates don't duplicate server state unnecessarily
- [ ] Auth token attached via existing axios interceptor

### Performance
- [ ] Large tables: pagination or virtual scroll; avoid rendering 500+ rows
- [ ] Debounce search inputs; cancel in-flight requests on unmount if needed

---

## Feishu Mini-Program

- [ ] Parity with PC for core flows: upload → recognize → review → confirm
- [ ] Country gate (`countryGate.js`) respected before recognition
- [ ] i18n in `localeMessages.js` for new strings
- [ ] API base URL / auth header same pattern as existing pages
- [ ] `requiredRecordFields.js` aligned with country field config
- [ ] Image upload size and format constraints match backend

---

## Cross-cutting

### Multi-country
- [ ] Changes respect per-country prompt (`recognition_prompt`) and Feishu mapping (`feishu.md`)
- [ ] Country selector affects AI config and Bitable target, not hardcoded defaults

### Feishu integration
- [ ] OAuth redirect URIs match environment (`deploy/` rendered config for prod)
- [ ] `open_id` binding logic preserves single `user_id` across PC and mini
- [ ] Bitable write failures surfaced to user with actionable message

### Notifications / reminders (if touched)
- [ ] `content_vars` or localization keys stored for template rendering
- [ ] Scheduler idempotency; no duplicate deliveries for same rule + window
- [ ] Locale resolution matches `ReminderLocaleSupport` conventions

### Tests
- [ ] Critical business rules have unit test (`backend/src/test/`)
- [ ] Status transition and permission logic covered for new paths
- [ ] No tests that depend on external Feishu/MiMo in CI without mocks

### Config layering
- [ ] Secrets in `.env` only, not `application.yml`
- [ ] Business config in `base-config/` or DB, not hardcoded in Java
- [ ] Feature flags use existing `attendance.*` properties pattern
