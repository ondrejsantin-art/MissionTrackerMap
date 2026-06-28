import json
from pathlib import Path

from models import Calibration


def save(calibration: Calibration, filename: str):

    Path(filename).write_text(
        calibration.model_dump_json(indent=4),
        encoding="utf-8",
    )


def load(filename: str) -> Calibration:

    text = Path(filename).read_text(encoding="utf-8")

    return Calibration.model_validate_json(text)
