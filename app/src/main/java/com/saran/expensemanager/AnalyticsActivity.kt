package com.saran.expensemanager

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.saran.expensemanager.databinding.ActivityAnalyticsBinding
import com.saran.expensemanager.databinding.ItemCategoryStatBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class AnalyticsActivity : EdgeToEdgeActivity() {

    private lateinit var binding: ActivityAnalyticsBinding
    private lateinit var db: DatabaseHelper
    private val fmt = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

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

    private val categoryIcons = mapOf(
        "Food" to R.drawable.ic_food,
        "Travel" to R.drawable.ic_travel,
        "Shopping" to R.drawable.ic_shopping,
        "Bills" to R.drawable.ic_bills,
        "Health" to R.drawable.ic_health,
        "Entertainment" to R.drawable.ic_entertainment,
        "Education" to R.drawable.ic_education,
        "Other" to R.drawable.ic_other
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.appBar.applyTopSystemBarInsetPadding()
        binding.nestedScroll.applyBottomSystemBarInsetPadding()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        db = DatabaseHelper.getInstance(this)
    }

    override fun onResume() {
        super.onResume()
        loadAnalytics()
    }

    private data class AnalyticsData(
        val week: Double,
        val month: Double,
        val year: Double,
        val avgDay: Double,
        val cats: Map<String, Double>
    )

    private fun loadAnalytics() {
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                AnalyticsData(
                    week = db.getWeekTotal(),
                    month = db.getCurrentMonthTotal(),
                    year = db.getYearTotal(),
                    avgDay = db.getAverageDailySpend(),
                    cats = db.getCategoryTotals()
                )
            }
            bindAnalytics(data)
        }
    }

    private fun bindAnalytics(data: AnalyticsData) {
        binding.tvWeekTotal.text = fmt.format(data.week)
        binding.tvMonthTotal.text = fmt.format(data.month)
        binding.tvYearTotal.text = fmt.format(data.year)
        binding.tvAvgDay.text = fmt.format(data.avgDay)

        val cats = data.cats
        val grandTotal = cats.values.sum()

        binding.llCategoryStats.removeAllViews()

        if (cats.isEmpty()) {
            binding.tvAnalyticsEmpty.visibility = View.VISIBLE
            binding.llCategoryStats.visibility = View.GONE
            return
        }

        binding.tvAnalyticsEmpty.visibility = View.GONE
        binding.llCategoryStats.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(this)
        cats.entries.forEachIndexed { index, (cat, amount) ->
            val row = ItemCategoryStatBinding.inflate(inflater, binding.llCategoryStats, false)
            row.tvStatCategory.text = cat
            row.tvStatAmount.text = fmt.format(amount)

            val pct = if (grandTotal > 0) (amount / grandTotal * 100).toInt() else 0
            row.tvStatPercent.text = "$pct%"

            val colorHex = categoryColors[cat] ?: "#90A4AE"
            row.vStatBadgeBg.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(colorHex))
            }
            row.ivStatIcon.setImageResource(categoryIcons[cat] ?: R.drawable.ic_other)

            row.progressStat.setIndicatorColor(Color.parseColor(colorHex))
            row.progressStat.progress = pct

            if (index < cats.size - 1) {
                val divider = View(this)
                divider.layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1
                )
                divider.setBackgroundColor("#12000000".toColorInt())
                binding.llCategoryStats.addView(row.root)
                binding.llCategoryStats.addView(divider)
            } else {
                binding.llCategoryStats.addView(row.root)
            }
        }

        val topCat = cats.keys.firstOrNull() ?: ""
        binding.tvTopCategory.text = if (topCat.isNotEmpty()) topCat else getString(R.string.no_data)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

