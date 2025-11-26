package org.example;

import org.bytedeco.opencv.opencv_core.*;

import java.util.*;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;

/**
 * Row-based answer extraction:
 * 1. Detect horizontal row rectangles (each containing 4 bubbles)
 * 2. Within each row, detect which bubble (A/B/C/D) is filled
 * 
 * This is more robust because it uses the actual structure of the OMR sheet.
 */
public class RowBasedAnswerExtractor {

    private static final int COLUMNS = 4;
    private static final int ROWS_PER_COLUMN = 15;
    private static final int CHOICES = 4;
    private static final String[] CHOICE_LABELS = {"A", "B", "C", "D"};
    
    // Row detection parameters
    private int minRowWidth = 150;      // Minimum width for a row rectangle
    private int minRowHeight = 20;      // Minimum height for a row rectangle
    private int maxRowHeight = 100;     // Maximum height for a row rectangle
    private double minRowAspectRatio = 2.0; // Width should be at least 2x height
    private int minRowArea = 3000;      // Minimum area for a row
    private int maxRowArea = 100000;    // Maximum area for a row
    
    // Bubble detection within row
    private double fillThreshold = 0.40; // 40% filled = marked
    
    // Auto-calibration
    private BubblePositionCalibrator calibrator = new BubblePositionCalibrator();
    
    private boolean saveDebugImages = false;
    private String debugOutputDir = "output";

    public static class Result {
        public String[] answers = new String[60];
        public double[] confidences = new double[60];
        public int detectedCount = 0;
    }

    /**
     * Extract answers by first detecting rows, then bubbles within each row.
     */
    public Result extract(Mat binaryImage) {
        Result result = new Result();
        
        int width = binaryImage.cols();
        int height = binaryImage.rows();
        
        System.out.println("  Row-based extraction from: " + width + "x" + height);
        
        // Step 1: Detect horizontal row rectangles
        List<RowInfo> rows = detectRows(binaryImage);
        System.out.println("  Detected " + rows.size() + " row rectangles");
        
        // Debug: show X distribution
        if (!rows.isEmpty()) {
            int minX = rows.stream().mapToInt(r -> r.rect.x()).min().orElse(0);
            int maxX = rows.stream().mapToInt(r -> r.rect.x() + r.rect.width()).max().orElse(0);
            System.out.println("  Row X range: " + minX + " to " + maxX + " (image width: " + width + ")");
        }
        
        if (rows.isEmpty()) {
            System.out.println("  ⚠ No rows detected!");
            return result;
        }
        
        // Step 2: Deduplicate overlapping rows
        rows = deduplicateRows(rows);
        System.out.println("  After deduplication: " + rows.size() + " unique rows");
        
        // Step 3: Sort rows by Y position (top to bottom)
        rows.sort(Comparator.comparingInt(r -> r.rect.y()));
        
        // Step 3.5: Auto-calibrate from first few rows
        if (rows.size() >= 3) {
            List<BubblePositionCalibrator.RowInfo> sampleRows = new ArrayList<>();
            int sampleCount = Math.min(5, rows.size());
            for (int i = 0; i < sampleCount; i++) {
                RowInfo row = rows.get(i);
                sampleRows.add(new BubblePositionCalibrator.RowInfo(row.rect));
            }
            calibrator.calibrate(sampleRows, binaryImage);
        }
        
        // Step 4: Map rows to questions and detect answers
        mapRowsToQuestions(rows, binaryImage, result, width, height);
        
        System.out.println("  Detected " + result.detectedCount + " answers");
        
        // Debug visualization
        if (saveDebugImages) {
            saveDebugImage(binaryImage, rows, result);
        }
        
        return result;
    }

    /**
     * Information about a detected row.
     */
    private static class RowInfo {
        Rect rect;
        int rowIndex = -1;  // Which question row (0-14) within column
        int colIndex = -1;  // Which column (0-3)
        
