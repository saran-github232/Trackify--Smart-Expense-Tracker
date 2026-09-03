package com.saran.expensemanager

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.saran.expensemanager.databinding.ActivityMainBinding

class MainActivity : EdgeToEdgeActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.bottomNav.applyBottomSystemBarInsetPadding()
        if (savedInstanceState == null) {
            loadFragment(DashboardFragment())
        }

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
        SheetSyncManager.triggerSync(this)
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
