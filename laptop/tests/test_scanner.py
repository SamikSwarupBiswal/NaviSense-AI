"""Unit tests for Hard Scan transactional engine (laptop/scanner.py)."""

import pytest
import time
from typing import Any, List, Optional
from laptop.config.settings import LaptopConfig, SpatialZone
from laptop.scanner import (
    HardScanEngine,
    Detection,
    compute_iou,
    match_zone,
)
from laptop.storage.db import DatabaseManager


class MockFrameSource:
    """Mock frame source producing test frames or failing on demand."""
    def __init__(self, max_frames: int = 10, fail_at: Optional[int] = None):
        self.max_frames = max_frames
        self.fail_at = fail_at
        self.captured_count = 0

    def capture_frame(self) -> Optional[Any]:
        if self.fail_at is not None and self.captured_count == self.fail_at:
            return None
        if self.captured_count >= self.max_frames:
            return None
        self.captured_count += 1
        return f"mock_frame_{self.captured_count}"


class MockDetector:
    """Mock detector returning pre-configured detections per frame."""
    def __init__(self, detections_per_frame: List[List[Detection]]):
        self.detections_per_frame = detections_per_frame
        self.call_count = 0

    def detect(self, image_data: Any) -> List[Detection]:
        if self.call_count < len(self.detections_per_frame):
            dets = self.detections_per_frame[self.call_count]
        else:
            dets = []
        self.call_count += 1
        return dets


@pytest.fixture
def fast_config():
    """Fast config for tests (e.g. 0.05s scan duration for 10 frames)."""
    return LaptopConfig(
        scan_frames_count=10,
        scan_duration_seconds=0.05,  # 5ms per frame for snappy tests
        detection_confidence_threshold=0.60,
        min_frames_for_confirmation=6,
        camera_profile_id="test_cam",
        zones=[
            SpatialZone("zone_left", "Left Table", (0.0, 0.0, 0.33, 1.0)),
            SpatialZone("zone_center", "Center Table", (0.33, 0.0, 0.66, 1.0)),
            SpatialZone("zone_right", "Right Table", (0.66, 0.0, 1.0, 1.0)),
        ],
    )


@pytest.fixture
def scanner(tmp_path, fast_config):
    db = DatabaseManager(str(tmp_path / "test_scanner.db"))
    return HardScanEngine(db_manager=db, config=fast_config)


def test_compute_iou():
    # Identical boxes -> IoU = 1.0
    b1 = (0.1, 0.1, 0.3, 0.3)
    assert compute_iou(b1, b1) == pytest.approx(1.0)

    # Disjoint boxes -> IoU = 0.0
    b2 = (0.5, 0.5, 0.7, 0.7)
    assert compute_iou(b1, b2) == 0.0

    # 50% overlap box
    b3 = (0.1, 0.1, 0.2, 0.3)
    assert 0.0 < compute_iou(b1, b3) < 1.0


def test_match_zone(fast_config):
    # Left table
    z_left = match_zone((0.15, 0.5), fast_config.zones)
    assert z_left is not None and z_left.zone_id == "zone_left"

    # Center table
    z_center = match_zone((0.50, 0.5), fast_config.zones)
    assert z_center is not None and z_center.zone_id == "zone_center"

    # Outside (e.g. negative or > 1.0)
    z_out = match_zone((1.2, 0.5), fast_config.zones)
    assert z_out is None


def test_hard_scan_successful_acceptance(scanner):
    # 8 of 10 frames have 'keys' in center zone with confidence >= 0.60
    # box center = (0.45, 0.50) -> inside zone_center (0.33 to 0.66)
    box = (0.40, 0.45, 0.50, 0.55)
    dets = [
        [Detection("keys", 0.85, box)],  # Frame 1
        [Detection("keys", 0.88, box)],  # Frame 2
        [Detection("keys", 0.90, box)],  # Frame 3
        [],                              # Frame 4 (missed)
        [Detection("keys", 0.87, box)],  # Frame 5
        [Detection("keys", 0.92, box)],  # Frame 6
        [Detection("keys", 0.84, box)],  # Frame 7
        [Detection("keys", 0.89, box)],  # Frame 8
        [],                              # Frame 9 (missed)
        [Detection("keys", 0.86, box)],  # Frame 10 (total 8 frames)
    ]
    frame_source = MockFrameSource(max_frames=10)
    detector = MockDetector(dets)

    result = scanner.execute_scan(frame_source, detector)

    assert result.success is True
    assert result.sampled_frames == 10
    assert len(result.accepted_observations) == 1

    obs = result.accepted_observations[0]
    assert obs.object_name == "keys"
    assert obs.zone == "zone_center"
    assert obs.seen_frames == 8
    assert obs.sampled_frames == 10
    assert obs.confidence >= 0.85

    # Verify persisted in database
    status, candidates = scanner.db.query_latest_scan("keys", active_camera_profile_version=1)
    assert status == "found"
    assert len(candidates) == 1
    assert candidates[0].zone == "zone_center"


