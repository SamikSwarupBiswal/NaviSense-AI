"""SQLite Persistence Layer for NaviSense Laptop Memory.

Authoritative implementation of PRD v3.2 Section 11 (Object Memory) and
Section 23 (Data Model).
"""

from __future__ import annotations

import math
import re
import sqlite3
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import List, Optional, Tuple

UTC_ISO_MS_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$")


def format_utc_iso_ms(dt: Optional[datetime] = None) -> str:
    """Format datetime as fixed millisecond precision UTC ISO-8601 ending in Z."""
    if dt is None:
        dt = datetime.now(timezone.utc)
    elif dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    else:
        dt = dt.astimezone(timezone.utc)
    return dt.strftime("%Y-%m-%dT%H:%M:%S.") + f"{int(dt.microsecond / 1000):03d}Z"


def is_valid_utc_iso_ms(iso_str: str) -> bool:
    """Check if string strictly matches YYYY-MM-DDTHH:MM:SS.sssZ format."""
    if not isinstance(iso_str, str):
        return False
    if not UTC_ISO_MS_PATTERN.match(iso_str):
        return False
    try:
        parse_utc_iso_ms(iso_str)
        return True
    except (ValueError, OverflowError):
        return False


def parse_utc_iso_ms(iso_str: str) -> datetime:
    """Parse fixed millisecond precision UTC ISO-8601 string to timezone-aware UTC datetime."""
    if not isinstance(iso_str, str) or not iso_str.endswith("Z"):
        raise ValueError(f"Timestamp must end with 'Z': {iso_str}")
    # Replace Z with +00:00 for fromisoformat
    clean_str = iso_str[:-1] + "+00:00"
    return datetime.fromisoformat(clean_str)


@dataclass
class ObservationRecord:
    """Represents a single accepted object detection instance stored in SQLite."""
    scan_id: str
    object_name: str
    camera_id: str
    camera_profile_version: int
    area: str
    zone: str
    instance_in_scan: int
    relative_x: float
    relative_y: float
    confidence: float
    seen_frames: int
    first_seen: str
    last_seen: str
    sampled_frames: int = 10
    id: Optional[int] = None

    def validate(self) -> None:
        """Validate all field constraints per PRD §11.1 before database operations."""
        if not self.scan_id or not isinstance(self.scan_id, str):
            raise ValueError("scan_id must be non-empty string")
        if not self.object_name or not isinstance(self.object_name, str):
            raise ValueError("object_name must be non-empty string")
        if not self.camera_id or not isinstance(self.camera_id, str):
            raise ValueError("camera_id must be non-empty string")
        if self.camera_profile_version < 1:
            raise ValueError("camera_profile_version must be >= 1")
        if not self.area or not isinstance(self.area, str):
            raise ValueError("area must be non-empty string")
        if not self.zone or not isinstance(self.zone, str):
            raise ValueError("zone must be non-empty string")
        if self.instance_in_scan < 1:
            raise ValueError("instance_in_scan must be >= 1")
        if not (0.0 <= self.relative_x <= 1.0):
            raise ValueError(f"relative_x {self.relative_x} not in [0, 1]")
        if not (0.0 <= self.relative_y <= 1.0):
            raise ValueError(f"relative_y {self.relative_y} not in [0, 1]")
        if not (0.0 <= self.confidence <= 1.0):
            raise ValueError(f"confidence {self.confidence} not in [0, 1]")
        if self.sampled_frames != 10:
            raise ValueError(f"sampled_frames must be exactly 10, got {self.sampled_frames}")
        if not (6 <= self.seen_frames <= self.sampled_frames):
            raise ValueError(f"seen_frames {self.seen_frames} must be between 6 and {self.sampled_frames}")
        if not is_valid_utc_iso_ms(self.first_seen):
            raise ValueError(f"first_seen has invalid UTC ISO format: {self.first_seen}")
        if not is_valid_utc_iso_ms(self.last_seen):
            raise ValueError(f"last_seen has invalid UTC ISO format: {self.last_seen}")
        if self.first_seen > self.last_seen:
            raise ValueError(f"first_seen ({self.first_seen}) > last_seen ({self.last_seen})")


