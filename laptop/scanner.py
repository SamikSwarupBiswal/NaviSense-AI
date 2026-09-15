"""Stationary Hard Scan Transactional Engine.

Authoritative implementation of PRD v3.2 Section 10 (Hard Scan) and
Section 11.2 (Observation Acceptance).
"""

from __future__ import annotations

import logging
import threading
import time
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Callable, Dict, List, Optional, Protocol, Tuple

from laptop.config.settings import LaptopConfig, SpatialZone
from laptop.storage.db import (
    DatabaseManager,
    ObservationRecord,
    format_utc_iso_ms,
)

logger = logging.getLogger("navisense.laptop.scanner")


@dataclass
class Detection:
    """A single object detection from a vision model."""
    class_name: str
    confidence: float
    # [x1, y1, x2, y2] in normalized coordinates [0.0, 1.0]
    box: Tuple[float, float, float, float]

    @property
    def center(self) -> Tuple[float, float]:
        x1, y1, x2, y2 = self.box
        return ((x1 + x2) / 2.0, (y1 + y2) / 2.0)


@dataclass
class CapturedFrame:
    """A single captured frame with timestamp and index."""
    frame_index: int
    timestamp_iso: str
    timestamp_monotonic: float
    image_data: Any


class IDetector(Protocol):
    """Protocol for stationary locate object detectors."""
    def detect(self, image_data: Any) -> List[Detection]:
        ...


class IFrameSource(Protocol):
    """Protocol for stationary frame capture sources."""
    def capture_frame(self) -> Optional[Any]:
        ...


class OpenCvFrameSource:
    """Captures real-time frames from a stationary webcam using OpenCV."""

    def __init__(self, camera_index: int = 0):
        self.camera_index = camera_index
        self._cap = None

    def _get_cap(self):
        import cv2
        if self._cap is None or not self._cap.isOpened():
            self._cap = cv2.VideoCapture(self.camera_index)
        return self._cap

    def capture_frame(self) -> Optional[Any]:
        try:
            import cv2
            cap = self._get_cap()
            if cap is None or not cap.isOpened():
                return None
            ret, frame = cap.read()
            if not ret or frame is None:
                return None
            return frame
        except Exception as e:
            logger.warning("OpenCV frame capture failed: %s", e)
            return None

    def release(self):
        if self._cap is not None:
            self._cap.release()
            self._cap = None


class YoloLocateDetector:
    """YOLO locate detector running inference on captured frames."""

    def __init__(
        self,
        model_path: str = "models/locate/locate_best.pt",
        conf_threshold: float = 0.50,
    ):
        from pathlib import Path
        self.model_path = Path(model_path)
        self.conf_threshold = conf_threshold
        self._model = None
        self._load_model()

    def _load_model(self):
        from pathlib import Path
        from ultralytics import YOLO

        target = self.model_path
        if not target.exists():
            fallback = Path("models/smoke/locate_smoke.pt")
            if fallback.exists():
                target = fallback

        if target.exists():
            logger.info("Loading YOLO Locate detector from %s", target)
            self._model = YOLO(str(target))
        else:
            logger.warning("Locate model weights not found at %s", target)

    def detect(self, image_data: Any) -> List[Detection]:
        if self._model is None or image_data is None:
            return []

        try:
            results = self._model.predict(
                source=image_data,
                conf=self.conf_threshold,
                verbose=False,
            )
            detections: List[Detection] = []
            if not results:
                return detections

            r = results[0]
            boxes = r.boxes
            if boxes is None or len(boxes) == 0:
                return detections

            orig_h, orig_w = r.orig_shape

            for box in boxes:
                cls_id = int(box.cls[0].item())
                conf = float(box.conf[0].item())
                cls_name = r.names.get(cls_id, str(cls_id)).lower()

                xyxy = box.xyxy[0].tolist()
                x1 = max(0.0, min(1.0, xyxy[0] / orig_w))
                y1 = max(0.0, min(1.0, xyxy[1] / orig_h))
                x2 = max(0.0, min(1.0, xyxy[2] / orig_w))
                y2 = max(0.0, min(1.0, xyxy[3] / orig_h))

                detections.append(
                    Detection(
                        class_name=cls_name,
                        confidence=conf,
                        box=(x1, y1, x2, y2),
                    )
                )
            return detections
        except Exception as e:
            logger.warning("YOLO Locate detection failed: %s", e)
            return []