def test_hard_scan_rejection_insufficient_frames(scanner):
    # Only 5 of 10 frames detected -> requires >= 6 -> must write nothing
    box = (0.40, 0.45, 0.50, 0.55)
    dets = [
        [Detection("keys", 0.85, box)],  # 1
        [Detection("keys", 0.88, box)],  # 2
        [Detection("keys", 0.90, box)],  # 3
        [Detection("keys", 0.87, box)],  # 4
        [Detection("keys", 0.92, box)],  # 5 (only 5 frames)
        [], [], [], [], []
    ]
    frame_source = MockFrameSource(max_frames=10)
    detector = MockDetector(dets)

    result = scanner.execute_scan(frame_source, detector)

    assert result.success is True
    assert result.sampled_frames == 10
    assert len(result.accepted_observations) == 0  # No accepted observations

    # DB remains empty
    status, candidates = scanner.db.query_latest_scan("keys", active_camera_profile_version=1)
    assert status == "not_found"


def test_hard_scan_rejection_low_confidence(scanner):
    # 8 of 10 frames detected, but confidence is 0.55 (< 0.60 threshold)
    box = (0.40, 0.45, 0.50, 0.55)
    dets = [[Detection("keys", 0.55, box)] for _ in range(8)] + [[], []]
    frame_source = MockFrameSource(max_frames=10)
    detector = MockDetector(dets)

    result = scanner.execute_scan(frame_source, detector)

    assert result.success is True
    assert len(result.accepted_observations) == 0


def test_hard_scan_rejection_split_across_zones(scanner):
    # 8 frames total, but 4 frames in left zone, 4 frames in center zone -> neither zone reaches 6
    box_left = (0.10, 0.40, 0.20, 0.60)   # cx = 0.15 (zone_left)
    box_center = (0.40, 0.40, 0.50, 0.60) # cx = 0.45 (zone_center)

    dets = (
        [[Detection("keys", 0.85, box_left)] for _ in range(4)] +
        [[Detection("keys", 0.85, box_center)] for _ in range(4)] +
        [[], []]
    )
    frame_source = MockFrameSource(max_frames=10)
    detector = MockDetector(dets)

    result = scanner.execute_scan(frame_source, detector)

    assert result.success is True
    assert len(result.accepted_observations) == 0


def test_hard_scan_fails_on_camera_capture_failure(scanner):
    # Frame capture fails at frame 5
    frame_source = MockFrameSource(max_frames=10, fail_at=5)
    detector = MockDetector([])

    result = scanner.execute_scan(frame_source, detector)

    assert result.success is False
    assert "Capture failed" in result.error_message
    assert len(result.accepted_observations) == 0


def test_hard_scan_user_cancellation(scanner):
    # Cancel during scan via frame_source callback
    box = (0.40, 0.45, 0.50, 0.55)
    dets = [[Detection("keys", 0.85, box)] for _ in range(10)]
    
    class CancellingFrameSource:
        def __init__(self, scanner_to_cancel):
            self.scanner = scanner_to_cancel
            self.count = 0

        def capture_frame(self):
            self.count += 1
            if self.count == 3:
                # Cancel the scan while it's in progress
                self.scanner.cancel_active_scan()
            return f"frame_{self.count}"

    frame_source = CancellingFrameSource(scanner)
    detector = MockDetector(dets)

    result = scanner.execute_scan(frame_source, detector)

    assert result.success is False
    assert "cancelled" in result.error_message
    assert len(result.accepted_observations) == 0


def test_hard_scan_clear_history_invalidates_scan(scanner):
    # Clear history called
    scanner.clear_history_and_invalidate()

    box = (0.40, 0.45, 0.50, 0.55)
    dets = [[Detection("keys", 0.85, box)] for _ in range(10)]
    frame_source = MockFrameSource(max_frames=10)
    detector = MockDetector(dets)

    result = scanner.execute_scan(frame_source, detector)
    assert result.success is True
    assert len(result.accepted_observations) == 1


def test_hard_scan_concurrent_scan_rejected(scanner):
    # Simulate an active scan lock
    with scanner._lock:
        scanner._active_scan_id = "existing_scan"

    frame_source = MockFrameSource(max_frames=10)
    detector = MockDetector([])
    result = scanner.execute_scan(frame_source, detector)

    assert result.success is False
    assert "currently in progress" in result.error_message

