package org.example.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.example.model.AnswerKey;
import org.example.model.Scan;
import org.example.model.ScanAnswer;
import org.example.service.AnswerKeyService;
import org.example.service.ScanService;

import java.io.File;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controller for the Single Scan screen.
 * Handles image loading, processing, and result display.
 */
public class ScanController implements Initializable {

    // =========================================
    // FXML Bindings - Left Panel
    // =========================================
    @FXML private StackPane imageContainer;
    @FXML private ImageView imagePreview;
    @FXML private ScrollPane imageScrollPane;
    @FXML private Slider zoomSlider;
    @FXML private Label zoomLabel;
    @FXML private CheckBox chkStudentId;
    @FXML private CheckBox chkTestId;
    @FXML private CheckBox chkAnswerBlocks;
    @FXML private Button btnLoadImage;
    @FXML private Button btnProcess;
    @FXML private TextArea logArea;

    // =========================================
    // FXML Bindings - Right Panel
    // =========================================
    @FXML private TextField txtStudentId;
    @FXML private Label lblStudentIdStatus;
    @FXML private TextField txtTestId;
    @FXML private Label lblTestIdStatus;
    @FXML private ComboBox<AnswerKey> cmbAnswerKey;
    @FXML private Label lblAutoDetected;
    @FXML private TableView<ScanAnswer> tblAnswers;
    @FXML private TableColumn<ScanAnswer, String> colQuestion;
    @FXML private TableColumn<ScanAnswer, String> colAnswer;
    @FXML private TableColumn<ScanAnswer, String> colKey;
    @FXML private TableColumn<ScanAnswer, String> colStatus;
    @FXML private ProgressBar progressScore;
    @FXML private Label lblScore;
    @FXML private Label lblCorrect;
    @FXML private Label lblWrong;
    @FXML private Label lblSkipped;
    @FXML private Label lblInvalid;
    @FXML private Button btnSave;
    @FXML private Button btnExport;
    @FXML private Button btnRescan;

    // =========================================
    // State
    // =========================================
    private ScanService scanService;
    private AnswerKeyService answerKeyService;
    private ObservableList<AnswerKey> answerKeys;
    private File currentImageFile;
    private Scan currentScan;
    private boolean isProcessing;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        scanService = new ScanService();
        answerKeyService = new AnswerKeyService();
        answerKeys = FXCollections.observableArrayList();
        
