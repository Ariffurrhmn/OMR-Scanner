package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Main controller handling navigation and content loading
 */
public class MainController implements Initializable {

    @FXML private VBox sidebar;
    @FXML private StackPane contentArea;
    @FXML private Label statusLabel;
    @FXML private Label dbStatusLabel;

    @FXML private Button btnHome;
    @FXML private Button btnScan;
    @FXML private Button btnBatch;
    @FXML private Button btnKeys;
    @FXML private Button btnHistory;

    private Button activeButton;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Check database status
        updateDbStatus();
        
        // Set initial screen to Home
        navigateToHome();
    }
    
    /**
     * Update the database connection status in the status bar
     */
    private void updateDbStatus() {
        try {
            boolean connected = org.example.service.DatabaseService.getInstance().isConnected();
            setDbStatus(connected);
        } catch (Exception e) {
            setDbStatus(false);
        }
    }

    // =========================================
    // Navigation Methods
    // =========================================

    @FXML
    private void navigateToHome() {
        loadContent("/fxml/home.fxml");
        setActiveButton(btnHome);
        updateStatus("Home");
    }

    @FXML
    private void navigateToScan() {
        loadContent("/fxml/scan.fxml");
        setActiveButton(btnScan);
        updateStatus("Single Scan");
    }

    @FXML
    private void navigateToBatch() {
        loadContent("/fxml/batch.fxml");
        setActiveButton(btnBatch);
        updateStatus("Batch Processing");
    }

    @FXML
    private void navigateToKeys() {
        loadContent("/fxml/answer-key.fxml");
        setActiveButton(btnKeys);
        updateStatus("Answer Keys");
    }

    @FXML
    private void navigateToHistory() {
        loadContent("/fxml/history.fxml");
        setActiveButton(btnHistory);
        updateStatus("Results History");
        // HistoryController will automatically load data in initialize()
        // Scans are ordered by created_at DESC, so most recent appear first
    }

    // =========================================
    // Helper Methods
    // =========================================

    /**
     * Loads an FXML file into the content area
     */
    private void loadContent(String fxmlPath) {
        try {
            URL resource = getClass().getResource(fxmlPath);
            if (resource == null) {
                System.err.println("FXML resource not found: " + fxmlPath);
                showPlaceholder(fxmlPath + " (Resource not found)");
                return;
            }
            Node content = FXMLLoader.load(resource);
            contentArea.getChildren().setAll(content);
        } catch (Exception e) {
            System.err.println("Error loading FXML: " + fxmlPath);
            e.printStackTrace();
            showPlaceholder(fxmlPath + "\n\nError: " + e.getMessage());
        }
    }

    /**
     * Shows a placeholder when FXML is not yet implemented
     */
    private void showPlaceholder(String screenName) {
        Label placeholder = new Label("Screen: " + screenName + "\n\n(Not yet implemented)");
        placeholder.setStyle("-fx-font-size: 18px; -fx-text-fill: #71717A;");
        contentArea.getChildren().setAll(placeholder);
    }

    /**
     * Updates the active navigation button styling
     */
    private void setActiveButton(Button button) {
        // Remove active class from previous button
        if (activeButton != null) {
            activeButton.getStyleClass().remove("nav-button-active");
        }
        // Add active class to new button
        if (button != null) {
            button.getStyleClass().add("nav-button-active");
            activeButton = button;
        }
    }

    /**
     * Updates the status bar
     */
    private void updateStatus(String screen) {
        if (statusLabel != null) {
            statusLabel.setText("Current: " + screen);
        }
    }

    /**
     * Sets the database status in status bar
     */
    public void setDbStatus(boolean connected) {
        if (dbStatusLabel != null) {
            dbStatusLabel.setText(connected ? "DB: Connected" : "DB: Disconnected");
            dbStatusLabel.setStyle(connected ? "-fx-text-fill: #16A34A;" : "-fx-text-fill: #EF4444;");
        }
    }
}

