# Pending Tasks

## Shake-to-add overlay — needs real-device testing

Rebuilt on 2026-09-05 as a true system overlay: `ShakeOverlayService` now pops
`QuickAddBannerView` directly via `WindowManager`
(`QuickAddOverlay`, `TYPE_APPLICATION_OVERLAY`) instead of calling
`startActivity()`, which Android 10+ silently blocks from background services —
that was the bug where only the service notification ever appeared. A shake
never opens the app: without the "Display over other apps" permission it just
toasts and shows a one-time notification that opens the permission toggle
(the old `QuickAddOverlayActivity` fallback was removed entirely).
`assembleDebug` passes; verify on the Vivo T3x (Funtouch OS):

- [ ] Settings → enable shake toggle → overlay permission dialog appears;
      granting it returns from "Display over other apps".
- [ ] "shake to test" onboarding dialog shows the "Shake detected" toast.
- [ ] Background notification "Shake to add expense is on" appears after
      enabling (may need to grant notification permission when prompted).
- [ ] Shake from the **home screen** → banner slides in over the launcher
      (the app itself must NOT open): amount keypad → category → remarks →
      saved. Tap outside the card or ✕ → banner slides away, no app opened.
- [ ] With the screen **off**, shake → screen wakes and the banner is on top
      of the lock screen (test with and without a PIN/pattern set; confirm
      what a secure lock actually shows).
- [ ] Shake **inside another app** (e.g. WhatsApp) → banner appears on top,
      doesn't crash the other app; remarks step opens the system keyboard.
- [ ] Shake **inside Add Expense / Add Income** → nothing happens (suppressed
      by design).
- [ ] Save from the banner → expense actually appears in the list with the
      right amount/category/title; Sheets sync row becomes Pending/Synced.
- [ ] Without the overlay permission, shake → short toast + a one-time
      "Allow pop-ups" notification appears (tap → the permission toggle, not
      the app); shaking again repeats the toast but not the notification.
- [ ] Leave the toggle on for a while → check Vivo's battery/background app
      management (Settings → Battery → high background power consumption)
      hasn't silently killed the service. If it has, the fix is whitelisting
      the app there — not a code change.
- [ ] Turn the toggle off → notification disappears and shake stops
      everywhere (not just in-app).

If the lock-screen case doesn't work on Vivo specifically, that's a known
possible OEM restriction (see README's Shake to Add Expense section) —
report back what you actually see rather than assuming it's broken.
