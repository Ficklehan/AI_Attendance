# Attendance Agent — Design System (Product Clarity)

Evolution of **Clarity Ledger** into a product UI system: illustration-led key pages, dense business cards, clear status hierarchy, and flat minimal interactions.

## Principles

- **Key pages need atmosphere**: login, home, empty states, and recognition progress may use purpose-made light illustrations.
- **Business pages stay readable**: task/result/settings use dense cards, clear status tags, and fixed action areas.
- **One blue, controlled gradients**: primary remains `#2563EB`; gradients are limited to CTA and hero surfaces, not random decoration.
- **No AI-demo cues**: no emoji logos, no Robot-as-brand, no generic purple template styling.

## Color

| Token | Hex | Usage |
|-------|-----|--------|
| `primary` | `#2563EB` | Buttons, links, active nav |
| `primary-hover` | `#1D4ED8` | Button hover / pressed |
| `primary-muted` | `#EFF6FF` | Selected nav, tag bg |
| `page` | `#F8FAFC` | Business page background |
| `page-hero` | `#EAF3FF` → `#FFFFFF` | Key-page gradient background |
| `surface` | `#FFFFFF` | Cards, inputs |
| `border` | `#E5E7EB` | Dividers, card outline |
| `text` | `#111827` | Headings, body |
| `text-secondary` | `#6B7280` | Meta, captions |
| `text-muted` | `#9CA3AF` | Placeholder |
| `success` | `#059669` | Success state |
| `warning` | `#D97706` | Warning state |
| `error` | `#DC2626` | Error state |

## Typography

- Family: system UI stack (`-apple-system`, `SF Pro`, `Segoe UI`, sans-serif).
- Title: 20–22px / 600–700.
- Body: 14px (Web) / 28rpx (mini) / 400–500.
- Caption: 12–13px / `#6B7280`.

## Radius & shadow

- Card: `12px` (Web) / `24–30rpx` (mini).
- Button: `8px` / `24–28rpx`.
- Shadow: subtle slate shadow for cards; blue shadow only for primary CTA or hero surfaces.

## Components

- **Primary button**: blue CTA, optional restrained blue gradient on mini key flows.
- **Secondary**: white + `1px #2563EB` or `#BFDBFE`.
- **Stat card**: white + `3px` left border (semantic color).
- **Tag**: light tint bg + saturated text (see `design.ttss`).

## File map

| Platform | Tokens | Components |
|----------|--------|------------|
| Web | `frontend/src/styles/variables.scss` | `global.scss`, `PageShell.vue`, `StepGuide.vue`, `StatOverview.vue` |
| Mini | `feishu-miniprogram/styles/design.ttss` | `app-*` product components + existing `ios-*` compatibility |

## Mini program pages (Product Clarity)

| Page | Pattern |
|------|---------|
| 识别 `index` | Illustration hero + primary upload CTA + compact queue/tasks |
| 任务 `tasks` | Segmented board + dense task cards + swipe delete |
| 我的 `profile` | Profile dashboard + stat cards + action cards |
| 结果 `result` | Summary card + anomaly-first chips + dense review table |
| 识别中 `recognizing` | Branded processing illustration + steps + live progress |
| 设置 `settings` | Configuration cards + selectable country/language rows |
| 拍照 `camera` | Full-bleed preview, precise scanning frame, calm bottom controls |
| 登录 `login` | Portrait hero `attendance-login-hero-vertical.png` (1024×1280, `widthFix`) + floating login panel |
| 问答 `chat` | Assistant workspace with compact bubbles and fixed composer |

## Web pages

| Page | Pattern |
|------|---------|
| Home | `PageShell` + `StepGuide` + white upload card on `#F2F2F7` |
| Tasks | `PageShell` + filter bar + sync tags in table |
| Config / Audit | `PageShell` + `surface-card`, no purple gradients |
