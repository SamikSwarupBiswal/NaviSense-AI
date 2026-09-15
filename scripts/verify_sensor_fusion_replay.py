#!/usr/bin/env python3
"""
NaviSense AI — Sensor Transport & Freshness Replay Verification Suite

Owner: Rohan (Hardware, Firmware & USB Transport)
Authority: PRD v3.2 Section 14.1 & 14.2 (Freshness, Recovery & Reconnection)

Tests the exact PRD requirements:
1. Usable distance requires VALID=1 and receipt age <= 300 ms.
2. Newer invalid record immediately invalidates prior distance.
3. Recovery requires 3 consecutive valid records spanning >= 150 ms with no gap > 300 ms.
4. Immediate STOP candidate: fresh valid reading <= 50 cm triggers STOP immediately
   even during initial recovery (without declaring recovery/clearance).
5. Watchdog health staleness: detects silence/staleness even if no bytes arrive.
6. Delayed delivery detection: if receipt time advances > 200 ms more than device
   uptime since recovery baseline, stream is invalidated.
7. Sequence/Uptime backwards wrap or reset reopens stream as a new session.
"""

import sys
import time
from typing import Optional, List, Tuple
from enum import Enum


class SensorStatus(Enum):
    DISCONNECTED = "DISCONNECTED"
    CONNECTING = "CONNECTING"
    RECOVERING = "RECOVERING"
    HEALTHY = "HEALTHY"
    STALE = "STALE"
    DEGRADED_INVALID = "DEGRADED_INVALID"


class SensorEvent:
    def __init__(self, seq: int, up_ms: int, dist_cm: int, valid: bool, receipt_monotonic_ms: float):
        self.seq = seq
        self.up_ms = up_ms
        self.dist_cm = dist_cm
        self.valid = valid
        self.receipt_monotonic_ms = receipt_monotonic_ms


