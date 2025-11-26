package org.example;

import org.bytedeco.opencv.opencv_core.*;

import java.util.*;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;

/**
 * Hybrid answer extraction that:
 * 1. Detects all circular bubbles in the answer section
 * 2. Maps detected bubbles to the expected 4-column × 15-row × 4-choice grid
 * 3. Determines which bubbles are marked
 * 
 * This is more robust than pure grid-based sampling because it finds
 * where bubbles actually are, rather than assuming positions.
 */
public class HybridAnswerExtractor {

    private static final int COLUMNS = 4;
    private static final int ROWS_PER_COLUMN = 15;
    private static final int CHOICES = 4;
    private static final String[] CHOICE_LABELS = {"A", "B", "C", "D"};

    // Bubble detection parameters
    private double minBubbleArea = 150;
    private double maxBubbleArea = 6000;
    private double minCircularity = 0.4;
    private double fillThreshold = 0.25; // Lower threshold for scanned images
    
    private boolean saveDebugImages = false;
    private String debugOutputDir = "output";

    public static class ExtractionResult {
        public String[] answers = new String[60];
        public double[] confidences = new double[60];
        public int bubblesDetected = 0;
        public int answersFound = 0;
    }

    /**
     * Extract answers using hybrid bubble detection + grid mapping.
     */
    public ExtractionResult extract(Mat answerSection) {
        ExtractionResult result = new ExtractionResult();
        
        int width = answerSection.cols();
        int height = answerSection.rows();
        
        System.out.println("  Hybrid extraction from: " + width + "x" + height);
        
        // Step 1: Detect all bubbles
        List<BubbleInfo> allBubbles = detectAllBubbles(answerSection);
        result.bubblesDetected = allBubbles.size();
        // Remove duplicate/overlapping bubbles
        allBubbles = removeOverlappingBubbles(allBubbles);
        result.bubblesDetected = allBubbles.size();
        System.out.println("  Detected " + allBubbles.size() + " unique bubbles");
        
        // Print fill ratio distribution
        int markedCount = 0;
        double maxFill = 0, minFill = 1;
        for (BubbleInfo b : allBubbles) {
            if (b.fillRatio > fillThreshold) markedCount++;
            maxFill = Math.max(maxFill, b.fillRatio);
            minFill = Math.min(minFill, b.fillRatio);
        }
        System.out.println("  Fill ratios: min=" + String.format("%.2f", minFill) + 
                          ", max=" + String.format("%.2f", maxFill) + 
                          ", marked=" + markedCount + " (threshold=" + fillThreshold + ")");
        
        // Debug: check Y distribution of bubbles (with header offset)
        double headerH = height * 0.06;
        double rowH = (height - headerH) / (double) ROWS_PER_COLUMN;
        int[] rowCounts = new int[4];
        for (BubbleInfo b : allBubbles) {
            double yInGrid = b.y - headerH;
            if (yInGrid < 0) continue;
            int row = (int) (yInGrid / rowH);
            if (row < 4) rowCounts[row]++;
        }
        System.out.println("  Bubbles in rows 0-3 (with header offset): " + 
            rowCounts[0] + ", " + rowCounts[1] + ", " + rowCounts[2] + ", " + rowCounts[3]);
        
        // Debug: check X distribution in first column
        double colW = width / (double) COLUMNS;
        int[] quartiles = new int[4]; // A, B, C, D areas
        for (BubbleInfo b : allBubbles) {
            if (b.x < colW) { // First column only
                int q = (int) (b.x / (colW / 4));
                if (q < 4) quartiles[q]++;
            }
        }
        System.out.println("  Col0 X distribution (A,B,C,D areas): " + 
            quartiles[0] + ", " + quartiles[1] + ", " + quartiles[2] + ", " + quartiles[3]);
        
        // Show X range
        int minX = Integer.MAX_VALUE, maxX = 0;
        for (BubbleInfo b : allBubbles) {
            if (b.x < colW) {
                minX = Math.min(minX, b.x);
                maxX = Math.max(maxX, b.x);
            }
        }
        System.out.println("  Col0 X range: " + minX + " to " + maxX + " (colW=" + (int)colW + ")");
        
        if (allBubbles.isEmpty()) {
            System.out.println("  ⚠ No bubbles detected!");
            return result;
        }
        
        // Step 2: Analyze bubble distribution to find grid parameters
        GridParameters gridParams = analyzeGrid(allBubbles, width, height);
        System.out.println("  Grid: " + gridParams);
        
        // Step 3: Map bubbles to grid cells
        Map<String, List<BubbleInfo>> grid = mapBubblesToGrid(allBubbles, gridParams);
        
        // Step 4: Extract answers from grid
        System.out.println("  First 5 questions debug:");
        for (int col = 0; col < COLUMNS; col++) {
            for (int row = 0; row < ROWS_PER_COLUMN; row++) {
                int qNum = col * ROWS_PER_COLUMN + row + 1;
                
                List<String> marked = new ArrayList<>();
                double bestConfidence = 0;
                StringBuilder debugInfo = new StringBuilder();
                
                for (int choice = 0; choice < CHOICES; choice++) {
                    String key = col + "," + row + "," + choice;
                    List<BubbleInfo> bubbles = grid.get(key);
                    
                    if (bubbles != null && !bubbles.isEmpty()) {
                        for (BubbleInfo b : bubbles) {
                            debugInfo.append(CHOICE_LABELS[choice]).append(":")
                                    .append(String.format("%.2f", b.fillRatio)).append(" ");
                            if (b.fillRatio > fillThreshold) {
                                marked.add(CHOICE_LABELS[choice]);
                                if (b.fillRatio > bestConfidence) {
                                    bestConfidence = b.fillRatio;
                                }
                            }
                        }
                    }
                }
                
                // Debug output for first 5 questions - show all choices
                if (qNum <= 5) {
                    // Count bubbles per choice
                    StringBuilder allChoices = new StringBuilder();
                    for (int c = 0; c < CHOICES; c++) {
                        String k = col + "," + row + "," + c;
                        int count = grid.containsKey(k) ? grid.get(k).size() : 0;
                        allChoices.append(CHOICE_LABELS[c]).append(":").append(count).append(" ");
                    }
                    System.out.println("    Q" + qNum + " [" + allChoices + "] fills: " + debugInfo + 
                        "→ " + (marked.isEmpty() ? "-" : String.join(",", marked)));
                }
                
                if (marked.isEmpty()) {
                    result.answers[qNum - 1] = null;
                } else if (marked.size() == 1) {
                    result.answers[qNum - 1] = marked.get(0);
                    result.confidences[qNum - 1] = bestConfidence;
                    result.answersFound++;
                } else {
                    // Multiple - take highest fill ratio
                    result.answers[qNum - 1] = marked.get(0);
                    result.confidences[qNum - 1] = bestConfidence;
                    result.answersFound++;
                }
            }
        }
        
        // Debug visualization
        if (saveDebugImages) {
            saveDebugImage(answerSection, allBubbles, gridParams);
        }
        
        System.out.println("  Extracted " + result.answersFound + " answers");
        return result;
    }

