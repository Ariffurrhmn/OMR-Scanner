package org.example;

import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.global.opencv_core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * Detects fiducial markers in OMR sheets for alignment and region extraction.
 * 
 * The OMR sheet has two types of fiducials:
 * 1. L-shaped markers at 4 page corners - for deskewing the entire page
 * 2. Rectangular markers at 4 corners of answer section - for isolating answer blocks
 * 
 * Additionally, the Student ID and Test ID regions can be found as the second 
 * largest rectangular region after the answer block.
 */
public class FiducialDetector {

    // Detection parameters
    private double minLShapeArea = 500;
    private double maxLShapeArea = 10000;
    private double minRectArea = 100;
    private double maxRectArea = 3000;
    private double lShapeAngleTolerance = 15; // degrees

    /**
     * Represents an L-shaped fiducial marker.
     */
    public static class LShapedFiducial {
        public Point2f corner;        // The inner corner of the L
        public Point2f armEnd1;       // End of first arm
        public Point2f armEnd2;       // End of second arm
        public double angle;          // Orientation angle
        public CornerPosition position; // Which corner (TL, TR, BL, BR)
        
        public enum CornerPosition {
            TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, UNKNOWN
        }
        
        @Override
        public String toString() {
            return String.format("L-Fiducial(%s, corner=(%.0f,%.0f))", 
                position, corner.x(), corner.y());
        }
    }

    /**
     * Represents a rectangular fiducial marker.
     */
    public static class RectFiducial {
        public Point2f center;
        public double width;
        public double height;
        public double area;
        public Rect boundingBox;
        
        @Override
        public String toString() {
            return String.format("Rect-Fiducial(center=(%.0f,%.0f), size=%.0fx%.0f)", 
                center.x(), center.y(), width, height);
        }
    }

    /**
     * Represents detected page corners from L-shaped fiducials.
     */
    public static class PageCorners {
        public Point2f topLeft;
        public Point2f topRight;
        public Point2f bottomLeft;
        public Point2f bottomRight;
        
        public boolean isValid() {
            return topLeft != null && topRight != null && 
                   bottomLeft != null && bottomRight != null;
        }
        
        public void print() {
            System.out.println("  Page Corners:");
            System.out.println("    TL: " + (topLeft != null ? String.format("(%.0f, %.0f)", topLeft.x(), topLeft.y()) : "null"));
            System.out.println("    TR: " + (topRight != null ? String.format("(%.0f, %.0f)", topRight.x(), topRight.y()) : "null"));
            System.out.println("    BL: " + (bottomLeft != null ? String.format("(%.0f, %.0f)", bottomLeft.x(), bottomLeft.y()) : "null"));
            System.out.println("    BR: " + (bottomRight != null ? String.format("(%.0f, %.0f)", bottomRight.x(), bottomRight.y()) : "null"));
        }
    }

    /**
     * Represents detected answer section corners from rectangular fiducials.
     */
    public static class AnswerSectionCorners {
        public Point2f topLeft;
        public Point2f topRight;
        public Point2f bottomLeft;
        public Point2f bottomRight;
        
        public boolean isValid() {
            return topLeft != null && topRight != null && 
                   bottomLeft != null && bottomRight != null;
        }
        
        public Rect getBoundingRect() {
            if (!isValid()) return null;
            int x = (int) Math.min(topLeft.x(), bottomLeft.x());
            int y = (int) Math.min(topLeft.y(), topRight.y());
            int w = (int) (Math.max(topRight.x(), bottomRight.x()) - x);
            int h = (int) (Math.max(bottomLeft.y(), bottomRight.y()) - y);
            return new Rect(x, y, w, h);
        }
        
        public void print() {
            System.out.println("  Answer Section Corners:");
            System.out.println("    TL: " + (topLeft != null ? String.format("(%.0f, %.0f)", topLeft.x(), topLeft.y()) : "null"));
            System.out.println("    TR: " + (topRight != null ? String.format("(%.0f, %.0f)", topRight.x(), topRight.y()) : "null"));
            System.out.println("    BL: " + (bottomLeft != null ? String.format("(%.0f, %.0f)", bottomLeft.x(), bottomLeft.y()) : "null"));
            System.out.println("    BR: " + (bottomRight != null ? String.format("(%.0f, %.0f)", bottomRight.x(), bottomRight.y()) : "null"));
        }
    }

