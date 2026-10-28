package com.mercureal.nana

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Gives NaNa screen access: global back/home/recents/notifications, reading
 * visible text, tapping a control by its label, and scrolling.
 * Enabled by the user in Settings > Accessibility. Nothing leaves the device.
 */
class NanaAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: NanaAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun performGlobal(action: Int): Boolean = performGlobalAction(action)

    fun readScreenText(limit: Int = 40): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val out = LinkedHashSet<String>()
        collectText(root, out, limit)
        return out.toList()
    }

    private fun collectText(node: AccessibilityNodeInfo?, out: MutableSet<String>, limit: Int) {
        if (node == null || out.size >= limit) return
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        when {
            !text.isNullOrEmpty() -> out.add(text)
            !desc.isNullOrEmpty() -> out.add(desc)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            collectText(child, out, limit)
        }
    }

    /** Clicks the first clickable node (or its clickable ancestor) matching the label. */
    fun tapText(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        for (node in root.findAccessibilityNodeInfosByText(target)) {
            var current: AccessibilityNodeInfo? = node
            while (current != null) {
                if (current.isClickable) return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                current = current.parent
            }
        }
        return false
    }

    fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findScrollable(root) ?: return false
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return node.performAction(action)
    }

    private fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val found = findScrollable(node.getChild(i))
            if (found != null) return found
        }
        return null
    }
}
