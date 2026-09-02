package com.saran.expensemanager

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.saran.expensemanager.databinding.BottomSheetAddBinding

class AddChooserBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetAddBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.chooserRoot.applyBottomSystemBarInsetPadding()

        binding.cardAddExpense.setOnClickListener {
            dismiss()
            startActivity(Intent(requireContext(), AddExpenseActivity::class.java))
        }

        binding.cardAddIncome.setOnClickListener {
            dismiss()
            startActivity(Intent(requireContext(), AddIncomeActivity::class.java))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
