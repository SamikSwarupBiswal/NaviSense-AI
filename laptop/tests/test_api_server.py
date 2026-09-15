"""Comprehensive tests for FastAPI Locate & Health server (PRD Sections 11, 12, 13.3 & 23)."""

from datetime import datetime, timezone
import pytest
from fastapi.testclient import TestClient

from laptop.api.schemas import LocateStatus, ServiceStatus
from laptop.api.server import create_app
from laptop.config.settings import LaptopConfig, SpatialZone
from laptop.storage.db import (
    DatabaseManager,
    ObservationRecord,
    format_utc_iso_ms,
)


@pytest.fixture
def mock_db(tmp_path):
    db_file = tmp_path / "test_api_memory.db"
    return DatabaseManager(str(db_file))


@pytest.fixture
def test_config():
    return LaptopConfig(
        camera_profile_id="tabletop_cam_v1",
        stale_threshold_seconds=60.0,
        zones=[
            SpatialZone("zone_left", "Left Table", (0.0, 0.0, 0.33, 1.0)),
            SpatialZone("zone_center", "Center Table", (0.33, 0.0, 0.66, 1.0)),
            SpatialZone("zone_right", "Right Table", (0.66, 0.0, 1.0, 1.0)),
        ],
    )


@pytest.fixture
def client(mock_db, test_config):
    app = create_app(
        config=test_config,
        db_manager=mock_db,
        camera_probe_fn=lambda idx: True,
    )
    return TestClient(app)


def test_health_endpoint_ok(client, mock_db):
    resp = client.get("/api/v1/health")
    assert resp.status_code == 200
    data = resp.json()
    assert data["service_status"] == "ok"
    assert data["camera_connected"] is True
    assert data["memory_records_count"] == 0
    assert data["active_profile_id"] == "tabletop_cam_v1"

    # Alias /health works identically
    alias_resp = client.get("/health")
    assert alias_resp.status_code == 200
    assert alias_resp.json() == data


def test_health_endpoint_degraded_when_camera_disconnected(mock_db, test_config):
    app = create_app(
        config=test_config,
        db_manager=mock_db,
        camera_probe_fn=lambda idx: False,
    )
    c = TestClient(app)
    resp = c.get("/api/v1/health")
    assert resp.status_code == 200
    data = resp.json()
    assert data["service_status"] == "degraded"
    assert data["camera_connected"] is False


def test_locate_missing_or_invalid_query_parameter(client):
    # Missing query param
    resp = client.get("/api/v1/objects/locate")
    assert resp.status_code == 400

    # Empty query param
    resp = client.get("/api/v1/objects/locate?name=  ")
    assert resp.status_code == 400

    # Query > 64 chars per PRD §13.3
    oversized = "a" * 65
    resp = client.get(f"/api/v1/objects/locate?name={oversized}")
    assert resp.status_code == 400


def test_locate_unsupported_object_name(client):
    # Class not in class_aliases
    resp = client.get("/api/v1/objects/locate?name=backpack")
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "unsupported"
    assert data["query_name"] == "backpack"
    assert data["canonical_name"] is None
    assert len(data["candidates"]) == 0


def test_locate_not_found(client):
    # Supported class but zero DB records
    resp = client.get("/api/v1/objects/locate?name=keys")
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "not_found"
    assert data["query_name"] == "keys"
    assert data["canonical_name"] == "keys"
    assert len(data["candidates"]) == 0


def test_locate_single_found(client, mock_db):
    now_iso = format_utc_iso_ms()
    rec = ObservationRecord(
        scan_id="scan_test_001",
        object_name="keys",
        camera_id="cam_test",
        camera_profile_version=1,
        area="hackathon_hall",
        zone="zone_center",
        instance_in_scan=1,
        relative_x=0.50,
        relative_y=0.55,
        confidence=0.88,
        seen_frames=8,
        first_seen=now_iso,
        last_seen=now_iso,
    )
    mock_db.save_scan_snapshot([rec])

    # Query with alias 'car keys'
    resp = client.get("/api/v1/objects/locate?name=car%20keys")
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "found"
    assert data["query_name"] == "car keys"
    assert data["canonical_name"] == "keys"
    assert data["scan_id"] == "scan_test_001"
    assert len(data["candidates"]) == 1

    cand = data["candidates"][0]
    assert cand["zone_id"] == "zone_center"
    assert cand["zone_name"] == "Center Table"
    assert cand["confidence"] == 0.88
    assert len(cand["box"]) == 4
    # Box should be centered around relative_x, relative_y
    assert cand["box"][0] < cand["box"][2]
    assert cand["box"][1] < cand["box"][3]


