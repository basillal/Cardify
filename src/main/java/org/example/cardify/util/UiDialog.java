package org.example.cardify.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

public final class UiDialog {
    private UiDialog() {
    }

    public static void info(Window owner, String title, String message) {
        show(owner, Alert.AlertType.INFORMATION, title, message);
    }

    public static void warn(Window owner, String title, String message) {
        show(owner, Alert.AlertType.WARNING, title, message);
    }

    public static void error(Window owner, String title, String message) {
        show(owner, Alert.AlertType.ERROR, title, message);
    }

    public static boolean confirm(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.CANCEL);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        return alert.showAndWait().filter(ButtonType.YES::equals).isPresent();
    }

    public static void showDiagnostics(Window owner, String logContent) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.setTitle("Print Diagnostics");
        alert.setHeaderText("Recent print log");

        javafx.scene.control.TextArea area = new javafx.scene.control.TextArea(logContent == null ? "(no log found)" : logContent);
        area.setEditable(false);
        area.setWrapText(false);
        area.setPrefWidth(780);
        area.setPrefHeight(420);

        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(area);
        alert.getDialogPane().setContent(box);
        alert.showAndWait();
    }

    private static void show(Window owner, Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
