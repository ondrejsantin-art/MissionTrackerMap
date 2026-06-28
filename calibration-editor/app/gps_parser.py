import re

from app.models import GpsPoint


class GpsParseError(ValueError):
    """Raised when GPS text cannot be parsed into latitude/longitude."""


_COORDINATE_PATTERN = re.compile(
    r"""
    ^\s*
    (?P<lat>[+-]?\d+(?:\.\d+)?)
    (?P<lat_dir>[NnSs])?
    \s*(?:,|\s)?
    \s*
    (?P<lon_dir_prefix>[EeWw])?
    \s*
    (?P<lon>[+-]?\d+(?:\.\d+)?)
    (?P<lon_dir_suffix>[NnSsEeWw])?
    \s*$
    """,
    re.VERBOSE,
)


def parse_gps(text: str) -> GpsPoint:
    """Parse GPS text into a decimal latitude/longitude pair.

    Supported examples include:
    - 50.8656661N, 15.1380419E
    - 50.8656661N 15.1380419E
    - 50.8656661,15.1380419
    - 50.8656661 15.1380419
    - 50.8656661 N 15.1380419
    - 50.8656661 E 15.1380419
    """

    if not isinstance(text, str):
        raise GpsParseError("GPS value must be a string")

    cleaned = text.strip()
    if not cleaned:
        raise GpsParseError("GPS value cannot be empty")

    match = _COORDINATE_PATTERN.match(cleaned)
    if not match:
        raise GpsParseError(f"Unsupported GPS format: {text}")

    latitude = float(match.group("lat"))
    longitude = float(match.group("lon"))

    lat_dir = match.group("lat_dir")
    if lat_dir is not None:
        if lat_dir.lower() == "s":
            latitude = -latitude

    lon_dir = match.group("lon_dir_prefix") or match.group("lon_dir_suffix")
    if lon_dir is not None:
        if lon_dir.lower() == "w":
            longitude = -longitude

    return GpsPoint(latitude=latitude, longitude=longitude)
