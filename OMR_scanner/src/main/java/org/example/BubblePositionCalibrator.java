package org.example;

import org.bytedeco.opencv.opencv_core.*;
import java.util.*;
import java.util.Comparator;

import static org.bytedeco.opencv.global.opencv_imgproc.*;
import static org.bytedeco.opencv.global.opencv_core.*;

/**
 * Auto-calibrates bubble positions by learning from detected rows.
 * This makes the system adaptive to different OMR sheet layouts.
 */
public class BubblePositionCalibrator {
    
    private static final String[] CHOICE_LABELS = {"A", "B", "C", "D"};
    
    private double questionNumberRatio = 0.35;  // Learned from image
    private double[] bubblePositions = new double[4];  // Relative positions of A/B/C/D
    private boolean calibrated = false;
    
    /**
     * Information about a detected blob in a row.
     */
    private static class BlobInfo {
        int centerX;
        double area;
    }
    
    /**
     * Information about a row (compatible with RowBasedAnswerExtractor.RowInfo).
     */
    public static class RowInfo {
        public Rect rect;
        
        public RowInfo(Rect rect) {
            this.rect = rect;
        }
    }
    
    /**
     * Calibrate from a set of detected rows with known bubble positions.
     */
    public void calibrate(List<RowInfo> sampleRows, Mat binaryImage) {
        if (sampleRows.isEmpty()) return;
        
        List<Double> qNumRatios = new ArrayList<>();
        List<List<Double>> bubbleXPositions = new ArrayList<>();
        
        // Analyze each sample row
        for (RowInfo row : sampleRows) {
            Mat rowRegion = binaryImage.apply(row.rect);
            int rowWidth = row.rect.width();
            
            // Find all white blobs in this row
            List<BlobInfo> blobs = findBlobsInRow(rowRegion);
            
            if (blobs.size() >= 1) {
                // Find the leftmost blob (should be in question number area or first bubble)
                blobs.sort(Comparator.comparingInt(b -> b.centerX));
                
                // Estimate question number area - look for gap between QNum and bubbles
                // The first significant blob after a gap is likely the first bubble
                int leftmostBlobX = blobs.get(0).centerX;
                
                // If we have multiple blobs, find the gap
                if (blobs.size() >= 2) {
                    // Look for the largest gap (should be between QNum and bubbles)
                    int maxGap = 0;
                    int gapStart = 0;
                    for (int i = 0; i < blobs.size() - 1; i++) {
                        int gap = blobs.get(i + 1).centerX - blobs.get(i).centerX;
                        if (gap > maxGap) {
                            maxGap = gap;
                            gapStart = blobs.get(i).centerX;
                        }
                    }
                    
                    // If gap is significant (>15% of row width), use it as QNum boundary
                    if (maxGap > rowWidth * 0.15) {
                        double qNumRatio = (double) gapStart / rowWidth;
                        qNumRatios.add(qNumRatio);
                        
                        // Collect bubble positions (blobs after the gap)
                        int bubbleAreaStart = gapStart + maxGap / 2;
                        List<Double> bubbleXs = new ArrayList<>();
                        for (BlobInfo blob : blobs) {
                            if (blob.centerX > bubbleAreaStart && blob.area > 20) {
                                double relativeX = (double)(blob.centerX - bubbleAreaStart) / (rowWidth - bubbleAreaStart);
                                if (relativeX >= 0 && relativeX <= 1.0) {
                                    bubbleXs.add(relativeX);
                                }
                            }
                        }
                        if (!bubbleXs.isEmpty()) {
                            bubbleXPositions.add(bubbleXs);
                        }
                    } else {
                        // No clear gap, estimate QNum area from first blob
                        double qNumRatio = Math.min((double) leftmostBlobX / rowWidth, 0.4);
                        qNumRatios.add(qNumRatio);
                    }
                } else {
                    // Only one blob, estimate QNum area
                    double qNumRatio = Math.min((double) leftmostBlobX / rowWidth, 0.4);
                    qNumRatios.add(qNumRatio);
                }
            }
            
            rowRegion.release();
        }
        
        // Calculate average question number ratio
        if (!qNumRatios.isEmpty()) {
            questionNumberRatio = qNumRatios.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.35);
            // Clamp to reasonable range
            questionNumberRatio = Math.max(0.15, Math.min(0.45, questionNumberRatio));
        }
        
