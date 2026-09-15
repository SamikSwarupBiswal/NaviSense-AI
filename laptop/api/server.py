"""FastAPI HTTP REST Service for NaviSense Laptop Object Memory and Hard Scan.

Authoritative implementation of PRD v3.2 Section 12, Section 13.3 & Section 23.
Exposes:
- GET  /api/v1/health          (and alias GET  /health)
- GET  /api/v1/objects/locate  (and alias GET  /locate)
- POST /api/v1/memory/clear    (and alias POST /clear)
- POST /api/v1/scan            (and alias POST /scan)
"""

from __future__ import annotations

import logging
import os
from contextlib import asynccontextmanager
from typing import Any, Dict, List, Optional

from fastapi import Depends, FastAPI, HTTPException, Query, Request, Security, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from laptop.api.schemas import (
    Candidate,
    HealthResponse,
    LocateResponse,
    LocateStatus,
    ServiceStatus,
)
from laptop.config.settings import LaptopConfig, SpatialZone
from laptop.scanner import HardScanEngine
from laptop.storage.db import (
    DatabaseManager,
    ObservationRecord,
    format_utc_iso_ms,
)

logger = logging.getLogger("navisense.laptop.api")

security = HTTPBearer(auto_error=False)


def check_camera_available(camera_index: int = 0) -> bool:
    """Check if the stationary camera is connected and queryable."""
    try:
        import cv2
        cap = cv2.VideoCapture(camera_index)
        if cap is None or not cap.isOpened():
            return False
        ret, _ = cap.read()
        cap.release()
        return bool(ret)
    except Exception as e:
        logger.warning("Camera availability probe failed: %s", e)
        return False


def verify_bearer_token(
    credentials: Optional[HTTPAuthorizationCredentials] = Security(security),
    required_token: Optional[str] = None,
) -> bool:
    """Verify Bearer authentication per PRD §12 and §23."""
    # Determine token from explicit parameter or environment
    expected_token = required_token or os.environ.get("NAVISENSE_API_TOKEN") or os.environ.get("LAPTOP_API_TOKEN")
    if not expected_token:
        # No token configured; open access on isolated local network
        return True

    if credentials is None or not credentials.credentials:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing Bearer authentication token",
            headers={"WWW-Authenticate": "Bearer"},
        )

    if credentials.credentials != expected_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid Bearer authentication token",
            headers={"WWW-Authenticate": "Bearer"},
        )

    return True


