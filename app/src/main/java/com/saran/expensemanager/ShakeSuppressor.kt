package com.saran.expensemanager

/**
 * Set while an expense/income entry screen (or the shake overlay itself) is on top, so the
 * always-on background [ShakeOverlayService] doesn't pop another overlay on top of it.
 */
object ShakeSuppressor {
    @Volatile var suppressed: Boolean = false
}
