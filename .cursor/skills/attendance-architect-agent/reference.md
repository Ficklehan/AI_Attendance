# Attendance Architect — Reference

## Module Map

```
backend/src/main/java/com/attendance/
├── controller/     # REST — thin, delegate to services
├── service/        # Business logic, transactions
├── mapper/         # MyBatis interfaces
├── dto/            # request/ response DTOs
├── config/         # Bootstraps, CountryCatalog, security
├── security/       # JWT, AdminAuthService
└── util/           # Shared helpers

frontend/src/
├── api/            # axios wrappers per domain
├── views/          # Page components
├── stores/         # Pinia (auth, country)
├── composables/    # Reusable logic
├── constants/      # capabilityDefs, languageOptions
└── locales/        # zh-CN.js, en-US.js, ...

feishu-miniprogram/
├── pages/          # Mini-program pages
├── utils/          # API, locale, country gate
└── shared-js/      # Copied from shared/js (do not edit directly)
```

## API Conventions

**Base path:** `/attendance/api`

**Response envelope:**
```json
{
  "code": 200,
  "data": { },
  "message": "optional",
  "messageKey": "error.key.for.i18n",
  "messageArgs": [],
  "timestamp": 1234567890
}
```

**Headers:**
| Header | Purpose |
|--------|---------|
| `Authorization: Bearer <jwt>` | Auth |
| `X-Country` | Working country (PC) |
| `X-Locale` | Response locale hint |

**Mutation style:** Prefer `POST .../update`, `POST .../delete` over REST DELETE.

## Controller Index

| Prefix | Domain |
|--------|--------|
| `/auth` | Login, current user |
| `/feishu-auth` | OAuth |
| `/tasks` | Task lifecycle, summary, records |
| `/employees` | Employee master data |
| `/users` | User admin |
| `/roles` | System roles + members |
| `/permissions` | Functional permission bundle |
| `/data-scope` | Role data scope config + `/me` |
| `/config` | Country, prompts, Feishu |
| `/exports` | Async Excel jobs |
| `/reminder-rules` | Task reminder config |
| `/audit` | Audit logs |
| `/notifications` | In-app notifications |
| `/local` | Image upload/serve |
| `/agency-billing` | Billing reports |

## Database Patterns

**New table checklist:**
1. Add to `backend/config/init.sql` (greenfield)
2. Add migration `backend/config/migration/NNN_feature.sql` (existing DB)
3. Optional `*DatabaseBootstrap.java` with `@Order(N)` for idempotent DDL
4. MyBatis mapper + XML under `resources/mapper/`

**Common columns:** `id BIGINT AUTO_INCREMENT`, `created_at`, `updated_at`, soft status enums as VARCHAR.

**Recent migration themes:** 009–010 (RBAC scope), 015 (working_country), 020 (employees), 023 (user_role), 024 (work_region dimension).

## Bootstrap Runners (@Order)

| Order | Class | Purpose |
|-------|-------|---------|
| 15 | ExportJobDatabaseBootstrap | export_jobs table |
| 20 | PromptDatabaseBootstrap | recognition_prompt seed |
| — | UserRoleDatabaseBootstrap | user_role table |
| — | RoleDataScopeDatabaseBootstrap | role_data_scope tables |
| — | DefaultAdminBootstrap | dev admin (conditional) |

## Permission Keys (`permissions.json`)

| Key | Typical scope |
|-----|---------------|
| `tasks` | Task list/edit |
| `country` | Working country switch |
| `aiConfig` | AI prompts |
| `feishuConfig` | Feishu Bitable mapping |
| `users` | User management |
| `audit` | Audit logs |
| `recordCalibrate` | Record calibration (mini) |
| `taskDeleteConfirmed` | Delete confirmed tasks |
| `reminderConfig` | Reminder rules |
| `employees` | Employee management |

Add new admin features here + `frontend/src/constants/capabilityDefs.js` + locales.

## Data Scope Dimensions

| Dimension | Applies to | Example rule value |
|-----------|------------|-------------------|
| `owner_user` | tasks | specific user IDs |
| `country` | task_records | CN, FR |
| `warehouse` | task_records | warehouse codes |
| `agency` | task_records | agency codes |
| `work_region` | employees | region codes |

Resolution: `DataScopeService.resolveForCurrentUser()` → union across roles; any `all` → full access.

## Shared JS Sync

When changing cross-client logic:

```bash
# Edit source
vim shared/js/featureCore.cjs

# Sync to miniprogram
npm run sync:miniprogram-shared

# Frontend imports via
frontend/src/utils/importSharedCjs.js
```

## Config Layers

1. `backend/.env` — secrets
2. `application.yml` — framework
3. `base-config/` — business editable config
4. MySQL — runtime state (tasks, prompts, roles)

## Task State Machine

```
processing → processed → confirmed
              ↓    ↓
           failed  cancelled
```

User labels: 识别中 / 待核对 / 已完成 / 失败 / 已作废

## Multi-Country

- `CountryCatalog` — canonical country codes
- Per-country: `recognition_prompt`, `feishu.md` sections, optional `permissions-by-country.json`
- `tasks.prompt_country` snapshots country at recognition
- Default when unset: **FR**

## Dev Commands

```bash
./start.sh all                    # backend + frontend
bash scripts/mvn-jdk8.sh test     # backend tests (JDK 8)
cd frontend && npm run dev        # :5175
cd frontend && npm run build      # production build
```

## Doc Update Triggers

| Change | Update |
|--------|--------|
| New status or summary field | `docs/data-consistency.md` |
| New config layer or bootstrap | `docs/architecture-and-config.md` |
| New feature shipped | `docs/requirements/<feature>.md` status |
| Breaking API | requirements + SOP if user-facing |