def create_app(
    config: Optional[LaptopConfig] = None,
    db_manager: Optional[DatabaseManager] = None,
    scanner: Optional[HardScanner] = None,
    camera_probe_fn: Optional[Any] = None,
    required_token: Optional[str] = None,
) -> FastAPI:
    """Factory creating and configuring the NaviSense FastAPI application."""
    cfg = config or LaptopConfig()
    db = db_manager or DatabaseManager()
    probe_camera = camera_probe_fn if camera_probe_fn is not None else check_camera_available

    app = FastAPI(
        title="NaviSense Laptop Locate Service",
        description="Stationary Tabletop Object Memory and Hard Scan Service (PRD v3.2)",
        version="1.0.0",
    )

    # Enable CORS for local Android development/testing
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # Dependency for authenticated routes
    async def auth_dependency(credentials: Optional[HTTPAuthorizationCredentials] = Security(security)):
        verify_bearer_token(credentials, required_token)

    @app.get(
        "/api/v1/health",
        response_model=HealthResponse,
        tags=["System"],
        summary="Service Health Check",
    )
    @app.get(
        "/health",
        response_model=HealthResponse,
        include_in_schema=False,
    )
    async def get_health() -> HealthResponse:
        """Query service status, camera connection, active profile, and stored observations count."""
        try:
            records_count = db.get_total_records_count()
        except Exception as e:
            logger.error("Failed to query records count from DB: %s", e)
            return HealthResponse(
                service_status=ServiceStatus.ERROR,
                camera_connected=False,
                memory_records_count=0,
                active_profile_id=cfg.camera_profile_id,
            )

        cam_ok = probe_camera(cfg.camera_device_index)
        svc_status = ServiceStatus.OK if cam_ok else ServiceStatus.DEGRADED

        return HealthResponse(
            service_status=svc_status,
            camera_connected=cam_ok,
            memory_records_count=records_count,
            active_profile_id=cfg.camera_profile_id,
        )

    @app.get(
        "/api/v1/objects/locate",
        response_model=LocateResponse,
        tags=["Locate"],
        summary="Query Object Memory Location",
        dependencies=[Depends(auth_dependency)],
    )
    @app.get(
        "/locate",
        response_model=LocateResponse,
        include_in_schema=False,
        dependencies=[Depends(auth_dependency)],
    )
    async def locate_object(
        name: Optional[str] = Query(None, description="Target object query string (e.g. 'keys', 'wallet')"),
        query: Optional[str] = Query(None, description="Alternative parameter alias for query"),
    ) -> LocateResponse:
        """Look up the most recent accepted observation of a target object."""
        raw_query = name or query
        if raw_query is None:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Missing required query parameter: 'name' or 'query'",
            )

        target_str = raw_query.strip()
        if not target_str or len(target_str) > 64:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Query parameter must be between 1 and 64 characters",
            )

        # Normalize alias to canonical class
        canonical_class = cfg.class_aliases.get(target_str.lower())
        if not canonical_class:
            # PRD §11.3: Unsupported names return status: unsupported
            return LocateResponse(
                status=LocateStatus.UNSUPPORTED,
                query_name=target_str,
                canonical_name=None,
                target_class=None,
                candidates=[],
                scan_id=None,
                observation_time_iso=None,
                age_seconds=None,
                camera_profile_id=None,
            )

        # Query authoritative SQLite database
        try:
            # Active camera profile version is assumed 1 unless configured
            query_status, raw_candidates = db.query_latest_scan(
                object_name=canonical_class,
                active_camera_profile_version=1,
            )
        except Exception as e:
            logger.error("Database query failure during locate: %s", e)
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Database query failure",
            )

        if query_status == "not_found":
            return LocateResponse(
                status=LocateStatus.NOT_FOUND,
                query_name=target_str,
                canonical_name=canonical_class,
                target_class=canonical_class,
                candidates=[],
                scan_id=None,
                observation_time_iso=None,
                age_seconds=None,
                camera_profile_id=None,
            )

        if query_status == "historical_only" or not raw_candidates:
            return LocateResponse(
                status=LocateStatus.HISTORICAL_ONLY,
                query_name=target_str,
                canonical_name=canonical_class,
                target_class=canonical_class,
                candidates=[],
                scan_id=None,
                observation_time_iso=None,
                age_seconds=None,
                camera_profile_id=None,
            )

        # Build candidate models
        first_c = raw_candidates[0]
        age = db.compute_age_seconds(first_c.last_seen)
        final_status = LocateStatus.FOUND if len(raw_candidates) == 1 else LocateStatus.AMBIGUOUS

        # PRD §11.3: Age > 60 seconds is stale
        if age > cfg.stale_threshold_seconds:
            final_status = LocateStatus.STALE

        candidates: List[Candidate] = []
        for idx, rc in enumerate(raw_candidates):
            z_name: Optional[str] = None
            for z in cfg.zones:
                if z.zone_id == rc.zone:
                    z_name = z.name
                    break

            # Box is [x1, y1, x2, y2] centered at relative_x, relative_y
            half_w = 0.08
            half_h = 0.08
            box = [
                max(0.0, min(1.0, rc.relative_x - half_w)),
                max(0.0, min(1.0, rc.relative_y - half_h)),
                max(0.0, min(1.0, rc.relative_x + half_w)),
                max(0.0, min(1.0, rc.relative_y + half_h)),
            ]

            candidates.append(
                Candidate(
                    instance_id=f"inst_{idx + 1:03d}",
                    zone_id=rc.zone,
                    zone_name=z_name,
                    box=box,
                    confidence=rc.confidence,
                )
            )

        return LocateResponse(
            status=final_status,
            query_name=target_str,
            canonical_name=canonical_class,
            target_class=canonical_class,
            candidates=candidates,
            scan_id=first_c.scan_id,
            observation_time_iso=first_c.last_seen,
            age_seconds=float(age) if age >= 0 else 0.0,
            camera_profile_id=cfg.camera_profile_id,
        )

    @app.post(
        "/api/v1/memory/clear",
        tags=["Management"],
        summary="Clear Object Memory History",
        dependencies=[Depends(auth_dependency)],
    )
    @app.post(
        "/clear",
        include_in_schema=False,
        dependencies=[Depends(auth_dependency)],
    )
    async def clear_memory() -> Dict[str, Any]:
        """Atomically clear all stored observations per PRD §11.1 & §11.2."""
        try:
            # If scanner is active, invalidate in-flight scan
            if scanner is not None and scanner.is_scanning:
                scanner.cancel_scan()

            success = db.clear_history()
            return {"status": "cleared", "success": success}
        except Exception as e:
            logger.error("Failed to clear memory: %s", e)
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Failed to clear memory",
            )

    @app.post(
        "/api/v1/scan",
        tags=["Hard Scan"],
        summary="Trigger a Stationary Hard Scan",
        dependencies=[Depends(auth_dependency)],
    )
    @app.post(
        "/scan",
        include_in_schema=False,
        dependencies=[Depends(auth_dependency)],
    )
    async def trigger_scan() -> Dict[str, Any]:
        """Trigger a 2.0s Hard Scan on the stationary laptop camera."""
        if scanner is None:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="HardScanner engine is not configured on this server instance",
            )
        try:
            res = scanner.execute_scan()
            return {
                "scan_id": res.scan_id,
                "status": res.status.value,
                "accepted_count": res.accepted_count,
                "duration_seconds": res.duration_seconds,
                "error_message": res.error_message,
            }
        except Exception as e:
            logger.error("Hard scan failed: %s", e)
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Hard scan execution error: {e}",
            )

    return app


# Default app instance for uvicorn runner
app = create_app()
