package com.saran.expensemanager

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import com.saran.expensemanager.databinding.ActivityEditIncomeBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EditIncomeActivity : EdgeToEdgeActivity() {

    companion object {
        const val EXTRA_ID = "income_id"
        const val EXTRA_TITLE = "income_title"
        const val EXTRA_AMOUNT = "income_amount"
        const val EXTRA_SOURCE = "income_source"
        const val EXTRA_DATE = "income_date"
        const val EXTRA_NOTES = "income_notes"
        const val EXTRA_TYPE = "income_type"
    }

    private lateinit var binding: ActivityEditIncomeBinding
    private lateinit var db: DatabaseHelper
    private val cal = Calendar.getInstance()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var incomeId = 0L
    private var isSaving = false

    private val sources = listOf(
        "Salary", "Freelance", "Business", "Investment", "Rental", "Gift", "Other"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditIncomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.appBar.applyTopSystemBarInsetPadding()
        binding.nestedScroll.applyBottomSystemBarInsetPadding()

        db = DatabaseHelper.getInstance(this)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        incomeId = intent.getLongExtra(EXTRA_ID, 0L)
        binding.etTitle.setText(intent.getStringExtra(EXTRA_TITLE) ?: "")
        val amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
        binding.etAmount.setText(if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString())
        binding.etDate.setText(intent.getStringExtra(EXTRA_DATE) ?: dateFmt.format(cal.time))
        binding.etNotes.setText(intent.getStringExtra(EXTRA_NOTES) ?: "")

        binding.actvSource.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sources)
        )
        binding.actvSource.setOnClickListener { binding.actvSource.showDropDown() }
        binding.tilSource.setEndIconOnClickListener { binding.actvSource.showDropDown() }
        val savedSource = intent.getStringExtra(EXTRA_SOURCE) ?: ""
        binding.actvSource.setText(savedSource, false)

        val savedType = intent.getStringExtra(EXTRA_TYPE) ?: DatabaseHelper.INCOME_TYPE_INCOME
        binding.toggleIncomeType.check(
            if (savedType == DatabaseHelper.INCOME_TYPE_TRANSFER) binding.btnTypeTransfer.id else binding.btnTypeIncome.id
        )

        intent.getStringExtra(EXTRA_DATE)?.let {
            try { cal.time = dateFmt.parse(it)!! } catch (_: Exception) {}
        }

        binding.etDate.setOnClickListener { showDatePicker() }
        binding.tilDate.setEndIconOnClickListener { showDatePicker() }
        binding.btnSaveIncome.setOnClickListener { updateIncome() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    private fun showDatePicker() {
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            binding.etDate.setText(dateFmt.format(cal.time))
        }, cal[Calendar.YEAR], cal[Calendar.MONTH], cal[Calendar.DAY_OF_MONTH]).show()
    }

    private fun updateIncome() {
        if (isSaving) return

        val title = binding.etTitle.text.toString().trim()
        val amountStr = binding.etAmount.text.toString().trim()
        val source = binding.actvSource.text.toString().trim()
        val date = binding.etDate.text.toString().trim()
        val notes = binding.etNotes.text.toString().trim()
        val amount = amountStr.toDoubleOrNull()

        var ok = true
        if (title.isEmpty()) { binding.tilTitle.error = getString(R.string.error_title_required); ok = false }
        else binding.tilTitle.error = null
        if (amount == null || amount <= 0) { binding.tilAmount.error = getString(R.string.error_invalid_amount); ok = false }
        else binding.tilAmount.error = null
        if (source.isEmpty() || source !in sources) { binding.tilSource.error = getString(R.string.error_source_required); ok = false }
        else binding.tilSource.error = null
        if (date.isEmpty()) { binding.tilDate.error = getString(R.string.error_date_required); ok = false }
        else binding.tilDate.error = null

        if (!ok) return
        isSaving = true
        binding.btnSaveIncome.isEnabled = false

        val type = if (binding.toggleIncomeType.checkedButtonId == binding.btnTypeTransfer.id) {
            DatabaseHelper.INCOME_TYPE_TRANSFER
        } else {
            DatabaseHelper.INCOME_TYPE_INCOME
        }
        val updated = db.updateIncome(Income(id = incomeId, title = title, amount = amount!!, source = source, date = date, notes = notes, type = type))
        if (updated > 0) {
            Toast.makeText(this, getString(R.string.msg_income_updated), Toast.LENGTH_SHORT).show()
            finish()
        } else {
            isSaving = false
            binding.btnSaveIncome.isEnabled = true
            Toast.makeText(this, getString(R.string.msg_income_update_failed), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

