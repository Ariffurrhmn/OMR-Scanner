package org.example.service;

import org.example.model.OMRResult;
import org.example.model.OMRResult.AnswerStatus;

import java.io.File;
import java.util.*;

/**
 * Mock implementation of IOMRProcessor for development and testing.
 * 
 * This processor generates fake but realistic-looking data without
 * requiring actual image processing capabilities. Use this while:
 * - Developing the UI
 * - Testing database operations
 * - Running without OpenCV/JavaCV
 * 
 * When the real OMRProcessor is ready, simply swap the implementation.
 */
public class MockOMRProcessor implements IOMRProcessor {

    private static final String PROCESSOR_NAME = "MockOMRProcessor";
    private static final int TOTAL_QUESTIONS = 60;
    private static final String[] VALID_ANSWERS = {"A", "B", "C", "D"};
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        ".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".tif"
    );

    private final Random random = new Random();
    private final boolean simulateErrors;
    private final int simulatedDelayMs;

    /**
     * Create a MockOMRProcessor with default settings.
     */
    public MockOMRProcessor() {
        this(false, 500);
    }

    /**
     * Create a MockOMRProcessor with custom settings.
     * 
     * @param simulateErrors If true, occasionally return error results
     * @param simulatedDelayMs Artificial delay to simulate processing time
     */
    public MockOMRProcessor(boolean simulateErrors, int simulatedDelayMs) {
        this.simulateErrors = simulateErrors;
        this.simulatedDelayMs = simulatedDelayMs;
    }

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

        return generateMockResult(imageFile.getName());
    }

    @Override
    public OMRResult processImage(byte[] imageBytes, String fileName) {
        if (imageBytes == null || imageBytes.length == 0) {
            return createErrorResult("Image data is empty");
        }

        return generateMockResult(fileName);
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
        return true; // Mock is always ready
    }

    // =========================================
    // Private Helper Methods
    // =========================================

    /**
     * Generate a mock OMR result with realistic-looking data.
     */
    private OMRResult generateMockResult(String fileName) {
        long startTime = System.currentTimeMillis();

        // Simulate processing delay
        if (simulatedDelayMs > 0) {
            try {
                Thread.sleep(simulatedDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Occasionally simulate errors if enabled
        if (simulateErrors && random.nextInt(10) == 0) {
            return createErrorResult("Simulated processing error for: " + fileName);
        }

        OMRResult result = new OMRResult();
        result.setSuccessful(true);
        
        // Generate Student ID (10 digits)
        result.setStudentId(generateStudentId());
        
        // Generate Test ID (4 digits)
        result.setTestId(generateTestId());
        
        // Generate answers
        List<String> answers = new ArrayList<>();
        List<Double> confidenceScores = new ArrayList<>();
        List<AnswerStatus> statuses = new ArrayList<>();
        
        for (int i = 0; i < TOTAL_QUESTIONS; i++) {
            // 85% chance of valid answer
            // 8% chance of empty
            // 4% chance of multiple
            // 3% chance of uncertain
            int roll = random.nextInt(100);
            
            if (roll < 85) {
                // Valid answer
                answers.add(VALID_ANSWERS[random.nextInt(4)]);
                confidenceScores.add(0.85 + random.nextDouble() * 0.15); // 0.85-1.0
                statuses.add(AnswerStatus.VALID);
            } else if (roll < 93) {
                // Empty
                answers.add(null);
                confidenceScores.add(0.95);
                statuses.add(AnswerStatus.EMPTY);
            } else if (roll < 97) {
                // Multiple
                answers.add(null);
                confidenceScores.add(0.90);
                statuses.add(AnswerStatus.MULTIPLE);
            } else {
                // Uncertain
                answers.add(VALID_ANSWERS[random.nextInt(4)]);
                confidenceScores.add(0.50 + random.nextDouble() * 0.30); // 0.50-0.80
                statuses.add(AnswerStatus.UNCERTAIN);
            }
        }
        
        result.setAnswers(answers);
        result.setConfidenceScores(confidenceScores);
        result.setAnswerStatuses(statuses);
        
        // Add metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileName", fileName);
        metadata.put("processor", PROCESSOR_NAME);
        metadata.put("timestamp", System.currentTimeMillis());
        metadata.put("mock", true);
        result.setMetadata(metadata);
        
        // Calculate processing time
        result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
        
        return result;
    }

    /**
     * Generate a realistic-looking Student ID.
     * Format: YYYYNNNNN (year + 6-digit sequence)
     */
    private String generateStudentId() {
        int year = 2020 + random.nextInt(5); // 2020-2024
        int sequence = random.nextInt(999999);
        return String.format("%d%06d", year, sequence);
    }

    /**
     * Generate a realistic-looking Test ID.
     * Format: NNNN (4-digit number)
     */
    private String generateTestId() {
        return String.format("%04d", 1000 + random.nextInt(9000));
    }

    /**
     * Create an error result.
     */
    private OMRResult createErrorResult(String message) {
        OMRResult result = new OMRResult(false, message);
        result.setProcessingTimeMs(0);
        return result;
    }
}

