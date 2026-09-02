package com.saran.expensemanager

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.saran.expensemanager.databinding.ItemGoalBinding
import java.text.NumberFormat
import java.util.Locale

class GoalAdapter(
    private val onAddMoney: (Goal) -> Unit,
    private val onDelete: (Goal) -> Unit
) : ListAdapter<Goal, GoalAdapter.ViewHolder>(DIFF) {

    private val fmt = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Goal>() {
            override fun areItemsTheSame(a: Goal, b: Goal) = a.id == b.id
            override fun areContentsTheSame(a: Goal, b: Goal) = a == b
        }
    }

    inner class ViewHolder(private val b: ItemGoalBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(goal: Goal) {
            b.tvGoalName.text = goal.name
            b.tvGoalSaved.text = fmt.format(goal.savedAmount)
            b.tvGoalTarget.text = fmt.format(goal.targetAmount)
            b.tvGoalPercent.text = "${goal.progress}%"

            if (goal.deadline.isNotBlank()) {
                b.tvGoalDeadline.text = "By ${goal.deadline}"
                b.tvGoalDeadline.visibility = android.view.View.VISIBLE
            } else {
                b.tvGoalDeadline.visibility = android.view.View.GONE
            }

            val color = try { Color.parseColor(goal.color) } catch (_: Exception) { Color.parseColor("#22C55E") }

            b.vGoalColor.background = GradientDrawable().apply {
                cornerRadius = 8f
                setColor(color)
            }

            b.progressGoal.progress = goal.progress
            b.progressGoal.setIndicatorColor(color)
            b.tvGoalPercent.setTextColor(color)

            b.btnAddMoney.strokeColor = ColorStateList.valueOf(color)
            b.btnAddMoney.setTextColor(color)
            b.btnAddMoney.iconTint = ColorStateList.valueOf(color)

            if (goal.isCompleted) {
                b.tvGoalName.text = "${goal.name} ✓"
                b.btnAddMoney.isEnabled = false
                b.btnAddMoney.alpha = 0.4f
            } else {
                b.tvGoalName.text = goal.name
                b.btnAddMoney.isEnabled = true
                b.btnAddMoney.alpha = 1f
            }

            b.btnAddMoney.setOnClickListener { onAddMoney(goal) }
            b.btnDeleteGoal.setOnClickListener { onDelete(goal) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemGoalBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))
}
