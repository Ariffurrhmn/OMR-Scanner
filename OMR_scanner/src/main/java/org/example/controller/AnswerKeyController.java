package org.example.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.model.AnswerKey;
import org.example.service.AnswerKeyService;
import org.example.service.DatabaseService;

import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controller for the Answer Key management screen.
 * Handles CRUD operations and paginated answer grid.
 */
public class AnswerKeyController implements Initializable {

    private static final int QUESTIONS_PER_BLOCK = 15;
    private static final int TOTAL_QUESTIONS = 60;
    private static final int TOTAL_BLOCKS = 4;

    // =========================================
    // FXML Bindings - Left Panel (List)
    // =========================================
    @FXML private TextField searchField;
    @FXML private ListView<AnswerKey> keysList;
    @FXML private Button btnNew;
    @FXML private Button btnDuplicate;
    @FXML private Button btnDelete;

    // =========================================
    // FXML Bindings - Right Panel (Editor)
    // =========================================
    @FXML private TextField nameField;
    @FXML private TextField testIdField;
    @FXML private HBox blockTabsContainer;
    @FXML private VBox answerGridContainer;
    @FXML private TextField quickEntryField;
    @FXML private Button btnParse;
    @FXML private Button btnSave;
    @FXML private Button btnImportCsv;
    @FXML private Button btnExportCsv;

    // =========================================
    // State
    // =========================================
    private AnswerKeyService service;
    private ObservableList<AnswerKey> answerKeyList;
    private AnswerKey currentKey;
    private int currentBlock = 0; // 0-3 for blocks 1-4
    private ToggleGroup[] answerGroups; // Toggle groups for radio buttons
    private Button[] blockTabs; // Block navigation tabs

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("AnswerKeyController: Starting initialization...");
        
