package com.saran.expensemanager

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import com.saran.expensemanager.databinding.ActivityEditExpenseBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EditExpenseActivity : EdgeToEdgeActivity() {

    private lateinit var binding: ActivityEditExpenseBinding
    private lateinit var db: DatabaseHelper
    private val cal = Calendar.getInstance()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var expenseId: Long = 0
    private var isSaving = false

    private val categories = listOf(
        "Food", "Travel", "Shopping", "Bills",
        "Health", "Entertainment", "Education", "Other",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.appBar.applyTopSystemBarInsetPadding()
        binding.nestedScroll.applyBottomSystemBarInsetPadding()
        db = DatabaseHelper.getInstance(this)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.actvCategory.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        )
        binding.actvCategory.setOnClickListener { binding.actvCategory.showDropDown() }
        binding.tilCategory.setEndIconOnClickListener { binding.actvCategory.showDropDown() }

        binding.etDate.setOnClickListener { showDatePicker() }
        binding.tilDate.setEndIconOnClickListener { showDatePicker() }

        loadData()

        binding.btnUpdate.setOnClickListener { updateExpense() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    private fun loadData() {
        expenseId = intent.getLongExtra("expense_id", 0)
        binding.etTitle.setText(intent.getStringExtra("expense_title") ?: "")
        val amount = intent.getDoubleExtra("expense_amount", 0.0)
        binding.etAmount.setText(
            if ((amount % 1.0) == 0.0) {
                amount.toLong().toString()
            } else {
                amount.toString()
            },
        )
        binding.actvCategory.setText(intent.getStringExtra("expense_category") ?: "", false)
        val dateStr = intent.getStringExtra("expense_date") ?: ""
        binding.etDate.setText(dateStr)
        binding.etNotes.setText(intent.getStringExtra("expense_notes") ?: "")
        try {
            if (dateStr.isNotEmpty()) cal.time = dateFmt.parse(dateStr) ?: cal.time
        } catch (_: Exception) {}
    }

    private fun showDatePicker() {
        DatePickerDialog(
            this,
            { _, y, m, d ->
                cal.set(y, m, d)
                binding.etDate.setText(dateFmt.format(cal.time))
            },
            cal[Calendar.YEAR],
            cal[Calendar.MONTH],
            cal[Calendar.DAY_OF_MONTH],
        ).show()
    }

    private fun updateExpense() {
        if (isSaving) return

        val title = binding.etTitle.text.toString().trim()
        val amountStr = binding.etAmount.text.toString().trim()
        val category = binding.actvCategory.text.toString().trim()
        val date = binding.etDate.text.toString().trim()
        val notes = binding.etNotes.text.toString().trim()

        if (!validate(title, amountStr, category, date)) return
        isSaving = true
        binding.btnUpdate.isEnabled = false

        val rows = db.updateExpense(
            Expense(
                id = expenseId,
                title = title,
                amount = amountStr.toDouble(),
                category = category,
                date = date,
                notes = notes,
            ),
        )
        if (rows > 0) {
            Toast.makeText(this, getString(R.string.msg_expense_updated), Toast.LENGTH_SHORT).show()
            SheetSyncManager.triggerSync(this)
            finish()
        } else {
            isSaving = false
            binding.btnUpdate.isEnabled = true
            Toast.makeText(this, getString(R.string.msg_update_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun validate(title: String, amountStr: String, category: String, date: String): Boolean {
        var ok = true
        if (title.isEmpty()) {
            binding.tilTitle.error = getString(R.string.error_title_required)
            ok = false
        } else {
            binding.tilTitle.error = null
        }

        val amount = amountStr.toDoubleOrNull()
        if ((amount == null) || (amount <= 0)) {
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

        if (date.isEmpty()) {
            binding.tilDate.error = getString(R.string.error_date_required)
            ok = false
        } else {
            binding.tilDate.error = null
        }

        return ok
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

