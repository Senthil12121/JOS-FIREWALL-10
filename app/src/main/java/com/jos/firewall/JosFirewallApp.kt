package com.jos.firewall

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import com.jos.firewall.data.AppDatabase
import com.jos.firewall.data.AppRuleEntity
import com.jos.firewall.firewall.RuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JosFirewallApp : Application() {

    val applicationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
    val ruleRepository by lazy { RuleRepository(database, applicationScope) }

    override fun onCreate() {
        super.onCreate()
        scanAllInstalledPackages()
    }

    /**
     * Scans every installed application on the mobile device (both user apps and system apps)
     * and synchronizes them into Room database so the firewall can inspect and control all traffic.
     */
    fun scanAllInstalledPackages() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                val pm = packageManager
                val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val currentRules = database.firewallDao().getAllAppRules()
                val ruleMap = currentRules.associateBy { it.packageName }

                val prefs = getSharedPreferences("jos_firewall_prefs", Context.MODE_PRIVATE)
                val blockNewApps = prefs.getBoolean("block_new_apps_by_default", false)

                val entities = installed.map { info ->
                    val pkg = info.packageName
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

                database.firewallDao().insertAppRules(entities)
            } catch (_: Exception) {}
        }
    }
}
