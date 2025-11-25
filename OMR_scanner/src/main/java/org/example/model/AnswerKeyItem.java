package org.example.model;

/**
 * Represents a single answer within an answer key.
 * Links a question number to its correct answer.
 */
public class AnswerKeyItem {
    
    private Long id;
    private Long answerKeyId;
    private int questionNumber;
    private String correctAnswer;

    // =========================================
    // Constructors
    // =========================================

    public AnswerKeyItem() {
    }

    public AnswerKeyItem(int questionNumber, String correctAnswer) {
        this.questionNumber = questionNumber;
        this.correctAnswer = correctAnswer;
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

    public Long getAnswerKeyId() {
        return answerKeyId;
    }

    public void setAnswerKeyId(Long answerKeyId) {
        this.answerKeyId = answerKeyId;
    }

    public int getQuestionNumber() {
        return questionNumber;
    }

    public void setQuestionNumber(int questionNumber) {
        this.questionNumber = questionNumber;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        // Validate that answer is A, B, C, or D (or null)
        if (correctAnswer != null) {
            correctAnswer = correctAnswer.toUpperCase().trim();
            if (!correctAnswer.matches("[ABCD]")) {
                throw new IllegalArgumentException("Answer must be A, B, C, or D");
            }
        }
        this.correctAnswer = correctAnswer;
    }

    // =========================================
    // Helper Methods
    // =========================================

    /**
     * Check if this answer matches another answer.
     */
    public boolean matches(String answer) {
        if (correctAnswer == null || answer == null) {
            return false;
        }
        return correctAnswer.equalsIgnoreCase(answer.trim());
    }

    @Override
    public String toString() {
        return "Q" + questionNumber + ": " + (correctAnswer != null ? correctAnswer : "-");
    }
}

