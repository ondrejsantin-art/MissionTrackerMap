import os
import json
import keyring
from PySide6.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout, QLabel, 
    QLineEdit, QPushButton, QMessageBox, QListWidget, QFileDialog
)
from PySide6.QtCore import Qt, QThread, Signal, QSettings
from app.supabase_client import SupabaseClient, SupabaseAuthError, SupabaseRequestError


class FetchMissionsWorker(QThread):
    finished_signal = Signal(bool, list, str)

    def __init__(self, email, password, parent=None):
        super().__init__(parent)
        self.email = email
        self.password = password
        self.client = SupabaseClient()

    def run(self):
        try:
            self.client.login(self.email, self.password)
            missions = self.client.fetch_missions()
            self.finished_signal.emit(True, missions, "")
        except Exception as e:
            self.finished_signal.emit(False, [], str(e))


class DownloadMissionWorker(QThread):
    finished_signal = Signal(bool, str, str)

    def __init__(self, client, mission_id, target_dir, parent=None):
        super().__init__(parent)
        self.client = client
        self.mission_id = mission_id
        self.target_dir = target_dir

    def run(self):
        try:
            detail = self.client.fetch_mission_detail(self.mission_id)
            json_data = detail.get("json_data", {})
            image_name = json_data.get("image")
            if not image_name:
                raise ValueError("JSON data missing 'image' field")

            # Download Image
            image_path = os.path.join(self.target_dir, image_name)
            self.client.download_image(self.mission_id, image_name, image_path)

            # Save JSON
            json_path = os.path.join(self.target_dir, f"{self.mission_id}.json")
            with open(json_path, "w", encoding="utf-8") as f:
                json.dump(json_data, f, indent=4)

            self.finished_signal.emit(True, json_path, "")
        except Exception as e:
            self.finished_signal.emit(False, "", str(e))


class OpenCloudDialog(QDialog):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Open Mission from Cloud")
        self.setMinimumWidth(400)
        self.downloaded_json_path = None

        layout = QVBoxLayout(self)

        # Email
        layout.addWidget(QLabel("Supabase Email:"))
        self.email_edit = QLineEdit()
        layout.addWidget(self.email_edit)

        # Password
        layout.addWidget(QLabel("Supabase Password:"))
        self.password_edit = QLineEdit()
        self.password_edit.setEchoMode(QLineEdit.EchoMode.Password)
        layout.addWidget(self.password_edit)

        # Connect button
        self.connect_btn = QPushButton("Connect and Fetch Missions")
        self.connect_btn.clicked.connect(self.on_connect_clicked)
        layout.addWidget(self.connect_btn)

        # Mission List
        layout.addWidget(QLabel("Available Missions:"))
        self.mission_list = QListWidget()
        layout.addWidget(self.mission_list)

        self.status_label = QLabel("")
        self.status_label.setWordWrap(True)
        self.status_label.setStyleSheet("color: #555;")
        layout.addWidget(self.status_label)

        # Buttons
        btn_layout = QHBoxLayout()
        self.open_btn = QPushButton("Download and Open")
        self.open_btn.setEnabled(False)
        self.open_btn.clicked.connect(self.on_open_clicked)
        self.cancel_btn = QPushButton("Cancel")
        self.cancel_btn.clicked.connect(self.reject)
        
        btn_layout.addStretch()
        btn_layout.addWidget(self.cancel_btn)
        btn_layout.addWidget(self.open_btn)
        layout.addLayout(btn_layout)

        self.fetch_worker = None
        self.download_worker = None
        self.client = None
        self._load_credentials()

    def _load_credentials(self):
        settings = QSettings("MissionTracker", "CalibrationEditor")
        saved_email = settings.value("supabase_email", "")
        if saved_email:
            self.email_edit.setText(saved_email)
            try:
                saved_pw = keyring.get_password("MissionTracker_Supabase", saved_email)
                if saved_pw:
                    self.password_edit.setText(saved_pw)
            except Exception:
                pass

    def _save_credentials(self, email, password):
        settings = QSettings("MissionTracker", "CalibrationEditor")
        settings.setValue("supabase_email", email)
        try:
            keyring.set_password("MissionTracker_Supabase", email, password)
        except Exception:
            pass

    def on_connect_clicked(self):
        email = self.email_edit.text().strip()
        password = self.password_edit.text().strip()

        if not email or not password:
            QMessageBox.warning(self, "Validation Error", "Email and Password are required.")
            return

        self.connect_btn.setEnabled(False)
        self.status_label.setText("Connecting...")
        self.status_label.setStyleSheet("color: #555;")
        self.mission_list.clear()
        
        self.fetch_worker = FetchMissionsWorker(email, password)
        self.fetch_worker.finished_signal.connect(self.on_fetch_finished)
        self.fetch_worker.start()

    def on_fetch_finished(self, success, missions, error_msg):
        self.connect_btn.setEnabled(True)
        if success:
            self._save_credentials(self.email_edit.text().strip(), self.password_edit.text().strip())
            self.client = self.fetch_worker.client
            self.status_label.setText(f"Found {len(missions)} missions.")
            for m in missions:
                self.mission_list.addItem(m.get("id", "Unknown"))
            self.open_btn.setEnabled(len(missions) > 0)
        else:
            self.status_label.setText(f"Error: {error_msg}")
            self.status_label.setStyleSheet("color: red;")

    def on_open_clicked(self):
        selected = self.mission_list.selectedItems()
        if not selected:
            QMessageBox.warning(self, "Selection Error", "Please select a mission.")
            return

        mission_id = selected[0].text()

        target_dir = QFileDialog.getExistingDirectory(self, "Select Folder to Save Mission")
        if not target_dir:
            return

        self.open_btn.setEnabled(False)
        self.connect_btn.setEnabled(False)
        self.status_label.setText(f"Downloading {mission_id}...")
        
        self.download_worker = DownloadMissionWorker(self.client, mission_id, target_dir)
        self.download_worker.finished_signal.connect(self.on_download_finished)
        self.download_worker.start()

    def on_download_finished(self, success, json_path, error_msg):
        self.open_btn.setEnabled(True)
        self.connect_btn.setEnabled(True)
        if success:
            self.downloaded_json_path = json_path
            self.accept()
        else:
            self.status_label.setText(f"Download Error: {error_msg}")
            self.status_label.setStyleSheet("color: red;")
