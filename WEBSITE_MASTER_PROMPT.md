# Trackify Website — Master Prompt / Living Spec

> **Purpose of this file:** a single, complete, self-contained brief that an AI agent (or a
> human developer) can read top-to-bottom and use to build the **Trackify web app** with true
> feature parity with the Android app. It defines the product, every feature and its exact
> behavior, the data model, business rules, design system, screens, and acceptance criteria.
>
> **THIS IS A LIVING DOCUMENT.** Any change to the Trackify Android app — a new feature, a
> behavior change, a new data field, a removed feature — **must** be reflected here in the
> same change set. Update the relevant section, tick/untick the parity table, bump the
> "Spec version" and add a row to the changelog at the bottom. An out-of-date master prompt
> is a bug.

---

## 1. Product definition

**Trackify — Smart Expense Tracker (web).** A local-first personal finance web app for
tracking expenses and income. All data lives **on the user's device/browser** (IndexedDB or
localStorage — see [Data layer](#7-data-layer)); nothing is sent anywhere unless the user
explicitly turns on the optional Google Sheets backup sync.

- **Tone:** friendly, modern, minimal. Dark & light theme.
- **Audience:** a single user tracking their own money. No multi-user accounts, no server
  database, no login required (see [Authentication](#8-authentication--privacy)).
- **Local-first rule:** every write goes to local storage first and works fully offline.
  Sync/backup is a best-effort, opt-in layer on top — never a blocker.
- **Money is not float-displayed raw**: amounts are stored as numbers, always displayed via
  the currency formatter (see [Formatting rules](#8-formatting-rules)).

## 2. Target platform & stack (recommended)

- **Framework:** Next.js 14+ (App Router) with TypeScript. (React + Vite is an acceptable
  fallback if the agent justifies it — keep the spec, not the framework, as the contract.)
- **Styling:** Tailwind CSS, Material 3-flavored tokens (exact palette in §5).
- **Charts:** Recharts (or equivalent SVG chart lib) — no paid services.
- **Storage:** IndexedDB (via Dexie.js or idb) for the four data tables; localStorage for
  small preference key/values (see §7). Optional: export/import via File System Access API
  or download/upload fallbacks.
- **PWA:** installable (manifest + service worker), works fully offline, Lighthouse ≥ 90 on
  Performance/Accessibility/Best Practices/SEO.
- **No backend required** for core functionality. The only network calls are the optional
  Google Sheets sync (direct to the user's own Apps Script Web App, exactly like mobile)
  and nothing else. No analytics, no telemetry, no cookies beyond local prefs.
- **Ads:** the Android app shows AdMob ads; the website ships **without ads** by default
  (documented decision — don't port AdMob). Keep the "after-save interstitial" behavior
  as a lightweight "saved ✓" confirmation toast instead.

## 3. Mobile-only features (do NOT port, or replace deliberately)

| Android feature | Web replacement / decision |
|---|---|
| Shake-to-add overlay banner (accelerometer) | Replace with: a **⌘K / Ctrl+K quick-add palette** and a `n` keyboard shortcut + a FAB on mobile. Keep the same 3-step flow (§6.9). |
| Lock-screen / other-app overlay | N/A on web. The quick-add palette covers the same intent. |
| Home-screen AppWidget, Quick Settings tile | N/A; the PWA install covers "one tap from launcher". |
| AdMob banner / native / interstitial | Ship ad-free on web (see §2). |
| Receipt OCR (ML Kit camera) | Optional stretch: use the Web Image Capture API + on-device OCR if trivially available; otherwise out of scope v1. |
| Voice capture (SpeechRecognizer intent) | Optional stretch: use the Web Speech API (`webkitSpeechRecognition`), same parser rules as §6.8. Out of scope v1. |
| Local budget/recurring system notifications | Web: in-app notification center + (optional, stretch) Web Push when permission is granted. v1: in-app banner + badge. |
| AdMob & its test ad units | Skip entirely. |

Everything else in this document **is in scope** and must reach parity.

## 4. Guiding principles for the agent (read before writing code)

1. **Port behavior, not screens.** Every screen must reproduce the business rules in §7
   exactly — when this file and "what looks reasonable" disagree, this file wins.
2. **Don't invent features.** Build exactly what's here; anything not listed is out of
   scope (§14). If something is genuinely ambiguous, make the smallest reasonable choice,
   mark it with a `// SPEC-GAP:` comment, and list the gaps in your final summary.
3. **Empty states are features.** Every screen must be designed for zero data (first run).
4. **Local-first always wins.** No feature may require the network; sync (§12) is opt-in
   and must fail gracefully offline.
5. **One source of truth for money math.** Put totals/savings-rate/health-score logic in
   one well-tested module and reuse it everywhere — never re-derive per screen.
6. **Keep the spec in sync.** When you add/change a feature, update this file in the same
   commit (§15).

## 5. Design system (exact tokens)

The Android app uses Material 3 with a violet primary. Port these tokens to CSS variables
(support light + dark; values below are the light theme — derive dark variants with the same
hue family and equivalent contrast ratios):

| Token | Light value | Usage |
|---|---|---|
| `primary` | `#7C3AED` (deep violet) | Primary actions, active nav, links |
| `on-primary` | `#FFFFFF` | Text/icons on primary |
| `primary-container` | `#EDE9FE` | Filled chips, selected card backgrounds |
| `on-primary-container` | `#2E1065` | Text on primary-container |
| `secondary` | `#D97706` (warm amber) | Accent, income highlight |
| `secondary-container` | `#FEF3C7` | Income badges |
| `tertiary` | `#0EA5E9` (sky blue) | Charts, analytics accents |
| `background` | `#FAF8FF` | App background |
| `on-background` / `on-surface` | `#1A0A3D` | Body text |
| `surface` | `#FFFFFF` | Cards, sheets, dialogs |
| `surface-variant` | `#EDE9FE` | Keypad keys, subtle fills |
| `on-surface-variant` | `#5B21B6` | Secondary text (violet-tinted) |
| `surface-container` | `#F5F0FF` | Elevated cards, list rows |
| `error` | `#DC2626` | Errors, delete actions |
| `outline` / `outline-variant` | `#A78BFA` / `#DDD6FE` | Borders, dividers |
| `success` | `#10B981` | Synced status, positive deltas |
| `warning` | `#F59E0B` | Budget warning state |

**Category colors** (badge/chip/icon background per category — fixed, not theme-dependent):

| Category | Hex |
|---|---|
| Food | `#FF6B6B` |
| Travel | `#4ECDC4` |
| Shopping | `#45B7D1` |
| Bills | `#FFA07A` |
| Health | `#66BB6A` |
| Entertainment | `#BA68C8` |
| Education | `#42A5F5` |
| Other | `#90A4AE` |

Goal cards have a user-chosen color, default `#22C55E`.

**Shape & elevation:** cards 16px radius (small 12, large 24), keypad keys 16px, buttons 12px,
12px base padding, 24px screen padding. Soft shadows only (Material-style tonal elevation).

**Typography:** one family (Inter or Roboto), scale: display 28sp-equivalent, headline 22,
title 18, body 15, body-small 13, caption 11.

## 6. Data model (must match the Android SQLite schema 1:1)

Four tables + preference keys. Column names here match the Android DB exactly so a future
export/import round-trip is lossless.

**expenses**

| Column | Type | Notes |
|---|---|---|
| id | INTEGER PK auto-increment | stable local ID, also used for idempotent sync |
| title | TEXT NOT NULL | falls back to category when remarks left blank (§6.9) |
| amount | REAL NOT NULL | > 0 validated |
| category | TEXT NOT NULL | one of the 8 fixed categories |
| date | TEXT NOT NULL | `yyyy-MM-dd` (ISO, local) |
| notes | TEXT DEFAULT '' | free text (a.k.a. remarks) |
| sync_status | TEXT DEFAULT 'pending' | `pending` / `synced` / `failed` |
| payment_method | TEXT DEFAULT '' | e.g. Cash / UPI / Card (free text chip) |

**income**

| Column | Type | Notes |
|---|---|---|
| id | INTEGER PK auto-increment | |
| title | TEXT NOT NULL | |
| amount | REAL NOT NULL | |
| source | TEXT NOT NULL | e.g. Salary / Freelance (free text) |
| date | TEXT NOT NULL | `yyyy-MM-dd` |
| notes | TEXT DEFAULT '' | |
| type | TEXT DEFAULT 'income' | `income` or **`transfer`** |

**recurring**

| Column | Type | Notes |
|---|---|---|
| id | INTEGER PK auto-increment | |
| title | TEXT NOT NULL | |
| amount | REAL NOT NULL | |
| category | TEXT NOT NULL | |
| day_of_month | INTEGER NOT NULL | 1–31 (clamp to month length when materializing) |
| notes | TEXT DEFAULT '' | |
| is_subscription | INTEGER DEFAULT 0 | 1 = subscription (feeds the subscription total) |

**goals**

| Column | Type | Notes |
|---|---|---|
| id | INTEGER PK auto-increment | |
| name | TEXT NOT NULL | |
| target_amount | REAL NOT NULL | |
| saved_amount | REAL DEFAULT 0 | |
| color | TEXT DEFAULT '#22C55E' | hex, user-chosen |
| deadline | TEXT DEFAULT '' | `yyyy-MM`, optional |

**Preference keys (localStorage):** `user_prefs.name`, `user_prefs.onboarding_done`,
`budget_prefs.monthly_budget` (0 = unset), `currency_prefs.code`
(INR/USD/EUR/GBP/AED/SGD → display locale mapping), `pin_prefs.*` (optional app lock),
`reminder_prefs.recurring_days_before` (default 2),
`reminder_prefs.budget_alert_threshold` (default 90),
`recent_categories` (last-used categories for chips),
`merchant_memory` (title→last-used-category suggestions).

## 7. Data layer & business rules (exact behavior — implement to the letter)

1. **Transfers are not income.** Income entries with `type = 'transfer'` appear in the
   Income list (with a transfer badge) but are **excluded from every income total** —
   dashboard income, analytics, savings rate, health score. This is a deliberate,
   user-facing rule; a regression here is a launch blocker.
2. **Recurring auto-materialization.** On every app load (and on dashboard render), for each
   recurring rule: if `today.day_of_month >= rec.day_of_month` AND no expense with the same
   `title` exists in the current `yyyy-MM` month, create an expense dated
   `yyyy-MM-{day clamped to month length}` (title/amount/category/notes copied from the
   rule). If `today.day_of_month < rec.day_of_month`, show a "due in N days / due today"
   reminder badge in the Recurring screen.
3. **Budget alerts (once per month).** If a monthly budget is set and
   `monthExpenses / budget >= threshold%` (default 90), fire **one** alert (in-app
   notification) per `year-month` — tracked via a fired-alerts set so it never repeats.
4. **Recurring reminders** fire `N` days before `day_of_month` (default 2), once per rule
   per month.
5. **Expense title fallback.** When saving, if `title` is blank use `category`.
6. **Validation.** Amount must parse to a number > 0; category is required (fixed list);
   date defaults to today.
7. **Sync status lifecycle.** Every new/edited expense row starts `pending`; on successful
   Sheets push becomes `synced`; on failure `failed`. Status shows as a small chip in the
   transactions list. Sync is retried on app load and after each save; retries are
   **idempotent via the row's stable local id** (the Apps Script upserts by it — see
   GOOGLE_SHEETS_SETUP.md).
8. **Recent categories.** On save, record the category in a most-recent list (max ~5) shown
   as quick chips in the add-expense form.
9. **Merchant memory.** When the user types a title previously used, auto-suggest the
   category they picked last time for that title (all on-device).
10. **Financial health score** (dashboard, 0–100, label: Excellent ≥ 80, Good ≥ 60,
    Fair ≥ 40, Needs Work < 40):
    - Budget component (0–40): if budget set — `monthTotal/budget`: ≤0.7 → 40, ≤0.85 → 30,
      ≤1.0 → 20, ≤1.2 → 10, else 0; if no budget → 20.
    - Savings component (0–40): if `monthIncome > 0` — savings rate `(income − expenses)/income`:
      ≥0.3 → 40, ≥0.2 → 35, ≥0.1 → 25, ≥0.0 → 15, else 0; if no income → 20.
    - Activity component (0–20): lifetime expense count ≥20 → 20, ≥10 → 15, ≥5 → 10, >0 → 5.
    - Clamp to 0–100.

## 8. Formatting rules

- Currency display via `Intl.NumberFormat(locale, { style: 'currency', currency: code })`
  using the code→locale map: INR→en-IN, USD→en-US, EUR→de-DE, GBP→en-GB, AED→ar-AE,
  SGD→en-SG. Changing the currency in Settings re-formats **every** displayed amount
  instantly (no stored-formatted values).
- Dates stored ISO `yyyy-MM-dd`, displayed per user locale; month labels for charts from
  the user locale.
- Percentages: round to whole percents (e.g. `Saving 32% of income this month`).

## 9. Authentication & privacy

- No accounts, no server auth, no cookies for tracking. App lock (optional): a 4-digit PIN
  gate on load if enabled in Settings (hash-stored locally), matching mobile's PIN lock
  including wrong-attempt counter and "forgot PIN" reset (with confirmation).
- All data stays in browser storage; "Clear All Data" wipes everything after a typed
  confirmation dialog.

## 10. Screens & information architecture (parity with the Android app)

Navigation: desktop = persistent sidebar; mobile = bottom nav (Home, Transactions,
**+ Add** (center FAB), Income, Analytics) + a top app bar with Settings gear and a
Search icon. Routes:

### 10.1 Onboarding (`/onboarding`, once)
Single card: "Welcome to Trackify!" → name input ("What should we call you?") →
"Get Started →". Skipped if `onboarding_done`. Name shown on the dashboard greeting.

### 10.2 Dashboard (`/`)
Greeting ("Good Morning/Afternoon/Evening 👋, {name}"), then cards in order:
1. **Totals row:** Today / This Week / This Month / This Year expense totals (cards).
2. **Financial Overview:** Expenses (month), Income (month, transfers excluded),
   Net Saved, and "Saving X% of income this month".
3. **Budget card:** monthly budget with progress bar; states — not set ("Tap to set your
   monthly budget"), on-track ("On track — N% used"), warning ("Warning — N% used" at
   ≥ threshold), exceeded ("Over budget by {formatted amount}!").
4. **Smart Insight:** one rule-based line — spending up/down vs last month (%), savings
   rate, over-income warning, top category suggestion, or welcome line for empty data.
5. **Financial Health:** animated 0–100 score + label + "Based on budget, savings & activity".
6. **7-Day Spending chart** (bar, per-day totals, tertiary color; empty state copy).
7. **Category Breakdown** (current month): rows with icon, name, amount, percent,
   progress bar in category color; tap → filtered transactions.
8. **Savings Goals** summary card ("N goal(s) · {amount} saved", "View Goals →") with
   progress + "Goal complete!" state.
9. **Recent Transactions** (last 5): icon, title, date, amount (+ sync chip), tap → edit.

### 10.3 Transactions (`/transactions`)
Full expense list grouped by date; each row: category icon, title, notes (if any), date,
payment-method chip, amount, sync-status chip. Features: search box (§10.4), filter by
category, sort (by date / by amount), swipe-to-delete (or hover/kebab delete) **with undo
snackbar**, inline edit (opens `/transactions/:id/edit`), empty state
("No Expenses Found — Start tracking your spending by adding your first expense."),
CSV export of the current view.

### 10.4 Natural-language search (transactions search box)
Parse — case-insensitive — into filters, remainder = substring match on title/notes:
- `above N` / `over N` / `> N` → min amount; `below N` / `under N` / `< N` → max amount.
- Category names (`food`, `travel`, …) → category filter.
- `today`, `yesterday` → exact-day filter; month names/abbrevs
  (january…december) → that month of the current year; `yyyy` → that year.
- The full grammar and month list live in `NaturalSearchParser.kt` (mobile) — port it
  verbatim (both full names and 3-letter abbrevs, with "sept").
- Example: "food above 500 in august" → Food, min 500, August, remainder ignored.
  A filter that matches nothing must return zero rows (it's a real filter).

### 10.5 Analytics (`/analytics`)
Header totals (This Month), **Spending Overview** donut by category, **Top Category** and
**Avg/Day** stat cards, per-category stat rows (icon, amount, share %, progress), and the
7-day chart. All empty-state-safe ("No spending data yet").

### 10.6 Income (`/income`)
Income list with rows (source icon, title, date, amount in secondary color) and a
**total (all-time, income-type only)** header. Add/edit form: title, amount, source,
date, notes, and the **Income/Transfer toggle** (see rule §7.1). Delete with undo.

### 10.7 Recurring (`/recurring`)
Rules list (title, category icon, amount, "Every month on the {n}th", subscription badge,
due-soon badge). Add/edit: title, amount, category, day of month, notes, **subscription
checkbox**. Header shows **subscription totals**: monthly and annual sums of
subscription-flagged rules. Due rules are auto-materialized per §7.2.

### 10.8 Goals (`/goals`)
Goal cards with name, saved/target (formatted), progress bar in the goal's color, deadline
chip, "Add Money" (quick amount dialog), edit, delete ("Goal deleted" undo), and a
"Goal complete! 🎉" state at 100%. Empty state: "No savings goals yet. Tap + to create
your first!"

### 10.9 Add / Edit flows
- **Add Expense** (`/add` — also opened by FAB, `n`/`⌘K` palette, and any quick-capture):
  title (with merchant-memory suggestion), amount (required > 0), category grid/chips
  (8 fixed + recent chips), date (default today), notes, payment-method chips
  (Cash/UPI/Card/NetBanking/Other — free text allowed), **Save** → toast "Expense saved!"
  → confirmation (no interstitial ad on web) → dashboard refresh. Edit reuses the form.
- **Add Income** (`/add-income`): title, amount, source, Income/Transfer toggle, date, notes.
- **Quick Add 3-step banner/palette** (port of the mobile shake banner, §2): step 1 amount
  on a **built-in keypad** (soft tonal keys, haptic on capable devices, currency symbol
  from prefs, leading-zero/double-dot guards, max 9 chars) → step 2 category (icon list,
  3-dot step indicator, ‹ Back) → step 3 optional remarks (summary line
  "₹500 · Food", full-width Done). Cancel/✕/Esc at every step; saves via the same rules
  (§7). On web this opens as a centered modal (desktop) / bottom sheet (mobile).
- **Add chooser bottom sheet** ("What would you like to add?"): Expense / Income /
  Add by Voice (stretch) / Scan Receipt (stretch).

### 10.10 Settings (`/settings`)
Sections: **Profile** (name edit dialog), **Quick Expense** (see §11 — web keyboard
shortcut info instead of shake), **Security** (optional PIN app lock: enable/change/
disable with confirmations), **Currency** (6-code picker, instant re-format app-wide),
**Data** (Export CSV, Import CSV, Backup JSON, Restore JSON, Clear All Data — all with
confirmations where destructive), **Google Sheets Sync** (§12), **About** (version,
tagline).

## 11. Data tools (exact formats)

- **CSV export** — header `Title,Amount,Category,Date,Notes,PaymentMethod`, one row per
  expense, current filter applied, downloaded as `trackify_expenses.csv`.
- **CSV import** — same columns; extra/missing columns tolerated where possible; each
  imported row is validated (amount > 0, category normalized to the fixed list or mapped
  to Other); summary toast "Imported N expenses" + "Failed M rows" when applicable.
- **PDF report** — monthly/yearly report: per-category totals with category colors, a
  simple bar visualization, and a full transaction table; generated client-side
  (jsPDF or print-stylesheet), filename `trackify_report_{period}.pdf`.
- **JSON backup** — `{ app: 'Trackify', version: <dbVersion>, exportedAt: ISO, expenses: [],
  income: [], recurring: [], goals: [] }` — raw column names per §6, downloadable as
  `trackify_backup.json`.
- **Restore** — validates shape before overwriting; asks for typed confirmation
  ("This will replace your current data"). Partial restores are not allowed.

## 12. Google Sheets sync (optional, mirrors mobile)

- Settings: Web App URL + Access Token (user's own Apps Script deployment — see
  `GOOGLE_SHEETS_SETUP.md`), **Test Connection** button (surfaces the script's response
  verbatim, including errors), **Sync Now**.
- Behavior: on save, rows are queued (`pending`); sync pushes pending/failed rows; script
  upserts by the row's local id (idempotent); on success mark `synced`, on failure `failed`
  with retry on next load/save. Sync status chips visible in the transactions list
  ("Synced" / "Pending sync" / "Sync failed").
- No credentials are ever stored outside the user's own browser; the URL/token are local
  prefs. CORS is the user's Apps Script concern (documented in the setup guide).

## 13. Acceptance criteria (definition of done)

1. All §10 screens exist, match the design tokens (§5), and handle empty data gracefully.
2. Every business rule in §7 is implemented exactly, with unit tests (at minimum:
   transfer exclusion, recurring materialization + clamping, health score table, NL search
   grammar, currency re-formatting, title fallback).
3. Offline-first: full CRUD works with the network disabled; PWA installable.
4. CRUD round-trip: create → appears in lists/dashboard → edit → delete (with undo).
5. Currency switch re-formats every amount app-wide instantly.
6. NL search behaves exactly as §10.4's example.
7. Data tools round-trip: backup → wipe → restore reproduces identical data; CSV
   import/export verified.
8. Lighthouse ≥ 90 on Performance / Accessibility / Best Practices / SEO.
9. Responsive at 360px and 1440px; keyboard-navigable; focus-visible styles; ARIA labels
   on icon-only buttons.
10. Dark/light theme follows system by default with a manual override persisted.

## 14. Out of scope (v1, web)

Voice capture and receipt OCR (stretch items §3), AdMob, real push notifications,
multi-currency per-transaction storage, multi-user/multi-account, travel mode,
location-based categorization, shared/debt-splitting beyond personal share,
back-tap gestures. (Mirrors the Android app's deliberate exclusions.)

## 15. Maintenance protocol for this file

- **Rule:** every change to the Trackify Android app's features/behavior — and every
  feature added to the website — must update this file in the same commit. If a section
  here disagrees with the shipped app, the app is the source of truth and this file must
  be corrected immediately.
- When updating: edit the relevant section, update the parity table below, add a row to
  the changelog, and bump the spec version.
- Suggested commit message: `spec: <what changed>`.

**Feature parity matrix** (keep updated — ✅ = in web app, 🚫 = deliberately not ported,
⬜ = planned):

| Feature | App | Web |
|---|---|---|
| Dashboard (totals, budget, insight, health, 7-day chart, breakdown, goals, recent) | ✅ | ⬜ |
| Expenses CRUD + list + undo delete | ✅ | ⬜ |
| Income CRUD + transfer exclusion | ✅ | ⬜ |
| Recurring rules + subscriptions + auto-add | ✅ | ⬜ |
| Savings goals | ✅ | ⬜ |
| Monthly budget + alerts (90% default) | ✅ | ⬜ |
| Natural-language search | ✅ | ⬜ |
| Merchant memory + recent categories | ✅ | ⬜ |
| Quick add 3-step flow (keypad → category → remarks) | ✅ (shake/overlay) | ⬜ (⌘K palette) |
| CSV export/import, JSON backup/restore, PDF report | ✅ | ⬜ |
| Google Sheets sync (idempotent, status chips) | ✅ | ⬜ |
| Currency picker (6 codes) | ✅ | ⬜ |
| PIN app lock | ✅ | ⬜ |
| Onboarding + name greeting | ✅ | ⬜ |
| Dark/light theme | ✅ | ⬜ |
| Shake gesture | ✅ | 🚫 (⌘K / `n` instead) |
| AdMob ads | ✅ | 🚫 |
| Lock-screen overlay / widgets / QS tile | ✅ | 🚫 |
| Voice / receipt scan | ✅ | 🚫 v1 (stretch later) |

## 16. Spec changelog

| Date | Version | Change |
|---|---|---|
| 2026-09-05 | 1.0 | Initial spec extracted from the Android app (full parity brief). |




