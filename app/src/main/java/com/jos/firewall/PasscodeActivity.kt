package com.jos.firewall

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jos.firewall.databinding.ActivityPasscodeBinding
import com.jos.firewall.security.PasscodeManager

class PasscodeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPasscodeBinding
    private lateinit var passcodeManager: PasscodeManager
    private val enteredPin = StringBuilder()
    private var isPinMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPasscodeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        passcodeManager = PasscodeManager(this)

        if (!passcodeManager.isPasscodeEnabled()) {
            PasscodeManager.setSessionUnlocked(true)
            setResult(Activity.RESULT_OK)
            finish()
            return
        }

        isPinMode = passcodeManager.getPasscodeType() == "pin"
        setupViews()
        setupKeypad()
    }

    private fun setupViews() {
        if (isPinMode) {
            binding.layoutPinDots.visibility = View.VISIBLE
            binding.gridKeypad.visibility = View.VISIBLE
            binding.layoutPasswordInput.visibility = View.GONE
            binding.btnUnlockPassword.visibility = View.GONE
            binding.tvInstruction.text = "Enter 4-Digit Security PIN"
        } else {
            binding.layoutPinDots.visibility = View.GONE
            binding.gridKeypad.visibility = View.GONE
            binding.layoutPasswordInput.visibility = View.VISIBLE
            binding.btnUnlockPassword.visibility = View.VISIBLE
            binding.tvInstruction.text = "Enter Application Password"

            binding.btnUnlockPassword.setOnClickListener {
                val pwd = binding.etPassword.text?.toString() ?: ""
                checkCredentials(pwd)
            }
        }

        binding.tvForgotPasscode.setOnClickListener {
            showForgotPasscodeDialog()
        }
    }

    private fun setupKeypad() {
        val numberButtons = listOf(
            binding.btnKey0 to "0",
            binding.btnKey1 to "1",
            binding.btnKey2 to "2",
            binding.btnKey3 to "3",
            binding.btnKey4 to "4",
            binding.btnKey5 to "5",
            binding.btnKey6 to "6",
            binding.btnKey7 to "7",
            binding.btnKey8 to "8",
            binding.btnKey9 to "9"
        )

        for ((btn, digit) in numberButtons) {
            btn.setOnClickListener {
                if (enteredPin.length < 4) {
                    enteredPin.append(digit)
                    updatePinDots()
                    if (enteredPin.length == 4) {
                        checkCredentials(enteredPin.toString())
                    }
                }
            }
        }

        binding.btnKeyBackspace.setOnClickListener {
            if (enteredPin.isNotEmpty()) {
                enteredPin.deleteCharAt(enteredPin.length - 1)
                updatePinDots()
                binding.tvError.visibility = View.INVISIBLE
            }
        }

        binding.btnKeyBiometric.setOnClickListener {
            Toast.makeText(this, "Use PIN keypad or enter password", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePinDots() {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4)
        val activeColor = ContextCompat.getColor(this, R.color.primary)
        val inactiveColor = ContextCompat.getColor(this, R.color.surface_stroke)

        for (i in dots.indices) {
            if (i < enteredPin.length) {
                dots[i].backgroundTintList = android.content.res.ColorStateList.valueOf(activeColor)
            } else {
                dots[i].backgroundTintList = android.content.res.ColorStateList.valueOf(inactiveColor)
            }
        }
    }

    private fun checkCredentials(code: String) {
        val isValid = passcodeManager.verifyPasscode(code)
        if (isValid) {
            binding.tvError.visibility = View.INVISIBLE
            setResult(Activity.RESULT_OK)
            finish()
        } else {
            binding.tvError.visibility = View.VISIBLE
            binding.tvError.text = "Incorrect passcode. Please try again."
            enteredPin.clear()
            updatePinDots()

            // Shake animation
            binding.layoutPinDots.animate()
                .translationXBy(20f)
                .setDuration(50)
                .withEndAction {
                    binding.layoutPinDots.animate()
                        .translationXBy(-40f)
                        .setDuration(100)
                        .withEndAction {
                            binding.layoutPinDots.animate()
                                .translationX(0f)
                                .setDuration(50)
                                .start()
                        }.start()
                }.start()
        }
    }

    private fun showForgotPasscodeDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset Security Passcode")
            .setMessage("If you forgot your password or PIN, resetting it will remove the passcode lock so you can access JOS Firewall and set a new password in Settings.")
            .setPositiveButton("Reset Passcode") { _, _ ->
                passcodeManager.removePasscode()
                Toast.makeText(this, "Passcode reset. You can set a new one in Settings.", Toast.LENGTH_LONG).show()
                setResult(Activity.RESULT_OK)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Prevent back press from bypassing lock screen
        moveTaskToBack(true)
    }
}
