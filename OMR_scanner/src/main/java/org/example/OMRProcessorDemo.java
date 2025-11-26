package org.example;

import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;

import java.io.File;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_imgcodecs.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * Demo application for testing OMR image processing with OpenCV.
 * 
 * This demo tests:
 * 1. OpenCV/JavaCV library loading
 * 2. Image loading and basic operations
 * 3. Preprocessing (grayscale, threshold, blur)
 * 4. Fiducial detection (L-shaped and rectangular markers)
 * 5. Perspective transformation (deskewing)
 * 6. Full OMR processing pipeline
 * 
 * OMR Sheet Fiducials:
 * - 4 L-shaped markers at page corners → for deskewing
 * - 4 rectangular markers at answer section corners → for region isolation
 * - ID section found as second largest rectangle
 * 
 * Run with: mvn exec:java
 * Or: mvn compile exec:java -Dexec.mainClass="org.example.OMRProcessorDemo"
 */
public class OMRProcessorDemo {

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("OMR Processor Demo - OpenCV Test");
        System.out.println("=".repeat(60));
        
        // Test 1: Check OpenCV is loaded
        testOpenCVLoading();
        
        // Test 2: Basic matrix operations
        testMatrixOperations();
        
        // Test 3: Image loading (if sample exists)
        File sampleImage = testImageLoading();
        
        // Test 4: Image preprocessing pipeline
        testPreprocessingPipeline();
        
        // Test 5: Fiducial detection
        testFiducialDetection();
        
