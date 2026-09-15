package dev.navisense.voice

import java.util.Locale

/**
 * Pure Kotlin natural language pattern matcher for NaviSense voice commands.
 * Runs 100% offline with zero external dependencies.
 */
object VoiceCommandParser {

    private val WALLET_KEYWORDS = setOf(
        "wallet", "billfold", "purse"
    )

    private val KEYS_KEYWORDS = setOf(
        "keys", "key", "keychain", "car keys", "house keys"
    )

    private val STOP_KEYWORDS = setOf(
        "stop", "cancel", "halt", "freeze", "pause", "emergency stop", "quit"
    )

    private val WALK_KEYWORDS = setOf(
        "start walking", "start walk", "walk", "begin walk", "start navigation",
        "navigate", "let s walk", "lets walk", "walking mode", "start walking mode"
    )

    private val CONFIRM_KEYWORDS = setOf(
        "confirm arrival", "arrived", "i am here", "i m here", "im here", "reached"
    )

    private val HELP_KEYWORDS = setOf(
        "help", "what can i say", "commands", "options", "help me"
    )

    private val STATUS_KEYWORDS = setOf(
        "status", "what is the status", "sensor status", "system status"
    )

    /**
     * Parses raw transcribed speech text into a structured [VoiceCommand].
     */
    fun parse(rawText: String?): VoiceCommand {
        if (rawText.isNullOrBlank()) {
            return VoiceCommand.Unknown("")
        }

        // 1. Normalize: lowercase, replace apostrophe with space, clean punctuation, collapse whitespace
        var text = rawText.lowercase(Locale.ROOT)
            .replace("'", " ")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // 2. Strip conversational prefixes ("please", "can you", "navi sense", "navisense", etc.)
        val prefixes = listOf(
            "hey navisense", "hey navi sense", "ok navisense", "ok navi sense",
            "navisense", "navi sense", "please", "can you", "could you"
        )
        for (prefix in prefixes) {
            if (text.startsWith(prefix)) {
                text = text.removePrefix(prefix).trim()
            }
        }

        if (text.isEmpty()) {
            return VoiceCommand.Unknown(rawText)
        }

        // 3. Exact or prefix command matching

        // Emergency Stop has highest priority
        for (stopKey in STOP_KEYWORDS) {
            if (text == stopKey || text.startsWith("$stopKey ") || text.endsWith(" $stopKey")) {
                return VoiceCommand.Stop
            }
        }

        // Confirm arrival
        for (confirmKey in CONFIRM_KEYWORDS) {
            if (text == confirmKey || text.contains(confirmKey)) {
                return VoiceCommand.ConfirmArrival
            }
        }

        // Help
        for (helpKey in HELP_KEYWORDS) {
            if (text == helpKey || text.startsWith("$helpKey ") || text.contains(helpKey)) {
                return VoiceCommand.Help
            }
        }

        // Status
        for (statusKey in STATUS_KEYWORDS) {
            if (text == statusKey || text.contains(statusKey)) {
                return VoiceCommand.AppStatus
            }
        }

        // Pedestrian Navigation: "take me to <dest>", "navigate to <dest>", "directions to <dest>", "walk to <dest>"
        val navPrefixes = listOf(
            "take me to", "i want to go to", "navigate to", "directions to", "find route to", "route to", "walk to", "go to"
        )
        for (navPrefix in navPrefixes) {
            if (text.startsWith("$navPrefix ")) {
                val destRaw = text.removePrefix("$navPrefix ").trim()
                if (destRaw.isNotEmpty() && !KEYS_KEYWORDS.contains(destRaw) && !WALLET_KEYWORDS.contains(destRaw)) {
                    val formattedDest = destRaw.split(" ").joinToString(" ") { word ->
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    }
                    return VoiceCommand.NavigateTo(formattedDest)
                }
            }
        }

        // Start Walking
        for (walkKey in WALK_KEYWORDS) {
            if (text == walkKey || text.startsWith("$walkKey ") || text.endsWith(" $walkKey")) {
                return VoiceCommand.StartWalking
            }
        }

        // Target Search: "find <target>", "search <target>", "where is my <target>"
        // Check for Keys
        for (keyWord in KEYS_KEYWORDS) {
            if (text == keyWord || text.contains(keyWord)) {
                return VoiceCommand.FindTarget("keys")
            }
        }

        // Check for Wallet
        for (walletWord in WALLET_KEYWORDS) {
            if (text == walletWord || text.contains(walletWord)) {
                return VoiceCommand.FindTarget("wallet")
            }
        }

        return VoiceCommand.Unknown(rawText)
    }
}
