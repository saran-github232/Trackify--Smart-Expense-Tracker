package com.saran.expensemanager

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.saran.expensemanager.databinding.BottomSheetAddBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class AddChooserBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddBinding? = null
    private val binding get() = _binding!!
    private var pendingReceiptUri: Uri? = null

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

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingReceiptUri
        if (success && uri != null) recognizeReceipt(uri) else pendingReceiptUri = null
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else Toast.makeText(requireContext(), R.string.camera_permission_needed, Toast.LENGTH_SHORT).show()
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
        binding.cardScanReceipt.setOnClickListener { requestCameraAndScan() }
    }

    private fun requestCameraAndScan() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val fileName = "receipt_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())}.jpg"
        val file = File(requireContext().cacheDir, fileName)
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", file)
        pendingReceiptUri = uri
        cameraLauncher.launch(uri)
    }

    private fun recognizeReceipt(uri: Uri) {
        val image = runCatching { InputImage.fromFilePath(requireContext(), uri) }.getOrNull()
        if (image == null) {
            Toast.makeText(requireContext(), R.string.receipt_scan_failed, Toast.LENGTH_SHORT).show()
            return
        }
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
            .addOnSuccessListener { result ->
                if (_binding == null) return@addOnSuccessListener
                dismiss()
                val parsed = ReceiptParser.parse(result.text)
                startActivity(Intent(requireContext(), AddExpenseActivity::class.java).apply {
                    putExtra(AddExpenseActivity.EXTRA_PREFILL_TITLE, getString(R.string.scan_receipt))
                    putExtra(AddExpenseActivity.EXTRA_PREFILL_AMOUNT, parsed.amount ?: -1.0)
                    parsed.date?.let { putExtra(AddExpenseActivity.EXTRA_PREFILL_DATE, it) }
                    putExtra(AddExpenseActivity.EXTRA_PREFILL_NOTES, parsed.rawText.take(500))
                })
            }
            .addOnFailureListener {
                if (_binding != null) Toast.makeText(requireContext(), R.string.receipt_scan_failed, Toast.LENGTH_SHORT).show()
            }
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