        // Test 6: Full OMR processing (if sample image exists)
        if (sampleImage != null) {
            testFullProcessing(sampleImage);
        }
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("All tests completed!");
        System.out.println("=".repeat(60));
    }

    /**
     * Test 1: Verify OpenCV native library is loaded correctly
     */
    private static void testOpenCVLoading() {
        System.out.println("\n[Test 1] OpenCV Loading...");
        try {
            String version = opencv_core.getBuildInformation().getString();
            System.out.println("  ✓ OpenCV loaded successfully");
            System.out.println("  ✓ Build info available (length: " + version.length() + " chars)");
        } catch (Exception e) {
            System.err.println("  ✗ Failed to load OpenCV: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Test 2: Basic matrix creation and operations
     */
    private static void testMatrixOperations() {
        System.out.println("\n[Test 2] Matrix Operations...");
        try {
            // Create a simple matrix
            Mat mat = new Mat(100, 100, opencv_core.CV_8UC3);
            System.out.println("  ✓ Created Mat: " + mat.rows() + "x" + mat.cols() + 
                             ", channels=" + mat.channels());
            
            // Fill with color
            mat.put(new Scalar(255, 0, 0, 0)); // Blue in BGR
            System.out.println("  ✓ Filled with color");
            
            // Convert to grayscale
            Mat gray = new Mat();
            cvtColor(mat, gray, COLOR_BGR2GRAY);
            System.out.println("  ✓ Converted to grayscale: channels=" + gray.channels());
            
            // Clean up
            mat.release();
            gray.release();
            System.out.println("  ✓ Memory released");
            
        } catch (Exception e) {
            System.err.println("  ✗ Matrix operations failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Test 3: Load an image file
     */
    private static File testImageLoading() {
        System.out.println("\n[Test 3] Image Loading...");
        
        // Check for sample images
        String[] possiblePaths = {
            "samples/test_omr.jpg",
            "samples/test_omr.png",
            "samples/omr_sheet.jpg",
            "samples/omr_sheet.png",
            "test.jpg",
            "test.png"
        };
        
        File imageFile = null;
        for (String path : possiblePaths) {
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                imageFile = f;
                break;
            }
        }
        
        if (imageFile == null) {
            System.out.println("  ⚠ No sample image found");
            System.out.println("  → Place a test OMR sheet image in: samples/test_omr.jpg");
            System.out.println("  → Creating samples directory...");
            new File("samples").mkdirs();
            return null;
        }
        
        try {
            Mat image = imread(imageFile.getAbsolutePath());
            if (image.empty()) {
                System.out.println("  ✗ Failed to load image (empty Mat)");
                return null;
            }
            
            System.out.println("  ✓ Loaded: " + imageFile.getName());
            System.out.println("  ✓ Size: " + image.cols() + "x" + image.rows());
            System.out.println("  ✓ Channels: " + image.channels());
            System.out.println("  ✓ Type: " + image.type());
            
            image.release();
            return imageFile;
            
        } catch (Exception e) {
            System.err.println("  ✗ Image loading failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Test 4: Full preprocessing pipeline
     */
    private static void testPreprocessingPipeline() {
        System.out.println("\n[Test 4] Preprocessing Pipeline...");
        
        try {
            // Create a synthetic test image (simulating an OMR sheet)
            int width = 800;
            int height = 1000;
            
            Mat image = new Mat(height, width, opencv_core.CV_8UC3, new Scalar(255, 255, 255, 0));
            System.out.println("  ✓ Created synthetic image: " + width + "x" + height);
            
            // Draw some fake bubbles (circles)
            for (int row = 0; row < 10; row++) {
                for (int col = 0; col < 4; col++) {
                    int x = 200 + col * 100;
                    int y = 100 + row * 80;
                    
                    // Draw circle (some filled, some not)
                    if ((row + col) % 3 == 0) {
                        // Filled bubble
                        circle(image, new Point(x, y), 20, new Scalar(50, 50, 50, 0), -1, LINE_AA, 0);
                    } else {
                        // Empty bubble
                        circle(image, new Point(x, y), 20, new Scalar(150, 150, 150, 0), 2, LINE_AA, 0);
                    }
                }
            }
            System.out.println("  ✓ Drew synthetic bubbles");
            
            // Step 1: Convert to grayscale
            Mat gray = new Mat();
            cvtColor(image, gray, COLOR_BGR2GRAY);
            System.out.println("  ✓ Step 1: Grayscale conversion");
            
            // Step 2: Gaussian blur
            Mat blurred = new Mat();
            GaussianBlur(gray, blurred, new Size(5, 5), 0);
            System.out.println("  ✓ Step 2: Gaussian blur (5x5)");
            
            // Step 3: Adaptive threshold
            Mat thresh = new Mat();
            adaptiveThreshold(blurred, thresh, 255, ADAPTIVE_THRESH_GAUSSIAN_C, 
                            THRESH_BINARY_INV, 11, 2);
            System.out.println("  ✓ Step 3: Adaptive threshold");
            
            // Step 4: Find contours
            MatVector contours = new MatVector();
            Mat hierarchy = new Mat();
            findContours(thresh.clone(), contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
            System.out.println("  ✓ Step 4: Found " + contours.size() + " contours");
            
            // Step 5: Filter circular contours (bubbles)
            int bubbleCount = 0;
            for (int i = 0; i < contours.size(); i++) {
                Mat contour = contours.get(i);
                double area = contourArea(contour);
                double perimeter = arcLength(contour, true);
                
                if (perimeter > 0) {
                    double circularity = 4 * Math.PI * area / (perimeter * perimeter);
                    if (circularity > 0.7 && area > 500 && area < 5000) {
                        bubbleCount++;
                    }
                }
            }
            System.out.println("  ✓ Step 5: Detected " + bubbleCount + " potential bubbles");
            
            // Save output for inspection
            File outputDir = new File("output");
            outputDir.mkdirs();
            
            imwrite("output/01_original.png", image);
            imwrite("output/02_grayscale.png", gray);
            imwrite("output/03_blurred.png", blurred);
            imwrite("output/04_threshold.png", thresh);
            System.out.println("  ✓ Saved pipeline outputs to: output/");
            
            // Cleanup
            image.release();
            gray.release();
            blurred.release();
            thresh.release();
            hierarchy.release();
            
            System.out.println("  ✓ Pipeline completed successfully!");
            
        } catch (Exception e) {
            System.err.println("  ✗ Pipeline failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Test 5: Fiducial Detection
     */
    private static void testFiducialDetection() {
        System.out.println("\n[Test 5] Fiducial Detection...");
        
        try {
            // Create a synthetic image with fiducials
            int width = 800;
            int height = 1100;
            
            Mat image = new Mat(height, width, opencv_core.CV_8UC1, new Scalar(255)); // White background
            System.out.println("  ✓ Created synthetic image: " + width + "x" + height);
            
            // Draw L-shaped fiducials at corners
            drawLShape(image, 20, 20, 40, 10, true);      // Top-left (opening: bottom-right)
            drawLShape(image, width-60, 20, 40, 10, false); // Top-right (opening: bottom-left)
            drawLShape(image, 20, height-60, 40, 10, false); // Bottom-left (opening: top-right)
            drawLShape(image, width-60, height-60, 40, 10, true); // Bottom-right (opening: top-left)
            System.out.println("  ✓ Drew 4 L-shaped fiducials at corners");
            
            // Draw rectangular fiducials for answer section
            int answerY = 350;
            int answerH = 700;
            rectangle(image, new Rect(50, answerY, 15, 15), new Scalar(0), -1, LINE_8, 0);     // TL
            rectangle(image, new Rect(width-65, answerY, 15, 15), new Scalar(0), -1, LINE_8, 0); // TR
            rectangle(image, new Rect(50, answerY+answerH, 15, 15), new Scalar(0), -1, LINE_8, 0); // BL
            rectangle(image, new Rect(width-65, answerY+answerH, 15, 15), new Scalar(0), -1, LINE_8, 0); // BR
            System.out.println("  ✓ Drew 4 rectangular fiducials for answer section");
            
            // Draw a rectangle for ID section
            rectangle(image, new Rect(50, 100, 400, 200), new Scalar(0), 2, LINE_8, 0);
            System.out.println("  ✓ Drew ID section border");
            
            // Invert for detection (black on white → white on black)
            Mat binary = new Mat();
            threshold(image, binary, 127, 255, THRESH_BINARY_INV);
            
            // Detect fiducials
            FiducialDetector detector = new FiducialDetector();
            
            List<FiducialDetector.LShapedFiducial> lFiducials = detector.detectLShapedFiducials(binary);
            System.out.println("  ✓ Detected " + lFiducials.size() + " L-shaped fiducials");
            
            List<FiducialDetector.RectFiducial> rectFiducials = detector.detectRectFiducials(binary);
            System.out.println("  ✓ Detected " + rectFiducials.size() + " rectangular fiducials");
            
            // Get corners
            FiducialDetector.PageCorners pageCorners = detector.getPageCorners(lFiducials);
            System.out.println("  ✓ Page corners valid: " + pageCorners.isValid());
            
            // Save for inspection
            imwrite("output/05_fiducial_test.png", image);
            imwrite("output/06_fiducial_binary.png", binary);
            System.out.println("  ✓ Saved fiducial test images to output/");
            
            image.release();
            binary.release();
            
        } catch (Exception e) {
            System.err.println("  ✗ Fiducial detection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Draw an L-shaped fiducial marker.
     */
    private static void drawLShape(Mat image, int x, int y, int armLength, int thickness, boolean flip) {
        // Draw vertical arm
        rectangle(image, new Rect(x, y, thickness, armLength), new Scalar(0), -1, LINE_8, 0);
        
        // Draw horizontal arm
        if (flip) {
            rectangle(image, new Rect(x, y, armLength, thickness), new Scalar(0), -1, LINE_8, 0);
        } else {
            rectangle(image, new Rect(x + armLength - thickness, y, thickness, armLength), new Scalar(0), -1, LINE_8, 0);
            rectangle(image, new Rect(x, y, armLength, thickness), new Scalar(0), -1, LINE_8, 0);
        }
    }

    /**
     * Test 6: Full OMR Processing Pipeline
     */
    private static void testFullProcessing(File imageFile) {
        System.out.println("\n[Test 6] Full OMR Processing...");
        
        try {
            OMRSheetProcessor processor = new OMRSheetProcessor();
            processor.setSaveDebugImages(true);
            processor.setDebugOutputDir("output");
            
            OMRSheetProcessor.ProcessResult result = processor.process(imageFile);
            
            System.out.println("\n  Processing Result:");
            System.out.println("  " + "-".repeat(40));
            System.out.println("  Success: " + result.success);
            System.out.println("  Student ID: " + (result.studentId != null ? result.studentId : "N/A"));
            System.out.println("  Test ID: " + (result.testId != null ? result.testId : "N/A"));
            System.out.println("  L-Fiducials found: " + result.lFiducialsFound);
            System.out.println("  Rect-Fiducials found: " + result.rectFiducialsFound);
            System.out.println("  Processing time: " + result.processingTimeMs + "ms");
            
            if (result.errorMessage != null) {
                System.out.println("  Error: " + result.errorMessage);
            }
            
        } catch (Exception e) {
            System.err.println("  ✗ Full processing failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
