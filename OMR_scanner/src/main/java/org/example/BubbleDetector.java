package org.example;

import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.global.opencv_core;

import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_imgproc.*;
import static org.bytedeco.opencv.global.opencv_core.countNonZero;

/**
 * Utility class for detecting and analyzing bubbles in OMR sheets.
 */
public class BubbleDetector {

    // Bubble detection parameters - tuned for real scanned OMR sheets
    private double minArea = 100;      // Smaller min for scanned images
    private double maxArea = 8000;     // Larger max for higher resolution scans
    private double minCircularity = 0.4; // More lenient for imperfect circles
    private double fillThreshold = 0.35; // Lower threshold - real marks may not be perfectly filled

    /**
     * Represents a detected bubble.
     */
    public static class Bubble {
        public int x, y;           // Center position
        public int radius;         // Approximate radius
        public double area;        // Contour area
        public double circularity; // How circular (0-1)
        public double fillRatio;   // How filled (0-1)
        public boolean isMarked;   // Is this bubble filled?
        public Rect boundingBox;   // Bounding rectangle
        
        @Override
        public String toString() {
            return String.format("Bubble(x=%d, y=%d, r=%d, marked=%s, fill=%.2f)", 
                x, y, radius, isMarked, fillRatio);
        }
    }

    /**
     * Detect all bubbles in a binary (thresholded) image.
     * 
     * @param binaryImage The thresholded image (white bubbles on black background)
     * @return List of detected bubbles
     */
    public List<Bubble> detectBubbles(Mat binaryImage) {
        List<Bubble> bubbles = new ArrayList<>();
        
        // Find contours
        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        findContours(binaryImage.clone(), contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
        
        for (int i = 0; i < contours.size(); i++) {
            Mat contour = contours.get(i);
            
            // Calculate area and perimeter
            double area = contourArea(contour);
            double perimeter = arcLength(contour, true);
            
            // Filter by area
            if (area < minArea || area > maxArea) {
                continue;
            }
            
            // Calculate circularity
            double circularity = 0;
            if (perimeter > 0) {
                circularity = 4 * Math.PI * area / (perimeter * perimeter);
            }
            
            // Filter by circularity
            if (circularity < minCircularity) {
                continue;
            }
            
            // Get bounding box and center
            Rect bbox = boundingRect(contour);
            int centerX = bbox.x() + bbox.width() / 2;
            int centerY = bbox.y() + bbox.height() / 2;
            int radius = (bbox.width() + bbox.height()) / 4;
            
            // Create bubble object
            Bubble bubble = new Bubble();
            bubble.x = centerX;
            bubble.y = centerY;
            bubble.radius = radius;
            bubble.area = area;
            bubble.circularity = circularity;
            bubble.boundingBox = bbox;
            
            bubbles.add(bubble);
        }
        
        hierarchy.release();
        return bubbles;
    }

    /**
     * Analyze a bubble to determine if it's marked (filled).
     * 
     * @param bubble The bubble to analyze
     * @param binaryImage The thresholded image
     * @return true if the bubble is marked
     */
    public boolean isBubbleMarked(Bubble bubble, Mat binaryImage) {
        // Extract ROI for this bubble
        Rect roi = bubble.boundingBox;
        
        // Ensure ROI is within image bounds
        int x = Math.max(0, roi.x());
        int y = Math.max(0, roi.y());
        int w = Math.min(roi.width(), binaryImage.cols() - x);
        int h = Math.min(roi.height(), binaryImage.rows() - y);
        
        if (w <= 0 || h <= 0) {
            return false;
        }
        
        // Get bubble region
        Mat bubbleRegion = binaryImage.apply(new Rect(x, y, w, h));
        
        // Count white pixels
        int totalPixels = w * h;
        int whitePixels = countNonZero(bubbleRegion);
        
        double fillRatio = (double) whitePixels / totalPixels;
        bubble.fillRatio = fillRatio;
        bubble.isMarked = fillRatio >= fillThreshold;
        
        return bubble.isMarked;
    }

    /**
     * Analyze all bubbles in an image.
     */
    public void analyzeBubbles(List<Bubble> bubbles, Mat binaryImage) {
        for (Bubble bubble : bubbles) {
            isBubbleMarked(bubble, binaryImage);
        }
    }

    /**
     * Group bubbles into rows based on Y position.
     * 
     * @param bubbles List of bubbles
     * @param tolerance Y tolerance for grouping
     * @return List of rows, each containing bubbles sorted by X
     */
    public List<List<Bubble>> groupBubblesIntoRows(List<Bubble> bubbles, int tolerance) {
        List<List<Bubble>> rows = new ArrayList<>();
        
        // Sort bubbles by Y first
        List<Bubble> sorted = new ArrayList<>(bubbles);
        sorted.sort((a, b) -> Integer.compare(a.y, b.y));
        
        List<Bubble> currentRow = new ArrayList<>();
        int currentY = -1000;
        
        for (Bubble bubble : sorted) {
            if (Math.abs(bubble.y - currentY) > tolerance) {
                // Start new row
                if (!currentRow.isEmpty()) {
                    currentRow.sort((a, b) -> Integer.compare(a.x, b.x));
                    rows.add(currentRow);
                }
                currentRow = new ArrayList<>();
                currentY = bubble.y;
            }
            currentRow.add(bubble);
        }
        
        // Add last row
        if (!currentRow.isEmpty()) {
            currentRow.sort((a, b) -> Integer.compare(a.x, b.x));
            rows.add(currentRow);
        }
        
        return rows;
    }

    // Getters and setters for parameters
    public void setMinArea(double minArea) { this.minArea = minArea; }
    public void setMaxArea(double maxArea) { this.maxArea = maxArea; }
    public void setMinCircularity(double minCircularity) { this.minCircularity = minCircularity; }
    public void setFillThreshold(double fillThreshold) { this.fillThreshold = fillThreshold; }
}

