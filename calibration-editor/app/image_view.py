from pathlib import Path

from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QPixmap
from PySide6.QtWidgets import (
    QFileDialog,
    QGraphicsPixmapItem,
    QGraphicsScene,
    QGraphicsView,
)


class ImageView(QGraphicsView):

    imageLoaded = Signal(str, int, int)
    zoomChanged = Signal(float)

    def __init__(self):

        super().__init__()

        self._scene = QGraphicsScene(self)
        self.setScene(self._scene)

        self._pixmapItem = None

        self.setTransformationAnchor(
            QGraphicsView.AnchorUnderMouse
        )

        self.setResizeAnchor(
            QGraphicsView.AnchorUnderMouse
        )

        self.setDragMode(
            QGraphicsView.ScrollHandDrag
        )

        self.setHorizontalScrollBarPolicy(
            Qt.ScrollBarAsNeeded
        )

        self.setVerticalScrollBarPolicy(
            Qt.ScrollBarAsNeeded
        )

        self.setBackgroundBrush(Qt.black)

    def openImage(self):

        filename, _ = QFileDialog.getOpenFileName(
            self,
            "Open map",
            "",
            "Images (*.png *.jpg *.jpeg)"
        )

        if not filename:
            return

        pixmap = QPixmap(filename)

        self._scene.clear()

        self._pixmapItem = QGraphicsPixmapItem(
            pixmap
        )

        self._scene.addItem(self._pixmapItem)

        self.fitToWindow()

        self.imageLoaded.emit(
            Path(filename).name,
            pixmap.width(),
            pixmap.height()
        )

    def fitToWindow(self):

        if self._pixmapItem is None:
            return

        self.fitInView(
            self._pixmapItem,
            Qt.KeepAspectRatio
        )

        self.zoomChanged.emit(100)

    def wheelEvent(self, event):

        factor = 1.15

        if event.angleDelta().y() > 0:

            self.scale(factor, factor)

        else:

            self.scale(
                1 / factor,
                1 / factor,
            )

        zoom = self.transform().m11() * 100

        self.zoomChanged.emit(zoom)