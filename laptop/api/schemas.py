"""FastAPI / Pydantic schemas for NaviSense v1 REST API (PRD Section 23)."""
from enum import Enum
from typing import List, Optional
from pydantic import BaseModel, Field, field_validator


class LocateStatus(str, Enum):
    """Possible outcomes for an object location query."""
    FOUND = "found"
    AMBIGUOUS = "ambiguous"
    STALE = "stale"
    HISTORICAL_ONLY = "historical_only"
    NOT_FOUND = "not_found"
    UNSUPPORTED = "unsupported"


class ServiceStatus(str, Enum):
    """Health check status."""
    OK = "ok"
    DEGRADED = "degraded"
    ERROR = "error"


class Candidate(BaseModel):
    """A detected instance assigned to a spatial zone."""
    instance_id: str = Field(..., description="Unique instance identifier within the scan")
    zone_id: str = Field(..., description="Semantic coarse zone ID (e.g., zone_left)")
    zone_name: Optional[str] = Field(None, description="Human-readable zone label (e.g., Left Table)")
    box: List[float] = Field(..., description="Normalized bounding box [x1, y1, x2, y2] in range [0.0, 1.0]")
    confidence: float = Field(..., ge=0.0, le=1.0, description="Mean detection confidence across scan frames")

    @field_validator("box")
    @classmethod
    def validate_box(cls, v: List[float]) -> List[float]:
        if len(v) != 4:
            raise ValueError("Bounding box must contain exactly 4 coordinates [x1, y1, x2, y2]")
        for coord in v:
            if not (0.0 <= coord <= 1.0):
                raise ValueError(f"Coordinate {coord} out of normalized bounds [0.0, 1.0]")
        if v[0] >= v[2] or v[1] >= v[3]:
            raise ValueError("Invalid box coordinates: x1 must be < x2 and y1 must be < y2")
        return v


class LocateResponse(BaseModel):
    """Response payload for GET /api/v1/objects/locate."""
    status: LocateStatus = Field(..., description="Query result classification")
    query_name: str = Field(..., description="Raw query name submitted by client")
    canonical_name: Optional[str] = Field(None, description="Canonical class name after alias resolution")
    target_class: Optional[str] = Field(None, description="Locate YOLO class identifier")
    candidates: List[Candidate] = Field(default_factory=list, description="Matching object candidates")
    scan_id: Optional[str] = Field(None, description="UUID of the scan snapshot where object was observed")
    observation_time_iso: Optional[str] = Field(None, description="ISO 8601 timestamp of observation")
    age_seconds: Optional[float] = Field(None, ge=0.0, description="Elapsed seconds since observation")
    camera_profile_id: Optional[str] = Field(None, description="Camera profile ID during observation")


class HealthResponse(BaseModel):
    """Response payload for GET /api/v1/health."""
    service_status: ServiceStatus = Field(..., description="Overall service status")
    camera_connected: bool = Field(..., description="Whether the stationary webcam is connected and accessible")
    memory_records_count: int = Field(..., ge=0, description="Total number of stored object observations")
    active_profile_id: str = Field(..., description="Identifier of the active camera/zone calibration profile")
