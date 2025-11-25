package org.example.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a scan result stored in the database.
 * Contains extracted data, grading results, and metadata.
 */
public class Scan {
    
    private Long id;
    private String studentId;
    private String testId;
    private Long answerKeyId;
    private String imagePath;
    
    // Score breakdown
    private int scoreCorrect;
    private int scoreWrong;
    private int scoreEmpty;
    private int scoreInvalid;
    private double scorePercentage;
    
    // Status
    private ScanStatus status;
    private long processingTimeMs;
    private String errorMessage;
    
    // Related data
    private List<ScanAnswer> answers;
    private AnswerKey answerKey;
    
    // Timestamps
    private LocalDateTime createdAt;

    // =========================================
    // Constructors
    // =========================================

    public Scan() {
        this.status = ScanStatus.PENDING;
        this.answers = new ArrayList<>();
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

    public Long getAnswerKeyId() {
        return answerKeyId;
    }

    public void setAnswerKeyId(Long answerKeyId) {
        this.answerKeyId = answerKeyId;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public int getScoreCorrect() {
        return scoreCorrect;
    }

    public void setScoreCorrect(int scoreCorrect) {
        this.scoreCorrect = scoreCorrect;
    }

    public int getScoreWrong() {
        return scoreWrong;
    }

    public void setScoreWrong(int scoreWrong) {
        this.scoreWrong = scoreWrong;
    }

    public int getScoreEmpty() {
        return scoreEmpty;
    }

    public void setScoreEmpty(int scoreEmpty) {
        this.scoreEmpty = scoreEmpty;
    }

    public int getScoreInvalid() {
        return scoreInvalid;
    }

    public void setScoreInvalid(int scoreInvalid) {
        this.scoreInvalid = scoreInvalid;
    }

    public double getScorePercentage() {
        return scorePercentage;
    }

    public void setScorePercentage(double scorePercentage) {
        this.scorePercentage = scorePercentage;
    }

    public ScanStatus getStatus() {
        return status;
    }

    public void setStatus(ScanStatus status) {
        this.status = status;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<ScanAnswer> getAnswers() {
        return answers;
    }

    public void setAnswers(List<ScanAnswer> answers) {
        this.answers = answers;
    }

    public AnswerKey getAnswerKey() {
        return answerKey;
    }

    public void setAnswerKey(AnswerKey answerKey) {
        this.answerKey = answerKey;
        if (answerKey != null) {
            this.answerKeyId = answerKey.getId();
        }
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // =========================================
    // Helper Methods
    // =========================================

    /**
     * Get total questions count.
     */
    public int getTotalQuestions() {
        return scoreCorrect + scoreWrong + scoreEmpty + scoreInvalid;
    }

    /**
     * Calculate and update the score percentage.
     */
    public void calculateScore() {
        int total = getTotalQuestions();
        if (total > 0) {
            this.scorePercentage = (double) scoreCorrect / total * 100.0;
        } else {
            this.scorePercentage = 0.0;
        }
    }

    /**
     * Get the score as a formatted string (e.g., "51/60 (85.0%)").
     */
    public String getScoreDisplay() {
        return String.format("%d/%d (%.1f%%)", 
            scoreCorrect, getTotalQuestions(), scorePercentage);
    }

    /**
     * Check if Student ID is valid (10 digits).
     */
    public boolean isStudentIdValid() {
        return studentId != null && studentId.matches("\\d{10}");
    }

    /**
     * Check if Test ID is valid (4 digits).
     */
    public boolean isTestIdValid() {
        return testId != null && testId.matches("\\d{4}");
    }

    /**
     * Check if scan needs review (has issues).
     */
    public boolean needsReview() {
        return status == ScanStatus.REVIEW || 
               !isStudentIdValid() || 
               !isTestIdValid() ||
               scoreInvalid > 0;
    }

    /**
     * Populate from an OMRResult and grade against an answer key.
     */
    public void populateFromResult(OMRResult result, AnswerKey key) {
        this.studentId = result.getStudentId();
        this.testId = result.getTestId();
        this.processingTimeMs = result.getProcessingTimeMs();
        
        if (!result.isSuccessful()) {
            this.status = ScanStatus.FAILED;
            this.errorMessage = result.getErrorMessage();
            return;
        }

        // Initialize counts
        this.scoreCorrect = 0;
        this.scoreWrong = 0;
        this.scoreEmpty = 0;
        this.scoreInvalid = 0;
        this.answers = new ArrayList<>();

        // Grade each answer
        List<String> detectedAnswers = result.getAnswers();
        List<OMRResult.AnswerStatus> statuses = result.getAnswerStatuses();

        for (int i = 0; i < detectedAnswers.size(); i++) {
            int qNum = i + 1;
            String detected = detectedAnswers.get(i);
            OMRResult.AnswerStatus ansStatus = statuses != null && i < statuses.size() 
                ? statuses.get(i) : OMRResult.AnswerStatus.VALID;
            
            String correct = key != null ? key.getAnswer(qNum) : null;
            
            ScanAnswer scanAns = new ScanAnswer();
            scanAns.setQuestionNumber(qNum);
            scanAns.setDetectedAnswer(detected);
            scanAns.setCorrectAnswer(correct);
            
            // Determine status and correctness
            switch (ansStatus) {
                case EMPTY:
                    scanAns.setStatus(ScanAnswer.AnswerStatus.EMPTY);
                    scanAns.setCorrect(false);
                    scoreEmpty++;
                    break;
                case MULTIPLE:
                case ERROR:
                    scanAns.setStatus(ScanAnswer.AnswerStatus.INVALID);
                    scanAns.setCorrect(false);
                    scoreInvalid++;
                    break;
                case UNCERTAIN:
                case VALID:
                default:
                    if (detected == null) {
                        scanAns.setStatus(ScanAnswer.AnswerStatus.EMPTY);
                        scanAns.setCorrect(false);
                        scoreEmpty++;
                    } else if (correct != null && detected.equalsIgnoreCase(correct)) {
                        scanAns.setStatus(ScanAnswer.AnswerStatus.CORRECT);
                        scanAns.setCorrect(true);
                        scoreCorrect++;
                    } else {
                        scanAns.setStatus(ScanAnswer.AnswerStatus.WRONG);
                        scanAns.setCorrect(false);
                        scoreWrong++;
                    }
                    break;
            }
            
            this.answers.add(scanAns);
        }

        // Calculate percentage
        calculateScore();
        
        // Set overall status
        if (scoreInvalid > 0 || !isStudentIdValid() || !isTestIdValid()) {
            this.status = ScanStatus.REVIEW;
        } else {
            this.status = ScanStatus.SUCCESS;
        }
        
        this.answerKey = key;
        if (key != null) {
            this.answerKeyId = key.getId();
        }
    }

    /**
     * Enum representing scan status.
     */
    public enum ScanStatus {
        PENDING("pending"),
        SUCCESS("success"),
        REVIEW("review"),
        FAILED("failed");

        private final String value;

        ScanStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static ScanStatus fromValue(String value) {
            for (ScanStatus status : values()) {
                if (status.value.equalsIgnoreCase(value)) {
                    return status;
                }
            }
            return PENDING;
        }
    }
}