    /**
     * Simple bubble info class.
     */
    private static class BubbleInfo {
        int x, y;       // Center
        int radius;     // Approximate radius
        double area;
        double fillRatio;
        boolean isMarked;
        
        @Override
        public String toString() {
            return String.format("Bubble(%d,%d r=%d fill=%.2f)", x, y, radius, fillRatio);
        }
    }

    /**
     * Grid parameters determined from bubble distribution.
     */
    private static class GridParameters {
        double colWidth;    // Width of each column
        double rowHeight;   // Height of each row
        double bubbleSpacing; // Horizontal spacing between bubbles in a row
        double xOffset;     // X offset to first bubble column
        double yOffset;     // Y offset to first bubble row
        
        @Override
        public String toString() {
            return String.format("colW=%.0f rowH=%.0f spacing=%.0f offset=(%.0f,%.0f)", 
                colWidth, rowHeight, bubbleSpacing, xOffset, yOffset);
        }
    }

    /**
     * Detect all circular bubbles in the image.
     */
    private List<BubbleInfo> detectAllBubbles(Mat binaryImage) {
        List<BubbleInfo> bubbles = new ArrayList<>();
        
        // Find contours
        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        findContours(binaryImage.clone(), contours, hierarchy, RETR_LIST, CHAIN_APPROX_SIMPLE);
        
        for (int i = 0; i < contours.size(); i++) {
            Mat contour = contours.get(i);
            double area = contourArea(contour);
            
            // Filter by area
            if (area < minBubbleArea || area > maxBubbleArea) continue;
            
            // Check circularity
            double perimeter = arcLength(contour, true);
            if (perimeter == 0) continue;
            
            double circularity = 4 * Math.PI * area / (perimeter * perimeter);
            if (circularity < minCircularity) continue;
            
            // Get bounding box and center
            Rect bbox = boundingRect(contour);
            int cx = bbox.x() + bbox.width() / 2;
            int cy = bbox.y() + bbox.height() / 2;
            int radius = (bbox.width() + bbox.height()) / 4;
            
            // Calculate fill ratio using bounding box sampling (simpler and more reliable)
            Rect sampleArea = new Rect(
                Math.max(0, cx - radius/2),
                Math.max(0, cy - radius/2),
                Math.min(radius, binaryImage.cols() - cx + radius/2),
                Math.min(radius, binaryImage.rows() - cy + radius/2)
            );
            
            if (sampleArea.width() <= 0 || sampleArea.height() <= 0) continue;
            
            Mat bubbleRegion = binaryImage.apply(sampleArea);
            int filledPixels = countNonZero(bubbleRegion);
            int totalPixels = sampleArea.width() * sampleArea.height();
            double fillRatio = (double) filledPixels / totalPixels;
            
            BubbleInfo bubble = new BubbleInfo();
            bubble.x = cx;
            bubble.y = cy;
            bubble.radius = radius;
            bubble.area = area;
            bubble.fillRatio = fillRatio;
            bubble.isMarked = fillRatio > fillThreshold;
            
            bubbles.add(bubble);
        }
        
        hierarchy.release();
        return bubbles;
    }

