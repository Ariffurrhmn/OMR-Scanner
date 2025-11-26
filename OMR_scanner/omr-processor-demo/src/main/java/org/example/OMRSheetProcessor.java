package org.example;

import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.global.opencv_core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * Complete OMR Sheet Processor that uses fiducial markers for accurate detection.
 * 
 * Processing Pipeline:
 * 1. Load and preprocess image
 * 2. Detect L-shaped fiducials at page corners
 * 3. Deskew page using L-shaped fiducials
 * 4. Detect rectangular fiducials for answer section
 * 5. Extract answer section region
 * 6. Find ID section (second largest rectangle)
 * 7. Detect and analyze bubbles in each region
 * 8. Extract answers
 */
public class OMRSheetProcessor {

    private ImagePreprocessor preprocessor;
    private FiducialDetector fiducialDetector;
    private BubbleDetector bubbleDetector;
    private PerspectiveCorrector perspectiveCorrector;
    private GridBasedAnswerExtractor gridExtractor;
    private HybridAnswerExtractor hybridExtractor;
    private StructuredAnswerExtractor structuredExtractor;
    private MorphologicalAnswerExtractor morphExtractor;
    private RowBasedAnswerExtractor rowExtractor;
    private IDExtractor idExtractor;
    
    // Configuration
    private int targetWidth = 1000;  // Target width after deskewing
    private int targetHeight = 1400; // Target height after deskewing
    private boolean saveDebugImages = true;
    private String debugOutputDir = "output";

    public OMRSheetProcessor() {
        this.preprocessor = new ImagePreprocessor();
        this.fiducialDetector = new FiducialDetector();
        this.bubbleDetector = new BubbleDetector();
        this.perspectiveCorrector = new PerspectiveCorrector();
        this.gridExtractor = new GridBasedAnswerExtractor();
        this.hybridExtractor = new HybridAnswerExtractor();
        this.structuredExtractor = new StructuredAnswerExtractor();
        this.morphExtractor = new MorphologicalAnswerExtractor();
        this.rowExtractor = new RowBasedAnswerExtractor();
        this.idExtractor = new IDExtractor();
    }

    /**
     * Process result containing all extracted data.
     */
    public static class ProcessResult {
        public boolean success;
        public String errorMessage;
        
        // Extracted IDs
        public String studentId;  // 10 digits
        public String testId;     // 4 digits
        
        // Detected answers (60 questions, values: A/B/C/D/null/MULTIPLE)
        public String[] answers = new String[60];
        
        // Confidence scores for each answer
        public double[] confidences = new double[60];
        
