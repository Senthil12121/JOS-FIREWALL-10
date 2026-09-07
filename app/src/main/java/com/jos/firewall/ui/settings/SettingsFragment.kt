package com.jos.firewall.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jos.firewall.JosFirewallApp
import com.jos.firewall.databinding.FragmentSettingsBinding
import com.jos.firewall.security.PasscodeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var passcodeManager: PasscodeManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        passcodeManager = PasscodeManager(requireContext())
        val prefs = requireContext().getSharedPreferences("jos_firewall_prefs", Context.MODE_PRIVATE)
        val app = requireActivity().application as JosFirewallApp
        val dao = app.database.firewallDao()

        // 1. Passcode & App Lock Setup
        updatePasscodeUI()

        binding.switchPasscode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!passcodeManager.isPasscodeEnabled()) {
                    showSetPasscodeDialog()
                }
            } else {
                if (passcodeManager.isPasscodeEnabled()) {
                    showDisablePasscodeDialog()
                }
            }
        }

        binding.btnChangePasscode.setOnClickListener {
            showChangePasscodeDialog()
        }

        // 2. All Apps Control & Discovery
        binding.switchBlockNewApps.isChecked = prefs.getBoolean("block_new_apps_by_default", false)
        binding.switchBlockNewApps.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("block_new_apps_by_default", isChecked).apply()
            Toast.makeText(
                requireContext(),
                if (isChecked) "New apps will be blocked upon install" else "New apps will be allowed by default",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.switchBlockUnidentified.isChecked = prefs.getBoolean("block_unidentified_traffic", false)
        binding.switchBlockUnidentified.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("block_unidentified_traffic", isChecked).apply()
            Toast.makeText(
                requireContext(),
                if (isChecked) "Strict Whitelist mode activated: Unidentified packets will be dropped" else "Default Allow mode: Unidentified packets will be inspected",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnRescanApps.setOnClickListener {
            Toast.makeText(requireContext(), "Scanning device for all applications...", Toast.LENGTH_SHORT).show()
            app.scanAllInstalledPackages()
            Toast.makeText(requireContext(), "Device applications synchronized", Toast.LENGTH_SHORT).show()
        }

        // 3. Automation & Network
        binding.switchBoot.isChecked = prefs.getBoolean("start_on_boot", true)
        binding.switchKillswitch.isChecked = prefs.getBoolean("kill_switch", false)

        binding.switchBoot.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("start_on_boot", isChecked).apply()
        }

        binding.switchKillswitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("kill_switch", isChecked).apply()
            Toast.makeText(requireContext(), "Kill-switch ${if (isChecked) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
        }

        binding.rowAlwaysOn.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Always-on VPN Setup")
                .setMessage("To prevent leaks during device boot or connection switches:\n\n1. Open Android Settings\n2. Search for 'VPN'\n3. Tap the Gear icon beside 'JOS Firewall'\n4. Enable 'Always-on VPN' and 'Block connections without VPN'")
                .setPositiveButton("Open Settings") { _, _ ->
                    try {
                        startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), "Open Android Settings -> VPN manually", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Close", null)
                .show()
        }

        binding.btnClearAllLogs.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear Logs")
                .setMessage("Are you sure you want to delete all stored connection audit logs?")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        dao.clearLogs()
                    }
                    Toast.makeText(requireContext(), "Logs cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnResetRules.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reset Rules")
                .setMessage("Reset all per-app and firewall rules to default settings?")
                .setPositiveButton("Reset") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        dao.deleteAllFirewallRules()
                    }
                    Toast.makeText(requireContext(), "Rules reset to default", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun updatePasscodeUI() {
        val isEnabled = passcodeManager.isPasscodeEnabled()
        binding.switchPasscode.isChecked = isEnabled
        binding.btnChangePasscode.visibility = if (isEnabled) View.VISIBLE else View.GONE
        binding.tvPasscodeStatus.text = if (isEnabled) {
            "Password protection is ACTIVE (App is locked on exit)"
        } else {
            "Protect firewall settings and app access with a passcode"
        }
    }

    private fun showSetPasscodeDialog() {
        val input = EditText(requireContext())
        input.hint = "Enter 4-6 digit PIN or Password"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        input.setPadding(48, 32, 48, 32)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Set JOS Firewall Password")
            .setMessage("Create a PIN or password required each time you open the application:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val code = input.text.toString().trim()
                if (code.length >= 4) {
                    passcodeManager.setPasscode(code, isPin = code.all { it.isDigit() })
                    Toast.makeText(requireContext(), "Passcode set successfully", Toast.LENGTH_SHORT).show()
                    updatePasscodeUI()
                } else {
                    Toast.makeText(requireContext(), "Passcode must be at least 4 digits", Toast.LENGTH_LONG).show()
                    updatePasscodeUI()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                updatePasscodeUI()
            }
            .setCancelable(false)
            .show()
    }

    private fun showChangePasscodeDialog() {
        val input = EditText(requireContext())
        input.hint = "Enter New PIN / Password"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        input.setPadding(48, 32, 48, 32)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Change Passcode")
            .setMessage("Enter your new security PIN or password (minimum 4 characters):")
            .setView(input)
            .setPositiveButton("Update") { _, _ ->
                val code = input.text.toString().trim()
                if (code.length >= 4) {
                    passcodeManager.setPasscode(code, isPin = code.all { it.isDigit() })
                    Toast.makeText(requireContext(), "Passcode updated successfully", Toast.LENGTH_SHORT).show()
                    updatePasscodeUI()
                } else {
                    Toast.makeText(requireContext(), "Passcode must be at least 4 digits", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDisablePasscodeDialog() {
        val input = EditText(requireContext())
        input.hint = "Enter Current PIN / Password"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        input.setPadding(48, 32, 48, 32)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Disable Passcode Protection")
            .setMessage("Confirm your current password to turn off app lock:")
            .setView(input)
            .setPositiveButton("Disable") { _, _ ->
                val code = input.text.toString().trim()
                if (passcodeManager.verifyPasscode(code)) {
                    passcodeManager.removePasscode()
                    Toast.makeText(requireContext(), "Passcode lock disabled", Toast.LENGTH_SHORT).show()
                    updatePasscodeUI()
                } else {
                    Toast.makeText(requireContext(), "Incorrect passcode", Toast.LENGTH_SHORT).show()
                    updatePasscodeUI()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                updatePasscodeUI()
            }
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
