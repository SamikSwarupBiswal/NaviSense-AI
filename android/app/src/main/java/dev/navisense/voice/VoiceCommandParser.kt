package dev.navisense.voice

import java.util.Locale

/**
 * Natural language pattern matcher for NaviSense voice commands with support for
 * walking mode, object search, and turn-by-turn map directions around VIT Chennai.
 */
object VoiceCommandParser {

    private val WALLET_KEYWORDS = setOf(
        "wallet", "billfold", "purse"
    )

    private val KEYS_KEYWORDS = setOf(
        "keys", "key", "keychain", "car keys", "house keys"
    )

    private val STOP_KEYWORDS = setOf(
        "stop", "cancel", "halt", "freeze", "pause", "emergency stop", "quit", "stop walking", "stop navigation"
    )

    private val WALK_KEYWORDS = setOf(
        "start walking mode", "start walking", "start walk", "walk", "begin walk", "begin walking",
        "walking mode", "start walking assistance", "start navigation", "navigate", "let s walk", "lets walk"
    )

    private val MAP_MODE_KEYWORDS = setOf(
        "open map mode", "open map", "map mode", "start map mode", "show map", "map directions", "campus map", "directions"
    )

    private val SEARCH_NEARBY_KEYWORDS = setOf(
        "start search", "search nearby", "search", "scan nearby", "look nearby"
    )

    private val CONFIRM_KEYWORDS = setOf(
        "confirm arrival", "arrived", "i am here", "i m here", "im here", "reached"
    )

    private val HELP_KEYWORDS = setOf(
        "help", "what can i say", "commands", "options", "help me",
        "open the app", "open app", "launch app", "hello", "hi navisense"
    )

    private val STATUS_KEYWORDS = setOf(
        "status", "what is the status", "sensor status", "system status"
    )

    // Navigation trigger prefixes
    private val NAV_PREFIXES = listOf(
        "take me to", "directions to", "direction to", "navigate to",
        "go to", "walk to", "route to", "bring me to", "lead me to"
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
        val conversationalPrefixes = listOf(
            "hey navisense", "hey navi sense", "ok navisense", "ok navi sense",
            "navisense", "navi sense", "please", "can you", "could you"
        )
        for (prefix in conversationalPrefixes) {
            if (text.startsWith(prefix)) {
                text = text.removePrefix(prefix).trim()
            }
        }

        if (text.isEmpty()) {
            return VoiceCommand.Unknown(rawText)
        }

        // 3. Emergency Stop has highest priority
        for (stopKey in STOP_KEYWORDS) {
            if (text == stopKey || text.startsWith("$stopKey ") || text.endsWith(" $stopKey")) {
                return VoiceCommand.Stop
            }
        }

        // 4. Confirm arrival
        for (confirmKey in CONFIRM_KEYWORDS) {
            if (text == confirmKey || text.contains(confirmKey)) {
                return VoiceCommand.ConfirmArrival
            }
        }

        // 5. Help & Status
        for (helpKey in HELP_KEYWORDS) {
            if (text == helpKey || text.startsWith("$helpKey ") || text.contains(helpKey)) {
                return VoiceCommand.Help
            }
        }
        for (statusKey in STATUS_KEYWORDS) {
            if (text == statusKey || text.contains(statusKey)) {
                return VoiceCommand.AppStatus
            }
        }

        // 6. Direct VIT Chennai campus destination shortcuts
        if (text.contains("ambrosia") || text.contains("gazebo") || text.contains("canteen") || text.contains("food court")) {
            return VoiceCommand.NavigateTo("Food Court / Ambrosia Canteen")
        }
        if (text.contains("ab1") || text.contains("ab 1") || text.contains("academic block 1")) {
            return VoiceCommand.NavigateTo("Academic Block 1 (AB1)")
        }
        if (text.contains("ab2") || text.contains("ab 2") || text.contains("academic block 2")) {
            return VoiceCommand.NavigateTo("Academic Block 2 (AB2)")
        }
        if (text.contains("ab3") || text.contains("ab 3") || text.contains("academic block 3")) {
            return VoiceCommand.NavigateTo("Academic Block 3 (AB3)")
        }
        if (text.contains("library") || text.contains("central library")) {
            return VoiceCommand.NavigateTo("Central Library")
        }
        if (text.contains("main gate") || text.contains("entrance gate")) {
            return VoiceCommand.NavigateTo("Main Entrance Gate")
        }
        if (text.contains("admin block") || text.contains("administration")) {
            return VoiceCommand.NavigateTo("Admin Block")
        }
        if (text.contains("delta hostel") || text.contains("hostel delta")) {
            return VoiceCommand.NavigateTo("Delta Hostel Block")
        }
        if (text.contains("gamma hostel") || text.contains("hostel gamma")) {
            return VoiceCommand.NavigateTo("Gamma Hostel Block")
        }
        if (text.contains("sports ground") || text.contains("sports complex")) {
            return VoiceCommand.NavigateTo("Sports Complex & Ground")
        }

        // 7. Navigation with destination phrases: e.g. "take me to <location>", "directions to <location>"
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

        // 8. Start Walking Mode (must check before generic Map Mode)
        for (walkKey in WALK_KEYWORDS) {
            if (text == walkKey || text.startsWith("$walkKey ") || text.endsWith(" $walkKey")) {
                return VoiceCommand.StartWalking
            }
        }

        // 9. Open Map Mode
        for (mapKey in MAP_MODE_KEYWORDS) {
            if (text == mapKey || text.startsWith("$mapKey ") || text.contains(mapKey)) {
                return VoiceCommand.OpenMapMode
            }
        }

        // 10. Target Search for Keys or Wallet
        for (keyWord in KEYS_KEYWORDS) {
            if (text == keyWord || text.contains(keyWord)) {
                return VoiceCommand.FindTarget("keys")
            }
        }
        for (walletWord in WALLET_KEYWORDS) {
            if (text == walletWord || text.contains(walletWord)) {
                return VoiceCommand.FindTarget("wallet")
            }
        }

        // 11. Generic Start Search
        for (searchKey in SEARCH_NEARBY_KEYWORDS) {
            if (text == searchKey || text.startsWith("$searchKey ")) {
                return VoiceCommand.StartSearch
            }
        }

        return VoiceCommand.Unknown(rawText)
    }
}
