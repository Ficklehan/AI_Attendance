# Architecture Dimensions — AttendanceAgent

Load only sections relevant to audit scope. Each dimension ends with **drift signals** — grep patterns or symptoms that suggest a problem.

---

## 1. System topology & module boundaries

### Containers (must be identifiable)

- [ ] PC (`frontend/`), mini (`feishu-miniprogram/`), API (`backend/`) are the three runtime clients
- [ ] All clients talk to single API prefix `/clockai/api`
- [ ] MySQL is system of record for tasks, users, prompts, exports
- [ ] MiMo and Feishu are external; failures must degrade gracefully (task status, retry)

### Backend layering

- [ ] **Controller** — HTTP, DTO mapping, auth annotations only
- [ ] **Service** — business rules, transactions, orchestration
- [ ] **Mapper/XML** — persistence; no business logic
- [ ] **Util** — pure helpers; no Spring bean side effects unless justified

**Violations to flag:**

| Anti-pattern | Example |
|--------------|---------|
| Controller calls Mapper directly | Skips access control / transaction |
| Service imports `HttpServletRequest` | Leaks web layer into domain |
| God service > 800 lines | `TaskService` doing export + Feishu + recognition |
| Circular service dependency | A → B → A without event/interface break |

### Frontend layering

- [ ] `api/` — HTTP only
- [ ] `stores/` — session / global state
- [ ] `composables/` — reusable UI logic
- [ ] `views/` — page composition
- [ ] `utils/` — pure helpers; business rules should prefer `shared/js`

### Mini-program layering

- [ ] `utils/` — thin wrappers; core rules in `shared-js/`
- [ ] `pages/` — UI + orchestration
- [ ] `config.js` / `config.prod.js` — env only, not business rules

**Drift signals:** `rg "Mapper\\." backend/src/main/java/com/attendance/controller` · service files with `RestTemplate` + SQL in same method

---

## 2. Cross-client consistency (critical)

### Single sources of truth

| Concern | Canonical | Consumers |
|---------|-----------|-----------|
| Record validation / duplicate check | `shared/js/*.cjs` | PC via Vite, mini via `shared-js/`, backend Java mirror |
| Task payload shape | `shared/js/taskRecordPayload.cjs` | All clients + `TaskRecordPayloadResolver` |
| Working country setup | `shared/js/workingCountrySetupCore.cjs` | PC + mini |
| i18n common strings | `shared/locales/common/` | PC + mini + code generators |
| PC-only UI strings | `shared/locales/pc/` → `frontend/src/locales/` |
| Mini UI strings | `shared/locales/miniprogram/` → `localeMessages.js` |
| Task counts | `GET /tasks/summary` | All clients |
| Permissions | `base-config/permissions.json` | Backend `PermissionService` + PC menu |

### Sync contract

```
shared/js/foo.cjs  ──►  frontend (importSharedCjs via Vite plugin)
                  ──►  feishu-miniprogram/shared-js/foo.js (must stay in sync)
                  ──►  backend Java equivalent for server-side enforcement
```

- [ ] Every `shared/js/*.cjs` has mini counterpart (or documented exception)
- [ ] PC does not reimplement shared logic inline in views
- [ ] Backend enforces rules server-side (client validation is UX only)
- [ ] Locale keys exist in all required locale files for user-facing features

**Drift signals:**

```bash
# Files in shared/js without mini mirror
comm -23 <(ls shared/js/*.cjs | xargs -n1 basename | sed 's/.cjs//') \
         <(ls feishu-miniprogram/shared-js/*.js 2>/dev/null | xargs -n1 basename | sed 's/.js//')

# Inline validation bypassing shared
rg "function.*(normalize|validate|duplicate)" frontend/src/views feishu-miniprogram/pages --glob '!*test*'
```

---

## 3. Config layering

Four layers — **never mix**:

1. **Secrets** — `.env`, `deploy/secrets`, JWT, API keys
2. **Framework** — `application.yml`, `application-dev.yml`
3. **Business files** — `base-config/*.md`, `permissions.json`
4. **Runtime DB** — `recognition_prompt`, tasks, `plugin_config` (legacy)

- [ ] No secrets in committed YAML/SQL/JS
- [ ] `attendance.bootstrap-default-admin` only in dev profile
- [ ] Business config not hardcoded in Java when `base-config/` is the declared source
- [ ] `ConfigPathResolver` paths documented if new lookup added
- [ ] Country-specific AI/Feishu config uses country key consistently (CN, FR, …)

**Drift signals:** `rg "sk-|api_key|password\s*=" --glob '!*.example' --glob '!node_modules'` · `bootstrap-default-admin: true` outside dev yml

---

## 4. Data & API contracts

### State machine (invariant)

Valid transitions only. User label `processed` = **待核对 / Pending review**.

- [ ] No client invents new status values
- [ ] Confirm/cancel/sync paths match `docs/data-consistency.md`

### API consistency

- [ ] `Result<T>` wrapper with stable `code` / `message`
- [ ] Error codes mappable by `translateError` (PC + mini)
- [ ] Pagination fields: `current`, `size`, `total` — `total` must match summary when same filter
- [ ] Admin `allUsersScope` honored in list, summary, export
- [ ] Breaking DTO changes ship with migration + all clients

### Database

- [ ] Migrations numbered sequentially in `backend/config/migration/`
- [ ] `init.sql` updated for greenfield installs when migrations add tables
- [ ] Bootstrap runners (`*DatabaseBootstrap`) idempotent
- [ ] FK columns indexed (`user_id`, `status`, `created_at`)

**Drift signals:** clients counting `records.length` · `rg "status\s*=\s*['\"]pending['\"]"` (non-standard enum) · migration number gaps