class AndroidSensorAdapter:
    """
    Simulates the phone-side Android USB Sensor Manager implementing PRD 14.1 & 14.2.
    """
    def __init__(self):
        self.status = SensorStatus.DISCONNECTED
        self.session_id = 0
        self.last_accepted_record: Optional[SensorEvent] = None
        self.usable_distance_cm: Optional[int] = None
        self.immediate_stop_candidate: bool = False

        # Recovery tracking
        self.recovery_records: List[SensorEvent] = []
        self.recovery_baseline_receipt_ms: Optional[float] = None
        self.recovery_baseline_up_ms: Optional[float] = None

        # Uptime & sequence wrap tracking
        self.prev_seq: Optional[int] = None
        self.prev_up_ms: Optional[int] = None

    def connect(self, timestamp_ms: float):
        self.status = SensorStatus.CONNECTING
        self.session_id += 1
        self.last_accepted_record = None
        self.usable_distance_cm = None
        self.immediate_stop_candidate = False
        self.recovery_records.clear()
        self.recovery_baseline_receipt_ms = None
        self.recovery_baseline_up_ms = None
        self.prev_seq = None
        self.prev_up_ms = None

    def detach(self):
        self.status = SensorStatus.DISCONNECTED
        self.last_accepted_record = None
        self.usable_distance_cm = None
        self.immediate_stop_candidate = False
        self.recovery_records.clear()

    def process_record(self, seq: int, up_ms: int, dist_cm: int, valid: bool, receipt_monotonic_ms: float) -> Tuple[SensorStatus, Optional[int], bool]:
        """
        Processes a structurally valid record received at receipt_monotonic_ms.
        Returns: (status, usable_distance_cm, immediate_stop_candidate)
        """
        # Step 1: Modulo-2^32 check for backwards sequence/uptime
        TWO_POW_32 = 1 << 32
        HALF_POW_32 = 1 << 31

        if self.prev_seq is not None and self.prev_up_ms is not None:
            seq_delta = (seq - self.prev_seq) % TWO_POW_32
            up_delta = (up_ms - self.prev_up_ms) % TWO_POW_32

            if seq_delta == 0:
                # Duplicate record: ignore without refreshing time
                return self.evaluate_health(receipt_monotonic_ms)

            if seq_delta >= HALF_POW_32 or up_delta >= HALF_POW_32:
                # Sequence or uptime jumped backwards: reopen as new session
                self.connect(receipt_monotonic_ms)
                # Discard record per PRD 14.1
                return self.evaluate_health(receipt_monotonic_ms)

        self.prev_seq = seq
        self.prev_up_ms = up_ms

        event = SensorEvent(seq, up_ms, dist_cm, valid, receipt_monotonic_ms)
        self.last_accepted_record = event

        # Step 2: Immediate STOP check (PRD Section 14.2)
        # "fresh valid <= 50 cm record, which may trigger STOP immediately but cannot establish recovery or clearance"
        if valid and 2 <= dist_cm <= 50:
            self.immediate_stop_candidate = True
        else:
            self.immediate_stop_candidate = False

        # Step 3: Handle Invalid Record
        if not valid or dist_cm < 2 or dist_cm > 400:
            # Newer invalid record immediately invalidates previous distance
            self.usable_distance_cm = None
            self.recovery_records.clear()
            self.status = SensorStatus.DEGRADED_INVALID
            return self.status, None, self.immediate_stop_candidate

        # Step 4: Recovery tracking (3 valid advancing records spanning >= 150 ms with no gap > 300 ms)
        if self.status != SensorStatus.HEALTHY:
            if len(self.recovery_records) > 0:
                gap_ms = receipt_monotonic_ms - self.recovery_records[-1].receipt_monotonic_ms
                if gap_ms > 300:
                    # Gap exceeded: reset recovery sequence
                    self.recovery_records.clear()

            self.recovery_records.append(event)
            self.status = SensorStatus.RECOVERING

            if len(self.recovery_records) >= 3:
                time_span_ms = receipt_monotonic_ms - self.recovery_records[0].receipt_monotonic_ms
                if time_span_ms >= 150:
                    # Recovery achieved!
                    self.status = SensorStatus.HEALTHY
                    self.usable_distance_cm = dist_cm
                    self.recovery_baseline_receipt_ms = receipt_monotonic_ms
                    self.recovery_baseline_up_ms = up_ms
                    return self.status, self.usable_distance_cm, self.immediate_stop_candidate

            # Still in recovery; distance not usable yet (unless immediate stop candidate)
            self.usable_distance_cm = None
            return self.status, None, self.immediate_stop_candidate

        # Step 5: Healthy State Verification & Delay Check (PRD 14.2)
        if self.recovery_baseline_receipt_ms is not None and self.recovery_baseline_up_ms is not None:
            elapsed_receipt_ms = receipt_monotonic_ms - self.recovery_baseline_receipt_ms
            elapsed_device_ms = (up_ms - self.recovery_baseline_up_ms) % TWO_POW_32

            if elapsed_receipt_ms > elapsed_device_ms + 200:
                # Receipt time advanced > 200 ms more than device uptime: invalid delay!
                self.usable_distance_cm = None
                self.recovery_records.clear()
                self.status = SensorStatus.DEGRADED_INVALID
                return self.status, None, self.immediate_stop_candidate

        # All checks passed: update usable distance
        self.usable_distance_cm = dist_cm
        return self.status, self.usable_distance_cm, self.immediate_stop_candidate

    def evaluate_health(self, current_monotonic_ms: float) -> Tuple[SensorStatus, Optional[int], bool]:
        """
        Called by app watchdog (e.g. 50 ms loop) even when no new bytes arrive.
        Enforces <= 300 ms freshness.
        """
        if self.status == SensorStatus.DISCONNECTED:
            return SensorStatus.DISCONNECTED, None, False

        if self.last_accepted_record is None:
            return self.status, None, False

        age_ms = current_monotonic_ms - self.last_accepted_record.receipt_monotonic_ms
        if age_ms > 300:
            # Stale reading: invalidate usable distance
            self.usable_distance_cm = None
            self.immediate_stop_candidate = False
            if self.status == SensorStatus.HEALTHY:
                self.status = SensorStatus.STALE

        return self.status, self.usable_distance_cm, self.immediate_stop_candidate


