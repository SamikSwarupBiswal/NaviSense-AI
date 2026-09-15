# Rohan — Hardware & Electrical Specification Record

**Document:** Hardware, Wiring, Mount and Electrical Limits  
**Owner:** Rohan  
**Authority:** [PRD v3.2](../README.md) Sections 7.1–7.3, 14.1–14.2, 28.1; [Rohan Guidance](guidance.md)  
**Status:** Baseline established for Phase 0  

---

## 1. Physical Hardware Components

| Component | Selected Specification | Purpose |
|---|---|---|
| **Microcontroller** | ESP32-S3 DevKit (ESP32-S3-WROOM-1 / DevKitC-1) | Sensor controller, 10 Hz acquisition loop, USB CDC serial communication |
| **Ultrasonic Sensor** | HC-SR04 (4-pin: VCC, GND, TRIG, ECHO) | Forward distance sensing (range 2 cm – 400 cm) |
| **USB Host / Mobile** | Android smartphone (Android 11+ / API 30+, USB-C OTG enabled) | Supplies 5V host power to ESP32-S3; runs Android USB host driver, perception fusion, risk engine |
| **USB Interconnect** | USB-C to USB-C (or OTG adapter cable) | High-reliability bidirectional power & data connection |
| **Resistor Divider** | R1 = 1.0 kΩ (±1%), R2 = 2.0 kΩ (±1%) | Steps down HC-SR04 5.0 V ECHO pulse to safe ~3.33 V for ESP32-S3 GPIO input |
| **Rig / Mount** | Fixed forward-facing chest / lanyard harness | Rigid forward alignment with phone camera, stable walking plane |

---

## 2. Electrical Protection: HC-SR04 ECHO Voltage Divider

### 2.1 The Problem
The HC-SR04 sensor operates at 5.0 V VCC and outputs a ~5.0 V pulse on the `ECHO` line. Connecting a 5.0 V logic output directly to an ESP32-S3 GPIO pin exceeds the maximum absolute rating (typically $V_{DD} + 0.3 \text{ V} \approx 3.6 \text{ V}$) and risks permanent damage to the microcontroller pin.

### 2.2 Divider Schematic
A passive 1 kΩ / 2 kΩ resistor voltage divider is implemented between `ECHO` and ESP32-S3 GPIO 5:

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

### 2.3 Voltage Calculation
$$V_{out} = V_{in} \times \frac{R_2}{R_1 + R_2} = 5.0\text{ V} \times \frac{2000\,\Omega}{1000\,\Omega + 2000\,\Omega} = 5.0 \times \frac{2}{3} \approx 3.33\text{ V}$$

* $3.33\text{ V}$ is safely within the ESP32-S3 high-level input window ($0.75 \times V_{DD} \le V_{IH} \le V_{DD} \approx 3.3\text{ V}$).
* Divider current draw: $I = \frac{5\text{ V}}{3000\,\Omega} \approx 1.67\text{ mA}$, safely within HC-SR04 output drive capacity.

---

## 3. Complete Pinout & Wiring Table

| Component Pin | ESP32-S3 Pin / Rail | Wire Type | Voltage Level | Notes |
|---|---|---|---|---|
| **HC-SR04 VCC** | ESP32 5V (VBUS / VIN) | 22–24 AWG red wire | 5.0 V DC | Powered directly from USB-C OTG 5V supply |
| **HC-SR04 GND** | ESP32 GND | 22–24 AWG black wire | 0.0 V | Common ground plane |
| **HC-SR04 TRIG** | ESP32 GPIO 4 (Output) | Jumper wire | 3.3 V CMOS | Emits 10 µs trigger pulses from ESP32 |
| **HC-SR04 ECHO** | ESP32 GPIO 5 (Input) | Through 1k / 2k divider | 3.33 V divided | Scaled down from 5.0 V; never connect raw ECHO |

---

## 4. Power Budget & USB OTG Verification

* **Android Phone USB-C OTG Output:** Supplies standard 5.0 V @ 500 mA (minimum 2.5 W), up to 1.5 A under USB Type-C Current.
* **ESP32-S3 Current Draw:** Active Wi-Fi/BT is **disabled**; peak current with CPU at 80/160 MHz without radio is ~40–60 mA.
* **HC-SR04 Current Draw:** ~15 mA active sensing current.
* **Total Assembly Current:** < 80 mA, well within Android USB OTG power limits.
* **Thermal / Brownout Margin:** No power brownout observed under continuous 10 Hz pulsing.

---

## 5. Mechanical Mounting & Geometric Alignment

1. **Chest / Lanyard Rig:**
   * The HC-SR04 sensor is mounted on a rigid prototype plate secured to a chest strap or lanyard.
   * Transducer plane is oriented vertically perpendicular to the walking vector, facing directly forward.
   * Mounting height: ~100 cm – 120 cm from ground level to detect waist/chest-level hazards while avoiding ground reflections.
2. **Camera Alignment (Coordinated with Samik):**
   * The phone camera and ultrasonic transducers share an aligned forward axis ($\pm 5^\circ$ pitch/yaw deviation).
   * Ultrasonic field of view (~15° cone) monitors the primary walking corridor directly ahead.

---

## 6. Communication branch — mandatory

All work/progress/handoff/review/decision messages belong in communication's append-only team-chat.md. Only chat/decision-log changes go there; implementation and status changes stay on work branches. Preserve messages, link real evidence, record actual times and obtain receiver responses. The branch cannot authorize frozen-contract changes or substitute for tests. Section 6 of AGENTS.md defines the complete workflow.
