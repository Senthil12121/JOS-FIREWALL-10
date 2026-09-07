package com.jos.firewall.ui.logs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jos.firewall.JosFirewallApp
import com.jos.firewall.R
import com.jos.firewall.data.ConnectionLogEntity
import com.jos.firewall.databinding.FragmentLogsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogsFragment : Fragment() {

    private var _binding: FragmentLogsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: LogsAdapter
    private var allLogs: List<ConnectionLogEntity> = emptyList()
    private var selectedFilter = "ALL"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as JosFirewallApp
        val dao = app.database.firewallDao()

        adapter = LogsAdapter()
        binding.rvLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLogs.adapter = adapter

        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedFilter = when {
                checkedIds.contains(R.id.chip_allowed) -> "ALLOWED"
                checkedIds.contains(R.id.chip_blocked) -> "BLOCKED"
                else -> "ALL"
            }
            applyFilter()
        }

        binding.btnClearLogs.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                dao.clearLogs()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            dao.getRecentLogsFlow().collectLatest { logs ->
                withContext(Dispatchers.Main) {
                    allLogs = logs
                    applyFilter()
                }
            }
        }
    }

    private fun applyFilter() {
        val filtered = when (selectedFilter) {
            "ALLOWED" -> allLogs.filter { it.status == "ALLOWED" }
            "BLOCKED" -> allLogs.filter { it.status == "BLOCKED" }
            else -> allLogs
        }
        adapter.submitList(filtered)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
