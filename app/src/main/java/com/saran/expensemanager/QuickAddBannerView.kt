package com.saran.expensemanager

import android.content.Context
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.saran.expensemanager.databinding.ViewQuickAddBannerBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The shake-to-add banner itself: a three-step flow (amount on a built-in keypad → category →
 * optional remarks) styled as a top-of-screen card, like the iOS reference. It is hosted either
 * as a real system overlay window by [QuickAddOverlay] — so it can pop over the home screen,
 * lock screen, or other apps. Shaking never launches an activity: if the "Display over other
 * apps" permission is missing, the service just nudges once to grant it.
 *
 * The amount keypad is part of the banner on purpose: Android never shows the system keyboard
 * over the lock screen, so a self-contained keypad is the only way to type an amount there.
 * Remarks stay optional for the same reason (title falls back to the category when blank).
 */
class QuickAddBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : MaterialCardView(context, attrs) {

    /** Called when the banner is finished for any reason — saved or cancelled. */
    interface Host {
        fun onBannerFinished(saved: Boolean)
    }

    var host: Host? = null

    private lateinit var binding: ViewQuickAddBannerBinding
    private val categories = listOf(
        "Food", "Travel", "Shopping", "Bills",
        "Health", "Entertainment", "Education", "Other",
    )
    private val categoryIcons = mapOf(
        "Food" to R.drawable.ic_food,
        "Travel" to R.drawable.ic_travel,
        "Shopping" to R.drawable.ic_shopping,
        "Bills" to R.drawable.ic_bills,
        "Health" to R.drawable.ic_health,
        "Entertainment" to R.drawable.ic_entertainment,
        "Education" to R.drawable.ic_education,
        "Other" to R.drawable.ic_other,
    )

    /** Symbol from the user's currency setting (₹ by default), shown next to the amount. */
    private val currencySymbol: String by lazy {
        runCatching {
            NumberFormat.getCurrencyInstance(CurrencyPrefs(context).locale).currency?.symbol
                ?: CurrencyPrefs(context).currencyCode
        }.getOrDefault("₹")
    }
    private var amount = StringBuilder()
    private var selectedCategory: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private enum class Step { AMOUNT, CATEGORY, REMARKS }

    init {
        radius = resources.getDimension(R.dimen.card_corner_radius_large)
        cardElevation = resources.getDimension(R.dimen.card_elevation) * 3f
        binding = ViewQuickAddBannerBinding.inflate(LayoutInflater.from(context), this, true)
        binding.tvCurrency.text = currencySymbol
        setupKeys()
        setupButtons()
        buildCategoryList()
        showStep(Step.AMOUNT)
    }

    override fun onDetachedFromWindow() {
        mainHandler.removeCallbacksAndMessages(null)
        hideKeyboard()
        super.onDetachedFromWindow()
    }

    // ── Keypad ────────────────────────────────────────────────────────────────

    private fun setupKeys() {
        val keyListener = View.OnClickListener { v ->
            binding.tvAmountError.isVisible = false
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            when (v.id) {
                R.id.btnKeyBackspace -> if (amount.isNotEmpty()) amount.deleteCharAt(amount.length - 1)
                R.id.btnKeyDot -> if (amount.none { it == '.' }) appendToAmount(".")
                else -> appendToAmount((v as TextView).text.toString())
            }
            refreshAmountDisplay()
        }
        listOf(
            R.id.btnKey1, R.id.btnKey2, R.id.btnKey3, R.id.btnKey4,
            R.id.btnKey5, R.id.btnKey6, R.id.btnKey7, R.id.btnKey8,
            R.id.btnKey9, R.id.btnKey0, R.id.btnKeyDot, R.id.btnKeyBackspace,
        ).forEach { id -> findViewById<View>(id).setOnClickListener(keyListener) }
        refreshAmountDisplay()
    }

    private fun appendToAmount(key: String) {
        if (amount.length >= MAX_AMOUNT_CHARS) return
        if (amount.isEmpty() && key == "0") return // no leading zeros
        if (amount.isEmpty() && key == ".") { amount.append("0."); return }
        if (amount.toString() == "0" && key != ".") { amount.clear(); amount.append(key); return }
        amount.append(key)
    }

    private fun refreshAmountDisplay() {
        val empty = amount.isEmpty()
        binding.tvAmountDisplay.text = if (empty) "0" else amount.toString()
        val attr = if (empty) com.google.android.material.R.attr.colorOnSurfaceVariant
        else com.google.android.material.R.attr.colorOnSurface
        binding.tvAmountDisplay.setTextColor(MaterialColors.getColor(this, attr))
    }

