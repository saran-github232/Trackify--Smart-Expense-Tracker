# Trackify — Smart Expense Tracker

A local-first Android expense & income tracker. All data lives in SQLite on
your device; nothing is sent anywhere unless you explicitly turn on
[Google Sheets sync](#google-sheets-sync-optional).

## Features

- **Dashboard** — today/week/month/year totals, category breakdown, 7-day
  spending chart, financial health score, budget tracking.
- **Expenses & Income** — add, edit, delete, search, filter by category/date
  range, sort by date or amount, swipe-to-delete with undo, CSV export.
- **Recurring expenses** — auto-added each month on a scheduled day.
- **Savings goals**, **PIN app lock**, dark/light theme.
- **Shake to Add Expense** — shake your phone while the app is open to jump
  straight into Add Expense. See below.
- **Google Sheets sync (optional)** — back up expenses to a spreadsheet you
  own and control. See [`GOOGLE_SHEETS_SETUP.md`](GOOGLE_SHEETS_SETUP.md).
- **Ads** — AdMob banner + native ad on the dashboard, and an interstitial
  after saving an expense/income. Debug builds use Google's public test ad
  unit IDs (`AdIds.kt`); release builds use the app's real ad units.

## Shake to Add Expense

Enable it from **Settings → Quick Expense**.

- Uses only the device accelerometer — no extra runtime permission, since
  motion sensors don't require one on Android.
- Detection only runs while the app is in the foreground (`MainActivity`
  resumed). Android does not offer a reliable, battery-safe way to detect a
  shake while the app is backgrounded or killed, so this app doesn't pretend
  to — it's an in-app shortcut, not a background always-on listener. It's
  automatically suspended while `AddExpenseActivity`/`AddIncomeActivity` (and
  their post-save interstitial ad) are on top, so it can never fire on top of
  an ad or double-open the entry screen.
  ([`MainActivity.onResume`/`onPause`](app/src/main/java/com/saran/expensemanager/MainActivity.kt))
- Requires 3 distinct jolts above the sensitivity threshold within a 1-second
  window (`ShakeDetector.kt`) — a single bump (picking the phone up, setting
  it down) won't trigger it. A 2-second cooldown after a trigger prevents
  rapid re-fires.
- Sensitivity (Low/Medium/High) and vibration feedback are configurable; if
  the device has no accelerometer, the toggle is disabled with an explanation
  instead of silently failing.
- Turning it on shows a short onboarding dialog with a live "shake now to
  test" check, using the same detector class the real feature uses.

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
   ├─ Quick Expense entry — AddExpenseActivity, reused by both the bottom-nav
   │  "+" and the shake trigger
   └─ Settings — Quick Expense (shake) + Google Sheets Sync sections
         │
DatabaseHelper (SQLiteOpenHelper) — single source of truth, local-only
         │
SheetSyncManager — optional, best-effort push to a user-owned Apps Script
   (plain HttpURLConnection + org.json; no extra network dependency)

ShakeDetector — isolated SensorEventListener, independent of any UI,
   only wired up by MainActivity while it's in the foreground
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
excluded from version control via `.gitignore`.
