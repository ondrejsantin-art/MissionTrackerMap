from pathlib import Path

from PySide6.QtCore import QPointF, Qt, Signal
from PySide6.QtGui import QPixmap
from PySide6.QtWidgets import (
    QFileDialog,
    QGraphicsPixmapItem,
    QGraphicsScene,
    QGraphicsView,
)


class ImageView(QGraphicsView):
    """
    Widget responsible only for displaying an image.

    Responsibilities:
      - display image
      - zoom
      - pan
      - translate mouse position -> image pixel
      - emit pixelSelected()

    This widget intentionally knows nothing about GPS,
    calibration points or JSON.
    """

    imageLoaded = Signal(str, int, int)
    pixelSelected = Signal(int, int)
    zoomChanged = Signal(float)

    ZOOM_FACTOR = 1.15

    def __init__(self):
        super().__init__()

        self._scene = QGraphicsScene(self)
        self.setScene(self._scene)

        self._pixmap_item: QGraphicsPixmapItem | None = None

        self.setTransformationAnchor(
            QGraphicsView.AnchorUnderMouse
        )

        self.setResizeAnchor(
            QGraphicsView.AnchorUnderMouse
        )

        self.setDragMode(
            QGraphicsView.ScrollHandDrag
        )

        self.setBackgroundBrush(Qt.black)

        self.setMouseTracking(True)

    # --------------------------------------------------------

    def hasImage(self) -> bool:
        return self._pixmap_item is not None

    # --------------------------------------------------------

    def openImage(self):

        filename, _ = QFileDialog.getOpenFileName(
            self,
            "Open map",
            "",
            "Images (*.png *.jpg *.jpeg)",
        )

        if not filename:
            return

        pixmap = QPixmap(filename)

        if pixmap.isNull():
            return

        self._scene.clear()

        self._pixmap_item = self._scene.addPixmap(
            pixmap
        )

        self.setSceneRect(
            self._pixmap_item.boundingRect()
        )

        self.fitToWindow()

        self.imageLoaded.emit(
            Path(filename).name,
            pixmap.width(),
            pixmap.height(),
        )

    # --------------------------------------------------------

    def fitToWindow(self):

        if not self.hasImage():
            return

        self.fitInView(
            self._pixmap_item,
            Qt.KeepAspectRatio,
        )

        self.zoomChanged.emit(
            self.transform().m11() * 100.0
        )

    # --------------------------------------------------------

    def wheelEvent(self, event):

        if not self.hasImage():
            return

        if event.angleDelta().y() > 0:

            factor = self.ZOOM_FACTOR

        else:

            factor = 1.0 / self.ZOOM_FACTOR

        self.scale(factor, factor)

        self.zoomChanged.emit(
            self.transform().m11() * 100.0
        )

    # --------------------------------------------------------

    def mousePressEvent(self, event):

        super().mousePressEvent(event)

        if (
            event.button() != Qt.LeftButton
            or not self.hasImage()
        ):
            return

        scene_pos = self.mapToScene(
            event.position().toPoint()
        )

        if not self._pixmap_item.contains(scene_pos):
            return

        pixel = scene_pos.toPoint()

        self.pixelSelected.emit(
            pixel.x(),
            pixel.y(),
        )

    # --------------------------------------------------------

    def mouseMoveEvent(self, event):

        super().mouseMoveEvent(event)

        if not self.hasImage():
            return

        scene_pos: QPointF = self.mapToScene(
            event.position().toPoint()
        )

        if not self._pixmap_item.contains(scene_pos):
            return

        pixel = scene_pos.toPoint()

        self.setToolTip(
            f"Pixel: {pixel.x()}, {pixel.y()}"
        )