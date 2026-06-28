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


class Calibration(BaseModel):

    version: int = 1

    image: str = ""

    imageWidth: int = 0

    imageHeight: int = 0

    points: list[CalibrationPoint] = Field(default_factory=list)