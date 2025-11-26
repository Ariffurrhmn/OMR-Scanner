package org.example;

import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_imgproc.CLAHE;

import static org.bytedeco.opencv.global.opencv_imgproc.*;
import static org.bytedeco.opencv.global.opencv_core.*;

/**
 * Utility class for OMR image preprocessing operations.
 */
public class ImagePreprocessor {

    /**
     * Convert image to grayscale.
     */
    public Mat toGrayscale(Mat image) {
        if (image.channels() == 1) {
            return image.clone();
        }
        Mat gray = new Mat();
        cvtColor(image, gray, COLOR_BGR2GRAY);
        return gray;
    }

    /**
     * Apply Gaussian blur to reduce noise.
     */
    public Mat applyGaussianBlur(Mat image, int kernelSize) {
        Mat blurred = new Mat();
        GaussianBlur(image, blurred, new Size(kernelSize, kernelSize), 0);
        return blurred;
    }

    /**
     * Apply adaptive threshold for binarization.
     * Good for handling uneven lighting.
     */
    public Mat applyAdaptiveThreshold(Mat grayImage, int blockSize, double c) {
        Mat thresh = new Mat();
        adaptiveThreshold(grayImage, thresh, 255, 
            ADAPTIVE_THRESH_GAUSSIAN_C, THRESH_BINARY_INV, blockSize, c);
        return thresh;
    }

    /**
     * Apply Otsu's threshold for binarization.
     * Good for images with bimodal histogram.
     */
    public Mat applyOtsuThreshold(Mat grayImage) {
        Mat thresh = new Mat();
        threshold(grayImage, thresh, 0, 255, THRESH_BINARY_INV | THRESH_OTSU);
        return thresh;
    }

    /**
     * Apply morphological operations to clean up the image.
     */
    public Mat applyMorphology(Mat binaryImage, int operation, int kernelSize) {
        Mat kernel = getStructuringElement(MORPH_ELLIPSE, 
            new Size(kernelSize, kernelSize));
        Mat result = new Mat();
        morphologyEx(binaryImage, result, operation, kernel);
        kernel.release();
        return result;
    }

    /**
     * Apply dilation to expand white regions.
     */
    public Mat dilate(Mat binaryImage, int kernelSize) {
        return applyMorphology(binaryImage, MORPH_DILATE, kernelSize);
    }

    /**
     * Apply erosion to shrink white regions.
     */
    public Mat erode(Mat binaryImage, int kernelSize) {
        return applyMorphology(binaryImage, MORPH_ERODE, kernelSize);
    }

    /**
     * Apply opening (erosion followed by dilation) to remove noise.
     */
    public Mat open(Mat binaryImage, int kernelSize) {
        return applyMorphology(binaryImage, MORPH_OPEN, kernelSize);
    }

    /**
     * Apply closing (dilation followed by erosion) to fill holes.
     */
    public Mat close(Mat binaryImage, int kernelSize) {
        return applyMorphology(binaryImage, MORPH_CLOSE, kernelSize);
    }

    /**
     * Apply Canny edge detection.
     */
    public Mat applyCanny(Mat grayImage, double threshold1, double threshold2) {
        Mat edges = new Mat();
        Canny(grayImage, edges, threshold1, threshold2);
        return edges;
    }

    /**
     * Resize image while maintaining aspect ratio.
     */
    public Mat resizeImage(Mat image, int maxDimension) {
        int width = image.cols();
        int height = image.rows();
        
        if (width <= maxDimension && height <= maxDimension) {
            return image.clone();
        }
        
        double scale;
        if (width > height) {
            scale = (double) maxDimension / width;
        } else {
            scale = (double) maxDimension / height;
        }
        
        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);
        
        Mat resized = new Mat();
        org.bytedeco.opencv.global.opencv_imgproc.resize(image, resized, new Size(newWidth, newHeight));
        return resized;
    }

    /**
     * Normalize image contrast using histogram equalization.
     * Makes the image more uniform and easier to process.
     */
    public Mat normalizeContrast(Mat grayImage) {
        Mat normalized = new Mat();
        equalizeHist(grayImage, normalized);
        return normalized;
    }

    /**
     * Apply CLAHE (Contrast Limited Adaptive Histogram Equalization).
     * Better than regular histogram equalization for uneven lighting.
     */
    public Mat applyCLAHE(Mat grayImage) {
        CLAHE clahe = createCLAHE();
        clahe.setClipLimit(2.0);
        clahe.setTilesGridSize(new Size(8, 8));
        
        Mat normalized = new Mat();
        clahe.apply(grayImage, normalized);
        clahe.close();
        return normalized;
    }

    /**
     * Normalize image intensity (brightness normalization).
     */
    public Mat normalizeIntensity(Mat grayImage) {
        Mat normalized = new Mat();
        grayImage.convertTo(normalized, CV_8UC1, 1.0, 0);
        
        // Calculate mean and std
        Mat mean = new Mat();
        Mat stddev = new Mat();
        meanStdDev(normalized, mean, stddev, new Mat());
        
        // Get values from Mat (first element)
        double meanVal = mean.ptr(0).getDouble(0);
        double stddevVal = stddev.ptr(0).getDouble(0);
        
        // Normalize to mean=128, std=64
        double targetMean = 128.0;
        double targetStd = 64.0;
        if (stddevVal > 0) {
            double scale = targetStd / stddevVal;
            double shift = targetMean - (meanVal * scale);
            normalized.convertTo(normalized, CV_8UC1, scale, shift);
        }
        
        mean.release();
        stddev.release();
        return normalized;
    }

    /**
     * Full preprocessing pipeline for OMR sheets with normalization.
     * 
     * @param image Input color image
     * @return Preprocessed binary image ready for bubble detection
     */
    public Mat preprocess(Mat image) {
        // Step 1: Convert to grayscale
        Mat gray = toGrayscale(image);
        
        // Step 2: Normalize contrast (flatten the image)
        Mat normalized = applyCLAHE(gray);
        gray.release();
        
        // Step 3: Apply Gaussian blur
        Mat blurred = applyGaussianBlur(normalized, 5);
        normalized.release();
        
        // Step 4: Apply adaptive threshold
        Mat thresh = applyAdaptiveThreshold(blurred, 11, 2);
        blurred.release();
        
        // Step 5: Clean up with morphological operations
        Mat cleaned = close(thresh, 3);
        thresh.release();
        
        return cleaned;
    }
}

