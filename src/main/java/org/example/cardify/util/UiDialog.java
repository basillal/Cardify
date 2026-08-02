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
        styleDialog(alert, owner);
        return alert.showAndWait().filter(ButtonType.YES::equals).isPresent();
    }

    public static void showDiagnostics(Window owner, String logContent) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.setTitle("Print Diagnostics");
        alert.setHeaderText("Recent print log");
        styleDialog(alert, owner);

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
        styleDialog(alert, owner);
        alert.showAndWait();
    }

    private static void styleDialog(Alert alert, Window owner) {
        if (owner != null && owner.getScene() != null) {
            alert.getDialogPane().getStylesheets().setAll(owner.getScene().getStylesheets());
            boolean isLight = owner.getScene().getRoot().getStyleClass().contains("light-theme");
            alert.getDialogPane().getStyleClass().removeAll("dark-theme", "light-theme");
            alert.getDialogPane().getStyleClass().add(isLight ? "light-theme" : "dark-theme");
        } else {
            try {
                String css = UiDialog.class.getResource("/org/example/cardify/styles/app.css").toExternalForm();
                alert.getDialogPane().getStylesheets().add(css);
                alert.getDialogPane().getStyleClass().add("dark-theme");
            } catch (Exception ignored) {
            }
        }
    }
}