def compute_iou(
    box_a: Tuple[float, float, float, float],
    box_b: Tuple[float, float, float, float],
) -> float:
    """Compute Intersection over Union (IoU) between two bounding boxes."""
    ax1, ay1, ax2, ay2 = box_a
    bx1, by1, bx2, by2 = box_b

    ix1 = max(ax1, bx1)
    iy1 = max(ay1, by1)
    ix2 = min(ax2, bx2)
    iy2 = min(ay2, by2)

    inter_w = max(0.0, ix2 - ix1)
    inter_h = max(0.0, iy2 - iy1)
    inter_area = inter_w * inter_h

    area_a = max(0.0, ax2 - ax1) * max(0.0, ay2 - ay1)
    area_b = max(0.0, bx2 - bx1) * max(0.0, by2 - by1)

    union_area = area_a + area_b - inter_area
    if union_area <= 0.0:
        return 0.0
    return inter_area / union_area


def match_zone(center: Tuple[float, float], zones: List[SpatialZone]) -> Optional[SpatialZone]:
    """Find the spatial zone containing the given center point (cx, cy)."""
    cx, cy = center
    for zone in zones:
        zx1, zy1, zx2, zy2 = zone.bounds
        if zx1 <= cx <= zx2 and zy1 <= cy <= zy2:
            return zone
    return None


@dataclass
class HardScanResult:
    """Result of a Hard Scan operation."""
    success: bool
    scan_id: str
    sampled_frames: int
    accepted_observations: List[ObservationRecord]
    error_message: Optional[str] = None
    duration_seconds: float = 0.0


