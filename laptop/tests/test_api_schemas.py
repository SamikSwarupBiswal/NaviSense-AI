"""Tests for API schemas and PRD v1 response fixtures."""
import json
from pathlib import Path
import pytest
from pydantic import ValidationError

from laptop.api.schemas import (
    Candidate,
    HealthResponse,
    LocateResponse,
    LocateStatus,
    ServiceStatus,
)


@pytest.fixture
def fixtures_data():
    fixture_path = Path(__file__).parent / "fixtures" / "responses.json"
    with open(fixture_path, "r", encoding="utf-8") as f:
        return json.load(f)


def test_fixture_found_valid(fixtures_data):
    data = fixtures_data["found"]
    resp = LocateResponse.model_validate(data)
    assert resp.status == LocateStatus.FOUND
    assert resp.query_name == "keys"
    assert resp.canonical_name == "keys"
    assert len(resp.candidates) == 1
    assert resp.candidates[0].zone_id == "zone_center"
    assert resp.candidates[0].confidence == 0.88
    assert resp.age_seconds <= 60.0


def test_fixture_ambiguous_valid(fixtures_data):
    data = fixtures_data["ambiguous"]
    resp = LocateResponse.model_validate(data)
    assert resp.status == LocateStatus.AMBIGUOUS
    assert len(resp.candidates) == 2
    assert resp.candidates[0].zone_id != resp.candidates[1].zone_id


def test_fixture_stale_valid(fixtures_data):
    data = fixtures_data["stale"]
    resp = LocateResponse.model_validate(data)
    assert resp.status == LocateStatus.STALE
    assert resp.age_seconds > 60.0
    assert len(resp.candidates) >= 1


def test_fixture_historical_only_valid(fixtures_data):
    data = fixtures_data["historical_only"]
    resp = LocateResponse.model_validate(data)
    assert resp.status == LocateStatus.HISTORICAL_ONLY
    assert resp.camera_profile_id == "tabletop_cam_v0"


def test_fixture_not_found_valid(fixtures_data):
    data = fixtures_data["not_found"]
    resp = LocateResponse.model_validate(data)
    assert resp.status == LocateStatus.NOT_FOUND
    assert len(resp.candidates) == 0


def test_fixture_unsupported_valid(fixtures_data):
    data = fixtures_data["unsupported"]
    resp = LocateResponse.model_validate(data)
    assert resp.status == LocateStatus.UNSUPPORTED
    assert resp.canonical_name is None
    assert len(resp.candidates) == 0


def test_fixture_health_responses(fixtures_data):
    ok_data = fixtures_data["health_ok"]
    ok_resp = HealthResponse.model_validate(ok_data)
    assert ok_resp.service_status == ServiceStatus.OK
    assert ok_resp.camera_connected is True

    deg_data = fixtures_data["health_degraded"]
    deg_resp = HealthResponse.model_validate(deg_data)
    assert deg_resp.service_status == ServiceStatus.DEGRADED
    assert deg_resp.camera_connected is False


def test_candidate_box_validation():
    # Valid box
    c = Candidate(
        instance_id="inst_1",
        zone_id="zone_center",
        box=[0.1, 0.2, 0.5, 0.6],
        confidence=0.9,
    )
    assert c.box == [0.1, 0.2, 0.5, 0.6]

    # Out of bounds (> 1.0)
    with pytest.raises(ValidationError):
        Candidate(
            instance_id="inst_1",
            zone_id="zone_center",
            box=[0.1, 0.2, 1.5, 0.6],
            confidence=0.9,
        )

    # Inverted coordinates (x1 >= x2)
    with pytest.raises(ValidationError):
        Candidate(
            instance_id="inst_1",
            zone_id="zone_center",
            box=[0.5, 0.2, 0.1, 0.6],
            confidence=0.9,
        )

    # Wrong length
    with pytest.raises(ValidationError):
        Candidate(
            instance_id="inst_1",
            zone_id="zone_center",
            box=[0.1, 0.2, 0.5],
            confidence=0.9,
        )
