from pydantic import BaseModel, Field


class PixelPoint(BaseModel):
    x: int
    y: int


class GpsPoint(BaseModel):
    latitude: float
    longitude: float


class CalibrationPoint(BaseModel):
    name: str

    pixel: PixelPoint

    gps: GpsPoint

    missionObjective: str | None = None


class CalibrationPointDraft(BaseModel):
    name: str = ""
    pixel_x: int = 0
    pixel_y: int = 0
    gps_text: str = ""


class Calibration(BaseModel):

    version: int = 1

    image: str = ""

    imageWidth: int = 0

    imageHeight: int = 0

    selectedPoint: int | None = None

    points: list[CalibrationPoint] = Field(default_factory=list)