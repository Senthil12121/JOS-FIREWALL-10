package com.jos.firewall.ui.applications

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jos.firewall.JosFirewallApp
import com.jos.firewall.R
import com.jos.firewall.data.AppRuleEntity
import com.jos.firewall.databinding.FragmentApplicationsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ApplicationsFragment : Fragment() {

    private var _binding: FragmentApplicationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AppListAdapter
    private var allApps: List<AppRuleEntity> = emptyList()
    private val systemPackageSet = mutableSetOf<String>()
    private var isAscending = true
    private var currentFilterMode = FilterMode.ALL

    enum class FilterMode {
        ALL, DOWNLOADED, SYSTEM
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApplicationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as JosFirewallApp
        val repo = app.ruleRepository

        adapter = AppListAdapter { updatedRule ->
            lifecycleScope.launch(Dispatchers.IO) {
                repo.updateAppRule(updatedRule)
            }
        }

        binding.rvApplications.layoutManager = LinearLayoutManager(requireContext())
        binding.rvApplications.adapter = adapter

        // Search text watcher
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterAndDisplayApps()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Sort button
        binding.btnSort.setOnClickListener {
            isAscending = !isAscending
            binding.btnSort.text = if (isAscending) "A-Z" else "Z-A"
            filterAndDisplayApps()
        }

        // Filter chips: All, Downloaded, System
        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilterMode = when {
                checkedIds.contains(R.id.chip_downloaded_apps) -> FilterMode.DOWNLOADED
                checkedIds.contains(R.id.chip_system_apps) -> FilterMode.SYSTEM
                else -> FilterMode.ALL
            }
            filterAndDisplayApps()
        }

        // Batch Action: Block All Filtered Apps
        binding.btnBatchBlockAll.setOnClickListener {
            batchUpdateRules(block = true)
        }

        // Batch Action: Allow All Filtered Apps
        binding.btnBatchAllowAll.setOnClickListener {
            batchUpdateRules(block = false)
        }

        loadAllInstalledApps(app)
        observeAppRules(repo)
    }

    private fun loadAllInstalledApps(app: JosFirewallApp) {
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = requireContext().packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val currentRules = app.database.firewallDao().getAllAppRules()
            val ruleMap = currentRules.associateBy { it.packageName }

            val prefs = requireContext().getSharedPreferences("jos_firewall_prefs", Context.MODE_PRIVATE)
            val blockNewApps = prefs.getBoolean("block_new_apps_by_default", false)

            systemPackageSet.clear()

            val appEntities = installed.map { info ->
                val pkg = info.packageName
                val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (isSystem) {
                    systemPackageSet.add(pkg)
                }

                val name = try {
                    pm.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                    pkg
                }

                ruleMap[pkg]?.copy(uid = info.uid, appName = name) ?: AppRuleEntity(
                    packageName = pkg,
                    uid = info.uid,
                    appName = name,
                    isWifiAllowed = !blockNewApps,
                    isMobileAllowed = !blockNewApps,
                    isBlocked = blockNewApps
                )
            }

            app.database.firewallDao().insertAppRules(appEntities)
        }
    }

    private fun observeAppRules(repo: com.jos.firewall.firewall.RuleRepository) {
        viewLifecycleOwner.lifecycleScope.launch {
            repo.getAllAppRulesFlow().collectLatest { rules ->
                withContext(Dispatchers.Main) {
                    allApps = rules
                    filterAndDisplayApps()
                }
            }
        }
    }

    private fun filterAndDisplayApps() {
        val query = binding.etSearch.text?.toString().orEmpty()

        var filtered = allApps.filter { rule ->
            val matchesSearch = query.isBlank() ||
                    rule.appName.contains(query, ignoreCase = true) ||
                    rule.packageName.contains(query, ignoreCase = true)

            val isSystem = systemPackageSet.contains(rule.packageName)
            val matchesCategory = when (currentFilterMode) {
                FilterMode.ALL -> true
                FilterMode.DOWNLOADED -> !isSystem
                FilterMode.SYSTEM -> isSystem
            }

            matchesSearch && matchesCategory
        }

        filtered = if (isAscending) {
            filtered.sortedBy { it.appName.lowercase() }
        } else {
            filtered.sortedByDescending { it.appName.lowercase() }
        }

        binding.tvAppCount.text = "Showing ${filtered.size} of ${allApps.size} apps"
        adapter.submitList(filtered)
    }

    private fun batchUpdateRules(block: Boolean) {
        val app = requireActivity().application as JosFirewallApp
        lifecycleScope.launch(Dispatchers.IO) {
            val query = withContext(Dispatchers.Main) { binding.etSearch.text?.toString().orEmpty() }
            val targets = allApps.filter { rule ->
                val matchesSearch = query.isBlank() ||
                        rule.appName.contains(query, ignoreCase = true) ||
                        rule.packageName.contains(query, ignoreCase = true)

                val isSystem = systemPackageSet.contains(rule.packageName)
                val matchesCategory = when (currentFilterMode) {
                    FilterMode.ALL -> true
                    FilterMode.DOWNLOADED -> !isSystem
                    FilterMode.SYSTEM -> isSystem
                }
                matchesSearch && matchesCategory
            }.map {
                it.copy(
                    isBlocked = block,
                    isWifiAllowed = !block,
                    isMobileAllowed = !block
                )
            }

            app.database.firewallDao().insertAppRules(targets)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    requireContext(),
                    "${if (block) "Blocked" else "Allowed"} ${targets.size} applications",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