    /**
     * Analyze bubble distribution to determine grid parameters.
     */
    private GridParameters analyzeGrid(List<BubbleInfo> bubbles, int width, int height) {
        GridParameters params = new GridParameters();
        
        // The answer section has a header row at the top
        // Account for ~6% header space
        double headerHeight = height * 0.06;
        double effectiveHeight = height - headerHeight;
        
        params.colWidth = width / (double) COLUMNS;
        params.rowHeight = effectiveHeight / (double) ROWS_PER_COLUMN;
        params.bubbleSpacing = params.colWidth / (CHOICES + 1);
        params.xOffset = params.bubbleSpacing * 0.8;
        params.yOffset = headerHeight; // Start after header
        
        return params;
    }

    /**
     * Map bubbles to grid cells based on their positions.
     */
    private Map<String, List<BubbleInfo>> mapBubblesToGrid(List<BubbleInfo> bubbles, GridParameters params) {
        Map<String, List<BubbleInfo>> grid = new HashMap<>();
        
        for (BubbleInfo bubble : bubbles) {
            // Determine which column (0-3)
            int col = (int) (bubble.x / params.colWidth);
            col = Math.min(col, COLUMNS - 1);
            
            // Determine which row (0-14) accounting for header offset
            double yInGrid = bubble.y - params.yOffset;
            if (yInGrid < 0) continue; // In header area, skip
            
            int row = (int) (yInGrid / params.rowHeight);
            if (row >= ROWS_PER_COLUMN) continue; // Beyond last row
            
            // Determine which choice (0-3 = A-D)
            double xInCol = bubble.x - (col * params.colWidth);
            
            // Question numbers take up ~40% of column width, bubbles are in remaining 60%
            double qNumAreaEnd = params.colWidth * 0.40;
            
            if (xInCol < qNumAreaEnd) {
                continue; // In question number area, not a bubble
            }
            
            // Map bubble area (43% to 100% of column) to 4 choices
            double bubbleAreaWidth = params.colWidth - qNumAreaEnd;
            double xInBubbleArea = xInCol - qNumAreaEnd;
            int choice = (int) (xInBubbleArea / (bubbleAreaWidth / CHOICES));
            choice = Math.max(0, Math.min(choice, CHOICES - 1));
            
            String key = col + "," + row + "," + choice;
            grid.computeIfAbsent(key, k -> new ArrayList<>()).add(bubble);
        }
        
        return grid;
    }

    /**
     * Save debug visualization.
     */
    private void saveDebugImage(Mat binaryImage, List<BubbleInfo> bubbles, GridParameters params) {
        Mat debug = new Mat();
        cvtColor(binaryImage, debug, COLOR_GRAY2BGR);
        
        // Draw detected bubbles
        for (BubbleInfo b : bubbles) {
            Scalar color = b.isMarked ? 
                new Scalar(0, 255, 0, 0) :   // Green for marked
                new Scalar(128, 128, 128, 0); // Gray for unmarked
            circle(debug, new Point(b.x, b.y), b.radius, color, 2, LINE_AA, 0);
        }
        
        // Draw grid lines
        for (int i = 1; i < COLUMNS; i++) {
            int x = (int) (i * params.colWidth);
            line(debug, new Point(x, 0), new Point(x, debug.rows()), 
                new Scalar(255, 0, 0, 0), 1, LINE_8, 0);
        }
        for (int i = 1; i < ROWS_PER_COLUMN; i++) {
            int y = (int) (i * params.rowHeight);
            line(debug, new Point(0, y), new Point(debug.cols(), y), 
                new Scalar(255, 0, 0, 0), 1, LINE_8, 0);
        }
        
        imwrite(debugOutputDir + "/09_hybrid_debug.png", debug);
        debug.release();
    }

    /**
     * Remove overlapping/duplicate bubble detections.
     */
    private List<BubbleInfo> removeOverlappingBubbles(List<BubbleInfo> bubbles) {
        List<BubbleInfo> unique = new ArrayList<>();
        
        for (BubbleInfo bubble : bubbles) {
            boolean isDuplicate = false;
            for (BubbleInfo existing : unique) {
                double dist = Math.sqrt(Math.pow(bubble.x - existing.x, 2) + 
                                       Math.pow(bubble.y - existing.y, 2));
                if (dist < (bubble.radius + existing.radius) * 0.7) {
                    // Overlapping - keep the one with higher fill ratio
                    if (bubble.fillRatio > existing.fillRatio) {
                        unique.remove(existing);
                        unique.add(bubble);
                    }
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) {
                unique.add(bubble);
            }
        }
        
        return unique;
    }

    // Setters
    public void setFillThreshold(double threshold) { this.fillThreshold = threshold; }
    public void setSaveDebugImages(boolean save) { this.saveDebugImages = save; }
    public void setDebugOutputDir(String dir) { this.debugOutputDir = dir; }
}

