package org.example.model;

/**
 * Represents a single answer within a scan result.
 * Links a question number to detected and correct answers.
 */
public class ScanAnswer {
    
    private Long id;
    private Long scanId;
    private int questionNumber;
    private String detectedAnswer;
    private String correctAnswer;
    private AnswerStatus status;
    private double confidence;
    private boolean correct;

    // =========================================
    // Constructors
    // =========================================

    public ScanAnswer() {
        this.status = AnswerStatus.VALID;
        this.confidence = 1.0;
    }

    public ScanAnswer(int questionNumber, String detectedAnswer, String correctAnswer) {
        this();
        this.questionNumber = questionNumber;
        this.detectedAnswer = detectedAnswer;
        this.correctAnswer = correctAnswer;
        this.correct = detectedAnswer != null && 
                      correctAnswer != null && 
                      detectedAnswer.equalsIgnoreCase(correctAnswer);
    }

    // =========================================
    // Getters and Setters
    // =========================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getScanId() {
        return scanId;
    }

    public void setScanId(Long scanId) {
        this.scanId = scanId;
    }

    public int getQuestionNumber() {
        return questionNumber;
    }

    public void setQuestionNumber(int questionNumber) {
        this.questionNumber = questionNumber;
    }

    public String getDetectedAnswer() {
        return detectedAnswer;
    }

    public void setDetectedAnswer(String detectedAnswer) {
        this.detectedAnswer = detectedAnswer;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public AnswerStatus getStatus() {
        return status;
    }

    public void setStatus(AnswerStatus status) {
        this.status = status;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public boolean isCorrect() {
        return correct;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }

    // =========================================
    // Helper Methods
    // =========================================

    /**
     * Get display text for the detected answer.
     * Shows "-" for empty, "?" for invalid.
     */
    public String getDetectedDisplay() {
        if (status == AnswerStatus.EMPTY) return "-";
        if (status == AnswerStatus.INVALID) return "?";
        return detectedAnswer != null ? detectedAnswer : "-";
    }

    /**
     * Get status indicator icon.
     */
    public String getStatusIcon() {
        switch (status) {
            case CORRECT: return "✓";
            case WRONG: return "✗";
            case EMPTY: return "–";
            case INVALID: return "⚠";
            default: return "?";
        }
    }

    /**
     * Get CSS class for status styling.
     */
    public String getStatusStyleClass() {
        switch (status) {
            case CORRECT: return "status-success";
            case WRONG: return "status-error";
            case EMPTY: return "status-muted";
            case INVALID: return "status-warning";
            default: return "";
        }
    }

    @Override
    public String toString() {
        return String.format("Q%d: %s (Key: %s) - %s", 
            questionNumber, 
            getDetectedDisplay(), 
            correctAnswer != null ? correctAnswer : "?",
            status);
    }

    /**
     * Enum representing the status of an answer.
     */
    public enum AnswerStatus {
        /** Correct answer matching the key */
        CORRECT("correct"),
        
        /** Wrong answer not matching the key */
        WRONG("wrong"),
        
        /** No answer marked */
        EMPTY("empty"),
        
        /** Multiple marks or unreadable */
        INVALID("invalid"),
        
        /** Valid detection (before grading) */
        VALID("valid");

        private final String value;

        AnswerStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static AnswerStatus fromValue(String value) {
            for (AnswerStatus status : values()) {
                if (status.value.equalsIgnoreCase(value)) {
                    return status;
                }
            }
            return VALID;
        }
    }
}