def test_locate_ambiguous_multiple_candidates(client, mock_db):
    now_iso = format_utc_iso_ms()
    rec1 = ObservationRecord(
        scan_id="scan_test_multi",
        object_name="keys",
        camera_id="cam_test",
        camera_profile_version=1,
        area="hackathon_hall",
        zone="zone_left",
        instance_in_scan=1,
        relative_x=0.20,
        relative_y=0.50,
        confidence=0.85,
        seen_frames=8,
        first_seen=now_iso,
        last_seen=now_iso,
    )
    rec2 = ObservationRecord(
        scan_id="scan_test_multi",
        object_name="keys",
        camera_id="cam_test",
        camera_profile_version=1,
        area="hackathon_hall",
        zone="zone_right",
        instance_in_scan=2,
        relative_x=0.80,
        relative_y=0.50,
        confidence=0.90,
        seen_frames=9,
        first_seen=now_iso,
        last_seen=now_iso,
    )
    mock_db.save_scan_snapshot([rec1, rec2])

    resp = client.get("/api/v1/objects/locate?name=keys")
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "ambiguous"
    assert len(data["candidates"]) == 2
    assert {c["zone_id"] for c in data["candidates"]} == {"zone_left", "zone_right"}


def test_locate_stale_observation(client, mock_db):
    # Observation timestamp from 10 minutes ago
    old_time = datetime(2026, 9, 14, 10, 0, 0, tzinfo=timezone.utc)
    old_iso = format_utc_iso_ms(old_time)

    rec = ObservationRecord(
        scan_id="scan_test_stale",
        object_name="wallet",
        camera_id="cam_test",
        camera_profile_version=1,
        area="hackathon_hall",
        zone="zone_right",
        instance_in_scan=1,
        relative_x=0.75,
        relative_y=0.50,
        confidence=0.82,
        seen_frames=7,
        first_seen=old_iso,
        last_seen=old_iso,
    )
    mock_db.save_scan_snapshot([rec])

    resp = client.get("/api/v1/objects/locate?name=wallet")
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "stale"
    assert data["age_seconds"] > 60.0
    assert len(data["candidates"]) == 1


def test_locate_historical_only_old_profile(client, mock_db):
    now_iso = format_utc_iso_ms()
    rec = ObservationRecord(
        scan_id="scan_test_v0",
        object_name="keys",
        camera_id="cam_test",
        camera_profile_version=2,  # Different from active version 1
        area="hackathon_hall",
        zone="zone_left",
        instance_in_scan=1,
        relative_x=0.20,
        relative_y=0.50,
        confidence=0.85,
        seen_frames=8,
        first_seen=now_iso,
        last_seen=now_iso,
    )
    mock_db.save_scan_snapshot([rec])

    resp = client.get("/api/v1/objects/locate?name=keys")
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "historical_only"
    assert len(data["candidates"]) == 0


def test_bearer_token_authentication(mock_db, test_config):
    token = "secret-token-12345"
    app = create_app(
        config=test_config,
        db_manager=mock_db,
        camera_probe_fn=lambda idx: True,
        required_token=token,
    )
    auth_client = TestClient(app)

    # Health check is public / unauthenticated
    health_resp = auth_client.get("/api/v1/health")
    assert health_resp.status_code == 200

    # Locate without token -> 401
    no_token_resp = auth_client.get("/api/v1/objects/locate?name=keys")
    assert no_token_resp.status_code == 401

    # Locate with wrong token -> 401
    bad_token_resp = auth_client.get(
        "/api/v1/objects/locate?name=keys",
        headers={"Authorization": "Bearer wrong-token"},
    )
    assert bad_token_resp.status_code == 401

    # Locate with valid token -> 200
    good_token_resp = auth_client.get(
        "/api/v1/objects/locate?name=keys",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert good_token_resp.status_code == 200


def test_clear_memory_endpoint(client, mock_db):
    now_iso = format_utc_iso_ms()
    rec = ObservationRecord(
        scan_id="scan_to_clear",
        object_name="keys",
        camera_id="cam_test",
        camera_profile_version=1,
        area="hackathon_hall",
        zone="zone_center",
        instance_in_scan=1,
        relative_x=0.50,
        relative_y=0.55,
        confidence=0.88,
        seen_frames=8,
        first_seen=now_iso,
        last_seen=now_iso,
    )
    mock_db.save_scan_snapshot([rec])
    assert mock_db.get_total_records_count() == 1

    resp = client.post("/api/v1/memory/clear")
    assert resp.status_code == 200
    assert resp.json()["status"] == "cleared"
    assert mock_db.get_total_records_count() == 0

    # Subsequent locate should report not_found
    loc_resp = client.get("/api/v1/objects/locate?name=keys")
    assert loc_resp.json()["status"] == "not_found"
