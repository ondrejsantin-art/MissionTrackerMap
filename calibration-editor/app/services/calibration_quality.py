import math
from typing import Sequence

from app.models.calibration_quality import CalibrationQuality
from app.models import Calibration, CalibrationPoint


class CalibrationQualityService:
    """Evaluate calibration quality independent of image resolution."""

    def evaluate(self, calibration: Calibration) -> CalibrationQuality:
        if len(calibration.points) < 3:
            return CalibrationQuality(
                point_count=len(calibration.points),
                rms_pixel_error=0.0,
                max_pixel_error=0.0,
                normalized_rms_percent=0.0,
                status="Poor",
                per_point_errors=[],
            )

        transform = self._fit_affine_transform(calibration.points)
        errors = self._compute_point_errors(transform, calibration.points)
        rms_pixel_error = self._rms(errors)
        max_pixel_error = max(errors) if errors else 0.0
        image_diagonal = self._image_diagonal(calibration.imageWidth, calibration.imageHeight)
        normalized_rms = rms_pixel_error / image_diagonal if image_diagonal > 0 else 0.0
        normalized_rms_percent = normalized_rms * 100.0

        return CalibrationQuality(
            point_count=len(calibration.points),
            rms_pixel_error=rms_pixel_error,
            max_pixel_error=max_pixel_error,
            normalized_rms_percent=normalized_rms_percent,
            status=self._classify(normalized_rms_percent),
            per_point_errors=errors,
        )

    def _fit_affine_transform(self, points: Sequence[CalibrationPoint]) -> list[float]:
        rows: list[list[float]] = []
        targets: list[float] = []

        for point in points:
            lat = float(point.gps.latitude)
            lon = float(point.gps.longitude)
            rows.append([lat, lon, 1.0, 0.0, 0.0, 0.0])
            rows.append([0.0, 0.0, 0.0, lat, lon, 1.0])
            targets.extend([float(point.pixel.x), float(point.pixel.y)])

        ata = [[0.0] * 6 for _ in range(6)]
        atb = [0.0] * 6

        for row_index, row in enumerate(rows):
            for col_index, value in enumerate(row):
                atb[col_index] += value * targets[row_index]
                for col_index_2 in range(6):
                    ata[col_index][col_index_2] += value * row[col_index_2]

        params = self._solve_linear_system(ata, atb)
        if params is None:
            return [0.0] * 6
        return params

    def _compute_point_errors(self, params: Sequence[float], points: Sequence[CalibrationPoint]) -> list[float]:
        errors: list[float] = []
        for point in points:
            lat = float(point.gps.latitude)
            lon = float(point.gps.longitude)
            predicted_x = params[0] * lat + params[1] * lon + params[2]
            predicted_y = params[3] * lat + params[4] * lon + params[5]
            dx = predicted_x - point.pixel.x
            dy = predicted_y - point.pixel.y
            errors.append(math.hypot(dx, dy))
        return errors

    def _rms(self, values: Sequence[float]) -> float:
        if not values:
            return 0.0
        return math.sqrt(sum(value * value for value in values) / len(values))

    def _image_diagonal(self, width: int, height: int) -> float:
        return math.hypot(float(width), float(height))

    def _classify(self, normalized_rms_percent: float) -> str:
        if normalized_rms_percent < 0.30:
            return "Excellent"
        if normalized_rms_percent < 0.70:
            return "Good"
        if normalized_rms_percent < 1.20:
            return "Acceptable"
        return "Poor"

    def _solve_linear_system(self, matrix: Sequence[Sequence[float]], vector: Sequence[float]) -> list[float] | None:
        size = len(matrix)
        aug = [list(row) + [vector[index]] for index, row in enumerate(matrix)]

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
