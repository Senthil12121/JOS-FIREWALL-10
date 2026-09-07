package com.jos.firewall.firewall

import com.jos.firewall.data.RuleAction
import java.net.InetAddress

data class FirewallDecision(
    val isAllowed: Boolean,
    val reason: String,
    val matchedRuleName: String? = null
)

/**
 * Core decision-making engine for network packet filtering.
 */
class FirewallEngine(
    private val ruleRepository: RuleRepository
) {
    var isFirewallActive: Boolean = true
    var isKillSwitchActive: Boolean = false
    var isBlockUnidentifiedTraffic: Boolean = false

    /**
     * Determines whether to ALLOW or BLOCK a given network packet or flow.
     */
    fun evaluate(
        srcIp: InetAddress,
        srcPort: Int,
        dstIp: InetAddress,
        dstPort: Int,
        protocol: String,
        uid: Int,
        packageName: String?,
        isWifi: Boolean
    ): FirewallDecision {
        if (!isFirewallActive) {
            return FirewallDecision(true, "Firewall Disabled")
        }

        // 1. Check Application-Level Rules (Per-App Firewall)
        // Check both UID and Package Name for absolute coverage of all installed apps
        val appRule = (if (uid > 0) ruleRepository.getAppRuleByUid(uid) else null)
            ?: (if (packageName != null) ruleRepository.getAppRuleByPackage(packageName) else null)

        if (appRule != null) {
            if (appRule.isBlocked) {
                return FirewallDecision(
                    isAllowed = false,
                    reason = "App '${appRule.appName}' blocked by master rule"
                )
            }
            if (isWifi && !appRule.isWifiAllowed) {
                return FirewallDecision(
                    isAllowed = false,
                    reason = "Wi-Fi traffic blocked for '${appRule.appName}'"
                )
            }
            if (!isWifi && !appRule.isMobileAllowed) {
                return FirewallDecision(
                    isAllowed = false,
                    reason = "Mobile data blocked for '${appRule.appName}'"
                )
            }
        } else if (isBlockUnidentifiedTraffic && uid <= 0 && packageName == null) {
            return FirewallDecision(
                isAllowed = false,
                reason = "Unidentified app traffic dropped by strict security policy"
            )
        }

        // 2. Check Custom IP, Port, and Protocol Rules
        val activeRules = ruleRepository.getActiveRules()
        for (rule in activeRules) {
            if (rule.matches(dstIp, dstPort, protocol, isWifi, packageName)) {
                val allowed = rule.action == RuleAction.ALLOW
                return FirewallDecision(
                    isAllowed = allowed,
                    reason = "Matched rule: ${rule.name} [${rule.action}]",
                    matchedRuleName = rule.name
                )
            }
        }

        // 3. Default Policy: ALLOW
        return FirewallDecision(
            isAllowed = true,
            reason = "Default Policy (Allow)"
        )
    }
}
