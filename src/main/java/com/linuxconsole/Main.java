package com.linuxconsole;

import com.linuxconsole.ui.DashboardView;
import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        new DashboardView().start(primaryStage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
