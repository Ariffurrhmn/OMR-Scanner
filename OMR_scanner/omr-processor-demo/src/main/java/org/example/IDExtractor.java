package org.example;

import org.bytedeco.opencv.opencv_core.*;

import java.util.*;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;

/**
 * Extracts Student ID (10 digits) and Test ID (4 digits) from OMR sheets.
 * 
 * ID Section Layout:
 * ┌────────────────────────────────────────────────────────────┐
 * │  STUDENT ID (10 columns)     TEST ID (4 columns)          │
 * │  ┌─┬─┬─┬─┬─┬─┬─┬─┬─┬─┐      ┌─┬─┬─┬─┐                     │
 * │  │1│1│1│1│1│1│1│1│1│1│      │1│1│1│1│                     │
 * │  │2│2│2│2│2│2│2│2│2│2│      │2│2│2│2│                     │
 * │  │ ...                      │ ...                          │
 * │  │0│0│0│0│0│0│0│0│0│0│      │0│0│0│0│                     │
 * │  └─┴─┴─┴─┴─┴─┴─┴─┴─┴─┘      └─┴─┴─┴─┘                     │
 * └────────────────────────────────────────────────────────────┘
 * 
 * Each column has 10 bubbles for digits 1-9,0.
 * The filled bubble indicates the digit for that position.
 */
public class IDExtractor {

    private static final int STUDENT_ID_DIGITS = 10;
    private static final int TEST_ID_DIGITS = 4;
    private static final int DIGIT_ROWS = 10;  // 1-9, 0
    
    // Debug settings
    private boolean saveDebugImages = false;
    private String debugOutputDir = "output";
    
    public void setSaveDebugImages(boolean save) {
        this.saveDebugImages = save;
    }
    
    public void setDebugOutputDir(String dir) {
        this.debugOutputDir = dir;
    }
    
    /**
     * Result of ID extraction.
     */
    public static class Result {
        public String studentId;
        public String testId;
        public double studentIdConfidence;
        public double testIdConfidence;
        public boolean success;
        public String errorMessage;
    }
    
    /**
     * Extract IDs from the top portion of a deskewed OMR sheet.
     * 
     * @param deskewedImage The full deskewed OMR sheet (grayscale or color)
     * @return Extraction result with Student ID and Test ID
     */
    public Result extract(Mat deskewedImage) {
        Result result = new Result();
        result.success = false;
        
        try {
            int imgHeight = deskewedImage.rows();
            int imgWidth = deskewedImage.cols();
            
            // ID section - tight bounds around actual bubble area
            // Avoid header and gap regions
            int idSectionTop = (int)(imgHeight * 0.20);
            int idSectionBottom = (int)(imgHeight * 0.44);
            int idSectionHeight = idSectionBottom - idSectionTop;
            
            // Extract ID section region
            Rect idSectionRect = new Rect(0, idSectionTop, imgWidth, idSectionHeight);
            Mat idSection = deskewedImage.apply(idSectionRect).clone();
            
            System.out.println("  Extracting IDs from region: " + 
                imgWidth + "x" + idSectionHeight + " (Y: " + idSectionTop + "-" + idSectionBottom + ")");
            
            // Convert to grayscale if needed
            Mat gray;
            if (idSection.channels() == 3) {
                gray = new Mat();
                cvtColor(idSection, gray, COLOR_BGR2GRAY);
            } else {
                gray = idSection.clone();
            }
            
            // Apply adaptive threshold to get binary image
            // Use inverted so filled bubbles are white
            Mat binary = new Mat();
            adaptiveThreshold(gray, binary, 255, ADAPTIVE_THRESH_GAUSSIAN_C, 
                             THRESH_BINARY_INV, 15, 5);
            
            if (saveDebugImages) {
                imwrite(debugOutputDir + "/08_id_section.png", idSection);
                imwrite(debugOutputDir + "/09_id_binary.png", binary);
            }
            
            // First, detect the actual bordered rectangular regions
            Rect[] idBoxes = detectIdBoxesByBorders(binary);
            
            Rect studentBoxRect;
            Rect testBoxRect;
            
            if (idBoxes[0] != null && idBoxes[1] != null) {
                // Use detected boxes
                studentBoxRect = idBoxes[0];
                testBoxRect = idBoxes[1];
                System.out.println("  Detected Student ID box: " + studentBoxRect.width() + "x" + studentBoxRect.height() + 
                    " at (" + studentBoxRect.x() + "," + studentBoxRect.y() + ")");
                System.out.println("  Detected Test ID box: " + testBoxRect.width() + "x" + testBoxRect.height() + 
                    " at (" + testBoxRect.x() + "," + testBoxRect.y() + ")");
            } else {
                // Fallback to template positions
                int idWidth = idSection.cols();
                int idHeight = idSection.rows();
                studentBoxRect = new Rect(0, 0, (int)(idWidth * 0.30), idHeight);
                testBoxRect = new Rect((int)(idWidth * 0.30), 0, (int)(idWidth * 0.20), idHeight);
                System.out.println("  Using template positions (boxes not detected)");
            }
            
                // Extract regions inside the boxes (with margins to avoid borders)
                // Use larger top margin to preserve first row, smaller side/bottom margins
                int topMargin = 12;  // Even larger top margin to ensure first row is fully preserved
                int sideMargin = 3;
                int bottomMargin = 3;
                Rect studentInner = new Rect(
                    studentBoxRect.x() + sideMargin, 
                    studentBoxRect.y() + topMargin,
                    studentBoxRect.width() - 2 * sideMargin,
                    studentBoxRect.height() - topMargin - bottomMargin
                );
                Rect testInner = new Rect(
                    testBoxRect.x() + sideMargin,
                    testBoxRect.y() + topMargin,
                    testBoxRect.width() - 2 * sideMargin,
                    testBoxRect.height() - topMargin - bottomMargin
                );
                
                System.out.println("  Cut-out Student ID box: " + studentInner.width() + "x" + studentInner.height() + 
                    " at (" + studentInner.x() + "," + studentInner.y() + ")");
                System.out.println("  Cut-out Test ID box: " + testInner.width() + "x" + testInner.height() + 
                    " at (" + testInner.x() + "," + testInner.y() + ")");
            
            Mat studentBox = binary.apply(studentInner).clone();
            Mat testBox = binary.apply(testInner).clone();
            
            if (saveDebugImages) {
                imwrite(debugOutputDir + "/10_student_box.png", studentBox);
                imwrite(debugOutputDir + "/11_test_box.png", testBox);
            }
            
                // Extract digits from within the cut-out boxes
                result.studentId = extractDigitsFromBox(studentBox, 10);
                result.testId = extractDigitsFromBox(testBox, 4);
                
                // Use grid fill as fallback for missing or questionable digits
                // Pass the original binary (not the cut-out box) for grid sampling
                Mat studentBoxForGrid = binary.apply(studentInner).clone();
                Mat testBoxForGrid = binary.apply(testInner).clone();
                result.studentId = fillMissingDigitsWithGrid(studentBoxForGrid, result.studentId, 10, 0, 1.0, studentBoxForGrid.cols(), studentBoxForGrid.rows());
                result.testId = fillMissingDigitsWithGrid(testBoxForGrid, result.testId, 4, 0, 1.0, testBoxForGrid.cols(), testBoxForGrid.rows());
                studentBoxForGrid.release();
                testBoxForGrid.release();
                
                studentBox.release();
                testBox.release();
            
            // Count detected digits for confidence
            if (result.studentId != null) {
                long detected = result.studentId.chars().filter(c -> c != '?').count();
                result.studentIdConfidence = (double) detected / STUDENT_ID_DIGITS;
            }
            if (result.testId != null) {
                long detected = result.testId.chars().filter(c -> c != '?').count();
                result.testIdConfidence = (double) detected / TEST_ID_DIGITS;
            }
            
            // Fallback to bubble detection if direct grid fails
            if (result.studentId == null || result.studentIdConfidence < 0.5) {
                List<DigitColumn> columns = findDigitColumns(binary, imgWidth);
            
                System.out.println("  Found " + columns.size() + " digit columns (fallback)");
            
            if (columns.size() >= STUDENT_ID_DIGITS) {
                // First 10 columns are Student ID
                StringBuilder studentIdBuilder = new StringBuilder();
                int studentIdDetected = 0;
                
                for (int i = 0; i < STUDENT_ID_DIGITS && i < columns.size(); i++) {
                    DigitColumn col = columns.get(i);
                    int digit = detectDigitInColumn(binary, col);
                    if (digit >= 0) {
                        studentIdBuilder.append(digit);
                        studentIdDetected++;
                    } else {
                        studentIdBuilder.append("?");
                    }
                }
                
                result.studentId = studentIdBuilder.toString();
                result.studentIdConfidence = (double) studentIdDetected / STUDENT_ID_DIGITS;
                System.out.println("  Student ID: " + result.studentId + 
                    " (confidence: " + String.format("%.0f%%", result.studentIdConfidence * 100) + ")");
            }
            
            if (columns.size() >= STUDENT_ID_DIGITS + TEST_ID_DIGITS) {
                // Columns 10-13 are Test ID
                StringBuilder testIdBuilder = new StringBuilder();
                int testIdDetected = 0;
                
                for (int i = STUDENT_ID_DIGITS; i < STUDENT_ID_DIGITS + TEST_ID_DIGITS && i < columns.size(); i++) {
                    DigitColumn col = columns.get(i);
                    int digit = detectDigitInColumn(binary, col);
                    if (digit >= 0) {
                        testIdBuilder.append(digit);
                        testIdDetected++;
                    } else {
                        testIdBuilder.append("?");
                    }
                }
                
                result.testId = testIdBuilder.toString();
                result.testIdConfidence = (double) testIdDetected / TEST_ID_DIGITS;
                System.out.println("  Test ID: " + result.testId + 
                    " (confidence: " + String.format("%.0f%%", result.testIdConfidence * 100) + ")");
            }
            
            // Draw debug image showing detected columns
                if (saveDebugImages) {
                    Mat debug = new Mat();
                    cvtColor(binary, debug, COLOR_GRAY2BGR);
                    
                    for (int i = 0; i < columns.size(); i++) {
                        DigitColumn col = columns.get(i);
                        // Green for student ID, Blue for test ID
                        Scalar color = (i < STUDENT_ID_DIGITS) ? 
                            new Scalar(0, 255, 0, 255) : new Scalar(255, 0, 0, 255);
                        rectangle(debug, col.bounds, color, 2, LINE_8, 0);
                        
                        // Mark detected digit rows
                        for (int row = 0; row < col.digitBounds.length; row++) {
                            if (col.digitBounds[row] != null) {
                                rectangle(debug, col.digitBounds[row], 
                                    new Scalar(0, 255, 255, 255), 1, LINE_8, 0);
                            }
                        }
                    }
                    
                    imwrite(debugOutputDir + "/10_id_columns_debug.png", debug);
                    debug.release();
                }
            }  // End of fallback bubble detection
            
            gray.release();
            binary.release();
            idSection.release();
            
            result.success = (result.studentId != null || result.testId != null);
            
        } catch (Exception e) {
            result.errorMessage = e.getMessage();
            e.printStackTrace();
        }
        
        return result;
    }
    