        // Calculate average bubble positions
        if (!bubbleXPositions.isEmpty()) {
            // Collect all positions and find the 4 most common clusters
            List<Double> allPositions = new ArrayList<>();
            for (List<Double> positions : bubbleXPositions) {
                positions.sort(Double::compareTo);
                allPositions.addAll(positions);
            }
            
            if (allPositions.size() >= 4) {
                // Find 4 clusters using k-means-like approach
                // Start with evenly spaced initial positions
                double[] clusterCenters = {0.125, 0.375, 0.625, 0.875};
                
                // Simple clustering: assign each position to nearest cluster
                double[] clusterSums = new double[4];
                int[] clusterCounts = new int[4];
                
                for (Double pos : allPositions) {
                    int nearestCluster = 0;
                    double minDist = Math.abs(pos - clusterCenters[0]);
                    for (int i = 1; i < 4; i++) {
                        double dist = Math.abs(pos - clusterCenters[i]);
                        if (dist < minDist) {
                            minDist = dist;
                            nearestCluster = i;
                        }
                    }
                    clusterSums[nearestCluster] += pos;
                    clusterCounts[nearestCluster]++;
                }
                
                // Calculate average positions for each cluster
                for (int i = 0; i < 4; i++) {
                    if (clusterCounts[i] > 0) {
                        bubblePositions[i] = clusterSums[i] / clusterCounts[i];
                    } else {
                        bubblePositions[i] = clusterCenters[i];
                    }
                }
                
                // Sort positions to ensure A < B < C < D order
                // Create index array and sort by position
                Integer[] indices = {0, 1, 2, 3};
                java.util.Arrays.sort(indices, Comparator.comparingDouble((Integer i) -> bubblePositions[i.intValue()]));
                
                // Reorder to A, B, C, D
                double[] sortedPositions = new double[4];
                for (int i = 0; i < 4; i++) {
                    sortedPositions[i] = bubblePositions[indices[i]];
                }
                bubblePositions = sortedPositions;
            } else {
                // Not enough data, use default positions
                for (int i = 0; i < 4; i++) {
                    bubblePositions[i] = (i + 0.5) / 4.0;
                }
            }
        } else {
            // Default positions if no bubbles found
            for (int i = 0; i < 4; i++) {
                bubblePositions[i] = (i + 0.5) / 4.0;
            }
        }
        
        calibrated = true;
        System.out.println("  ✓ Calibrated: QNum ratio=" + String.format("%.2f", questionNumberRatio) +
            ", Bubble positions: " + 
            String.format("%.2f", bubblePositions[0]) + ", " +
            String.format("%.2f", bubblePositions[1]) + ", " +
            String.format("%.2f", bubblePositions[2]) + ", " +
            String.format("%.2f", bubblePositions[3]));
    }
    
    /**
     * Find white blobs in a row region.
     * Only returns blobs that are likely filled bubbles (high fill ratio).
     */
    private List<BlobInfo> findBlobsInRow(Mat rowRegion) {
        List<BlobInfo> blobs = new ArrayList<>();
        
        Mat eroded = new Mat();
        Mat kernel = getStructuringElement(MORPH_ELLIPSE, new Size(3, 3));
        erode(rowRegion, eroded, kernel, new Point(-1, -1), 1, BORDER_CONSTANT, new Scalar(0));
        kernel.release();
        
        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        findContours(eroded.clone(), contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
        
        for (int i = 0; i < contours.size(); i++) {
            double area = contourArea(contours.get(i));
            if (area >= 10 && area <= 1000) {
                Moments moments = moments(contours.get(i));
                if (moments.m00() != 0) {
                    // Calculate fill ratio to ensure it's a filled bubble
                    Rect bbox = boundingRect(contours.get(i));
                    Mat mask = new Mat(rowRegion.rows(), rowRegion.cols(), CV_8UC1, new Scalar(0));
                    drawContours(mask, contours, i, new Scalar(255), -1, LINE_8, hierarchy, 0, new Point(0, 0));
                    Mat blobRegion = new Mat();
                    rowRegion.copyTo(blobRegion, mask);
                    int whitePixels = countNonZero(blobRegion);
                    double fillRatio = (double) whitePixels / area;
                    
                    mask.release();
                    blobRegion.release();
                    
                    // Only include if fill ratio is high (likely a filled bubble, not just outline)
                    if (fillRatio > 0.5) {
                        BlobInfo blob = new BlobInfo();
                        blob.centerX = (int)(moments.m10() / moments.m00());
                        blob.area = area;
                        blobs.add(blob);
                    }
                }
            }
        }
        
        eroded.release();
        hierarchy.release();
        return blobs;
    }
    
    /**
     * Map X position to choice using calibrated positions.
     */
    public String mapToChoice(int x, int rowWidth) {
        if (!calibrated) return null;
        
        int bubbleAreaStart = (int)(rowWidth * questionNumberRatio);
        int bubbleAreaWidth = rowWidth - bubbleAreaStart;
        
        if (x < bubbleAreaStart || bubbleAreaWidth <= 0) return null;
        
        double relativeX = (double)(x - bubbleAreaStart) / bubbleAreaWidth;
        
        // Find closest bubble position
        int bestChoice = 0;
        double minDist = Math.abs(relativeX - bubblePositions[0]);
        
        for (int i = 1; i < 4; i++) {
            double dist = Math.abs(relativeX - bubblePositions[i]);
            if (dist < minDist) {
                minDist = dist;
                bestChoice = i;
            }
        }
        
        // Only return if close enough (within 25% of bubble area for better tolerance)
        if (minDist < 0.25) {
            return CHOICE_LABELS[bestChoice];
        }
        
        return null;
    }
    
    public boolean isCalibrated() { 
        return calibrated; 
    }
    
    public double getQuestionNumberRatio() { 
        return questionNumberRatio; 
    }
    
    public double[] getBubblePositions() {
        return bubblePositions.clone();
    }
}

