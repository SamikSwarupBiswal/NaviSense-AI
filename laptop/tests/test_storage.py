"""Unit tests for SQLite persistence engine (laptop/storage/db.py)."""

import pytest
from datetime import datetime, timezone, timedelta
from laptop.storage.db import (
    DatabaseManager,
    ObservationRecord,
    format_utc_iso_ms,
    parse_utc_iso_ms,
    is_valid_utc_iso_ms,
)


@pytest.fixture
def db(tmp_path):
    """Provide a fresh SQLite DatabaseManager for each test."""
    db_file = tmp_path / "test_memory.db"
    return DatabaseManager(str(db_file))


def test_utc_iso_ms_formatting_and_parsing():
    now = datetime(2026, 9, 14, 10, 31, 42, 123456, tzinfo=timezone.utc)
    formatted = format_utc_iso_ms(now)
    assert formatted == "2026-09-14T10:31:42.123Z"
    assert is_valid_utc_iso_ms(formatted)

    parsed = parse_utc_iso_ms(formatted)
    assert parsed.year == 2026
    assert parsed.month == 9
    assert parsed.day == 14
    assert parsed.hour == 10
    assert parsed.minute == 31
    assert parsed.second == 42
    assert parsed.microsecond == 123000

    assert not is_valid_utc_iso_ms("invalid-timestamp")
    assert not is_valid_utc_iso_ms("2026-09-14T10:31:42Z")  # Missing milliseconds
    assert not is_valid_utc_iso_ms("2026-09-14T10:31:42.123+00:00")  # Must end in Z


def test_observation_record_validation():
    t1 = "2026-09-14T10:31:40.000Z"
    t2 = "2026-09-14T10:31:42.000Z"

    valid = ObservationRecord(
        scan_id="scan_01",
        object_name="keys",
        camera_id="laptop_webcam",
        camera_profile_version=1,
        area="hackathon_hall",
        zone="zone_center",
        instance_in_scan=1,
        relative_x=0.5,
        relative_y=0.5,
        confidence=0.85,
        seen_frames=8,
        sampled_frames=10,
        first_seen=t1,
        last_seen=t2,
    )
    valid.validate()

    # Invalid relative_x
    with pytest.raises(ValueError, match="relative_x"):
        bad = ObservationRecord(**{**valid.__dict__, "relative_x": 1.5})
        bad.validate()

    # Invalid seen_frames (< 6)
    with pytest.raises(ValueError, match="seen_frames"):
        bad = ObservationRecord(**{**valid.__dict__, "seen_frames": 5})
        bad.validate()

    # Invalid time order (first_seen > last_seen)
    with pytest.raises(ValueError, match="first_seen"):
        bad = ObservationRecord(**{**valid.__dict__, "first_seen": t2, "last_seen": t1})
        bad.validate()


def test_save_and_query_single_candidate(db):
    t1 = "2026-09-14T10:31:40.000Z"
    t2 = "2026-09-14T10:31:42.000Z"

    record = ObservationRecord(
        scan_id="scan_101",
        object_name="keys",
        camera_id="laptop_cam",
        camera_profile_version=1,
        area="hackathon_hall",
        zone="zone_center",
        instance_in_scan=1,
        relative_x=0.45,
        relative_y=0.60,
        confidence=0.91,
        seen_frames=9,
        sampled_frames=10,
        first_seen=t1,
        last_seen=t2,
    )

    saved = db.save_scan_snapshot([record])
    assert saved is True
    assert record.id is not None

    status, candidates = db.query_latest_scan(
        object_name="keys",
        active_camera_profile_version=1,
        current_iso_time="2026-09-14T10:31:50.000Z",
    )
    assert status == "found"
    assert len(candidates) == 1
    assert candidates[0].object_name == "keys"
    assert candidates[0].zone == "zone_center"
    assert candidates[0].relative_x == 0.45


def test_query_not_found(db):
    status, candidates = db.query_latest_scan("wallet", active_camera_profile_version=1)
    assert status == "not_found"
    assert candidates == []


def test_query_ambiguous_multiple_candidates(db):
    t_seen = "2026-09-14T10:31:42.000Z"
    r1 = ObservationRecord(
        scan_id="scan_202",
        object_name="keys",
        camera_id="laptop_cam",
        camera_profile_version=1,
        area="hackathon_hall",
        zone="zone_left",
        instance_in_scan=1,
        relative_x=0.20,
        relative_y=0.50,
        confidence=0.88,
        seen_frames=7,
        sampled_frames=10,
        first_seen=t_seen,
        last_seen=t_seen,
    )
    r2 = ObservationRecord(
        scan_id="scan_202",
        object_name="keys",
        camera_id="laptop_cam",
        camera_profile_version=1,
        area="hackathon_hall",
        zone="zone_right",
        instance_in_scan=2,
        relative_x=0.80,
        relative_y=0.50,
        confidence=0.95,
        seen_frames=9,
        sampled_frames=10,
        first_seen=t_seen,
        last_seen=t_seen,
    )

    db.save_scan_snapshot([r1, r2])

    status, candidates = db.query_latest_scan(
        object_name="keys",
        active_camera_profile_version=1,
        current_iso_time="2026-09-14T10:31:50.000Z",
    )
    assert status == "ambiguous"
    assert len(candidates) == 2
    # Check sorting: highest confidence first (since last_seen is equal)
    assert candidates[0].confidence == 0.95
    assert candidates[1].confidence == 0.88


