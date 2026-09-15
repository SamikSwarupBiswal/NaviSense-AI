"""Tests for indoor obstacle dataset ingestion and class remapping."""

import tempfile
from pathlib import Path
from datasets.ingest_indoor_obstacles import (
    build_remapping_table,
    process_label_file,
    ingest_dataset,
    DEFAULT_SOURCE_CLASS_NAMES,
)


def test_build_remapping_table():
    mapping = build_remapping_table(DEFAULT_SOURCE_CLASS_NAMES, include_sofa_as_table=False)
    # chair is index 5 -> target 1
    assert mapping[5] == 1
    # table is index 6 -> target 2
    assert mapping[6] == 2
    # door (0), window (4), pole (9) should NOT be mapped
    assert 0 not in mapping
    assert 4 not in mapping
    assert 9 not in mapping


def test_build_remapping_table_with_sofa():
    mapping = build_remapping_table(DEFAULT_SOURCE_CLASS_NAMES, include_sofa_as_table=True)
    assert mapping[5] == 1  # chair
    assert mapping[6] == 2  # table
    assert mapping[8] == 2  # sofa mapped to table/furniture hazard


def test_process_label_file(tmp_path: Path):
    mapping = {5: 1, 6: 2}
    lbl_file = tmp_path / "frame_001.txt"
    # Lines:
    # 0: door (skip)
    # 5: chair (keep -> 1)
    # 6: table (keep -> 2)
    # 9: pole (skip)
    content = (
        "0 0.1 0.1 0.2 0.2\n"
        "5 0.5 0.5 0.3 0.4\n"
        "6 0.7 0.8 0.2 0.1\n"
        "9 0.9 0.9 0.05 0.3\n"
    )
    lbl_file.write_text(content, encoding="utf-8")

    lines, counts = process_label_file(lbl_file, mapping)
    assert len(lines) == 2
    assert lines[0].startswith("1 0.500000 0.500000")
    assert lines[1].startswith("2 0.700000 0.800000")
    assert counts[1] == 1
    assert counts[2] == 1


def test_ingest_dataset_flow(tmp_path: Path):
    in_dir = tmp_path / "mock_raw"
    in_dir.mkdir()
    out_dir = tmp_path / "mock_out"

    # Create dummy images and labels
    (in_dir / "img1.jpg").write_bytes(b"\xFF\xD8\xFF" + b"\x00" * 10)
    (in_dir / "img1.txt").write_text("5 0.4 0.4 0.2 0.2\n", encoding="utf-8")

    (in_dir / "img2.png").write_bytes(b"\x89PNG" + b"\x00" * 10)
    (in_dir / "img2.txt").write_text("0 0.1 0.1 0.2 0.2\n", encoding="utf-8")  # negative for chair/table

    stats = ingest_dataset(
        input_dir=in_dir,
        output_dir=out_dir,
        keep_empty_as_negatives=True,
        val_ratio=0.5,
    )

    assert stats["total_source_images"] == 2
    assert (out_dir / "data.yaml").exists()
    assert (out_dir / "manifest.json").exists()
    assert (out_dir / "images" / "train").exists()
    assert (out_dir / "images" / "val").exists()