    /**
     * Detect bordered rectangles by finding the actual border lines.
     * Uses edge detection and looks for rectangular regions enclosed by borders.
     */
    private Rect[] detectIdBoxesByBorders(Mat binary) {
        int width = binary.cols();
        int height = binary.rows();
        
        // In binary image, borders are white (THRESH_BINARY_INV makes filled bubbles white)
        // But borders should be black (empty). Let's invert to make borders white
        Mat inverted = new Mat();
        bitwise_not(binary, inverted);
        
        // Detect horizontal lines (borders are thick, so use large kernel)
        Mat horizontalKernel = getStructuringElement(MORPH_RECT, new Size(width / 3, 5));
        Mat horizontal = new Mat();
        morphologyEx(inverted, horizontal, MORPH_OPEN, horizontalKernel);
        
        // Detect vertical lines
        Mat verticalKernel = getStructuringElement(MORPH_RECT, new Size(5, height / 2));
        Mat vertical = new Mat();
        morphologyEx(inverted, vertical, MORPH_OPEN, verticalKernel);
        
        // Combine to find rectangular regions
        Mat boxes = new Mat();
        bitwise_and(horizontal, vertical, boxes);
        
        // Find contours
        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        findContours(boxes, contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
        
        List<Rect> candidates = new ArrayList<>();
        
        for (int i = 0; i < contours.size(); i++) {
            Rect bounds = boundingRect(contours.get(i));
            double area = bounds.width() * bounds.height();
            double imageArea = width * height;
            
            // Look for reasonably sized boxes (at least 2% of image)
            if (area < imageArea * 0.02) continue;
            
            // Should be wider than tall
            if (bounds.width() < bounds.height() * 0.6) continue;
            
            // Should be in left 60% of image
            if (bounds.x() > width * 0.6) continue;
            
            // Size ranges
            boolean isStudentSize = bounds.width() > 120 && bounds.width() < 400 
                                  && bounds.height() > 80 && bounds.height() < 350;
            boolean isTestSize = bounds.width() > 50 && bounds.width() < 200 
                               && bounds.height() > 80 && bounds.height() < 350;
            
            if (isStudentSize || isTestSize) {
                candidates.add(bounds);
                System.out.println("    Found candidate box: " + bounds.width() + "x" + bounds.height() + 
                    " at (" + bounds.x() + "," + bounds.y() + "), area=" + (int)area);
            }
        }
        
        horizontalKernel.release();
        verticalKernel.release();
        horizontal.release();
        vertical.release();
        boxes.release();
        inverted.release();
        hierarchy.release();
        
        if (candidates.isEmpty()) {
            System.out.println("    No bordered boxes detected");
            return new Rect[] {null, null};
        }
        
        // Sort by area (largest = Student ID, second = Test ID)
        candidates.sort((a, b) -> {
            int areaA = a.width() * a.height();
            int areaB = b.width() * b.height();
            return Integer.compare(areaB, areaA);
        });
        
        Rect[] result = new Rect[2];
        if (candidates.size() >= 1) {
            result[0] = candidates.get(0);
            System.out.println("    Selected Student ID box: " + result[0].width() + "x" + result[0].height());
        }
        if (candidates.size() >= 2) {
            result[1] = candidates.get(1);
            System.out.println("    Selected Test ID box: " + result[1].width() + "x" + result[1].height());
        }
        
        return result;
    }
    
    /**
     * Detect the thick-bordered rectangles for Student ID and Test ID boxes.
     * Finds contours and looks for rectangular shapes with thick borders.
     */
    private Rect[] detectIdBoxes(Mat binary) {
        int width = binary.cols();
        int height = binary.rows();
        
        // Find all contours - use RETR_TREE to get nested contours (borders might be nested)
        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        findContours(binary.clone(), contours, hierarchy, RETR_TREE, CHAIN_APPROX_SIMPLE);
        
        List<Rect> candidates = new ArrayList<>();
        
        for (int i = 0; i < contours.size(); i++) {
            Mat contour = contours.get(i);
            double area = contourArea(contour);
            Rect bounds = boundingRect(contour);
            
            // Filter by area (should be reasonably large for ID boxes)
            if (area < 5000 || area > width * height * 0.3) continue;
            
            // Should be wider than tall (horizontal layout)
            if (bounds.width() < bounds.height() * 0.5) continue;
            
            // Should be in the left 60% of the image
            if (bounds.x() > width * 0.6) continue;
            
            // Check if it's roughly rectangular (approximate polygon)
            Mat approx = new Mat();
            approxPolyDP(contour, approx, 0.02 * arcLength(contour, true), true);
            
            // Should have 4 corners (rectangle)
            if (approx.rows() >= 4 && approx.rows() <= 8) {
                // Size filters
                boolean isStudentSize = bounds.width() > 120 && bounds.width() < 400 
                                      && bounds.height() > 80 && bounds.height() < 350;
                boolean isTestSize = bounds.width() > 50 && bounds.width() < 200 
                                   && bounds.height() > 80 && bounds.height() < 350;
                
                if (isStudentSize || isTestSize) {
                    candidates.add(bounds);
                    System.out.println("    Found bordered box: " + bounds.width() + "x" + bounds.height() + 
                        " at (" + bounds.x() + "," + bounds.y() + "), area=" + (int)area + ", corners=" + approx.rows());
                }
            }
            approx.release();
        }
        
        hierarchy.release();
        
        if (candidates.isEmpty()) {
            System.out.println("    No bordered boxes found");
            return new Rect[] {null, null};
        }
        
        // Sort by X position (left to right) - Student ID should be leftmost
        candidates.sort(Comparator.comparingInt(Rect::x));
        
        Rect[] result = new Rect[2];
        
        if (candidates.size() >= 1) {
            result[0] = candidates.get(0);
            System.out.println("    Selected Student ID box: " + result[0].width() + "x" + result[0].height() + 
                " at (" + result[0].x() + "," + result[0].y() + ")");
        }
        
        if (candidates.size() >= 2) {
            result[1] = candidates.get(1);
            System.out.println("    Selected Test ID box: " + result[1].width() + "x" + result[1].height() + 
                " at (" + result[1].x() + "," + result[1].y() + ")");
        }
        
        return result;
    }
    
    /**
     * Alternative: Detect boxes by finding regions with high bubble density.
     * Uses the actual blob detection to find column positions.
     */
    private Rect[] detectIdBoxesByBubbleDensity(Mat binary) {
        // This method is called from detectIdBoxes, so we already have the binary image
        // Just return null to fall back to blob-based extraction
        // The extractDigitsFromBox will be called with the full binary if boxes are null
        return new Rect[] {null, null};
    }
    
    /**
     * OLD METHOD - kept for reference but not used
     */
    private Rect[] detectIdBoxesByBubbleDensityOld(Mat binary) {
        int width = binary.cols();
        int height = binary.rows();
        
        // Find all blobs first
        Mat eroded = new Mat();
        Mat kernel = getStructuringElement(MORPH_ELLIPSE, new Size(3, 3));
        erode(binary, eroded, kernel, new Point(-1, -1), 1, BORDER_CONSTANT, Scalar.all(0));
        
        Mat labels = new Mat();
        Mat stats = new Mat();
        Mat centroids = new Mat();
        int numLabels = connectedComponentsWithStats(eroded, labels, stats, centroids);
        
        // Group blobs by X position to find columns
        Map<Integer, List<Integer>> xGroups = new HashMap<>();
        
        for (int i = 1; i < numLabels; i++) {
            int area = stats.ptr(i).getInt(CC_STAT_AREA * 4);
            if (area < 20 || area > 600) continue;
            
            int x = stats.ptr(i).getInt(CC_STAT_LEFT * 4);
            int y = stats.ptr(i).getInt(CC_STAT_TOP * 4);
            int w = stats.ptr(i).getInt(CC_STAT_WIDTH * 4);
            int h = stats.ptr(i).getInt(CC_STAT_HEIGHT * 4);
            
            int centerX = x + w / 2;
            int centerY = y + h / 2;
            
            // Group by X position (within 25 pixels for finer grouping)
            int xGroup = (centerX / 25) * 25;
            xGroups.computeIfAbsent(xGroup, k -> new ArrayList<>()).add(centerY);
        }
        
        labels.release();
        stats.release();
        centroids.release();
        eroded.release();
        kernel.release();
        
        // Find X groups with many blobs (8+ for Student ID columns, 3+ for Test ID columns)
        List<Integer> studentXGroups = new ArrayList<>();
        List<Integer> testXGroups = new ArrayList<>();
        
        System.out.println("    Found " + xGroups.size() + " X groups");
        for (Map.Entry<Integer, List<Integer>> entry : xGroups.entrySet()) {
            int xPos = entry.getKey();
            int blobCount = entry.getValue().size();
            System.out.println("      X group at " + xPos + ": " + blobCount + " blobs");
            
            if (blobCount >= 7) {  // At least 7 blobs (digits 1-9,0)
                if (xPos < width * 0.3) {
                    studentXGroups.add(xPos);
                } else if (xPos < width * 0.5) {
                    testXGroups.add(xPos);
                }
            } else if (blobCount >= 3 && xPos >= width * 0.3 && xPos < width * 0.5) {
                // Test ID might have fewer blobs (only 3-4 digits filled)
                testXGroups.add(xPos);
            }
        }
        
        Rect[] result = new Rect[2];
        
        if (!studentXGroups.isEmpty()) {
            // Estimate Student ID box from blob positions
            int studentXMin = studentXGroups.stream().mapToInt(i -> i).min().orElse(0) - 30;
            int studentXMax = studentXGroups.stream().mapToInt(i -> i).max().orElse(0) + 80;
            int studentYMin = 0;
            int studentYMax = height;
            
            result[0] = new Rect(studentXMin, studentYMin, studentXMax - studentXMin, studentYMax - studentYMin);
            System.out.println("    Estimated Student ID box: " + result[0].width() + "x" + result[0].height() + 
                " from " + studentXGroups.size() + " columns");
        }
        
        if (!testXGroups.isEmpty()) {
            // Estimate Test ID box from blob positions
            int testXMin = testXGroups.stream().mapToInt(i -> i).min().orElse(0) - 20;
            int testXMax = testXGroups.stream().mapToInt(i -> i).max().orElse(0) + 60;
            int testYMin = 0;
            int testYMax = height;
            
            result[1] = new Rect(testXMin, testYMin, testXMax - testXMin, testYMax - testYMin);
            System.out.println("    Estimated Test ID box: " + result[1].width() + "x" + result[1].height() + 
                " from " + testXGroups.size() + " columns");
        }
        
        return result;
    }
    
    /**
     * Extract digits from within a detected ID box using blob detection.
     * Works within the cut-out box region.
     */
    private String extractDigitsFromBox(Mat boxBinary, int numDigits) {
        int width = boxBinary.cols();
        int height = boxBinary.rows();
        
        // Try with minimal erosion to catch faint blobs
        // Also try without erosion for comparison
        Mat eroded = new Mat();
        Mat kernel = getStructuringElement(MORPH_ELLIPSE, new Size(2, 2));  // Smaller kernel
        erode(boxBinary, eroded, kernel, new Point(-1, -1), 1, BORDER_CONSTANT, Scalar.all(0));
        
        Mat noErosion = boxBinary.clone();
        
        // Find connected components
        Mat labels = new Mat();
        Mat stats = new Mat();
        Mat centroids = new Mat();
        int numLabels = connectedComponentsWithStats(eroded, labels, stats, centroids);
        
        // Collect blob centers with row information
        List<int[]> blobs = new ArrayList<>();  // {x, y, row}
        int numRows = 12;
        int rowHeight = height / numRows;
        
        for (int i = 1; i < numLabels; i++) {
            int area = stats.ptr(i).getInt(CC_STAT_AREA * 4);
            int x = stats.ptr(i).getInt(CC_STAT_LEFT * 4);
            int y = stats.ptr(i).getInt(CC_STAT_TOP * 4);
            int w = stats.ptr(i).getInt(CC_STAT_WIDTH * 4);
            int h = stats.ptr(i).getInt(CC_STAT_HEIGHT * 4);
            
            // Filter by area (typical bubble size) - be more lenient
            if (area < 15 || area > 600) continue;
            
            // Filter by aspect ratio (roughly circular)
            double aspectRatio = (double) w / h;
            if (aspectRatio < 0.5 || aspectRatio > 2.0) continue;
            
            int centerX = x + w / 2;
            int centerY = y + h / 2;
            int row = centerY / rowHeight;
            
            // Account for header row - if there's a header, row 0 might be at Y > 0
            // But for now, use the calculated row
            if (row >= 0 && row < numRows) {
                blobs.add(new int[] {centerX, centerY, row});
                System.out.println("      Blob: X=" + centerX + ", Y=" + centerY + ", row=" + row + ", area=" + area + ", rowHeight=" + rowHeight);
            }
        }
        
        labels.release();
        stats.release();
        centroids.release();
        eroded.release();
        kernel.release();
        
        System.out.println("    Found " + blobs.size() + " blobs in box");
        
        // Map blobs to digits: group by column (X position), then find row (digit)
        return mapBlobsToDigitsInBox(blobs, width, height, numDigits);
    }
    
    /**
     * Map blobs to digits within a cut-out box.
     * Sorts by X position, assigns to columns, then maps row to digit.
     */
    private String mapBlobsToDigitsInBox(List<int[]> blobs, int boxWidth, int boxHeight, int numDigits) {
        if (blobs.isEmpty()) return "?".repeat(numDigits);
        
        // Sort blobs by X position (left to right)
        blobs.sort(Comparator.comparingInt(b -> b[0]));
        
        // Filter duplicates (blobs too close in X) - be more lenient
        List<int[]> filtered = new ArrayList<>();
        int lastX = -100;
        for (int[] blob : blobs) {
            if (blob[0] - lastX >= 8) {  // At least 8 pixels apart (more lenient)
                filtered.add(blob);
                lastX = blob[0];
            } else {
                // If too close, keep the one with better row alignment
                if (!filtered.isEmpty()) {
                    int[] last = filtered.get(filtered.size() - 1);
                    // Prefer blob closer to expected row center
                    int expectedRowY = blob[2] * (boxHeight / 12) + (boxHeight / 24);
                    int lastExpectedY = last[2] * (boxHeight / 12) + (boxHeight / 24);
                    if (Math.abs(blob[1] - expectedRowY) < Math.abs(last[1] - lastExpectedY)) {
                        filtered.set(filtered.size() - 1, blob);
                        lastX = blob[0];
                    }
                }
            }
        }
        
        char[] result = new char[numDigits];
        java.util.Arrays.fill(result, '?');
        
        // Assign blobs to columns based on X position
        // First, find the leftmost and rightmost blobs to determine column boundaries
        if (filtered.isEmpty()) return "?".repeat(numDigits);
        
        // Calculate column width based on box width
        double colWidth = (double) boxWidth / numDigits;
        
        // Find the leftmost blob that's clearly a bubble (not a row label)
        // Look for blobs that are:
        // 1. Not too close to left edge (X > 20% of box width)
        // 2. In reasonable rows (0-8)
        // 3. Have reasonable area (not too small)
        int leftmostBubbleX = boxWidth;
        double minXForBubble = boxWidth * 0.20;  // Bubbles start after 20% of box width
        
        for (int[] blob : filtered) {
            int x = blob[0];
            int y = blob[1];
            int area = blob[2];
            int row = y / (boxHeight / 12);
            
            // Must be past the row label area and have reasonable size
            if (x > minXForBubble && row >= 0 && row <= 8 && area > 30 && x < leftmostBubbleX) {
                leftmostBubbleX = x;
            }
        }
        
        // If we didn't find a good leftmost bubble, use a default
        if (leftmostBubbleX >= boxWidth) {
            leftmostBubbleX = (int)(boxWidth * 0.25);  // Default to 25% of box width
        }
        
        // Use the leftmost bubble as the start of the bubble area
        // Subtract a small margin to account for bubble center vs left edge
        double bubbleAreaStart = Math.max(0, leftmostBubbleX - 5);
        double bubbleAreaWidth = boxWidth - bubbleAreaStart;
        double bubbleColWidth = bubbleAreaWidth / numDigits;
        
        System.out.println("    Detected bubble area start at X=" + (int)bubbleAreaStart + " (leftmost bubble at X=" + leftmostBubbleX + ")");
        
        // Filter out blobs that are too far left (before bubble area)
        List<int[]> validBlobs = new ArrayList<>();
        for (int[] blob : filtered) {
            if (blob[0] < bubbleAreaStart) {
                System.out.println("      Filtered row label blob: X=" + blob[0] + " (before bubble area)");
                continue;
            }
            validBlobs.add(blob);
        }
        
        System.out.println("    After filtering row labels: " + validBlobs.size() + " blobs");
        
        Map<Integer, List<int[]>> columnGroups = new HashMap<>();
        
        for (int[] blob : validBlobs) {
            int x = blob[0];
            // Calculate column relative to bubble area (not including row labels)
            int col = (int) ((x - bubbleAreaStart) / bubbleColWidth);
            if (col < 0) col = 0;
            if (col >= numDigits) col = numDigits - 1;
            
            columnGroups.computeIfAbsent(col, k -> new ArrayList<>()).add(blob);
            System.out.println("      Blob at X=" + x + " -> col=" + col + " (bubble area starts at " + (int)bubbleAreaStart + ")");
        }
        
        // Calculate row height for mapping Y to row
        int numRows = 12;
        int rowHeight = boxHeight / numRows;
        
        // For each column, find the best blob
        for (int col = 0; col < numDigits; col++) {
            List<int[]> colBlobs = columnGroups.get(col);
            if (colBlobs == null || colBlobs.isEmpty()) continue;
            
            // Expected X range for this column
            double expectedXCenter = bubbleAreaStart + col * bubbleColWidth + bubbleColWidth / 2;
            
            System.out.println("      Col " + col + ": Found " + colBlobs.size() + " blobs, expected X=" + (int)expectedXCenter);
            
            // Find blob closest to expected X position with valid row
            int bestRow = -1;
            int bestY = -1;
            int bestX = -1;
            double bestScore = -1;
            
            for (int[] blob : colBlobs) {
                int x = blob[0];
                int y = blob[1];
                int area = blob[2];
                int row = y / rowHeight;
                if (row < 0 || row >= numRows) continue;
                
                // Check X distance from expected column center
                double xDistance = Math.abs(x - expectedXCenter);
                if (xDistance > bubbleColWidth * 0.7) continue;  // Too far from column
                
                // For early columns (0-1), filter out blobs with very high rows (row > 6)
                // These are likely from later columns that got mis-grouped
                if (col < 2 && row > 6) continue;
                
                // For last columns (8-9), slightly prefer higher rows (row >= 8 for digit 9-0)
                // But don't exclude lower rows - just give them lower score
                double rowBonus = 1.0;
                if (col >= 8 && row >= 8) {
                    rowBonus = 1.2;  // Slight bonus for high rows in last columns
                } else if (col >= 8 && row < 7) {
                    rowBonus = 0.8;  // Slight penalty for low rows in last columns
                }
                
                // Calculate score: prefer blobs closer to column center and row center
                int rowCenter = row * rowHeight + rowHeight / 2;
                double yDistance = Math.abs(y - rowCenter);
                
                // Normalize yDistance by row height (prefer blobs more centered in row)
                double normalizedYDistance = yDistance / rowHeight;
                
                // Filter out blobs that are too large (likely noise or merged blobs)
                // Typical bubble area is 15-200, very large blobs (>250) are suspicious
                double areaPenalty = 1.0;
                if (area > 250) {
                    areaPenalty = 0.3;  // Heavy penalty for very large blobs
                } else if (area > 200) {
                    areaPenalty = 0.6;  // Moderate penalty for large blobs
                }
                
                // Score = area / (distances + 1) - prefer larger, well-positioned blobs
                // Give more weight to row centering (yDistance) than column centering
                // Apply row bonus for last columns and area penalty for oversized blobs
                double score = area / (normalizedYDistance + 0.1) / (xDistance / bubbleColWidth + 0.3) * rowBonus * areaPenalty;
                
                int digit = (row >= 9) ? 0 : (row + 1);
                System.out.println("        Blob: X=" + x + ", Y=" + y + ", row=" + row + " (digit " + digit + "), area=" + area + 
                    ", X-dist=" + String.format("%.1f", xDistance) + ", Y-dist=" + String.format("%.1f", yDistance) + 
                    ", score=" + String.format("%.2f", score));
                
                if (score > bestScore) {
                    bestScore = score;
                    bestRow = row;
                    bestY = y;
                    bestX = x;
                }
            }
            
            if (bestRow >= 0) {
                // Row 0 = digit 1, Row 8 = digit 9, Row 9+ = digit 0
                int digit = (bestRow >= 9) ? 0 : (bestRow + 1);
                result[col] = (char)('0' + digit);
                System.out.println("      Mapped: col=" + col + " -> digit=" + digit + " (row=" + bestRow + ", Y=" + bestY + ", X=" + bestX + ")");
            }
        }
        
        return new String(result);
    }
    
    /**
     * Map blobs to digits using relative positioning (leftmost = col 0, etc.)
     * Filters out noise blobs that are too close to other blobs.
     */
    private String mapBlobsToDigitsRelative(List<int[]> blobs, int imgWidth, int imgHeight, int numDigits) {
        if (blobs.isEmpty()) return "?".repeat(numDigits);
        
        // Sort blobs by X to determine column
        blobs.sort(Comparator.comparingInt(p -> p[0]));
        
        // Simple relative positioning: sort by X, take first N, map by row
        blobs.sort(Comparator.comparingInt(p -> p[0]));
        
        // Filter duplicates (blobs within 15 pixels of each other)
        List<int[]> filtered = new ArrayList<>();
        int lastX = -100;
        for (int[] blob : blobs) {
            if (blob[0] - lastX >= 15) {
                filtered.add(blob);
                lastX = blob[0];
                if (filtered.size() >= numDigits) break;
            }
        }
        
        char[] result = new char[numDigits];
        java.util.Arrays.fill(result, '?');
        
        int numRows = 12;
        int rowHeight = imgHeight / numRows;
        
        // Map blobs in order to columns 0, 1, 2, ...
        for (int i = 0; i < Math.min(filtered.size(), numDigits); i++) {
            int[] blob = filtered.get(i);
            int row = blob[1] / rowHeight;
            if (row < 0 || row >= numRows) continue;
            
            // Row 0 = digit 1, Row 8 = digit 9, Row 9+ = digit 0
            int digit = (row >= 9) ? 0 : (row + 1);
            result[i] = (char)('0' + digit);
        }
        
        return new String(result);
    }
    
    /**
     * Process a set of blobs from connected components.
     * Uses a Map to track best blob for each position (by area).
     */
    private void processBlobSet(Mat source, Mat labels, Mat stats, int numLabels, int width, int height,
                               List<int[]> studentBlobs, List<int[]> testBlobs, Set<String> seenBlobs, String method) {
        // Use maps to track best blob for each approximate position
        Map<String, int[]> bestStudentBlobs = new HashMap<>();  // key = "col,row", value = {x, y, area}
        Map<String, int[]> bestTestBlobs = new HashMap<>();
        
        for (int i = 1; i < numLabels; i++) {
            int area = stats.ptr(i).getInt(CC_STAT_AREA * 4);
            int x = stats.ptr(i).getInt(CC_STAT_LEFT * 4);
            int y = stats.ptr(i).getInt(CC_STAT_TOP * 4);
            int w = stats.ptr(i).getInt(CC_STAT_WIDTH * 4);
            int h = stats.ptr(i).getInt(CC_STAT_HEIGHT * 4);
            
            int centerX = x + w / 2;
            int centerY = y + h / 2;
            double xRatio = (double) centerX / width;
            
            // Filter by area - different thresholds for different methods
            // For "no erosion", be more lenient to catch faint blobs
            int minArea = method.equals("eroded") ? 20 : 30;
            int maxArea = 700;
            if (area < minArea || area > maxArea) continue;
            
            double aspectRatio = (double) w / h;
            if (aspectRatio < 0.4 || aspectRatio > 2.5) continue;
            
            // Student ID: X = 5.5-29% (wider to catch 10th digit)
            if (xRatio >= 0.055 && xRatio < 0.29 && centerX >= width * 0.055) {
                // Group by approximate column and row (10px tolerance)
                int col = (centerX - (int)(width * 0.055)) / 20;
                int row = centerY / 28;
                String key = col + "," + row;
                
                // Keep blob with largest area for this position
                if (!bestStudentBlobs.containsKey(key) || bestStudentBlobs.get(key)[2] < area) {
                    bestStudentBlobs.put(key, new int[] {centerX, centerY, area});
                    if (centerY >= 250 || (xRatio >= 0.24 && xRatio < 0.29)) {
                        System.out.println("      Student blob (" + method + "): X=" + centerX + " (" + String.format("%.1f%%", xRatio*100) + 
                            "), Y=" + centerY + ", area=" + area + ", row=" + row);
                    }
                }
            } 
            // Test ID: X = 33-44% (wider to catch 4th digit)
            else if (xRatio >= 0.33 && xRatio < 0.44 && centerY <= 110) {
                int col = (centerX - (int)(width * 0.33)) / 25;
                int row = centerY / 28;
                String key = col + "," + row;
                
                if (!bestTestBlobs.containsKey(key) || bestTestBlobs.get(key)[2] < area) {
                    bestTestBlobs.put(key, new int[] {centerX, centerY, area});
                    if (centerY >= 85 && centerY <= 105) {
                        System.out.println("      Test blob (" + method + "): X=" + centerX + " (" + String.format("%.1f%%", xRatio*100) + 
                            "), Y=" + centerY + ", area=" + area + ", row=" + row);
                    }
                }
            }
        }
        
        // Add best blobs to lists
        for (int[] blob : bestStudentBlobs.values()) {
            String key = blob[0] + "," + blob[1];
            if (!seenBlobs.contains(key)) {
                seenBlobs.add(key);
                studentBlobs.add(new int[] {blob[0], blob[1]});
            }
        }
        
        for (int[] blob : bestTestBlobs.values()) {
            String key = blob[0] + "," + blob[1];
            if (!seenBlobs.contains(key)) {
                seenBlobs.add(key);
                testBlobs.add(new int[] {blob[0], blob[1]});
            }
        }
    }
    
    /**
     * Cut out Student ID and Test ID regions, then extract from each.
     */
    private String[] extractByCuttingRegions(Mat binary, int width, int height) {
        // Cut out Student ID region: X = 5.5-28%, Y = 0-100%
        int studentXStart = (int)(width * 0.055);
        int studentXEnd = (int)(width * 0.29);
        int studentWidth = studentXEnd - studentXStart;
        Rect studentRect = new Rect(studentXStart, 0, studentWidth, height);
        Mat studentRegion = binary.apply(studentRect).clone();
        
        // Cut out Test ID region: X = 33-44%, Y = 0-100%
        int testXStart = (int)(width * 0.33);
        int testXEnd = (int)(width * 0.44);
        int testWidth = testXEnd - testXStart;
        Rect testRect = new Rect(testXStart, 0, testWidth, height);
        Mat testRegion = binary.apply(testRect).clone();
        
        System.out.println("  Cut Student ID region: " + studentWidth + "x" + height + " at X=" + studentXStart);
        System.out.println("  Cut Test ID region: " + testWidth + "x" + height + " at X=" + testXStart);
        
        // Extract from each region
        String studentId = extractDigitsFromBox(studentRegion, 10);
        String testId = extractDigitsFromBox(testRegion, 4);
        
        studentRegion.release();
        testRegion.release();
        
        return new String[] {studentId, testId};
    }
    
    /**
     * Extract IDs using blob detection with region cropping.
     * First finds blobs, then crops to their bounding regions.
     */
    private String[] extractByBlobDetectionWithRegions(Mat binary) {
        int width = binary.cols();
        int height = binary.rows();
        
        // Use eroded for main detection, then grid sampling for missing positions
        Mat eroded = new Mat();
        Mat kernel = getStructuringElement(MORPH_ELLIPSE, new Size(3, 3));
        erode(binary, eroded, kernel, new Point(-1, -1), 1, BORDER_CONSTANT, Scalar.all(0));
        
        Mat labels = new Mat();
        Mat stats = new Mat();
        Mat centroids = new Mat();
        int numLabels = connectedComponentsWithStats(eroded, labels, stats, centroids);
        
        Set<String> seenBlobs = new HashSet<>();
        List<int[]> studentBlobs = new ArrayList<>();
        List<int[]> testBlobs = new ArrayList<>();
        
        // Process eroded blobs
        processBlobSet(eroded, labels, stats, numLabels, width, height, studentBlobs, testBlobs, seenBlobs, "eroded");
        
        labels.release();
        stats.release();
        centroids.release();
        eroded.release();
        kernel.release();
        
        System.out.println("    Found " + studentBlobs.size() + " student blobs, " + testBlobs.size() + " test blobs");
        
        // Map blobs to digits using relative positioning
        String studentId = mapBlobsToDigitsRelative(studentBlobs, width, height, 10);
        String testId = mapBlobsToDigitsRelative(testBlobs, width, height, 4);
        
        // Fill missing positions using grid sampling
        studentId = fillMissingDigitsWithGrid(binary, studentId, 10, 0.055, 0.29, width, height);
        testId = fillMissingDigitsWithGrid(binary, testId, 4, 0.33, 0.44, width, height);
        
        System.out.println("    StudentID by blob: " + studentId);
        System.out.println("    TestID by blob: " + testId);
        
        return new String[] { studentId, testId };
    }
    
    /**
     * Fill missing digits using grid sampling at expected positions.
     */
    private String fillMissingDigitsWithGrid(Mat binary, String current, int numDigits, 
                                            double xStart, double xEnd, int width, int height) {
        if (!current.contains("?")) {
            System.out.println("      Grid fill: " + current + " already complete");
            return current;  // Already complete
        }
        
        System.out.println("      Grid fill: filling missing digits in " + current);
        char[] result = current.toCharArray();
        int gridLeft = (int)(width * xStart);
        int gridWidth = (int)(width * (xEnd - xStart));
        int colWidth = gridWidth / numDigits;
        int numRows = 12;
        int rowHeight = height / numRows;
        
        // For each position, check if we should verify/correct it
        for (int col = 0; col < numDigits; col++) {
            // Only check missing positions - don't try to correct existing values
            // Grid fill is not reliable enough for correction
            if (result[col] != '?') continue;
            
            int colX = gridLeft + col * colWidth;
            int bestRow = -1;
            int bestY = -1;
            int maxFill = 0;
            int secondFill = 0;
            
            for (int row = 0; row < numRows; row++) {
                int rowY = row * rowHeight;
                
                // Try multiple sampling positions for better detection
                int[] sampleXs = {colX + colWidth / 3, colX + colWidth / 2, colX + colWidth * 2 / 3};
                int[] sampleYs = {rowY + rowHeight / 4, rowY + rowHeight / 2, rowY + rowHeight * 3 / 4};
                int sampleW = colWidth / 4;
                int sampleH = rowHeight / 4;
                
                int totalFill = 0;
                int sampleCount = 0;
                
                for (int sx : sampleXs) {
                    for (int sy : sampleYs) {
                        if (sx < 0 || sy < 0) continue;
                        if (sx + sampleW > width) continue;
                        if (sy + sampleH > height) continue;
                        
                        // Sample from binary - count white pixels (filled bubbles are white in binary)
                        Mat binarySample = binary.apply(new Rect(sx, sy, sampleW, sampleH));
                        int whitePixels = countNonZero(binarySample);
                        totalFill += whitePixels;
                        sampleCount++;
                    }
                }
                
                int fill = (sampleCount > 0) ? (totalFill / sampleCount) : 0;
                
                if (fill > maxFill) {
                    secondFill = maxFill;
                    maxFill = fill;
                    bestRow = row;
                } else if (fill > secondFill) {
                    secondFill = fill;
                }
            }
            
            // Row 0 = digit 1, Row 8 = digit 9, Row 9+ = digit 0
            if (bestRow >= 0 && maxFill > 8) {
                int digit = (bestRow >= 9) ? 0 : (bestRow + 1);
                
                // For missing positions, be more lenient
                // Accept if maxFill is clearly best or reasonably high
                boolean accept = (maxFill > secondFill * 1.05) || (maxFill > secondFill && maxFill > 25);
                
                if (accept) {
                    result[col] = (char)('0' + digit);
                    System.out.println("      Grid fill: col=" + col + " -> digit=" + digit + " (row=" + bestRow + ", fill=" + maxFill + ")");
                }
            }
        }
        
        return new String(result);
    }
    
    /**
     * Extract IDs using blob detection - find actual filled bubbles.
     */
    private String[] extractByBlobDetection(Mat binary) {
        int width = binary.cols();
        int height = binary.rows();
        
        // Use minimal erosion to isolate filled bubbles but keep more blobs
        Mat eroded = new Mat();
        Mat kernel = getStructuringElement(MORPH_ELLIPSE, new Size(3, 3));
        erode(binary, eroded, kernel, new Point(-1, -1), 1, BORDER_CONSTANT, Scalar.all(0));
        
        // Also try without erosion for Test ID region specifically
        Mat noErosion = binary.clone();
        
        // Process Student ID with erosion
        List<int[]> studentBlobs = extractBlobsFromImage(eroded, width, 0.055, 0.28, "Student");
        
        // Process Test ID without erosion (to catch more blobs)
        List<int[]> testBlobs = extractBlobsFromImage(noErosion, width, 0.33, 0.43, "Test");
        
        eroded.release();
        noErosion.release();
        kernel.release();
        
        System.out.println("    Found " + studentBlobs.size() + " student blobs, " + 
                          testBlobs.size() + " test blobs");
        
        // Map blobs to digits
        double studentXStart = 0.055;
        double studentXEnd = 0.28;
        double testXStart = 0.33;
        double testXEnd = 0.43;
        
        String studentId = mapBlobsToDigits(studentBlobs, width, height, 10, studentXStart, studentXEnd);
        String testId = mapBlobsToDigits(testBlobs, width, height, 4, testXStart, testXEnd);
        
        System.out.println("    StudentID by blob: " + studentId);
        System.out.println("    TestID by blob: " + testId);
        
        return new String[] { studentId, testId };
    }
    
    /**
     * Extract blobs from a binary image within a specific X range.
     */
    private List<int[]> extractBlobsFromImage(Mat binaryImg, int width, double xStart, double xEnd, String type) {
        List<int[]> blobs = new ArrayList<>();
        
        Mat labels = new Mat();
        Mat stats = new Mat();
        Mat centroids = new Mat();
        int numLabels = connectedComponentsWithStats(binaryImg, labels, stats, centroids);
        
        for (int i = 1; i < numLabels; i++) {
            int area = stats.ptr(i).getInt(CC_STAT_AREA * 4);
            int x = stats.ptr(i).getInt(CC_STAT_LEFT * 4);
            int y = stats.ptr(i).getInt(CC_STAT_TOP * 4);
            int w = stats.ptr(i).getInt(CC_STAT_WIDTH * 4);
            int h = stats.ptr(i).getInt(CC_STAT_HEIGHT * 4);
            
            // Filter by area
            int minArea = type.equals("Test") ? 10 : 15;  // Lower threshold for Test ID
            if (area < minArea || area > 600) continue;
            
            // Filter by aspect ratio
            double aspectRatio = (double) w / h;
            if (aspectRatio < 0.5 || aspectRatio > 2.0) continue;
            
            int centerX = x + w / 2;
            int centerY = y + h / 2;
            double xRatio = (double) centerX / width;
            
            if (xRatio >= xStart && xRatio < xEnd) {
                if (type.equals("Student") && centerX < width * 0.055) continue;
                if (type.equals("Test") && centerY > 95) continue;  // Filter row 4+
                blobs.add(new int[] {centerX, centerY});
                System.out.println("      " + type + " blob: X=" + centerX + " (" + String.format("%.1f%%", xRatio*100) + "), Y=" + centerY + ", area=" + area);
            }
        }
        
        labels.release();
        stats.release();
        centroids.release();
        
        return blobs;
    }
    
    /**
     * Map detected blobs to digit positions.
     */
    private String mapBlobsToDigits(List<int[]> blobs, int imgWidth, int imgHeight, 
                                    int numDigits, double xStart, double xEnd) {
        if (blobs.isEmpty()) return "?".repeat(numDigits);
        
        // Sort blobs by X to determine column
        blobs.sort(Comparator.comparingInt(p -> p[0]));
        
        // For Test ID (4 digits), use relative positioning (leftmost blob = col 0)
        if (numDigits == 4 && blobs.size() >= 3) {
            char[] result = new char[4];
            java.util.Arrays.fill(result, '?');
            
            int numRows = 12;
            int rowHeight = imgHeight / numRows;
            
            // Map blobs in order to columns 0, 1, 2, 3
            for (int i = 0; i < Math.min(blobs.size(), 4); i++) {
                int[] blob = blobs.get(i);
                int row = blob[1] / rowHeight;
                if (row < 0 || row >= numRows) continue;
                
                int digit = (row >= 9) ? 0 : (row + 1);
                result[i] = (char)('0' + digit);
                System.out.println("      TestID mapping: blob(" + blob[0] + "," + blob[1] + ") -> col=" + i + " row=" + row + " digit=" + digit);
            }
            
            return new String(result);
        }
        
        // For Student ID, use grid-based mapping
        int gridLeft = (int)(imgWidth * xStart);
        int gridWidth = (int)(imgWidth * (xEnd - xStart));
        int colWidth = gridWidth / numDigits;
        
        // Map each blob to a column and determine the row (digit)
        char[] result = new char[numDigits];
        java.util.Arrays.fill(result, '?');
        
        // 12 rows for better alignment
        int numRows = 12;
        int rowHeight = imgHeight / numRows;
        
        System.out.println("      StudentID grid: left=" + gridLeft + " width=" + gridWidth + " colW=" + colWidth + " rowH=" + rowHeight);
        
        // Track best blob for each column (by area, to handle conflicts)
        int[] bestAreas = new int[numDigits];
        int[] bestRows = new int[numDigits];
        java.util.Arrays.fill(bestAreas, -1);
        java.util.Arrays.fill(bestRows, -1);
        
        for (int[] blob : blobs) {
            // Use direct column calculation based on position within grid
            int xOffset = blob[0] - gridLeft;
            int col = xOffset / colWidth;
            if (col < 0) col = 0;
            if (col >= numDigits) col = numDigits - 1;
            
            // No offset needed - start Y calculation at blob position
            int adjustedY = blob[1];
            int row = adjustedY / rowHeight;
            
            if (col < 0 || col >= numDigits) continue;
            if (row < 0 || row >= numRows) continue;
            
            // Get blob area (we need to calculate it from the blob data)
            // For now, estimate area from distance - larger blobs are more reliable
            // Actually, we need to pass area through. Let me use a different approach.
            // Filter out very small blobs first, then use first-come-first-served but with area check
            
            // Row 0 = digit 1, Row 8 = digit 9, Row 9+ = digit 0
            int digit = (row >= 9) ? 0 : (row + 1);
            
            System.out.println("      StudentID mapping: blob(" + blob[0] + "," + blob[1] + ") -> col=" + col + " row=" + row + " digit=" + digit);
            
            // Only set if this column is empty, or if we have a better match
            if (result[col] == '?' || bestAreas[col] < 50) {  // Prefer larger blobs
                result[col] = (char)('0' + digit);
                bestRows[col] = row;
            }
        }
        
        return new String(result);
    }
    
    /**
     * Extract digits using direct grid sampling at known template positions.
     * 
     * @param binary Binary image
     * @param numDigits Number of digits to extract
     * @param xStartRatio Starting X position as ratio of image width
     * @param xEndRatio Ending X position as ratio of image width
     * @param type Label for debug output
     */
    private String extractByDirectGrid(Mat binary, int numDigits, double xStartRatio, double xEndRatio, String type) {
        int width = binary.cols();
        int height = binary.rows();
        
        int gridLeft = (int)(width * xStartRatio);
        int gridRight = (int)(width * xEndRatio);
        int gridWidth = gridRight - gridLeft;
        int colWidth = gridWidth / numDigits;
        
        // 9 rows for digits 1-9
        int numRows = 9;
        int rowHeight = height / numRows;
        
        StringBuilder result = new StringBuilder();
        int detected = 0;
        
        for (int col = 0; col < numDigits; col++) {
            int colX = gridLeft + col * colWidth;
            int bestRow = -1;
            int maxFill = 0;
            int secondFill = 0;
            int[] fills = new int[numRows];
            
            for (int row = 0; row < numRows; row++) {
                int rowY = row * rowHeight;
                
                // Sample the RIGHT portion of each cell (avoid row labels on left)
                int sampleX = colX + colWidth / 2;  // Start at middle of column
                int sampleY = rowY + rowHeight / 4;
                int sampleW = colWidth / 3;  // Sample right third
                int sampleH = rowHeight / 2;
                
                // Bounds check
                if (sampleX < 0 || sampleY < 0) continue;
                if (sampleX + sampleW > width) sampleW = width - sampleX;
                if (sampleY + sampleH > height) sampleH = height - sampleY;
                if (sampleW <= 0 || sampleH <= 0) continue;
                
                Mat sample = binary.apply(new Rect(sampleX, sampleY, sampleW, sampleH));
                int fill = countNonZero(sample);
                fills[row] = fill;
                
                if (fill > maxFill) {
                    secondFill = maxFill;
                    maxFill = fill;
                    bestRow = row;
                } else if (fill > secondFill) {
                    secondFill = fill;
                }
            }
            
            // Debug all columns
            if (col < 10) {
                StringBuilder fillStr = new StringBuilder("[");
                for (int r = 0; r < numRows; r++) {
                    fillStr.append(fills[r]);
                    if (r < numRows - 1) fillStr.append(",");
                }
                fillStr.append("]");
                System.out.println("      " + type + " col " + col + ": fills=" + fillStr + 
                    " best=" + bestRow + " max=" + maxFill + " 2nd=" + secondFill);
            }
            
            // Row 0 = digit 1, Row 1 = digit 2, ... Row 8 = digit 9
            // For digit 0, use row 8 (last row), will need special handling
            if (bestRow >= 0 && maxFill > 10 && maxFill > secondFill * 1.1) {
                result.append(bestRow + 1);
                detected++;
            } else {
                result.append("?");
            }
        }
        
        double confidence = (double) detected / numDigits;
        System.out.println("    " + type + ": " + result + " (confidence: " + String.format("%.0f%%", confidence * 100) + ")");
        
        // Save debug image showing grid
        if (saveDebugImages) {
            Mat debug = new Mat();
            cvtColor(binary, debug, COLOR_GRAY2BGR);
            
            for (int c = 0; c < numDigits; c++) {
                int cx = gridLeft + c * colWidth + colWidth / 2;
                for (int r = 0; r < numRows; r++) {
                    int ry = r * rowHeight + rowHeight / 2;
                    // Draw sampling points
                    circle(debug, new Point(cx + colWidth/6, ry), 2, new Scalar(0, 255, 0, 255), -1, LINE_8, 0);
                }
                // Draw column boundaries
                line(debug, new Point(gridLeft + c * colWidth, 0), 
                     new Point(gridLeft + c * colWidth, height), new Scalar(255, 0, 0, 255), 1, LINE_8, 0);
            }
            
            String filename = type.equals("StudentID") ? "/11_studentid_grid.png" : "/12_testid_grid.png";
            imwrite(debugOutputDir + filename, debug);
            debug.release();
        }
        
        return result.toString();
    }
    
    /**
     * Find ID boxes (Student ID and Test ID bordered rectangles) and extract digits.
     */
    private void extractFromBoxes(Mat binary, Result result) {
        int width = binary.cols();
        int height = binary.rows();
        
        // Find large rectangular contours (the ID boxes)
        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        findContours(binary.clone(), contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
        
        // Look for rectangles that could be ID boxes
        // Student ID: 10 cols x 9 rows, roughly 200x180 pixels  
        // Test ID: 4 cols x 9 rows, roughly 80x180 pixels
        List<Rect> boxes = new ArrayList<>();
        for (int i = 0; i < contours.size(); i++) {
            Rect bounds = boundingRect(contours.get(i));
            
            // ID boxes should be in specific size range
            // Student ID: width 100-300, height 80-250
            // Test ID: width 40-150, height 80-250
            boolean validStudentSize = bounds.width() > 100 && bounds.width() < 300 
                                     && bounds.height() > 80 && bounds.height() < 250;
            boolean validTestSize = bounds.width() > 40 && bounds.width() < 150 
                                  && bounds.height() > 80 && bounds.height() < 250;
            
            if (!validStudentSize && !validTestSize) continue;
            
            // Should be in left half of image
            if (bounds.x() > width * 0.5) continue;
            
            boxes.add(bounds);
        }
        hierarchy.release();
        
        // Sort by X position (left to right)
        boxes.sort(Comparator.comparingInt(Rect::x));
        
        System.out.println("    Found " + boxes.size() + " potential ID boxes");
        
        if (boxes.size() >= 1) {
            // First box should be Student ID (leftmost, larger)
            Rect studentBox = boxes.get(0);
            result.studentId = extractDigitsFromBox(binary, studentBox, 10);
            System.out.println("    Student ID box: " + studentBox.width() + "x" + studentBox.height() + 
                " at (" + studentBox.x() + "," + studentBox.y() + ") -> " + result.studentId);
        }
        
        if (boxes.size() >= 2) {
            // Second box should be Test ID
            Rect testBox = boxes.get(1);
            result.testId = extractDigitsFromBox(binary, testBox, 4);
            System.out.println("    Test ID box: " + testBox.width() + "x" + testBox.height() + 
                " at (" + testBox.x() + "," + testBox.y() + ") -> " + result.testId);
        }
    }
    
    /**
     * Extract digits from a detected ID box using grid sampling.
     */
    private String extractDigitsFromBox(Mat binary, Rect box, int numDigits) {
        // Skip the header row (first ~15% of box height contains column numbers)
        int headerHeight = (int)(box.height() * 0.15);
        int gridTop = box.y() + headerHeight;
        int gridHeight = box.height() - headerHeight;
        int gridLeft = box.x();
        int gridWidth = box.width();
        
        int colWidth = gridWidth / numDigits;
        int numRows = 9;  // Digits 1-9 (0 is often at bottom or separate)
        int rowHeight = gridHeight / numRows;
        
        StringBuilder result = new StringBuilder();
        
        for (int col = 0; col < numDigits; col++) {
            int colX = gridLeft + col * colWidth;
            int bestRow = -1;
            int maxFill = 0;
            int secondFill = 0;
            
            for (int row = 0; row < numRows; row++) {
                int rowY = gridTop + row * rowHeight;
                
                // Sample center of cell
                int sampleX = colX + colWidth / 4;
                int sampleY = rowY + rowHeight / 4;
                int sampleW = colWidth / 2;
                int sampleH = rowHeight / 2;
                
                // Bounds check
                if (sampleX < 0 || sampleY < 0) continue;
                if (sampleX + sampleW > binary.cols()) sampleW = binary.cols() - sampleX;
                if (sampleY + sampleH > binary.rows()) sampleH = binary.rows() - sampleY;
                if (sampleW <= 0 || sampleH <= 0) continue;
                
                Mat sample = binary.apply(new Rect(sampleX, sampleY, sampleW, sampleH));
                int fill = countNonZero(sample);
                
                if (fill > maxFill) {
                    secondFill = maxFill;
                    maxFill = fill;
                    bestRow = row;
                } else if (fill > secondFill) {
                    secondFill = fill;
                }
            }
            
            // Row 0 = digit 1, Row 1 = digit 2, ... Row 8 = digit 9
            if (bestRow >= 0 && maxFill > 15 && maxFill > secondFill * 1.3) {
                result.append(bestRow + 1);
            } else {
                result.append("?");
            }
        }
        
        return result.toString();
    }
    
    /**
     * Find digit columns in the ID section.
     * The Student ID has 10 columns, Test ID has 4 columns.
     * Each column has bubbles for digits 1-9, 0.
     */
    private List<DigitColumn> findDigitColumns(Mat binary, int imageWidth) {
        List<DigitColumn> columns = new ArrayList<>();
        
        // First, find the Student ID and Test ID boxes by looking for large rectangles
        // Then find bubbles inside them
        
        // Find contours - use RETR_LIST to get all contours including nested
        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        findContours(binary.clone(), contours, hierarchy, RETR_LIST, CHAIN_APPROX_SIMPLE);
        
        System.out.println("    Found " + contours.size() + " contours total");
        
        // Collect all potential bubble contours
        List<Rect> bubbles = new ArrayList<>();
        
        for (int i = 0; i < contours.size(); i++) {
            Rect bounds = boundingRect(contours.get(i));
            double area = contourArea(contours.get(i));
            
            // Bubbles in ID section are typically 10-30 pixels in diameter
            // Area would be roughly 75-700 pixels (pi * r^2)
            if (area < 20 || area > 800) continue;
            if (bounds.width() < 5 || bounds.height() < 5) continue;
            if (bounds.width() > 40 || bounds.height() > 40) continue;
            
            // Check rough circularity
            double perimeter = arcLength(contours.get(i), true);
            if (perimeter == 0) continue;
            double circularity = 4 * Math.PI * area / (perimeter * perimeter);
            if (circularity < 0.4) continue; // Bubbles should be fairly circular
            
            bubbles.add(bounds);
        }
        
        hierarchy.release();
        
        System.out.println("    Found " + bubbles.size() + " potential bubbles");
        
        // Group bubbles by X position (columns)
        // The ID section is in the left ~40% of the image
        int idSectionWidth = (int)(imageWidth * 0.5);  // Student ID + Test ID region
        int groupWidth = idSectionWidth / 20;  // Expect ~14 columns in this region
        
        Map<Integer, List<Rect>> contoursByX = new TreeMap<>();
        // Skip leftmost 5% (row labels) and rightmost 50% (info section)
        int leftMargin = (int)(imageWidth * 0.03);
        int rightLimit = (int)(imageWidth * 0.45);
        
        for (Rect r : bubbles) {
            int centerX = r.x() + r.width() / 2;
            
            // Only consider bubbles in the Student ID + Test ID region
            if (centerX < leftMargin || centerX > rightLimit) continue;
            
            int xGroup = centerX / groupWidth;
            contoursByX.computeIfAbsent(xGroup, k -> new ArrayList<>()).add(r);
        }
        
        int totalGrouped = contoursByX.values().stream().mapToInt(List::size).sum();
        System.out.println("    Grouped: " + totalGrouped + " bubbles in " + contoursByX.size() + " X-groups");
        
        // Identify valid columns (need at least 3 bubbles vertically aligned)
        for (Map.Entry<Integer, List<Rect>> entry : contoursByX.entrySet()) {
            List<Rect> rects = entry.getValue();
            if (rects.size() < 3) continue;
            
            // Sort by Y position
            rects.sort(Comparator.comparingInt(Rect::y));
            
            // Calculate average X and width
            int sumX = 0, sumWidth = 0;
            for (Rect r : rects) {
                sumX += r.x();
                sumWidth += r.width();
            }
            int avgX = sumX / rects.size();
            int avgWidth = sumWidth / rects.size();
            
            // Create column
            DigitColumn col = new DigitColumn();
            col.centerX = avgX + avgWidth / 2;
            
            // Calculate column bounds
            int minY = rects.get(0).y();
            int maxY = rects.get(rects.size() - 1).y() + rects.get(rects.size() - 1).height();
            col.bounds = new Rect(avgX - avgWidth / 2, minY, avgWidth * 2, maxY - minY);
            
            // Assign bubbles to digit rows (0-9)
            col.digitBounds = new Rect[DIGIT_ROWS];
            int height = maxY - minY;
            if (height < 10) continue;  // Skip if column too short
            
            int rowHeight = height / DIGIT_ROWS;
            if (rowHeight == 0) rowHeight = 1;  // Prevent division by zero
            
            for (Rect r : rects) {
                int rowIndex = (r.y() - minY) / rowHeight;
                if (rowIndex >= 0 && rowIndex < DIGIT_ROWS) {
                    col.digitBounds[rowIndex] = r;
                }
            }
            
            columns.add(col);
        }
        
        // Sort columns by X position (left to right)
        columns.sort(Comparator.comparingInt(c -> c.centerX));
        
        System.out.println("    Valid columns found: " + columns.size());
        
        return columns;
    }
    
    /**
     * Detect which digit (0-9) is marked in a column.
     * Rows are labeled 1,2,3,4,5,6,7,8,9,0 from top to bottom.
     * 
     * Sample the CENTER of each bubble - filled bubbles have solid centers,
     * empty bubbles have hollow centers.
     */
    private int detectDigitInColumn(Mat binary, DigitColumn col) {
        int bestRow = -1;
        int maxCenterFill = 0;
        int secondMaxFill = 0;
        
        // Sample the CENTER of each bubble position
        for (int row = 0; row < DIGIT_ROWS; row++) {
            Rect digitRect = col.digitBounds[row];
            if (digitRect == null) continue;
            
            // Sample only the center 50% of the bubble (where fill difference is highest)
            int centerX = digitRect.x() + digitRect.width() / 4;
            int centerY = digitRect.y() + digitRect.height() / 4;
            int sampleW = digitRect.width() / 2;
            int sampleH = digitRect.height() / 2;
            
            // Bounds check
            if (centerX < 0 || centerY < 0) continue;
            if (centerX + sampleW > binary.cols()) sampleW = binary.cols() - centerX;
            if (centerY + sampleH > binary.rows()) sampleH = binary.rows() - centerY;
            if (sampleW <= 0 || sampleH <= 0) continue;
            
            Rect centerRect = new Rect(centerX, centerY, sampleW, sampleH);
            Mat sample = binary.apply(centerRect);
            
            int whitePixels = countNonZero(sample);
            
            if (whitePixels > maxCenterFill) {
                secondMaxFill = maxCenterFill;
                maxCenterFill = whitePixels;
                bestRow = row;
            } else if (whitePixels > secondMaxFill) {
                secondMaxFill = whitePixels;
            }
        }
        
        // Debug: show center fill for first few columns
        if (col.centerX < 200) {
            System.out.println(String.format("      Col X=%d: best row=%d centerFill=%d, 2nd=%d", 
                col.centerX, bestRow, maxCenterFill, secondMaxFill));
        }
        
        // Filled bubble center should have much more white than empty bubble center
        // Empty bubble center is mostly black (hollow)
        if (bestRow >= 0 && maxCenterFill > 10 && maxCenterFill > secondMaxFill * 2) {
            // Convert row index to digit value
            // Row 0 = digit 1, Row 1 = digit 2, ... Row 8 = digit 9, Row 9 = digit 0
            return (bestRow == 9) ? 0 : (bestRow + 1);
        }
        
        return -1;  // No clear detection
    }
    
    /**
     * Represents a column of digit bubbles.
     */
    private static class DigitColumn {
        int centerX;
        Rect bounds;
        Rect[] digitBounds;  // Bounds for each of the 10 digit rows
    }
}