SCHEMA_SQL = """
CREATE TABLE IF NOT EXISTS observations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scan_id TEXT NOT NULL,
    object_name TEXT NOT NULL,
    camera_id TEXT NOT NULL,
    camera_profile_version INTEGER NOT NULL CHECK(camera_profile_version >= 1),
    area TEXT NOT NULL,
    zone TEXT NOT NULL,
    instance_in_scan INTEGER NOT NULL CHECK(instance_in_scan >= 1),
    relative_x REAL NOT NULL CHECK(relative_x BETWEEN 0 AND 1),
    relative_y REAL NOT NULL CHECK(relative_y BETWEEN 0 AND 1),
    confidence REAL NOT NULL CHECK(confidence BETWEEN 0 AND 1),
    seen_frames INTEGER NOT NULL CHECK(seen_frames BETWEEN 6 AND sampled_frames),
    sampled_frames INTEGER NOT NULL CHECK(sampled_frames = 10),
    first_seen TEXT NOT NULL,
    last_seen TEXT NOT NULL CHECK(first_seen <= last_seen),
    UNIQUE(scan_id, object_name, instance_in_scan)
);

CREATE INDEX IF NOT EXISTS idx_obs_lookup ON observations(object_name, camera_profile_version, last_seen);
CREATE INDEX IF NOT EXISTS idx_obs_scan ON observations(scan_id);
"""


