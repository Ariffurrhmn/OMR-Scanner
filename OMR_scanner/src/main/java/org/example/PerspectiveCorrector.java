package org.example;

import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.javacpp.DoublePointer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * Utility class for perspective correction (deskewing) of OMR sheets.
 * Uses corner detection to find the document boundary and applies
 * perspective transformation to get a top-down view.
 */
public class PerspectiveCorrector {

    /**
     * Represents the four corners of a document.
     */
    public static class DocumentCorners {
        public Point2f topLeft;
        public Point2f topRight;
        public Point2f bottomLeft;
        public Point2f bottomRight;
        
        public boolean isValid() {
            return topLeft != null && topRight != null && 
                   bottomLeft != null && bottomRight != null;
        }
    }

    /**
     * Find the document contour (largest rectangular contour).
     * 
     * @param edgeImage Binary edge image (from Canny)
     * @return The document corners, or null if not found
     */
    public DocumentCorners findDocumentCorners(Mat edgeImage) {
        // Find contours
        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        findContours(edgeImage.clone(), contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
        
        // Find largest 4-point contour
        Mat bestContour = null;
        double maxArea = 0;
        
        for (int i = 0; i < contours.size(); i++) {
            Mat contour = contours.get(i);
            double area = contourArea(contour);
            
            // Skip small contours
            if (area < 10000) continue;
            
            // Approximate contour to polygon
            Mat approx = new Mat();
            double peri = arcLength(contour, true);
            approxPolyDP(contour, approx, 0.02 * peri, true);
            
            // Check if it's a quadrilateral
            if (approx.rows() == 4 && area > maxArea) {
                maxArea = area;
                bestContour = approx.clone();
            }
            
            approx.release();
        }
        
        hierarchy.release();
        
        if (bestContour == null) {
            return null;
        }
        
        // Extract corners and order them
        return orderCorners(bestContour);
    }

    /**
     * Order corners as: top-left, top-right, bottom-right, bottom-left.
     */
    private DocumentCorners orderCorners(Mat contour) {
        // Extract points
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            DoublePointer ptr = new DoublePointer(contour.ptr(i));
            points.add(new double[]{ptr.get(0), ptr.get(1)});
        }
        
        // Sort by sum (x+y) to find top-left and bottom-right
        points.sort(Comparator.comparingDouble(p -> p[0] + p[1]));
        double[] topLeft = points.get(0);
        double[] bottomRight = points.get(3);
        
        // Sort by difference (y-x) to find top-right and bottom-left
        points.sort(Comparator.comparingDouble(p -> p[1] - p[0]));
        double[] topRight = points.get(0);
        double[] bottomLeft = points.get(3);
        
        DocumentCorners corners = new DocumentCorners();
        corners.topLeft = new Point2f((float)topLeft[0], (float)topLeft[1]);
        corners.topRight = new Point2f((float)topRight[0], (float)topRight[1]);
        corners.bottomLeft = new Point2f((float)bottomLeft[0], (float)bottomLeft[1]);
        corners.bottomRight = new Point2f((float)bottomRight[0], (float)bottomRight[1]);
        
        return corners;
    }

    /**
     * Apply perspective transformation to get a top-down view.
     * 
     * @param image The input image
     * @param corners The document corners
     * @param outputWidth Desired output width
     * @param outputHeight Desired output height
     * @return The warped image
     */
    public Mat warpPerspective(Mat image, DocumentCorners corners, int outputWidth, int outputHeight) {
        // Source points
        Mat srcPoints = new Mat(4, 1, CV_32FC2);
        srcPoints.ptr(0).putFloat(corners.topLeft.x()).putFloat(4, corners.topLeft.y());
        srcPoints.ptr(1).putFloat(corners.topRight.x()).putFloat(4, corners.topRight.y());
        srcPoints.ptr(2).putFloat(corners.bottomRight.x()).putFloat(4, corners.bottomRight.y());
        srcPoints.ptr(3).putFloat(corners.bottomLeft.x()).putFloat(4, corners.bottomLeft.y());
        
        // Destination points (rectangle)
        Mat dstPoints = new Mat(4, 1, CV_32FC2);
        dstPoints.ptr(0).putFloat(0).putFloat(4, 0);
        dstPoints.ptr(1).putFloat(outputWidth).putFloat(4, 0);
        dstPoints.ptr(2).putFloat(outputWidth).putFloat(4, outputHeight);
        dstPoints.ptr(3).putFloat(0).putFloat(4, outputHeight);
        
        // Get transformation matrix
        Mat transformMatrix = getPerspectiveTransform(srcPoints, dstPoints);
        
        // Apply transformation
        Mat warped = new Mat();
        org.bytedeco.opencv.global.opencv_imgproc.warpPerspective(
            image, warped, transformMatrix, new Size(outputWidth, outputHeight));
        
        // Cleanup
        srcPoints.release();
        dstPoints.release();
        transformMatrix.release();
        
        return warped;
    }

    /**
     * Auto-detect and correct perspective.
     * 
     * @param image Input image
     * @return Corrected image, or original if correction fails
     */
    public Mat autoCorrect(Mat image) {
        // Preprocess for edge detection
        ImagePreprocessor preprocessor = new ImagePreprocessor();
        Mat gray = preprocessor.toGrayscale(image);
        Mat blurred = preprocessor.applyGaussianBlur(gray, 5);
        Mat edges = preprocessor.applyCanny(blurred, 50, 150);
        
        // Dilate edges to connect broken lines
        Mat dilated = preprocessor.dilate(edges, 3);
        
        // Find corners
        DocumentCorners corners = findDocumentCorners(dilated);
        
        // Cleanup
        gray.release();
        blurred.release();
        edges.release();
        dilated.release();
        
        if (corners == null || !corners.isValid()) {
            System.out.println("  ⚠ Could not detect document corners");
            return image.clone();
        }
        
        // Calculate output dimensions
        double topWidth = distance(corners.topLeft, corners.topRight);
        double bottomWidth = distance(corners.bottomLeft, corners.bottomRight);
        double leftHeight = distance(corners.topLeft, corners.bottomLeft);
        double rightHeight = distance(corners.topRight, corners.bottomRight);
        
        int outputWidth = (int) Math.max(topWidth, bottomWidth);
        int outputHeight = (int) Math.max(leftHeight, rightHeight);
        
        return warpPerspective(image, corners, outputWidth, outputHeight);
    }

    /**
     * Calculate distance between two points.
     */
    private double distance(Point2f p1, Point2f p2) {
        double dx = p2.x() - p1.x();
        double dy = p2.y() - p1.y();
        return Math.sqrt(dx * dx + dy * dy);
    }
}

