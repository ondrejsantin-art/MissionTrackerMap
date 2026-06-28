from PySide6.QtCore import QPoint, Qt
from PySide6.QtGui import QAction

from PySide6.QtWidgets import (
    QDockWidget,
    QLabel,
    QLineEdit,
    QListWidget,
    QMainWindow,
    QMessageBox,
    QPushButton,
    QVBoxLayout,
    QWidget,
)

from app.gps_parser import GpsParseError, parse_gps
from app.image_view import ImageView
from app.models import CalibrationPointDraft


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

        self._calibration_points: list[CalibrationPointDraft] = []
        self._next_point_number = 1

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

    def onPixelHovered(self, pixel: QPoint) -> None:
        self._pixel_status_label.setText(
            f"Pixel: {pixel.x()}, {pixel.y()}"
        )

    def onPixelClicked(self, pixel: QPoint) -> None:
        self._pixel_x_edit.setText(str(pixel.x()))
        self._pixel_y_edit.setText(str(pixel.y()))

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
            parsed_gps = parse_gps(gps_text)
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

        point = CalibrationPointDraft(
            name=point_name,
            pixel_x=int(self._pixel_x_edit.text()),
            pixel_y=int(self._pixel_y_edit.text()),
            gps_text=gps_text,
        )
        point.gps_text = gps_text
        self._calibration_points.append(point)
        self._refresh_points_list()
        self._gps_edit.clear()
        self._name_edit.clear()
        self._name_edit.setText(self._default_point_name())

    def onUpdatePointClicked(self) -> None:
        selected_items = self._points_list.selectedItems()
        if not selected_items:
            return

        index = self._points_list.row(selected_items[0])
        point = self._calibration_points[index]
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

        point.name = self._name_edit.text().strip() or self._default_point_name()
        point.gps_text = gps_text
        self._refresh_points_list()
        self._points_list.setCurrentRow(index)

    def onDeletePointClicked(self) -> None:
        selected_items = self._points_list.selectedItems()
        if not selected_items:
            return

        index = self._points_list.row(selected_items[0])
        del self._calibration_points[index]
        self._refresh_points_list()
        self._clear_point_form()

    def onPointSelectionChanged(self) -> None:
        selected_items = self._points_list.selectedItems()
        if not selected_items:
            self._update_point_button.setEnabled(False)
            self._delete_point_button.setEnabled(False)
            return

        index = self._points_list.row(selected_items[0])
        if index < 0 or index >= len(self._calibration_points):
            self._update_point_button.setEnabled(False)
            self._delete_point_button.setEnabled(False)
            return

        point = self._calibration_points[index]
        self._name_edit.setText(point.name)
        self._gps_edit.setText(point.gps_text)
        self._pixel_x_edit.setText(str(point.pixel_x))
        self._pixel_y_edit.setText(str(point.pixel_y))
        self._update_point_button.setEnabled(True)
        self._delete_point_button.setEnabled(True)

    def _refresh_points_list(self) -> None:
        self._points_list.clear()
        if not self._calibration_points:
            self._points_list.addItem("(empty list)")
            return

        for point in self._calibration_points:
            self._points_list.addItem(
                f"{point.name} @ ({point.pixel_x}, {point.pixel_y})"
            )

    def _clear_point_form(self) -> None:
        self._gps_edit.clear()
        self._name_edit.clear()
        self._name_edit.setText(self._default_point_name())
        self._pixel_x_edit.setText("0")
        self._pixel_y_edit.setText("0")
        self._update_point_button.setEnabled(False)
        self._delete_point_button.setEnabled(False)

    def _default_point_name(self) -> str:
        return f"point_name_{self._next_point_number:02d}"

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