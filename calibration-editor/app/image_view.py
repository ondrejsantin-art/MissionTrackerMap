from PySide6.QtWidgets import QLabel


class ImageView(QLabel):

    def __init__(self):

        super().__init__()

        self.setText(
            "MissionTracker\n\nOpen image..."
        )