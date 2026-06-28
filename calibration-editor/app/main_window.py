from pathlib import Path

from PySide6.QtCore import QPoint, Qt
from PySide6.QtGui import QAction

from PySide6.QtWidgets import (
    QDockWidget,
    QFileDialog,
    QLabel,
    QLineEdit,
    QListWidget,
    QMainWindow,
    QMessageBox,
    QPushButton,
    QVBoxLayout,
    QWidget,
)

from app.calibration_controller import CalibrationController
from app.calibration_io import load as load_calibration, save as save_calibration
from app.gps_parser import GpsParseError, parse_gps
from app.image_view import ImageView


class MainWindow(QMainWindow):

    def __init__(self):

        super().__init__()

        self.setWindowTitle(
            "MissionTracker Calibration Editor"
        )

        self.resize(1400, 900)

        self._calibration_path: str | None = None
        self.imageView = ImageView()
        self._image_status_label = QLabel("Image: —")
        self._pixel_status_label = QLabel("Pixel: —")
        self._zoom_status_label = QLabel("Zoom: 100%")

        self._controller = CalibrationController()

        self._point_dock = self._create_point_dock()
        self.addDockWidget(
            Qt.DockWidgetArea.RightDockWidgetArea,
            self._point_dock,
        )

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
        self.imageView.pixelClicked.connect(
            self.onPixelClicked
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

        openCalibrationAction = QAction("Open Calibration", self)
        openCalibrationAction.triggered.connect(self.onOpenCalibrationTriggered)
        fileMenu.addAction(openCalibrationAction)

        saveAction = QAction("Save Calibration", self)
        saveAction.triggered.connect(self.onSaveCalibrationTriggered)
        fileMenu.addAction(saveAction)

        saveAsAction = QAction("Save Calibration As", self)
        saveAsAction.triggered.connect(self.onSaveCalibrationAsTriggered)
        fileMenu.addAction(saveAsAction)

        viewMenu = self.menuBar().addMenu("&View")

        fitAction = QAction(
            "Fit to Window",
            self
        )
        fitAction.triggered.connect(
            self.imageView.fitToWindow
        )
        viewMenu.addAction(fitAction)

        viewMenu.addSeparator()
        viewMenu.addAction(self._point_dock.toggleViewAction())

    def _create_point_dock(self) -> QDockWidget:
        dock = QDockWidget("Calibration Point", self)
        dock.setAllowedAreas(
            Qt.DockWidgetArea.RightDockWidgetArea
            | Qt.DockWidgetArea.LeftDockWidgetArea
        )

        container = QWidget(self)
        layout = QVBoxLayout(container)
        layout.setContentsMargins(8, 8, 8, 8)
        layout.setSpacing(8)

        layout.addWidget(QLabel("Calibration Point"))

        self._pixel_x_edit = QLineEdit("0")
        self._pixel_x_edit.setReadOnly(True)
        self._pixel_y_edit = QLineEdit("0")
        self._pixel_y_edit.setReadOnly(True)

        layout.addWidget(QLabel("Pixel"))
        layout.addWidget(self._pixel_x_edit)
        layout.addWidget(self._pixel_y_edit)

        self._gps_edit = QLineEdit()
        self._gps_edit.setPlaceholderText("GPS")
        self._name_edit = QLineEdit()
        self._name_edit.setPlaceholderText("Name")

        layout.addWidget(QLabel("GPS"))
        layout.addWidget(self._gps_edit)
        layout.addWidget(QLabel("Name"))
        layout.addWidget(self._name_edit)

        self._add_point_button = QPushButton("Add Point")
        self._add_point_button.clicked.connect(self.onAddPointClicked)
        layout.addWidget(self._add_point_button)

        self._update_point_button = QPushButton("Update Point")
        self._update_point_button.clicked.connect(self.onUpdatePointClicked)
        self._update_point_button.setEnabled(False)
        layout.addWidget(self._update_point_button)

        self._delete_point_button = QPushButton("Delete Point")
        self._delete_point_button.clicked.connect(self.onDeletePointClicked)
        self._delete_point_button.setEnabled(False)
        layout.addWidget(self._delete_point_button)

        layout.addWidget(QLabel("Calibration Points"))
        self._points_list = QListWidget()
        self._points_list.setMinimumHeight(140)
        self._points_list.itemSelectionChanged.connect(self.onPointSelectionChanged)
        self._refresh_points_list()
        layout.addWidget(self._points_list)

        dock.setWidget(container)
        return dock

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
        self._controller.set_image_metadata(filename, width, height)
        self._refresh_point_markers()

    def onPixelHovered(self, pixel: QPoint) -> None:
        self._pixel_status_label.setText(
            f"Pixel: {pixel.x()}, {pixel.y()}"
        )

    def onPixelClicked(self, pixel: QPoint) -> None:
        self._pixel_x_edit.setText(str(pixel.x()))
        self._pixel_y_edit.setText(str(pixel.y()))
        self.imageView.set_pending_marker(pixel)

    def onAddPointClicked(self) -> None:
        gps_text = self._gps_edit.text().strip()
        if not gps_text:
            QMessageBox.warning(
                self,
                "GPS required",
                "Please enter a GPS value before adding a calibration point.",
            )
            return

        try:
            parse_gps(gps_text)
        except GpsParseError as exc:
            QMessageBox.warning(
                self,
                "Invalid GPS",
                str(exc),
            )
            return

        point_name = self._name_edit.text().strip()
        if not point_name:
            point_name = self._default_point_name()

        self._controller.add_point(
            pixel_x=int(self._pixel_x_edit.text()),
            pixel_y=int(self._pixel_y_edit.text()),
            gps_text=gps_text,
            name=point_name,
        )
        self.imageView.clear_pending_marker()
        self._refresh_points_list()
        self._refresh_point_markers()
        self._gps_edit.clear()
        self._name_edit.clear()
        self._name_edit.setText(self._default_point_name())

    def onUpdatePointClicked(self) -> None:
        selected_items = self._points_list.selectedItems()
        if not selected_items:
            return

        index = self._points_list.row(selected_items[0])
        point = self._controller.points[index]
        gps_text = self._gps_edit.text().strip()
        if not gps_text:
            QMessageBox.warning(
                self,
                "GPS required",
                "Please enter a GPS value before updating a calibration point.",
            )
            return

        try:
            parse_gps(gps_text)
        except GpsParseError as exc:
            QMessageBox.warning(
                self,
                "Invalid GPS",
                str(exc),
            )
            return

        point_name = self._name_edit.text().strip() or self._default_point_name()
        self._controller.update_point(
            index,
            pixel_x=int(self._pixel_x_edit.text()),
            pixel_y=int(self._pixel_y_edit.text()),
            gps_text=gps_text,
            name=point_name,
        )
        self._refresh_points_list()
        self._refresh_point_markers()
        self._points_list.setCurrentRow(index)

    def onDeletePointClicked(self) -> None:
        selected_items = self._points_list.selectedItems()
        if not selected_items:
            return

        index = self._points_list.row(selected_items[0])
        self._controller.delete_point(index)
        self._refresh_points_list()
        self._refresh_point_markers()
        self._clear_point_form()

    def onPointSelectionChanged(self) -> None:
        selected_items = self._points_list.selectedItems()
        if not selected_items:
            self._update_point_button.setEnabled(False)
            self._delete_point_button.setEnabled(False)
            return

        index = self._points_list.row(selected_items[0])
        if index < 0 or index >= len(self._controller.points):
            self._update_point_button.setEnabled(False)
            self._delete_point_button.setEnabled(False)
            return

        point = self._controller.points[index]
        self._name_edit.setText(point.name)
        self._gps_edit.setText(self._gps_text_for_point(point))
        self._pixel_x_edit.setText(str(point.pixel.x))
        self._pixel_y_edit.setText(str(point.pixel.y))
        self._update_point_button.setEnabled(True)
        self._delete_point_button.setEnabled(True)

    def _refresh_points_list(self) -> None:
        self._points_list.clear()
        if not self._controller.points:
            self._points_list.addItem("(empty list)")
            return

        for point in self._controller.points:
            self._points_list.addItem(self._controller.point_display_text(point))

    def _clear_point_form(self) -> None:
        self._gps_edit.clear()
        self._name_edit.clear()
        self._name_edit.setText(self._default_point_name())
        self._pixel_x_edit.setText("0")
        self._pixel_y_edit.setText("0")
        self._update_point_button.setEnabled(False)
        self._delete_point_button.setEnabled(False)

    def _refresh_point_markers(self) -> None:
        points = [
            (point.name, point.pixel.x, point.pixel.y)
            for point in self._controller.points
        ]
        self.imageView.set_point_markers(points)

    def onOpenCalibrationTriggered(self) -> None:
        filename, _ = QFileDialog.getOpenFileName(
            self,
            "Open Calibration",
            "",
            "JSON Files (*.json)",
        )
        if not filename:
            return
        self._load_calibration(filename)

    def onSaveCalibrationTriggered(self) -> None:
        if self._calibration_path:
            self._save_calibration(self._calibration_path)
            return
        self.onSaveCalibrationAsTriggered()

    def onSaveCalibrationAsTriggered(self) -> None:
        filename, _ = QFileDialog.getSaveFileName(
            self,
            "Save Calibration",
            "",
            "JSON Files (*.json)",
        )
        if not filename:
            return
        self._save_calibration(filename)

    def _save_calibration(self, filename: str) -> None:
        save_calibration(self._controller.calibration, filename)
        self._calibration_path = filename

    def _load_calibration(self, filename: str) -> None:
        try:
            calibration = load_calibration(filename)
        except (FileNotFoundError, OSError, ValueError) as exc:
            QMessageBox.warning(self, "Load failed", str(exc))
            return

        self._controller = CalibrationController(calibration)
        self._refresh_points_list()
        self._refresh_point_markers()
        self._clear_point_form()
        self._calibration_path = filename
        self._image_status_label.setText(f"Image: {calibration.image}")

        if calibration.image:
            image_path = Path(calibration.image)
            if not image_path.is_absolute():
                image_path = (Path(filename).parent / image_path).resolve()
            if image_path.exists():
                self.imageView.open_image_path(str(image_path))

    def _gps_text_for_point(self, point) -> str:
        latitude_suffix = "N" if point.gps.latitude >= 0 else "S"
        longitude_suffix = "E" if point.gps.longitude >= 0 else "W"
        return (
            f"{abs(point.gps.latitude):.8f}{latitude_suffix} "
            f"{abs(point.gps.longitude):.8f}{longitude_suffix}"
        )

    def _default_point_name(self) -> str:
        return self._controller.next_default_name()

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