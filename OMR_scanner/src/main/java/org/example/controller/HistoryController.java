package org.example.controller;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.FileChooser;
import org.example.model.AnswerKey;
import org.example.model.Scan;
import org.example.service.AnswerKeyService;
import org.example.service.ExportService;
import org.example.service.ScanService;

import java.io.File;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Controller for the Results History screen.
 * Handles browsing, filtering, and managing past scan results.
 */
public class HistoryController implements Initializable {

    private static final int PAGE_SIZE = 50;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // =========================================
    // FXML Bindings - Filters
    // =========================================
    @FXML private DatePicker dateFrom;
    @FXML private DatePicker dateTo;
    @FXML private ComboBox<String> cmbTestId;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private TextField txtStudentId;
    @FXML private Button btnSearch;
    @FXML private Button btnClear;

    // =========================================
    // FXML Bindings - Table
    // =========================================
    @FXML private TableView<ScanRow> tblResults;
    @FXML private TableColumn<ScanRow, Boolean> colCheckbox;
    @FXML private TableColumn<ScanRow, String> colDate;
    @FXML private TableColumn<ScanRow, String> colStudentId;
    @FXML private TableColumn<ScanRow, String> colTestId;
    @FXML private TableColumn<ScanRow, String> colAnswerKey;
    @FXML private TableColumn<ScanRow, String> colScore;
    @FXML private TableColumn<ScanRow, String> colStatus;

    // =========================================
    // FXML Bindings - Pagination & Stats
    // =========================================
    @FXML private Button btnPrev;
    @FXML private Button btnNext;
    @FXML private Label lblPageInfo;
    @FXML private Label lblResultInfo;
    @FXML private Label lblTotalScans;
    @FXML private Label lblAvgScore;
    @FXML private Label lblHighScore;
    @FXML private Label lblLowScore;

    // =========================================
    // FXML Bindings - Actions
    // =========================================
    @FXML private Button btnViewDetails;
    @FXML private Button btnDelete;
    @FXML private Button btnExport;

    // =========================================
    // State
    // =========================================
    private ScanService scanService;
    private AnswerKeyService answerKeyService;
    private ExportService exportService;
    private ObservableList<ScanRow> scanRows;
    private List<Scan> allScans;
    private int currentPage = 0;
    private int totalPages = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        scanService = new ScanService();
        answerKeyService = new AnswerKeyService();
        exportService = new ExportService();
        scanRows = FXCollections.observableArrayList();
        allScans = new ArrayList<>();

        setupTable();
        setupFilters();
        loadData();
        