def test_query_latest_scan_never_mixes_scans(db):
    # Old scan
    t_old = "2026-09-14T10:00:00.000Z"
    r_old = ObservationRecord(
        scan_id="scan_old",
        object_name="wallet",
        camera_id="laptop_cam",
        camera_profile_version=1,
        area="hackathon_hall",
        zone="zone_left",
        instance_in_scan=1,
        relative_x=0.20,
        relative_y=0.50,
        confidence=0.85,
        seen_frames=6,
        sampled_frames=10,
        first_seen=t_old,
        last_seen=t_old,
    )
    db.save_scan_snapshot([r_old])

    # Newer scan
    t_new = "2026-09-14T10:15:00.000Z"
    r_new = ObservationRecord(
        scan_id="scan_new",
        object_name="wallet",
        camera_id="laptop_cam",
        camera_profile_version=1,
        area="hackathon_hall",
        zone="zone_center",
        instance_in_scan=1,
        relative_x=0.50,
        relative_y=0.50,
        confidence=0.92,
        seen_frames=10,
        sampled_frames=10,
        first_seen=t_new,
        last_seen=t_new,
    )
    db.save_scan_snapshot([r_new])

    status, candidates = db.query_latest_scan(
        object_name="wallet",
        active_camera_profile_version=1,
        current_iso_time="2026-09-14T10:20:00.000Z",
    )
    assert status == "found"
    assert len(candidates) == 1
    assert candidates[0].scan_id == "scan_new"
    assert candidates[0].zone == "zone_center"


def test_query_historical_only_on_profile_version_change(db):
    t_seen = "2026-09-14T10:31:42.000Z"
    r_v1 = ObservationRecord(
        scan_id="scan_v1",
        object_name="keys",
        camera_id="laptop_cam",
        camera_profile_version=1,
        area="hackathon_hall",
        zone="zone_left",
        instance_in_scan=1,
        relative_x=0.20,
        relative_y=0.50,
        confidence=0.88,
        seen_frames=7,
        sampled_frames=10,
        first_seen=t_seen,
        last_seen=t_seen,
    )
    db.save_scan_snapshot([r_v1])

    # Query with active profile version 2 (e.g. camera moved or recalibrated)
    status, candidates = db.query_latest_scan(
        object_name="keys",
        active_camera_profile_version=2,
        current_iso_time="2026-09-14T10:31:50.000Z",
    )
    assert status == "historical_only"
    assert candidates == []


def test_atomic_transaction_rollback_on_failure(db):
    t_seen = "2026-09-14T10:31:42.000Z"
    good = ObservationRecord(
        scan_id="scan_atomic",
        object_name="keys",
        camera_id="laptop_cam",
        camera_profile_version=1,
        area="hackathon_hall",
        zone="zone_left",
        instance_in_scan=1,
        relative_x=0.20,
        relative_y=0.50,
        confidence=0.88,
        seen_frames=7,
        sampled_frames=10,
        first_seen=t_seen,
        last_seen=t_seen,
    )
    bad = ObservationRecord(
        scan_id="scan_atomic",
        object_name="keys",
        camera_id="laptop_cam",
        camera_profile_version=1,
        area="hackathon_hall",
        zone="zone_right",
        instance_in_scan=1,  # Duplicate instance_in_scan -> violates UNIQUE(scan_id, object_name, instance_in_scan)
        relative_x=0.80,
        relative_y=0.50,
        confidence=0.88,
        seen_frames=7,
        sampled_frames=10,
        first_seen=t_seen,
        last_seen=t_seen,
    )

    with pytest.raises(Exception):
        db.save_scan_snapshot([good, bad])

    # Verify that 'good' was NOT committed
    status, candidates = db.query_latest_scan("keys", active_camera_profile_version=1)
    assert status == "not_found"
    assert candidates == []


def test_clear_history(db):
    t_seen = "2026-09-14T10:31:42.000Z"
    rec = ObservationRecord(
        scan_id="scan_clear",
        object_name="keys",
        camera_id="laptop_cam",
        camera_profile_version=1,
        area="hackathon_hall",
        zone="zone_center",
        instance_in_scan=1,
        relative_x=0.5,
        relative_y=0.5,
        confidence=0.90,
        seen_frames=8,
        sampled_frames=10,
        first_seen=t_seen,
        last_seen=t_seen,
    )
    db.save_scan_snapshot([rec])

    status, candidates = db.query_latest_scan("keys", active_camera_profile_version=1)
    assert status == "found"

    db.clear_history()

    status, candidates = db.query_latest_scan("keys", active_camera_profile_version=1)
    assert status == "not_found"


def test_compute_age_seconds(db):
    t_last = "2026-09-14T10:00:00.000Z"
    t_exact_60s = "2026-09-14T10:01:00.000Z"
    t_60_1s = "2026-09-14T10:01:00.100Z"
    t_future = "2026-09-14T09:59:59.000Z"

    assert db.compute_age_seconds(t_last, t_exact_60s) == 60
    # Ceil of 60.1s is 61s per PRD §11.3
    assert db.compute_age_seconds(t_last, t_60_1s) == 61
    # Future-dated candidate returns -1
    assert db.compute_age_seconds(t_last, t_future) == -1
