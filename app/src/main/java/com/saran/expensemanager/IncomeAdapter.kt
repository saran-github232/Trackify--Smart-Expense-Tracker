package com.saran.expensemanager

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.saran.expensemanager.databinding.ItemIncomeBinding
import java.text.NumberFormat
import java.util.Locale

class IncomeAdapter(
    private val onDelete: (Income) -> Unit,
    private val onEdit: (Income) -> Unit = {}
) : ListAdapter<Income, IncomeAdapter.ViewHolder>(DIFF) {

    private val fmt = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    private val sourceColors = mapOf(
        "Salary" to "#4CAF50",
        "Freelance" to "#2196F3",
        "Business" to "#FF9800",
        "Investment" to "#9C27B0",
        "Rental" to "#00BCD4",
        "Gift" to "#E91E63",
        "Other" to "#9E9E9E"
    )

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Income>() {
            override fun areItemsTheSame(a: Income, b: Income) = a.id == b.id
            override fun areContentsTheSame(a: Income, b: Income) = a == b
        }
    }

    inner class ViewHolder(private val binding: ItemIncomeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(income: Income) {
            binding.tvIncomeTitle.text = income.title
            binding.tvIncomeSource.text = if (income.type == DatabaseHelper.INCOME_TYPE_TRANSFER) {
                "${income.source} · Transfer"
            } else {
                income.source
            }
            binding.tvIncomeDate.text = income.date
            binding.tvIncomeAmount.text = "+${fmt.format(income.amount)}"

            val colorHex = sourceColors[income.source] ?: "#9E9E9E"
            val color = Color.parseColor(colorHex)
            binding.vSourceBadge.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            binding.tvSourceInitial.text = income.source.firstOrNull()?.toString() ?: "?"

            binding.btnDeleteIncome.setOnClickListener { onDelete(income) }
            binding.root.setOnClickListener { onEdit(income) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemIncomeBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))
}
