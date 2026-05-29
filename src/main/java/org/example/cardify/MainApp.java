package org.example.cardify;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.cardify.controller.MainController;

public class MainApp extends Application {
    @Override
    public void start(Stage stage) {
        MainController controller = new MainController(stage);
        Scene scene = new Scene(controller.getRoot(), 1480, 920);
        scene.getStylesheets().add(getClass().getResource("/org/example/cardify/styles/app.css").toExternalForm());

        stage.setTitle("Cardify - HTML to Excel to ID Card Printer");
        stage.setScene(scene);
        stage.show();

        controller.initialize();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
