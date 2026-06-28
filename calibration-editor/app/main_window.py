from PySide6.QtWidgets import QMainWindow

from app.image_view import ImageView


class MainWindow(QMainWindow):

    def __init__(self):

        super().__init__()

        self.setWindowTitle(
            "MissionTracker Calibration Editor"
        )

        self.image = ImageView()

        self.setCentralWidget(
            self.image
        )