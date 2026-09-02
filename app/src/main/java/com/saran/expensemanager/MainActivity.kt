package com.saran.expensemanager

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.fragment.app.Fragment
import com.saran.expensemanager.databinding.ActivityMainBinding

class MainActivity : EdgeToEdgeActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var shakePrefs: ShakePrefs
    private lateinit var shakeDetector: ShakeDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.bottomNav.applyBottomSystemBarInsetPadding()
        if (savedInstanceState == null) {
            loadFragment(DashboardFragment())
        }

        shakePrefs = ShakePrefs(this)
        shakeDetector = ShakeDetector(this) { onShakeDetected() }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { loadFragment(DashboardFragment()); true }
                R.id.nav_expenses -> { loadFragment(ExpenseListFragment()); true }
                R.id.nav_add -> {
                    if (supportFragmentManager.findFragmentByTag("add_chooser") == null) {
                        AddChooserBottomSheet().show(supportFragmentManager, "add_chooser")
                    }
                    false
                }
                R.id.nav_income -> { loadFragment(IncomeFragment()); true }
                R.id.nav_analytics -> { loadFragment(AnalyticsFragment()); true }
                else -> false
            }
        }

        // App shortcut deep link (res/xml/shortcuts.xml) — "Transactions" opens straight to the list.
        if (intent.getStringExtra("open_tab") == "expenses") {
            binding.bottomNav.selectedItemId = R.id.nav_expenses
        }
    }

    override fun onResume() {
        super.onResume()
        // Shake detection only runs while this Activity is on top, so it's automatically
        // suspended during AddExpenseActivity/AddIncomeActivity — including while their
        // post-save interstitial ad is showing — and resumes cleanly once back here.
        if (shakePrefs.enabled && shakeDetector.isAvailable) {
            shakeDetector.thresholdG = shakePrefs.thresholdG
            shakeDetector.start()
        }
        SheetSyncManager.triggerSync(this)
    }

    override fun onPause() {
        super.onPause()
        shakeDetector.stop()
    }

    private fun onShakeDetected() {
        if (shakePrefs.vibrate) vibrate()
        startActivity(Intent(this, AddExpenseActivity::class.java))
    }

    private fun vibrate() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(80)
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    fun navigateToTab(tabId: Int) {
        binding.bottomNav.selectedItemId = tabId
    }

    fun showFullScreenFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
