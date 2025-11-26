package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_imgproc.CLAHE;
import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

/**
 * OMR Template Calibration UI
 * 
 * A visual tool to calibrate OMR template positions for accurate bubble detection.
 * Allows users to:
 * - Load an OMR sheet image
 * - Adjust template positions with sliders
 * - See live preview of bubble sampling locations
 * - Process and verify results
 */
public class OMRCalibrationUI extends Application {
    
    // UI Components
    private ImageView imageView;
    private Pane overlayPane;
    private TextArea resultsArea;
    private Label statusLabel;
    
    // Template Configuration Spinners
    private Spinner<Integer> answerXSpinner, answerYSpinner;
    private Spinner<Integer> optionSpacingSpinner, rowSpacingSpinner;
    private Spinner<Integer> colSpacingSpinner;
    private Spinner<Integer> bubbleWSpinner, bubbleHSpinner;
    private Spinner<Integer> studentIdXSpinner, studentIdYSpinner;
    private Spinner<Integer> testIdXSpinner, testIdYSpinner;
    private CheckBox reverseOptionsCheck;
    
    // Current image
    private Mat currentImage;
    private Mat processedImage;
    private String currentImagePath;
    private int actualImageWidth = 1;
    private int actualImageHeight = 1;
    
    // Template values (defaults)
    private int answerX = 160, answerY = 925;
    private int optionSpacing = 46, rowSpacing = 56;
    private int colSpacing = 285;
    private int bubbleW = 35, bubbleH = 35;
    private int studentIdX = 50, studentIdY = 300;
    private int testIdX = 385, testIdY = 300;
    private boolean reverseOptions = true;
    
    private static final int PAGE_WIDTH = 1240;
    private static final int PAGE_HEIGHT = 1754;
    
    public static void main(String[] args) {
        launch(args);
    }
    
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("OMR Template Calibration Tool");
        
        // Main layout
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1a1a2e;");
        
        // Top toolbar
        HBox toolbar = createToolbar();
        root.setTop(toolbar);
        
        // Center: Image with overlay
        StackPane imageContainer = createImageContainer();
        root.setCenter(imageContainer);
        
        // Right: Configuration panel
        ScrollPane configPanel = createConfigPanel();
        root.setRight(configPanel);
        
        // Bottom: Results and status
        VBox bottomPanel = createBottomPanel();
        root.setBottom(bottomPanel);
        
