import pytest
from unittest.mock import patch
from PySide6.QtWidgets import QApplication

from app.main_window import MainWindow
from app.models import Calibration


@pytest.fixture(autouse=True)
def mock_qmessagebox():
    with patch("app.main_window.QMessageBox.warning") as mock_warning:
        yield mock_warning


def test_main_window_saves_calibration_file(tmp_path):
    app = QApplication.instance() or QApplication([])
    window = MainWindow()

    window._controller.set_image_metadata("scarif.png", 3165, 4473)
    window._controller.add_point(
        pixel_x=1321,
        pixel_y=4021,
        gps_text="50.8656661N 15.1380419E",
        name="Archive",
    )
    window._points_list.setCurrentRow(0)

    output_path = tmp_path / "calibration.json"
    window._save_calibration(str(output_path))

    assert output_path.exists()
    loaded = Calibration.model_validate_json(output_path.read_text(encoding="utf-8"))
    assert loaded.image == "scarif.png"
    assert loaded.imageWidth == 3165
    assert loaded.imageHeight == 4473
    assert loaded.points[0].name == "Archive"
    assert loaded.points[0].pixel.x == 1321
    assert loaded.points[0].pixel.y == 4021
    assert loaded.points[0].gps.latitude == 50.8656661
    assert loaded.points[0].gps.longitude == 15.1380419

    window._load_calibration(str(output_path))
    assert window._controller.calibration.image == "scarif.png"
    assert len(window._controller.points) == 1


def test_mission_objective_saved_and_loaded(tmp_path):
    app = QApplication.instance() or QApplication([])
    window = MainWindow()

    window._controller.set_image_metadata("test.png", 100, 100)
    window._controller.add_point(
        pixel_x=50,
        pixel_y=50,
        gps_text="50.0N 15.0E",
        name="Waypoint Alpha",
        mission_objective="Retrieve the beacon from the hilltop.",
    )
    window._points_list.setCurrentRow(0)

    output_path = tmp_path / "mission_objective.json"
    window._save_calibration(str(output_path))

    loaded = Calibration.model_validate_json(output_path.read_text(encoding="utf-8"))
    assert loaded.points[0].missionObjective == "Retrieve the beacon from the hilltop."


def test_mission_objective_blank_saved_as_none(tmp_path):
    app = QApplication.instance() or QApplication([])
    window = MainWindow()

    window._controller.set_image_metadata("test.png", 100, 100)
    window._controller.add_point(
        pixel_x=10,
        pixel_y=10,
        gps_text="50.0N 15.0E",
        name="Pure Calibration Point",
        mission_objective="   ",  # blank → should become None
    )
    window._points_list.setCurrentRow(0)

    output_path = tmp_path / "blank_objective.json"
    window._save_calibration(str(output_path))

    loaded = Calibration.model_validate_json(output_path.read_text(encoding="utf-8"))
    assert loaded.points[0].missionObjective is None


def test_old_json_without_mission_objective_backward_compatible(tmp_path):
    old_json = """{
        "version": 1,
        "image": "scarif.png",
        "imageWidth": 100,
        "imageHeight": 100,
        "selectedPoint": null,
        "points": [
            {"name": "P1", "pixel": {"x": 10, "y": 10}, "gps": {"latitude": 50.0, "longitude": 15.0}},
            {"name": "P2", "pixel": {"x": 20, "y": 20}, "gps": {"latitude": 50.1, "longitude": 15.1}},
            {"name": "P3", "pixel": {"x": 30, "y": 30}, "gps": {"latitude": 50.2, "longitude": 15.2}}
        ]
    }"""
    old_path = tmp_path / "old.json"
    old_path.write_text(old_json, encoding="utf-8")

    loaded = Calibration.model_validate_json(old_path.read_text(encoding="utf-8"))
    assert len(loaded.points) == 3
    for point in loaded.points:
        assert point.missionObjective is None
