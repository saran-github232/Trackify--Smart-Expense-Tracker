package com.saran.expensemanager

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.TranslateAnimation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.saran.expensemanager.databinding.ActivityPinLoginBinding

class PinLoginActivity : EdgeToEdgeActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_SETUP = "setup"
        const val MODE_VERIFY = "verify"
    }

    private lateinit var binding: ActivityPinLoginBinding
    private lateinit var pinPrefs: PinPrefs

    private val digits = mutableListOf<Int>()
    private val confirmDigits = mutableListOf<Int>()
    private var isConfirming = false
    private var mode = MODE_VERIFY
    private var failCount = 0

    private val dots get() = listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.llPinRoot.applyTopSystemBarInsetPadding()
        binding.llPinRoot.applyBottomSystemBarInsetPadding()
        supportActionBar?.hide()

        pinPrefs = PinPrefs(this)
        mode = intent.getStringExtra(EXTRA_MODE)
            ?: if (!pinPrefs.hasPin) MODE_SETUP else MODE_VERIFY

        setupUI()
        setupNumpad()
    }

    private fun setupUI() {
        when {
            mode == MODE_SETUP && !isConfirming -> {
                binding.tvPinTitle.text = getString(R.string.pin_create_title)
                binding.tvPinSubtitle.text = getString(R.string.pin_subtitle_create)
            }
            mode == MODE_SETUP && isConfirming -> {
                binding.tvPinTitle.text = getString(R.string.pin_confirm_title)
                binding.tvPinSubtitle.text = getString(R.string.pin_subtitle_confirm)
            }
            else -> {
                binding.tvPinTitle.text = getString(R.string.pin_enter_title)
                binding.tvPinSubtitle.text = getString(R.string.pin_subtitle_verify)
            }
        }
        binding.tvPinError.visibility = View.GONE
        binding.btnForgotPin.visibility = if (mode == MODE_VERIFY) View.VISIBLE else View.GONE
        updateDots(0)
    }

    private fun setupNumpad() {
        listOf(
            binding.btn0 to 0, binding.btn1 to 1, binding.btn2 to 2,
            binding.btn3 to 3, binding.btn4 to 4, binding.btn5 to 5,
            binding.btn6 to 6, binding.btn7 to 7, binding.btn8 to 8,
            binding.btn9 to 9
        ).forEach { (btn, digit) -> btn.setOnClickListener { onDigit(digit) } }

        binding.btnBackspace.setOnClickListener {
            if (digits.isNotEmpty()) {
                digits.removeAt(digits.lastIndex)
                updateDots(digits.size)
                binding.tvPinError.visibility = View.GONE
            }
        }

        binding.btnForgotPin.setOnClickListener { showForgotPinDialog() }
    }

    private fun onDigit(d: Int) {
        if (digits.size >= 4) return
        digits.add(d)
        updateDots(digits.size)
        if (digits.size == 4) binding.root.postDelayed({ processPin() }, 200)
    }

    private fun processPin() {
        if (mode == MODE_SETUP) {
            if (!isConfirming) {
                confirmDigits.clear()
                confirmDigits.addAll(digits)
                digits.clear()
                isConfirming = true
                binding.tvPinTitle.text = getString(R.string.pin_confirm_title)
                binding.tvPinSubtitle.text = getString(R.string.pin_subtitle_confirm)
                binding.tvPinError.visibility = View.GONE
                updateDots(0)
            } else {
                if (digits == confirmDigits) {
                    pinPrefs.setPin(digits.joinToString(""))
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    showError(getString(R.string.pin_mismatch))
                    digits.clear()
                    confirmDigits.clear()
                    isConfirming = false
                    binding.root.postDelayed({
                        binding.tvPinTitle.text = getString(R.string.pin_create_title)
                        binding.tvPinSubtitle.text = getString(R.string.pin_subtitle_create)
                        binding.tvPinError.visibility = View.GONE
                        updateDots(0)
                    }, 1200)
                }
            }
        } else {
            val entered = digits.joinToString("")
            if (pinPrefs.verifyPin(entered)) {
                startActivity(Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            } else {
                failCount++
                digits.clear()
                val remaining = (3 - failCount).coerceAtLeast(0)
                showError(getString(R.string.pin_wrong, remaining))
                updateDots(0)
            }
        }
    }

    private fun updateDots(filled: Int) {
        dots.forEachIndexed { i, dot ->
            dot.setBackgroundResource(
                if (i < filled) R.drawable.bg_pin_dot_filled else R.drawable.bg_pin_dot_empty
            )
        }
    }

    private fun showError(msg: String) {
        binding.tvPinError.text = msg
        binding.tvPinError.visibility = View.VISIBLE
        val shake = TranslateAnimation(-12f, 12f, 0f, 0f).apply {
            duration = 80; repeatCount = 4; repeatMode = TranslateAnimation.REVERSE
        }
        binding.llDots.startAnimation(shake)
    }

    private fun showForgotPinDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.forgot_pin_title)
            .setMessage(R.string.forgot_pin_message)
            .setPositiveButton(R.string.disable_app_lock) { _, _ ->
                pinPrefs.disablePin()
                startActivity(Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
