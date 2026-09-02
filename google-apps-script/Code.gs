/**
 * Trackify — Google Sheets sync endpoint.
 *
 * Deploy: Extensions > Apps Script > paste this file > Deploy > New deployment
 *         > type "Web app" > Execute as "Me" > Who has access "Anyone".
 * Then copy the deployment URL into Trackify > Settings > Google Sheets Sync,
 * and set the same ACCESS_TOKEN value in both places.
 *
 * See ../GOOGLE_SHEETS_SETUP.md for the full step-by-step guide.
 */

const SHEET_NAME = 'Transactions';
const ACCESS_TOKEN = 'CHANGE-ME'; // Pick your own value — must match the app's "Access Token" field.

function doPost(e) {
  try {
    const data = JSON.parse(e.postData.contents);
    if (data.token !== ACCESS_TOKEN) {
      return jsonResponse({ success: false, message: 'Invalid access token' });
    }

    switch (data.action) {
      case 'ping':
        return jsonResponse({
          success: true,
          message: 'Connected to "' + SpreadsheetApp.getActiveSpreadsheet().getName() + '"',
        });
      case 'sync':
        return jsonResponse(upsertRow(data));
      case 'delete':
        return jsonResponse(deleteRow(data.localId));
      default:
        return jsonResponse({ success: false, message: 'Unknown action: ' + data.action });
    }
  } catch (err) {
    return jsonResponse({ success: false, message: String(err.message || err) });
  }
}

function getSheet() {
  const sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(SHEET_NAME);
  if (!sheet) throw new Error('Sheet tab "' + SHEET_NAME + '" not found — see the setup guide.');
  return sheet;
}

/** Row index (1-based, includes header) of the row whose ID column matches localId, or -1. */
function findRow(sheet, localId) {
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) return -1;
  const ids = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
  for (let i = 0; i < ids.length; i++) {
    if (String(ids[i][0]) === String(localId)) return i + 2;
  }
  return -1;
}

/** Insert or, if this localId already has a row (e.g. the expense was edited), overwrite it. */
function upsertRow(data) {
  if (data.localId == null || !data.date || !data.category || typeof data.amount !== 'number') {
    return { success: false, message: 'Missing or invalid fields' };
  }
  const sheet = getSheet();
  const row = [data.localId, data.date, data.month || '', data.category, data.amount, data.remarks || ''];
  const existingRow = findRow(sheet, data.localId);
  if (existingRow > 0) {
    sheet.getRange(existingRow, 1, 1, row.length).setValues([row]);
  } else {
    sheet.appendRow(row);
  }
  return { success: true };
}

function deleteRow(localId) {
  const sheet = getSheet();
  const existingRow = findRow(sheet, localId);
  if (existingRow > 0) sheet.deleteRow(existingRow);
  return { success: true };
}

function jsonResponse(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
