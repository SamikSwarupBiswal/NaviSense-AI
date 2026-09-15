# Rohan — Phase 0 Handoffs & Inter-Subsystem Requests

**Owner:** Rohan (Hardware, Firmware, Mount, USB & Android Sensor Adapter)  
**Subsystem Ownership:** HC-SR04 ultrasonic sensor + ESP32-S3 assembly, phone mount alignment, electrical/power verification, 10 Hz acquisition loop firmware, and Android USB serial parser/adapter (`dev.navisense.usb`).  
**Repository:** [NaviSense-AI](https://github.com/SamikSwarupBiswal/NaviSense-AI)  
**Branch:** `main` & `communication`  
**Authority:** [PRD v3.2](../README.md) Sections 7, 13–19, 25–30, and 38; [Rohan Guidance](guidance.md); [AGENTS.md](../AGENTS.md)  

---

## 1. Executive Summary & Phase 0 Deliverables (Handoff S03 / H3)

Rohan has completed all Phase 0 hardware specifications, electrical verification, ESP32-S3 acquisition firmware, host test tooling, replay test suite, and the Android USB serial adapter module (`dev.navisense.usb`).

### Key Deliverables Completed:
1. **Hardware & Electrical Specification** ([docs/rohan/hardware-spec.md](hardware-spec.md)):
   * Microcontroller: ESP32-S3 DevKit (`ESP32-S3-WROOM-1` / `DevKitC-1`).
   * Ultrasonic Sensor: HC-SR04 4-pin transducer (VCC 5V, GND, TRIG GPIO 4, ECHO GPIO 5).
   * Voltage Protection: Passive 1.0 kΩ ($R_1$) / 2.0 kΩ ($R_2$) ±1% resistor voltage divider stepping down 5.0 V ECHO pulse to 3.33 V input for ESP32-S3 GPIO 5 (within 3.3 V CMOS bounds; 1.67 mA draw). Direct 3.3 V trigger pulses emitted from GPIO 4 (10 µs pulse).
   * Power Budget & USB OTG: Android phone USB-C OTG supplies standard 5.0 V @ 500 mA (up to 1.5 A). ESP32-S3 with Wi-Fi/BT disabled draws ~60 mA; HC-SR04 draws ~15 mA active. Total assembly current < 80 mA (well below 500 mA OTG limit, zero brownout).
   * Mechanical Alignment: Chest / lanyard rig, 100–120 cm height from ground, transducer plane facing directly forward perpendicular to walking vector, sharing aligned forward axis with phone camera (±5° pitch/yaw deviation).
2. **ESP32-S3 10 Hz Acquisition Firmware** ([esp32/navisense_sensor/navisense_sensor.ino](../../esp32/navisense_sensor/navisense_sensor.ino)):
   * PRD §14.1 Wire Format: `V=1,SEQ=<seq>,UP_MS=<uptime_ms>,DIST_CM=<dist>,VALID=<valid>\n` at 10 Hz over native USB CDC serial at 115200 baud, 8N1.
   * Non-blocking acquisition loop with 100 ms spacing without accumulated drift.
   * Zero-backlog policy: measurements are never buffered; only fresh unbuffered records emitted.
   * Integer rounding formula: `(duration_us * 343 + 10000) / 20000`. Range 2..400 cm sets `VALID=1`; timeouts/out-of-range emit `DIST_CM=-1,VALID=0`.
3. **Host Serial Verification Tool** ([scripts/test_sensor_serial.py](../../scripts/test_sensor_serial.py)):
   * 20/20 test cases PASSED: Validated 128-byte line bound, CRLF/LF trimming, oversize discard through next `\n`, schema compliance, invalid range rejection, duplicate SEQ rejection, 32-bit wrap, and backward jump session resets.
   * Mock 10 Hz stream (5.0s, 50 records) verified: zero packet loss, critical close obstacle flags (<= 50 cm) correctly tagged.
4. **Transport & Freshness Replay Suite** ([scripts/verify_sensor_fusion_replay.py](../../scripts/verify_sensor_fusion_replay.py)):
   * 10/10 test cases PASSED: Verified 3-valid-record recovery (span >= 150 ms, gap <= 300 ms), 300 ms staleness watchdog expiry, immediate STOP candidate detection (<= 50 cm) during recovery, newer invalid record clearing distance, delay drift detection (> 200 ms), and backwards sequence reset.
5. **Android USB Serial Package** (`dev.navisense.usb`):
   * [SensorRecord.kt](../../android/app/src/main/java/dev/navisense/usb/SensorRecord.kt): Data class with monotonic receipt timestamp (`receiptMonotonicMs`) and `isImmediateStopCandidate` (<= 50 cm).
   * [SensorHealth.kt](../../android/app/src/main/java/dev/navisense/usb/SensorHealth.kt): State machine (`DISCONNECTED`, `CONNECTING`, `RECOVERING`, `HEALTHY`, `STALE`, `DEGRADED_INVALID`) and `SensorState` snapshot.
   * [SensorParser.kt](../../android/app/src/main/java/dev/navisense/usb/SensorParser.kt): Bounded parser (128-byte limit, oversize discard, US-ASCII decoding, regex schema verification).
   * [SensorStateManager.kt](../../android/app/src/main/java/dev/navisense/usb/SensorStateManager.kt): Connection lifecycle, 3-record recovery, delay drift tracking, and `evaluateHealth()` periodic watchdog API for Rishav's Risk Engine.
   * [UsbSensorAdapter.kt](../../android/app/src/main/java/dev/navisense/usb/UsbSensorAdapter.kt): Complete Android USB adapter handling background worker thread, transport abstraction (`UsbTransport`), sequence loss diagnostics (`totalLostPackets`), and immediate STOP callbacks.
   * Unit Tests: `SensorParserTest.kt`, `SensorStateManagerTest.kt`, `UsbSensorAdapterTest.kt`.

---

## 2. Hardware & Electrical Verification

### 2.1 HC-SR04 ECHO Voltage Divider
```text
HC-SR04 ECHO (5.0V Pulse)
          |
         [R1: 1.0 kΩ]
          |
          +--------------------> ESP32-S3 GPIO 5 (Input)
          |
         [R2: 2.0 kΩ]
          |
         GND (Common Ground)
```
$$V_{out} = 5.0\text{ V} \times \frac{2000\,\Omega}{1000\,\Omega + 2000\,\Omega} = 3.33\text{ V}$$
* $3.33\text{ V}$ is safely within the ESP32-S3 high-level input window ($0.75 \times V_{DD} \le V_{IH} \le 3.6\text{ V}$).
* Divider current draw: $1.67\text{ mA}$, well within HC-SR04 drive capability.

### 2.2 Pinout & Wiring Configuration
| Component Pin | ESP32-S3 Pin / Rail | Wire Type | Voltage Level | Notes |
|---|---|---|---|---|
| **HC-SR04 VCC** | ESP32 5V (VBUS / VIN) | 22–24 AWG Red | 5.0 V DC | Powered directly from phone USB-C OTG |
| **HC-SR04 GND** | ESP32 GND | 22–24 AWG Black | 0.0 V | Common ground plane |
| **HC-SR04 TRIG** | ESP32 GPIO 4 (Output) | Jumper Wire | 3.3 V CMOS | 10 µs trigger pulse |
| **HC-SR04 ECHO** | ESP32 GPIO 5 (Input) | Through 1k/2k divider | 3.33 V divided | Scaled down from 5.0 V |

---

## 3. Phase 0 Inter-Subsystem Information & Prerequisite Requests Matrix

To guarantee clean integration from Phase 0 into Phase 1 and downstream acceptance gates, Rohan coordinates the following requirements:

### A. To Rishav (System Integration, Voice UX & Shell Lead)
1. **Android USB Lifecycle Integration:**
   * Integrate `dev.navisense.usb.UsbSensorAdapter` into the foreground navigation service.
   * Register a `BroadcastReceiver` for `ACTION_USB_DEVICE_ATTACHED` and `ACTION_USB_DEVICE_DETACHED`.
   * Add USB Host feature and ESP32-S3 VID/PID to `device_filter.xml`:
     * Vendor ID: `0x303A` (Decimal: `12346`)
     * Product ID: `0x1001` (Decimal: `4097`)
2. **Watchdog Loop Hookup:**
   * Confirm Rishav's 50 ms Risk Engine watchdog loop calls `adapter.evaluateHealth()` to update `SensorHealth` and detect staleness (> 300 ms) even when USB packets drop.
3. **Immediate STOP Priority:**
   * Confirm Risk Engine checks `record.isImmediateStopCandidate` (valid <= 50 cm) to trigger immediate emergency STOP (< 100 ms decision, < 500 ms speech onset) without waiting for vision inference or full 3-packet recovery.

### B. To Samik (Android Perception & Camera Lead)
1. **Rig Mechanical Clamp Clearance:**
   * Confirm chest mount phone clamp does not obstruct the phone's bottom USB-C port or cause mechanical strain on the right-angle USB-C OTG cable connected to the ESP32-S3.
2. **Coaxial Optical Alignment:**
   * Maintain rigid forward alignment between HC-SR04 transducers and rear camera lens (±5° pitch/yaw).
3. **AC-06 Co-Benchmarking Under Inference Load:**
   * For the AC-06 benchmark (T+19:00–19:30), confirm Samik will run concurrent on-device vision inference (Locate + Mobility YOLO) to validate latency targets under realistic CPU/NPU contention.

### C. To Spandan (Models & Datasets Lead)
1. **Ultrasonic vs Camera FOV Coverage:**
   * HC-SR04 has a narrow ~15° cone facing straight ahead (effective 2–400 cm).
   * Mobile camera FOV is ~68°–75° diagonal.
   * Test scenes and walking corridors must account for this: direct forward corridor is protected by ultrasonic; peripheral obstacles (> 10° off-axis) rely on Mobility YOLO.
2. **Physical Acoustic Limitations:**
   * Disclose non-reflective scenarios: angled smooth surfaces (> 45° incident angle), sound-absorbing fabrics, and ground drop-offs/downward stairs.

### D. To Subham (Laptop Memory & API Lead)
1. **Network Isolation Confirmed:**
   * Confirmed 100% transport independence. The sensor node operates purely over USB CDC/OTG with Wi-Fi/BT radios disabled; zero packets touch the laptop Wi-Fi hotspot subnet (`192.168.43.0/24`).
2. **Error Taxonomy Alignment:**
   * Confirmed clean separation between sensor transport health states (`DISCONNECTED`, `STALE`, `DEGRADED_INVALID`) and laptop memory API errors (HTTP 504 timeout, connection refused, age > 60s stale).

---

## 4. Rohan Deliverables & Acceptance Gates Roadmap

| Timeline | Deliverable / Gate | Description & Scope |
|---|---|---|
| **T+01:45** | **Handoff S03 (H3)** | Hardware spec, 10 Hz firmware, serial test tool (20/20 PASS), replay harness (10/10 PASS), Android USB module. |
| **T+04:00** | **Handoff S05 (H3)** | Exact sensor acquisition: 10 Hz bounded loops, sequence progression, zero backlog verification. |
| **T+07:00** | **Handoff S08 (H3)** | Android USB adapter live connection, bounded LF/CRLF parsing, monotonic receipt timestamps, recovery & delay checks. |
| **T+11:30** | **Handoff S13 (H3)** | Stable Android USB adapter handling bounded line parsing (128-byte limit), monotonic receipt timestamps (<= 300 ms validity), sequence loss diagnostics, and 3-valid-packet recovery logic. |
| **T+15:30** | **Handoff S17 Freeze** | Freeze physical sensor mount, wiring, and firmware configuration. Pre-mark 6 bench distances (30, 50, 75, 100, 150, 200 cm) with dual optical/audio recording setup. |
| **T+17:00–17:20** | **AC-08 (Lead)** | Sensor Bench Calibration: 120 physical readings across 6 pre-marked distances (>= 90% validity per distance, median absolute error <= 5 cm). |
| **T+19:00–19:30** | **AC-06 (Lead)** | Physical Obstacle to Audible STOP Latency: 20 bench presentations (10 at 30 cm, 10 at 40 cm) under live inference load (decision <= 100 ms, audio <= 500 ms, physical-to-audio <= 750 ms, zero missed STOPs). |
| **T+19:30–20:00** | **AC-09 (Lead)** | USB Transport Stability & Recovery: 10-minute continuous stream without crash or drift, followed by 5 physical cable disconnect/reconnect cycles (recovery <= 2.0 s). |
| **T+18:30–21:30** | **Peer Reviews** | Reviews AC-05 (Samik mobile inference latency) and AC-14 (Subham full flow integration). Contributes to AC-01, AC-07, AC-10, AC-12. |
| **T+23:30–24:00** | **Live Demo S18** | Manages physical hardware & mount during demo. Demonstrates immediate proximity STOP bypassing vision, sensor disconnect resilience, and acoustic limitation disclosure. |

---

## 5. Frozen Contract Hash Re-Verification

- **`docs/README.md` SHA-256:** `54B140D1442F9E82DFCA024DE157BD6505452906968EC92588D8B2337F5990BA` (MATCH - Frozen)
- **`docs/guidance.md` SHA-256:** `A317342E58F0F29528002A3581C804B403070DB99F8EE8622914722609D7596E` (MATCH - Frozen)

---
*Communication Branch Rule: All work/progress/handoff/review/decision messages belong in communication's append-only team-chat.md.*
