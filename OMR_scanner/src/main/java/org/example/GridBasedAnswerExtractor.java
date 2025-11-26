package org.example;

import org.bytedeco.opencv.opencv_core.*;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_core.countNonZero;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;

/**
 * Grid-based answer extraction for OMR sheets.
 * 
 * Instead of detecting individual bubbles, this approach:
 * 1. Divides the answer section into a known grid (4 columns × 15 rows × 4 choices)
 * 2. Samples each grid cell to determine if it's marked
 * 
 * This is more reliable for scanned images where bubble detection may be noisy.
 */
public class GridBasedAnswerExtractor {

    // Grid configuration
    private static final int COLUMNS = 4;          // Q1-15, Q16-30, Q31-45, Q46-60
    private static final int ROWS_PER_COLUMN = 15; // 15 questions per column
    private static final int CHOICES = 4;          // A, B, C, D
    private static final String[] CHOICE_LABELS = {"A", "B", "C", "D"};

    // Detection parameters
    private double fillThreshold = 0.25;  // 25% black pixels = marked
    private double emptyThreshold = 0.10; // Less than 10% = definitely empty
    private boolean saveDebugImages = false;
    private String debugOutputDir = "output";

    /**
     * Result of grid-based extraction.
     */
    public static class GridResult {
        public String[] answers = new String[60];
        public double[] confidences = new double[60];
        public int detectedCount = 0;
        public int multipleCount = 0;
        public int emptyCount = 0;
    }

