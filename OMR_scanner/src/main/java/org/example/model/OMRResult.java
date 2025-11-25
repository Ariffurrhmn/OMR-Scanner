package org.example.model;

import java.util.List;
import java.util.Map;

/**
 * Represents the complete result of processing an OMR sheet.
 * This is the output from the IOMRProcessor.
 */
public class OMRResult {
    
    /** Extracted Student ID (10 digits) */
    private String studentId;
    
    /** Extracted Test ID (4 digits) */
    private String testId;
    
    /** List of detected answers (A, B, C, D, or null for empty/invalid) */
    private List<String> answers;
    
    /** Confidence scores for each answer (0.0 to 1.0) */
    private List<Double> confidenceScores;
    
    /** Processing status for each question */
    private List<AnswerStatus> answerStatuses;
    
    /** Overall processing success */
    private boolean successful;
    
    /** Error message if processing failed */
    private String errorMessage;
    
    /** Processing time in milliseconds */
    private long processingTimeMs;
    
    /** Additional metadata from processing */
    private Map<String, Object> metadata;

    // =========================================
    // Constructors
    // =========================================

    public OMRResult() {
    }

    public OMRResult(boolean successful, String errorMessage) {
        this.successful = successful;
        this.errorMessage = errorMessage;
    }

    // =========================================
    // Getters and Setters
    // =========================================

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getTestId() {
        return testId;
    }

    public void setTestId(String testId) {
        this.testId = testId;
    }

    public List<String> getAnswers() {
        return answers;
    }

    public void setAnswers(List<String> answers) {
        this.answers = answers;
    }

    public List<Double> getConfidenceScores() {
        return confidenceScores;
    }

    public void setConfidenceScores(List<Double> confidenceScores) {
        this.confidenceScores = confidenceScores;
    }

    public List<AnswerStatus> getAnswerStatuses() {
        return answerStatuses;
    }

    public void setAnswerStatuses(List<AnswerStatus> answerStatuses) {
        this.answerStatuses = answerStatuses;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    // =========================================
    // Helper Methods
    // =========================================

    /**
     * Get the answer for a specific question (1-indexed)
     */
    public String getAnswer(int questionNumber) {
        if (answers == null || questionNumber < 1 || questionNumber > answers.size()) {
            return null;
        }
        return answers.get(questionNumber - 1);
    }

    /**
     * Get the total number of questions
     */
    public int getTotalQuestions() {
        return answers != null ? answers.size() : 0;
    }

    /**
     * Check if Student ID is valid (10 digits)
     */
    public boolean isStudentIdValid() {
        return studentId != null && studentId.matches("\\d{10}");
    }

    /**
     * Check if Test ID is valid (4 digits)
     */
    public boolean isTestIdValid() {
        return testId != null && testId.matches("\\d{4}");
    }

    /**
     * Enum representing the status of each answer detection
     */
    public enum AnswerStatus {
        /** Answer detected successfully with high confidence */
        VALID,
        
        /** No bubble marked for this question */
        EMPTY,
        
        /** Multiple bubbles marked for this question */
        MULTIPLE,
        
        /** Low confidence in detection */
        UNCERTAIN,
        
        /** Could not process this question */
        ERROR
    }
}