    // ── Step flow ─────────────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnClose.setOnClickListener { cancel() }
        binding.btnCancelAmount.setOnClickListener { cancel() }
        binding.btnNextAmount.setOnClickListener { onAmountDone() }
        binding.btnBackAmount.setOnClickListener { showStep(Step.AMOUNT) }
        binding.btnCancelCategory.setOnClickListener { cancel() }
        binding.btnBackCategory.setOnClickListener { showStep(Step.CATEGORY) }
        binding.btnCancelRemarks.setOnClickListener { cancel() }
        binding.btnDoneRemarks.setOnClickListener { save() }
        binding.etRemarks.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                save(); true
            } else {
                false
            }
        }
    }

    private fun buildCategoryList() {
        val outline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
        categories.forEachIndexed { index, category ->
            if (index > 0) {
                binding.llCategoryList.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(1))
                    setBackgroundColor(outline)
                })
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val tv = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                setBackgroundResource(tv.resourceId)
                setPadding(px(8), px(10), px(8), px(10))
                setOnClickListener {
                    selectedCategory = category
                    showStep(Step.REMARKS)
                }
                addView(ImageView(context).apply {
                    setImageResource(categoryIcons[category] ?: R.drawable.ic_other)
                    setColorFilter(
                        MaterialColors.getColor(this@QuickAddBannerView, com.google.android.material.R.attr.colorOnSurfaceVariant)
                    )
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    layoutParams = LinearLayout.LayoutParams(px(22), px(22))
                })
                addView(
                    TextView(context).apply {
                        text = category
                        textSize = 16f
                        setPadding(px(14), 0, 0, 0)
                        setTextColor(
                            MaterialColors.getColor(this@QuickAddBannerView, com.google.android.material.R.attr.colorOnSurface)
                        )
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(TextView(context).apply {
                    text = "›"
                    textSize = 18f
                    setTextColor(
                        MaterialColors.getColor(this@QuickAddBannerView, com.google.android.material.R.attr.colorOnSurfaceVariant)
                    )
                })
            }
            binding.llCategoryList.addView(
                row,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
        }
    }

    private fun showStep(step: Step) {
        binding.stepAmount.isVisible = step == Step.AMOUNT
        binding.stepCategory.isVisible = step == Step.CATEGORY
        binding.stepRemarks.isVisible = step == Step.REMARKS
        updateDots(step)
        if (step == Step.REMARKS) {
            // Echo what's being saved so the user can confirm before typing a note.
            binding.tvSummary.text = "$currencySymbol$amount · $selectedCategory"
            // Only the remarks step needs the system keyboard, which requires window focus —
            // flip the overlay window to focusable for it (no-op outside an overlay window).
            setOverlayFocusable(true)
            binding.etRemarks.requestFocus()
            mainHandler.postDelayed({ showKeyboard() }, KEYBOARD_DELAY_MS)
        } else {
            hideKeyboard()
            setOverlayFocusable(false)
        }
    }

    private fun updateDots(step: Step) {
        val active = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
        val inactive = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant)
        val index = when (step) {
            Step.AMOUNT -> 0
            Step.CATEGORY -> 1
            Step.REMARKS -> 2
        }
        listOf(binding.dotStep1, binding.dotStep2, binding.dotStep3).forEachIndexed { i, dot ->
            dot.backgroundTintList = ColorStateList.valueOf(if (i == index) active else inactive)
        }
    }

    private fun onAmountDone() {
        val value = amount.toString().toDoubleOrNull()
        if (value == null || value <= 0) {
            binding.tvAmountError.isVisible = true
            return
        }
        showStep(Step.CATEGORY)
    }

    // ── Save / cancel ─────────────────────────────────────────────────────────

    private fun save() {
        val value = amount.toString().toDoubleOrNull() ?: 0.0
        val category = selectedCategory
        if (value <= 0 || category == null) {
            showStep(Step.AMOUNT)
            return
        }
        val remarks = binding.etRemarks.text.toString().trim()
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val id = DatabaseHelper.getInstance(context).addExpense(
            Expense(
                title = remarks.ifEmpty { category },
                amount = value,
                category = category,
                date = date,
                notes = remarks,
            )
        )
        if (id > 0) {
            RecentCategories.record(context, category)
            SheetSyncManager.triggerSync(context)
            Toast.makeText(context, R.string.msg_expense_saved, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, R.string.msg_save_failed, Toast.LENGTH_SHORT).show()
        }
        host?.onBannerFinished(id > 0)
    }

    private fun cancel() = host?.onBannerFinished(false)

    // ── Overlay-window helpers (no-ops when hosted in the fallback activity) ──

    private fun setOverlayFocusable(focusable: Boolean) {
        val lp = layoutParams as? WindowManager.LayoutParams ?: return
        lp.flags = if (focusable) {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (isAttachedToWindow) {
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                ?.updateViewLayout(this, lp)
        }
    }

    private fun showKeyboard() {
        if (!isAttachedToWindow) return
        val token = binding.etRemarks.windowToken ?: return
        val ime = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        ime.showSoftInput(binding.etRemarks, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        if (!isAttachedToWindow) return
        val token = binding.etRemarks.windowToken ?: return
        val ime = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        ime.hideSoftInputFromWindow(token, 0)
    }

    private fun px(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private companion object {
        private const val MAX_AMOUNT_CHARS = 9
        private const val KEYBOARD_DELAY_MS = 150L
    }
}
