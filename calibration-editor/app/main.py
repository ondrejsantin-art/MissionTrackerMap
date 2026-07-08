import sys
import os

from PySide6.QtWidgets import QApplication
from PySide6.QtCore import QTimer

from app.main_window import MainWindow


def main():

    app = QApplication(sys.argv)

    window = MainWindow()

    window.resize(1200, 800)

    window.show()

    if os.environ.get("CI") == "true":
        print("Running in CI environment, scheduling auto-exit...")
        QTimer.singleShot(1000, app.quit)

    sys.exit(app.exec())


if __name__ == "__main__":
    main()