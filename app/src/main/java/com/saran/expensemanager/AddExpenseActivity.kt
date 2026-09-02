package com.saran.expensemanager

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.children
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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

    private val paymentMethods = listOf(
        "Cash", "UPI", "Debit Card", "Credit Card", "Bank Transfer", "Net Banking", "Wallet", "Other",
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

        binding.actvPaymentMethod.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, paymentMethods)
        )
        binding.actvPaymentMethod.setOnClickListener { binding.actvPaymentMethod.showDropDown() }
        binding.tilPaymentMethod.setEndIconOnClickListener { binding.actvPaymentMethod.showDropDown() }

        binding.etDate.setText(dateFmt.format(cal.time))
        binding.etDate.setOnClickListener { showDatePicker() }
        binding.tilDate.setEndIconOnClickListener { showDatePicker() }

        binding.btnSave.setOnClickListener { saveExpense() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.tvSplitExpense.setOnClickListener { showSplitDialog() }

        setupQuickAmountChips()
        setupRecentCategoryChips()
        setupMerchantSuggestion()
        applyPrefill()
        applySharedText()

        InterstitialAd.load(this, AdIds.INTERSTITIAL, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitialAd = ad }
                override fun onAdFailedToLoad(err: LoadAdError) { interstitialAd = null }
            })
    }

    /** Pre-fills fields from voice/shake entry points; the user still reviews and can edit everything. */
    private fun applyPrefill() {
        intent.getStringExtra(EXTRA_PREFILL_TITLE)?.let { binding.etTitle.setText(it) }
        val prefillAmount = intent.getDoubleExtra(EXTRA_PREFILL_AMOUNT, -1.0)
        if (prefillAmount > 0) {
            binding.etAmount.setText(
                if (prefillAmount % 1.0 == 0.0) prefillAmount.toLong().toString() else prefillAmount.toString()
            )
        }
        intent.getStringExtra(EXTRA_PREFILL_CATEGORY)?.takeIf { categories.contains(it) }
            ?.let { binding.actvCategory.setText(it, false) }
        intent.getStringExtra(EXTRA_PREFILL_NOTES)?.let { binding.etNotes.setText(it) }
        intent.getStringExtra(EXTRA_PREFILL_DATE)?.let { dateStr ->
            runCatching { cal.time = dateFmt.parse(dateStr)!! }
            binding.etDate.setText(dateStr)
        }
    }

    /** Handles "Share > Trackify" from an SMS/notification app — e.g. a bank debit alert. */
    private fun applySharedText() {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val parsed = VoiceExpenseParser.parse(sharedText)
        binding.etTitle.setText(parsed.title)
        if ((parsed.amount ?: 0.0) > 0) {
            binding.etAmount.setText(
                if (parsed.amount!! % 1.0 == 0.0) parsed.amount.toLong().toString() else parsed.amount.toString()
            )
        }
        binding.actvCategory.setText(parsed.category, false)
        binding.etNotes.setText(sharedText)
    }

    private fun showSplitDialog() {
        val dp = resources.displayMetrics.density
        val pad = (16 * dp).toInt()
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }
        fun field(hint: String): TextInputEditText {
            val til = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
                this.hint = hint
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = (12 * dp).toInt() }
            }
            val et = TextInputEditText(til.context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            til.addView(et)
            container.addView(til)
            return et
        }
        val etTotal = field(getString(R.string.split_total_hint))
        val etPeople = field(getString(R.string.split_people_hint))
        if (binding.etAmount.text?.isNotBlank() == true) etTotal.setText(binding.etAmount.text)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.split_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.split_apply) { _, _ ->
                val total = etTotal.text.toString().toDoubleOrNull()
                val people = etPeople.text.toString().toIntOrNull()
                if (total != null && total > 0 && people != null && people > 0) {
                    val share = total / people
                    val fmt = CurrencyFormatter.currencyInstance(this@AddExpenseActivity)
                    binding.etAmount.setText(
                        if (share % 1.0 == 0.0) share.toLong().toString() else "%.2f".format(share)
                    )
                    binding.etNotes.setText(getString(R.string.split_note, fmt.format(total), people))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupQuickAmountChips() {
        val amounts = listOf(50, 100, 200, 500, 1000)
        binding.chipGroupQuickAmount.children.forEachIndexed { index, chip ->
            (chip as? Chip)?.setOnClickListener { binding.etAmount.setText(amounts[index].toString()) }
        }
    }

    private fun setupRecentCategoryChips() {
        val recent = RecentCategories.get(this)
        if (recent.isEmpty()) return
        binding.chipGroupRecentCategory.visibility = View.VISIBLE
        recent.forEach { category ->
            val chip = Chip(this).apply {
                text = category
                setOnClickListener { binding.actvCategory.setText(category, false) }
            }
            binding.chipGroupRecentCategory.addView(chip)
        }
    }

    /** Suggests a category based on what the user picked last time for a similarly-named title. */
    private fun setupMerchantSuggestion() {
        binding.etTitle.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (binding.actvCategory.text.isNullOrBlank()) {
                    MerchantMemory.suggest(this@AddExpenseActivity, s?.toString() ?: "")
                        ?.let { binding.actvCategory.setText(it, false) }
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
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
        val paymentMethod = binding.actvPaymentMethod.text.toString().trim()

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
                paymentMethod = paymentMethod,
            )
        )
        if (id > 0) {
            MerchantMemory.remember(this, title, category)
            RecentCategories.record(this, category)
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

    companion object {
        const val EXTRA_PREFILL_TITLE = "prefill_title"
        const val EXTRA_PREFILL_AMOUNT = "prefill_amount"
        const val EXTRA_PREFILL_CATEGORY = "prefill_category"
        const val EXTRA_PREFILL_NOTES = "prefill_notes"
        const val EXTRA_PREFILL_DATE = "prefill_date"
    }
}
