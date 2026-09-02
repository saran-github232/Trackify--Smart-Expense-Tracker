# Google Sheets Sync — Setup Guide

Trackify stores everything locally on your device by default (SQLite — nothing
leaves your phone). Google Sheets sync is **optional**: turn it on only if you
want a live backup of your expenses in a spreadsheet you own. Nobody but you
gets access — you create the sheet, you host the sync script, you hold the URL.

## What you need

- A Google account (any free Gmail account works).
- 5 minutes.
- Nothing else — no billing, no API keys, no Google Cloud project.

## Step 1 — Create the spreadsheet

1. Go to [sheets.new](https://sheets.new) to create a blank Google Sheet.
2. Rename the sheet tab at the bottom from "Sheet1" to **`Transactions`** (exact spelling, case-sensitive).
3. In row 1, add these headers, one per column: `ID | DATE | MONTH | CATEGORY | AMOUNT | REMARKS`.

## Step 2 — Deploy the sync script

1. In your sheet, open **Extensions → Apps Script**.
2. Delete the placeholder code and paste in the contents of
   [`google-apps-script/Code.gs`](google-apps-script/Code.gs) from this repo.
3. Near the top of the script, change:
   ```js
   const ACCESS_TOKEN = 'CHANGE-ME';
   ```
   to any password-like string you make up (e.g. `trackify-9f2a1c`). This
   stops random people from writing to your sheet even if they guess the URL.
4. Click **Deploy → New deployment**.
5. Click the gear icon next to "Select type" and choose **Web app**.
6. Set **Execute as: Me**, **Who has access: Anyone**, then click **Deploy**.
7. Authorize the script when prompted (it only touches this one spreadsheet).
8. Copy the **Web app URL** shown — it looks like
   `https://script.google.com/macros/s/AKfycb.../exec`.

## Step 3 — Connect the app

1. Open Trackify → bottom nav **Add → Settings** (or Settings from wherever it's linked in your build).
2. Under **Google Sheets Sync**, turn the switch on.
3. Paste the **Web App URL** from Step 2.
4. Enter the same **Access Token** string you set in the script.
5. Tap **Test Connection** — you should see "Connected to \<your sheet name\>".
6. Tap **Sync Now** to push any expenses you already have.

From then on, every expense you add, edit, or delete syncs automatically in
the background, whether it comes from the normal Add Expense screen or from
[Shake to Add](README.md#shake-to-add-expense).

## How it behaves offline

- No internet when you save an expense? It's kept locally with a **Pending
  sync** status and retried automatically next time the app is opened with a
  connection.
- A failed push (bad URL, script error, no internet) is marked **Sync
  failed** and retried on the next sync pass — nothing is ever lost, only your
  local SQLite copy is authoritative.
- Editing a previously-synced expense re-sends it — the script updates the
  existing sheet row (matched by the ID column) instead of creating a
  duplicate.
- Deleting a previously-synced expense removes the matching sheet row.

## Troubleshooting

| Symptom | Fix |
|---|---|
| "Invalid access token" | The token in Settings doesn't match `ACCESS_TOKEN` in the script. |
| "Sheet tab \"Transactions\" not found" | Rename your sheet tab to exactly `Transactions`. |
| Test Connection times out | Redeploy the script (Deploy → Manage deployments → Edit → New version) and make sure access is set to "Anyone". |
| Rows aren't updating on edit | Don't manually edit the ID column in the sheet — it's how the app matches rows for updates/deletes. |

## Data sent to the script

Only what you'd expect for a single expense: local ID, date, month, category,
amount, remarks, and your access token. No device identifiers, no account
info, no analytics.
