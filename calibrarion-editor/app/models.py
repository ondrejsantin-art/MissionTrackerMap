from pydantic import BaseModel


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
    image: str
    imageWidth: int
    imageHeight: int
    points: list[CalibrationPoint] = []
