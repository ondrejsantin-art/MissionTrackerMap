import os
import keyring
from PySide6.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout, QLabel, 
    QLineEdit, QPushButton, QMessageBox
)
from PySide6.QtCore import Qt, QThread, Signal, QSettings
from app.supabase_client import SupabaseClient, SupabaseAuthError, SupabaseRequestError


class PublishWorker(QThread):
    finished_signal = Signal(bool, str)

    def __init__(self, email, password, mission_id, image_path, calibration_json, parent=None):
        super().__init__(parent)
        self.email = email
        self.password = password
        self.mission_id = mission_id
        self.image_path = image_path
        self.calibration_json = calibration_json

    def run(self):
        client = SupabaseClient()
        try:
            client.login(self.email, self.password)
            image_name = os.path.basename(self.image_path)
            
            client.upload_image(self.mission_id, image_name, self.image_path)
            client.publish_mission(self.mission_id, self.calibration_json, version=1)
            
            self.finished_signal.emit(True, "Successfully published mission to cloud.")
        except SupabaseAuthError as e:
            self.finished_signal.emit(False, str(e))
        except SupabaseRequestError as e:
            self.finished_signal.emit(False, str(e))
        except Exception as e:
            self.finished_signal.emit(False, f"Unexpected error: {str(e)}")


class PublishDialog(QDialog):
    def __init__(self, image_path: str, calibration_json: str, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Publish to Cloud")
        self.setMinimumWidth(400)
        
        self.image_path = image_path
        self.calibration_json = calibration_json

        layout = QVBoxLayout(self)

        # Mission ID
        layout.addWidget(QLabel("Mission ID (Unique Name):"))
        self.mission_id_edit = QLineEdit()
        default_id = os.path.splitext(os.path.basename(self.image_path))[0]
        self.mission_id_edit.setText(default_id)
        layout.addWidget(self.mission_id_edit)

        # Email
        layout.addWidget(QLabel("Supabase Email:"))
        self.email_edit = QLineEdit()
        layout.addWidget(self.email_edit)

        # Password
        layout.addWidget(QLabel("Supabase Password:"))
        self.password_edit = QLineEdit()
        self.password_edit.setEchoMode(QLineEdit.EchoMode.Password)
        layout.addWidget(self.password_edit)

        self.status_label = QLabel("")
        self.status_label.setWordWrap(True)
        self.status_label.setStyleSheet("color: #555;")
        layout.addWidget(self.status_label)

        # Buttons
        btn_layout = QHBoxLayout()
        self.publish_btn = QPushButton("Publish")
        self.publish_btn.clicked.connect(self.on_publish_clicked)
        self.cancel_btn = QPushButton("Cancel")
        self.cancel_btn.clicked.connect(self.reject)
        
        btn_layout.addStretch()
        btn_layout.addWidget(self.cancel_btn)
        btn_layout.addWidget(self.publish_btn)
        layout.addLayout(btn_layout)

        self.worker = None
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

    def on_publish_clicked(self):
        email = self.email_edit.text().strip()
        password = self.password_edit.text().strip()
        mission_id = self.mission_id_edit.text().strip()

        if not email or not password or not mission_id:
            QMessageBox.warning(self, "Validation Error", "All fields are required.")
            return

        self.publish_btn.setEnabled(False)
        self.cancel_btn.setEnabled(False)
        self.status_label.setText("Publishing... Please wait.")

        self.worker = PublishWorker(email, password, mission_id, self.image_path, self.calibration_json)
        self.worker.finished_signal.connect(self.on_publish_finished)
        self.worker.start()

    def on_publish_finished(self, success: bool, message: str):
        self.publish_btn.setEnabled(True)
        self.cancel_btn.setEnabled(True)
        
        if success:
            self._save_credentials(self.email_edit.text().strip(), self.password_edit.text().strip())
            QMessageBox.information(self, "Success", message)
            self.accept()
        else:
            self.status_label.setText(f"Error: {message}")
            self.status_label.setStyleSheet("color: red;")
