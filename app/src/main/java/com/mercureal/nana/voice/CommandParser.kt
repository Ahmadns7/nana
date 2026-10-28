package com.mercureal.nana.voice

/** Everything NaNa can do. Parsed on-device from recognized speech. */
sealed class Command {
    data object Back : Command()
    data object Home : Command()
    data object Recents : Command()
    data object Notifications : Command()
    data object ReadScreen : Command()
    data object ScrollUp : Command()
    data object ScrollDown : Command()
    data object StopListening : Command()
    data object Help : Command()
    data object Time : Command()
    data class OpenApp(val name: String) : Command()
    data class CallNumber(val number: String) : Command()
    data class CallContact(val name: String) : Command()
    data class TapText(val text: String) : Command()
    data class Unknown(val text: String) : Command()
}

/**
 * Offline intent parser. Pure Kotlin so it is unit-tested on the JVM.
 * Order matters: fixed phrases are checked BEFORE the regex rules, so
 * "press back" never falls into the "press <thing>" tap rule and
 * "open notifications" never falls into "open <app>".
 */
object CommandParser {

    /** Words that wake NaNa. Vosk sometimes hears "nana" as "nanna". */
    val wakeWords: Set<String> = setOf("nana", "nanna")

    private val fixed: Map<String, Command> = buildMap {
        for (k in listOf("press back", "go back", "back")) put(k, Command.Back)
        for (k in listOf("go home", "press home", "home")) put(k, Command.Home)
        for (k in listOf("recents", "recent apps", "show recents", "open recents")) put(k, Command.Recents)
        for (k in listOf(
            "open notifications", "show notifications", "pull down notifications",
            "pull down the notifications", "notifications"
        )) put(k, Command.Notifications)
        for (k in listOf("read screen", "read the screen", "read my screen", "whats on screen", "what is on screen")) {
            put(k, Command.ReadScreen)
        }
        put("scroll down", Command.ScrollDown)
        put("scroll up", Command.ScrollUp)
        for (k in listOf("stop listening", "go to sleep", "sleep", "goodbye")) put(k, Command.StopListening)
        for (k in listOf("help", "what can you do")) put(k, Command.Help)
        for (k in listOf("what time is it", "whats the time", "time")) put(k, Command.Time)
    }

    private val spokenDigits: Map<String, String> = mapOf(
        "zero" to "0", "oh" to "0", "o" to "0", "one" to "1", "two" to "2", "three" to "3",
        "four" to "4", "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",
        "plus" to "+"
    )

    private val callRule = Regex("^(call|dial|ring) (.+)$")
    private val openRule = Regex("^(open|launch|start|run) (.+)$")
    private val tapRule = Regex("^(tap|click|press|select) (.+)$")

    /** Lowercase, no apostrophes, only a-z 0-9 + and single spaces. */
    fun normalize(raw: String): String = raw.lowercase()
        .replace("'", "")
        .replace(Regex("[^a-z0-9+ ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * Returns the text after a wake word, "" if only the wake word was said,
     * or null if no wake word is present.
     */
    fun afterWakeWord(raw: String): String? {
        val tokens = normalize(raw).replace("na na", "nana").split(" ").filter { it.isNotEmpty() }
        val idx = tokens.indexOfFirst { it in wakeWords }
        if (idx < 0) return null
        return tokens.drop(idx + 1).joinToString(" ")
    }

    fun parse(raw: String): Command {
        var text = normalize(raw)
        text = text.removePrefix("please ").removeSuffix(" please").trim()
        if (text.isEmpty()) return Command.Unknown(raw)

        // 1. Fixed phrases first.
        fixed[text]?.let { return it }

        // 2. Regex rules.
        callRule.matchEntire(text)?.let { m ->
            val arg = m.groupValues[2]
            val number = spokenNumber(arg)
            return if (number != null) Command.CallNumber(number) else Command.CallContact(arg)
        }
        openRule.matchEntire(text)?.let { return Command.OpenApp(it.groupValues[2]) }
        tapRule.matchEntire(text)?.let { return Command.TapText(it.groupValues[2]) }

        return Command.Unknown(text)
    }

    /** "zero eight zero one two" -> "08012"; null if it is not a phone-like number. */
    fun spokenNumber(arg: String): String? {
        val out = StringBuilder()
        for (token in arg.split(" ").filter { it.isNotEmpty() }) {
            when {
                token.all { it.isDigit() } -> out.append(token)
                token == "+" -> out.append("+")
                spokenDigits[token] != null -> out.append(spokenDigits[token])
                else -> return null
            }
        }
        val digitCount = out.count { it.isDigit() }
        return if (digitCount >= 3) out.toString() else null
    }
}