        @Override
        public String toString() {
            return String.format("Row[%d,%d] at (%d,%d) %dx%d", 
                colIndex, rowIndex, rect.x(), rect.y(), rect.width(), rect.height());
        }
    }

    /**
     * Detect horizontal row rectangles in the answer section.
     */
    private List<RowInfo> detectRows(Mat binaryImage) {
        List<RowInfo> rows = new ArrayList<>();
        
        // Find contours - use RETR_TREE to get all contours including nested ones
        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        findContours(binaryImage.clone(), contours, hierarchy, RETR_TREE, CHAIN_APPROX_SIMPLE);
        
        for (int i = 0; i < contours.size(); i++) {
            Mat contour = contours.get(i);
            double area = contourArea(contour);
            
            // Filter by area (rows should be reasonably sized)
            if (area < minRowArea || area > maxRowArea) continue;
            
            // Get bounding rectangle
            Rect bbox = boundingRect(contour);
            
            // Filter by dimensions
            if (bbox.width() < minRowWidth) continue;
            if (bbox.height() < minRowHeight || bbox.height() > maxRowHeight) continue;
            
            // Check aspect ratio (rows are wide, not tall)
            double aspectRatio = (double) bbox.width() / bbox.height();
            if (aspectRatio < minRowAspectRatio) continue;
            
            // Approximate to polygon to check if it's roughly rectangular
            Mat approx = new Mat();
            double peri = arcLength(contour, true);
            approxPolyDP(contour, approx, 0.02 * peri, true);
            
            // Should have 4 vertices (rectangle)
            if (approx.rows() >= 4 && approx.rows() <= 8) {
                RowInfo row = new RowInfo();
                row.rect = bbox;
                rows.add(row);
            }
            
            approx.release();
        }
        
        hierarchy.release();
        return rows;
    }

