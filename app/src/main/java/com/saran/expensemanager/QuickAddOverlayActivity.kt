package com.saran.expensemanager

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Toast
import com.saran.expensemanager.databinding.ActivityQuickAddOverlayBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small floating card shown on top of whatever's on screen — home screen, lock screen, or another
 * app — when the phone is shaken. Deliberately a single screen (amount + category + remarks) with
 * no title field: it captures the same information the full Add Expense screen would, just faster,
 * and uses the remarks (or the category, if left blank) as the expense title.
 */
class QuickAddOverlayActivity : EdgeToEdgeActivity() {

    private lateinit var binding: ActivityQuickAddOverlayBinding
    private lateinit var db: DatabaseHelper

    private val categories = listOf(
        "Food", "Travel", "Shopping", "Bills",
        "Health", "Entertainment", "Education", "Other",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        binding = ActivityQuickAddOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = DatabaseHelper.getInstance(this)
        binding.actvCategory.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories))
        binding.etAmount.requestFocus()

        binding.btnSave.setOnClickListener { save() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        ShakeSuppressor.suppressed = true
    }

    override fun onPause() {
        super.onPause()
        ShakeSuppressor.suppressed = false
    }

    /** Lets this card show on top of a locked screen — same mechanism incoming-call UIs use. */
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun save() {
        val amount = binding.etAmount.text.toString().trim().toDoubleOrNull()
        val category = binding.actvCategory.text.toString().trim()
        val remarks = binding.etRemarks.text.toString().trim()

        var ok = true
        if (amount == null || amount <= 0) {
            binding.tilAmount.error = getString(R.string.error_invalid_amount)
            ok = false
        } else {
            binding.tilAmount.error = null
        }
        if (category.isEmpty() || !categories.contains(category)) {
            binding.tilCategory.error = getString(R.string.error_category_required)
            ok = false
        } else {
            binding.tilCategory.error = null
        }
        if (!ok) return

        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val id = db.addExpense(
            Expense(
                title = remarks.ifEmpty { category },
                amount = amount!!,
                category = category,
                date = date,
                notes = remarks,
            )
        )
        if (id > 0) {
            RecentCategories.record(this, category)
            SheetSyncManager.triggerSync(this)
            Toast.makeText(this, R.string.msg_expense_saved, Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, R.string.msg_save_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
