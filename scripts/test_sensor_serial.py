#!/usr/bin/env python3
"""
NaviSense AI — Sensor Serial Protocol Verification & Testing Tool

Owner: Rohan (Hardware & Firmware)
Authority: PRD v3.2 Section 14.1 & 14.2; Rohan Guidance

Usage:
  # Self-test parser against synthetic PRD edge cases:
  python scripts/test_sensor_serial.py --self-test

  # Simulate a 10 Hz stream (mock mode without microcontroller):
  python scripts/test_sensor_serial.py --mock --duration 5

  # Connect to real ESP32-S3 over USB serial:
  python scripts/test_sensor_serial.py --port COM3 --baud 115200 --duration 10
"""

import sys
import os
import re
import time
import argparse
from typing import Optional, Tuple, Dict, Any, List

# PRD Section 14.1 Constraints
MAX_LINE_BYTES = 128
EXPECTED_VERSION = 1
MIN_VALID_DIST_CM = 2
MAX_VALID_DIST_CM = 400
TWO_POW_32 = 1 << 32
HALF_POW_32 = 1 << 31

# Strict PRD v1 Record Schema:
# V=1,SEQ=<unsigned 32-bit>,UP_MS=<unsigned 32-bit>,DIST_CM=<-1 or 2..400>,VALID=<0 or 1>
RECORD_REGEX = re.compile(r"^V=1,SEQ=(\d+),UP_MS=(\d+),DIST_CM=(-?\d+),VALID=([01])$")


class SensorRecord:
    """Represents a validated PRD v1 ultrasonic sensor record."""
    def __init__(self, raw_line: str, seq: int, up_ms: int, dist_cm: int, valid: bool, receipt_time_s: float):
        self.raw_line = raw_line
        self.seq = seq
        self.up_ms = up_ms
        self.dist_cm = dist_cm
        self.valid = valid
        self.receipt_time_s = receipt_time_s

    def __repr__(self) -> str:
        return (f"SensorRecord(seq={self.seq}, up_ms={self.up_ms}, "
                f"dist_cm={self.dist_cm}, valid={self.valid}, t={self.receipt_time_s:.3f})")


