# Duplication Hunt Playbook

Use during architecture audits to detect **重复造轮子** (reinventing the wheel) and **cross-client drift**.

## When to run

- Full architecture audit (mandatory)
- Module audit touching `frontend/`, `feishu-miniprogram/`, `shared/`, or validation-related backend
- User explicitly asks about duplication or consistency

## Step 1 — Inventory canonical sources

```bash
# Shared business logic modules
ls -1 shared/js/*.cjs | xargs -n1 basename

# Mini mirrors
ls -1 feishu-miniprogram/shared-js/*.js 2>/dev/null | xargs -n1 basename

# Shared locales
find shared/locales -name '*.json' | head -40
```

Record gaps: `.cjs` without `.js` mirror, or mirror filename mismatch.

## Step 2 — Concept keyword map

For each shared module, identify **search keywords** and run ripgrep across all layers:

| Shared module | Search keywords |
|---------------|-----------------|
| `duplicateCheckCore` | `duplicateCheck`, `isDuplicate`, `findDuplicate` |
| `taskRecordPayload` | `taskRecordPayload`, `buildPayload`, `confirmed_data` |
| `recognizedDateNormalizer` | `normalizeDate`, `recognizedDate`, `parseAttendanceDate` |
| `recognizedTimeNormalizer` | `normalizeTime`, `recognizedTime` |
| `workingCountrySetupCore` | `workingCountry`, `promptCountry`, `setWorkingCountry` |
| `recordFieldFormatRules` | `fieldFormat`, `formatRule`, `validateField` |
| `requiredRecordFields` | `requiredField`, `requiredRecord` |
| `employeeMatchCore` | `matchEmployee`, `employeeMatch` |
| `workerNoNormalize` | `workerNo`, `normalizeWorker` |
| `translateError` / errors | `translateError`, `errorCode`, `ERROR_` |

```bash
# Example: hunt parallel date normalization
rg -l "normalizeDate|recognizedDate|parseDate" \
  shared/js frontend/src feishu-miniprogram backend/src \
  --glob '!node_modules' --glob '!*.test.*'
```

## Step 3 — Classify each hit

| Classification | Criteria | Action |
|----------------|----------|--------|
| **Canonical** | Lives in `shared/js` or official mirror | None |
| **Adapter** | Thin wrapper importing shared; no duplicate logic | Document as OK |
| **Backend mirror** | Java class tests against shared contract | Verify test parity |
| **Duplicate wheel** | Reimplements logic; divergent behavior possible | P1 finding |
| **Orphan** | Logic only in one client; others use shared | P0/P1 drift risk |

## Step 4 — Deep compare suspicious pairs

When two files implement the same concept:

1. Read both; list **behavioral differences** (not just syntax)
2. Check git history — was one forked from the other?
3. Check if PC uses `importSharedCjs` or copy-paste
4. Check if backend enforces the stricter or looser rule

Report format:

```markdown
| Concept | Canonical | Duplicate | Diff summary | Risk |
|---------|-----------|-----------|--------------|------|
| Date normalize | shared/js/recognizedDateNormalizer.cjs | frontend/src/utils/recognizedDateNormalizer.js | PC re-exports shared via Vite — OK | Low |
```

## Step 5 — Locale duplication

```bash
# Keys in PC locales not in shared
rg -o '"[a-zA-Z0-9_.]+"\s*:' frontend/src/locales/zh-CN.js | head -5

# Compare locale file counts per language
for loc in zh-CN en-US; do
  echo "=== $loc ==="
  wc -l shared/locales/common/$loc.json \
        shared/locales/pc/$loc.json \
        frontend/src/locales/$loc.js 2>/dev/null
done
```

Flag:

- Same string defined in both `shared/locales` and client-only file with different text
- Mini `localeMessages.js` out of sync with `shared/locales/miniprogram/`

## Step 6 — Backend mirror audit

Shared rules **must** be enforced server-side. Hunt Java reimplementations:

```bash
rg -l "duplicate|normalize|validateRecord|fieldFormat" \
  backend/src/main/java/com/attendance \
  --glob '!*Test*'
```

Cross-check against:

- `TaskRecordPayloadResolver`
- `FeishuUserProfileResolver`
- Service-level validation in `TaskService`, `EmployeeService`
- Test classes mirroring shared scenarios

Missing backend mirror for a client-only rule = **P0** (security / data integrity).

## Step 7 — Permission & config duplication

```bash
rg "recordCalibrate|hasPermission|canExport" frontend backend base-config
rg "admin" backend/src/main/java --glob '*Permission*' --glob '*Auth*'
```

Flag hardcoded permission strings not in `permissions.json`.

## Output: duplicate wheel inventory table

Always include in architecture report:

```markdown
## Duplicate wheel inventory

| # | Concept | Canonical | Duplicate(s) | Classification | Drift risk | Recommended action |
|---|---------|-----------|--------------|----------------|------------|-------------------|
| 1 | ... | shared/js/foo.cjs | frontend/... | duplicate wheel | High | Consolidate into shared |
```

**Effort estimates:** S = <1 day, M = 1–3 days, L = >3 days or cross-team coordination.
