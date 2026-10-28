package com.mercureal.nana.voice

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.mercureal.nana.NanaAccessibilityService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Turns a parsed Command into a real phone action and returns what NaNa should say. */
class ActionExecutor(private val ctx: Context) {

    private val needAccessibility = "turn on nana in accessibility settings first"

    fun execute(cmd: Command): String = when (cmd) {
        Command.Back -> global(AccessibilityService.GLOBAL_ACTION_BACK, "going back")
        Command.Home -> global(AccessibilityService.GLOBAL_ACTION_HOME, "going home")
        Command.Recents -> global(AccessibilityService.GLOBAL_ACTION_RECENTS, "opening recents")
        Command.Notifications -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "opening notifications")
        Command.ReadScreen -> readScreen()
        Command.ScrollUp -> scroll(forward = false)
        Command.ScrollDown -> scroll(forward = true)
        Command.StopListening -> "okay, going quiet"
        Command.Help -> "you can say open an app, call a number or a contact, press back, go home, " +
            "open notifications, read screen, scroll up or down, or tap a button name"
        Command.Time -> "it's " + SimpleDateFormat("h:mm a", Locale.US).format(Date())
        is Command.OpenApp -> openApp(cmd.name)
        is Command.CallNumber -> call(cmd.number, cmd.number)
        is Command.CallContact -> callContact(cmd.name)
        is Command.TapText -> tap(cmd.text)
        is Command.Unknown -> "sorry, i didn't get that"
    }

    private fun global(action: Int, ok: String): String {
        val svc = NanaAccessibilityService.instance ?: return needAccessibility
        return if (svc.performGlobal(action)) ok else "that didn't work"
    }

    private fun readScreen(): String {
        val svc = NanaAccessibilityService.instance ?: return needAccessibility
        val lines = svc.readScreenText()
        if (lines.isEmpty()) return "i don't see any text on screen"
        return lines.joinToString(". ").take(400)
    }

    private fun scroll(forward: Boolean): String {
        val svc = NanaAccessibilityService.instance ?: return needAccessibility
        return if (svc.scroll(forward)) {
            if (forward) "scrolling down" else "scrolling up"
        } else {
            "nothing to scroll here"
        }
    }

    private fun tap(target: String): String {
        val svc = NanaAccessibilityService.instance ?: return needAccessibility
        return if (svc.tapText(target)) "tapped $target" else "i can't find $target on screen"
    }

    private fun openApp(query: String): String {
        val intent = findLaunchIntent(query) ?: return "i couldn't find an app called $query"
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(intent)
            "opening $query"
        } catch (e: Exception) {
            "couldn't open $query"
        }
    }

    private fun findLaunchIntent(query: String): Intent? {
        val pm = ctx.packageManager
        val q = query.lowercase().replace(" ", "")
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        val labeled = apps.map {
            it.loadLabel(pm).toString().lowercase().replace(" ", "") to it.activityInfo.packageName
        }
        val pkg = labeled.firstOrNull { it.first == q }?.second
            ?: labeled.firstOrNull { q.length >= 3 && it.first.contains(q) }?.second
            ?: labeled.firstOrNull { it.first.length >= 3 && q.contains(it.first) }?.second
            ?: return null
        return pm.getLaunchIntentForPackage(pkg)
    }

    private fun call(number: String, label: String): String {
        val canCall = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val action = if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(intent)
            if (canCall) "calling $label" else "dialing $label, tap call to confirm"
        } catch (e: Exception) {
            "couldn't place that call"
        }
    }

    private fun callContact(name: String): String {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return "i need contacts permission to call $name. allow it in nana's screen"
        val hit = lookupContact(name) ?: return "no contact named $name"
        return call(hit.second, hit.first)
    }

    private fun lookupContact(name: String): Pair<String, String>? {
        val q = name.lowercase()
        return try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val display = c.getString(0) ?: continue
                    val number = c.getString(1) ?: continue
                    if (display.lowercase().contains(q)) return display to number
                }
                null
            }
        } catch (e: SecurityException) {
            null
        }
    }
}