class SensorParser:
    """
    Implements PRD Sections 14.1 and 14.2 parsing rules:
    - Buffers partial lines, accepts LF and CRLF
    - Enforces 128-byte line limit (discarding oversized records through next newline)
    - Strict schema and type checking
    - Unsigned 32-bit sequence and uptime modulo comparison
    - Reset detection on backward sequence or uptime
    """
    def __init__(self):
        self._buffer = bytearray()
        self.discarding_oversize = False
        self.session_generation = 0
        self.prev_seq: Optional[int] = None
        self.prev_up_ms: Optional[int] = None
        self.prev_receipt_time: Optional[float] = None

        # Statistics
        self.total_lines = 0
        self.accepted_records = 0
        self.valid_distance_records = 0
        self.invalid_distance_records = 0
        self.malformed_records = 0
        self.oversize_lines = 0
        self.duplicates = 0
        self.sequence_gaps = 0
        self.session_resets = 0

    def feed_bytes(self, chunk: bytes, receipt_time: Optional[float] = None) -> List[Tuple[Optional[SensorRecord], Optional[str]]]:
        """
        Feeds incoming bytes and returns a list of (record, error_message) tuples.
        """
        if receipt_time is None:
            receipt_time = time.monotonic()

        results = []
        for byte in chunk:
            if self.discarding_oversize:
                if byte == ord('\n'):
                    self.discarding_oversize = False
                    self._buffer.clear()
                continue

            if byte == ord('\n'):
                # Line complete (strip trailing \r if present)
                if len(self._buffer) > 0 and self._buffer[-1] == ord('\r'):
                    line_bytes = bytes(self._buffer[:-1])
                else:
                    line_bytes = bytes(self._buffer)
                self._buffer.clear()

                self.total_lines += 1
                rec, err = self._parse_line(line_bytes, receipt_time)
                results.append((rec, err))
            else:
                self._buffer.append(byte)
                if len(self._buffer) > MAX_LINE_BYTES:
                    # Oversized line: discard through newline per PRD 14.1
                    self.discarding_oversize = True
                    self.oversize_lines += 1
                    self.total_lines += 1
                    self._buffer.clear()
                    results.append((None, f"Oversize line exceeded {MAX_LINE_BYTES} bytes"))

        return results

    def _parse_line(self, line_bytes: bytes, receipt_time: float) -> Tuple[Optional[SensorRecord], Optional[str]]:
        try:
            line_str = line_bytes.decode('ascii').strip()
        except UnicodeDecodeError:
            self.malformed_records += 1
            return None, "Malformed record: non-ASCII characters"

        if not line_str:
            return None, "Empty line"

        match = RECORD_REGEX.match(line_str)
        if not match:
            self.malformed_records += 1
            return None, f"Malformed record: does not match PRD v1 format: '{line_str}'"

        seq_str, up_ms_str, dist_str, valid_str = match.groups()

        seq = int(seq_str)
        up_ms = int(up_ms_str)
        dist_cm = int(dist_str)
        valid = (int(valid_str) == 1)

        # PRD Section 14.1: Bounds & Consistency Check
        if seq >= TWO_POW_32:
            self.malformed_records += 1
            return None, f"Sequence overflow (>= 2^32): {seq}"
        if up_ms >= TWO_POW_32:
            self.malformed_records += 1
            return None, f"Uptime overflow (>= 2^32): {up_ms}"

        if valid:
            if dist_cm < MIN_VALID_DIST_CM or dist_cm > MAX_VALID_DIST_CM:
                self.malformed_records += 1
                return None, f"Inconsistent record: VALID=1 but DIST_CM={dist_cm} not in 2..400"
        else:
            if dist_cm != -1:
                self.malformed_records += 1
                return None, f"Inconsistent record: VALID=0 but DIST_CM={dist_cm} != -1"

        # PRD Section 14.1: Modulo-2^32 Sequence & Uptime Progression
        if self.prev_seq is not None and self.prev_up_ms is not None:
            seq_delta = (seq - self.prev_seq) % TWO_POW_32
            up_delta = (up_ms - self.prev_up_ms) % TWO_POW_32

            if seq_delta == 0:
                self.duplicates += 1
                return None, f"Duplicate record ignored (SEQ={seq})"

            if seq_delta >= HALF_POW_32 or up_delta >= HALF_POW_32:
                # Sequence or uptime moved backwards: reset session per PRD 14.1
                self.session_resets += 1
                self.session_generation += 1
                self.prev_seq = seq
                self.prev_up_ms = up_ms
                self.prev_receipt_time = receipt_time
                return None, f"Session reset triggered: backwards SEQ/UP_MS delta (SEQ={seq}, UP_MS={up_ms})"

            if seq_delta > 1:
                gap = seq_delta - 1
                self.sequence_gaps += gap

        self.prev_seq = seq
        self.prev_up_ms = up_ms
        self.prev_receipt_time = receipt_time

        self.accepted_records += 1
        if valid:
            self.valid_distance_records += 1
        else:
            self.invalid_distance_records += 1

        record = SensorRecord(line_str, seq, up_ms, dist_cm, valid, receipt_time)
        return record, None