        // Refresh data when tab becomes visible (if needed)
        // This ensures recent scans appear immediately
    }
    
    /**
     * Public method to refresh data - can be called from MainController if needed
     */
    public void refresh() {
        loadData();
    }

    // =========================================
    // Setup Methods
    // =========================================

    private void setupTable() {
        if (tblResults == null) return;

        tblResults.setItems(scanRows);
        
        // Make table editable for checkbox column
        tblResults.setEditable(true);

        // Checkbox column
        if (colCheckbox != null) {
            colCheckbox.setEditable(true);
            colCheckbox.setCellValueFactory(data -> data.getValue().selectedProperty());
            colCheckbox.setCellFactory(CheckBoxTableCell.forTableColumn(colCheckbox));
        }

        // Date column
        if (colDate != null) {
            colDate.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().scan.getCreatedAt() != null ?
                    data.getValue().scan.getCreatedAt().format(DATE_FORMAT) : ""));
        }

        // Student ID column
        if (colStudentId != null) {
            colStudentId.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().scan.getStudentId() != null ?
                    data.getValue().scan.getStudentId() : "N/A"));
        }

        // Test ID column
        if (colTestId != null) {
            colTestId.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().scan.getTestId() != null ?
                    data.getValue().scan.getTestId() : "N/A"));
        }

        // Answer Key column
        if (colAnswerKey != null) {
            colAnswerKey.setCellValueFactory(data -> {
                Scan scan = data.getValue().scan;
                String keyName = "N/A";
                if (scan.getAnswerKey() != null) {
                    keyName = scan.getAnswerKey().getName();
                } else if (scan.getAnswerKeyId() != null) {
                    try {
                        Optional<AnswerKey> key = answerKeyService.findById(scan.getAnswerKeyId());
                        if (key.isPresent()) {
                            keyName = key.get().getName();
                        }
                    } catch (SQLException e) {
                        // Ignore
                    }
                }
                return new SimpleStringProperty(keyName);
            });
        }

        // Score column
        if (colScore != null) {
            colScore.setCellValueFactory(data -> new SimpleStringProperty(
                String.format("%.1f%%", data.getValue().scan.getScorePercentage())));
        }

        // Status column with color
        if (colStatus != null) {
            colStatus.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().scan.getStatus().getValue()));
            colStatus.setCellFactory(column -> new TableCell<ScanRow, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item);
                        switch (item.toLowerCase()) {
                            case "success":
                                setStyle("-fx-text-fill: #16A34A;");
                                break;
                            case "review":
                                setStyle("-fx-text-fill: #F59E0B;");
                                break;
                            case "failed":
                                setStyle("-fx-text-fill: #EF4444;");
                                break;
                            default:
                                setStyle("-fx-text-fill: #71717A;");
                        }
                    }
                }
            });
        }

        // Double-click to view details
        tblResults.setRowFactory(tv -> {
            TableRow<ScanRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                // Don't interfere with checkbox clicks
                if (event.getTarget() instanceof CheckBox) {
                    return;
                }
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    viewDetails();
                }
            });
            return row;
        });
    }

    private void setupFilters() {
        // Test ID filter
        if (cmbTestId != null) {
            cmbTestId.getItems().add("All");
            try {
                List<String> testIds = scanService.findAll().stream()
                    .map(Scan::getTestId)
                    .filter(id -> id != null && !id.isEmpty())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
                cmbTestId.getItems().addAll(testIds);
                cmbTestId.setValue("All");
            } catch (SQLException e) {
                System.err.println("Failed to load test IDs: " + e.getMessage());
            }
        }

        // Status filter
        if (cmbStatus != null) {
            cmbStatus.getItems().addAll("All", "success", "review", "failed", "pending");
            cmbStatus.setValue("All");
        }
    }

    // =========================================
    // Data Loading
    // =========================================

    private void loadData() {
        try {
            allScans = scanService.findAll();
            currentPage = 0;
            updateDisplay();
            updateStatistics();
        } catch (SQLException e) {
            showError("Failed to load data", e.getMessage());
        }
    }

    private void updateDisplay() {
        // Calculate pagination
        totalPages = (int) Math.ceil((double) allScans.size() / PAGE_SIZE);
        if (totalPages == 0) totalPages = 1;

        // Get current page scans
        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, allScans.size());
        List<Scan> pageScans = allScans.subList(start, end);

        // Update table
        scanRows.clear();
        for (Scan scan : pageScans) {
            scanRows.add(new ScanRow(scan));
        }

        // Update pagination labels
        if (lblPageInfo != null) {
            lblPageInfo.setText(String.format("Page %d of %d", currentPage + 1, totalPages));
        }
        if (lblResultInfo != null) {
            lblResultInfo.setText(String.format("Showing %d-%d of %d results",
                start + 1, end, allScans.size()));
        }

        // Update button states
        if (btnPrev != null) btnPrev.setDisable(currentPage == 0);
        if (btnNext != null) btnNext.setDisable(currentPage >= totalPages - 1);
    }

    private void updateStatistics() {
        try {
            ScanService.ScanStatistics stats = scanService.getStatistics();
            if (lblTotalScans != null) lblTotalScans.setText(String.valueOf(stats.totalScans));
            if (lblAvgScore != null) lblAvgScore.setText(stats.getAverageDisplay());
            if (lblHighScore != null) lblHighScore.setText(stats.getHighestDisplay());
            if (lblLowScore != null) lblLowScore.setText(stats.getLowestDisplay());
        } catch (SQLException e) {
            System.err.println("Failed to load statistics: " + e.getMessage());
        }
    }

    // =========================================
    // Actions
    // =========================================

    @FXML
    private void search() {
        try {
            // Gather filters
            LocalDateTime fromDate = null;
            LocalDateTime toDate = null;

            if (dateFrom != null && dateFrom.getValue() != null) {
                fromDate = LocalDateTime.of(dateFrom.getValue(), LocalTime.MIN);
            }
            if (dateTo != null && dateTo.getValue() != null) {
                toDate = LocalDateTime.of(dateTo.getValue(), LocalTime.MAX);
            }

            String studentId = txtStudentId != null ? txtStudentId.getText() : null;
            if (studentId != null && studentId.trim().isEmpty()) {
                studentId = null;
            }

            String testId = null;
            if (cmbTestId != null && cmbTestId.getValue() != null && !cmbTestId.getValue().equals("All")) {
                testId = cmbTestId.getValue();
            }

            Scan.ScanStatus status = null;
            if (cmbStatus != null && cmbStatus.getValue() != null && !cmbStatus.getValue().equals("All")) {
                status = Scan.ScanStatus.fromValue(cmbStatus.getValue());
            }

            // Search
            allScans = scanService.search(studentId, testId, status, fromDate, toDate);
            currentPage = 0;
            updateDisplay();

        } catch (SQLException e) {
            showError("Search failed", e.getMessage());
        }
    }

    @FXML
    private void clearFilters() {
        if (dateFrom != null) dateFrom.setValue(null);
        if (dateTo != null) dateTo.setValue(null);
        if (txtStudentId != null) txtStudentId.clear();
        if (cmbTestId != null) cmbTestId.setValue("All");
        if (cmbStatus != null) cmbStatus.setValue("All");
        loadData();
    }

    @FXML
    private void prevPage() {
        if (currentPage > 0) {
            currentPage--;
            updateDisplay();
        }
    }

    @FXML
    private void nextPage() {
        if (currentPage < totalPages - 1) {
            currentPage++;
            updateDisplay();
        }
    }

    @FXML
    private void viewDetails() {
        ScanRow selected = tblResults.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("No Selection", "Please select a scan to view details.");
            return;
        }

        try {
            // Load full scan with answers
            Optional<Scan> fullScan = scanService.findById(selected.scan.getId());
            if (fullScan.isPresent()) {
                showScanDetails(fullScan.get());
            }
        } catch (SQLException e) {
            showError("Failed to load details", e.getMessage());
        }
    }

    @FXML
    private void deleteSelected() {
        List<ScanRow> selected = scanRows.stream()
            .filter(row -> row.selected.get())
            .collect(Collectors.toList());

        if (selected.isEmpty()) {
            showWarning("No Selection", "Please select scans to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText(String.format("Delete %d scan(s)?", selected.size()));
        confirm.setContentText("This action cannot be undone.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                List<Long> ids = selected.stream()
                    .map(row -> row.scan.getId())
                    .collect(Collectors.toList());
                scanService.deleteMultiple(ids);
                loadData();
                showInfo("Deleted", String.format("Successfully deleted %d scan(s).", selected.size()));
            } catch (SQLException e) {
                showError("Delete failed", e.getMessage());
            }
        }
    }

    @FXML
    private void exportSelected() {
        List<ScanRow> selected = scanRows.stream()
            .filter(row -> row.selected.get())
            .collect(Collectors.toList());

        if (selected.isEmpty()) {
            showWarning("No Selection", "Please select scans to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to CSV");
        fileChooser.setInitialFileName("scan_results.csv");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                // Load full scan data with answers for each selected scan
                List<Scan> scansToExport = new ArrayList<>();
                for (ScanRow row : selected) {
                    Optional<Scan> fullScan = scanService.findById(row.scan.getId());
                    if (fullScan.isPresent()) {
                        scansToExport.add(fullScan.get());
                    } else {
                        // Fallback to row.scan if full scan not found
                        scansToExport.add(row.scan);
                    }
                }
                
                exportService.exportScans(scansToExport, file);
                showInfo("Export Successful", String.format("Exported %d scan(s) to %s",
                    scansToExport.size(), file.getName()));
            } catch (Exception e) {
                showError("Export failed", e.getMessage());
            }
        }
    }

    // =========================================
    // Helper Methods
    // =========================================

    private void showScanDetails(Scan scan) {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Scan Details");
        dialog.setHeaderText(String.format("Student: %s | Test: %s",
            scan.getStudentId(), scan.getTestId()));

        StringBuilder content = new StringBuilder();
        content.append(String.format("Score: %s\n", scan.getScoreDisplay()));
        content.append(String.format("Correct: %d | Wrong: %d | Empty: %d | Invalid: %d\n\n",
            scan.getScoreCorrect(), scan.getScoreWrong(), scan.getScoreEmpty(), scan.getScoreInvalid()));
        content.append(String.format("Answer Key: %s\n",
            scan.getAnswerKey() != null ? scan.getAnswerKey().getName() : "N/A"));
        content.append(String.format("Status: %s\n", scan.getStatus().getValue()));
        content.append(String.format("Date: %s\n",
            scan.getCreatedAt() != null ? scan.getCreatedAt().format(DATE_FORMAT) : "N/A"));

        dialog.setContentText(content.toString());
        dialog.showAndWait();
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
    // Inner Class for Table Row
    // =========================================

    public static class ScanRow {
        public final Scan scan;
        public final SimpleBooleanProperty selected;

        public ScanRow(Scan scan) {
            this.scan = scan;
            this.selected = new SimpleBooleanProperty(false);
        }

        public SimpleBooleanProperty selectedProperty() {
            return selected;
        }
    }
}

