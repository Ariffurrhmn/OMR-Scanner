package org.example.service;

import org.example.model.OMRResult;
import org.example.model.OMRResult.AnswerStatus;
import org.example.OMRSheetProcessor;

import java.io.File;
import java.util.*;

/**
 * Real OMR Processor implementation using OpenCV.
 * Extracts answers from OMR sheets but does NOT extract Student ID or Test ID.
 * These must be entered manually by the user.
 */
public class OMRProcessor implements IOMRProcessor {

    private static final String PROCESSOR_NAME = "OMRProcessor";
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        ".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".tif"
    );

    @Override
    public OMRResult processImage(File imageFile) {
        if (imageFile == null) {
            return createErrorResult("Image file is null");
        }
        
        if (!imageFile.exists()) {
            return createErrorResult("File does not exist: " + imageFile.getName());
        }
        
        if (!isValidImageFile(imageFile)) {
            return createErrorResult("Unsupported file format: " + imageFile.getName());
        }

        long startTime = System.currentTimeMillis();
        
        try {
            // Disable debug image saving for production
            OMRSheetProcessor processor = new OMRSheetProcessor();
            processor.setSaveDebugImages(false);
            
            // Use the OMR processor to extract answers
            OMRSheetProcessor.ProcessResult result = processor.process(imageFile);
            
            if (!result.success) {
                return createErrorResult(result.errorMessage != null ? result.errorMessage : "Processing failed");
            }
            
            // Convert to OMRResult
            OMRResult omrResult = new OMRResult();
            omrResult.setSuccessful(true);
            
            // Skip ID extraction - return null for manual input
            omrResult.setStudentId(null);
            omrResult.setTestId(null);
            
            // Convert answers array to list
            List<String> answers = new ArrayList<>();
            List<Double> confidences = new ArrayList<>();
            List<AnswerStatus> statuses = new ArrayList<>();
            
            for (int i = 0; i < result.answers.length; i++) {
                String answer = result.answers[i];
                double confidence = (i < result.confidences.length) ? result.confidences[i] : 0.0;
                
                answers.add(answer);
                confidences.add(confidence);
                
                // Map answer to status
                if (answer == null || answer.isEmpty()) {
                    statuses.add(AnswerStatus.EMPTY);
                } else if ("MULTIPLE".equals(answer)) {
                    statuses.add(AnswerStatus.MULTIPLE);
                } else if (confidence < 0.7) {
                    statuses.add(AnswerStatus.UNCERTAIN);
                } else {
                    statuses.add(AnswerStatus.VALID);
                }
            }
            
            omrResult.setAnswers(answers);
            omrResult.setConfidenceScores(confidences);
            omrResult.setAnswerStatuses(statuses);
            
            // Add metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("fileName", imageFile.getName());
            metadata.put("processor", PROCESSOR_NAME);
            metadata.put("timestamp", System.currentTimeMillis());
            metadata.put("lFiducialsFound", result.lFiducialsFound);
            metadata.put("rectFiducialsFound", result.rectFiducialsFound);
            omrResult.setMetadata(metadata);
            
            omrResult.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            
            return omrResult;
            
        } catch (Exception e) {
            e.printStackTrace();  // Print full stack trace for debugging
            return createErrorResult("Processing error: " + e.getMessage() + 
                (e.getCause() != null ? " (Caused by: " + e.getCause().getMessage() + ")" : ""));
        }
    }

    @Override
    public OMRResult processImage(byte[] imageBytes, String fileName) {
        // For now, save to temp file and process
        // TODO: Process directly from bytes if needed
        try {
            java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("omr_", ".jpg");
            java.nio.file.Files.write(tempFile, imageBytes);
            File file = tempFile.toFile();
            OMRResult result = processImage(file);
            file.delete(); // Clean up temp file
            return result;
        } catch (Exception e) {
            return createErrorResult("Error processing image bytes: " + e.getMessage());
        }
    }

    @Override
    public boolean isValidImageFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }
        
        String name = file.getName().toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    @Override
    public String getProcessorName() {
        return PROCESSOR_NAME;
    }

    @Override
    public boolean isReady() {
        try {
            // Check if OpenCV is available by trying to create a Mat
            org.bytedeco.opencv.opencv_core.Mat testMat = new org.bytedeco.opencv.opencv_core.Mat();
            testMat.release();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private OMRResult createErrorResult(String message) {
        OMRResult result = new OMRResult(false, message);
        result.setProcessingTimeMs(0);
        return result;
    }
}