---

## 5. Security & access architecture

- [ ] JWT on all mutating REST endpoints
- [ ] `TaskAccessService` on every task read/mutation by ID
- [ ] `PermissionService` + `permissions.json` for feature gates
- [ ] `RoleDataScope` / `UserRole` patterns consistent with data scope docs
- [ ] File upload sandboxed under `uploads/`
- [ ] Feishu OAuth links `feishu_user_id` for account unification

**Architectural smells:**

- Permission check only in frontend router (no backend gate)
- Admin capabilities inferred from hardcoded role string in multiple places
- Data scope filter missing in one query path (list has it, export doesn't)

---

## 6. Development standards

### JDK 8 (backend)

- [ ] No Java 9+ APIs: `var`, records, `List.of`, `String.isBlank()`, text blocks
- [ ] `maven.compiler.source=1.8` preserved

### i18n

- [ ] New user-facing keys in `zh-CN` + `en-US` minimum
- [ ] Shared concepts use `shared/locales/common/`
- [ ] Status labels align with backend enum semantics

### UI / design system

- [ ] Primary `#2563EB`; semantic status tags per `design-system.md`
- [ ] No purple AI-template gradients on business pages

### Error handling

- [ ] API errors use codes, not raw stack traces to client
- [ ] Server logs: English, with `taskId`/`userId` context, no PII

### Testing architecture

- [ ] Critical shared logic has tests in `backend/src/test` mirroring `shared/js` cases
- [ ] Country-specific behavior covered per catalog entry

**Drift signals:** `rg "List\\.of|var |record " backend/src/main` · locale key in one file only

---

## 7. Duplicate wheels & reinvention

### Known canonical modules (extend when adding)

| Module | Purpose |
|--------|---------|
| `duplicateCheckCore.cjs` | Duplicate row detection |
| `taskRecordPayload.cjs` | Record JSON shape |
| `workingCountrySetupCore.cjs` | Country selection persistence |
| `recognizedDateNormalizer.cjs` | Date parsing |
| `recognizedTimeNormalizer.cjs` | Time parsing |
| `recognizedTextNormalizer.cjs` | Text cleanup |
| `recordFieldFormatRules.cjs` | Field format validation |
| `fieldFormatHints.cjs` | Format hints for UI |
| `fieldPlaceholder.cjs` | Placeholder text logic |
| `requiredRecordFields.cjs` | Required field resolution |
| `confirmValidationGrouping.cjs` | Confirm-step grouping |
| `employeeMatchCore.cjs` | Employee matching |
| `workerNoNormalize.cjs` | Worker number normalization |
| `recognitionMarkCore.cjs` | Recognition marks |
| `taskWorkRegionCore.cjs` | Work region logic |

### Reinvention patterns to flag

| Pattern | Prefer instead |
|---------|----------------|
| New date parser in `frontend/src/utils` | Extend `shared/js/recognizedDateNormalizer.cjs` |
| Copy-paste validation in mini page | Import from `shared-js/` |
| Third error translation map | Extend `translateError` + shared error codes |
| New permission boolean in code only | Add to `permissions.json` + backend |
| Custom task count from list | `GET /tasks/summary` |
| Ad-hoc Feishu field mapping in service | `base-config/feishu.md` + country key |
| New `*Util.java` overlapping existing resolver | Extend `TaskRecordPayloadResolver` or shared contract |

### Acceptable duplication

- **UI adapters** — formatting for display if business outcome unchanged
- **Platform glue** — `importSharedCjs`, Vite plugin, mini `require` wrapper
- **Backend mirror** — Java reimplementation **if** kept in sync with shared tests

---

## 8. Integration architecture

### MiMo recognition

- [ ] Streaming path with timeout and failure → `failed` status
- [ ] Concurrency guard on recognition jobs
- [ ] Country prompt from `recognition_prompt` table, not hardcoded

### Feishu sync

- [ ] Field mapping per country from `feishu.md`
- [ ] Partial failure handling; `sync_status` on task
- [ ] Retry idempotency (`_feishuRecordId` or lookup by task+worker)

### Export

- [ ] Async job with `allUsersScope` persisted
- [ ] Retention per `export.retention-days`

**Drift signals:** hardcoded `bitable` tokens in Java · sync without country context

---

## 9. Observability & operability

- [ ] Long-running jobs queryable (recognition, export)
- [ ] Audit log for admin config changes
- [ ] Startup bootstrap order documented (`@Order` on bootstraps)
- [ ] `deploy/` render path for prod env separation

---

## 10. Documentation & ADR hygiene

- [ ] `docs/architecture-and-config.md` matches actual ports, paths, bootstrap
- [ ] `docs/data-consistency.md` matches API behavior
- [ ] Contradictions between docs flagged with recommended single source
- [ ] Major architectural decisions have ADR or section in architecture doc
- [ ] `base-config/README.md` lists all config files

**Drift signals:** doc references migration `001–006` but repo has `023_*` · README port mismatch with `vite.config.js`

---

## 11. Evolution readiness

### Modularity score questions

- Can we change recognition provider without touching Feishu sync?
- Can we add a country without editing >3 places?
- Can we test validation without spinning up UI?

### Tech debt indicators

- [ ] Dead code paths (`mobile/` if unused)
- [ ] `plugin_config` writes for new features (legacy)
- [ ] Multiple auth flows without unified session model
- [ ] Feature flags missing for risky migrations

### Scalability notes (proportionate to product)

- Recognition queue: single-node vs future queue
- Export: disk growth under `exports/`
- DB: tasks table size, index strategy for admin all-user queries
