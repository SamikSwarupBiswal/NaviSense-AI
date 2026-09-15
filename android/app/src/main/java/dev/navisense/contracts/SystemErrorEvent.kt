package dev.navisense.contracts

/**
 * Subsystem component originating an error.
 */
enum class SubsystemComponent {
    CAMERA,
    MODEL_RUNTIME,
    USB_SENSOR,
    NETWORK_MEMORY,
    TTS_AUDIO,
    SYSTEM_COORDINATOR
}

/**
 * Structured system error report.
 */
data class SystemErrorEvent(
    val component: SubsystemComponent,
    val errorCode: String,
    val message: String,
    val isFatal: Boolean,
    val timestampMonotonicMs: Long
)
