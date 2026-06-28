import math

from app.gps_parser import GpsParseError, parse_gps
from app.models import Calibration, CalibrationPoint, PixelPoint


class CalibrationController:
    """Owns the in-memory calibration session and point management."""

    def __init__(self, calibration: Calibration | None = None) -> None:
        self._calibration = calibration or Calibration()

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
    ) -> CalibrationPoint:
        parsed_gps = parse_gps(gps_text)
        point_name = name.strip() or self.next_default_name()
        point = CalibrationPoint(
            name=point_name,
            pixel=PixelPoint(x=pixel_x, y=pixel_y),
            gps=parsed_gps,
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
    ) -> CalibrationPoint:
        if not 0 <= index < len(self._calibration.points):
            raise IndexError("Calibration point index out of range")

        parsed_gps = parse_gps(gps_text)
        point = self._calibration.points[index]
        point.name = name.strip() or self.next_default_name()
        point.pixel = PixelPoint(x=pixel_x, y=pixel_y)
        point.gps = parsed_gps
        return point

    def delete_point(self, index: int) -> None:
        if not 0 <= index < len(self._calibration.points):
            raise IndexError("Calibration point index out of range")
        del self._calibration.points[index]

    def point_display_text(self, point: CalibrationPoint) -> str:
        return f"{point.name}   ({point.pixel.x},{point.pixel.y})"

    def compute_affine_transform(self) -> tuple[list[float] | None, dict[str, float] | None]:
        if len(self._calibration.points) < 3:
            return None, None

        rows: list[list[float]] = []
        targets: list[float] = []

        for point in self._calibration.points:
            lat = float(point.gps.latitude)
            lon = float(point.gps.longitude)
            pixel_x = float(point.pixel.x)
            pixel_y = float(point.pixel.y)

            rows.append([lat, lon, 1.0, 0.0, 0.0, 0.0])
            rows.append([0.0, 0.0, 0.0, lat, lon, 1.0])
            targets.extend([pixel_x, pixel_y])

        ata = [[0.0] * 6 for _ in range(6)]
        atb = [0.0] * 6

        for row_index, row in enumerate(rows):
            for col_index, value in enumerate(row):
                atb[col_index] += value * targets[row_index]
                for col_index_2 in range(6):
                    ata[col_index][col_index_2] += value * row[col_index_2]

        params = self._solve_linear_system(ata, atb)
        if params is None:
            return None, None

        errors = []
        for point in self._calibration.points:
            lat = float(point.gps.latitude)
            lon = float(point.gps.longitude)
            predicted_x = params[0] * lat + params[1] * lon + params[2]
            predicted_y = params[3] * lat + params[4] * lon + params[5]
            dx = predicted_x - point.pixel.x
            dy = predicted_y - point.pixel.y
            errors.append(math.hypot(dx, dy))

        rms_error = math.sqrt(sum(value * value for value in errors) / len(errors)) if errors else 0.0
        max_error = max(errors) if errors else 0.0
        return params, {
            "rms": rms_error,
            "max": max_error,
            "errors": errors,
        }

    def transform_gps_to_pixel(self, latitude: float, longitude: float) -> tuple[float, float] | None:
        params, _ = self.compute_affine_transform()
        if params is None:
            return None

        return (
            params[0] * latitude + params[1] * longitude + params[2],
            params[3] * latitude + params[4] * longitude + params[5],
        )

    def _solve_linear_system(self, matrix: list[list[float]], vector: list[float]) -> list[float] | None:
        size = len(matrix)
        aug = [row[:] + [vector[index]] for index, row in enumerate(matrix)]

        for pivot in range(size):
            pivot_row = max(range(pivot, size), key=lambda row: abs(aug[row][pivot]))
            if abs(aug[pivot_row][pivot]) < 1e-12:
                return None
            
            if pivot_row != pivot:
                aug[pivot], aug[pivot_row] = aug[pivot_row], aug[pivot]

            pivot_value = aug[pivot][pivot]
            for col in range(pivot, size + 1):
                aug[pivot][col] /= pivot_value

            for row in range(size):
                if row == pivot:
                    continue
                factor = aug[row][pivot]
                if factor == 0.0:
                    continue
                for col in range(pivot, size + 1):
                    aug[row][col] -= factor * aug[pivot][col]

        return [aug[index][size] for index in range(size)]
