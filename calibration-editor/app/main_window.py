from PySide6.QtCore import QPoint
from PySide6.QtGui import QAction

from PySide6.QtWidgets import (
    QLabel,
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
        self._image_status_label = QLabel("Image: —")
        self._pixel_status_label = QLabel("Pixel: —")
        self._zoom_status_label = QLabel("Zoom: 100%")

        self.setCentralWidget(
            self.imageView
        )

        self.createMenu()

        self.statusBar().addPermanentWidget(self._image_status_label)
        self.statusBar().addPermanentWidget(self._pixel_status_label)
        self.statusBar().addPermanentWidget(self._zoom_status_label)

        self.imageView.imageLoaded.connect(
            self.onImageLoaded
        )
        self.imageView.pixelHovered.connect(
            self.onPixelHovered
        )
        self.imageView.zoomChanged.connect(
            self.onZoomChanged
        )

    def createMenu(self):

        fileMenu = self.menuBar().addMenu("&File")

        openAction = QAction(
            "Open...",
            self
        )
        openAction.triggered.connect(
            self.imageView.openImage
        )
        fileMenu.addAction(openAction)

        viewMenu = self.menuBar().addMenu("&View")

        fitAction = QAction(
            "Fit to Window",
            self
        )
        fitAction.triggered.connect(
            self.imageView.fitToWindow
        )
        viewMenu.addAction(fitAction)

    def onImageLoaded(
        self,
        filename,
        width,
        height,
    ):

        self._image_status_label.setText(
            f"Image: {filename}"
        )
        self._pixel_status_label.setText("Pixel: —")

    def onPixelHovered(self, pixel: QPoint) -> None:
        self._pixel_status_label.setText(
            f"Pixel: {pixel.x()}, {pixel.y()}"
        )

    def onZoomChanged(
        self,
        zoom,
    ):

        self._zoom_status_label.setText(
            f"Zoom: {zoom:.0f}%"
        )
        self.setWindowTitle(
            f"MissionTracker Calibration Editor   ({zoom:.0f}%)"
        )