class DatabaseManager:
    """Authoritative manager for SQLite-backed NaviSense object memory."""

    def __init__(self, db_path: str = "laptop_memory.db"):
        self.db_path = str(db_path)
        self.init_db()

    def _get_connection(self) -> sqlite3.Connection:
        """Create a sqlite3 connection with strict constraints and row factory."""
        conn = sqlite3.connect(self.db_path, timeout=5.0)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys = ON;")
        return conn

    def init_db(self) -> None:
        """Initialize database tables and indices per PRD §11.1."""
        if self.db_path != ":memory:":
            Path(self.db_path).parent.mkdir(parents=True, exist_ok=True)
        with self._get_connection() as conn:
            conn.executescript(SCHEMA_SQL)

    def save_scan_snapshot(
        self,
        observations: List[ObservationRecord],
    ) -> bool:
        """Atomically persist accepted observations for a single Hard Scan in one transaction.
        
        Per PRD §11.1 & §11.2:
        - All records for the scan are committed together in one transaction.
        - If any constraint fails, the entire transaction rolls back and writes nothing.
        - Returns True on success, False if no records or on error.
        """
        if not observations:
            return False

        # Validate all records before attempting DB operation
        scan_id = observations[0].scan_id
        for obs in observations:
            obs.validate()
            if obs.scan_id != scan_id:
                raise ValueError(f"All observations in a snapshot must share scan_id '{scan_id}'")

        insert_sql = """
        INSERT INTO observations (
            scan_id, object_name, camera_id, camera_profile_version,
            area, zone, instance_in_scan, relative_x, relative_y,
            confidence, seen_frames, sampled_frames, first_seen, last_seen
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """

        conn = self._get_connection()
        try:
            with conn:
                # Explicit transactional write
                cursor = conn.cursor()
                for obs in observations:
                    cursor.execute(
                        insert_sql,
                        (
                            obs.scan_id,
                            obs.object_name,
                            obs.camera_id,
                            obs.camera_profile_version,
                            obs.area,
                            obs.zone,
                            obs.instance_in_scan,
                            obs.relative_x,
                            obs.relative_y,
                            obs.confidence,
                            obs.seen_frames,
                            obs.sampled_frames,
                            obs.first_seen,
                            obs.last_seen,
                        ),
                    )
                    obs.id = cursor.lastrowid
            return True
        except Exception:
            # Transaction auto-rolled back by `with conn:`
            raise
        finally:
            conn.close()

    def query_latest_scan(
        self,
        object_name: str,
        active_camera_profile_version: int = 1,
        current_iso_time: Optional[str] = None,
    ) -> Tuple[str, List[ObservationRecord]]:
        """Query object observations per deterministic rules in PRD §11.3.
        
        Returns:
            Tuple of (status, candidates):
            - status: 'found' (1 candidate), 'ambiguous' (>1 candidates),
                      'historical_only', or 'not_found'
            - candidates: List[ObservationRecord] sorted by last_seen DESC, confidence DESC, id DESC
        """
        if current_iso_time is None:
            current_iso_time = format_utc_iso_ms()

        conn = self._get_connection()
        try:
            cursor = conn.cursor()

            # Step 1: Check if this object exists at all in the database
            cursor.execute(
                "SELECT COUNT(*) FROM observations WHERE object_name = ?;",
                (object_name,),
            )
            total_count = cursor.fetchone()[0]
            if total_count == 0:
                return "not_found", []

            # Step 2: Check observations matching the active camera profile version
            cursor.execute(
                """
                SELECT scan_id, last_seen, id
                FROM observations
                WHERE object_name = ?
                  AND camera_profile_version = ?
                  AND last_seen <= ?
                ORDER BY last_seen DESC, id DESC;
                """,
                (object_name, active_camera_profile_version, current_iso_time),
            )
            matching_rows = cursor.fetchall()

            if not matching_rows:
                # Observations exist, but only from older/different camera profile versions
                # or future-dated records -> return historical_only per PRD §11.3
                return "historical_only", []

            # Step 3: Choose the latest accepted scan containing that class
            # by maximum last_seen, breaking ties by maximum id
            latest_scan_id = matching_rows[0]["scan_id"]

            # Step 4: Fetch ALL matching instances from that single scan
            cursor.execute(
                """
                SELECT id, scan_id, object_name, camera_id, camera_profile_version,
                       area, zone, instance_in_scan, relative_x, relative_y,
                       confidence, seen_frames, sampled_frames, first_seen, last_seen
                FROM observations
                WHERE scan_id = ?
                  AND object_name = ?
                  AND camera_profile_version = ?
                  AND last_seen <= ?
                ORDER BY last_seen DESC, confidence DESC, id DESC;
                """,
                (latest_scan_id, object_name, active_camera_profile_version, current_iso_time),
            )
            rows = cursor.fetchall()

            candidates = [
                ObservationRecord(
                    id=row["id"],
                    scan_id=row["scan_id"],
                    object_name=row["object_name"],
                    camera_id=row["camera_id"],
                    camera_profile_version=row["camera_profile_version"],
                    area=row["area"],
                    zone=row["zone"],
                    instance_in_scan=row["instance_in_scan"],
                    relative_x=row["relative_x"],
                    relative_y=row["relative_y"],
                    confidence=row["confidence"],
                    seen_frames=row["seen_frames"],
                    sampled_frames=row["sampled_frames"],
                    first_seen=row["first_seen"],
                    last_seen=row["last_seen"],
                )
                for row in rows
            ]

            if not candidates:
                return "historical_only", []
            elif len(candidates) == 1:
                return "found", candidates
            else:
                return "ambiguous", candidates

        finally:
            conn.close()

    def clear_history(self) -> bool:
        """Atomically delete all stored observations per PRD §11.1 & §11.2."""
        conn = self._get_connection()
        try:
            with conn:
                conn.execute("DELETE FROM observations;")
            return True
        finally:
            conn.close()

    def compute_age_seconds(self, last_seen_iso: str, server_time_iso: Optional[str] = None) -> int:
        """Compute age_seconds as integer ceiling of (server_time - last_seen) per PRD §11.3."""
        if server_time_iso is None:
            server_time_iso = format_utc_iso_ms()
        dt_last = parse_utc_iso_ms(last_seen_iso)
        dt_server = parse_utc_iso_ms(server_time_iso)
        delta_sec = (dt_server - dt_last).total_seconds()
        if delta_sec < 0.0:
            # Future-dated
            return -1
        return int(math.ceil(delta_sec))
