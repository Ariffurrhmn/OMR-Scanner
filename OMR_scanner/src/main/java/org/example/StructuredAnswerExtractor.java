package org.example;

import org.bytedeco.opencv.opencv_core.*;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;

/**
 * Structured answer extraction using hierarchical approach:
 * 1. Divide into 4 column regions
 * 2. Within each column, divide into 15 row regions
 * 3. Within each row, sample 4 bubble positions for SOLID white circles only
 * 
 * This approach ignores unfilled bubble outlines and only detects filled answers.
 */
public class StructuredAnswerExtractor {

    private static final int COLUMNS = 4;
    private static final int ROWS_PER_COLUMN = 15;
    private static final int CHOICES = 4;
    private static final String[] CHOICE_LABELS = {"A", "B", "C", "D"};
    
    // Only detect circles that are mostly solid (filled bubbles)
    // Lower threshold since fill ratios vary (0.4-1.0 for marked, <0.35 for unmarked)
    private double solidFillThreshold = 0.38;
    
    private boolean saveDebugImages = false;
    private String debugOutputDir = "output";

    public static class Result {
        public String[] answers = new String[60];
        public double[] confidences = new double[60];
        public int answeredCount = 0;
    }

    /**
     * Extract answers using structured grid sampling.
     * Only detects solid white circles (filled answers).
     */
    public Result extract(Mat answerSection) {
        Result result = new Result();
        
        int width = answerSection.cols();
        int height = answerSection.rows();
        
        System.out.println("  Structured extraction from: " + width + "x" + height);
        
        // Add internal padding to exclude thick borders
        int borderPad = 8;
        int effectiveWidth = width - 2 * borderPad;
        int effectiveHeight = height - 2 * borderPad;
        int startX = borderPad;
        int startY = borderPad;
        
        // Skip header row (~5% of effective height)
        int headerHeight = (int)(effectiveHeight * 0.05);
        int contentHeight = effectiveHeight - headerHeight;
        int contentStartY = startY + headerHeight;
        
        // Calculate grid dimensions
        double colWidth = effectiveWidth / (double) COLUMNS;
        double rowHeight = contentHeight / (double) ROWS_PER_COLUMN;
        
        System.out.println("  Grid: col=" + (int)colWidth + "px, row=" + (int)rowHeight + "px");
        
        // Debug visualization
        Mat debugImage = null;
        if (saveDebugImages) {
            debugImage = new Mat();
            cvtColor(answerSection, debugImage, COLOR_GRAY2BGR);
        }
        
        // Process each question
        for (int col = 0; col < COLUMNS; col++) {
            int colX = startX + (int)(col * colWidth);
            
            for (int row = 0; row < ROWS_PER_COLUMN; row++) {
                int qNum = col * ROWS_PER_COLUMN + row + 1;
                int rowY = contentStartY + (int)(row * rowHeight);
                
                // Find marked answer in this row
                RowResult rowResult = analyzeRow(answerSection, colX, rowY, 
                    (int)colWidth, (int)rowHeight, qNum);
                
                result.answers[qNum - 1] = rowResult.answer;
                result.confidences[qNum - 1] = rowResult.confidence;
                if (rowResult.answer != null) {
                    result.answeredCount++;
                }
                
                // Debug visualization
                if (debugImage != null) {
                    // Draw row boundary
                    Rect rowRect = new Rect(colX, rowY, (int)colWidth, (int)rowHeight);
                    Scalar color = rowResult.answer != null ? 
                        new Scalar(0, 255, 0, 0) : new Scalar(80, 80, 80, 0);
                    rectangle(debugImage, rowRect, color, 1, LINE_8, 0);
                    
                    // Mark the detected answer bubble
                    if (rowResult.answerIndex >= 0) {
                        int bubbleX = colX + (int)(colWidth * 0.40) + 
                            rowResult.answerIndex * (int)(colWidth * 0.15);
                        int bubbleY = rowY + (int)(rowHeight * 0.5);
                        circle(debugImage, new Point(bubbleX, bubbleY), 8, 
                            new Scalar(0, 255, 255, 0), 2, LINE_AA, 0);
                    }
                }
            }
        }
        
        // Save debug image
        if (debugImage != null) {
            imwrite(debugOutputDir + "/10_structured_debug.png", debugImage);
            debugImage.release();
            System.out.println("  Saved debug image: 10_structured_debug.png");
        }
        
        System.out.println("  Detected " + result.answeredCount + " marked answers");
        
        return result;
    }