    /**
     * Detect L-shaped fiducial markers for page deskewing.
     * 
     * L-shaped markers have a distinctive shape with two perpendicular arms
     * meeting at an inner corner.
     */
    public List<LShapedFiducial> detectLShapedFiducials(Mat binaryImage) {
        List<LShapedFiducial> fiducials = new ArrayList<>();
        
        // Find contours
        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        findContours(binaryImage.clone(), contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
        
        for (int i = 0; i < contours.size(); i++) {
            Mat contour = contours.get(i);
            double area = contourArea(contour);
            
            // Filter by area
            if (area < minLShapeArea || area > maxLShapeArea) {
                continue;
            }
            
            // Approximate to polygon
            Mat approx = new Mat();
            double peri = arcLength(contour, true);
            approxPolyDP(contour, approx, 0.02 * peri, true);
            
            // L-shape typically has 6 vertices when approximated
            // (outer corner, two arm ends, inner corner, two more points)
            int vertices = approx.rows();
            if (vertices >= 5 && vertices <= 8) {
                LShapedFiducial fid = analyzeLShape(approx, binaryImage);
                if (fid != null) {
                    fiducials.add(fid);
                }
            }
            
            approx.release();
        }
        
        hierarchy.release();
        
        // Assign corner positions based on location
        assignCornerPositions(fiducials, binaryImage.cols(), binaryImage.rows());
        
        return fiducials;
    }

    /**
     * Analyze a contour to determine if it's an L-shape and extract its properties.
     */
    private LShapedFiducial analyzeLShape(Mat approx, Mat image) {
        // Get bounding box
        Rect bbox = boundingRect(approx);
        
        // Check aspect ratio - L-shapes are roughly square in bounding box
        double aspectRatio = (double) bbox.width() / bbox.height();
        if (aspectRatio < 0.5 || aspectRatio > 2.0) {
            return null;
        }
        
        // Find the convex hull and check for L-shape characteristics
        Mat hull = new Mat();
        convexHull(approx, hull);
        
        double contourArea = contourArea(approx);
        double hullArea = contourArea(hull);
        
        // L-shape has lower solidity (area/hull_area) than a rectangle
        double solidity = contourArea / hullArea;
        if (solidity > 0.75 || solidity < 0.3) {
            hull.release();
            return null;
        }
        
        hull.release();
        
        // Create fiducial
        LShapedFiducial fid = new LShapedFiducial();
        fid.corner = new Point2f(bbox.x() + bbox.width() / 2f, bbox.y() + bbox.height() / 2f);
        fid.position = LShapedFiducial.CornerPosition.UNKNOWN;
        
        return fid;
    }

    /**
     * Assign corner positions (TL, TR, BL, BR) based on fiducial locations.
     */
    private void assignCornerPositions(List<LShapedFiducial> fiducials, int imageWidth, int imageHeight) {
        int midX = imageWidth / 2;
        int midY = imageHeight / 2;
        
        for (LShapedFiducial fid : fiducials) {
            boolean isLeft = fid.corner.x() < midX;
            boolean isTop = fid.corner.y() < midY;
            
            if (isTop && isLeft) {
                fid.position = LShapedFiducial.CornerPosition.TOP_LEFT;
            } else if (isTop && !isLeft) {
                fid.position = LShapedFiducial.CornerPosition.TOP_RIGHT;
            } else if (!isTop && isLeft) {
                fid.position = LShapedFiducial.CornerPosition.BOTTOM_LEFT;
            } else {
                fid.position = LShapedFiducial.CornerPosition.BOTTOM_RIGHT;
            }
        }
    }

    /**
     * Detect small rectangular fiducial markers for answer section isolation.
     */
    public List<RectFiducial> detectRectFiducials(Mat binaryImage) {
        List<RectFiducial> fiducials = new ArrayList<>();
        
        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        findContours(binaryImage.clone(), contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
        
        for (int i = 0; i < contours.size(); i++) {
            Mat contour = contours.get(i);
            double area = contourArea(contour);
            
            // Filter by area (small rectangles)
            if (area < minRectArea || area > maxRectArea) {
                continue;
            }
            
            // Approximate to polygon
            Mat approx = new Mat();
            double peri = arcLength(contour, true);
            approxPolyDP(contour, approx, 0.02 * peri, true);
            
            // Rectangle has 4 vertices
            if (approx.rows() == 4) {
                Rect bbox = boundingRect(contour);
                double aspectRatio = (double) bbox.width() / bbox.height();
                
                // Small square-ish rectangles
                if (aspectRatio > 0.5 && aspectRatio < 2.0) {
                    RectFiducial fid = new RectFiducial();
                    fid.center = new Point2f(bbox.x() + bbox.width() / 2f, bbox.y() + bbox.height() / 2f);
                    fid.width = bbox.width();
                    fid.height = bbox.height();
                    fid.area = area;
                    fid.boundingBox = bbox;
                    fiducials.add(fid);
                }
            }
            
            approx.release();
        }
        
        hierarchy.release();
        return fiducials;
    }

    /**
     * Extract page corners from detected L-shaped fiducials.
     */
    public PageCorners getPageCorners(List<LShapedFiducial> fiducials) {
        PageCorners corners = new PageCorners();
        
        for (LShapedFiducial fid : fiducials) {
            switch (fid.position) {
                case TOP_LEFT -> corners.topLeft = fid.corner;
                case TOP_RIGHT -> corners.topRight = fid.corner;
                case BOTTOM_LEFT -> corners.bottomLeft = fid.corner;
                case BOTTOM_RIGHT -> corners.bottomRight = fid.corner;
            }
        }
        
        return corners;
    }

    /**
     * Extract answer section corners from rectangular fiducials.
     * Works with 2, 3, or 4 fiducials by estimating missing corners.
     */
    public AnswerSectionCorners getAnswerSectionCorners(List<RectFiducial> fiducials, int imageWidth, int imageHeight) {
        if (fiducials.size() < 2) {
            System.out.println("    Only " + fiducials.size() + " rectangular fiducials found, need at least 2");
            return null;
        }
        
        // Filter to get fiducials in the answer section area
        List<RectFiducial> answerFiducials = fiducials.stream()
            .filter(f -> f.center.y() > imageHeight * 0.20) // Below header area
            .collect(java.util.stream.Collectors.toList());
        
        if (answerFiducials.size() < 2) {
            // Use all fiducials if we have at least 2
            answerFiducials = fiducials;
        }
        
        if (answerFiducials.size() < 2) {
            return null;
        }
        
        // Sort into corners based on position
        double minX = answerFiducials.stream().mapToDouble(f -> f.center.x()).min().orElse(0);
        double maxX = answerFiducials.stream().mapToDouble(f -> f.center.x()).max().orElse(imageWidth);
        double minY = answerFiducials.stream().mapToDouble(f -> f.center.y()).min().orElse(0);
        double maxY = answerFiducials.stream().mapToDouble(f -> f.center.y()).max().orElse(imageHeight);
        
        AnswerSectionCorners corners = new AnswerSectionCorners();
        
        // Assign detected fiducials to corners
        for (RectFiducial fid : answerFiducials) {
            boolean isLeft = fid.center.x() < (minX + maxX) / 2;
            boolean isTop = fid.center.y() < (minY + maxY) / 2;
            
            if (isTop && isLeft) {
                corners.topLeft = fid.center;
            } else if (isTop && !isLeft) {
                corners.topRight = fid.center;
            } else if (!isTop && isLeft) {
                corners.bottomLeft = fid.center;
            } else {
                corners.bottomRight = fid.center;
            }
        }
        
        // Estimate missing corners based on detected ones
        estimateMissingCorners(corners, minX, maxX, minY, maxY, imageWidth, imageHeight);
        
        int detectedCount = 0;
        if (corners.topLeft != null) detectedCount++;
        if (corners.topRight != null) detectedCount++;
        if (corners.bottomLeft != null) detectedCount++;
        if (corners.bottomRight != null) detectedCount++;
        
        System.out.println("    Using " + answerFiducials.size() + " fiducials, estimated " + 
            (4 - answerFiducials.size()) + " missing corners (total: " + detectedCount + "/4)");
        
        return corners;
    }
    
    /**
     * Estimate missing corners based on detected ones.
     * Uses the expected rectangular shape of the answer section.
     */
    private void estimateMissingCorners(AnswerSectionCorners corners, 
                                       double minX, double maxX, double minY, double maxY,
                                       int imageWidth, int imageHeight) {
        int detectedCount = 0;
        if (corners.topLeft != null) detectedCount++;
        if (corners.topRight != null) detectedCount++;
        if (corners.bottomLeft != null) detectedCount++;
        if (corners.bottomRight != null) detectedCount++;
        
        if (detectedCount == 4) {
            return; // All corners detected
        }
        
        // Calculate average width and height from detected corners
        double avgWidth = 0, avgHeight = 0;
        int widthCount = 0, heightCount = 0;
        
        if (corners.topLeft != null && corners.topRight != null) {
            avgWidth += Math.abs(corners.topRight.x() - corners.topLeft.x());
            widthCount++;
        }
        if (corners.bottomLeft != null && corners.bottomRight != null) {
            avgWidth += Math.abs(corners.bottomRight.x() - corners.bottomLeft.x());
            widthCount++;
        }
        if (widthCount > 0) {
            avgWidth /= widthCount;
        } else {
            // Estimate from detected X range
            avgWidth = maxX - minX;
        }
        
        if (corners.topLeft != null && corners.bottomLeft != null) {
            avgHeight += Math.abs(corners.bottomLeft.y() - corners.topLeft.y());
            heightCount++;
        }
        if (corners.topRight != null && corners.bottomRight != null) {
            avgHeight += Math.abs(corners.bottomRight.y() - corners.topRight.y());
            heightCount++;
        }
        if (heightCount > 0) {
            avgHeight /= heightCount;
        } else {
            // Estimate from detected Y range
            avgHeight = maxY - minY;
        }
        
        // Estimate missing corners
        if (corners.topLeft == null) {
            if (corners.topRight != null && corners.bottomLeft != null) {
                // Estimate from top-right and bottom-left
                corners.topLeft = new Point2f(
                    (float)(corners.bottomLeft.x()),
                    (float)(corners.topRight.y())
                );
            } else if (corners.topRight != null) {
                corners.topLeft = new Point2f(
                    (float)(corners.topRight.x() - avgWidth),
                    (float)(corners.topRight.y())
                );
            } else if (corners.bottomLeft != null) {
                corners.topLeft = new Point2f(
                    (float)(corners.bottomLeft.x()),
                    (float)(corners.bottomLeft.y() - avgHeight)
                );
            }
        }
        
        if (corners.topRight == null) {
            if (corners.topLeft != null && corners.bottomRight != null) {
                corners.topRight = new Point2f(
                    (float)(corners.bottomRight.x()),
                    (float)(corners.topLeft.y())
                );
            } else if (corners.topLeft != null) {
                corners.topRight = new Point2f(
                    (float)(corners.topLeft.x() + avgWidth),
                    (float)(corners.topLeft.y())
                );
            } else if (corners.bottomRight != null) {
                corners.topRight = new Point2f(
                    (float)(corners.bottomRight.x()),
                    (float)(corners.bottomRight.y() - avgHeight)
                );
            }
        }
        
        if (corners.bottomLeft == null) {
            if (corners.topLeft != null && corners.bottomRight != null) {
                corners.bottomLeft = new Point2f(
                    (float)(corners.topLeft.x()),
                    (float)(corners.bottomRight.y())
                );
            } else if (corners.topLeft != null) {
                corners.bottomLeft = new Point2f(
                    (float)(corners.topLeft.x()),
                    (float)(corners.topLeft.y() + avgHeight)
                );
            } else if (corners.bottomRight != null) {
                corners.bottomLeft = new Point2f(
                    (float)(corners.bottomRight.x() - avgWidth),
                    (float)(corners.bottomRight.y())
                );
            }
        }
        
        if (corners.bottomRight == null) {
            if (corners.topRight != null && corners.bottomLeft != null) {
                corners.bottomRight = new Point2f(
                    (float)(corners.topRight.x()),
                    (float)(corners.bottomLeft.y())
                );
            } else if (corners.topRight != null) {
                corners.bottomRight = new Point2f(
                    (float)(corners.topRight.x()),
                    (float)(corners.topRight.y() + avgHeight)
                );
            } else if (corners.bottomLeft != null) {
                corners.bottomRight = new Point2f(
                    (float)(corners.bottomLeft.x() + avgWidth),
                    (float)(corners.bottomLeft.y())
                );
            }
        }
    }

    /**
     * Find the ID section (Student ID + Test ID) as the second largest rectangle.
     * 
     * @param binaryImage The preprocessed binary image
     * @param answerSectionRect The already-detected answer section (to exclude)
     * @return Bounding rectangle of the ID section
     */
    public Rect findIdSection(Mat binaryImage, Rect answerSectionRect) {
        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        findContours(binaryImage.clone(), contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
        
        List<Rect> rectangles = new ArrayList<>();
        
        for (int i = 0; i < contours.size(); i++) {
            Mat contour = contours.get(i);
            double area = contourArea(contour);
            
            // Skip small contours
            if (area < 5000) continue;
            
            // Approximate to polygon
            Mat approx = new Mat();
            double peri = arcLength(contour, true);
            approxPolyDP(contour, approx, 0.02 * peri, true);
            
            // Check if it's a rectangle (4 vertices)
            if (approx.rows() == 4) {
                Rect bbox = boundingRect(contour);
                
                // Skip if this is the answer section
                if (answerSectionRect != null && overlaps(bbox, answerSectionRect)) {
                    approx.release();
                    continue;
                }
                
                rectangles.add(bbox);
            }
            
            approx.release();
        }
        
        hierarchy.release();
        
        // Sort by area (descending)
        rectangles.sort((a, b) -> Integer.compare(b.width() * b.height(), a.width() * a.height()));
        
        // Return second largest (first is likely the full page or answer section)
        if (rectangles.size() >= 2) {
            return rectangles.get(1);
        } else if (rectangles.size() == 1) {
            return rectangles.get(0);
        }
        
        return null;
    }

    /**
     * Alternative method: Find ID section by looking for the bubble grid pattern
     * in the upper portion of the page.
     */
    public Rect findIdSectionByLocation(Mat binaryImage) {
        int width = binaryImage.cols();
        int height = binaryImage.rows();
        
        // ID section is typically in the upper 30% of the page
        // and occupies the left 60% of the width
        int searchHeight = (int) (height * 0.35);
        int searchWidth = (int) (width * 0.65);
        
        // Create ROI for the upper-left area
        Rect searchArea = new Rect(0, 0, searchWidth, searchHeight);
        Mat roi = binaryImage.apply(searchArea);
        
        // Find the largest contour in this area (should be the ID section border)
        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        findContours(roi.clone(), contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
        
        Rect largestRect = null;
        double maxArea = 0;
        
        for (int i = 0; i < contours.size(); i++) {
            Mat contour = contours.get(i);
            double area = contourArea(contour);
            
            if (area > maxArea && area > 10000) {
                Rect bbox = boundingRect(contour);
                // Adjust coordinates back to full image
                largestRect = new Rect(bbox.x(), bbox.y(), bbox.width(), bbox.height());
                maxArea = area;
            }
        }
        
        hierarchy.release();
        return largestRect;
    }

    /**
     * Check if two rectangles overlap.
     */
    private boolean overlaps(Rect a, Rect b) {
        return !(a.x() + a.width() < b.x() || 
                 b.x() + b.width() < a.x() || 
                 a.y() + a.height() < b.y() || 
                 b.y() + b.height() < a.y());
    }

    // Setters for parameters
    public void setMinLShapeArea(double area) { this.minLShapeArea = area; }
    public void setMaxLShapeArea(double area) { this.maxLShapeArea = area; }
    public void setMinRectArea(double area) { this.minRectArea = area; }
    public void setMaxRectArea(double area) { this.maxRectArea = area; }
}

