from PySide6.QtGui import QAction

from PySide6.QtWidgets import (
    QFileDialog,
    QMainWindow,
)

from app.image_view import ImageView


class MainWindow(QMainWindow):

    def __init__(self):

        super().__init__()

        self.setWindowTitle(
            "MissionTracker Calibration Editor"
        )

        self.resize(1400, 900)

        self.imageView = ImageView()

        self.setCentralWidget(
            self.imageView
        )

        self.createMenu()

        self.statusBar().showMessage(
            "Ready"
        )

        self.imageView.imageLoaded.connect(
            self.onImageLoaded
        )

        self.imageView.zoomChanged.connect(
            self.onZoomChanged
        )

    def createMenu(self):

        menu = self.menuBar().addMenu("&File")

        openAction = QAction(
            "Open PNG...",
            self
        )

        openAction.triggered.connect(
            self.imageView.openImage
        )

        menu.addAction(openAction)

        fitAction = QAction(
            "Fit to Window",
            self
        )

        fitAction.triggered.connect(
            self.imageView.fitToWindow
        )

        menu.addAction(fitAction)

    def onImageLoaded(
        self,
        filename,
        width,
        height,
    ):

        self.statusBar().showMessage(
            f"{filename}   {width} × {height}"
        )

    def onZoomChanged(
        self,
        zoom,
    ):

        self.setWindowTitle(
            f"MissionTracker Calibration Editor   ({zoom:.0f}%)"
        )