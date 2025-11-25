package org.example.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import org.example.model.Scan;
import org.example.service.AnswerKeyService;
import org.example.service.ScanService;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for the Home/Dashboard screen.
 * Displays quick action cards, recent activity, and statistics.
 */
public class HomeController implements Initializable {

    private static final int RECENT_ACTIVITY_LIMIT = 10;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    // =========================================
    // FXML Bindings
    // =========================================
    @FXML private VBox cardScan;
    @FXML private VBox cardBatch;
    @FXML private VBox cardKeys;
    @FXML private VBox cardHistory;
    @FXML private ListView<ActivityItem> activityList;

    // =========================================
    // State
    // =========================================
    private ScanService scanService;
    private AnswerKeyService answerKeyService;
    private ObservableList<ActivityItem> activities;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        scanService = new ScanService();
        answerKeyService = new AnswerKeyService();
        activities = FXCollections.observableArrayList();

        setupActivityList();
        loadRecentActivity();
    }

    // =========================================
    // Setup Methods
    // =========================================

    private void setupActivityList() {
        if (activityList == null) return;

        activityList.setItems(activities);
        activityList.setCellFactory(lv -> new ListCell<ActivityItem>() {
            @Override
            protected void updateItem(ActivityItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.toString());
                }
            }
        });

        // Allow clicking items to navigate
        activityList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                ActivityItem selected = activityList.getSelectionModel().getSelectedItem();
                if (selected != null && selected.scan != null) {
                    // Could navigate to history and select this scan
                    // For now, just show a message
                    System.out.println("View scan: " + selected.scan.getId());
                }
            }
        });
    }

    private void loadRecentActivity() {
        new Thread(() -> {
            try {
                List<Scan> recentScans = scanService.findRecent(RECENT_ACTIVITY_LIMIT);
                Platform.runLater(() -> {
                    activities.clear();
                    for (Scan scan : recentScans) {
                        activities.add(new ActivityItem(scan));
                    }
                });
            } catch (SQLException e) {
                System.err.println("Failed to load recent activity: " + e.getMessage());
            }
        }).start();
    }

    // =========================================
    // Navigation Actions
    // =========================================

    @FXML
    private void onScanClick() {
        navigateToScreen("/fxml/scan.fxml");
    }

    @FXML
    private void onBatchClick() {
        navigateToScreen("/fxml/batch.fxml");
    }

    @FXML
    private void onKeysClick() {
        navigateToScreen("/fxml/answer-key.fxml");
    }

    @FXML
    private void onHistoryClick() {
        navigateToScreen("/fxml/history.fxml");
    }

    /**
     * Navigate to a different screen by loading its FXML.
     * This works by finding the parent container and replacing content.
     */
    private void navigateToScreen(String fxmlPath) {
        try {
            // Find the parent container (content area)
            Parent root = cardScan.getScene().getRoot();
            if (root.lookup(".content-area") != null) {
                Node contentArea = root.lookup(".content-area");
                if (contentArea instanceof javafx.scene.layout.StackPane) {
                    javafx.scene.layout.StackPane stackPane = (javafx.scene.layout.StackPane) contentArea;
                    
                    // Load new content
                    Node content = FXMLLoader.load(getClass().getResource(fxmlPath));
                    stackPane.getChildren().setAll(content);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to navigate to " + fxmlPath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================
    // Inner Classes
    // =========================================

    /**
     * Represents an activity item in the recent activity list.
     */
    public static class ActivityItem {
        private final Scan scan;
        private final String displayText;

        public ActivityItem(Scan scan) {
            this.scan = scan;
            this.displayText = formatActivity(scan);
        }

        private String formatActivity(Scan scan) {
            StringBuilder sb = new StringBuilder();

            // Icon based on status
            String icon = switch (scan.getStatus()) {
                case SUCCESS -> "✓";
                case REVIEW -> "⚠";
                case FAILED -> "✗";
                default -> "○";
            };
            sb.append(icon).append(" ");

            // Filename or image path
            String filename = "scan";
            if (scan.getImagePath() != null) {
                String path = scan.getImagePath();
                int lastSep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
                if (lastSep >= 0) {
                    filename = path.substring(lastSep + 1);
                } else {
                    filename = path;
                }
            }
            sb.append(filename);

            // Student ID
            if (scan.getStudentId() != null) {
                sb.append(" - Student ").append(scan.getStudentId());
            }

            // Score
            sb.append(String.format(" - %.1f%%", scan.getScorePercentage()));

            // Time ago
            if (scan.getCreatedAt() != null) {
                String timeAgo = formatTimeAgo(scan.getCreatedAt());
                sb.append(" - ").append(timeAgo);
            }

            return sb.toString();
        }

        private String formatTimeAgo(LocalDateTime dateTime) {
            Duration duration = Duration.between(dateTime, LocalDateTime.now());
            long seconds = duration.getSeconds();

            if (seconds < 60) {
                return "just now";
            } else if (seconds < 3600) {
                long minutes = seconds / 60;
                return minutes + " min" + (minutes == 1 ? "" : "s") + " ago";
            } else if (seconds < 86400) {
                long hours = seconds / 3600;
                return hours + " hour" + (hours == 1 ? "" : "s") + " ago";
            } else {
                long days = seconds / 86400;
                return days + " day" + (days == 1 ? "" : "s") + " ago";
            }
        }

        @Override
        public String toString() {
            return displayText;
        }
    }
}
