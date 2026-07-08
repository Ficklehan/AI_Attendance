# Attendance Architect — Templates

## Technical Design

```markdown
# [Feature Name] — Technical Design

> Status: draft | approved | implemented
> PRD: docs/requirements/[file].md
> Author / Date:

## 1. Goal & Acceptance

**Business goal:** [one sentence]

**Acceptance criteria:**
- [ ] Given … When … Then …
- [ ] …

**Non-goals:**
- …

**Clients:** PC | Mini-program | Both

## 2. Current State

[Mermaid diagram or bullet summary of existing modules]

**Constraints:**
- JDK 8, existing auth/permission model, …

## 3. Data Model

### New / changed tables

| Table | Purpose | Key columns |
|-------|---------|-------------|
| … | … | … |

**Migration:** `backend/config/migration/NNN_feature.sql`
**Bootstrap:** `FeatureDatabaseBootstrap.java` (yes/no)

### Indexes & constraints

- …

## 4. API Contract

| Method | Path | Auth | Permission | Description |
|--------|------|------|------------|-------------|
| POST | `/feature/create` | JWT | `featureKey` | … |

**Request / Response examples:**

```json
// POST /feature/create
{ "name": "…" }
```

**Error keys:** `feature.not_found`, …

## 5. Permissions & Data Scope

| Capability key | Roles | Data scope impact |
|----------------|-------|-------------------|
| `featureKey` | admin, custom | none / restricted by country |

## 6. Frontend

| Route | Component | Store / API |
|-------|-----------|-------------|
| `/settings/feature` | `FeaturePanel.vue` | `api/feature.js` |

**i18n keys:** `settings.feature.title`, …

## 7. Shared / Mini-program

- [ ] No shared change
- [ ] Update `shared/js/featureCore.cjs` + sync

## 8. Phased Delivery

| Phase | Scope | Verify |
|-------|-------|--------|
| MVP | … | … |
| P2 | … | … |

## 9. Risks & Rollback

| Risk | Mitigation | Rollback |
|------|------------|----------|
| … | … | Drop migration / disable flag |

## 10. Test Plan

- [ ] Unit: …
- [ ] API manual: …
- [ ] UI: loading / empty / error states
- [ ] Permission denied path
- [ ] Data scope isolation
```

---

## ADR

```markdown
# ADR-NNN: [Decision Title]

- **Status:** proposed | accepted | deprecated | superseded by ADR-XXX
- **Date:** YYYY-MM-DD
- **Context:** What problem or constraint triggered this?

## Decision

What we chose and why.

## Alternatives Considered

| Option | Pros | Cons |
|--------|------|------|
| A | … | … |
| B | … | … |

## Consequences

**Positive:** …
**Negative:** …
**Follow-up:** …
```

---

## Implementation Plan

```markdown
# [Feature] — Implementation Plan

> Design: [link to technical design]
> Target branch: feature/…

## Checklist

### Backend
- [ ] Migration NNN
- [ ] Bootstrap (if needed)
- [ ] Mapper + XML
- [ ] Service + tests
- [ ] Controller + DTOs
- [ ] permissions.json key

### Frontend
- [ ] api/module.js
- [ ] View / component
- [ ] Route + settingsAccess guard
- [ ] zh-CN + en-US locales

### Shared
- [ ] shared/js update + sync

### Docs
- [ ] requirements status → implemented
- [ ] data-consistency / architecture if invariant changed

## Verification Commands

```bash
bash scripts/mvn-jdk8.sh test -Dtest=FeatureTest -q
cd frontend && npm run build
```

## Manual Test Script

1. Login as admin → …
2. Login as restricted role → verify scope …
```

---

## Architecture Review (lightweight)

```markdown
# Architecture Review — [scope]

## Verdict
[APPROVE | APPROVE WITH CONDITIONS | NEEDS REDESIGN]

## Context diagram

```mermaid
flowchart LR
  …
```

## Findings

| Severity | Area | Finding | Recommendation |
|----------|------|---------|----------------|
| P0 | Security | … | … |
| P1 | Data | … | … |
| P2 | UX | … | … |

## Phased recommendation

1. …
2. …
```
