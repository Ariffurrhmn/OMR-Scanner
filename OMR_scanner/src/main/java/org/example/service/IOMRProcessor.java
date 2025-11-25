package org.example.service;

import org.example.model.OMRResult;

import java.io.File;

/**
 * Interface for OMR (Optical Mark Recognition) processing.
 * 
 * This interface abstracts the image processing logic, allowing:
 * - MockOMRProcessor for UI/DB development without real image processing
 * - OMRProcessor for actual image processing when ready
 * 
 * The implementation handles:
 * - Loading and preprocessing images
 * - Detecting fiducial markers
 * - Deskewing and perspective correction
 * - Extracting Student ID region
 * - Extracting Test ID region
 * - Extracting answer blocks
 * - Detecting marked bubbles
 * - Converting marks to answers
 */
public interface IOMRProcessor {
    
    /**
     * Process a single OMR sheet image file.
     * 
     * @param imageFile The image file to process (JPG, PNG, etc.)
     * @return OMRResult containing extracted data or error information
     */
    OMRResult processImage(File imageFile);
    
    /**
     * Process a single OMR sheet from image bytes.
     * 
     * @param imageBytes Raw image data
     * @param fileName Original filename (for logging/metadata)
     * @return OMRResult containing extracted data or error information
     */
    OMRResult processImage(byte[] imageBytes, String fileName);
    
    /**
     * Validate if the given file is a supported image format.
     * 
     * @param file The file to check
     * @return true if the file can be processed
     */
    boolean isValidImageFile(File file);
    
    /**
     * Get the name of this processor implementation.
     * Useful for logging and debugging.
     * 
     * @return Processor name (e.g., "MockOMRProcessor", "OpenCVProcessor")
     */
    String getProcessorName();
    
    /**
     * Check if the processor is ready to process images.
     * For real implementations, this might check if OpenCV is loaded.
     * 
     * @return true if the processor is ready
     */
    boolean isReady();
}

