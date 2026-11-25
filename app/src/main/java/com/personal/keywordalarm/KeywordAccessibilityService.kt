package com.personal.keywordalarm

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads visible text from the watched browser (Brave by default) and triggers the
 * alarm when a configured keyword appears on screen.
 *
 * Android does not let an app read another app's screen directly; an
 * AccessibilityService is the supported way to observe on-screen text. The user
 * must enable it manually under Settings > Accessibility.
 */
class KeywordAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "KeywordA11y"
        // Avoid hammering: don't rescan the same window more than this often.
        private const val MIN_SCAN_INTERVAL_MS = 400L
    }

    @Volatile
    private var isAlarmActive = false
    private var lastTriggerAt = 0L
    private var lastScanAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!Prefs.isEnabled(this)) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg !in Prefs.getWatchedPackages(this)) return

        // Only react to content/window changes, and rate-limit scans.
        val now = System.currentTimeMillis()
        if (now - lastScanAt < MIN_SCAN_INTERVAL_MS) return
        lastScanAt = now

        val keywords = Prefs.getKeywords(this)
        if (keywords.isEmpty()) return

        val root = rootInActiveWindow ?: return
        val caseSensitive = Prefs.isCaseSensitive(this)

        val matched = findKeyword(root, keywords, caseSensitive)
        root.recycle()

        if (matched != null) {
            val cooldownMs = Prefs.getCooldownSeconds(this) * 1000L
            if (now - lastTriggerAt >= cooldownMs) {
                lastTriggerAt = now
                Log.i(TAG, "Keyword detected: $matched")
                triggerAlarm(matched)
            }
        }
    }

    /**
     * Walks the accessibility node tree collecting text + content descriptions,
     * and returns the first keyword found (or null).
     */
    private fun findKeyword(
        root: AccessibilityNodeInfo,
        keywords: List<String>,
        caseSensitive: Boolean
    ): String? {
        val sb = StringBuilder()
        collectText(root, sb)
        val haystack = if (caseSensitive) sb.toString() else sb.toString().lowercase()

        for (kw in keywords) {
            val needle = if (caseSensitive) kw else kw.lowercase()
            if (needle.isNotEmpty() && haystack.contains(needle)) return kw
        }
        return null
    }

    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        node.text?.let { if (it.isNotEmpty()) sb.append(it).append('\n') }
        node.contentDescription?.let { if (it.isNotEmpty()) sb.append(it).append('\n') }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            collectText(child, sb)
            child?.recycle()
        }
    }

    private fun triggerAlarm(keyword: String) {
        isAlarmActive = true
        val intent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START
            putExtra(AlarmService.EXTRA_KEYWORD, keyword)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "Accessibility service interrupted")
    }
}