        // Processing stats
        public long processingTimeMs;
        public int lFiducialsFound;
        public int rectFiducialsFound;
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("ProcessResult {\n");
            sb.append("  success: ").append(success).append("\n");
            sb.append("  studentId: ").append(studentId).append("\n");
            sb.append("  testId: ").append(testId).append("\n");
            sb.append("  answers: ");
            for (int i = 0; i < 10; i++) {
                sb.append(answers[i] != null ? answers[i] : "-");
            }
            sb.append("... (showing first 10)\n");
            sb.append("  processingTime: ").append(processingTimeMs).append("ms\n");
            sb.append("}");
            return sb.toString();
        }
    }

    /**
     * Process an OMR sheet image.
     */
    public ProcessResult process(File imageFile) {
        ProcessResult result = new ProcessResult();
        long startTime = System.currentTimeMillis();
        
        try {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("Processing: " + imageFile.getName());
            System.out.println("=".repeat(60));
            
            // Step 1: Load image
            System.out.println("\n[Step 1] Loading image...");
            Mat original = imread(imageFile.getAbsolutePath());
            if (original.empty()) {
                result.success = false;
                result.errorMessage = "Failed to load image";
                return result;
            }
            System.out.println("  ✓ Loaded: " + original.cols() + "x" + original.rows());
            
            // Step 2: Preprocess
            System.out.println("\n[Step 2] Preprocessing...");
            Mat gray = preprocessor.toGrayscale(original);
            Mat blurred = preprocessor.applyGaussianBlur(gray, 5);
            // Use larger block size and C value for scanned images
            Mat binary = preprocessor.applyAdaptiveThreshold(blurred, 15, 4);
            System.out.println("  ✓ Grayscale, blur, threshold applied");
            
            if (saveDebugImages) {
                imwrite(debugOutputDir + "/01_original.png", original);
                imwrite(debugOutputDir + "/02_binary.png", binary);
            }
            
            // Step 3: Detect L-shaped fiducials
            System.out.println("\n[Step 3] Detecting L-shaped fiducials...");
            List<FiducialDetector.LShapedFiducial> lFiducials = 
                fiducialDetector.detectLShapedFiducials(binary);
            result.lFiducialsFound = lFiducials.size();
            System.out.println("  ✓ Found " + lFiducials.size() + " L-shaped fiducials");
            for (var fid : lFiducials) {
                System.out.println("    - " + fid);
            }
            
            // Step 4: Deskew using L-fiducials
            System.out.println("\n[Step 4] Deskewing page...");
            Mat deskewed;
            FiducialDetector.PageCorners pageCorners = fiducialDetector.getPageCorners(lFiducials);
            
            if (pageCorners.isValid()) {
                pageCorners.print();
                deskewed = applyPerspectiveTransform(original, pageCorners);
                System.out.println("  ✓ Deskewed using L-fiducials");
            } else {
                System.out.println("  ⚠ Not all L-fiducials found, using original image");
                deskewed = original.clone();
            }
            
            if (saveDebugImages) {
                imwrite(debugOutputDir + "/03_deskewed.png", deskewed);
            }
            
            // Step 5: Reprocess deskewed image
            System.out.println("\n[Step 5] Reprocessing deskewed image...");
            Mat deskewedGray = preprocessor.toGrayscale(deskewed);
            Mat deskewedBinary = preprocessor.applyAdaptiveThreshold(
                preprocessor.applyGaussianBlur(deskewedGray, 5), 11, 2);
            
            // Step 6: Detect rectangular fiducials for answer section
            System.out.println("\n[Step 6] Detecting answer section fiducials...");
            List<FiducialDetector.RectFiducial> rectFiducials = 
                fiducialDetector.detectRectFiducials(deskewedBinary);
            result.rectFiducialsFound = rectFiducials.size();
            System.out.println("  ✓ Found " + rectFiducials.size() + " rectangular fiducials");
            
            // Step 7: Extract and deskew answer section using rectangular fiducials
            System.out.println("\n[Step 7] Extracting answer section...");
            
            Mat answerSection = null;
            
            // Try to use rectangular fiducials to deskew the answer section
            // Works with 2, 3, or 4 fiducials
            FiducialDetector.AnswerSectionCorners answerCorners = 
                fiducialDetector.getAnswerSectionCorners(rectFiducials, deskewed.cols(), deskewed.rows());
            
            if (answerCorners != null && answerCorners.isValid()) {
                answerCorners.print();
                
                // Check if fiducials are spread out enough for perspective transform
                double bboxWidth = Math.max(
                    Math.abs(answerCorners.topRight.x() - answerCorners.topLeft.x()),
                    Math.abs(answerCorners.bottomRight.x() - answerCorners.bottomLeft.x())
                );
                double bboxHeight = Math.max(
                    Math.abs(answerCorners.bottomLeft.y() - answerCorners.topLeft.y()),
                    Math.abs(answerCorners.bottomRight.y() - answerCorners.topRight.y())
                );
                
                // If fiducials are too close together, use border detection instead
                // (fiducials are small markers, not full corners)
                if (bboxWidth < 400 || bboxHeight < 300) {
                    System.out.println("  ⚠ Fiducials too close (likely small markers), using border detection...");
                    answerSection = null; // Will fall through to border detection
                } else {
                    // Fiducials are spread out, use perspective transform
                    answerSection = deskewAnswerSection(deskewed, answerCorners);
                    System.out.println("  ✓ Deskewed answer section using fiducials: " + 
                        answerSection.cols() + "x" + answerSection.rows());
                }
            }
            
            // Fallback to border detection if fiducial method didn't work
            if (answerSection == null) {
                System.out.println("  Trying border detection...");
                answerSection = findAnswerSectionByBorder(deskewed, deskewedBinary);
                
                if (answerSection == null) {
                    System.out.println("  ⚠ Border detection failed, using heuristics...");
                    answerSection = extractAnswerSectionByHeuristics(deskewed);
                }
            }
            
            if (saveDebugImages && answerSection != null) {
                imwrite(debugOutputDir + "/04_answer_section.png", answerSection);
            }
            
            // Step 8: Find ID section
            System.out.println("\n[Step 8] Finding ID section...");
            Rect idSectionRect = fiducialDetector.findIdSectionByLocation(deskewedBinary);
            Mat idSection = null;
            if (idSectionRect != null) {
                idSection = extractRegion(deskewed, idSectionRect);
                System.out.println("  ✓ ID section found: " + 
                    idSectionRect.width() + "x" + idSectionRect.height());
                
                if (saveDebugImages) {
                    imwrite(debugOutputDir + "/05_id_section.png", idSection);
                }
            } else {
                System.out.println("  ⚠ ID section not found");
            }
            
            // Step 9: Extract Student ID and Test ID
            System.out.println("\n[Step 9] Extracting IDs...");
            // Pass the full deskewed image - IDExtractor finds ID section internally
            extractIds(deskewed, result);
            System.out.println("  Student ID: " + (result.studentId != null ? result.studentId : "N/A"));
            System.out.println("  Test ID: " + (result.testId != null ? result.testId : "N/A"));
            
            // Step 10: Extract answers using row-based approach
            System.out.println("\n[Step 10] Extracting answers (row-based)...");
            if (answerSection != null) {
                // Configure row-based extractor
                rowExtractor.setSaveDebugImages(saveDebugImages);
                rowExtractor.setDebugOutputDir(debugOutputDir);
                
                // Preprocess answer section - use inverted binary
                // so filled bubbles (dark on original) become white
                Mat ansGray = preprocessor.toGrayscale(answerSection);
                Mat ansBlurred = preprocessor.applyGaussianBlur(ansGray, 3);
                Mat ansBinary = new Mat();
                org.bytedeco.opencv.global.opencv_imgproc.threshold(
                    ansBlurred, ansBinary, 0, 255,
                    org.bytedeco.opencv.global.opencv_imgproc.THRESH_BINARY_INV |
                    org.bytedeco.opencv.global.opencv_imgproc.THRESH_OTSU);
                
                if (saveDebugImages) {
                    org.bytedeco.opencv.global.opencv_imgcodecs.imwrite(
                        debugOutputDir + "/07_answer_binary.png", ansBinary);
                }
                
                // Extract using row-based method (detect rows first, then bubbles)
                RowBasedAnswerExtractor.Result rowResult = rowExtractor.extract(ansBinary);
                
                // Copy results
                result.answers = rowResult.answers;
                result.confidences = rowResult.confidences;
                
                ansGray.release();
                ansBlurred.release();
                ansBinary.release();
            }
            
            // Print first 20 answers as sample
            System.out.println("  First 20 answers:");
            for (int i = 0; i < 20; i++) {
                System.out.print("  Q" + (i+1) + ":" + (result.answers[i] != null ? result.answers[i] : "-") + " ");
                if ((i + 1) % 5 == 0) System.out.println();
            }
            
            // Cleanup
            original.release();
            gray.release();
            blurred.release();
            binary.release();
            deskewed.release();
            deskewedGray.release();
            deskewedBinary.release();
            if (answerSection != null) answerSection.release();
            if (idSection != null) idSection.release();
            
            result.success = true;
            result.processingTimeMs = System.currentTimeMillis() - startTime;
            
            System.out.println("\n" + "=".repeat(60));
            System.out.println("Processing complete in " + result.processingTimeMs + "ms");
            System.out.println("=".repeat(60));
            
        } catch (Exception e) {
            result.success = false;
            result.errorMessage = e.getMessage();
            e.printStackTrace();
        }
        
        return result;
    }

    /**
     * Apply perspective transform using page corners.
     */
    private Mat applyPerspectiveTransform(Mat image, FiducialDetector.PageCorners corners) {
        // Source points from detected corners
        Mat srcPoints = new Mat(4, 1, CV_32FC2);
        srcPoints.ptr(0).putFloat(corners.topLeft.x()).putFloat(4, corners.topLeft.y());
        srcPoints.ptr(1).putFloat(corners.topRight.x()).putFloat(4, corners.topRight.y());
        srcPoints.ptr(2).putFloat(corners.bottomRight.x()).putFloat(4, corners.bottomRight.y());
        srcPoints.ptr(3).putFloat(corners.bottomLeft.x()).putFloat(4, corners.bottomLeft.y());
        
        // Destination points (rectangle)
        Mat dstPoints = new Mat(4, 1, CV_32FC2);
        dstPoints.ptr(0).putFloat(0).putFloat(4, 0);
        dstPoints.ptr(1).putFloat(targetWidth).putFloat(4, 0);
        dstPoints.ptr(2).putFloat(targetWidth).putFloat(4, targetHeight);
        dstPoints.ptr(3).putFloat(0).putFloat(4, targetHeight);
        
        // Get transformation matrix
        Mat transformMatrix = getPerspectiveTransform(srcPoints, dstPoints);
        
        // Apply transformation
        Mat warped = new Mat();
        warpPerspective(image, warped, transformMatrix, new Size(targetWidth, targetHeight));
        
        // Cleanup
        srcPoints.release();
        dstPoints.release();
        transformMatrix.release();
        
        return warped;
    }

    /**
     * Extract a rectangular region from an image.
     */
    private Mat extractRegion(Mat image, Rect rect) {
        // Ensure rect is within image bounds
        int x = Math.max(0, rect.x());
        int y = Math.max(0, rect.y());
        int w = Math.min(rect.width(), image.cols() - x);
        int h = Math.min(rect.height(), image.rows() - y);
        
        if (w <= 0 || h <= 0) {
            return null;
        }
        
        return image.apply(new Rect(x, y, w, h)).clone();
    }

    /**
     * Find the answer section by detecting its border rectangle.
     */
    private Mat findAnswerSectionByBorder(Mat image, Mat binaryImage) {
        // Find contours
        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        findContours(binaryImage.clone(), contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
        
        int imgArea = image.cols() * image.rows();
        Rect bestRect = null;
        double bestScore = 0;
        
        for (int i = 0; i < contours.size(); i++) {
            Mat contour = contours.get(i);
            double area = contourArea(contour);
            
            // Answer section should be 30-60% of image area
            double areaRatio = area / imgArea;
            if (areaRatio < 0.20 || areaRatio > 0.70) continue;
            
            // Approximate to polygon
            Mat approx = new Mat();
            double peri = arcLength(contour, true);
            approxPolyDP(contour, approx, 0.02 * peri, true);
            
            // Should be roughly rectangular (4 vertices)
            if (approx.rows() >= 4 && approx.rows() <= 8) {
                Rect bbox = boundingRect(contour);
                
                // Answer section should be in the lower part of the page
                double yRatio = (double) bbox.y() / image.rows();
                if (yRatio < 0.20 || yRatio > 0.50) {
                    approx.release();
                    continue;
                }
                
                // Score based on area and position
                double score = areaRatio * (1.0 - Math.abs(yRatio - 0.35));
                if (score > bestScore) {
                    bestScore = score;
                    bestRect = bbox;
                }
            }
            approx.release();
        }
        
        hierarchy.release();
        
        if (bestRect != null) {
            // Add small padding inside the border
            int padding = 5;
            int x = bestRect.x() + padding;
            int y = bestRect.y() + padding;
            int w = bestRect.width() - 2 * padding;
            int h = bestRect.height() - 2 * padding;
            
            System.out.println("  ✓ Found answer section by border: " + w + "x" + h + " at (" + x + "," + y + ")");
            return extractRegion(image, new Rect(x, y, w, h));
        }
        
        return null;
    }

    /**
     * Deskew the answer section using perspective correction based on rectangular fiducials.
     */
    private Mat deskewAnswerSection(Mat image, FiducialDetector.AnswerSectionCorners corners) {
        // Source points (detected corners) - use Mat format
        Mat srcPoints = new Mat(4, 1, CV_32FC2);
        srcPoints.ptr(0).putFloat(corners.topLeft.x()).putFloat(4, corners.topLeft.y());
        srcPoints.ptr(1).putFloat(corners.topRight.x()).putFloat(4, corners.topRight.y());
        srcPoints.ptr(2).putFloat(corners.bottomRight.x()).putFloat(4, corners.bottomRight.y());
        srcPoints.ptr(3).putFloat(corners.bottomLeft.x()).putFloat(4, corners.bottomLeft.y());
        
        // Calculate destination rectangle dimensions
        // Use average width and height from detected corners
        double width1 = 0, width2 = 0;
        int widthCount = 0;
        if (corners.topLeft != null && corners.topRight != null) {
            width1 = Math.abs(corners.topRight.x() - corners.topLeft.x());
            widthCount++;
        }
        if (corners.bottomLeft != null && corners.bottomRight != null) {
            width2 = Math.abs(corners.bottomRight.x() - corners.bottomLeft.x());
            widthCount++;
        }
        double avgWidth = widthCount > 0 ? (width1 + width2) / widthCount : 
            Math.max(Math.abs(corners.topRight.x() - corners.topLeft.x()),
                    Math.abs(corners.bottomRight.x() - corners.bottomLeft.x()));
        
        double height1 = 0, height2 = 0;
        int heightCount = 0;
        if (corners.topLeft != null && corners.bottomLeft != null) {
            height1 = Math.abs(corners.bottomLeft.y() - corners.topLeft.y());
            heightCount++;
        }
        if (corners.topRight != null && corners.bottomRight != null) {
            height2 = Math.abs(corners.bottomRight.y() - corners.topRight.y());
            heightCount++;
        }
        double avgHeight = heightCount > 0 ? (height1 + height2) / heightCount :
            Math.max(Math.abs(corners.bottomLeft.y() - corners.topLeft.y()),
                    Math.abs(corners.bottomRight.y() - corners.topRight.y()));
        
        // The rectangular fiducials are small markers, not the full corners
        // Expand outward to get the full answer section
        // Calculate bounding box of all corners
        double minCornerX = Double.MAX_VALUE, maxCornerX = 0;
        double minCornerY = Double.MAX_VALUE, maxCornerY = 0;
        
        if (corners.topLeft != null) {
            minCornerX = Math.min(minCornerX, corners.topLeft.x());
            maxCornerX = Math.max(maxCornerX, corners.topLeft.x());
            minCornerY = Math.min(minCornerY, corners.topLeft.y());
            maxCornerY = Math.max(maxCornerY, corners.topLeft.y());
        }
        if (corners.topRight != null) {
            minCornerX = Math.min(minCornerX, corners.topRight.x());
            maxCornerX = Math.max(maxCornerX, corners.topRight.x());
            minCornerY = Math.min(minCornerY, corners.topRight.y());
            maxCornerY = Math.max(maxCornerY, corners.topRight.y());
        }
        if (corners.bottomLeft != null) {
            minCornerX = Math.min(minCornerX, corners.bottomLeft.x());
            maxCornerX = Math.max(maxCornerX, corners.bottomLeft.x());
            minCornerY = Math.min(minCornerY, corners.bottomLeft.y());
            maxCornerY = Math.max(maxCornerY, corners.bottomLeft.y());
        }
        if (corners.bottomRight != null) {
            minCornerX = Math.min(minCornerX, corners.bottomRight.x());
            maxCornerX = Math.max(maxCornerX, corners.bottomRight.x());
            minCornerY = Math.min(minCornerY, corners.bottomRight.y());
            maxCornerY = Math.max(maxCornerY, corners.bottomRight.y());
        }
        
        // Expand by a margin (fiducials are inside the border)
        double expandMargin = 30; // Pixels to expand outward
        double bboxWidth = maxCornerX - minCornerX;
        double bboxHeight = maxCornerY - minCornerY;
        
        // Use the larger of calculated avgWidth or bounding box + margin
        double finalWidth = Math.max(avgWidth, bboxWidth + 2 * expandMargin);
        double finalHeight = Math.max(avgHeight, bboxHeight + 2 * expandMargin);
        
        // Ensure minimum reasonable dimensions
        if (finalWidth < 800) finalWidth = 900; // Typical answer section width
        if (finalHeight < 600) finalHeight = 800; // Typical answer section height
        
        int dstWidth = (int) finalWidth;
        int dstHeight = (int) finalHeight;
        
        System.out.println("    Bounding box: " + String.format("%.0f", bboxWidth) + "x" + 
            String.format("%.0f", bboxHeight) + ", Final: " + dstWidth + "x" + dstHeight);
        
        // Destination points (perfect rectangle)
        Mat dstPoints = new Mat(4, 1, CV_32FC2);
        dstPoints.ptr(0).putFloat(0).putFloat(4, 0);                    // Top-left
        dstPoints.ptr(1).putFloat(dstWidth).putFloat(4, 0);             // Top-right
        dstPoints.ptr(2).putFloat(dstWidth).putFloat(4, dstHeight);     // Bottom-right
        dstPoints.ptr(3).putFloat(0).putFloat(4, dstHeight);           // Bottom-left
        
        // Get perspective transform matrix
        Mat transformMatrix = getPerspectiveTransform(srcPoints, dstPoints);
        
        // Apply perspective transformation
        Mat deskewed = new Mat();
        warpPerspective(image, deskewed, transformMatrix, new Size(dstWidth, dstHeight),
            INTER_LINEAR, BORDER_CONSTANT, new Scalar(255, 255, 255, 0));
        
        // Cleanup
        transformMatrix.release();
        srcPoints.release();
        dstPoints.release();
        
        return deskewed;
    }

    /**
     * Extract answer section using heuristics when border detection fails.
     */
    private Mat extractAnswerSectionByHeuristics(Mat image) {
        int width = image.cols();
        int height = image.rows();
        
        // Based on the OMR template with borders and fiducials
        int x = (int) (width * 0.02);
        int y = (int) (height * 0.315);  // After ID section  
        int w = (int) (width * 0.96);
        int h = (int) (height * 0.635);
        
        System.out.println("  Heuristic bounds: x=" + x + ", y=" + y + ", w=" + w + ", h=" + h);
        
        return extractRegion(image, new Rect(x, y, w, h));
    }

    /**
     * Extract Student ID and Test ID from the ID section.
     * Uses the dedicated IDExtractor which detects digit columns and identifies filled bubbles.
     */
    private void extractIds(Mat idSection, ProcessResult result) {
        // Configure ID extractor
        idExtractor.setSaveDebugImages(saveDebugImages);
        idExtractor.setDebugOutputDir(debugOutputDir);
        
        // Extract IDs
        IDExtractor.Result idResult = idExtractor.extract(idSection);
        
        if (idResult.success) {
            result.studentId = idResult.studentId;
            result.testId = idResult.testId;
        }
    }

    /**
     * Extract answers from the answer section.
     * The answer section has 4 columns (Q1-15, Q16-30, Q31-45, Q46-60)
     * Each question has 4 choices (A, B, C, D)
     */
    private void extractAnswers(Mat answerSection, ProcessResult result) {
        // Preprocess with parameters tuned for real scans
        Mat gray = preprocessor.toGrayscale(answerSection);
        Mat blurred = preprocessor.applyGaussianBlur(gray, 3);
        
        // Try Otsu threshold for better results on scanned documents
        Mat binary = new Mat();
        org.bytedeco.opencv.global.opencv_imgproc.threshold(
            blurred, binary, 0, 255, 
            org.bytedeco.opencv.global.opencv_imgproc.THRESH_BINARY_INV | 
            org.bytedeco.opencv.global.opencv_imgproc.THRESH_OTSU);
        
        if (saveDebugImages) {
            org.bytedeco.opencv.global.opencv_imgcodecs.imwrite(
                debugOutputDir + "/07_answer_binary.png", binary);
        }
        
        // Detect bubbles
        List<BubbleDetector.Bubble> bubbles = bubbleDetector.detectBubbles(binary);
        bubbleDetector.analyzeBubbles(bubbles, binary);
        
        System.out.println("  Found " + bubbles.size() + " potential bubbles");
        
        blurred.release();
        
        // Group into rows
        List<List<BubbleDetector.Bubble>> rows = bubbleDetector.groupBubblesIntoRows(bubbles, 15);
        System.out.println("  Grouped into " + rows.size() + " rows");
        
        // The answer section should have 15 rows (one per question in each column)
        // And each row should have 16 bubbles (4 columns × 4 choices)
        
        // For each row, extract 4 questions (one from each column)
        for (int rowIdx = 0; rowIdx < Math.min(rows.size(), 15); rowIdx++) {
            List<BubbleDetector.Bubble> row = rows.get(rowIdx);
            
            // Sort by X position
            row.sort((a, b) -> Integer.compare(a.x, b.x));
            
            // Each row has 4 columns, each with 4 choices (A, B, C, D)
            // Questions: rowIdx+1, rowIdx+16, rowIdx+31, rowIdx+46
            int[] questionNumbers = {rowIdx + 1, rowIdx + 16, rowIdx + 31, rowIdx + 46};
            
            for (int col = 0; col < 4 && col * 4 < row.size(); col++) {
                int qNum = questionNumbers[col];
                if (qNum > 60) continue;
                
                // Get the 4 bubbles for this question
                int startIdx = col * 4;
                String[] choices = {"A", "B", "C", "D"};
                List<String> marked = new ArrayList<>();
                
                for (int choice = 0; choice < 4 && startIdx + choice < row.size(); choice++) {
                    BubbleDetector.Bubble bubble = row.get(startIdx + choice);
                    if (bubble.isMarked) {
                        marked.add(choices[choice]);
                        result.confidences[qNum - 1] = bubble.fillRatio;
                    }
                }
                
                // Set answer
                if (marked.isEmpty()) {
                    result.answers[qNum - 1] = null; // No answer
                } else if (marked.size() == 1) {
                    result.answers[qNum - 1] = marked.get(0);
                } else {
                    result.answers[qNum - 1] = "MULTIPLE"; // Multiple marks
                }
            }
        }
        
        gray.release();
        binary.release();
    }

    // Setters
    public void setTargetWidth(int width) { this.targetWidth = width; }
    public void setTargetHeight(int height) { this.targetHeight = height; }
    public void setSaveDebugImages(boolean save) { this.saveDebugImages = save; }
    public void setDebugOutputDir(String dir) { this.debugOutputDir = dir; }
}

