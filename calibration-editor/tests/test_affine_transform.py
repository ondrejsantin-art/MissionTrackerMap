from app.calibration_controller import CalibrationController
from app.models import Calibration


def test_affine_transform_exact_fit():
    controller = CalibrationController(Calibration())
    controller.add_point(pixel_x=10, pixel_y=20, gps_text="0N 0E", name="A")
    controller.add_point(pixel_x=20, pixel_y=30, gps_text="1N 0E", name="B")
    controller.add_point(pixel_x=15, pixel_y=25, gps_text="0N 1E", name="C")

    transform, rms = controller.compute_affine_transform()

    assert transform is not None
    assert rms is not None
    assert rms == 0.0

    pixel = controller.transform_gps_to_pixel(0.5, 0.5)
    assert pixel is not None
    assert pixel[0] == 15.0
    assert pixel[1] == 25.0
