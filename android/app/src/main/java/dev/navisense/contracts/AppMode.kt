package dev.navisense.contracts

/**
 * High-level operational modes for NaviSense AI mobile system adhering to PRD Section 13.2.
 */
enum class AppMode {
    /** Idle state. No sensor or vision pipeline actively running. */
    IDLE,

    /** Standalone indoor walking assistance using Mobility YOLO and USB ultrasonic sensor. */
    MOBILITY,

    /** Stationary close-range target object search using on-device Locate YOLO export. */
    FINAL_SEARCH,

    /** Active object memory lookup request to stationary laptop service. */
    FIND,

    /** Target object zone confirmed from memory; ready for "Guide me there". */
    TARGET_KNOWN,

    /** Target object confirmed detected by Locate YOLO during stationary search. */
    FOUND,

    /** Fatal error or background state. Processing paused; requires explicit restart. */
    PAUSED
}