        Scene scene = new Scene(root, 1400, 900);
        scene.getStylesheets().add(getClass().getResource("/css/calibration.css") != null ? 
            getClass().getResource("/css/calibration.css").toExternalForm() : "");
        
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // Add inline styles
        applyStyles(root);
    }
    
    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(10));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color: #16213e;");
        
        Button loadBtn = new Button("📂 Load Image");
        loadBtn.setStyle("-fx-background-color: #0f4c75; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 8 16;");
        loadBtn.setOnAction(e -> loadImage());
        
        Button processBtn = new Button("▶ Process");
        processBtn.setStyle("-fx-background-color: #3282b8; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 8 16;");
        processBtn.setOnAction(e -> processImage());
        
        Button updateOverlayBtn = new Button("🔄 Update Overlay");
        updateOverlayBtn.setStyle("-fx-background-color: #1b9aaa; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 8 16;");
        updateOverlayBtn.setOnAction(e -> updateOverlay());
        
        Button exportBtn = new Button("💾 Export Template");
        exportBtn.setStyle("-fx-background-color: #06a77d; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 8 16;");
        exportBtn.setOnAction(e -> exportTemplate());
        
        Label title = new Label("OMR Template Calibration");
        title.setStyle("-fx-text-fill: #bbe1fa; -fx-font-size: 18px; -fx-font-weight: bold;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        toolbar.getChildren().addAll(loadBtn, processBtn, updateOverlayBtn, exportBtn, spacer, title);
        return toolbar;
    }
    
    private StackPane createImageContainer() {
        StackPane container = new StackPane();
        container.setStyle("-fx-background-color: #0f0f23;");
        container.setPadding(new Insets(10));
        
        // Image view
        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(800);
        imageView.setFitHeight(700);
        
        // Overlay pane for template rectangles
        overlayPane = new Pane();
        overlayPane.setMouseTransparent(true);
        
        // Placeholder text
        Label placeholder = new Label("Load an OMR sheet image to begin calibration");
        placeholder.setStyle("-fx-text-fill: #666; -fx-font-size: 16px;");
        placeholder.setId("placeholder");
        
        container.getChildren().addAll(placeholder, imageView, overlayPane);
        return container;
    }
    
    private ScrollPane createConfigPanel() {
        VBox configBox = new VBox(15);
        configBox.setPadding(new Insets(15));
        configBox.setStyle("-fx-background-color: #16213e;");
        configBox.setPrefWidth(320);
        
        // Title
        Label configTitle = new Label("📐 Template Configuration");
        configTitle.setStyle("-fx-text-fill: #bbe1fa; -fx-font-size: 16px; -fx-font-weight: bold;");
        
        // Answer Grid Section
        TitledPane answerSection = createAnswerConfigSection();
        
        // ID Sections
        TitledPane idSection = createIdConfigSection();
        
        // Options Section
        TitledPane optionsSection = createOptionsSection();
        
        configBox.getChildren().addAll(configTitle, answerSection, idSection, optionsSection);
        
        ScrollPane scrollPane = new ScrollPane(configBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #16213e;");
        return scrollPane;
    }
    
    private TitledPane createAnswerConfigSection() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        
        int row = 0;
        
        // Answer X
        grid.add(createLabel("Answer X:"), 0, row);
        answerXSpinner = createSpinner(0, 3000, answerX);
        grid.add(answerXSpinner, 1, row++);
        
        // Answer Y
        grid.add(createLabel("Answer Y:"), 0, row);
        answerYSpinner = createSpinner(0, 3000, answerY);
        grid.add(answerYSpinner, 1, row++);
        
        // Option Spacing
        grid.add(createLabel("Option Spacing:"), 0, row);
        optionSpacingSpinner = createSpinner(20, 100, optionSpacing);
        grid.add(optionSpacingSpinner, 1, row++);
        
        // Row Spacing
        grid.add(createLabel("Row Spacing:"), 0, row);
        rowSpacingSpinner = createSpinner(30, 100, rowSpacing);
        grid.add(rowSpacingSpinner, 1, row++);
        
        // Column Spacing
        grid.add(createLabel("Column Spacing:"), 0, row);
        colSpacingSpinner = createSpinner(100, 500, colSpacing);
        grid.add(colSpacingSpinner, 1, row++);
        
        // Bubble Width
        grid.add(createLabel("Bubble Width:"), 0, row);
        bubbleWSpinner = createSpinner(15, 60, bubbleW);
        grid.add(bubbleWSpinner, 1, row++);
        
        // Bubble Height
        grid.add(createLabel("Bubble Height:"), 0, row);
        bubbleHSpinner = createSpinner(15, 60, bubbleH);
        grid.add(bubbleHSpinner, 1, row++);
        
        TitledPane pane = new TitledPane("Answer Grid", grid);
        pane.setExpanded(true);
        stylePane(pane);
        return pane;
    }
    
    private TitledPane createIdConfigSection() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        
        int row = 0;
        
        // Student ID X
        grid.add(createLabel("Student ID X:"), 0, row);
        studentIdXSpinner = createSpinner(0, 2000, studentIdX);
        grid.add(studentIdXSpinner, 1, row++);
        
        // Student ID Y
        grid.add(createLabel("Student ID Y:"), 0, row);
        studentIdYSpinner = createSpinner(0, 2000, studentIdY);
        grid.add(studentIdYSpinner, 1, row++);
        
        // Test ID X
        grid.add(createLabel("Test ID X:"), 0, row);
        testIdXSpinner = createSpinner(0, 2000, testIdX);
        grid.add(testIdXSpinner, 1, row++);
        
        // Test ID Y
        grid.add(createLabel("Test ID Y:"), 0, row);
        testIdYSpinner = createSpinner(0, 2000, testIdY);
        grid.add(testIdYSpinner, 1, row++);
        
        TitledPane pane = new TitledPane("ID Grids", grid);
        pane.setExpanded(false);
        stylePane(pane);
        return pane;
    }
    
    private TitledPane createOptionsSection() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(10));
        
        reverseOptionsCheck = new CheckBox("Reverse Option Order (D,C,B,A)");
        reverseOptionsCheck.setSelected(reverseOptions);
        reverseOptionsCheck.setStyle("-fx-text-fill: #ddd;");
        
        Label hint = new Label("Check this if your form has options ordered D→A instead of A→D");
        hint.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");
        hint.setWrapText(true);
        
        box.getChildren().addAll(reverseOptionsCheck, hint);
        
        TitledPane pane = new TitledPane("Options", box);
        pane.setExpanded(false);
        stylePane(pane);
        return pane;
    }
    
    private VBox createBottomPanel() {
        VBox bottom = new VBox(5);
        bottom.setPadding(new Insets(10));
        bottom.setStyle("-fx-background-color: #16213e;");
        
        // Status bar
        statusLabel = new Label("Ready - Load an image to begin");
        statusLabel.setStyle("-fx-text-fill: #bbe1fa; -fx-font-size: 12px;");
        
        // Results area
        resultsArea = new TextArea();
        resultsArea.setEditable(false);
        resultsArea.setPrefRowCount(6);
        resultsArea.setStyle("-fx-control-inner-background: #0f0f23; -fx-text-fill: #00ff88; -fx-font-family: 'Consolas', monospace;");
        resultsArea.setText("Results will appear here after processing...");
        
        bottom.getChildren().addAll(statusLabel, resultsArea);
        return bottom;
    }
    
    private Label createLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #ddd; -fx-font-size: 12px;");
        return label;
    }
    
    private Spinner<Integer> createSpinner(int min, int max, int initial) {
        Spinner<Integer> spinner = new Spinner<>(min, max, initial);
        spinner.setEditable(true);
        spinner.setPrefWidth(100);
        spinner.setStyle("-fx-background-color: #1a1a2e;");
        spinner.valueProperty().addListener((obs, oldVal, newVal) -> updateOverlay());
        return spinner;
    }
    
    private void stylePane(TitledPane pane) {
        pane.setStyle("-fx-text-fill: #bbe1fa;");
        pane.getContent().setStyle("-fx-background-color: #1a1a2e;");
    }
    
    private void applyStyles(BorderPane root) {
        // Additional runtime styling if needed
    }
    
    private void loadImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select OMR Sheet Image");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.bmp")
        );
        
        File file = fileChooser.showOpenDialog(imageView.getScene().getWindow());
        if (file != null) {
            currentImagePath = file.getAbsolutePath();
            statusLabel.setText("Loading: " + file.getName());
            
            // Load with OpenCV
            currentImage = imread(currentImagePath);
            if (currentImage.empty()) {
                showError("Failed to load image");
                return;
            }
            
            // Convert to grayscale - NO perspective correction, show original
            Mat gray = new Mat();
            if (currentImage.channels() > 1) {
                cvtColor(currentImage, gray, COLOR_BGR2GRAY);
            } else {
                gray = currentImage.clone();
            }
            
            // Just apply CLAHE for better contrast, no warping
            processedImage = new Mat();
            CLAHE claheObj = createCLAHE(2.0, new Size(8, 8));
            claheObj.apply(gray, processedImage);
            gray.release();
            
            // Store actual image dimensions for overlay calculation
            actualImageWidth = processedImage.cols();
            actualImageHeight = processedImage.rows();
            
            // Display the image
            displayImage(processedImage);
            
            // Draw overlay
            updateOverlay();
            
            statusLabel.setText("Loaded: " + file.getName() + " | Size: " + processedImage.cols() + "x" + processedImage.rows());
            
            // Hide placeholder
            overlayPane.getParent().getChildrenUnmodifiable().stream()
                .filter(n -> "placeholder".equals(n.getId()))
                .findFirst()
                .ifPresent(n -> n.setVisible(false));
        }
    }
    
    
    private void displayImage(Mat mat) {
        try {
            // Convert Mat to JavaFX Image
            Mat rgb = new Mat();
            if (mat.channels() == 1) {
                cvtColor(mat, rgb, COLOR_GRAY2BGR);
            } else {
                rgb = mat.clone();
            }
            
            // Save to temp file and load as JavaFX image
            String tempPath = System.getProperty("java.io.tmpdir") + "/omr_preview.png";
            imwrite(tempPath, rgb);
            rgb.release();
            
            Image image = new Image(new FileInputStream(tempPath));
            imageView.setImage(image);
            
        } catch (Exception e) {
            showError("Error displaying image: " + e.getMessage());
        }
    }
    
    private void updateOverlay() {
        if (processedImage == null || imageView.getImage() == null) return;
        
        // Update values from spinners
        answerX = answerXSpinner.getValue();
        answerY = answerYSpinner.getValue();
        optionSpacing = optionSpacingSpinner.getValue();
        rowSpacing = rowSpacingSpinner.getValue();
        colSpacing = colSpacingSpinner.getValue();
        bubbleW = bubbleWSpinner.getValue();
        bubbleH = bubbleHSpinner.getValue();
        studentIdX = studentIdXSpinner.getValue();
        studentIdY = studentIdYSpinner.getValue();
        testIdX = testIdXSpinner.getValue();
        testIdY = testIdYSpinner.getValue();
        reverseOptions = reverseOptionsCheck.isSelected();
        
        // Clear existing overlay
        overlayPane.getChildren().clear();
        
        // Calculate scale factor based on actual image dimensions
        double scaleX = imageView.getBoundsInParent().getWidth() / actualImageWidth;
        double scaleY = imageView.getBoundsInParent().getHeight() / actualImageHeight;
        double scale = Math.min(scaleX, scaleY);
        
        // Offset for centering
        double offsetX = (imageView.getBoundsInParent().getWidth() - actualImageWidth * scale) / 2;
        double offsetY = (imageView.getBoundsInParent().getHeight() - actualImageHeight * scale) / 2;
        
        // Draw answer grid overlay (first 15 questions only for clarity)
        for (int qCol = 0; qCol < 1; qCol++) {  // Just first column for now
            for (int qRow = 0; qRow < 15; qRow++) {
                for (int opt = 0; opt < 4; opt++) {
                    int x = answerX + qCol * colSpacing + opt * optionSpacing;
                    int y = answerY + qRow * rowSpacing;
                    
                    Rectangle rect = new Rectangle(
                        offsetX + x * scale,
                        offsetY + y * scale,
                        bubbleW * scale,
                        bubbleH * scale
                    );
                    rect.setFill(Color.TRANSPARENT);
                    rect.setStroke(Color.LIME);
                    rect.setStrokeWidth(1);
                    
                    overlayPane.getChildren().add(rect);
                }
            }
        }
        
        // Draw Student ID grid overlay (just first column for reference)
        for (int row = 0; row < 10; row++) {
            int x = studentIdX;
            int y = studentIdY + row * 38;
            
            Rectangle rect = new Rectangle(
                offsetX + x * scale,
                offsetY + y * scale,
                25 * scale,
                32 * scale
            );
            rect.setFill(Color.TRANSPARENT);
            rect.setStroke(Color.CYAN);
            rect.setStrokeWidth(1);
            
            overlayPane.getChildren().add(rect);
        }
    }
    
    private void processImage() {
        if (processedImage == null) {
            showError("Please load an image first");
            return;
        }
        
        statusLabel.setText("Processing...");
        resultsArea.setText("Processing OMR sheet...\n");
        
        // Run processing in background
        new Thread(() -> {
            try {
                // Read answers using current template settings
                String[] options = reverseOptions ? 
                    new String[]{"D", "C", "B", "A"} : 
                    new String[]{"A", "B", "C", "D"};
                
                StringBuilder results = new StringBuilder();
                results.append("=== PROCESSING RESULTS ===\n\n");
                
                // Collect all intensities for threshold calculation
                List<Double> allIntensities = new ArrayList<>();
                double[][] questionValues = new double[60][4];
                
                for (int qCol = 0; qCol < 4; qCol++) {
                    for (int qRow = 0; qRow < 15; qRow++) {
                        int questionNum = qCol * 15 + qRow + 1;
                        
                        for (int opt = 0; opt < 4; opt++) {
                            int x = answerX + qCol * colSpacing + opt * optionSpacing;
                            int y = answerY + qRow * rowSpacing;
                            
                            double intensity = sampleBubble(processedImage, x, y, bubbleW, bubbleH);
                            questionValues[questionNum - 1][opt] = intensity;
                            allIntensities.add(intensity);
                        }
                    }
                }
                
                // Calculate threshold
                double threshold = calculateThreshold(allIntensities);
                results.append("Global Threshold: ").append(String.format("%.1f", threshold)).append("\n\n");
                
                // Determine answers
                results.append("Answers:\n");
                StringBuilder answerLine = new StringBuilder();
                
                for (int q = 0; q < 60; q++) {
                    double[] values = questionValues[q];
                    int minIdx = 0;
                    double minVal = values[0];
                    
                    for (int i = 1; i < 4; i++) {
                        if (values[i] < minVal) {
                            minVal = values[i];
                            minIdx = i;
                        }
                    }
                    
                    String answer = minVal < threshold ? options[minIdx] : "-";
                    answerLine.append(answer);
                    
                    if ((q + 1) % 15 == 0) {
                        results.append("Q").append(String.format("%2d", q - 13)).append("-").append(String.format("%2d", q + 1))
                               .append(": ").append(answerLine).append("\n");
                        answerLine = new StringBuilder();
                    }
                }
                
                // Show first 5 question intensities for debugging
                results.append("\nQ1-5 Intensities (for debugging):\n");
                for (int q = 0; q < 5; q++) {
                    results.append("Q").append(q + 1).append(": ");
                    for (int opt = 0; opt < 4; opt++) {
                        results.append(options[opt]).append("=").append(String.format("%.0f", questionValues[q][opt])).append(" ");
                    }
                    results.append("\n");
                }
                
                final String finalResults = results.toString();
                Platform.runLater(() -> {
                    resultsArea.setText(finalResults);
                    statusLabel.setText("Processing complete!");
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError("Processing error: " + e.getMessage());
                    statusLabel.setText("Error during processing");
                });
            }
        }).start();
    }
    
    private double sampleBubble(Mat image, int x, int y, int w, int h) {
        int x1 = Math.max(0, x);
        int y1 = Math.max(0, y);
        int x2 = Math.min(image.cols(), x + w);
        int y2 = Math.min(image.rows(), y + h);
        
        if (x2 <= x1 || y2 <= y1) return 255;
        
        Rect roi = new Rect(x1, y1, x2 - x1, y2 - y1);
        Mat region = new Mat(image, roi);
        Scalar mean = mean(region);
        return mean.get(0);
    }
    
    private double calculateThreshold(List<Double> intensities) {
        if (intensities.size() < 2) return 127;
        
        List<Double> sorted = new ArrayList<>(intensities);
        Collections.sort(sorted);
        
        double maxGap = 0;
        double threshold = 127;
        
        for (int i = 2; i < sorted.size() - 2; i++) {
            double gap = sorted.get(i + 1) - sorted.get(i);
            if (gap > maxGap) {
                maxGap = gap;
                threshold = (sorted.get(i) + sorted.get(i + 1)) / 2;
            }
        }
        
        if (maxGap < 20) {
            threshold = sorted.get(sorted.size() / 2) * 0.85;
        }
        
        return threshold;
    }
    
    private void exportTemplate() {
        StringBuilder template = new StringBuilder();
        template.append("// OMR Template Configuration\n");
        template.append("// Generated by OMR Calibration Tool\n\n");
        template.append("// Answer Grid\n");
        template.append("ANSWER_X = ").append(answerX).append(";\n");
        template.append("ANSWER_Y = ").append(answerY).append(";\n");
        template.append("ANSWER_OPTION_SPACING = ").append(optionSpacing).append(";\n");
        template.append("ANSWER_ROW_SPACING = ").append(rowSpacing).append(";\n");
        template.append("ANSWER_COL_SPACING = ").append(colSpacing).append(";\n");
        template.append("ANSWER_BUBBLE_W = ").append(bubbleW).append(";\n");
        template.append("ANSWER_BUBBLE_H = ").append(bubbleH).append(";\n");
        template.append("REVERSE_OPTIONS = ").append(reverseOptions).append(";\n\n");
        template.append("// Student ID Grid\n");
        template.append("STUDENT_ID_X = ").append(studentIdX).append(";\n");
        template.append("STUDENT_ID_Y = ").append(studentIdY).append(";\n\n");
        template.append("// Test ID Grid\n");
        template.append("TEST_ID_X = ").append(testIdX).append(";\n");
        template.append("TEST_ID_Y = ").append(testIdY).append(";\n");
        
        // Show in dialog
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Template Export");
        alert.setHeaderText("Copy these values to your code:");
        
        TextArea textArea = new TextArea(template.toString());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        
        alert.getDialogPane().setContent(textArea);
        alert.getDialogPane().setMinWidth(500);
        alert.showAndWait();
    }
    
    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}