def run_self_test() -> bool:
    """Runs automated verification tests against PRD test vectors."""
    print("==================================================")
    print("NaviSense AI — Sensor Parser Self-Test")
    print("==================================================")
    parser = SensorParser()

    test_cases = [
        # (Input bytes, expected_valid, expected_dist, expected_err_substring)
        (b"V=1,SEQ=1001,UP_MS=100100,DIST_CM=45,VALID=1\n", True, 45, None),
        (b"V=1,SEQ=1002,UP_MS=100200,DIST_CM=-1,VALID=0\r\n", False, -1, None),
        (b"V=1,SEQ=1003,UP_MS=100300,DIST_CM=2,VALID=1\n", True, 2, None),
        (b"V=1,SEQ=1004,UP_MS=100400,DIST_CM=400,VALID=1\n", True, 400, None),
        # Inconsistent VALID=1 with out-of-range dist:
        (b"V=1,SEQ=1005,UP_MS=100500,DIST_CM=401,VALID=1\n", None, None, "Inconsistent record"),
        (b"V=1,SEQ=1006,UP_MS=100600,DIST_CM=1,VALID=1\n", None, None, "Inconsistent record"),
        # Inconsistent VALID=0 with non -1:
        (b"V=1,SEQ=1007,UP_MS=100700,DIST_CM=50,VALID=0\n", None, None, "Inconsistent record"),
        # Alternate / Malformed formats (e.g. D,104 or missing fields):
        (b"D,104\n", None, None, "does not match PRD v1 format"),
        (b"V=2,SEQ=1008,UP_MS=100800,DIST_CM=50,VALID=1\n", None, None, "does not match PRD v1 format"),
        (b"V=1,UP_MS=100900,DIST_CM=50,VALID=1\n", None, None, "does not match PRD v1 format"),
        # Duplicate record:
        (b"V=1,SEQ=1008,UP_MS=100800,DIST_CM=50,VALID=1\n", True, 50, None),
        (b"V=1,SEQ=1008,UP_MS=100800,DIST_CM=50,VALID=1\n", None, None, "Duplicate record"),
        # Sequence gap:
        (b"V=1,SEQ=1012,UP_MS=101200,DIST_CM=60,VALID=1\n", True, 60, None),
        # Oversized line (> 128 bytes):
        (b"V=1,SEQ=1013," + b"A" * 150 + b"\n", None, None, "Oversize line"),
        # Valid after oversize discard:
        (b"V=1,SEQ=1014,UP_MS=101400,DIST_CM=70,VALID=1\n", True, 70, None),
        # Sequence wrap forward test: reset parser baseline to near 2^32 - 1
        (b"V=1,SEQ=4294967290,UP_MS=4294967000,DIST_CM=80,VALID=1\n", None, None, "Session reset triggered"),
        (b"V=1,SEQ=4294967295,UP_MS=4294967100,DIST_CM=82,VALID=1\n", True, 82, None),
        # Forward wrap around 2^32 (delta = 2, strictly between 0 and 2^31):
        (b"V=1,SEQ=1,UP_MS=100,DIST_CM=85,VALID=1\n", True, 85, None),
        # Backwards reset trigger:
        (b"V=1,SEQ=100,UP_MS=50000,DIST_CM=90,VALID=1\n", True, 90, None),
        (b"V=1,SEQ=10,UP_MS=1000,DIST_CM=90,VALID=1\n", None, None, "Session reset triggered"),
    ]

    passed = 0
    failed = 0

    for i, (chunk, exp_valid, exp_dist, exp_err) in enumerate(test_cases, 1):
        res = parser.feed_bytes(chunk)
        assert len(res) >= 1, f"Test {i}: No output returned"
        rec, err = res[0]

        test_ok = True
        if exp_err:
            if not err or exp_err.lower() not in err.lower():
                print(f"[FAIL] Case {i}: Expected error containing '{exp_err}', got err='{err}'")
                test_ok = False
        else:
            if err:
                print(f"[FAIL] Case {i}: Unexpected error: '{err}' for input {chunk}")
                test_ok = False
            elif rec is None:
                print(f"[FAIL] Case {i}: Expected valid record, got None")
                test_ok = False
            elif rec.valid != exp_valid or rec.dist_cm != exp_dist:
                print(f"[FAIL] Case {i}: Mismatch (valid={rec.valid} vs {exp_valid}, dist={rec.dist_cm} vs {exp_dist})")
                test_ok = False

        if test_ok:
            passed += 1
            print(f"[PASS] Case {i:02d}: {chunk.strip()[:45]!r} -> {rec or err}")
        else:
            failed += 1

    print("\n--------------------------------------------------")
    print(f"Self-Test Summary: {passed} PASSED, {failed} FAILED")
    print(f"Parser stats: {parser.accepted_records} accepted, {parser.duplicates} duplicates, "
          f"{parser.sequence_gaps} lost packets, {parser.session_resets} resets")
    print("--------------------------------------------------")
    return failed == 0