        setupControls();
        loadAnswerKeys();
        clearResults();
    }

    // =========================================
    // Setup Methods
    // =========================================

    private void setupControls() {
        // Setup zoom slider
        if (zoomSlider != null) {
            zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                double zoom = newVal.doubleValue();
                if (zoomLabel != null) {
                    zoomLabel.setText(String.format("%.0f%%", zoom));
                }
                if (imagePreview != null && imagePreview.getImage() != null) {
                    double scale = zoom / 100.0;
                    imagePreview.setFitWidth(imagePreview.getImage().getWidth() * scale);
                    imagePreview.setFitHeight(imagePreview.getImage().getHeight() * scale);
                }
            });
        }

        // Setup answer key combo
        if (cmbAnswerKey != null) {
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
                    setText(empty || key == null ? null : key.getName());
                }
            });
        }

        // Setup answers table
        if (tblAnswers != null) {
            setupAnswersTable();
        }

        // Initial button states
        updateButtonStates();
    }

    private void setupAnswersTable() {
        if (colQuestion != null) {
            colQuestion.setCellValueFactory(data -> 
                new SimpleStringProperty(String.valueOf(data.getValue().getQuestionNumber())));
        }
        if (colAnswer != null) {
            colAnswer.setCellValueFactory(data -> 
                new SimpleStringProperty(data.getValue().getDetectedDisplay()));
        }
        if (colKey != null) {
            colKey.setCellValueFactory(data -> {
                String correct = data.getValue().getCorrectAnswer();
                return new SimpleStringProperty(correct != null ? correct : "-");
            });
        }
        if (colStatus != null) {
            colStatus.setCellValueFactory(data -> 
                new SimpleStringProperty(data.getValue().getStatusIcon()));
            colStatus.setCellFactory(column -> new TableCell<ScanAnswer, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item);
                        ScanAnswer ans = getTableView().getItems().get(getIndex());
                        getStyleClass().removeAll("status-success", "status-error", "status-muted", "status-warning");
                        getStyleClass().add(ans.getStatusStyleClass());
                    }
                }
            });
        }
    }

    private void loadAnswerKeys() {
        try {
            List<AnswerKey> keys = answerKeyService.findAll();
            answerKeys.setAll(keys);
        } catch (SQLException e) {
            log("Error loading answer keys: " + e.getMessage());
        }
    }

    // =========================================
    // Actions
    // =========================================

    @FXML
    private void loadImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select OMR Sheet Image");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.jpeg", "*.png", "*.bmp"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            loadImageFile(file);
        }
    }

    private void loadImageFile(File file) {
        currentImageFile = file;
        clearResults();
        
        try {
            Image image = new Image(file.toURI().toString());
            if (imagePreview != null) {
                imagePreview.setImage(image);
                imagePreview.setPreserveRatio(true);
                
                // Reset zoom
                if (zoomSlider != null) {
                    zoomSlider.setValue(100);
                }
            }
            
            log("✓ Image loaded: " + file.getName());
            updateButtonStates();
            
        } catch (Exception e) {
            log("✗ Error loading image: " + e.getMessage());
            currentImageFile = null;
        }
    }

    @FXML
    private void process() {
        if (currentImageFile == null) {
            showWarning("No Image", "Please load an image first.");
            return;
        }
        
        if (isProcessing) {
            return;
        }
        
        isProcessing = true;
        updateButtonStates();
        clearResults();
        
        // Get selected answer key (or null for auto-detect)
        AnswerKey selectedKey = cmbAnswerKey != null ? cmbAnswerKey.getValue() : null;
        
        // Run processing in background thread
        new Thread(() -> {
            try {
                log("⏳ Processing image...");
                log("  Using processor: " + scanService.getProcessor().getProcessorName());
                
                // Process without saving to DB yet
                Scan scan = scanService.processImage(currentImageFile, selectedKey, false);
                
                // Update UI on JavaFX thread
                Platform.runLater(() -> {
                    currentScan = scan;
                    displayResults(scan);
                    isProcessing = false;
                    updateButtonStates();
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> {
                    log("✗ Processing failed: " + e.getMessage());
                    isProcessing = false;
                    updateButtonStates();
                });
            }
        }).start();
    }

    @FXML
    private void save() {
        if (currentScan == null) {
            showWarning("Nothing to Save", "Please process an image first.");
            return;
        }
        
        try {
            currentScan = scanService.save(currentScan);
            log("✓ Scan saved to database (ID: " + currentScan.getId() + ")");
            showInfo("Saved", "Scan result saved successfully.");
        } catch (SQLException e) {
            log("✗ Save failed: " + e.getMessage());
            showError("Save Failed", e.getMessage());
        }
    }

    @FXML
    private void export() {
        if (currentScan == null) {
            showWarning("Nothing to Export", "Please process an image first.");
            return;
        }
        
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Export Scan Result");
        fileChooser.setInitialFileName("scan_result.csv");
        fileChooser.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        
        java.io.File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                org.example.service.ExportService exportService = new org.example.service.ExportService();
                exportService.exportScan(currentScan, file);
                log("✓ Exported to " + file.getName());
                showInfo("Export Successful", "Scan result exported to " + file.getName());
            } catch (Exception e) {
                log("✗ Export failed: " + e.getMessage());
                showError("Export Failed", e.getMessage());
            }
        }
    }

    @FXML
    private void rescan() {
        if (currentImageFile != null) {
            process();
        }
    }

    // =========================================
    // Display Methods
    // =========================================

    private void displayResults(Scan scan) {
        // Display IDs
        if (txtStudentId != null) {
            txtStudentId.setText(scan.getStudentId() != null ? scan.getStudentId() : "");
        }
        if (lblStudentIdStatus != null) {
            if (scan.isStudentIdValid()) {
                lblStudentIdStatus.setText("✓ Valid");
                lblStudentIdStatus.getStyleClass().removeAll("label-error", "label-warning");
                lblStudentIdStatus.getStyleClass().add("label-success");
            } else {
                lblStudentIdStatus.setText("⚠ Invalid");
                lblStudentIdStatus.getStyleClass().removeAll("label-success", "label-error");
                lblStudentIdStatus.getStyleClass().add("label-warning");
            }
        }
        
        if (txtTestId != null) {
            txtTestId.setText(scan.getTestId() != null ? scan.getTestId() : "");
        }
        if (lblTestIdStatus != null) {
            if (scan.isTestIdValid()) {
                lblTestIdStatus.setText("✓ Valid");
                lblTestIdStatus.getStyleClass().removeAll("label-error", "label-warning");
                lblTestIdStatus.getStyleClass().add("label-success");
            } else {
                lblTestIdStatus.setText("⚠ Invalid");
                lblTestIdStatus.getStyleClass().removeAll("label-success", "label-error");
                lblTestIdStatus.getStyleClass().add("label-warning");
            }
        }
        
        // Display auto-detected answer key
        if (lblAutoDetected != null) {
            if (scan.getAnswerKey() != null) {
                lblAutoDetected.setText("(auto: " + scan.getAnswerKey().getName() + ")");
                // Select in combo
                if (cmbAnswerKey != null) {
                    for (AnswerKey key : answerKeys) {
                        if (key.getId().equals(scan.getAnswerKeyId())) {
                            cmbAnswerKey.setValue(key);
                            break;
                        }
                    }
                }
            } else {
                lblAutoDetected.setText("(no key found)");
            }
        }
        
        // Display answers in table
        if (tblAnswers != null && scan.getAnswers() != null) {
            tblAnswers.setItems(FXCollections.observableArrayList(scan.getAnswers()));
        }
        
        // Display score
        if (progressScore != null) {
            progressScore.setProgress(scan.getScorePercentage() / 100.0);
        }
        if (lblScore != null) {
            lblScore.setText(scan.getScoreDisplay());
        }
        if (lblCorrect != null) {
            lblCorrect.setText("Correct: " + scan.getScoreCorrect());
        }
        if (lblWrong != null) {
            lblWrong.setText("Wrong: " + scan.getScoreWrong());
        }
        if (lblSkipped != null) {
            lblSkipped.setText("Skipped: " + scan.getScoreEmpty());
        }
        if (lblInvalid != null) {
            lblInvalid.setText("Invalid: " + scan.getScoreInvalid());
        }
        
        // Log summary
        log("✓ Student ID: " + (scan.getStudentId() != null ? scan.getStudentId() : "N/A"));
        log("✓ Test ID: " + (scan.getTestId() != null ? scan.getTestId() : "N/A"));
        log("✓ " + scan.getAnswers().size() + " answers detected");
        log("✓ Score: " + scan.getScoreDisplay());
        log("  Processing time: " + scan.getProcessingTimeMs() + "ms");
    }

    private void clearResults() {
        currentScan = null;
        
        if (txtStudentId != null) txtStudentId.setText("");
        if (lblStudentIdStatus != null) {
            lblStudentIdStatus.setText("");
            lblStudentIdStatus.getStyleClass().removeAll("label-success", "label-warning", "label-error");
        }
        if (txtTestId != null) txtTestId.setText("");
        if (lblTestIdStatus != null) {
            lblTestIdStatus.setText("");
            lblTestIdStatus.getStyleClass().removeAll("label-success", "label-warning", "label-error");
        }
        if (lblAutoDetected != null) lblAutoDetected.setText("");
        if (tblAnswers != null) tblAnswers.setItems(FXCollections.observableArrayList());
        if (progressScore != null) progressScore.setProgress(0);
        if (lblScore != null) lblScore.setText("0/60 (0.0%)");
        if (lblCorrect != null) lblCorrect.setText("Correct: 0");
        if (lblWrong != null) lblWrong.setText("Wrong: 0");
        if (lblSkipped != null) lblSkipped.setText("Skipped: 0");
        if (lblInvalid != null) lblInvalid.setText("Invalid: 0");
    }

    private void updateButtonStates() {
        if (btnProcess != null) {
            btnProcess.setDisable(currentImageFile == null || isProcessing);
        }
        if (btnSave != null) {
            btnSave.setDisable(currentScan == null);
        }
        if (btnExport != null) {
            btnExport.setDisable(currentScan == null);
        }
        if (btnRescan != null) {
            btnRescan.setDisable(currentImageFile == null || isProcessing);
        }
    }

    // =========================================
    // Helper Methods
    // =========================================

    private void log(String message) {
        Platform.runLater(() -> {
            if (logArea != null) {
                logArea.appendText(message + "\n");
            }
        });
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
}

