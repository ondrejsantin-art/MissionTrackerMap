from PySide6.QtWidgets import QApplication

from app.main_window import MainWindow
from app.models import Calibration


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