def run_mock_stream(duration_sec: float = 5.0):
    """Simulates a real-time 10 Hz ESP32-S3 ultrasonic serial stream."""
    print("==================================================")
    print(f"NaviSense AI — Running Mock Serial Stream ({duration_sec}s at 10 Hz)")
    print("==================================================")

    parser = SensorParser()
    seq = 1000
    up_ms = 50000
    start_time = time.monotonic()
    next_tick = start_time

    distances = [120, 115, 110, 105, 95, 80, 65, 45, 30, 25, -1, 30, 35, 40, 55, 75, 100]
    idx = 0

    while (time.monotonic() - start_time) < duration_sec:
        now = time.monotonic()
        if now < next_tick:
            time.sleep(max(0.001, next_tick - now))
            continue

        next_tick += 0.100  # 10 Hz (100 ms)

        target_dist = distances[idx % len(distances)]
        idx += 1
        valid = 1 if (target_dist >= 2 and target_dist <= 400) else 0
        dist = target_dist if valid else -1

        line = f"V=1,SEQ={seq},UP_MS={up_ms},DIST_CM={dist},VALID={valid}\n"
        seq += 1
        up_ms += 100

        results = parser.feed_bytes(line.encode('ascii'))
        for rec, err in results:
            if rec:
                status = f"DIST: {rec.dist_cm:3d} cm [VALID={rec.valid}]"
                if rec.valid and rec.dist_cm <= 50:
                    status += "  <-- CRITICAL CLOSE (Immediate STOP candidate)"
                print(f"[T+{rec.receipt_time_s - start_time:05.2f}s] SEQ={rec.seq:4d} UP={rec.up_ms:6d}ms | {status}")
            elif err:
                print(f"[ERR] {err}")

    print("\n--------------------------------------------------")
    print("Mock Stream Results:")
    print(f"  Total records received: {parser.accepted_records}")
    print(f"  Valid readings:         {parser.valid_distance_records}")
    print(f"  Invalid readings:       {parser.invalid_distance_records}")
    print(f"  Duplicates:             {parser.duplicates}")
    print(f"  Sequence gaps:          {parser.sequence_gaps}")
    print("--------------------------------------------------")


def run_live_serial(port: str, baud: int = 115200, duration_sec: float = 10.0):
    """Connects to a physical ESP32-S3 over serial and verifies live stream."""
    try:
        import serial
    except ImportError:
        print("[ERROR] pyserial is not installed. Please run: pip install pyserial")
        sys.exit(1)

    print(f"Connecting to ESP32-S3 on {port} at {baud} baud...")
    try:
        ser = serial.Serial(port, baud, timeout=0.2)
    except Exception as e:
        print(f"[ERROR] Failed to open serial port {port}: {e}")
        sys.exit(1)

    parser = SensorParser()
    start_time = time.time()
    print(f"Streaming data for {duration_sec}s. Press Ctrl+C to abort.\n")

    try:
        while (time.time() - start_time) < duration_sec:
            raw_bytes = ser.read(ser.in_waiting or 1)
            if raw_bytes:
                results = parser.feed_bytes(raw_bytes)
                for rec, err in results:
                    if rec:
                        print(f"SEQ={rec.seq:<6d} UP={rec.up_ms:<8d}ms DIST={rec.dist_cm:3d}cm VALID={rec.valid}")
                    elif err:
                        print(f"[WARN] {err}")
    except KeyboardInterrupt:
        print("\nAborted by user.")
    finally:
        ser.close()

    print("\n--------------------------------------------------")
    print(f"Live Stream Session Summary ({port}):")
    print(f"  Total Lines:      {parser.total_lines}")
    print(f"  Accepted Records: {parser.accepted_records}")
    print(f"  Valid Readings:   {parser.valid_distance_records}")
    print(f"  Invalid Readings: {parser.invalid_distance_records}")
    print(f"  Malformed:        {parser.malformed_records}")
    print(f"  Duplicates:       {parser.duplicates}")
    print(f"  Sequence Gaps:    {parser.sequence_gaps}")
    print("--------------------------------------------------")


def main():
    parser = argparse.ArgumentParser(description="NaviSense AI Sensor Serial Tool")
    parser.add_argument("--self-test", action="store_true", help="Run parser unit test cases")
    parser.add_argument("--mock", action="store_true", help="Simulate a 10 Hz sensor stream without hardware")
    parser.add_argument("--port", type=str, default=None, help="Serial COM port (e.g. COM3 or /dev/ttyUSB0)")
    parser.add_argument("--baud", type=int, default=115200, help="Serial baud rate (default 115200)")
    parser.add_argument("--duration", type=float, default=5.0, help="Run duration in seconds")

    args = parser.parse_args()

    if args.self_test:
        success = run_self_test()
        sys.exit(0 if success else 1)
    elif args.mock or args.port is None:
        if args.port is None and not args.mock:
            print("No --port specified. Running self-test and mock stream by default...\n")
            if not run_self_test():
                sys.exit(1)
            print("")
        run_mock_stream(args.duration)
    else:
        run_live_serial(args.port, args.baud, args.duration)


if __name__ == "__main__":
    main()
