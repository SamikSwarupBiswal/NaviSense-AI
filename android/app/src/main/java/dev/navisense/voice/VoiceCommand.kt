package dev.navisense.voice

/**
 * High-level voice command abstractions for hands-free and accessible operation of NaviSense.
 */
sealed class VoiceCommand {
    /**
     * Trigger Search Nearby for a specific target object class (e.g. "wallet", "keys").
     */
    data class FindTarget(val target: String) : VoiceCommand()

    /**
     * Start standalone Mobility walking mode.
     */
    object StartWalking : VoiceCommand()

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
