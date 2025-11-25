package org.example.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.example.model.AnswerKey;
import org.example.model.Scan;
import org.example.service.AnswerKeyService;
import org.example.service.ExportService;
import org.example.service.ScanService;

import java.io.File;
import java.net.URL;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Controller for the Batch Processing screen.
 * Handles processing multiple OMR sheets from a folder.
 */
public class BatchController implements Initializable {

    private static final String[] IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".tif"};

    // =========================================
    // FXML Bindings - Source
    // =========================================
    @FXML private TextField txtSourceFolder;
    @FXML private Button btnBrowse;
    @FXML private ComboBox<AnswerKey> cmbAnswerKey;

    // =========================================
    // FXML Bindings - Table
    // =========================================
    @FXML private TableView<BatchItem> tblFiles;
    @FXML private TableColumn<BatchItem, Boolean> colCheckbox;
    @FXML private TableColumn<BatchItem, String> colStatus;
    @FXML private TableColumn<BatchItem, String> colFilename;
    @FXML private TableColumn<BatchItem, String> colStudentId;
    @FXML private TableColumn<BatchItem, String> colTestId;
    @FXML private TableColumn<BatchItem, String> colScore;
    @FXML private TableColumn<BatchItem, String> colIssues;

    // =========================================
    // FXML Bindings - Progress & Stats
    // =========================================
    @FXML private ProgressBar progressBar;
    @FXML private Label lblProgress;
    @FXML private Label lblElapsed;
    @FXML private Label lblRemaining;
    @FXML private Label lblSuccessful;
    @FXML private Label lblReview;
    @FXML private Label lblFailed;
    @FXML private Label lblPending;

    // =========================================
    // FXML Bindings - Actions
    // =========================================
    @FXML private Button btnStart;
    @FXML private Button btnPause;
    @FXML private Button btnStop;
    @FXML private Button btnReviewIssues;
    @FXML private Button btnExportAll;

    // =========================================
    // State
    // =========================================
    private ScanService scanService;
    private AnswerKeyService answerKeyService;
    private ExportService exportService;
    private ObservableList<BatchItem> batchItems;
    private ObservableList<AnswerKey> answerKeys;
    private File sourceFolder;
    private Task<Void> processingTask;
    private boolean isPaused = false;
    private LocalDateTime batchStartTime;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        scanService = new ScanService();
        answerKeyService = new AnswerKeyService();
        exportService = new ExportService();
        batchItems = FXCollections.observableArrayList();
        answerKeys = FXCollections.observableArrayList();

        setupTable();
        setupAnswerKeyCombo();
        loadAnswerKeys();
        updateButtonStates();
    }

    // =========================================
    // Setup Methods
    // =========================================

    private void setupTable() {
        if (tblFiles == null) return;

        tblFiles.setItems(batchItems);

        // Checkbox column
        if (colCheckbox != null) {
            colCheckbox.setCellValueFactory(data -> data.getValue().selectedProperty());
            colCheckbox.setCellFactory(CheckBoxTableCell.forTableColumn(colCheckbox));
        }

        // Status column with icon
        if (colStatus != null) {
            colStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatusIcon()));
            colStatus.setCellFactory(column -> new TableCell<BatchItem, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item);
                        BatchItem batchItem = getTableView().getItems().get(getIndex());
                        setStyle(batchItem.getStatusColor());
                    }
                }
            });
        }

        // Other columns
        if (colFilename != null) {
            colFilename.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().file.getName()));
        }
        if (colStudentId != null) {
            colStudentId.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().scan != null ? data.getValue().scan.getStudentId() : ""));
        }
        if (colTestId != null) {
            colTestId.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().scan != null ? data.getValue().scan.getTestId() : ""));
        }
        if (colScore != null) {
            colScore.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().scan != null ?
                    String.format("%.1f%%", data.getValue().scan.getScorePercentage()) : ""));
        }
        if (colIssues != null) {
            colIssues.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().issues));
        }
    }

    private void setupAnswerKeyCombo() {
        if (cmbAnswerKey == null) return;

        cmbAnswerKey.setItems(answerKeys);
        cmbAnswerKey.setCellFactory(lv -> new ListCell<AnswerKey>() {
            @Override
            protected void updateItem(AnswerKey key, boolean empty) {
                super.updateItem(key, empty);
                setText(empty || key == null ? null : key.getName() + " (" + key.getTestId() + ")");
            }
        });
        cmbAnswerKey.setButtonCell(new ListCell<AnswerKey>() {
            @Override
            protected void updateItem(AnswerKey key, boolean empty) {
                super.updateItem(key, empty);
                setText(empty || key == null ? "Auto-detect by Test ID" : key.getName());
            }
        });
    }

    private void loadAnswerKeys() {
        try {
            List<AnswerKey> keys = answerKeyService.findAll();
            answerKeys.setAll(keys);
        } catch (SQLException e) {
            System.err.println("Failed to load answer keys: " + e.getMessage());
        }
    }

    // =========================================
    // Actions
    // =========================================

    @FXML
    private void browseFolder() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Folder with OMR Sheets");
        
        File folder = dirChooser.showDialog(null);
        if (folder != null && folder.isDirectory()) {
            sourceFolder = folder;
            txtSourceFolder.setText(folder.getAbsolutePath());
            loadFiles();
        }
    }

    private void loadFiles() {
        if (sourceFolder == null || !sourceFolder.isDirectory()) return;

        batchItems.clear();
        File[] files = sourceFolder.listFiles();
        
        if (files != null) {
            for (File file : files) {
                if (isImageFile(file)) {
                    batchItems.add(new BatchItem(file));
                }
            }
        }

        updateStats();
        updateButtonStates();
    }

    private boolean isImageFile(File file) {
        if (file == null || !file.isFile()) return false;
        String name = file.getName().toLowerCase();
        return Arrays.stream(IMAGE_EXTENSIONS).anyMatch(name::endsWith);
    }

    @FXML
    private void startBatch() {
        if (batchItems.isEmpty()) {
            showWarning("No Files", "Please select a folder with image files.");
            return;
        }

        isPaused = false;
        batchStartTime = LocalDateTime.now();
        
        // Get default answer key (if selected)
        AnswerKey defaultKey = cmbAnswerKey.getValue();

        // Create processing task
        processingTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                int total = batchItems.size();
                int processed = 0;

                for (BatchItem item : batchItems) {
                    // Check if cancelled or paused
                    if (isCancelled()) break;
                    while (isPaused && !isCancelled()) {
                        Thread.sleep(100);
                    }

                    // Skip if already processed
                    if (item.status != BatchStatus.PENDING) {
                        processed++;
                        continue;
                    }

                    // Update status
                    updateItemStatus(item, BatchStatus.PROCESSING, "Processing...");

                    try {
                        // Process the image
                        Scan scan = scanService.processImage(item.file, defaultKey, true);
                        item.scan = scan;

                        // Determine status
                        if (scan.needsReview()) {
                            updateItemStatus(item, BatchStatus.REVIEW, "Needs review");
                        } else {
                            updateItemStatus(item, BatchStatus.SUCCESS, "");
                        }

                    } catch (Exception e) {
                        updateItemStatus(item, BatchStatus.FAILED, e.getMessage());
                    }

                    processed++;
                    final int currentProcessed = processed;
                    
                    // Update progress
                    Platform.runLater(() -> {
                        double progress = (double) currentProcessed / total;
                        progressBar.setProgress(progress);
                        lblProgress.setText(String.format("%.0f%% (%d/%d files)",
                            progress * 100, currentProcessed, total));
                        updateStats();
                        updateElapsedTime();
                    });
                }

                return null;
            }
        };

        // Handle completion
        processingTask.setOnSucceeded(e -> onBatchComplete());
        processingTask.setOnFailed(e -> onBatchFailed());
        processingTask.setOnCancelled(e -> onBatchCancelled());

        // Start task in background
        Thread thread = new Thread(processingTask);
        thread.setDaemon(true);
        thread.start();

        updateButtonStates();
    }

    @FXML
    private void pauseBatch() {
        isPaused = !isPaused;
        if (btnPause != null) {
            btnPause.setText(isPaused ? "▶ Resume" : "⏸ Pause");
        }
    }

    @FXML
    private void stopBatch() {
        if (processingTask != null) {
            processingTask.cancel();
        }
        updateButtonStates();
    }

    @FXML
    private void reviewIssues() {
        // Filter table to show only items with issues
        ObservableList<BatchItem> issues = batchItems.stream()
            .filter(item -> item.status == BatchStatus.REVIEW || item.status == BatchStatus.FAILED)
            .collect(Collectors.toCollection(FXCollections::observableArrayList));

        if (issues.isEmpty()) {
            showInfo("No Issues", "All scans completed successfully.");
        } else {
            // Temporarily show only issues
            tblFiles.setItems(issues);
        }
    }

    @FXML
    private void exportAll() {
        List<Scan> scansToExport = batchItems.stream()
            .filter(item -> item.scan != null)
            .map(item -> item.scan)
            .collect(Collectors.toList());

        if (scansToExport.isEmpty()) {
            showWarning("No Results", "Process some files first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Batch Results");
        fileChooser.setInitialFileName("batch_results.csv");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                exportService.exportScans(scansToExport, file);
                showInfo("Export Successful",
                    String.format("Exported %d results to %s", scansToExport.size(), file.getName()));
            } catch (Exception e) {
                showError("Export Failed", e.getMessage());
            }
        }
    }

    // =========================================
    // Helper Methods
    // =========================================

    private void updateItemStatus(BatchItem item, BatchStatus status, String issues) {
        Platform.runLater(() -> {
            item.status = status;
            item.issues = issues;
            tblFiles.refresh();
        });
    }

    private void updateStats() {
        int successful = (int) batchItems.stream().filter(i -> i.status == BatchStatus.SUCCESS).count();
        int review = (int) batchItems.stream().filter(i -> i.status == BatchStatus.REVIEW).count();
        int failed = (int) batchItems.stream().filter(i -> i.status == BatchStatus.FAILED).count();
        int pending = (int) batchItems.stream().filter(i -> i.status == BatchStatus.PENDING).count();

        if (lblSuccessful != null) lblSuccessful.setText(String.valueOf(successful));
        if (lblReview != null) lblReview.setText(String.valueOf(review));
        if (lblFailed != null) lblFailed.setText(String.valueOf(failed));
        if (lblPending != null) lblPending.setText(String.valueOf(pending));
    }

    private void updateElapsedTime() {
        if (batchStartTime != null && lblElapsed != null) {
            Duration elapsed = Duration.between(batchStartTime, LocalDateTime.now());
            lblElapsed.setText(String.format("Elapsed: %02d:%02d:%02d",
                elapsed.toHours(), elapsed.toMinutesPart(), elapsed.toSecondsPart()));
        }
    }

    private void updateButtonStates() {
        boolean hasFiles = !batchItems.isEmpty();
        boolean isRunning = processingTask != null && processingTask.isRunning();

        if (btnStart != null) btnStart.setDisable(!hasFiles || isRunning);
        if (btnPause != null) btnPause.setDisable(!isRunning);
        if (btnStop != null) btnStop.setDisable(!isRunning);
    }

    private void onBatchComplete() {
        showInfo("Batch Complete", "All files have been processed.");
        updateButtonStates();
    }

    private void onBatchFailed() {
        showError("Batch Failed", "An error occurred during batch processing.");
        updateButtonStates();
    }

    private void onBatchCancelled() {
        showInfo("Batch Stopped", "Batch processing was cancelled.");
        updateButtonStates();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // =========================================
    // Inner Classes
    // =========================================

    public enum BatchStatus {
        PENDING, PROCESSING, SUCCESS, REVIEW, FAILED
    }

    public static class BatchItem {
        public final File file;
        public final SimpleBooleanProperty selected;
        public BatchStatus status;
        public Scan scan;
        public String issues;

        public BatchItem(File file) {
            this.file = file;
            this.selected = new SimpleBooleanProperty(false);
            this.status = BatchStatus.PENDING;
            this.issues = "";
        }

        public SimpleBooleanProperty selectedProperty() {
            return selected;
        }

        public String getStatusIcon() {
            return switch (status) {
                case SUCCESS -> "✓";
                case REVIEW -> "⚠";
                case FAILED -> "✗";
                case PROCESSING -> "⏳";
                case PENDING -> "○";
            };
        }

        public String getStatusColor() {
            return switch (status) {
                case SUCCESS -> "-fx-text-fill: #16A34A;";
                case REVIEW -> "-fx-text-fill: #F59E0B;";
                case FAILED -> "-fx-text-fill: #EF4444;";
                case PROCESSING -> "-fx-text-fill: #3B82F6;";
                case PENDING -> "-fx-text-fill: #71717A;";
            };
        }
    }
}

