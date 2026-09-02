package com.saran.expensemanager

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import com.saran.expensemanager.databinding.ActivityOnboardingBinding

class OnboardingActivity : EdgeToEdgeActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.llOnboardingRoot.applyTopSystemBarInsetPadding()
        binding.llOnboardingRoot.applyBottomSystemBarInsetPadding()
        supportActionBar?.hide()

        binding.etName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { proceed(); true } else false
        }

        binding.btnGetStarted.setOnClickListener { proceed() }
    }

    private fun proceed() {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            binding.tilName.error = getString(R.string.error_name_required)
            return
        }
        binding.tilName.error = null

        val userPrefs = UserPrefs(this)
        userPrefs.userName = name
        userPrefs.completeOnboarding()

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