    /**
     * Deduplicate overlapping rows - keep the largest one per column.
     * Allow multiple rows at same Y if they're in different columns.
     */
    private List<RowInfo> deduplicateRows(List<RowInfo> rows) {
        List<RowInfo> unique = new ArrayList<>();
        
        for (RowInfo row : rows) {
            boolean isDuplicate = false;
            for (RowInfo existing : unique) {
                // Check if rows overlap significantly
                // Same Y position (± 10 pixels) AND same X position (± 50 pixels) = duplicate
                if (Math.abs(row.rect.y() - existing.rect.y()) < 10 &&
                    Math.abs(row.rect.x() - existing.rect.x()) < 50) {
                    // Keep the one with larger area
                    if (row.rect.width() * row.rect.height() > 
                        existing.rect.width() * existing.rect.height()) {
                        unique.remove(existing);
                        unique.add(row);
                    }
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) {
                unique.add(row);
            }
        }
        
        return unique;
    }

    /**
     * Map detected rows to question positions and extract answers.
     */
    private void mapRowsToQuestions(List<RowInfo> rows, Mat binaryImage, 
                                   Result result, int imageWidth, int imageHeight) {
        if (rows.isEmpty()) return;
        
        // Account for header
        int headerHeight = (int)(imageHeight * 0.05);
        int contentHeight = imageHeight - headerHeight;
        double rowHeight = contentHeight / (double) ROWS_PER_COLUMN;
        
        // Determine column width
        double colWidth = imageWidth / (double) COLUMNS;
        
        // Group rows by Y position (rows at similar Y are in the same question row across columns)
        // Sort by Y first, then group with dynamic tolerance
        rows.sort(Comparator.comparingInt(r -> r.rect.y()));
        
        List<List<RowInfo>> yGroups = new ArrayList<>();
        List<RowInfo> currentGroup = new ArrayList<>();
        int lastY = -100;
        
        for (RowInfo row : rows) {
            int y = row.rect.y();
            // Start new group if Y differs by more than 20 pixels from last
            if (y - lastY > 20 && !currentGroup.isEmpty()) {
                yGroups.add(currentGroup);
                currentGroup = new ArrayList<>();
            }
            currentGroup.add(row);
            lastY = y;
        }
        if (!currentGroup.isEmpty()) {
            yGroups.add(currentGroup);
        }
        
        // Convert to map for compatibility
        Map<Integer, List<RowInfo>> rowsByY = new HashMap<>();
        for (List<RowInfo> group : yGroups) {
            int avgY = (int) group.stream().mapToInt(r -> r.rect.y()).average().orElse(0);
            rowsByY.put(avgY, group);
        }
        
        List<Integer> sortedYKeys = new ArrayList<>(rowsByY.keySet());
        sortedYKeys.sort(Integer::compareTo);
        
        System.out.println("  Grouped into " + sortedYKeys.size() + " Y-levels");
        
        int globalRowIndex = 0;
        for (int yKey : sortedYKeys) {
            List<RowInfo> rowGroup = rowsByY.get(yKey);
            
            // Sort by X position (left to right = column 0 to 3)
            rowGroup.sort(Comparator.comparingInt(r -> r.rect.x()));
            
            System.out.println("    Y-level " + yKey + ": " + rowGroup.size() + " rows at X positions: " +
                rowGroup.stream().map(r -> String.valueOf(r.rect.x())).collect(java.util.stream.Collectors.joining(", ")));
            
            // Assign to columns based on X position
            for (RowInfo row : rowGroup) {
                // Determine column from X position
                int col = (int)(row.rect.x() / colWidth);
                if (col >= COLUMNS) col = COLUMNS - 1;
                if (col < 0) col = 0;
                
                row.colIndex = col;
                row.rowIndex = globalRowIndex;
                
                if (globalRowIndex <= 2 && rowGroup.size() >= 2) {
                    System.out.println("      DEBUG: row at X=" + row.rect.x() + 
                        ", colWidth=" + String.format("%.0f", colWidth) + 
                        ", calculated col=" + col);
                }
                
                // Extract answer from this row
                String answer = detectAnswerInRow(binaryImage, row.rect);
                
                int qNum = col * ROWS_PER_COLUMN + globalRowIndex + 1;
                if (qNum <= 17) {
                    System.out.println("      " + row + " answer=" + (answer != null ? answer : "null") + " → Q" + qNum);
                }
                
                // Always set the answer (even if null) for all valid question numbers
                if (qNum >= 1 && qNum <= 60) {
                    result.answers[qNum - 1] = answer;
                    if (answer != null) {
                        result.confidences[qNum - 1] = 0.9;
                        result.detectedCount++;
                    } else {
                        result.confidences[qNum - 1] = 0.0;
                    }
                }
            }
            
            globalRowIndex++;
            if (globalRowIndex >= ROWS_PER_COLUMN) break;
        }
    }

    /**
     * Detect which bubble (A/B/C/D) is filled within a row rectangle.
     * 
     * Approach: Try multiple alignments and pick the one with the clearest winner.
     * This handles slight drift in row rectangles across the page.
     */
    private String detectAnswerInRow(Mat binaryImage, Rect rowRect) {
        // Extract the row region
        Mat rowRegion = binaryImage.apply(rowRect);
        
        int rowWidth = rowRect.width();
        int rowHeight = rowRect.height();
        
        // Sample vertical center (avoid row borders)
        int padY = rowHeight / 4;
        int sampleStartY = padY;
        int sampleHeight = rowHeight - 2 * padY;
        if (sampleHeight <= 0) sampleHeight = rowHeight;
        
        // For narrower rows (columns 1-3), use different ratios
        // Columns 1-3 have less space for question numbers, so bubbles start earlier
        double[] qNumRatios;
        if (rowWidth < 200) {
            // Narrower rows (columns 1-3) - bubbles start earlier
            qNumRatios = new double[]{0.05, 0.08, 0.10, 0.12};
        } else {
            // Wider rows (column 0) - has question number area
            qNumRatios = new double[]{0.08, 0.10, 0.12, 0.14};
        }
        
        double bestOverallRatio = 0;
        int bestOverallChoice = -1;
        int bestOverallMax = 0;
        
        for (double qNumRatio : qNumRatios) {
            int bubbleAreaStart = (int)(rowWidth * qNumRatio);
            int bubbleAreaWidth = rowWidth - bubbleAreaStart;
            int sectionWidth = bubbleAreaWidth / CHOICES;
            
            int[] counts = new int[CHOICES];
            int maxCount = 0;
            int bestChoice = -1;
            
            for (int choice = 0; choice < CHOICES; choice++) {
                int sectionStartX = bubbleAreaStart + (choice * sectionWidth);
                
                // Sample center of each section
                int padX = sectionWidth / 5;
                int sampleStartX = sectionStartX + padX;
                int sampleWidth = sectionWidth - 2 * padX;
                
                if (sampleWidth <= 0) sampleWidth = sectionWidth;
                if (sampleStartX + sampleWidth > rowWidth) {
                    sampleWidth = rowWidth - sampleStartX;
                }
                if (sampleWidth <= 0) continue;
                
                Rect sampleRect = new Rect(sampleStartX, sampleStartY, sampleWidth, sampleHeight);
                Mat sampleRegion = rowRegion.apply(sampleRect);
                
                int whitePixels = countNonZero(sampleRegion);
                counts[choice] = whitePixels;
                
                if (whitePixels > maxCount) {
                    maxCount = whitePixels;
                    bestChoice = choice;
                }
                
                sampleRegion.release();
            }
            
            // Find second best for this alignment
            int secondBest = 0;
            for (int i = 0; i < CHOICES; i++) {
                if (i != bestChoice && counts[i] > secondBest) {
                    secondBest = counts[i];
                }
            }
            
            // Calculate how clear this winner is
            double ratio = (secondBest > 0) ? (double) maxCount / secondBest : maxCount;
            
            // Keep track of the alignment that gives the clearest winner
            if (ratio > bestOverallRatio) {
                bestOverallRatio = ratio;
                bestOverallChoice = bestChoice;
                bestOverallMax = maxCount;
            }
        }
        
        rowRegion.release();
        
        if (bestOverallChoice < 0) return null;
        
        // Require clear winner with minimum pixel count
        // Adjust thresholds based on row width (narrower rows need lower thresholds)
        int minPixels = (rowWidth < 200) ? 20 : 40;  // Lower threshold for narrow rows
        int minPixelsStrong = (rowWidth < 200) ? 40 : 80;
        double minRatio = (rowWidth < 200) ? 1.2 : 1.4;  // Lower ratio for narrow rows
        double minRatioWeak = (rowWidth < 200) ? 1.05 : 1.15;
        
        // For very narrow rows (columns 2-3), be even more lenient
        if (rowWidth < 160) {
            minPixels = 15;
            minPixelsStrong = 30;
            minRatio = 1.15;
            minRatioWeak = 1.05;
        }
        
        if (bestOverallRatio > minRatio && bestOverallMax > minPixelsStrong) {
            return CHOICE_LABELS[bestOverallChoice];
        }
        
        if (bestOverallRatio > minRatioWeak && bestOverallMax > minPixels) {
            return CHOICE_LABELS[bestOverallChoice];
        }
        
        return null;
    }
    
    /**
     * Fallback method: sample expected bubble positions.
     * Uses calibrated positions if available.
     */
    private String detectAnswerBySampling(Mat binaryImage, Rect rowRect) {
        Mat rowRegion = binaryImage.apply(rowRect);
        int rowWidth = rowRect.width();
        int rowHeight = rowRect.height();
        
        double qNumRatio;
        double[] bubblePos;
        
        // Use calibrated positions if available
        if (calibrator.isCalibrated()) {
            qNumRatio = calibrator.getQuestionNumberRatio();
            bubblePos = calibrator.getBubblePositions();
        } else {
            // Default positions
            qNumRatio = 0.30;
            bubblePos = new double[]{0.125, 0.375, 0.625, 0.875};
        }
        
        int bubbleAreaStart = (int)(rowWidth * qNumRatio);
        int bubbleAreaWidth = rowWidth - bubbleAreaStart;
        
        int padY = rowHeight / 5;
        int sampleH = rowHeight - 2 * padY;
        int sampleY = padY;
        int sampleSize = Math.min(bubbleAreaWidth / 6, sampleH);  // Sample size based on bubble area
        
        double[] fillRatios = new double[CHOICES];
        
        for (int choice = 0; choice < CHOICES; choice++) {
            // Calculate bubble center using calibrated position
            int bubbleCenterX = bubbleAreaStart + (int)(bubbleAreaWidth * bubblePos[choice]);
            
            int sampleX = bubbleCenterX - sampleSize / 2;
            if (sampleX < 0) sampleX = 0;
            if (sampleX + sampleSize > rowWidth) sampleSize = rowWidth - sampleX;
            if (sampleSize <= 0 || sampleH <= 0) continue;
            
            Rect sampleRect = new Rect(sampleX, sampleY, sampleSize, sampleH);
            Mat sampleRegion = rowRegion.apply(sampleRect);
            
            int whitePixels = countNonZero(sampleRegion);
            int totalPixels = sampleSize * sampleH;
            double fillRatio = (double) whitePixels / totalPixels;
            fillRatios[choice] = fillRatio;
        }
        
        rowRegion.release();
        
        // Find best choice
        int bestIndex = -1;
        double bestFill = 0;
        for (int i = 0; i < CHOICES; i++) {
            if (fillRatios[i] > bestFill) {
                bestFill = fillRatios[i];
                bestIndex = i;
            }
        }
        
        if (bestIndex < 0) return null;
        
        // Find second best
        double secondBest = 0;
        for (int i = 0; i < CHOICES; i++) {
            if (i != bestIndex && fillRatios[i] > secondBest) {
                secondBest = fillRatios[i];
            }
        }
        
        // Return if best is significantly higher than second
        if (bestFill > fillThreshold * 0.7 && bestFill > secondBest * 1.15) {
            return CHOICE_LABELS[bestIndex];
        }
        
        return null;
    }

    /**
     * Save debug visualization.
     */
    private void saveDebugImage(Mat binaryImage, List<RowInfo> rows, Result result) {
        Mat debug = new Mat();
        cvtColor(binaryImage, debug, COLOR_GRAY2BGR);
        
        // Draw detected rows
        for (RowInfo row : rows) {
            Scalar color = row.rowIndex >= 0 ? 
                new Scalar(0, 255, 0, 0) :   // Green for mapped rows
                new Scalar(255, 0, 0, 0);    // Red for unmapped rows
            rectangle(debug, row.rect, color, 2, LINE_8, 0);
            
            // Draw label
            if (row.rowIndex >= 0) {
                int qNum = row.colIndex * ROWS_PER_COLUMN + row.rowIndex + 1;
                String label = "Q" + qNum + ":" + (result.answers[qNum - 1] != null ? result.answers[qNum - 1] : "-");
                putText(debug, label, new Point(row.rect.x() + 5, row.rect.y() + 15),
                    FONT_HERSHEY_SIMPLEX, 0.4, new Scalar(255, 255, 0, 0), 1, LINE_AA, false);
            }
        }
        
        imwrite(debugOutputDir + "/13_row_based_debug.png", debug);
        debug.release();
    }

    // Setters
    public void setFillThreshold(double threshold) { this.fillThreshold = threshold; }
    public void setSaveDebugImages(boolean save) { this.saveDebugImages = save; }
    public void setDebugOutputDir(String dir) { this.debugOutputDir = dir; }
}