class HardScanEngine:
    """Authoritative Hard Scan coordinator enforcing single-scan concurrency,
    10-frame sampling, IoU association, persistence gating, and atomic commit.
    """

    def __init__(
        self,
        db_manager: DatabaseManager,
        config: Optional[LaptopConfig] = None,
        frame_source: Optional[IFrameSource] = None,
        detector: Optional[IDetector] = None,
    ):
        self.db = db_manager
        self.config = config or LaptopConfig()
        self.default_frame_source = frame_source
        self.default_detector = detector
        self._lock = threading.Lock()
        self._active_scan_id: Optional[str] = None
        self._is_cancelled = False
        self._camera_profile_version = 1

    @property
    def is_scanning(self) -> bool:
        with self._lock:
            return self._active_scan_id is not None

    @property
    def active_camera_profile_version(self) -> int:
        with self._lock:
            return self._camera_profile_version

    def set_camera_profile_version(self, version: int) -> None:
        """Update active camera profile version; invalidates any active scan."""
        with self._lock:
            self._camera_profile_version = version
            if self._active_scan_id is not None:
                logger.warning(
                    "Profile version changed to %d during active scan %s; invalidating",
                    version,
                    self._active_scan_id,
                )
                self._is_cancelled = True

    def cancel_active_scan(self) -> bool:
        """Cancel current in-flight scan, preventing any database write."""
        with self._lock:
            if self._active_scan_id is not None:
                logger.info("Cancelling active scan %s", self._active_scan_id)
                self._is_cancelled = True
                return True
            return False

    def clear_history_and_invalidate(self) -> bool:
        """Serialize Clear history with commits: cancel in-flight scan and clear DB."""
        with self._lock:
            if self._active_scan_id is not None:
                logger.info("Clear history requested during scan %s; invalidating", self._active_scan_id)
                self._is_cancelled = True
        return self.db.clear_history()

    def execute_scan(
        self,
        frame_source: Optional[IFrameSource] = None,
        detector: Optional[IDetector] = None,
        custom_scan_id: Optional[str] = None,
        area: str = "hackathon_hall",
    ) -> HardScanResult:
        """Execute stationary Hard Scan session per PRD §10 & §11.2."""
        source = frame_source or self.default_frame_source
        det = detector or self.default_detector
        if source is None:
            raise ValueError("No frame source provided or configured for Hard Scan")
        if det is None:
            raise ValueError("No detector provided or configured for Hard Scan")

        scan_start_mono = time.monotonic()
        scan_id = custom_scan_id or f"scan_{uuid.uuid4().hex[:12]}"

        with self._lock:
            if self._active_scan_id is not None:
                return HardScanResult(
                    success=False,
                    scan_id=scan_id,
                    sampled_frames=0,
                    accepted_observations=[],
                    error_message=f"Another scan ({self._active_scan_id}) is currently in progress",
                    duration_seconds=0.0,
                )
            self._active_scan_id = scan_id
            self._is_cancelled = False
            pinned_profile_version = self._camera_profile_version

        try:
            # -------------------------------------------------------------
            # Stage 1: Sample exactly 10 distinct frames at scheduled offsets
            # (0, 200, ..., 1800 ms within 2.0 seconds)
            # -------------------------------------------------------------
            target_frame_count = self.config.scan_frames_count  # 10
            slot_interval = self.config.scan_duration_seconds / target_frame_count  # 0.2s = 200ms
            captured_frames: List[CapturedFrame] = []

            for frame_idx in range(target_frame_count):
                if self._is_cancelled:
                    return HardScanResult(
                        success=False,
                        scan_id=scan_id,
                        sampled_frames=len(captured_frames),
                        accepted_observations=[],
                        error_message="Scan cancelled before capture complete",
                        duration_seconds=time.monotonic() - scan_start_mono,
                    )

                scheduled_time = scan_start_mono + (frame_idx * slot_interval)
                sleep_needed = scheduled_time - time.monotonic()
                if sleep_needed > 0.001:
                    time.sleep(sleep_needed)

                frame_mono = time.monotonic()
                frame_data = source.capture_frame()

                if frame_data is None:
                    return HardScanResult(
                        success=False,
                        scan_id=scan_id,
                        sampled_frames=len(captured_frames),
                        accepted_observations=[],
                        error_message=f"Capture failed at frame {frame_idx + 1}/10",
                        duration_seconds=time.monotonic() - scan_start_mono,
                    )

                frame_iso = format_utc_iso_ms()
                captured_frames.append(
                    CapturedFrame(
                        frame_index=frame_idx,
                        timestamp_iso=frame_iso,
                        timestamp_monotonic=frame_mono,
                        image_data=frame_data,
                    )
                )

            if len(captured_frames) != target_frame_count:
                return HardScanResult(
                    success=False,
                    scan_id=scan_id,
                    sampled_frames=len(captured_frames),
                    accepted_observations=[],
                    error_message=f"Fewer than 10 frames obtained ({len(captured_frames)})",
                    duration_seconds=time.monotonic() - scan_start_mono,
                )

            # -------------------------------------------------------------
            # Stage 2: Run Locate YOLO inference per frame & accumulate
            # -------------------------------------------------------------
            frame_detections: List[List[Detection]] = []
            for frame in captured_frames:
                if self._is_cancelled:
                    return HardScanResult(
                        success=False,
                        scan_id=scan_id,
                        sampled_frames=len(captured_frames),
                        accepted_observations=[],
                        error_message="Scan cancelled during inference",
                        duration_seconds=time.monotonic() - scan_start_mono,
                    )
                dets = det.detect(frame.image_data)
                frame_detections.append(dets)

            # -------------------------------------------------------------
            # Stage 3: Track and associate same-class detections across frames
            # using IoU >= 0.30 and greedy one-to-one matching
            # -------------------------------------------------------------
            # We track instances as:
            # tracklet = {
            #     'class_name': str,
            #     'detections': List[Tuple[CapturedFrame, Detection]],
            #     'last_box': Tuple[float, float, float, float]
            # }
            tracklets: List[Dict[str, Any]] = []

            for frame, detections in zip(captured_frames, frame_detections):
                # Filter detections: normalize aliases
                normalized_dets: List[Detection] = []
                for d in detections:
                    canonical_name = self.config.class_aliases.get(d.class_name.lower(), d.class_name.lower())
                    normalized_dets.append(
                        Detection(
                            class_name=canonical_name,
                            confidence=d.confidence,
                            box=d.box,
                        )
                    )

                # Match detections to existing tracklets with same class and IoU >= 0.30
                unmatched_dets = list(normalized_dets)
                for trk in tracklets:
                    best_match_idx = -1
                    best_iou = 0.30  # PRD §11.2 threshold

                    for idx, det in enumerate(unmatched_dets):
                        if det.class_name != trk["class_name"]:
                            continue
                        iou = compute_iou(trk["last_box"], det.box)
                        if iou >= best_iou:
                            best_iou = iou
                            best_match_idx = idx

                    if best_match_idx >= 0:
                        matched_det = unmatched_dets.pop(best_match_idx)
                        trk["detections"].append((frame, matched_det))
                        trk["last_box"] = matched_det.box

                # Any remaining detections start new tracklets
                for remaining_det in unmatched_dets:
                    tracklets.append(
                        {
                            "class_name": remaining_det.class_name,
                            "detections": [(frame, remaining_det)],
                            "last_box": remaining_det.box,
                        }
                    )

            # -------------------------------------------------------------
            # Stage 4: Apply Acceptance Rule (PRD §11.2):
            # Confidence >= 0.60 in >= 6 of 10 frames in the SAME zone
            # -------------------------------------------------------------
            accepted_records: List[ObservationRecord] = []
            class_instance_counters: Dict[str, int] = {}

            for trk in tracklets:
                class_name = trk["class_name"]
                all_matches: List[Tuple[CapturedFrame, Detection]] = trk["detections"]

                # Group accepted detections by zone
                # (det.confidence >= 0.60 and box center in configured zone)
                zone_groups: Dict[str, List[Tuple[CapturedFrame, Detection]]] = {}

                for f, det in all_matches:
                    if det.confidence < self.config.detection_confidence_threshold:
                        continue
                    zone = match_zone(det.center, self.config.zones)
                    if zone is None:
                        continue
                    if zone.zone_id not in zone_groups:
                        zone_groups[zone.zone_id] = []
                    zone_groups[zone.zone_id].append((f, det))

                # Check if any zone has >= 6 accepted detections
                for zone_id, valid_matches in zone_groups.items():
                    seen_count = len(valid_matches)
                    if seen_count >= self.config.min_frames_for_confirmation:
                        # Accepted object instance!
                        class_instance_counters[class_name] = class_instance_counters.get(class_name, 0) + 1
                        inst_num = class_instance_counters[class_name]

                        mean_conf = sum(d.confidence for _, d in valid_matches) / seen_count
                        mean_x = sum(d.center[0] for _, d in valid_matches) / seen_count
                        mean_y = sum(d.center[1] for _, d in valid_matches) / seen_count

                        first_seen_iso = valid_matches[0][0].timestamp_iso
                        last_seen_iso = valid_matches[-1][0].timestamp_iso

                        rec = ObservationRecord(
                            scan_id=scan_id,
                            object_name=class_name,
                            camera_id=self.config.camera_profile_id,
                            camera_profile_version=pinned_profile_version,
                            area=area,
                            zone=zone_id,
                            instance_in_scan=inst_num,
                            relative_x=round(mean_x, 4),
                            relative_y=round(mean_y, 4),
                            confidence=round(mean_conf, 4),
                            seen_frames=seen_count,
                            sampled_frames=target_frame_count,
                            first_seen=first_seen_iso,
                            last_seen=last_seen_iso,
                        )
                        accepted_records.append(rec)
                        # An instance belongs to one zone; break after accepting
                        break

            # -------------------------------------------------------------
            # Stage 5: Atomic Commit & Timing Verification
            # -------------------------------------------------------------
            total_duration = time.monotonic() - scan_start_mono
            if total_duration > 5.0:
                return HardScanResult(
                    success=False,
                    scan_id=scan_id,
                    sampled_frames=len(captured_frames),
                    accepted_observations=[],
                    error_message=f"Scan exceeded 5.0s deadline ({total_duration:.2f}s); wrote nothing",
                    duration_seconds=total_duration,
                )

            if self._is_cancelled:
                return HardScanResult(
                    success=False,
                    scan_id=scan_id,
                    sampled_frames=len(captured_frames),
                    accepted_observations=[],
                    error_message="Scan cancelled before database commit; wrote nothing",
                    duration_seconds=total_duration,
                )

            # Persist observations if any were accepted
            if accepted_records:
                self.db.save_scan_snapshot(accepted_records)

            return HardScanResult(
                success=True,
                scan_id=scan_id,
                sampled_frames=len(captured_frames),
                accepted_observations=accepted_records,
                error_message=None,
                duration_seconds=total_duration,
            )

        finally:
            with self._lock:
                self._active_scan_id = None
                self._is_cancelled = False
