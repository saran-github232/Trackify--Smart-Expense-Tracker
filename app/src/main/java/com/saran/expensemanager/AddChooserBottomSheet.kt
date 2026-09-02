package com.saran.expensemanager

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.saran.expensemanager.databinding.BottomSheetAddBinding

class AddChooserBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddBinding? = null
    private val binding get() = _binding!!

    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spokenText = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        } else null
        if (spokenText.isNullOrBlank()) return@registerForActivityResult
        dismiss()
        val parsed = VoiceExpenseParser.parse(spokenText)
        startActivity(Intent(requireContext(), AddExpenseActivity::class.java).apply {
            putExtra(AddExpenseActivity.EXTRA_PREFILL_TITLE, parsed.title)
            putExtra(AddExpenseActivity.EXTRA_PREFILL_AMOUNT, parsed.amount ?: -1.0)
            putExtra(AddExpenseActivity.EXTRA_PREFILL_CATEGORY, parsed.category)
            putExtra(AddExpenseActivity.EXTRA_PREFILL_NOTES, spokenText)
        })
    }

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

        binding.cardVoiceAdd.setOnClickListener { launchVoiceRecognition() }
    }

    private fun launchVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt))
        }
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            voiceLauncher.launch(intent)
        } else {
            Toast.makeText(requireContext(), R.string.voice_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
