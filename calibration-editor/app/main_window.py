from pathlib import Path

from PySide6.QtCore import QPoint, Qt
from PySide6.QtGui import QAction, QColor, QBrush

from PySide6.QtWidgets import (
    QDockWidget,
    QFileDialog,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QMainWindow,
    QMessageBox,
    QPushButton,
    QTextEdit,
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
        self._loading_calibration = False
        self.imageView = ImageView()
        self._image_status_label = QLabel("Image: —")
        self._pixel_status_label = QLabel("Pixel: —")
        self._zoom_status_label = QLabel("Zoom: 100%")
        self._rms_status_label = QLabel("RMS: —")
        self._max_error_label = QLabel("Max: —")
        self._quality_status_label = QLabel("Calibration: —")
        self._quality_panel = self._create_quality_panel()

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
        self.statusBar().addPermanentWidget(self._quality_status_label)

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
            "Open Image...",
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

        layout.addWidget(QLabel("Mission Objective (optional)"))
        self._mission_objective_edit = QTextEdit()
        self._mission_objective_edit.setPlaceholderText("No mission objective")
        self._mission_objective_edit.setFixedHeight(72)
        layout.addWidget(self._mission_objective_edit)

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
        self._points_list.itemDoubleClicked.connect(self.onPointDoubleClicked)
        self._refresh_points_list()
        layout.addWidget(self._points_list)

        layout.addWidget(self._quality_panel)

        dock.setWidget(container)
        return dock

    def onImageLoaded(
        self,
        filename,
        width,
        height,
    ):

        image_name = filename
        if self._loading_calibration and self._controller.calibration.image:
            image_name = self._controller.calibration.image

        self._image_status_label.setText(
            f"Image: {image_name}"
        )
        self._pixel_status_label.setText("Pixel: —")
        self._controller.set_image_metadata(image_name, width, height)
        self._refresh_point_markers()
        self._loading_calibration = False

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
            mission_objective=self._mission_objective_edit.toPlainText().strip() or None,
        )
        self.imageView.clear_pending_marker()
        self._refresh_points_list()
        self._refresh_point_markers()
        self._update_transform_status()
        self._gps_edit.clear()
        self._name_edit.clear()
        self._name_edit.setText(self._default_point_name())
        self._mission_objective_edit.clear()

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
            mission_objective=self._mission_objective_edit.toPlainText().strip() or None,
        )
        self._refresh_points_list()
        self._refresh_point_markers()
        self._update_transform_status()
        self._points_list.setCurrentRow(index)

    def onDeletePointClicked(self) -> None:
        selected_items = self._points_list.selectedItems()
        if not selected_items:
            return

        index = self._points_list.row(selected_items[0])
        reply = QMessageBox.question(
            self,
            "Delete point",
            f"Delete calibration point '{self._controller.points[index].name}'?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
        )
        if reply != QMessageBox.StandardButton.Yes:
            return

        self._controller.delete_point(index)
        self._refresh_points_list()
        self._refresh_point_markers()
        self._update_transform_status()
        self._clear_point_form()

    def onPointSelectionChanged(self) -> None:
        selected_items = self._points_list.selectedItems()
        if not selected_items:
            self._controller.set_selected_point(None)
            self._update_point_button.setEnabled(False)
            self._delete_point_button.setEnabled(False)
            self._refresh_point_markers()
            return

        index = self._points_list.row(selected_items[0])
        if index < 0 or index >= len(self._controller.points):
            self._controller.set_selected_point(None)
            self._update_point_button.setEnabled(False)
            self._delete_point_button.setEnabled(False)
            self._refresh_point_markers()
            return

        self._controller.set_selected_point(index)
        point = self._controller.points[index]
        self._name_edit.setText(point.name)
        self._gps_edit.setText(self._gps_text_for_point(point))
        self._pixel_x_edit.setText(str(point.pixel.x))
        self._pixel_y_edit.setText(str(point.pixel.y))
        self._mission_objective_edit.setPlainText(point.missionObjective or "")
        self._update_point_button.setEnabled(True)
        self._delete_point_button.setEnabled(True)
        self._refresh_point_markers()

    def _create_quality_panel(self) -> QWidget:
        panel = QWidget(self)
        panel_layout = QVBoxLayout(panel)
        panel_layout.setContentsMargins(0, 8, 0, 0)
        panel_layout.setSpacing(4)

        panel_layout.addWidget(QLabel("Calibration Quality"))
        self._quality_points_label = QLabel("Points: —")
        self._quality_rms_label = QLabel("Pixel RMS: —")
        self._quality_normalized_label = QLabel("Normalized RMS: —")
        self._quality_max_label = QLabel("Maximum Error: —")
        self._quality_status_label_detail = QLabel("Status: —")

        panel_layout.addWidget(self._quality_points_label)
        panel_layout.addWidget(self._quality_rms_label)
        panel_layout.addWidget(self._quality_normalized_label)
        panel_layout.addWidget(self._quality_max_label)
        panel_layout.addWidget(self._quality_status_label_detail)
        return panel

    def _refresh_points_list(self) -> None:
        selected_index = self._points_list.currentRow()
        self._points_list.clear()
        if not self._controller.points:
            self._points_list.addItem("(empty list)")
            self._controller.set_selected_point(None)
            return

        _, metrics = self._controller.compute_affine_transform()
        errors = metrics.get("errors", []) if metrics else []

        for index, point in enumerate(self._controller.points):
            text = self._controller.point_display_text(point)
            if index < len(errors):
                error_value = errors[index]
                text = f"{text}  [err: {error_value:.1f}px]"
            item = QListWidgetItem(text)
            if index < len(errors):
                self._set_error_item_color(item, errors[index])
            self._points_list.addItem(item)

        if self._controller.selected_point_index() is not None:
            selected_index = self._controller.selected_point_index()
        if 0 <= selected_index < len(self._controller.points):
            self._points_list.setCurrentRow(selected_index)

    def _clear_point_form(self) -> None:
        self._controller.set_selected_point(None)
        self._gps_edit.clear()
        self._name_edit.clear()
        self._name_edit.setText(self._default_point_name())
        self._mission_objective_edit.clear()
        self._pixel_x_edit.setText("0")
        self._pixel_y_edit.setText("0")
        self._update_point_button.setEnabled(False)
        self._delete_point_button.setEnabled(False)
        self._refresh_point_markers()

    def _refresh_point_markers(self) -> None:
        points = [
            (point.name, point.pixel.x, point.pixel.y)
            for point in self._controller.points
        ]
        self.imageView.set_point_markers(points, self._controller.selected_point_index())

    def _update_transform_status(self) -> None:
        quality = self._controller.evaluate_quality()
        self._quality_points_label.setText(f"Points: {quality.point_count}")
        self._quality_rms_label.setText(f"Pixel RMS: {quality.rms_pixel_error:.2f}px")
        self._quality_normalized_label.setText(
            f"Normalized RMS: {quality.normalized_rms_percent:.2f}%"
        )
        self._quality_max_label.setText(f"Maximum Error: {quality.max_pixel_error:.2f}px")
        self._quality_status_label_detail.setText(f"Status: {quality.status}")
        self._quality_status_label.setText(
            f"Calibration: {quality.status} ({quality.normalized_rms_percent:.2f}%)"
        )

    def _set_error_item_color(self, item: QListWidgetItem, error_value: float) -> None:
        if error_value < 3.0:
            color = QColor("#2e7d32")
        elif error_value <= 10.0:
            color = QColor("#b8860b")
        else:
            color = QColor("#c62828")
        item.setForeground(QBrush(color))

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
            "calibration.json",
            "JSON Files (*.json)",
        )
        if not filename:
            return
        self._save_calibration(filename)

    def _save_calibration(self, filename: str) -> None:
        selected_index = self._points_list.currentRow()
        if 0 <= selected_index < len(self._controller.points):
            self._controller.set_selected_point(selected_index)
        else:
            self._controller.set_selected_point(None)

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
        self._update_transform_status()
        self._calibration_path = filename
        self._image_status_label.setText(f"Image: {calibration.image}")

        if calibration.selectedPoint is not None and 0 <= calibration.selectedPoint < len(self._controller.points):
            self._points_list.setCurrentRow(calibration.selectedPoint)
            point = self._controller.points[calibration.selectedPoint]
            self._name_edit.setText(point.name)
            self._gps_edit.setText(self._gps_text_for_point(point))
            self._pixel_x_edit.setText(str(point.pixel.x))
            self._pixel_y_edit.setText(str(point.pixel.y))
            self._mission_objective_edit.setPlainText(point.missionObjective or "")
            self._update_point_button.setEnabled(True)
            self._delete_point_button.setEnabled(True)
        else:
            self._clear_point_form()

        if calibration.image:
            image_path = Path(calibration.image)
            if not image_path.is_absolute():
                image_path = (Path(filename).parent / image_path).resolve()
            if image_path.exists():
                self._loading_calibration = True
                self.imageView.open_image_path(str(image_path))
            else:
                QMessageBox.warning(
                    self,
                    "Image not found",
                    f"The referenced image '{calibration.image}' could not be found.",
                )
        else:
            self._loading_calibration = False

    def onPointDoubleClicked(self, item) -> None:
        row = self._points_list.row(item)
        if row < 0 or row >= len(self._controller.points):
            return
        self._points_list.setCurrentRow(row)
        self._name_edit.setFocus()
        self._name_edit.selectAll()

    def _gps_text_for_point(self, point) -> str:
        latitude_suffix = "N" if point.gps.latitude >= 0 else "S"
        longitude_suffix = "E" if point.gps.longitude >= 0 else "W"
        return (
            f"{abs(point.gps.latitude):.8f}{latitude_suffix} "
            f"{abs(point.gps.longitude):.8f}{longitude_suffix}"
        )

    def _default_point_name(self) -> str:
        return self._controller.next_default_name()

    def keyPressEvent(self, event) -> None:
        if event.key() == Qt.Key.Key_Delete and self._points_list.selectedItems():
            self.onDeletePointClicked()
            event.accept()
            return
        super().keyPressEvent(event)

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