        try {
            // Initialize service and collections
            service = new AnswerKeyService();
            answerKeyList = FXCollections.observableArrayList();
            answerGroups = new ToggleGroup[TOTAL_QUESTIONS];
            
            System.out.println("AnswerKeyController: Service initialized");
            
            // Setup UI components
            setupKeysList();
            System.out.println("AnswerKeyController: Keys list setup complete");
            
            setupBlockTabs();
            System.out.println("AnswerKeyController: Block tabs setup complete");
            
            setupSearchField();
            System.out.println("AnswerKeyController: Search field setup complete");
            
            // Load data from database
            loadAnswerKeys();
            System.out.println("AnswerKeyController: Loaded " + answerKeyList.size() + " keys");
            
            // Select first key if available
            if (!answerKeyList.isEmpty()) {
                keysList.getSelectionModel().selectFirst();
            } else {
                createNewKey();
            }
            
            System.out.println("AnswerKeyController: Initialization complete");
            
        } catch (Exception e) {
            System.err.println("AnswerKeyController initialization error: " + e.getMessage());
            e.printStackTrace();
            
            // Initialize empty collections as fallback
            if (answerKeyList == null) {
                answerKeyList = FXCollections.observableArrayList();
            }
            if (answerGroups == null) {
                answerGroups = new ToggleGroup[TOTAL_QUESTIONS];
            }
            
            // Setup minimal UI
            try {
                setupKeysList();
                setupBlockTabs();
                setupSearchField();
            } catch (Exception e2) {
                System.err.println("Fallback UI setup failed: " + e2.getMessage());
            }
            
            // Create empty new key as fallback
            createNewKey();
        }
    }

    // =========================================
    // Setup Methods
    // =========================================

    private void setupKeysList() {
        if (keysList == null) {
            System.err.println("Warning: keysList is null - FXML injection may have failed");
            return;
        }
        
        try {
            keysList.setItems(answerKeyList);
            keysList.setCellFactory(lv -> new ListCell<AnswerKey>() {
                @Override
                protected void updateItem(AnswerKey key, boolean empty) {
                    super.updateItem(key, empty);
                    if (empty || key == null) {
                        setText(null);
                    } else {
                        setText(key.getName() + " (" + key.getTestId() + ")");
                    }
                }
            });
            
            keysList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    loadKeyIntoEditor(newVal);
                }
            });
        } catch (Exception e) {
            System.err.println("Error in setupKeysList: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupBlockTabs() {
        if (blockTabsContainer == null) {
            System.err.println("Warning: blockTabsContainer is null - FXML injection may have failed");
            return;
        }
        
        try {
            blockTabsContainer.getChildren().clear();
            blockTabs = new Button[TOTAL_BLOCKS];
            
            for (int i = 0; i < TOTAL_BLOCKS; i++) {
                int start = i * QUESTIONS_PER_BLOCK + 1;
                int end = start + QUESTIONS_PER_BLOCK - 1;
                
                Button tab = new Button("Q" + start + "-" + end);
                tab.getStyleClass().add(i == 0 ? "button-primary" : "button-secondary");
                
                final int blockIndex = i;
                tab.setOnAction(e -> selectBlock(blockIndex));
                
                blockTabs[i] = tab;
                blockTabsContainer.getChildren().add(tab);
            }
        } catch (Exception e) {
            System.err.println("Error in setupBlockTabs: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupSearchField() {
        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> {
                filterAnswerKeys(newVal);
            });
        }
    }

    // =========================================
    // Data Loading
    // =========================================

    private void loadAnswerKeys() {
        try {
            if (service == null) {
                System.err.println("Warning: AnswerKeyService is null");
                return;
            }
            
            List<AnswerKey> keys = service.findAll();
            if (keys != null) {
                answerKeyList.setAll(keys);
                System.out.println("Loaded " + keys.size() + " answer keys");
            } else {
                System.err.println("Warning: service.findAll() returned null");
                answerKeyList.clear();
            }
        } catch (SQLException e) {
            System.err.println("Failed to load answer keys (SQL): " + e.getMessage());
            e.printStackTrace();
            // Don't show error dialog during initialization
        } catch (Exception e) {
            System.err.println("Failed to load answer keys (unexpected): " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void filterAnswerKeys(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            loadAnswerKeys();
            return;
        }
        
        try {
            List<AnswerKey> keys = service.search(searchTerm);
            answerKeyList.setAll(keys);
        } catch (SQLException e) {
            showError("Search failed", e.getMessage());
        }
    }

    private void loadKeyIntoEditor(AnswerKey key) {
        try {
            // Load full key with items
            Optional<AnswerKey> fullKey = service.findById(key.getId());
            if (fullKey.isPresent()) {
                currentKey = fullKey.get();
            } else {
                currentKey = key;
            }
        } catch (SQLException e) {
            currentKey = key;
        }
        
        // Populate fields
        if (nameField != null) nameField.setText(currentKey.getName());
        if (testIdField != null) testIdField.setText(currentKey.getTestId());
        
        // Rebuild answer grid for current block
        selectBlock(0);
    }

    // =========================================
    // Block Navigation
    // =========================================

    private void selectBlock(int blockIndex) {
        currentBlock = blockIndex;
        
        // Update tab styles
        if (blockTabs != null) {
            for (int i = 0; i < blockTabs.length; i++) {
                blockTabs[i].getStyleClass().removeAll("button-primary", "button-secondary");
                blockTabs[i].getStyleClass().add(i == blockIndex ? "button-primary" : "button-secondary");
            }
        }
        
        // Rebuild answer grid
        buildAnswerGrid();
    }

    private void buildAnswerGrid() {
        if (answerGridContainer == null) {
            System.err.println("Warning: answerGridContainer is null");
            return;
        }
        
        try {
            answerGridContainer.getChildren().clear();
            
            int startQ = currentBlock * QUESTIONS_PER_BLOCK + 1;
            int endQ = startQ + QUESTIONS_PER_BLOCK - 1;
            
            for (int q = startQ; q <= endQ; q++) {
                HBox row = createAnswerRow(q);
                if (row != null) {
                    answerGridContainer.getChildren().add(row);
                }
            }
        } catch (Exception e) {
            System.err.println("Error in buildAnswerGrid: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private HBox createAnswerRow(int questionNumber) {
        try {
            HBox row = new HBox(16);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("answer-row");
            
            // Question label
            Label label = new Label("Q" + questionNumber + ":");
            label.setPrefWidth(50);
            label.getStyleClass().add("question-label");
            row.getChildren().add(label);
            
            // Toggle group for this question
            ToggleGroup group = new ToggleGroup();
            answerGroups[questionNumber - 1] = group;
            
            // Radio buttons for A, B, C, D
            String[] options = {"A", "B", "C", "D"};
            for (String option : options) {
                RadioButton rb = new RadioButton(option);
                rb.setToggleGroup(group);
                rb.getStyleClass().add("answer-radio");
                rb.setUserData(option);
                
                // Set selected if matches current key's answer
                if (currentKey != null) {
                    String answer = currentKey.getAnswer(questionNumber);
                    if (option.equals(answer)) {
                        rb.setSelected(true);
                    }
                }
                
                // Listen for changes
                final int qNum = questionNumber;
                rb.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                    if (isSelected && currentKey != null) {
                        currentKey.setAnswer(qNum, option);
                    }
                });
                
                row.getChildren().add(rb);
            }
            
            return row;
        } catch (Exception e) {
            System.err.println("Error creating answer row " + questionNumber + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // =========================================
    // CRUD Actions
    // =========================================

    @FXML
    private void createNewKey() {
        currentKey = new AnswerKey();
        currentKey.setName("New Answer Key");
        currentKey.setTestId(generateTestId());
        currentKey.setTotalQuestions(TOTAL_QUESTIONS);
        currentKey.initializeEmpty();
        
        if (nameField != null) nameField.setText(currentKey.getName());
        if (testIdField != null) testIdField.setText(currentKey.getTestId());
        
        selectBlock(0);
    }

    @FXML
    private void duplicateKey() {
        if (currentKey == null || currentKey.getId() == null) {
            showWarning("No key selected", "Please select an answer key to duplicate.");
            return;
        }
        
        AnswerKey duplicate = new AnswerKey();
        duplicate.setName(currentKey.getName() + " (Copy)");
        duplicate.setTestId(generateTestId());
        duplicate.setTotalQuestions(currentKey.getTotalQuestions());
        
        // Copy answers
        for (int i = 1; i <= TOTAL_QUESTIONS; i++) {
            duplicate.setAnswer(i, currentKey.getAnswer(i));
        }
        
        currentKey = duplicate;
        if (nameField != null) nameField.setText(currentKey.getName());
        if (testIdField != null) testIdField.setText(currentKey.getTestId());
        
        selectBlock(0);
    }

    @FXML
    private void saveKey() {
        if (currentKey == null) {
            createNewKey();
        }
        
        // Validate
        String name = nameField != null ? nameField.getText().trim() : "";
        String testId = testIdField != null ? testIdField.getText().trim() : "";
        
        if (name.isEmpty()) {
            showWarning("Validation Error", "Please enter a name for the answer key.");
            return;
        }
        if (testId.isEmpty() || !testId.matches("\\d{4}")) {
            showWarning("Validation Error", "Test ID must be a 4-digit number.");
            return;
        }
        
        currentKey.setName(name);
        currentKey.setTestId(testId);
        
        try {
            if (currentKey.getId() == null) {
                // Create new
                currentKey = service.create(currentKey);
                answerKeyList.add(currentKey);
            } else {
                // Update existing
                service.update(currentKey);
                // Refresh list
                loadAnswerKeys();
            }
            
            keysList.getSelectionModel().select(currentKey);
            showInfo("Saved", "Answer key saved successfully.");
            
        } catch (SQLException e) {
            showError("Save failed", e.getMessage());
        }
    }

    @FXML
    private void deleteKey() {
        if (currentKey == null || currentKey.getId() == null) {
            showWarning("No key selected", "Please select an answer key to delete.");
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete \"" + currentKey.getName() + "\"?");
        confirm.setContentText("This action cannot be undone.");
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                service.delete(currentKey.getId());
                answerKeyList.remove(currentKey);
                
                if (!answerKeyList.isEmpty()) {
                    keysList.getSelectionModel().selectFirst();
                } else {
                    createNewKey();
                }
                
            } catch (SQLException e) {
                showError("Delete failed", e.getMessage());
            }
        }
    }

    @FXML
    private void parseQuickEntry() {
        if (quickEntryField == null) return;
        
        String input = quickEntryField.getText();
        if (input == null || input.trim().isEmpty()) {
            showWarning("Empty Input", "Please enter answers in the quick entry field.");
            return;
        }
        
        if (currentKey == null) {
            createNewKey();
        }
        
        int parsed = currentKey.parseAnswerString(input);
        selectBlock(currentBlock); // Refresh grid
        
        showInfo("Parsed", "Successfully parsed " + parsed + " answers.");
    }

    @FXML
    private void importCsv() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Import Answer Key from CSV");
        fileChooser.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        
        java.io.File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            // TODO: Implement CSV parsing
            showInfo("Coming Soon", "CSV import will be implemented soon.");
        }
    }

    @FXML
    private void exportCsv() {
        if (currentKey == null || currentKey.getId() == null) {
            showWarning("No Answer Key", "Please create or select an answer key first.");
            return;
        }
        
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Export Answer Key to CSV");
        fileChooser.setInitialFileName(currentKey.getName().replaceAll("[^a-zA-Z0-9-_]", "_") + ".csv");
        fileChooser.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        
        java.io.File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                org.example.service.ExportService exportService = new org.example.service.ExportService();
                exportService.exportAnswerKey(currentKey, file);
                showInfo("Export Successful", "Answer key exported to " + file.getName());
            } catch (Exception e) {
                showError("Export Failed", e.getMessage());
            }
        }
    }

    // =========================================
    // Helper Methods
    // =========================================

    private String generateTestId() {
        return String.format("%04d", (int) (Math.random() * 9000) + 1000);
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

