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
import com.saran.expensemanager.databinding.ActivityAddIncomeBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddIncomeActivity : EdgeToEdgeActivity() {

    private lateinit var binding: ActivityAddIncomeBinding
    private lateinit var db: DatabaseHelper
    private val cal = Calendar.getInstance()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var interstitialAd: InterstitialAd? = null
    private var isSaving = false

    private val sources = listOf(
        "Salary", "Freelance", "Business", "Investment", "Rental", "Gift", "Other"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddIncomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.appBar.applyTopSystemBarInsetPadding()
        binding.nestedScroll.applyBottomSystemBarInsetPadding()

        db = DatabaseHelper.getInstance(this)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.toggleIncomeType.check(binding.btnTypeIncome.id)

        binding.actvSource.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sources)
        )
        binding.actvSource.setOnClickListener { binding.actvSource.showDropDown() }
        binding.tilSource.setEndIconOnClickListener { binding.actvSource.showDropDown() }

        binding.etDate.setText(dateFmt.format(cal.time))
        binding.etDate.setOnClickListener { showDatePicker() }
        binding.tilDate.setEndIconOnClickListener { showDatePicker() }

        binding.btnSaveIncome.setOnClickListener { saveIncome() }
        binding.btnCancel.setOnClickListener { finish() }

        InterstitialAd.load(this, AdIds.INTERSTITIAL, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitialAd = ad }
                override fun onAdFailedToLoad(err: LoadAdError) { interstitialAd = null }
            })
    }

    private fun showDatePicker() {
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            binding.etDate.setText(dateFmt.format(cal.time))
        }, cal[Calendar.YEAR], cal[Calendar.MONTH], cal[Calendar.DAY_OF_MONTH]).show()
    }

    private fun saveIncome() {
        if (isSaving) return

        val title = binding.etTitle.text.toString().trim()
        val amountStr = binding.etAmount.text.toString().trim()
        val source = binding.actvSource.text.toString().trim()
        val date = binding.etDate.text.toString().trim()
        val notes = binding.etNotes.text.toString().trim()
        val amount = amountStr.toDoubleOrNull()

        var ok = true
        if (title.isEmpty()) {
            binding.tilTitle.error = getString(R.string.error_title_required); ok = false
        } else binding.tilTitle.error = null
        if (amount == null || amount <= 0) {
            binding.tilAmount.error = getString(R.string.error_invalid_amount); ok = false
        } else binding.tilAmount.error = null
        if (source.isEmpty() || source !in sources) {
            binding.tilSource.error = getString(R.string.error_source_required); ok = false
        } else binding.tilSource.error = null
        if (date.isEmpty()) {
            binding.tilDate.error = getString(R.string.error_date_required); ok = false
        } else binding.tilDate.error = null

        if (!ok) return
        isSaving = true
        binding.btnSaveIncome.isEnabled = false

        val type = if (binding.toggleIncomeType.checkedButtonId == binding.btnTypeTransfer.id) {
            DatabaseHelper.INCOME_TYPE_TRANSFER
        } else {
            DatabaseHelper.INCOME_TYPE_INCOME
        }
        val id = db.addIncome(Income(title = title, amount = amount!!, source = source, date = date, notes = notes, type = type))
        if (id > 0) {
            Toast.makeText(this, getString(R.string.msg_income_saved), Toast.LENGTH_SHORT).show()
            showAdThenFinish()
        } else {
            isSaving = false
            binding.btnSaveIncome.isEnabled = true
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

    override fun onResume() {
        super.onResume()
        ShakeSuppressor.suppressed = true
    }

    override fun onPause() {
        super.onPause()
        ShakeSuppressor.suppressed = false
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
