package org.example.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an answer key for a test.
 * Contains the correct answers for all questions.
 */
public class AnswerKey {
    
    private Long id;
    private String name;
    private String testId;
    private int totalQuestions;
    private List<AnswerKeyItem> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // =========================================
    // Constructors
    // =========================================

    public AnswerKey() {
        this.totalQuestions = 60;
        this.items = new ArrayList<>();
    }

    public AnswerKey(String name, String testId) {
        this();
        this.name = name;
        this.testId = testId;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTestId() {
        return testId;
    }

    public void setTestId(String testId) {
        this.testId = testId;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public void setTotalQuestions(int totalQuestions) {
        this.totalQuestions = totalQuestions;
    }

    public List<AnswerKeyItem> getItems() {
        return items;
    }

    public void setItems(List<AnswerKeyItem> items) {
        this.items = items;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // =========================================
    // Helper Methods
    // =========================================

    /**
     * Get the correct answer for a specific question (1-indexed).
     */
    public String getAnswer(int questionNumber) {
        if (items == null) return null;
        AnswerKeyItem item = items.stream()
                .filter(i -> i.getQuestionNumber() == questionNumber)
                .findFirst()
                .orElse(null);
        return item != null ? item.getCorrectAnswer() : null;
    }

    /**
     * Set the correct answer for a specific question (1-indexed).
     */
    public void setAnswer(int questionNumber, String answer) {
        if (items == null) {
            items = new ArrayList<>();
        }

        // Find existing item or create new one
        AnswerKeyItem existing = items.stream()
                .filter(item -> item.getQuestionNumber() == questionNumber)
                .findFirst()
                .orElse(null);

        if (existing != null) {
            existing.setCorrectAnswer(answer);
        } else {
            AnswerKeyItem newItem = new AnswerKeyItem();
            newItem.setQuestionNumber(questionNumber);
            newItem.setCorrectAnswer(answer);
            items.add(newItem);
        }
    }

    /**
     * Initialize all answers to null (for creating a new answer key).
     */
    public void initializeEmpty() {
        items = new ArrayList<>();
        for (int i = 1; i <= totalQuestions; i++) {
            AnswerKeyItem item = new AnswerKeyItem();
            item.setQuestionNumber(i);
            item.setCorrectAnswer(null);
            items.add(item);
        }
    }

    /**
     * Parse a string of answers (e.g., "ABCDABCD...") and set all answers.
     * 
     * @param answerString String containing answers in sequence
     * @return Number of answers successfully parsed
     */
    public int parseAnswerString(String answerString) {
        if (answerString == null || answerString.isEmpty()) {
            return 0;
        }

        String cleaned = answerString.toUpperCase().replaceAll("[^ABCD]", "");
        int count = 0;

        for (int i = 0; i < cleaned.length() && i < totalQuestions; i++) {
            String answer = String.valueOf(cleaned.charAt(i));
            setAnswer(i + 1, answer);
            count++;
        }

        return count;
    }

    /**
     * Get all answers as a compact string (e.g., "ABCDABCD...").
     */
    public String toAnswerString() {
        if (items == null || items.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= totalQuestions; i++) {
            String answer = getAnswer(i);
            sb.append(answer != null ? answer : "_");
        }
        return sb.toString();
    }

    /**
     * Check if all questions have answers.
     */
    public boolean isComplete() {
        if (items == null || items.size() != totalQuestions) {
            return false;
        }
        return items.stream()
                .allMatch(item -> item.getCorrectAnswer() != null);
    }

    /**
     * Get the count of questions with answers.
     */
    public int getAnsweredCount() {
        if (items == null) return 0;
        return (int) items.stream()
                .filter(item -> item.getCorrectAnswer() != null)
                .count();
    }

    @Override
    public String toString() {
        return name + " (" + testId + ")";
    }
}

