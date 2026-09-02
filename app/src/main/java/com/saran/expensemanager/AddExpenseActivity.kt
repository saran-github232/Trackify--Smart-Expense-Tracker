package com.saran.expensemanager

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.saran.expensemanager.databinding.ActivityAddExpenseBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddExpenseActivity : EdgeToEdgeActivity() {

    private lateinit var binding: ActivityAddExpenseBinding
    private lateinit var db: DatabaseHelper
    private val cal = Calendar.getInstance()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var interstitialAd: InterstitialAd? = null
    private var isSaving = false

    private val categories = listOf(
        "Food", "Travel", "Shopping", "Bills",
        "Health", "Entertainment", "Education", "Other",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.appBar.applyTopSystemBarInsetPadding()
        binding.nestedScroll.applyBottomSystemBarInsetPadding()
        // toolbar wired below after db init

        db = DatabaseHelper.getInstance(this)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.actvCategory.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        )
        binding.actvCategory.setOnClickListener { binding.actvCategory.showDropDown() }
        binding.tilCategory.setEndIconOnClickListener { binding.actvCategory.showDropDown() }

        binding.etDate.setText(dateFmt.format(cal.time))
        binding.etDate.setOnClickListener { showDatePicker() }
        binding.tilDate.setEndIconOnClickListener { showDatePicker() }

        binding.btnSave.setOnClickListener { saveExpense() }
        binding.btnCancel.setOnClickListener { finish() }

        InterstitialAd.load(this, AdIds.INTERSTITIAL, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitialAd = ad }
                override fun onAdFailedToLoad(err: LoadAdError) { interstitialAd = null }
            })
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

    private fun saveExpense() {
        if (isSaving) return

        val title = binding.etTitle.text.toString().trim()
        val amountStr = binding.etAmount.text.toString().trim()
        val category = binding.actvCategory.text.toString().trim()
        val date = binding.etDate.text.toString().trim()
        val notes = binding.etNotes.text.toString().trim()

        if (!validate(title, amountStr, category, date)) return
        isSaving = true
        binding.btnSave.isEnabled = false

        val id = db.addExpense(
            Expense(
                title = title,
                amount = amountStr.toDouble(),
                category = category,
                date = date,
                notes = notes,
            )
        )
        if (id > 0) {
            Toast.makeText(this, getString(R.string.msg_expense_saved), Toast.LENGTH_SHORT).show()
            SheetSyncManager.triggerSync(this)
            showAdThenFinish()
        } else {
            isSaving = false
            binding.btnSave.isEnabled = true
            Toast.makeText(this, getString(R.string.msg_save_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAdThenFinish() {
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() { finish() }
                override fun onAdFailedToShowFullScreenContent(e: AdError) { finish() }
            }
            ad.show(this)
        } else {
            finish()
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
