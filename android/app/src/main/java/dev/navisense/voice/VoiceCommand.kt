package dev.navisense.voice

/**
 * High-level voice command abstractions for hands-free and accessible operation of NaviSense.
 */
sealed class VoiceCommand {
    /**
     * Start standalone Mobility walking mode (with Gemini 4s obstacle narration).
     */
    object StartWalking : VoiceCommand()

    /**
     * Trigger generic Search Nearby.
     */
    object StartSearch : VoiceCommand()

    /**
     * Trigger Search Nearby for a specific target object class (e.g. "wallet", "keys").
     */
    data class FindTarget(val target: String) : VoiceCommand()

    /**
     * Open campus map directions mode.
     */
    object OpenMapMode : VoiceCommand()

    /**
     * Request walking directions to a specific campus location or POI (e.g. "ab1", "ambrosia", "library").
     */
    data class NavigateToDestination(val destinationQuery: String) : VoiceCommand()

    /**
     * Start pedestrian walking navigation to a spoken destination via Google Routes.
     */
    data class NavigateTo(val destination: String) : VoiceCommand()

    /**
     * Immediately halt navigation or search and return to IDLE.
     */
    object Stop : VoiceCommand()

    /**
     * Confirm arrival at target zone in memory-assisted workflow.
     */
    object ConfirmArrival : VoiceCommand()

    /**
     * Speak available voice commands.
     */
    object Help : VoiceCommand()

    /**
     * Speak current application/sensor status.
     */
    object AppStatus : VoiceCommand()

    /**
     * Unrecognized voice utterance.
     */
    data class Unknown(val rawText: String) : VoiceCommand()
}