def run_transport_verification_tests():
    print("================================================================")
    print("NaviSense AI — Sensor Transport & Freshness Verification Suite")
    print("================================================================")

    adapter = AndroidSensorAdapter()
    passed = 0
    failed = 0

    def assert_case(name: str, cond: bool, msg: str = ""):
        nonlocal passed, failed
        if cond:
            print(f"[PASS] {name}")
            passed += 1
        else:
            print(f"[FAIL] {name}: {msg}")
            failed += 1

    # Case 1: Fresh Connection requires 3 valid records spanning >= 150 ms
    adapter.connect(1000.0)
    st, dist, stop = adapter.process_record(1, 100, 80, True, 1000.0)
    assert_case("Connection starts in RECOVERING, no usable dist", st == SensorStatus.RECOVERING and dist is None)

    st, dist, stop = adapter.process_record(2, 200, 80, True, 1100.0)
    assert_case("2nd record still RECOVERING", st == SensorStatus.RECOVERING and dist is None)

    st, dist, stop = adapter.process_record(3, 300, 80, True, 1200.0)
    # Span is 1200 - 1000 = 200 ms >= 150 ms
    assert_case("3rd valid record completes recovery to HEALTHY", st == SensorStatus.HEALTHY and dist == 80)

    # Case 2: Freshness Timeout (> 300 ms)
    # At t=1200 ms last record arrived; check watchdog at t=1550 ms (age = 350 ms > 300 ms)
    st, dist, stop = adapter.evaluate_health(1550.0)
    assert_case("Watchdog detects staleness at age > 300ms", st == SensorStatus.STALE and dist is None)

    # Case 3: Immediate STOP candidate on close hazard (<= 50 cm) even during recovery
    adapter.connect(2000.0)
    st, dist, stop = adapter.process_record(10, 1000, 35, True, 2000.0)
    assert_case("Fresh <= 50cm yields immediate_stop_candidate=True during recovery",
                stop is True and dist is None and st == SensorStatus.RECOVERING)

    # Case 4: Newer invalid record immediately invalidates prior distance
    # Recover first:
    adapter.process_record(11, 1100, 75, True, 2100.0)
    st, dist, stop = adapter.process_record(12, 1200, 75, True, 2200.0)
    assert_case("Stream recovered to HEALTHY", st == SensorStatus.HEALTHY and dist == 75)

    # Next record is VALID=0:
    st, dist, stop = adapter.process_record(13, 1300, -1, False, 2300.0)
    assert_case("Newer invalid record immediately clears distance",
                st == SensorStatus.DEGRADED_INVALID and dist is None)

    # Case 5: Delay Progression Tracking (Elapsed receipt time > device uptime + 200ms)
    # Recover at baseline
    adapter.connect(3000.0)
    adapter.process_record(20, 2000, 100, True, 3000.0)
    adapter.process_record(21, 2100, 100, True, 3100.0)
    st, dist, stop = adapter.process_record(22, 2200, 100, True, 3200.0)
    assert_case("Stream recovered baseline", st == SensorStatus.HEALTHY and dist == 100)

    # Host time advances 500 ms (t=3700), but device uptime only advanced 200 ms (up=2400)
    # Diff = 500 - 200 = 300 ms > 200 ms delay threshold!
    st, dist, stop = adapter.process_record(23, 2400, 100, True, 3700.0)
    assert_case("Delayed delivery (> 200ms drift) invalidates stream",
                st == SensorStatus.DEGRADED_INVALID and dist is None)

    # Case 6: Backwards Sequence Reset
    adapter.connect(4000.0)
    adapter.process_record(50, 5000, 90, True, 4000.0)
    orig_session = adapter.session_id
    # Sequence jumps backwards from 50 to 10
    adapter.process_record(10, 5100, 90, True, 4100.0)
    assert_case("Backwards sequence triggers session reset", adapter.session_id == orig_session + 1)

    print("----------------------------------------------------------------")
    print(f"Transport Verification Results: {passed} PASSED, {failed} FAILED")
    print("----------------------------------------------------------------")
    return failed == 0


if __name__ == "__main__":
    success = run_transport_verification_tests()
    sys.exit(0 if success else 1)
