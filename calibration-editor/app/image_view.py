from pathlib import Path

from PySide6.QtCore import QPoint, Qt, Signal
from PySide6.QtGui import QMouseEvent, QPixmap, QWheelEvent
from PySide6.QtWidgets import (
    QFileDialog,
    QGraphicsPixmapItem,
    QGraphicsScene,
    QGraphicsView,
)


class ImageView(QGraphicsView):
    """Widget responsible for displaying and interacting with a single image."""

    imageLoaded = Signal(str, int, int)
    pixelHovered = Signal(QPoint)
    pixelClicked = Signal(QPoint)
    zoomChanged = Signal(float)

    ZOOM_FACTOR = 1.15

    def __init__(self) -> None:
        super().__init__()

        self._scene = QGraphicsScene(self)
        self.setScene(self._scene)

        self._pixmap_item: QGraphicsPixmapItem | None = None
        self._image_path: Path | None = None
        self._press_position: QPoint | None = None
        self._is_dragging = False

        self.setTransformationAnchor(QGraphicsView.ViewportAnchor.AnchorUnderMouse)
        self.setResizeAnchor(QGraphicsView.ViewportAnchor.AnchorUnderMouse)
        self.setDragMode(QGraphicsView.DragMode.ScrollHandDrag)
        self.setBackgroundBrush(Qt.GlobalColor.black)
        self.setMouseTracking(True)

    def hasImage(self) -> bool:
        return self._pixmap_item is not None

    def openImage(self) -> None:
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
        self._pixmap_item = self._scene.addPixmap(pixmap)
        self._image_path = Path(filename)

        self.resetTransform()
        self.setSceneRect(self._pixmap_item.boundingRect())

        self.imageLoaded.emit(
            self._image_path.name,
            pixmap.width(),
            pixmap.height(),
        )

    def fitToWindow(self) -> None:
        if not self.hasImage() or self._pixmap_item is None:
            return

        self.fitInView(
            self._pixmap_item.boundingRect(),
            Qt.AspectRatioMode.KeepAspectRatio,
        )
        self.zoomChanged.emit(self.transform().m11() * 100.0)

    def wheelEvent(self, event: QWheelEvent) -> None:
        if not self.hasImage():
            return

        factor = self.ZOOM_FACTOR if event.angleDelta().y() > 0 else 1.0 / self.ZOOM_FACTOR
        self.scale(factor, factor)
        self.zoomChanged.emit(self.transform().m11() * 100.0)
        event.accept()

    def mousePressEvent(self, event: QMouseEvent) -> None:
        if event.button() == Qt.MouseButton.LeftButton and self.hasImage():
            self._press_position = event.position().toPoint()
            self._is_dragging = False

        super().mousePressEvent(event)

    def mouseMoveEvent(self, event: QMouseEvent) -> None:
        super().mouseMoveEvent(event)

        if not self.hasImage() or self._pixmap_item is None:
            return

        if (
            self._press_position is not None
            and event.buttons() & Qt.MouseButton.LeftButton
        ):
            drag_distance = (event.position().toPoint() - self._press_position).manhattanLength()
            if drag_distance > 3:
                self._is_dragging = True

        pixel = self._pixel_at(event.position().toPoint())
        if pixel is None:
            self.setToolTip("")
            return

        self.setToolTip(f"Pixel: {pixel.x()}, {pixel.y()}")
        self.pixelHovered.emit(pixel)

    def mouseReleaseEvent(self, event: QMouseEvent) -> None:
        super().mouseReleaseEvent(event)

        if event.button() != Qt.MouseButton.LeftButton or not self.hasImage():
            self._press_position = None
            self._is_dragging = False
            return

        if self._is_dragging:
            self._press_position = None
            self._is_dragging = False
            return

        pixel = self._pixel_at(event.position().toPoint())
        if pixel is not None:
            self.pixelClicked.emit(pixel)

        self._press_position = None
        self._is_dragging = False

    def _pixel_at(self, viewport_position: QPoint) -> QPoint | None:
        if not self.hasImage() or self._pixmap_item is None:
            return None

        scene_position = self.mapToScene(viewport_position)
        if not self._pixmap_item.contains(scene_position):
            return None

        return scene_position.toPoint()