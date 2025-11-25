package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.service.DatabaseService;

/**
 * OMR Reader V2 - Main Application
 * Entry point for the JavaFX application
 */
public class Main extends Application {

    private static final String APP_TITLE = "OMR Reader V2";
    private static final int WINDOW_WIDTH = 1200;
    private static final int WINDOW_HEIGHT = 800;

    @Override
    public void init() throws Exception {
        // Initialize database before UI loads
        System.out.println("Initializing database...");
        boolean dbInitialized = DatabaseService.getInstance().initialize();
        if (!dbInitialized) {
            System.err.println("Warning: Database initialization failed. Some features may not work.");
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Load the main FXML layout
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();

        // Create the scene with stylesheet
        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

        // Configure the stage
        primaryStage.setTitle(APP_TITLE);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        // Clean up database connection
        System.out.println("Closing database connection...");
        DatabaseService.getInstance().close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
