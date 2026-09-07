package com.jos.firewall.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.jos.firewall.JosFirewallApp
import com.jos.firewall.data.AppRuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Automatically captures app installation, update, and removal events to enforce
 * firewall rules on every newly installed application on the mobile device.
 */
class PackageChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return
        val app = context.applicationContext as? JosFirewallApp ?: return

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED -> {
                app.applicationScope.launch(Dispatchers.IO) {
                    try {
                        val pm = context.packageManager
                        val appInfo = pm.getApplicationInfo(packageName, 0)
                        val appName = pm.getApplicationLabel(appInfo).toString()
                        val uid = appInfo.uid

                        val prefs = context.getSharedPreferences("jos_firewall_prefs", Context.MODE_PRIVATE)
                        val blockNewApps = prefs.getBoolean("block_new_apps_by_default", false)

                        val existing = app.database.firewallDao().getAppRule(packageName)
                        val entity = existing?.copy(uid = uid, appName = appName) ?: AppRuleEntity(
                            packageName = packageName,
                            uid = uid,
                            appName = appName,
                            isWifiAllowed = !blockNewApps,
                            isMobileAllowed = !blockNewApps,
                            isBlocked = blockNewApps
                        )

                        app.database.firewallDao().insertOrUpdateAppRule(entity)
                    } catch (_: PackageManager.NameNotFoundException) {}
                }
            }

            Intent.ACTION_PACKAGE_REMOVED -> {
                // If not replacing (complete uninstall)
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (!isReplacing) {
                    app.applicationScope.launch(Dispatchers.IO) {
                        try {
                            app.database.firewallDao().deleteAppRule(packageName)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }
}
