package org.example;

import org.bytedeco.opencv.opencv_core.*;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;

/**
 * Morphological approach to answer extraction:
 * 1. Apply erosion to eliminate thin outlines (unfilled bubbles)
 * 2. Only solid filled bubbles survive erosion
 * 3. Detect remaining white blobs as marked answers
 * 4. Map blob positions to grid (column, row, choice)
 */
public class MorphologicalAnswerExtractor {

    private static final int COLUMNS = 4;
    private static final int ROWS_PER_COLUMN = 15;
    private static final int CHOICES = 4;
    private static final String[] CHOICE_LABELS = {"A", "B", "C", "D"};
    
    // Minimum area for a blob to be considered a filled bubble
    private int minBlobArea = 100;
    private int maxBlobArea = 3000;
    
    private boolean saveDebugImages = false;
    private String debugOutputDir = "output";

    public static class Result {
        public String[] answers = new String[60];
        public double[] confidences = new double[60];
        public int detectedCount = 0;
    }

    /**
     * Extract answers using morphological erosion to isolate filled bubbles.
     */
    public Result extract(Mat binaryImage) {
        Result result = new Result();
        
        int width = binaryImage.cols();
        int height = binaryImage.rows();
        
        System.out.println("  Morphological extraction from: " + width + "x" + height);
        
        // Step 1: Apply erosion to remove thin outlines
        // Only solid filled bubbles will survive
        // Balance: enough to remove outlines, not too much to lose filled bubbles
        Mat eroded = new Mat();
        Mat kernel = getStructuringElement(MORPH_ELLIPSE, new Size(4, 4));
        erode(binaryImage, eroded, kernel, new Point(-1, -1), 2, BORDER_CONSTANT, morphologyDefaultBorderValue());
        
        System.out.println("  Applied erosion (3 iterations, 5x5 ellipse kernel)");
        
        if (saveDebugImages) {
            imwrite(debugOutputDir + "/11_eroded.png", eroded);
        }
        
        // Step 2: Find connected components (remaining blobs)
        Mat labels = new Mat();
        Mat stats = new Mat();
        Mat centroids = new Mat();
        int numLabels = connectedComponentsWithStats(eroded, labels, stats, centroids, 8, CV_32S);
        
        System.out.println("  Found " + (numLabels - 1) + " blobs after erosion");
        
        // Step 3: First pass - find the first valid blob to calibrate row positions
        // Must be in reasonable Y range (skip top 5% for header noise)
        int minValidY = (int)(height * 0.05);
        int firstBlobY = Integer.MAX_VALUE;
        for (int i = 1; i < numLabels; i++) {
            int blobArea = stats.ptr(i, CC_STAT_AREA).getInt();
            if (blobArea >= minBlobArea && blobArea <= maxBlobArea) {
                double cy = centroids.ptr(i, 1).getDouble();
                if (cy > minValidY && cy < firstBlobY) {
                    firstBlobY = (int)cy;
                }
            }
        }
        if (firstBlobY == Integer.MAX_VALUE) {
            firstBlobY = (int)(height * 0.1); // Fallback
        }
        
        // Auto-calibrate using first and last blob positions
        // Find last blob Y position
        int lastBlobY = 0;
        for (int i = 1; i < numLabels; i++) {
            int blobArea = stats.ptr(i, CC_STAT_AREA).getInt();
            if (blobArea >= minBlobArea && blobArea <= maxBlobArea) {
                double cy = centroids.ptr(i, 1).getDouble();
                if (cy > minValidY && cy > lastBlobY) {
                    lastBlobY = (int)cy;
                }
            }
        }
        
        // Calculate rowHeight from average spacing between blobs
        // Collect all blob Y positions, sort them, and calculate average spacing
        java.util.List<Integer> blobYs = new java.util.ArrayList<>();
        for (int i = 1; i < numLabels; i++) {
            int blobArea = stats.ptr(i, CC_STAT_AREA).getInt();
            if (blobArea >= minBlobArea && blobArea <= maxBlobArea) {
                double cy = centroids.ptr(i, 1).getDouble();
                if (cy > minValidY) {
                    blobYs.add((int)cy);
                }
            }
        }
        blobYs.sort(Integer::compareTo);
        
        // Calculate average spacing between consecutive blobs
        double avgSpacing = 0;
        if (blobYs.size() > 1) {
            int totalSpacing = 0;
            for (int i = 1; i < blobYs.size(); i++) {
                totalSpacing += blobYs.get(i) - blobYs.get(i-1);
            }
            avgSpacing = totalSpacing / (double)(blobYs.size() - 1);
        }
        
        // Use average spacing as rowHeight (blobs should be roughly one row apart)
        double rowHeight = avgSpacing;
        if (rowHeight < 40 || rowHeight > 70) {
            // Fallback: use span method
            int blobSpan = lastBlobY - firstBlobY;
            rowHeight = blobSpan / 14.0;
            if (rowHeight < 40) rowHeight = height / 16.0;
        }
        
        // First blob should be at row 0 center: firstBlobY = marginTop + 0.5 * rowHeight
        // Adjust marginTop so first blob maps exactly to row 0 center
        double row0Center = rowHeight * 0.5;
        int marginTop = (int)(firstBlobY - row0Center);
        if (marginTop < 0) marginTop = 0;
        
        int borderPad = 5;
        int contentStartY = marginTop;
        int contentHeight = height - contentStartY - borderPad;
        int contentStartX = borderPad;
        int contentWidth = width - 2 * borderPad;
        
        double colWidth = contentWidth / (double) COLUMNS;
        
        System.out.println("  Auto-calibrated: firstBlobY=" + firstBlobY + 
            ", lastBlobY=" + lastBlobY + ", avgSpacing=" + String.format("%.1f", avgSpacing) +
            ", marginTop=" + marginTop + ", rowHeight=" + String.format("%.1f", rowHeight));
        
        // Question number area within each column
        double qNumAreaRatio = 0.30; // 30% for question number
        
        int validBlobCount = 0;
        
        // Create debug visualization
        Mat debugImage = null;
        if (saveDebugImages) {
            debugImage = new Mat();
            cvtColor(binaryImage, debugImage, COLOR_GRAY2BGR);
        }
        
        // Process each blob (skip label 0 which is background)
        for (int i = 1; i < numLabels; i++) {
            // Get blob stats: [x, y, width, height, area]
            int blobX = stats.ptr(i, CC_STAT_LEFT).getInt();
            int blobY = stats.ptr(i, CC_STAT_TOP).getInt();
            int blobW = stats.ptr(i, CC_STAT_WIDTH).getInt();
            int blobH = stats.ptr(i, CC_STAT_HEIGHT).getInt();
            int blobArea = stats.ptr(i, CC_STAT_AREA).getInt();
            
            // Get centroid
            double centroidX = centroids.ptr(i, 0).getDouble();
            double centroidY = centroids.ptr(i, 1).getDouble();
            
            // Filter by area
            if (blobArea < minBlobArea || blobArea > maxBlobArea) {
                continue;
            }
            
            // Filter by aspect ratio (should be roughly circular)
            double aspectRatio = (double) blobW / blobH;
            if (aspectRatio < 0.5 || aspectRatio > 2.0) {
                continue;
            }
            
            validBlobCount++;
            
            // Map centroid to grid position
            double relX = centroidX - contentStartX;
            double relY = centroidY - contentStartY;
            
            // Skip if outside content area
            if (relX < 0 || relY < 0 || relX > contentWidth || relY > contentHeight) {
                continue;
            }
            
            // Determine column (0-3)
            int col = (int)(relX / colWidth);
            if (col >= COLUMNS) col = COLUMNS - 1;
            
            // Determine row (0-14) by finding closest row center
            // Row centers are at: rowCenter[i] = (i + 0.5) * rowHeight
            // Find which row center this blob is closest to
            int bestRow = 0;
            double minDist = Double.MAX_VALUE;
            for (int r = 0; r < ROWS_PER_COLUMN; r++) {
                double rowCenterY = (r + 0.5) * rowHeight;
                double dist = Math.abs(relY - rowCenterY);
                if (dist < minDist) {
                    minDist = dist;
                    bestRow = r;
                }
            }
            int row = bestRow;
            
            // Debug for problematic blobs
            if (centroidY > 500 && centroidY < 530) {
                double row8Center = (8 + 0.5) * rowHeight;
                double row9Center = (9 + 0.5) * rowHeight;
                System.out.println("      DEBUG: blobY=" + (int)centroidY + " relY=" + String.format("%.1f", relY) +
                    " row8Center=" + String.format("%.1f", row8Center) + " row9Center=" + String.format("%.1f", row9Center) +
                    " dist8=" + String.format("%.1f", Math.abs(relY - row8Center)) +
                    " dist9=" + String.format("%.1f", Math.abs(relY - row9Center)) + " → row=" + row);
            }
            if (centroidY > 650 && centroidY < 720) {
                double row12Center = (12 + 0.5) * rowHeight;
                double row13Center = (13 + 0.5) * rowHeight;
                double row14Center = (14 + 0.5) * rowHeight;
                System.out.println("      DEBUG: blobY=" + (int)centroidY + " relY=" + String.format("%.1f", relY) +
                    " row12Center=" + String.format("%.1f", row12Center) + " row13Center=" + String.format("%.1f", row13Center) +
                    " row14Center=" + String.format("%.1f", row14Center) + " → row=" + row);
            }
            
            // Determine choice (A-D) based on X position within column
            double xInCol = relX - (col * colWidth);
            double bubbleAreaStart = colWidth * qNumAreaRatio;
            
            if (xInCol < bubbleAreaStart) {
                // In question number area, not a bubble
                continue;
            }
            
            double bubbleAreaWidth = colWidth - bubbleAreaStart;
            double xInBubbleArea = xInCol - bubbleAreaStart;
            int choice = (int)(xInBubbleArea / (bubbleAreaWidth / CHOICES));
            if (choice >= CHOICES) choice = CHOICES - 1;
            
            // Calculate question number
            int qNum = col * ROWS_PER_COLUMN + row + 1;
            
            // Record answer (only if not already set, or if this blob is larger)
            if (result.answers[qNum - 1] == null) {
                result.answers[qNum - 1] = CHOICE_LABELS[choice];
                result.confidences[qNum - 1] = blobArea;
                result.detectedCount++;
            }
            
            // Debug output for first 17 questions
            if (qNum <= 17) {
                System.out.println("    Blob at (" + (int)centroidX + "," + (int)centroidY + 
                    ") relY=" + String.format("%.1f", relY) + " row=" + row + 
                    " area=" + blobArea + " → Q" + qNum + ":" + CHOICE_LABELS[choice]);
            }
            
            // Draw on debug image
            if (debugImage != null) {
                circle(debugImage, new Point((int)centroidX, (int)centroidY), 
                    10, new Scalar(0, 255, 0, 0), 2, LINE_AA, 0);
                // Draw label
                putText(debugImage, CHOICE_LABELS[choice], 
                    new Point((int)centroidX - 5, (int)centroidY + 5),
                    FONT_HERSHEY_SIMPLEX, 0.4, new Scalar(0, 255, 255, 0), 1, LINE_AA, false);
            }
        }
        
        System.out.println("  Valid blobs (filtered): " + validBlobCount);
        System.out.println("  Detected " + result.detectedCount + " answers");
        
        // Save debug image
        if (debugImage != null) {
            // Draw grid overlay
            for (int c = 0; c <= COLUMNS; c++) {
                int x = contentStartX + (int)(c * colWidth);
                line(debugImage, new Point(x, 0), new Point(x, height), 
                    new Scalar(100, 100, 100, 0), 1, LINE_8, 0);
            }
            for (int r = 0; r <= ROWS_PER_COLUMN; r++) {
                int y = contentStartY + (int)(r * rowHeight);
                line(debugImage, new Point(0, y), new Point(width, y), 
                    new Scalar(100, 100, 100, 0), 1, LINE_8, 0);
            }
            
            imwrite(debugOutputDir + "/12_morphological_debug.png", debugImage);
            debugImage.release();
        }
        
        // Cleanup
        eroded.release();
        kernel.release();
        labels.release();
        stats.release();
        centroids.release();
        
        return result;
    }

    // Setters
    public void setMinBlobArea(int area) { this.minBlobArea = area; }
    public void setMaxBlobArea(int area) { this.maxBlobArea = area; }
    public void setSaveDebugImages(boolean save) { this.saveDebugImages = save; }
    public void setDebugOutputDir(String dir) { this.debugOutputDir = dir; }
}