    /**
     * Result from analyzing a single row.
     */
    private static class RowResult {
        String answer;
        double confidence;
        int answerIndex = -1;
        double[] fillRatios = new double[4];
    }

    /**
     * Analyze a single row to find the marked bubble.
     * Scans across the row to find regions with high white pixel density.
     */
    private RowResult analyzeRow(Mat image, int colX, int rowY, int colWidth, int rowHeight, int qNum) {
        RowResult result = new RowResult();
        
        // Scan the bubble area of the row (excluding left margin for question number + borders)
        // Based on detected shift, the Q number area + borders takes about 30% of column width
        int leftMargin = (int)(colWidth * 0.30);
        int bubbleAreaWidth = colWidth - leftMargin;
        int bubbleAreaStart = colX + leftMargin;
        
        // Each bubble takes 25% of the remaining width (4 bubbles)
        double singleBubbleWidth = bubbleAreaWidth / 4.0;
        
        // Vertical padding to sample only the center of the row
        int padY = rowHeight / 4;
        int sampleH = rowHeight - 2 * padY;
        
        double bestFill = 0;
        int bestChoice = -1;
        
        for (int choice = 0; choice < CHOICES; choice++) {
            // Calculate bubble center position
            double bubbleCenterX = bubbleAreaStart + (choice + 0.5) * singleBubbleWidth;
            
            // Sample a square region centered on the bubble
            int sampleSize = Math.min((int)(singleBubbleWidth * 0.5), sampleH);
            int sampleX = (int)(bubbleCenterX - sampleSize / 2);
            int sampleY = rowY + (rowHeight - sampleSize) / 2;
            
            // Bounds check
            if (sampleX < 0) sampleX = 0;
            if (sampleX + sampleSize > image.cols()) sampleSize = image.cols() - sampleX;
            if (sampleY < 0) sampleY = 0;
            if (sampleY + sampleSize > image.rows()) sampleSize = image.rows() - sampleY;
            if (sampleSize <= 0) continue;
            
            // Extract region and calculate fill ratio
            Rect sampleRect = new Rect(sampleX, sampleY, sampleSize, sampleSize);
            Mat sampleRegion = image.apply(sampleRect);
            
            int whitePixels = countNonZero(sampleRegion);
            int totalPixels = sampleSize * sampleSize;
            double fillRatio = (double) whitePixels / totalPixels;
            
            result.fillRatios[choice] = fillRatio;
            
            // Track the best (most filled) bubble
            if (fillRatio > bestFill) {
                bestFill = fillRatio;
                bestChoice = choice;
            }
        }
        
        // Debug output for first 17 questions (the ones with answers)
        if (qNum <= 17) {
            System.out.printf("    Q%d: A=%.2f B=%.2f C=%.2f D=%.2f", 
                qNum, result.fillRatios[0], result.fillRatios[1], 
                result.fillRatios[2], result.fillRatios[3]);
        }
        
        // Only mark as answered if the best fill exceeds threshold
        // AND it's significantly higher than the others (to avoid false positives)
        if (bestFill > solidFillThreshold) {
            // Check that this bubble is clearly more filled than others
            double secondBest = 0;
            for (int i = 0; i < CHOICES; i++) {
                if (i != bestChoice && result.fillRatios[i] > secondBest) {
                    secondBest = result.fillRatios[i];
                }
            }
            
            // The marked bubble should be more filled than unfilled ones
            // Relaxed ratio for cases where fill ratios are close
            if (bestFill > secondBest * 1.10) {
                result.answer = CHOICE_LABELS[bestChoice];
                result.confidence = bestFill;
                result.answerIndex = bestChoice;
                
                if (qNum <= 17) {
                    System.out.println(" → " + result.answer);
                }
            } else {
                if (qNum <= 17) {
                    System.out.println(" → unclear");
                }
            }
        } else {
            if (qNum <= 17) {
                System.out.println(" → blank");
            }
        }
        
        return result;
    }

    // Setters
    public void setSolidFillThreshold(double threshold) { 
        this.solidFillThreshold = threshold; 
    }
    
    public void setSaveDebugImages(boolean save) { 
        this.saveDebugImages = save; 
    }
    
    public void setDebugOutputDir(String dir) { 
        this.debugOutputDir = dir; 
    }
}

