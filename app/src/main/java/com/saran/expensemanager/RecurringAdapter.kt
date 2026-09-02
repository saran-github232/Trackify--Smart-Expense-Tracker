package com.saran.expensemanager

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.saran.expensemanager.databinding.ItemRecurringBinding

class RecurringAdapter(
    private val onDelete: (Recurring) -> Unit
) : ListAdapter<Recurring, RecurringAdapter.ViewHolder>(RecurringDiff()) {

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

    inner class ViewHolder(val b: ItemRecurringBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemRecurringBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val b = holder.b
        b.tvRecurringTitle.text = item.title
        b.tvRecurringDetail.text = "Every ${ordinal(item.dayOfMonth)} • ${item.category}"
        b.tvRecurringAmount.text = "₹${String.format("%.0f", item.amount)}"

        val color = Color.parseColor(categoryColors[item.category] ?: "#90A4AE")
        b.vRecurringBadge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        b.btnDeleteRecurring.setOnClickListener { onDelete(item) }
    }

    private fun ordinal(n: Int): String = when {
        n in 11..13 -> "${n}th"
        n % 10 == 1 -> "${n}st"
        n % 10 == 2 -> "${n}nd"
        n % 10 == 3 -> "${n}rd"
        else -> "${n}th"
    }

    class RecurringDiff : DiffUtil.ItemCallback<Recurring>() {
        override fun areItemsTheSame(a: Recurring, b: Recurring) = a.id == b.id
        override fun areContentsTheSame(a: Recurring, b: Recurring) = a == b
    }
}
