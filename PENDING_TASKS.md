# Pending Tasks

## Shake-to-add background overlay — needs real-device testing

Implemented but unverified on hardware (no device access during development,
see commit adding `ShakeOverlayService`/`QuickAddOverlayActivity`). Test on
the Vivo T3x (Funtouch OS) tomorrow:

- [ ] Settings → enable shake toggle → "shake to test" onboarding dialog
      shows the "Shake detected" toast.
- [ ] Background notification "Shake to add expense is on" appears after
      enabling (may need to grant notification permission when prompted).
- [ ] Shake from the **home screen** → quick-add card appears.
- [ ] Shake from the **lock screen** (both with and without a PIN/pattern set)
      → card appears; if the lock is secure, confirm what actually happens
      (card shown before unlock? unlock prompt first? nothing at all?).
- [ ] Shake **inside another app** (e.g. WhatsApp) → card appears on top,
      doesn't crash the other app.
- [ ] Shake **inside Add Expense / Add Income** → nothing happens (suppressed
      by design).
- [ ] Save from the quick-add card → expense actually appears in the list
      with the right amount/category/title.
- [ ] Leave the toggle on for a while → check Vivo's battery/background app
      management (Settings → Battery → high background power consumption)
      hasn't silently killed the service. If it has, the fix is whitelisting
      the app there — not a code change.
- [ ] Turn the toggle off → notification disappears and shake stops
      everywhere (not just in-app).

If the lock-screen case doesn't work on Vivo specifically, that's a known
possible OEM restriction (see README's Shake to Add Expense section) —
report back what you actually see rather than assuming it's broken.
