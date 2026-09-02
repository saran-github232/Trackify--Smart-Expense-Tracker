package com.saran.expensemanager

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.saran.expensemanager.databinding.ActivityDashboardBinding
import com.saran.expensemanager.databinding.ItemRecentTransactionBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Calendar
import kotlin.math.abs

class DashboardFragment : Fragment() {

    private var _binding: ActivityDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: DatabaseHelper
    private lateinit var budgetPrefs: BudgetPrefs
    private lateinit var userPrefs: UserPrefs
    private lateinit var reminderPrefs: ReminderPrefs
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* re-checked on next refresh */ }
    private lateinit var fmt: NumberFormat
    private var nativeAd: NativeAd? = null
    private var bannerAdView: AdView? = null

    private val categoryColors = mapOf(
        "Food" to "#FF6B6B",
        "Travel" to "#4ECDC4",
        "Shopping" to "#45B7D1",
        "Bills" to "#FFA07A",
        "Health" to "#66BB6A",
        "Entertainment" to "#BA68C8",
        "Education" to "#42A5F5",
        "Other" to "#90A4AE"
    )

    private data class DashboardData(
        val totalExpenses: Double,
        val totalIncome: Double,
        val totalCount: Int,
        val monthTotal: Double,
        val monthIncome: Double,
        val lastMonthTotal: Double,
        val cats: Map<String, Double>,
        val recent: List<Expense>,
        val daily: List<Pair<String, Double>>,
        val goals: List<Goal>,
        val budget: Double,
        val hasBudget: Boolean
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ActivityDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = DatabaseHelper.getInstance(requireContext())
        budgetPrefs = BudgetPrefs(requireContext())
        userPrefs = UserPrefs(requireContext())
        reminderPrefs = ReminderPrefs(requireContext())
        fmt = CurrencyFormatter.currencyInstance(requireContext())

        binding.headerContainer.applyTopSystemBarInsetPadding()

        updateProfileRow()

        binding.fabAddExpense.setOnClickListener {
            startActivity(Intent(requireContext(), AddExpenseActivity::class.java))
        }
        binding.btnViewExpenses.setOnClickListener {
            (requireActivity() as MainActivity).navigateToTab(R.id.nav_expenses)
        }
        binding.btnAnalytics.setOnClickListener {
            (requireActivity() as MainActivity).navigateToTab(R.id.nav_analytics)
        }
        binding.btnAddIncome.setOnClickListener {
            startActivity(Intent(requireContext(), AddIncomeActivity::class.java))
        }
        binding.btnRecurring.setOnClickListener {
            startActivity(Intent(requireContext(), RecurringActivity::class.java))
        }
        binding.cardBudget.setOnClickListener { showBudgetDialog() }
        binding.btnSetBudget.setOnClickListener { showBudgetDialog() }
        binding.btnSettings.setOnClickListener {
            (requireActivity() as MainActivity).showFullScreenFragment(SettingsFragment())
        }
        binding.btnViewGoals.setOnClickListener {
            (requireActivity() as MainActivity).showFullScreenFragment(GoalsFragment())
        }

        val adView = AdView(requireContext()).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = AdIds.BANNER
        }
        bannerAdView = adView
        binding.bannerAdContainer.addView(adView)
        adView.loadAd(AdRequest.Builder().build())

        loadNativeAd()
    }

    override fun onPause() {
        super.onPause()
        bannerAdView?.pause()
    }

    override fun onResume() {
        super.onResume()
        bannerAdView?.resume()
        updateProfileRow()
        refreshStats()
    }

    override fun onDestroyView() {
        bannerAdView?.destroy()
        bannerAdView = null
        nativeAd?.destroy()
        nativeAd = null
        super.onDestroyView()
        _binding = null
    }

    private fun updateProfileRow() {
        val name = userPrefs.userName
        val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        binding.tvAvatar.text = initial
        binding.tvProfileGreeting.text = if (name.isNotBlank()) "Hi, $name!" else "Hi there!"
        binding.tvGreeting.text = getGreeting(name)
    }

    private fun refreshStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Recurring auto-add + due-soon reminders (write op, needs IO)
            val hasRecurring = withContext(Dispatchers.IO) { applyRecurringExpenses() }
            if (budgetPrefs.hasBudget || hasRecurring) ensureNotificationPermission()

            // Fetch all dashboard data in one background pass
            val data = withContext(Dispatchers.IO) {
                DashboardData(
                    totalExpenses = db.getTotalAmount(),
                    totalIncome   = db.getTotalIncome(),
                    totalCount    = db.getTotalCount(),
                    monthTotal    = db.getCurrentMonthTotal(),
                    monthIncome   = db.getCurrentMonthIncome(),
                    lastMonthTotal = db.getLastMonthTotal(),
                    cats          = db.getCategoryTotals(),
                    recent        = db.getRecentExpenses(5),
                    daily         = db.getDailySpending(7),
                    goals         = db.getAllGoals(),
                    budget        = budgetPrefs.monthlyBudget,
                    hasBudget     = budgetPrefs.hasBudget
                )
            }

            if (_binding == null) return@launch
            bindDashboard(data)
            checkBudgetAlert(data)
        }
    }

    /** Asks for notification permission at most once — declined once means don't nag again. */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (NotificationHelper.hasPermission(requireContext())) return
        if (reminderPrefs.wasAlerted("permission_asked")) return
        reminderPrefs.markAlerted("permission_asked")
        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun checkBudgetAlert(data: DashboardData) {
        if (!data.hasBudget || data.budget <= 0) return
        val percent = ((data.monthTotal / data.budget) * 100).toInt()
        val yearMonth = "%04d-%02d".format(Calendar.getInstance()[Calendar.YEAR], Calendar.getInstance()[Calendar.MONTH] + 1)
        val key = "budget_$yearMonth"
        if (percent >= reminderPrefs.budgetAlertThreshold && !reminderPrefs.wasAlerted(key)) {
            reminderPrefs.markAlerted(key)
            NotificationHelper.postBudgetAlert(requireContext(), percent)
        }
    }

    private fun bindDashboard(data: DashboardData) {
        val net = data.totalIncome - data.totalExpenses

        binding.tvTotalAmount.text = fmt.format(data.totalExpenses)
        binding.tvTotalTransactions.text = data.totalCount.toString()
        binding.tvMonthAmount.text = fmt.format(data.monthTotal)

        binding.tvTotalIncome.text = fmt.format(data.totalIncome)
        binding.tvOverviewExpenses.text = fmt.format(data.totalExpenses)
        binding.tvNetBalance.text = fmt.format(abs(net))
        val netColor = if (net >= 0) Color.parseColor("#22C55E")
                       else ContextCompat.getColor(requireContext(), R.color.md3_error)
        binding.tvNetBalance.setTextColor(netColor)

        if (data.monthIncome > 0) {
            val rate = ((data.monthIncome - data.monthTotal) / data.monthIncome * 100).toInt().coerceAtLeast(0)
            binding.tvSavingsInfo.text = getString(R.string.savings_rate_info, rate)
            binding.tvSavingsInfo.visibility = View.VISIBLE
        } else {
            binding.tvSavingsInfo.visibility = View.GONE
        }

        binding.tvInsight.text = generateInsight(data)

        binding.tvCategorySummary.text = if (data.cats.isEmpty()) {
            getString(R.string.no_expenses_yet)
        } else {
            data.cats.entries.joinToString("\n") { (cat, amt) -> "• $cat:  ${fmt.format(amt)}" }
        }

        updateBudgetCard(data)
        updateHealthScore(data)
        updateSpendingChart(data.daily)
        updateGoalsCard(data.goals)
        loadRecentTransactions(data.recent)
    }

    /** Auto-adds due recurring expenses and reminds about ones due soon. Returns true if any exist. */
    private fun applyRecurringExpenses(): Boolean {
        val recurring = db.getAllRecurring()
        if (recurring.isEmpty()) return false
        val cal = Calendar.getInstance()
        val today = cal[Calendar.DAY_OF_MONTH]
        val yearMonth = "%04d-%02d".format(cal[Calendar.YEAR], cal[Calendar.MONTH] + 1)
        recurring.forEach { rec ->
            if (today >= rec.dayOfMonth && !db.hasExpenseForMonth(rec.title, yearMonth)) {
                val date = "$yearMonth-%02d".format(rec.dayOfMonth)
                db.addExpense(Expense(title = rec.title, amount = rec.amount, category = rec.category, date = date, notes = rec.notes))
            } else if (today < rec.dayOfMonth) {
                val daysUntil = rec.dayOfMonth - today
                val key = "recur_${rec.id}_$yearMonth"
                if (daysUntil <= reminderPrefs.recurringReminderDays && !reminderPrefs.wasAlerted(key)) {
                    reminderPrefs.markAlerted(key)
                    NotificationHelper.postRecurringReminder(requireContext(), rec.id, rec.title, daysUntil)
                }
            }
        }
        return true
    }

    private fun generateInsight(data: DashboardData): String {
        val name = userPrefs.userName
        val you = if (name.isNotBlank()) name else "You"

        if (data.totalCount == 0) return getString(R.string.insight_welcome)

        if (data.lastMonthTotal > 0 && data.monthTotal > 0) {
            val pct = ((data.monthTotal - data.lastMonthTotal) / data.lastMonthTotal * 100).toInt()
            if (pct >= 20) return getString(R.string.insight_spending_up, pct)
            if (pct <= -20) return "$you saved ${-pct}% more than last month. Keep it up!"
        }

        if (data.monthIncome > 0) {
            return if (data.monthTotal > data.monthIncome) getString(R.string.insight_over_income)
            else getString(R.string.insight_savings_rate, ((data.monthIncome - data.monthTotal) / data.monthIncome * 100).toInt())
        }

        val topCat = data.cats.keys.firstOrNull()
        return if (topCat != null) getString(R.string.insight_top_category, topCat)
        else getString(R.string.insight_on_track)
    }

    private fun updateHealthScore(data: DashboardData) {
        var score = 0

        if (data.hasBudget && data.budget > 0) {
            val ratio = data.monthTotal / data.budget
            score += when {
                ratio <= 0.7  -> 40
                ratio <= 0.85 -> 30
                ratio <= 1.0  -> 20
                ratio <= 1.2  -> 10
                else          -> 0
            }
        } else {
            score += 20
        }

        if (data.monthIncome > 0) {
            val savingsRate = (data.monthIncome - data.monthTotal) / data.monthIncome
            score += when {
                savingsRate >= 0.3 -> 40
                savingsRate >= 0.2 -> 35
                savingsRate >= 0.1 -> 25
                savingsRate >= 0.0 -> 15
                else               -> 0
            }
        } else {
            score += 20
        }

        score += when {
            data.totalCount >= 20 -> 20
            data.totalCount >= 10 -> 15
            data.totalCount >= 5  -> 10
            data.totalCount > 0   -> 5
            else                  -> 0
        }

        score = score.coerceIn(0, 100)

        val (color, label) = when {
            score >= 80 -> Color.parseColor("#22C55E") to getString(R.string.health_excellent)
            score >= 60 -> Color.parseColor("#3B82F6") to getString(R.string.health_good)
            score >= 40 -> Color.parseColor("#F59E0B") to getString(R.string.health_fair)
            else        -> ContextCompat.getColor(requireContext(), R.color.md3_error) to getString(R.string.health_needs_work)
        }

        binding.tvHealthScore.text = score.toString()
        binding.tvHealthScore.setTextColor(color)
        binding.tvHealthLabel.text = label
        binding.tvHealthLabel.setTextColor(color)
        binding.progressHealth.progress = score
        binding.progressHealth.setIndicatorColor(color)
    }

    private fun updateSpendingChart(daily: List<Pair<String, Double>>) {
        val maxAmount = daily.maxOfOrNull { it.second } ?: 0.0
        val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.md3_primary)

        binding.llSpendingChart.removeAllViews()

        val hasData = daily.any { it.second > 0 }
        binding.tvChartEmpty.visibility = if (hasData) View.GONE else View.VISIBLE
        if (!hasData) return

        val chartHeight = (72 * resources.displayMetrics.density).toInt()
        val minBarHeight = (4 * resources.displayMetrics.density).toInt()

        daily.forEach { (dateStr, amount) ->
            val col = android.widget.LinearLayout(requireContext()).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f
                )
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                setPadding(4, 0, 4, 0)
            }

            val barHeight = if (maxAmount > 0)
                ((amount / maxAmount) * (chartHeight - 20 * resources.displayMetrics.density)).toInt()
                    .coerceAtLeast(if (amount > 0) minBarHeight else 0)
            else 0

            val bar = View(requireContext()).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, barHeight
                ).also { it.bottomMargin = (2 * resources.displayMetrics.density).toInt() }
                background = GradientDrawable().apply {
                    cornerRadius = 6 * resources.displayMetrics.density
                    setColor(if (amount > 0) primaryColor else Color.parseColor("#E5E7EB"))
                }
                alpha = if (amount > 0) 1f else 0.3f
            }

            val dayOfWeek = try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val cal = java.util.Calendar.getInstance().also { it.time = sdf.parse(dateStr)!! }
                dayNames[cal[java.util.Calendar.DAY_OF_WEEK] - 1]
            } catch (_: Exception) { "?" }

            val label = android.widget.TextView(requireContext()).apply {
                text = dayOfWeek
                textSize = 9f
                gravity = android.view.Gravity.CENTER
                setTextColor(ContextCompat.getColor(requireContext(), R.color.md3_on_surface_variant))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            col.addView(bar)
            col.addView(label)
            binding.llSpendingChart.addView(col)
        }
    }

    private fun updateBudgetCard(data: DashboardData) {
        val budget = data.budget
        val spent = data.monthTotal

        if (!data.hasBudget) {
            binding.llBudgetInfo.visibility = View.GONE
            binding.progressBudget.visibility = View.GONE
            binding.tvBudgetStatus.text = getString(R.string.budget_not_set)
            binding.btnSetBudget.text = getString(R.string.set_budget)
            return
        }

        val pct = ((spent / budget) * 100).toInt().coerceAtLeast(0)
        binding.llBudgetInfo.visibility = View.VISIBLE
        binding.progressBudget.visibility = View.VISIBLE
        binding.tvBudgetSpent.text = fmt.format(spent)
        binding.tvBudgetLimit.text = fmt.format(budget)
        binding.progressBudget.progress = pct.coerceAtMost(100)
        binding.btnSetBudget.text = getString(R.string.edit_budget)

        val (color, statusText) = when {
            pct > 100 -> ContextCompat.getColor(requireContext(), R.color.md3_error) to
                    getString(R.string.budget_exceeded, fmt.format(spent - budget))
            pct >= 80 -> Color.parseColor("#F59E0B") to getString(R.string.budget_warning, pct)
            else      -> Color.parseColor("#22C55E") to getString(R.string.budget_on_track, pct)
        }
        binding.progressBudget.setIndicatorColor(color)
        binding.tvBudgetStatus.setTextColor(color)
        binding.tvBudgetStatus.text = statusText
    }

    private fun showBudgetDialog() {
        val inputLayout = TextInputLayout(requireContext(), null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = getString(R.string.budget_hint)
            setPadding(56, 24, 56, 8)
        }
        val editText = TextInputEditText(inputLayout.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (budgetPrefs.hasBudget) {
                val v = budgetPrefs.monthlyBudget
                setText(if (v % 1.0 == 0.0) v.toLong().toString() else v.toString())
                selectAll()
            }
        }
        inputLayout.addView(editText)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.budget_dialog_title)
            .setView(inputLayout)
            .setPositiveButton(R.string.budget_save) { _, _ ->
                val amount = editText.text.toString().trim().toDoubleOrNull()
                if (amount != null && amount > 0) {
                    budgetPrefs.monthlyBudget = amount
                    refreshStats()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateGoalsCard(goals: List<Goal>) {
        val top2 = goals.take(2)
        binding.llGoalsPreview.removeAllViews()

        if (top2.isEmpty()) {
            binding.tvNoGoals.visibility = View.VISIBLE
            return
        }
        binding.tvNoGoals.visibility = View.GONE

        val dp = resources.displayMetrics.density
        top2.forEach { goal ->
            val row = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
            }

            val goalColor = try { Color.parseColor(goal.color) } catch (_: Exception) { Color.parseColor("#22C55E") }

            val colorBar = View(requireContext()).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams((4 * dp).toInt(), (28 * dp).toInt())
                    .also { it.marginEnd = (10 * dp).toInt() }
                background = GradientDrawable().apply { cornerRadius = 4f; setColor(goalColor) }
            }

            val nameText = android.widget.TextView(requireContext()).apply {
                text = goal.name
                textSize = 13f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.md3_on_surface))
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val pctText = android.widget.TextView(requireContext()).apply {
                text = "${goal.progress}%"
                textSize = 12f
                setTextColor(goalColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginStart = (8 * dp).toInt() }
            }

            row.addView(colorBar)
            row.addView(nameText)
            row.addView(pctText)
            binding.llGoalsPreview.addView(row)
        }
    }

    private fun loadRecentTransactions(recent: List<Expense>) {
        binding.llRecentTransactions.removeAllViews()

        if (recent.isEmpty()) {
            binding.tvNoRecent.visibility = View.VISIBLE
            return
        }
        binding.tvNoRecent.visibility = View.GONE

        recent.forEach { expense ->
            val row = ItemRecentTransactionBinding.inflate(layoutInflater, binding.llRecentTransactions, false)
            row.tvRecentTitle.text = expense.title
            row.tvRecentAmount.text = fmt.format(expense.amount)

            val color = categoryColors[expense.category] ?: "#90A4AE"
            row.vDot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(color))
            }
            binding.llRecentTransactions.addView(row.root)

            if (expense != recent.last()) {
                val divider = View(requireContext())
                divider.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                divider.setBackgroundColor("#12000000".toColorInt())
                binding.llRecentTransactions.addView(divider)
            }
        }
    }

    private fun loadNativeAd() {
        AdLoader.Builder(requireContext(), AdIds.NATIVE)
            .forNativeAd { ad ->
                if (_binding == null) { ad.destroy(); return@forNativeAd }
                nativeAd?.destroy()
                nativeAd = ad
                populateNativeAd(ad)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    _binding?.nativeAdContainer?.visibility = View.GONE
                }
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    private fun populateNativeAd(ad: NativeAd) {
        val b = _binding ?: return
        val adView = layoutInflater.inflate(R.layout.ad_native_small, b.nativeAdContainer, false) as NativeAdView

        val headline = adView.findViewById<TextView>(R.id.ad_headline)
        val body     = adView.findViewById<TextView>(R.id.ad_body)
        val icon     = adView.findViewById<ImageView>(R.id.ad_app_icon)
        val cta      = adView.findViewById<MaterialButton>(R.id.ad_call_to_action)

        adView.headlineView       = headline
        adView.bodyView           = body
        adView.iconView           = icon
        adView.callToActionView   = cta

        headline.text = ad.headline
        body.text     = ad.body
        cta.text      = ad.callToAction
        cta.visibility = if (ad.callToAction != null) View.VISIBLE else View.GONE
        ad.icon?.drawable?.let { icon.setImageDrawable(it); icon.visibility = View.VISIBLE }
            ?: run { icon.visibility = View.GONE }

        adView.setNativeAd(ad)
        b.nativeAdContainer.removeAllViews()
        b.nativeAdContainer.addView(adView)
        b.nativeAdContainer.visibility = View.VISIBLE
    }

    private fun getGreeting(name: String): String {
        val base = when (Calendar.getInstance()[Calendar.HOUR_OF_DAY]) {
            in 0..11  -> "Good Morning"
            in 12..17 -> "Good Afternoon"
            else      -> "Good Evening"
        }
        return if (name.isNotBlank()) "$base, $name 👋" else "$base 👋"
    }
}
