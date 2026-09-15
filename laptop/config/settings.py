"""Laptop configuration settings for NaviSense Locate Service."""
from dataclasses import dataclass, field
from typing import Dict, List, Tuple

@dataclass(frozen=True)
class SpatialZone:
    """Semantic coarse zone on tabletop/stationary space."""
    zone_id: str
    name: str
    # Bounding box in normalized coordinates (x_min, y_min, x_max, y_max)
    bounds: Tuple[float, float, float, float]

@dataclass
class LaptopConfig:
    """Configuration for stationary locate system."""
    camera_profile_id: str = "tabletop_cam_v1"
    camera_device_index: int = 0
    server_host: str = "127.0.0.1"
    server_port: int = 8000
    
    # Hard Scan settings
    scan_frames_count: int = 10
    scan_duration_seconds: float = 2.0
    detection_confidence_threshold: float = 0.60
    min_frames_for_confirmation: int = 6  # at least 6 out of 10 frames
    
    # Staleness threshold
    stale_threshold_seconds: float = 60.0
    
    # Pre-configured non-overlapping tabletop zones
    zones: List[SpatialZone] = field(default_factory=lambda: [
        SpatialZone(zone_id="zone_left", name="Left Table", bounds=(0.0, 0.0, 0.33, 1.0)),
        SpatialZone(zone_id="zone_center", name="Center Table", bounds=(0.33, 0.0, 0.66, 1.0)),
        SpatialZone(zone_id="zone_right", name="Right Table", bounds=(0.66, 0.0, 1.0, 1.0)),
    ])
    
    # Alias mapping
    class_aliases: Dict[str, str] = field(default_factory=lambda: {
        "key": "keys",
        "keys": "keys",
        "house keys": "keys",
        "car keys": "keys",
        "wallet": "wallet",
        "billfold": "wallet",
        "purse": "wallet",
    })
