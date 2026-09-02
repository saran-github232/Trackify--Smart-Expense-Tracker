package com.saran.expensemanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.saran.expensemanager.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: DatabaseHelper
    private lateinit var userPrefs: UserPrefs
    private lateinit var shakePrefs: ShakePrefs
    private lateinit var sheetSyncPrefs: SheetSyncPrefs
    private lateinit var currencyPrefs: CurrencyPrefs

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = DatabaseHelper.getInstance(requireContext())
        userPrefs = UserPrefs(requireContext())
        shakePrefs = ShakePrefs(requireContext())
        sheetSyncPrefs = SheetSyncPrefs(requireContext())
        currencyPrefs = CurrencyPrefs(requireContext())

        binding.appBar.applyTopSystemBarInsetPadding()
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        updateProfileSection()
        binding.llProfileRow.setOnClickListener { showEditNameDialog() }
        binding.llClearData.setOnClickListener { confirmClearData() }
        setupShakeSection()
        setupSheetSyncSection()
        setupCurrencySection()
        setupDataToolsSection()
    }

    // ── Import / Export / Backup / Restore ───────────────────────────────────

    private val importCsvLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importCsv(it) }
    }
    private val backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { writeBackup(it) }
    }
    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readBackupForConfirmation(it) }
    }

    private fun setupDataToolsSection() {
        binding.llImportCsv.setOnClickListener { importCsvLauncher.launch(arrayOf("text/*", "*/*")) }
        binding.llExportReport.setOnClickListener { showReportPeriodPicker() }
        binding.llBackupData.setOnClickListener {
            val name = "trackify_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.json"
            backupLauncher.launch(name)
        }
        binding.llRestoreData.setOnClickListener { restoreLauncher.launch(arrayOf("application/json", "*/*")) }
    }

    private fun importCsv(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    val parsed = CsvImportManager.parse(text)
                    parsed.imported.forEach { db.addExpense(it) }
                    parsed
                }
            }
            if (_binding == null) return@launch
            result.onSuccess { r ->
                SheetSyncManager.triggerSync(requireContext())
                Toast.makeText(requireContext(), getString(R.string.import_result, r.imported.size, r.skipped), Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(requireContext(), R.string.import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showReportPeriodPicker() {
        val options = arrayOf(getString(R.string.report_this_month), getString(R.string.report_this_year))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.export_report)
            .setItems(options) { _, which -> generateReport(monthly = which == 0) }
            .show()
    }

    private fun generateReport(monthly: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                if (monthly) PdfReportGenerator.generateMonthlyReport(requireContext()) else PdfReportGenerator.generateYearlyReport(requireContext())
            }
            if (_binding == null) return@launch
            if (file != null) {
                Toast.makeText(requireContext(), R.string.report_generated, Toast.LENGTH_SHORT).show()
                PdfReportGenerator.shareReport(requireContext(), file)
            } else {
                Toast.makeText(requireContext(), R.string.report_no_data, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun writeBackup(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val json = BackupManager.export(requireContext())
                    requireContext().contentResolver.openOutputStream(uri)?.use { it.write(json.toString(2).toByteArray()) }
                }.isSuccess
            }
            if (_binding == null) return@launch
            Toast.makeText(requireContext(), if (ok) R.string.backup_created else R.string.backup_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun readBackupForConfirmation(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val json = withContext(Dispatchers.IO) {
                runCatching {
                    val text = requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    JSONObject(text)
                }
            }
            if (_binding == null) return@launch
            json.onSuccess { parsed ->
                val counts = BackupManager.counts(parsed)
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.restore_confirm_title)
                    .setMessage(getString(R.string.restore_confirm_message, counts.expenses, counts.income, counts.recurring, counts.goals))
                    .setPositiveButton(R.string.restore_data) { _, _ -> performRestore(parsed) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }.onFailure {
                Toast.makeText(requireContext(), R.string.restore_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performRestore(json: JSONObject) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) { BackupManager.restore(requireContext(), json) }
            if (_binding == null) return@launch
            Toast.makeText(requireContext(), R.string.restore_done, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupCurrencySection() {
        updateCurrencyLabel()
        binding.llCurrencyRow.setOnClickListener { showCurrencyPicker() }
    }

    private fun updateCurrencyLabel() {
        binding.tvCurrentCurrency.text = currencyPrefs.currencyCode
    }

    private fun showCurrencyPicker() {
        val codes = CurrencyPrefs.SUPPORTED.keys.toList()
        val current = codes.indexOf(currencyPrefs.currencyCode).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.display_currency)
            .setSingleChoiceItems(codes.toTypedArray(), current) { dialog, which ->
                currencyPrefs.currencyCode = codes[which]
                updateCurrencyLabel()
                Toast.makeText(requireContext(), getString(R.string.currency_changed, codes[which]), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupShakeSection() {
        val sensorAvailable = ShakeDetector(requireContext()) {}.isAvailable
        if (!sensorAvailable) {
            binding.switchShake.isEnabled = false
            binding.tvShakeDesc.setText(R.string.shake_unavailable)
        }

        // Reflect saved state before wiring listeners so restoring it doesn't re-trigger them.
        binding.switchShake.isChecked = shakePrefs.enabled && sensorAvailable
        binding.llShakeOptions.visibility = if (binding.switchShake.isChecked) View.VISIBLE else View.GONE
        val checkedButtonId = when (shakePrefs.sensitivity) {
            ShakePrefs.SENSITIVITY_LOW -> R.id.btnSensLow
            ShakePrefs.SENSITIVITY_HIGH -> R.id.btnSensHigh
            else -> R.id.btnSensMedium
        }
        binding.toggleSensitivity.check(checkedButtonId)
        binding.switchVibrate.isChecked = shakePrefs.vibrate

        binding.switchShake.setOnCheckedChangeListener { _, checked ->
            shakePrefs.enabled = checked
            binding.llShakeOptions.visibility = if (checked) View.VISIBLE else View.GONE
            if (checked) showShakeOnboarding()
        }
        binding.toggleSensitivity.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            shakePrefs.sensitivity = when (checkedId) {
                R.id.btnSensLow -> ShakePrefs.SENSITIVITY_LOW
                R.id.btnSensHigh -> ShakePrefs.SENSITIVITY_HIGH
                else -> ShakePrefs.SENSITIVITY_MEDIUM
            }
        }
        binding.switchVibrate.setOnCheckedChangeListener { _, checked -> shakePrefs.vibrate = checked }
    }

    private fun showShakeOnboarding() {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.shake_onboarding_title)
            .setMessage(R.string.shake_onboarding_message)
            .setPositiveButton(R.string.save, null)
            .create()
        val testDetector = ShakeDetector(requireContext()) {
            Toast.makeText(requireContext(), R.string.shake_detected, Toast.LENGTH_SHORT).show()
        }.apply { thresholdG = shakePrefs.thresholdG }
        dialog.setOnShowListener { testDetector.start() }
        dialog.setOnDismissListener { testDetector.stop() }
        dialog.show()
    }

    private fun setupSheetSyncSection() {
        binding.switchSheetSync.isChecked = sheetSyncPrefs.enabled
        binding.llSheetSyncOptions.visibility = if (sheetSyncPrefs.enabled) View.VISIBLE else View.GONE
        binding.etSheetUrl.setText(sheetSyncPrefs.webAppUrl)
        binding.etSheetToken.setText(sheetSyncPrefs.secretToken)

        binding.switchSheetSync.setOnCheckedChangeListener { _, checked ->
            sheetSyncPrefs.enabled = checked
            binding.llSheetSyncOptions.visibility = if (checked) View.VISIBLE else View.GONE
            if (checked) SheetSyncManager.triggerSync(requireContext())
        }
        binding.etSheetUrl.addTextChangedListener(onTextChanged { sheetSyncPrefs.webAppUrl = it })
        binding.etSheetToken.addTextChangedListener(onTextChanged { sheetSyncPrefs.secretToken = it })

        binding.btnTestSheetSync.setOnClickListener { testSheetConnection() }
        binding.btnSyncNow.setOnClickListener {
            if (!sheetSyncPrefs.isConfigured) {
                Toast.makeText(requireContext(), R.string.sheet_sync_url_required, Toast.LENGTH_SHORT).show()
            } else {
                SheetSyncManager.triggerSync(requireContext())
                Toast.makeText(requireContext(), R.string.sheet_sync_started, Toast.LENGTH_SHORT).show()
            }
        }
        binding.tvSheetSyncGuide.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SHEET_SYNC_GUIDE_URL)))
        }
    }

    private fun testSheetConnection() {
        val url = binding.etSheetUrl.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(requireContext(), R.string.sheet_sync_url_required, Toast.LENGTH_SHORT).show()
            return
        }
        val token = binding.etSheetToken.text.toString().trim()
        binding.btnTestSheetSync.isEnabled = false
        binding.tvSheetSyncResult.visibility = View.VISIBLE
        binding.tvSheetSyncResult.setTextColor(ContextCompat.getColor(requireContext(), R.color.md3_on_surface_variant))
        binding.tvSheetSyncResult.setText(R.string.sheet_sync_testing)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = SheetSyncManager.testConnection(url, token)
            if (_binding == null) return@launch
            binding.btnTestSheetSync.isEnabled = true
            result.onSuccess { message ->
                binding.tvSheetSyncResult.setTextColor(ContextCompat.getColor(requireContext(), R.color.md3_success))
                binding.tvSheetSyncResult.text = getString(R.string.sheet_sync_success, message)
            }.onFailure { error ->
                binding.tvSheetSyncResult.setTextColor(ContextCompat.getColor(requireContext(), R.color.md3_error))
                binding.tvSheetSyncResult.text = getString(R.string.sheet_sync_failed, error.message ?: "Unknown error")
            }
        }
    }

    private fun onTextChanged(action: (String) -> Unit) = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) = action(s?.toString() ?: "")
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    companion object {
        private const val SHEET_SYNC_GUIDE_URL =
            "https://github.com/saran-github232/Trackify--Smart-Expense-Tracker/blob/main/GOOGLE_SHEETS_SETUP.md"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateProfileSection() {
        val name = userPrefs.userName
        binding.tvSettingsName.text = if (name.isNotBlank()) name else getString(R.string.set_your_name)
        binding.tvSettingsAvatar.text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    }

    private fun showEditNameDialog() {
        val inputLayout = TextInputLayout(requireContext(), null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = getString(R.string.your_name_hint)
            setPadding(56, 24, 56, 8)
        }
        val editText = TextInputEditText(inputLayout.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            if (userPrefs.userName.isNotBlank()) {
                setText(userPrefs.userName)
                selectAll()
            }
        }
        inputLayout.addView(editText)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_name)
            .setView(inputLayout)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    userPrefs.userName = name
                    updateProfileSection()
                    Toast.makeText(requireContext(), "Name updated!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmClearData() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.clear_data_title)
            .setMessage(R.string.clear_data_message)
            .setPositiveButton(R.string.clear_data_confirm) { _, _ ->
                db.clearAllData()
                requireContext()
                    .getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)
                    .edit().clear().apply()
                Toast.makeText(requireContext(), R.string.data_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
