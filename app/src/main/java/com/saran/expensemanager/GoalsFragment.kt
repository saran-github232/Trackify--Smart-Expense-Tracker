package com.saran.expensemanager

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.saran.expensemanager.databinding.FragmentGoalsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat

class GoalsFragment : Fragment() {

    private var _binding: FragmentGoalsBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: DatabaseHelper
    private lateinit var adapter: GoalAdapter
    private lateinit var fmt: NumberFormat

    private val goalColors = listOf(
        "#22C55E", "#3B82F6", "#8B5CF6", "#F59E0B",
        "#EF4444", "#EC4899", "#14B8A6", "#F97316"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGoalsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = DatabaseHelper.getInstance(requireContext())
        fmt = CurrencyFormatter.currencyInstance(requireContext())

        binding.appBar.applyTopSystemBarInsetPadding()
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        adapter = GoalAdapter(
            onAddMoney = { goal -> showAddMoneyDialog(goal) },
            onDelete = { goal -> confirmDelete(goal) }
        )
        binding.rvGoals.layoutManager = LinearLayoutManager(requireContext())
        binding.rvGoals.adapter = adapter
        binding.rvGoals.isNestedScrollingEnabled = false

        binding.fabAddGoal.setOnClickListener { showAddGoalDialog() }
    }

    override fun onResume() {
        super.onResume()
        loadGoals()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadGoals() {
        viewLifecycleOwner.lifecycleScope.launch {
            val goals = withContext(Dispatchers.IO) { db.getAllGoals() }
            if (_binding == null) return@launch

            adapter.submitList(goals)

            val totalSaved = goals.sumOf { it.savedAmount }
            val totalTarget = goals.sumOf { it.targetAmount }
            binding.tvTotalSaved.text = fmt.format(totalSaved)
            binding.tvTotalTarget.text = fmt.format(totalTarget)

            binding.llGoalsEmpty.visibility = if (goals.isEmpty()) View.VISIBLE else View.GONE
            binding.rvGoals.visibility = if (goals.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun showAddGoalDialog() {
        val nameLayout = TextInputLayout(requireContext(), null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = getString(R.string.goal_name_hint)
            setPadding(56, 20, 56, 4)
        }
        val nameEdit = TextInputEditText(nameLayout.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        nameLayout.addView(nameEdit)

        val targetLayout = TextInputLayout(requireContext(), null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = getString(R.string.goal_target_hint)
            prefixText = "₹ "
            setPadding(56, 0, 56, 4)
        }
        val targetEdit = TextInputEditText(targetLayout.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        targetLayout.addView(targetEdit)

        val deadlineLayout = TextInputLayout(requireContext(), null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = getString(R.string.goal_deadline_hint)
            setPadding(56, 0, 56, 4)
        }
        val deadlineEdit = TextInputEditText(deadlineLayout.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        deadlineLayout.addView(deadlineEdit)

        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(nameLayout)
            addView(targetLayout)
            addView(deadlineLayout)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.new_goal)
            .setView(container)
            .setPositiveButton(R.string.add_goal) { _, _ ->
                val name = nameEdit.text.toString().trim()
                val target = targetEdit.text.toString().trim().toDoubleOrNull()
                val deadline = deadlineEdit.text.toString().trim()

                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.goal_error_name, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (target == null || target <= 0) {
                    Toast.makeText(requireContext(), R.string.goal_error_target, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        val color = goalColors[db.getAllGoals().size % goalColors.size]
                        db.addGoal(Goal(name = name, targetAmount = target, color = color, deadline = deadline))
                    }
                    loadGoals()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddMoneyDialog(goal: Goal) {
        val amountLayout = TextInputLayout(requireContext(), null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = getString(R.string.amount_to_add_hint)
            prefixText = "₹ "
            setPadding(56, 20, 56, 8)
        }
        val amountEdit = TextInputEditText(amountLayout.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        amountLayout.addView(amountEdit)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.add_to_goal_title) + ": ${goal.name}")
            .setMessage("Current: ${fmt.format(goal.savedAmount)} / ${fmt.format(goal.targetAmount)}")
            .setView(amountLayout)
            .setPositiveButton(R.string.add_money) { _, _ ->
                val add = amountEdit.text.toString().trim().toDoubleOrNull() ?: return@setPositiveButton
                val newSaved = (goal.savedAmount + add).coerceAtMost(goal.targetAmount)
                val updated = goal.copy(savedAmount = newSaved)
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) { db.updateGoal(updated) }
                    loadGoals()
                    if (updated.isCompleted) {
                        Snackbar.make(binding.root, getString(R.string.goal_completed), Snackbar.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(goal: Goal) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Goal")
            .setMessage("Remove \"${goal.name}\"?")
            .setPositiveButton(R.string.delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) { db.deleteGoal(goal.id) }
                    loadGoals()
                    Snackbar.make(binding.root, getString(R.string.goal_deleted), Snackbar.LENGTH_LONG)
                        .setAction(getString(R.string.undo)) {
                            viewLifecycleOwner.lifecycleScope.launch {
                                withContext(Dispatchers.IO) { db.addGoal(goal.copy(id = 0)) }
                                loadGoals()
                            }
                        }.show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
