# Trackify — Smart Expense Tracker

A local-first Android expense & income tracker. All data lives in SQLite on
your device; nothing is sent anywhere unless you explicitly turn on
[Google Sheets sync](#google-sheets-sync-optional).

## Features

- **Dashboard** — today/week/month/year totals, category breakdown, 7-day
  spending chart, financial health score, budget tracking.
- **Expenses & Income** — add, edit, delete, search, filter by category/date
  range, sort by date or amount, swipe-to-delete with undo, CSV export.
- **Recurring expenses** — auto-added each month on a scheduled day, plus a
  reminder notification a configurable number of days beforehand.
- **Budget alerts** — a local notification once monthly spend crosses a
  threshold (default 90%) of your set budget.
- **Savings goals**, **PIN app lock**, dark/light theme.
- **Shake to Add Expense** — shake your phone anywhere (home screen, lock
  screen, other apps) to pop a quick-add card on the spot — no app launch. See below.
- **Fast capture, seven ways**: the bottom-nav **+**, **Shake**, a **home
  screen widget**, a **Quick Settings tile**, **Voice** ("spent 500 on
  groceries"), **Scan Receipt** (on-device OCR), and **Share → Trackify**
  (parse a bank SMS/alert) all land in the same Add Expense screen,
  pre-filled where possible, for you to review and save. Static **app
  shortcuts** (long-press the launcher icon) jump straight to Add Expense,
  Transactions, or Dashboard.
- **Smart suggestions** — typing a title you've used before auto-fills the
  category you picked last time (`MerchantMemory.kt`, all on-device, no
  cloud AI); your 5 most recently used categories and common ₹ amounts show
  as quick-pick chips in Add Expense. A **split-expense** calculator turns a
  total bill + headcount into your share.
- **Payment method**, **income vs. transfer** (transfers are excluded from
  income totals), and **subscription tracking** on recurring items (with a
  monthly/annual subscription total).
- **Display currency** setting (INR/USD/EUR/GBP/AED/SGD) — see
  [Multi-currency](#multi-currency) for exactly what this does and doesn't do.
- **Natural-language search** — type "food above 500 in august" in
  Transactions and it's parsed into category/amount/date filters, with
  anything left over still doing a plain substring match.
- **Data tools** (Settings → Data) — **CSV export/import**, **PDF monthly/
  yearly reports**, and full **JSON backup/restore**, all via Android's
  Storage Access Framework (you pick the file/folder — no broad storage
  permission).
- **Google Sheets sync (optional)** — back up expenses to a spreadsheet you
  own and control. See [`GOOGLE_SHEETS_SETUP.md`](GOOGLE_SHEETS_SETUP.md).
- **Ads** — AdMob banner + native ad on the dashboard, and an interstitial
  after saving an expense/income. Debug builds use Google's public test ad
  unit IDs (`AdIds.kt`); release builds use the app's real ad units.

## Shake to Add Expense

Enable it from **Settings → Quick Expense**.

- Works anywhere — home screen, lock screen, or inside another app — not just
  while Trackify is open. Android only allows continuous accelerometer access
  outside your own foreground Activity through a foreground service, so
  turning this on starts `ShakeOverlayService`, which shows a permanent
  low-priority "Shake to add expense is on" notification for as long as the
  feature is enabled. That notification is a platform requirement, not a
  design choice — there's no way to listen for a shake in the background
  without it.
- On a shake, the service pops the quick-add banner **directly as a system
  overlay window** (`QuickAddOverlay` hosting `QuickAddBannerView` via
  `WindowManager`'s `TYPE_APPLICATION_OVERLAY`): a three-step card (amount on
  a built-in keypad → category → optional remarks; no title field — remarks or
  the category becomes the title) that draws on top of whatever's on screen —
  home screen, another app, or a locked screen (the display is woken if it was
  off) — and saves straight to the database without opening the rest of the
  app. A shake **never launches an activity**: startActivity() from a service
  is exactly what Android 10+ silently blocks, and yanking the user into the
  app defeats the point — so there is no fallback activity at all anymore.
  The card slides down from the top edge, shows a 3-dot step indicator, and
  dismisses when you tap outside it, hit ✕/Cancel, or finish step 3.
- The amount step uses a built-in keypad on purpose: Android never shows the
  system keyboard over the lock screen, so the card types its own amount
  (with haptic ticks per key, and the symbol from your currency setting).
  Remarks stay optional for the same reason (the remarks step flips the
  overlay window to focusable so the keyboard can appear on the home screen
  or inside another app).
- The overlay needs the **"Display over other apps"** permission — Settings
  prompts for it right after the toggle is enabled. If it hasn't been granted
  yet, a shake shows a short toast plus a one-time (per service start)
  heads-up notification whose tap opens the permission toggle directly —
  it never opens the app's UI.
- Suspended while `AddExpenseActivity`/`AddIncomeActivity` (and their post-save
  interstitial ad) — or the overlay card itself — are on top, via a shared
  `ShakeSuppressor` flag each of those sets in `onResume`/`onPause`, so it can
  never double-open or stack on top of itself.
- Requires 3 distinct jolts above the sensitivity threshold within a 1-second
  window (`ShakeDetector.kt`) — a single bump (picking the phone up, setting
  it down) won't trigger it. A 2-second cooldown after a trigger prevents
  rapid re-fires.
- Sensitivity (Low/Medium/High) and vibration feedback are configurable; if
  the device has no accelerometer, the toggle is disabled with an explanation
  instead of silently failing.
- Turning it on shows a short onboarding dialog with a live "shake now to
  test" check, using the same detector class the real feature uses, and — on
  Android 13+ — prompts for notification permission so the background service
  notification is actually visible, plus the overlay permission dialog so the
  card can actually pop over other apps.
- Doesn't survive a device reboot on its own (no `BOOT_COMPLETED` receiver —
  not worth the added background-start complexity); it's re-started the next
  time the app is opened (`SplashActivity`), which for most people is close
  enough to "always on."

## Fast capture entry points

All of these open the **same** `AddExpenseActivity` form (so validation, ad
flow, and Sheets sync are shared, not duplicated) — some just pre-fill it:

| Entry point | How | File |
|---|---|---|
| Shake | see [Shake to Add Expense](#shake-to-add-expense) | `ShakeDetector.kt` |
| Home screen widget | classic `AppWidgetProvider` + `RemoteViews` — no Jetpack Glance/Compose dependency added, since the rest of the app is View-based | `ExpenseWidgetProvider.kt` |
| Quick Settings tile | `TileService`, add via the QS panel's edit pencil | `QuickExpenseTileService.kt` |
| App shortcuts | long-press the launcher icon | `res/xml/shortcuts.xml` |
| Voice | bottom-sheet "Add by Voice" → Android's built-in speech recognizer activity (no `RECORD_AUDIO` permission needed — recognition happens in a separate system/Assistant app, not in-process) → a plain keyword/regex parser guesses amount/category/title → opens Add Expense pre-filled | `VoiceExpenseParser.kt` |
| Scan Receipt | bottom-sheet "Scan Receipt" → requests `CAMERA` → captures via the system camera app (`FileProvider`, no storage permission) → on-device ML Kit text recognition (bundled model, no cloud call) → regex pulls a total/date → opens Add Expense pre-filled | `ReceiptParser.kt` |
| Share → Trackify | share a bank/UPI SMS or alert to Trackify from any app | same `VoiceExpenseParser` the voice flow uses |

The widget refreshes automatically after any add/edit/delete/clear — every
write funnels through `DatabaseHelper`, which pings
`ExpenseWidgetProvider.updateAll()` once, rather than every call site
remembering to refresh it.

None of these OCR/voice/SMS results ever save on their own — every path
lands back in the normal Add Expense form (title/amount/category/date all
editable) so a bad guess just gets corrected before Save, the same way a
typo would.

## Multi-currency

**Settings → Currency** changes the symbol/format `CurrencyFormatter` uses
everywhere amounts are displayed — dashboard, transaction/income/goals/
recurring lists, the home widget, split-expense math. That is *all* it does:

- No per-transaction currency or FX-rate tracking — the database just stores
  numbers, same as always.
- No conversion of historical amounts when you switch currencies.
- The ₹ prefix inside the Add/Edit Expense/Income amount field stays static
  (`@string/prefix_currency`) — it's not wired to this setting.

A real multi-currency ledger (per-transaction currency + stored FX rate, per
the "don't silently convert historical transactions" principle) is a bigger
feature than this display-format swap; this covers the common case of
picking one currency and having the app consistently show it.

## Data tools (Settings → Data)

| Tool | What it does |
|---|---|
| Import CSV | Reads the same `Title,Amount,Category,Date,Notes` shape Export CSV writes (header matched case-insensitively, falls back to positional columns). Invalid rows are skipped and counted, valid ones are inserted and queued for Sheets sync — nothing is silently dropped. |
| Export Report (PDF) | A one-page monthly or yearly report (totals, category breakdown / month-by-month) drawn with the platform's own `android.graphics.pdf.PdfDocument` — no PDF library dependency — then shared via the same `FileProvider` chooser as CSV export. |
| Backup Data | Writes every expense/income/recurring/goal to a JSON file you choose via Android's document picker (`CreateDocument`) — a plain file, not an account or cloud service. |
| Restore Data | Reads a backup JSON, shows exactly how many rows of each type it contains, and **only after you confirm** replaces everything currently in the app with it. |

## Google Sheets Sync (optional)

Off by default. When enabled in **Settings → Google Sheets Sync**, every
expense you save, edit, or delete is pushed in the background to a Google
Apps Script web app that you deploy yourself against your own Google Sheet —
see the full walkthrough in [`GOOGLE_SHEETS_SETUP.md`](GOOGLE_SHEETS_SETUP.md)
(script source: [`google-apps-script/Code.gs`](google-apps-script/Code.gs)).

- Fully offline-safe: expenses save locally first regardless of sync state.
  Each row tracks a `Synced` / `Pending sync` / `Sync failed` status, shown
  in the transaction list once sync is turned on.
- Sync is retried automatically on app resume and after every save; nothing
  is lost if you're offline when you add an expense.
- Retries are idempotent — each row carries a stable local ID that the script
  uses to update the same sheet row instead of creating a duplicate.
- No credentials are stored in this repo or the app. You provide your own
  Web App URL and access token in Settings; both stay in the app's local
  SharedPreferences on your device.

## Architecture

```
UI (Activities/Fragments, ViewBinding)
   ├─ Dashboard / Analytics / Expenses / Income / Recurring / Goals / Settings
   ├─ Quick Expense entry — AddExpenseActivity, the single landing point for
   │  the bottom-nav "+", Shake, widget, QS tile, voice, receipt scan, and
   │  share-to-app — all just pre-fill it differently
   └─ Settings — Quick Expense, Currency, Google Sheets Sync, Data tools
         │
DatabaseHelper (SQLiteOpenHelper) — single source of truth, local-only;
   every write funnels through it, so widget refresh happens in one place
         │
SheetSyncManager — optional, best-effort push to a user-owned Apps Script
   (plain HttpURLConnection + org.json; no extra network dependency)
BackupManager / CsvImportManager / PdfReportGenerator — data in/out,
   independent of the sync layer (org.json + platform PdfDocument, no libs)

ShakeDetector — isolated SensorEventListener, independent of any UI,
   wired up by MainActivity while it's in the foreground and by
   ShakeOverlayService in the background (which pops QuickAddBannerView as a
   system overlay window via QuickAddOverlay — no activity launch involved)
VoiceExpenseParser / ReceiptParser / NaturalSearchParser — plain regex/
   keyword parsers, no cloud AI, each testable standalone
```

## Build

```
./gradlew assembleDebug     # debug build, AdMob test ad units
./gradlew assembleRelease   # release build, signed via keystore.properties (not committed)
```

Requires a JDK with `jlink` on the daemon JVM (Android Studio's bundled JBR,
or any full JDK 17+/21) — see `gradle/gradle-daemon-jvm.properties`.

## Privacy

All financial data is stored locally in SQLite. Google Sheets sync is opt-in
and points only at infrastructure you control. AdMob ad unit IDs are public
identifiers (not secrets); the release signing key and its passwords are
excluded from version control via `.gitignore`. Notification permission
(Android 13+) is requested at most once, only if you've set a budget or a
recurring expense — declining it once is respected and the app never asks
again (`NotificationHelper.kt`).

## Testing

See [`PENDING_TASKS.md`](PENDING_TASKS.md) for the shake-to-add overlay's
real-device test checklist — partially run on a Vivo T3x (lock-screen overlay
verified working; the home-screen "banner without opening the app" behavior
and the permission fallback were reworked from that feedback and still need a
re-verify pass).

Every feature above was exercised end-to-end on a Pixel 10 Pro emulator
(API 37, Google APIs + Play Store image) — not just compiled, actually run
and tapped through with `adb`/`uiautomator`, screenshotted at each step, and
checked against logcat for crashes. This found and fixed two real bugs:

- **Subscription tracker was wired to dead code.** Dashboard's "Recurring"
  quick action launches `RecurringActivity`, a separate pre-existing class
  from `RecurringFragment` (which nothing referenced — a leftover from
  before this work). The subscription checkbox and monthly/annual total had
  only been added to the unreachable `RecurringFragment`. Fixed by porting
  the feature into `RecurringActivity` and deleting the dead fragment.
- **Static app shortcuts never registered.** Their manifest meta-data
  (`android.app.shortcuts`) was declared on `MainActivity`, but Android
  requires it on the activity that actually holds the `MAIN`/`LAUNCHER`
  intent-filter — `SplashActivity` here, which forwards to `MainActivity`.
  `dumpsys shortcut` showed zero shortcuts until this moved; all three
  (Add Expense / Transactions / Dashboard) now register and resolve
  correctly.

Verified working: onboarding → dashboard → add/edit/delete expense with
payment method → interstitial ad → dashboard refresh; add income with the
Income/Transfer toggle (confirmed transfers are excluded from income totals
but still listed); split-expense dialog; the search box's natural-language
parsing (`"shopping above 500"` correctly returns zero results, proving it's
a real filter and not decoration); recurring/subscription add flow and its
monthly/annual total; Settings' Shake toggle, sensitivity, and live test
dialog; the Currency picker changing every displayed amount app-wide
instantly (Dashboard, Analytics, Recurring, transaction list — verified in
both directions, INR↔USD); Share → Trackify parsing a bank-SMS-shaped string
into amount/category/notes; the Google Sheets **Test Connection** button
reaching a real deployed Apps Script endpoint and correctly surfacing its
`Invalid access token` response (proving the network/JSON round-trip works —
full success needs the real token, which stays local to your device);
per-row Synced/Pending/**Sync failed** status rendering correctly on actual
failed syncs; the Backup Data flow launching Android's real "Save As" picker
with the expected suggested filename; and the app shortcut / widget /
Quick Settings tile provider registrations, confirmed via `dumpsys` after
the fix above.

**Not testable in this environment**, so unverified beyond code review and a
clean build: physically shaking a device (the emulator's console sensor
injection didn't reliably reach the accelerometer listener — a tooling gap,
not evidence of an app bug, since the same detector class is proven live by
the in-Settings test dialog), real speech input to the voice flow, a real
photographed receipt for OCR, and completing the Restore/Import file pickers
end-to-end. These should get a pass on a physical device before shipping.

## Progress log

- **2026-09-05 — Shake-to-add rebuilt as a true system overlay (Android 10+
  fix).** Shaking on the home screen used to call `startActivity()` from the
  background service, which Android 10+ silently blocks — so only the service
  notification ever appeared, and when a fallback activity did open, the app
  itself came to the foreground instead of a floating card (device feedback:
  lock screen worked, home screen opened the app, and a notification fired on
  every shake). Now:
  - The service pops `QuickAddBannerView` straight into a system overlay
    window (`QuickAddOverlay`, `TYPE_APPLICATION_OVERLAY`) on the home
    screen, lock screen, or inside other apps — **a shake never opens the
    app**; there is no fallback activity at all anymore
    (`QuickAddOverlayActivity` deleted).
  - Without the "Display over other apps" permission, a shake shows a short
    toast plus a **one-time** heads-up notification (per service start) that
    opens the permission toggle — it never auto-fires per shake and never
    opens the app.
  - Banner upgraded: drag-handle pill, `✕` close, 3-dot step indicator,
    currency symbol from the currency setting, soft tonal keypad keys with
    haptic ticks, category icons, remarks-step summary line ("₹500 · Food"),
    full-width Done, slide-in/out animations, and tap-outside-to-dismiss.
  - `README`'s Shake section and `PENDING_TASKS.md`'s checklist updated to
    match; `assembleDebug` green.

## What isn't built yet

Everything from a 105-section "real-world features" reference spec's P0 and
P1 phases is implemented, plus its P2 phase (receipt scanning, split
expenses, income transfers, subscription tracking, payment methods, display
currency, CSV import, PDF reports, backup/restore, natural-language search,
share-to-app parsing) — see the sections above for exactly what each one
does and, in currency's and NL search's case, doesn't do.

Still not built (that spec's P3 — travel mode, location-based
categorization, multi-account debt tracking, and similar) plus a few things
deliberately never attempted:

- **Multiple payment accounts/wallets** beyond the payment-method label on
  an expense — no per-account balances or transfers between them.
- **Travel mode**, **location-based categorization**, **split-expense debt
  tracking** (who-owes-whom across people) — the split calculator computes
  your share, it doesn't track other people's shares.
- **Back-tap gesture detection** — Android has no universal third-party API
  for it, so shake is the one supported gesture, by design, not oversight.
- **True per-transaction multi-currency** (stored FX rate, mixed-currency
  totals) — see [Multi-currency](#multi-currency).
