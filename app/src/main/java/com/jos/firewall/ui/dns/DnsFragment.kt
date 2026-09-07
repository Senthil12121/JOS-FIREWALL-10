package com.jos.firewall.ui.dns

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jos.firewall.JosFirewallApp
import com.jos.firewall.R
import com.jos.firewall.data.DnsRuleEntity
import com.jos.firewall.databinding.FragmentDnsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DnsFragment : Fragment() {

    private var _binding: FragmentDnsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: DnsRulesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDnsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as JosFirewallApp
        val dao = app.database.firewallDao()

        adapter = DnsRulesAdapter { updatedRule ->
            lifecycleScope.launch(Dispatchers.IO) {
                dao.updateDnsRule(updatedRule)
            }
        }

        binding.rvDnsRules.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDnsRules.adapter = adapter

        binding.btnAddDomain.setOnClickListener {
            showAddDomainDialog(dao)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            dao.getAllDnsRulesFlow().collectLatest { rules ->
                adapter.submitList(rules)
            }
        }
    }

    private fun showAddDomainDialog(dao: com.jos.firewall.data.FirewallDao) {
        val input = EditText(requireContext()).apply {
            hint = "e.g. tracker.example.com"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Domain to Blocklist")
            .setView(input)
            .setPositiveButton("Block Domain") { _, _ ->
                val domain = input.text.toString().trim().lowercase()
                if (domain.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        dao.insertDnsRules(listOf(DnsRuleEntity(domain, isBlocked = true, category = "CUSTOM")))
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
