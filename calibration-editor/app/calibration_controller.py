from app.gps_parser import GpsParseError, parse_gps
from app.models import Calibration, CalibrationPoint, PixelPoint
from app.models.calibration_quality import CalibrationQuality
from app.services.calibration_quality import CalibrationQualityService


class CalibrationController:
    """Owns the in-memory calibration session and point management."""

    def __init__(self, calibration: Calibration | None = None) -> None:
        self._calibration = calibration or Calibration()
        self._quality_service = CalibrationQualityService()

    @property
    def calibration(self) -> Calibration:
        return self._calibration

    @property
    def points(self) -> list[CalibrationPoint]:
        return self._calibration.points

    def set_image_metadata(self, filename: str, width: int, height: int) -> None:
        self._calibration.image = filename
        self._calibration.imageWidth = width
        self._calibration.imageHeight = height

    def set_selected_point(self, index: int | None) -> None:
        self._calibration.selectedPoint = index

    def selected_point_index(self) -> int | None:
        return self._calibration.selectedPoint

    def next_default_name(self) -> str:
        existing_names = {point.name for point in self._calibration.points if point.name}
        index = 1
        while f"P{index:03d}" in existing_names:
            index += 1
        return f"P{index:03d}"

    def add_point(
        self,
        *,
        pixel_x: int,
        pixel_y: int,
        gps_text: str,
        name: str = "",
        mission_objective: str | None = None,
    ) -> CalibrationPoint:
        parsed_gps = parse_gps(gps_text)
        point_name = name.strip() or self.next_default_name()
        objective = mission_objective.strip() if mission_objective else None
        point = CalibrationPoint(
            name=point_name,
            pixel=PixelPoint(x=pixel_x, y=pixel_y),
            gps=parsed_gps,
            missionObjective=objective or None,
        )
        self._calibration.points.append(point)
        return point

    def update_point(
        self,
        index: int,
        *,
        pixel_x: int,
        pixel_y: int,
        gps_text: str,
        name: str = "",
        mission_objective: str | None = None,
    ) -> CalibrationPoint:
        if not 0 <= index < len(self._calibration.points):
            raise IndexError("Calibration point index out of range")

        parsed_gps = parse_gps(gps_text)
        point = self._calibration.points[index]
        point.name = name.strip() or self.next_default_name()
        point.pixel = PixelPoint(x=pixel_x, y=pixel_y)
        point.gps = parsed_gps
        objective = mission_objective.strip() if mission_objective else None
        point.missionObjective = objective or None
        return point

    def delete_point(self, index: int) -> None:
        if not 0 <= index < len(self._calibration.points):
            raise IndexError("Calibration point index out of range")
        del self._calibration.points[index]

    def point_display_text(self, point: CalibrationPoint) -> str:
        return f"{point.name}   ({point.pixel.x},{point.pixel.y})"

    def evaluate_quality(self) -> CalibrationQuality:
        return self._quality_service.evaluate(self._calibration)

    def compute_affine_transform(self) -> tuple[list[float] | None, dict[str, float] | None]:
        if len(self._calibration.points) < 3:
            return None, None

        params = self._quality_service._fit_affine_transform(self._calibration.points)
        quality = self.evaluate_quality()
        return params, {
            "rms": quality.rms_pixel_error,
            "max": quality.max_pixel_error,
            "errors": quality.per_point_errors,
        }

    def transform_gps_to_pixel(self, latitude: float, longitude: float) -> tuple[float, float] | None:
        transform, _ = self.compute_affine_transform()
        if transform is None:
            return None

        return (
            transform[0] * latitude + transform[1] * longitude + transform[2],
            transform[3] * latitude + transform[4] * longitude + transform[5],
        )
