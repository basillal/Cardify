package org.example;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;


public class Main extends Application {
    public static void main(String[] args) {
        launch();
    }

    @Override
    public void start(Stage stage) throws Exception {
        Button btn = new Button("Click Me");

        btn.setOnAction(e->{

            System.out.println("Hello JavaFX!");

        });

        Scene scene = new Scene(btn, 400, 300);
        stage.setTitle("ID Card Generator");
        stage.setScene(scene);
        stage.show();

    }
}