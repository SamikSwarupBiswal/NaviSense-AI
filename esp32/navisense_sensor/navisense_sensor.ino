/**
 * NaviSense AI — ESP32-S3 HC-SR04 Sensor Firmware
 *
 * Owner: Rohan (Hardware & Firmware)
 * Authority: PRD v3.2 Section 14.1 (Wire Format & Acquisition Loop)
 *
 * Contract:
 * - Emits newline-delimited ASCII records at 10 Hz (every 100 ms)
 * - Serial baud: 115200, 8N1
 * - Wire format: V=1,SEQ=<seq>,UP_MS=<uptime_ms>,DIST_CM=<dist>,VALID=<valid>\n
 * - SEQ: uint32_t sequence counter, increments every acquisition cycle (including invalid/unsent)
 * - UP_MS: uint32_t device uptime in ms at measurement completion
 * - DIST_CM: 2..400 integer cm when valid; -1 when invalid/no echo/timeout
 * - VALID: 1 when 2 <= DIST_CM <= 400; 0 otherwise
 * - Zero backlog policy: measurements are never buffered; only fresh data is emitted
 */

#include <Arduino.h>

// Pin Configuration (as documented in docs/rohan/hardware-spec.md)
#ifndef PIN_TRIG
#define PIN_TRIG 4
#endif

#ifndef PIN_ECHO
#define PIN_ECHO 5
#endif

// Acquisition Timing Configuration
static const uint32_t ACQUISITION_INTERVAL_MS = 100; // 10 Hz rate
static const uint32_t ECHO_TIMEOUT_US = 25000;       // 25 ms timeout (~430 cm maximum travel)

// Range Constraints (PRD Section 14.1)
static const int32_t MIN_VALID_DIST_CM = 2;
static const int32_t MAX_VALID_DIST_CM = 400;

// State Tracking
static uint32_t g_sequence = 0;
static uint32_t g_next_acquisition_ms = 0;

void setup() {
    // Configure GPIOs
    pinMode(PIN_TRIG, OUTPUT);
    digitalWrite(PIN_TRIG, LOW);

    pinMode(PIN_ECHO, INPUT);

    // Initialize USB Serial (115200 baud, 8N1)
    // On ESP32-S3 with native USB CDC, Serial maps to the USB CDC device.
    Serial.begin(115200);

    // Wait up to 1 second for serial port to open (allows host connection)
    uint32_t start_wait = millis();
    while (!Serial && (millis() - start_wait < 1000)) {
        delay(10);
    }

    g_next_acquisition_ms = millis();
}

void loop() {
    uint32_t now = millis();

    // Enforce strict 10 Hz non-blocking acquisition cycle
    if ((int32_t)(now - g_next_acquisition_ms) < 0) {
        // Not yet time for next cycle; yield to system
        delay(1);
        return;
    }

    // Schedule next acquisition (maintain 100 ms spacing without accumulating drift)
    g_next_acquisition_ms += ACQUISITION_INTERVAL_MS;
    if ((int32_t)(now - g_next_acquisition_ms) > (int32_t)ACQUISITION_INTERVAL_MS) {
        // If execution fell behind by more than a full cycle, re-anchor without backlog
        g_next_acquisition_ms = now + ACQUISITION_INTERVAL_MS;
    }

    // SEQ increments on EVERY acquisition cycle, including failed/invalid attempts
    uint32_t current_seq = g_sequence++;

    // Step 1: Ensure TRIG is LOW before pulse
    digitalWrite(PIN_TRIG, LOW);
    delayMicroseconds(4);

    // Step 2: Emit 10 µs HIGH pulse on TRIG
    digitalWrite(PIN_TRIG, HIGH);
    delayMicroseconds(10);
    digitalWrite(PIN_TRIG, LOW);

    // Step 3: Measure pulse duration on ECHO with bounded timeout (25 ms)
    unsigned long duration_us = pulseIn(PIN_ECHO, HIGH, ECHO_TIMEOUT_US);



    // Step 4: Record measurement completion uptime in ms
    uint32_t completion_uptime_ms = millis();

    // Step 5: Calculate distance and validity
    int32_t dist_cm = -1;
    int valid = 0;

    if (duration_us > 0) {
        // Speed of sound = 343 m/s = 0.0343 cm/µs
        // Round trip distance: dist = (duration_us * 0.0343) / 2
        // Integer arithmetic with rounding: (duration_us * 343 + 10000) / 20000
        int32_t computed_cm = (int32_t)((duration_us * 343UL + 10000UL) / 20000UL);

        if (computed_cm >= MIN_VALID_DIST_CM && computed_cm <= MAX_VALID_DIST_CM) {
            dist_cm = computed_cm;
            valid = 1;
        } else {
            // Out of physical/calibrated bounds
            dist_cm = -1;
            valid = 0;
        }
    } else {
        // Timeout or no echo received
        dist_cm = -1;
        valid = 0;
    }

    // Step 6: Emit PRD v1 wire record:
    // Format: V=1,SEQ=<seq>,UP_MS=<uptime_ms>,DIST_CM=<cm>,VALID=<0|1>\n
    // Buffer capped well under the 128-byte limit
    char line_buffer[96];
    int len = snprintf(line_buffer, sizeof(line_buffer),
                       "V=1,SEQ=%lu,UP_MS=%lu,DIST_CM=%ld,VALID=%d\n",
                       (unsigned long)current_seq,
                       (unsigned long)completion_uptime_ms,
                       (long)dist_cm,
                       valid);

    if (len > 0 && (size_t)len < sizeof(line_buffer)) {
        // Non-blocking / unbuffered immediate transmission
        Serial.write((const uint8_t*)line_buffer, (size_t)len);
    }
}