    /**
     * Extract answers from the answer section using grid-based sampling.
     * 
     * @param answerSection Binary image of the answer section (white bubbles on black)
     * @return Extracted answers
     */
    public GridResult extractAnswers(Mat answerSection) {
        GridResult result = new GridResult();
        
        int width = answerSection.cols();
        int height = answerSection.rows();
        
        System.out.println("  Grid extraction from: " + width + "x" + height);
        
        // Account for borders and separators in the answer section
        // The section has: [border][col1][sep][col2][sep][col3][sep][col4][border]
        double borderPercent = 0.01;      // ~1% border on each side
        double separatorPercent = 0.005;  // ~0.5% for column separators
        
        // Effective area after removing borders
        double effectiveWidth = width * (1 - 2 * borderPercent);
        double effectiveHeight = height * (1 - 2 * borderPercent);
        double startX = width * borderPercent;
        double startY = height * borderPercent;
        
        // Account for 3 separators between 4 columns
        double colWidthWithSep = effectiveWidth / COLUMNS;
        double colWidth = colWidthWithSep * 0.98; // Slightly less to avoid separator
        double rowHeight = effectiveHeight / ROWS_PER_COLUMN;
        
        // Within each column: [QNum ~20%][A ~20%][B ~20%][C ~20%][D ~20%]
        // Bubbles start after question number
        double qNumWidth = 0.15;          // Question number takes 15%
        double bubbleStart = qNumWidth;   // Bubbles start after qnum
        double bubbleAreaWidth = 0.80;    // 4 bubbles share 80%
        double singleBubbleWidth = bubbleAreaWidth / CHOICES;
        
        // Vertical padding within each row
        double rowPaddingTop = 0.15;
        double rowPaddingBottom = 0.15;
        
        System.out.println("  Effective area: " + String.format("%.0f", effectiveWidth) + "x" + String.format("%.0f", effectiveHeight));
        System.out.println("  Column width: " + String.format("%.1f", colWidth) + 
                          ", Row height: " + String.format("%.1f", rowHeight));
        
        // Create debug visualization
        Mat debugImage = null;
        if (saveDebugImages) {
            debugImage = new Mat();
            cvtColor(answerSection, debugImage, COLOR_GRAY2BGR);
        }
        
        // Process each question
        for (int col = 0; col < COLUMNS; col++) {
            for (int row = 0; row < ROWS_PER_COLUMN; row++) {
                int questionNum = col * ROWS_PER_COLUMN + row + 1;
                
                // Calculate column and row pixel positions with borders accounted for
                int colStart = (int) (startX + col * colWidthWithSep);
                int rowStart = (int) (startY + row * rowHeight);
                
                // Check each choice (A, B, C, D)
                double[] fillRatios = new double[CHOICES];
                int markedCount = 0;
                int markedChoice = -1;
                
                for (int choice = 0; choice < CHOICES; choice++) {
                    // Calculate bubble region
                    // Position within column: qNum area + (choice index * bubble width) + centering offset
                    double bubbleCenterX = bubbleStart + (choice + 0.5) * singleBubbleWidth;
                    int bubbleX = colStart + (int) (colWidth * (bubbleCenterX - singleBubbleWidth * 0.35));
                    int bubbleY = rowStart + (int) (rowHeight * rowPaddingTop);
                    int bubbleW = (int) (colWidth * singleBubbleWidth * 0.6);
                    int bubbleH = (int) (rowHeight * (1.0 - rowPaddingTop - rowPaddingBottom) * 0.8);
                    
                    // Ensure within bounds
                    bubbleX = Math.max(0, Math.min(bubbleX, width - bubbleW - 1));
                    bubbleY = Math.max(0, Math.min(bubbleY, height - bubbleH - 1));
                    bubbleW = Math.min(bubbleW, width - bubbleX);
                    bubbleH = Math.min(bubbleH, height - bubbleY);
                    
                    if (bubbleW <= 0 || bubbleH <= 0) continue;
                    
                    // Sample the region
                    Rect roi = new Rect(bubbleX, bubbleY, bubbleW, bubbleH);
                    Mat bubbleRegion = answerSection.apply(roi);
                    
                    // Calculate fill ratio (white pixels / total pixels)
                    int whitePixels = countNonZero(bubbleRegion);
                    int totalPixels = bubbleW * bubbleH;
                    double fillRatio = (double) whitePixels / totalPixels;
                    fillRatios[choice] = fillRatio;
                    
                    if (fillRatio > fillThreshold) {
                        markedCount++;
                        markedChoice = choice;
                    }
                    
                    // Draw debug rectangle
                    if (debugImage != null) {
                        Scalar color;
                        if (fillRatio > fillThreshold) {
                            color = new Scalar(0, 255, 0, 0); // Green for marked
                        } else if (fillRatio > emptyThreshold) {
                            color = new Scalar(0, 255, 255, 0); // Yellow for uncertain
                        } else {
                            color = new Scalar(100, 100, 100, 0); // Gray for empty
                        }
                        rectangle(debugImage, roi, color, 1, LINE_8, 0);
                    }
                }
                
                // Determine answer
                if (markedCount == 0) {
                    result.answers[questionNum - 1] = null;
                    result.emptyCount++;
                } else if (markedCount == 1) {
                    result.answers[questionNum - 1] = CHOICE_LABELS[markedChoice];
                    result.confidences[questionNum - 1] = fillRatios[markedChoice];
                    result.detectedCount++;
                } else {
                    // Multiple marked - find the one with highest fill
                    int bestChoice = 0;
                    double bestFill = fillRatios[0];
                    for (int i = 1; i < CHOICES; i++) {
                        if (fillRatios[i] > bestFill) {
                            bestFill = fillRatios[i];
                            bestChoice = i;
                        }
                    }
                    result.answers[questionNum - 1] = CHOICE_LABELS[bestChoice];
                    result.confidences[questionNum - 1] = bestFill;
                    result.multipleCount++;
                }
            }
        }
        
        // Save debug image
        if (debugImage != null) {
            imwrite(debugOutputDir + "/08_grid_debug.png", debugImage);
            debugImage.release();
        }
        
        System.out.println("  Detected: " + result.detectedCount + 
                          ", Multiple: " + result.multipleCount + 
                          ", Empty: " + result.emptyCount);
        
        return result;
    }

    /**
     * Extract answers using adaptive grid detection.
     * First tries to detect the actual bubble positions, then uses grid sampling.
     */
    public GridResult extractAnswersAdaptive(Mat answerSection, Mat binaryImage) {
        // For now, use the basic grid approach
        // Could be enhanced to detect actual bubble positions first
        return extractAnswers(binaryImage);
    }

    // Setters
    public void setFillThreshold(double threshold) { this.fillThreshold = threshold; }
    public void setEmptyThreshold(double threshold) { this.emptyThreshold = threshold; }
    public void setSaveDebugImages(boolean save) { this.saveDebugImages = save; }
    public void setDebugOutputDir(String dir) { this.debugOutputDir = dir; }
}

