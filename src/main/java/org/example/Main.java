package org.example;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.controller.TSPController;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        TSPController controller = new TSPController();

        Scene scene = new Scene(controller.getRoot(), 1100, 650);
        stage.setTitle("TSP Visualiser");
        stage.setScene(scene);
        stage.show();

        controller.init(); // generate first set of points
    }

    public static void main(String[] args) {
        launch();
    }
}