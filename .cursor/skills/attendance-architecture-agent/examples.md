# Architecture Audit Examples

## Example 1 — Full audit with duplicate wheels (P0 drift)

```markdown
# Architecture Audit — Full system

## Executive summary
Cross-client validation is mostly anchored on `shared/js`, but date normalization has
a forked copy in `frontend/src/utils/recognizedDateNormalizer.js` with divergent
month-boundary handling. Backend does not mirror the shared rule for February 29th edge
cases. Recommend Phase 0 consolidation before adding new countries.

## Health scorecard
| Dimension | Grade | Top issue |
|-----------|-------|-----------|
| Topology & boundaries | B | `TaskService` approaching god-service size |
| Cross-client consistency | C | Date normalizer drift |
| Config layering | A | Clean four-layer separation |
| Standards compliance | B | Missing `en-US` keys for 3 new reminder strings |
| Duplicate wheels | C | 1 forked normalizer, 1 inline duplicate check in mini page |
| Evolution readiness | B | Good migration discipline; weak observability on export jobs |

## Topology (current state)
```mermaid
flowchart TB
  PC[frontend Vue3] --> API[/clockai/api]
  MP[feishu-miniprogram] --> API
  API --> SVC[Services]
  SVC --> MySQL[(MySQL)]
  SVC --> MIMO[MiMo API]
  SVC --> FS[Feishu API]
  SJ[shared/js/*.cjs] --> PC
  SJ --> MP_JS[shared-js/*.js]
  SJ -.->|should mirror| JAVA[Backend validators]
```

## Findings

### P0 — architectural risk
| Location | Category | Finding | Recommendation | Effort |
|----------|----------|---------|----------------|--------|
| `frontend/src/utils/recognizedDateNormalizer.js:44` | Cross-client drift | Implements own Feb-29 logic; differs from `shared/js/recognizedDateNormalizer.cjs:52` | Delete fork; import via `importSharedCjs` only | S |
| `backend/.../TaskService.java` (confirm path) | Missing mirror | Server accepts dates PC would reject | Add validation using shared test vectors in `TaskRecordPayloadResolverTest` | M |
| `feishu-miniprogram/pages/record-edit/index.js:210` | Duplicate wheel | Inline `isDuplicateRow()` duplicates `duplicateCheckCore` | Replace with `require('../../shared-js/duplicateCheckCore')` | S |

### P1 — standards drift
| Location | Category | Finding | Recommendation | Effort |
|----------|----------|---------|----------------|--------|
| `frontend/src/locales/zh-CN.js` | i18n | `reminder.rule.empty` missing in `en-US.js` | Add mirrored keys | S |
| `docs/architecture-and-config.md:183` | Doc drift | States migrations `001–006`; repo has `023_*` | Update doc range + link to migration README | S |

### P2 — improvement
| Location | Category | Finding | Recommendation | Effort |
|----------|----------|---------|----------------|--------|
| `TaskService.java` | Modularity | 920 lines, mixes export queue + confirm | Extract `ExportJobOrchestrator` | L |

## Duplicate wheel inventory
| Concept | Canonical | Duplicate(s) | Drift risk | Action |
|---------|-----------|------------|------------|--------|
| Date normalize | `shared/js/recognizedDateNormalizer.cjs` | `frontend/src/utils/recognizedDateNormalizer.js` | High | Remove fork |
| Duplicate row check | `shared/js/duplicateCheckCore.cjs` | `record-edit/index.js` inline | High | Use shared-js |
| Task payload | `shared/js/taskRecordPayload.cjs` | None | Low | OK |

## Standards drift matrix
| Constraint | Documented in | Observed | Status |
|------------|---------------|----------|--------|
| Validation in shared/js | project-report-sharing.md | 2 bypasses found | ❌ |
| Task counts from summary | data-consistency.md | All clients compliant | ✅ |
| JDK 8 only | architecture-and-config.md | No violations | ✅ |

## What is well-architected
- Config four-layer model consistently applied
- `permissions.json` drives both backend and PC menu
- Migration numbering sequential with idempotent bootstraps
- `importSharedCjs` pattern cleanly bridges CJS → ESM

## Phased roadmap
### Phase 0 — stabilize (3 days)
- Consolidate date normalizer and duplicate check
- Add backend mirror tests from shared vectors

### Phase 1 — consolidate (2 weeks)
- Split `TaskService` export/confirm concerns
- Sync locale gaps; automate shared/locales → client check in CI

### Phase 2 — evolve (1 month)
- Export job observability dashboard
- ADR for recognition queue extraction if volume grows

## Verification plan
- [ ] `rg "recognizedDateNormalizer" frontend/` returns only shared import path
- [ ] `TaskRecordPayloadResolverTest` includes Feb-29 cases from shared fixtures
- [ ] Mini record-edit uses `duplicateCheckCore` — manual submit duplicate row blocked on both PC and mini
```

---

## Example 2 — Module audit (`base-config` + permissions)

```markdown
# Architecture Audit — base-config & permissions

## Executive summary
Permission definitions are centralized in `permissions.json`, but two admin capabilities
are hardcoded in `RoleManagement.vue` and `PermissionService.java` with different
string keys. Config layering is otherwise clean.

## Health scorecard
| Dimension | Grade | Top issue |
|-----------|-------|-----------|
| Config layering | A- | One hardcoded capability key |
| Standards compliance | B | `permissions-by-country.json` partially overlaps `permissions.json` |

## Findings

### P1
| Location | Category | Finding | Recommendation | Effort |
|----------|----------|---------|----------------|--------|
| `PermissionService.java:88` | Inconsistent constraint | `canManageRoles` not in `permissions.json` | Add key to JSON; remove hardcode | S |
| `base-config/permissions-by-country.json` | Duplicate wheel | Overlaps global permissions for `recordCalibrate` | Document precedence; dedupe or merge | M |

## Verification plan
- [ ] All `hasPermission('...')` keys exist in `permissions.json`
- [ ] Grep `canManage|hasCapability` returns only JSON-backed keys
```

---

## Example 3 — Branch audit (architecture blast radius)

```markdown
# Architecture Audit — branch `feature/user-roles`

## Executive summary
Branch introduces `user_roles` table and `UserRoleService` — sound boundary.
Risk: `DataScopeService` and `RoleDataScopeService` now both filter by region;
callers must not double-apply scope. No shared-js impact.

## Findings

### P1
| Location | Category | Finding | Recommendation | Effort |
|----------|----------|---------|----------------|--------|
| `DataScopeService` + `RoleDataScopeService` | Boundary blur | Two services apply region filter | Document call order; add integration test for admin+role combo | M |
| `024_role_data_scope_work_region.sql` | Migration | No corresponding `init.sql` update | Add table to init.sql for greenfield | S |

## What is well-architected
- New migration follows numbering convention
- `UserRoleDatabaseBootstrap` matches existing bootstrap pattern
- JWT claims unchanged — role resolution at service layer
```
