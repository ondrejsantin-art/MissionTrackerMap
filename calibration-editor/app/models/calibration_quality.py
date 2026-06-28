from pydantic import BaseModel, Field


class CalibrationQuality(BaseModel):
    """Calculated calibration quality metrics."""

    point_count: int
    rms_pixel_error: float
    max_pixel_error: float
    normalized_rms_percent: float
    status: str
    per_point_errors: list[float] = Field(default_factory=list)
