package com.saran.expensemanager

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.saran.expensemanager.databinding.ItemExpenseBinding
import java.text.NumberFormat
import java.util.Locale

class ExpenseAdapter(
    private val onEdit: (Expense) -> Unit,
    private val onDelete: (Expense) -> Unit,
) : ListAdapter<Expense, ExpenseAdapter.ViewHolder>(ExpenseDiffCallback()) {

    private val fmt = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
    private var syncPrefs: SheetSyncPrefs? = null

    private val categoryColors = mapOf(
        "Food" to "#FF6B6B",
        "Travel" to "#4ECDC4",
        "Shopping" to "#45B7D1",
        "Bills" to "#FFA07A",
        "Health" to "#66BB6A",
        "Entertainment" to "#BA68C8",
        "Education" to "#42A5F5",
        "Other" to "#90A4AE",
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemExpenseBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val b: ItemExpenseBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(expense: Expense) {
            b.tvTitle.text = expense.title
            b.tvAmount.text = fmt.format(expense.amount)
            b.tvCategory.text = expense.category
            b.tvDate.text = expense.date

            val colorHex = categoryColors[expense.category] ?: "#90A4AE"
            b.vBadgeBg.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorHex.toColorInt())
            }

            b.ivCategoryIcon.setImageResource(categoryIcons[expense.category] ?: R.drawable.ic_other)

            val prefs = syncPrefs ?: SheetSyncPrefs(b.root.context).also { syncPrefs = it }
            if (prefs.enabled) {
                val (labelRes, colorRes) = when (expense.syncStatus) {
                    DatabaseHelper.SYNC_SYNCED -> R.string.sync_status_synced to R.color.md3_success
                    DatabaseHelper.SYNC_FAILED -> R.string.sync_status_failed to R.color.md3_error
                    else -> R.string.sync_status_pending to R.color.md3_warning
                }
                b.tvSyncStatus.visibility = View.VISIBLE
                b.tvSyncStatus.setText(labelRes)
                b.tvSyncStatus.setTextColor(ContextCompat.getColor(b.root.context, colorRes))
            } else {
                b.tvSyncStatus.visibility = View.GONE
            }

            b.btnEdit.setOnClickListener { onEdit(expense) }
            b.btnDelete.setOnClickListener { onDelete(expense) }
        }
    }

    class ExpenseDiffCallback : DiffUtil.ItemCallback<Expense>() {
        override fun areItemsTheSame(oldItem: Expense, newItem: Expense): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Expense, newItem: Expense): Boolean =
            oldItem == newItem
    }
}
