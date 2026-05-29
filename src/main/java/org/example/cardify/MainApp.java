package org.example.cardify;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.example.cardify.controller.MainController;

import javax.imageio.ImageIO;
import java.awt.Taskbar;
import java.io.InputStream;

public class MainApp extends Application {
    @Override
    public void start(Stage stage) {
        MainController controller = new MainController(stage);
        Scene scene = new Scene(controller.getRoot(), 1480, 920);
        scene.getStylesheets().add(getClass().getResource("/org/example/cardify/styles/app.css").toExternalForm());

        stage.setTitle("Cardify - HTML to Excel to ID Card Printer");

        // Load JavaFX window icon and attempt to set the native taskbar icon on supported platforms
        InputStream iconStream = getClass().getResourceAsStream("/org/example/cardify/icons/app-icon.png");
        if (iconStream != null) {
            try {
                Image fxIcon = new Image(iconStream);
                stage.getIcons().add(fxIcon);
            } catch (Exception ignored) {
            }

            try (InputStream awtStream = getClass().getResourceAsStream("/org/example/cardify/icons/app-icon.png")) {
                if (awtStream != null) {
                    try {
                        java.awt.Image awtImg = ImageIO.read(awtStream);
                        try {
                            Taskbar taskbar = Taskbar.getTaskbar();
                            taskbar.setIconImage(awtImg);
                        } catch (Throwable ignore) {
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        }

        stage.setScene(scene);
        stage.show();

        controller.initialize();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
