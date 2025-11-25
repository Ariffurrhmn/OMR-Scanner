package org.example.service;

import org.example.model.AnswerKey;
import org.example.model.AnswerKeyItem;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing Answer Keys in the database.
 * Provides CRUD operations for answer keys and their items.
 */
public class AnswerKeyService {
    
    private final DatabaseService db;
    
    public AnswerKeyService() {
        this.db = DatabaseService.getInstance();
    }

    // =========================================
    // CREATE Operations
    // =========================================

    /**
     * Create a new answer key with its items.
     * 
     * @param answerKey The answer key to create
     * @return The created answer key with ID populated
     */
    public AnswerKey create(AnswerKey answerKey) throws SQLException {
        String sql = "INSERT INTO answer_keys (name, test_id, total_questions) VALUES (?, ?, ?)";
        
        try {
            db.beginTransaction();
            
            // Insert answer key
            long id = db.executeInsert(sql, 
                answerKey.getName(), 
                answerKey.getTestId(),
                answerKey.getTotalQuestions());
            
            answerKey.setId(id);
            
            // Insert answer key items
            if (answerKey.getItems() != null && !answerKey.getItems().isEmpty()) {
                insertItems(id, answerKey.getItems());
            }
            
            db.commit();
            return answerKey;
            
        } catch (SQLException e) {
            db.rollback();
            throw e;
        }
    }

    // =========================================
    // READ Operations
    // =========================================

    /**
     * Find an answer key by its ID.
     */
    public Optional<AnswerKey> findById(long id) throws SQLException {
        String sql = "SELECT * FROM answer_keys WHERE id = ?";
        
        try (ResultSet rs = db.executeQuery(sql, id)) {
            if (rs.next()) {
                AnswerKey key = mapResultSetToAnswerKey(rs);
                key.setItems(findItemsByAnswerKeyId(id));
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }

    /**
     * Find an answer key by its Test ID.
     */
    public Optional<AnswerKey> findByTestId(String testId) throws SQLException {
        String sql = "SELECT * FROM answer_keys WHERE test_id = ?";
        
        try (ResultSet rs = db.executeQuery(sql, testId)) {
            if (rs.next()) {
                AnswerKey key = mapResultSetToAnswerKey(rs);
                key.setItems(findItemsByAnswerKeyId(key.getId()));
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }

    /**
     * Get all answer keys (without items for performance).
     */
    public List<AnswerKey> findAll() throws SQLException {
        String sql = "SELECT * FROM answer_keys ORDER BY name";
        List<AnswerKey> keys = new ArrayList<>();
        
        try (Statement stmt = db.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                keys.add(mapResultSetToAnswerKey(rs));
            }
        }
        return keys;
    }

    /**
     * Search answer keys by name (case-insensitive).
     */
    public List<AnswerKey> search(String searchTerm) throws SQLException {
        String sql = "SELECT * FROM answer_keys WHERE name LIKE ? OR test_id LIKE ? ORDER BY name";
        String term = "%" + searchTerm + "%";
        List<AnswerKey> keys = new ArrayList<>();
        
        try (ResultSet rs = db.executeQuery(sql, term, term)) {
            while (rs.next()) {
                keys.add(mapResultSetToAnswerKey(rs));
            }
        }
        return keys;
    }

    /**
     * Get the total count of answer keys.
     */
    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM answer_keys";
        try (ResultSet rs = db.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    // =========================================
    // UPDATE Operations
    // =========================================

    /**
     * Update an existing answer key and its items.
     */
    public void update(AnswerKey answerKey) throws SQLException {
        if (answerKey.getId() == null) {
            throw new IllegalArgumentException("Answer key ID cannot be null for update");
        }

        String sql = "UPDATE answer_keys SET name = ?, test_id = ?, total_questions = ? WHERE id = ?";
        
        try {
            db.beginTransaction();
            
            // Update answer key
            db.executeUpdate(sql,
                answerKey.getName(),
                answerKey.getTestId(),
                answerKey.getTotalQuestions(),
                answerKey.getId());
            
            // Delete existing items and re-insert
            deleteItemsByAnswerKeyId(answerKey.getId());
            if (answerKey.getItems() != null && !answerKey.getItems().isEmpty()) {
                insertItems(answerKey.getId(), answerKey.getItems());
            }
            
            db.commit();
            
        } catch (SQLException e) {
            db.rollback();
            throw e;
        }
    }

    /**
     * Update a single answer in an answer key.
     */
    public void updateAnswer(long answerKeyId, int questionNumber, String answer) throws SQLException {
        // First check if item exists
        String checkSql = "SELECT id FROM answer_key_items WHERE answer_key_id = ? AND question_number = ?";
        try (ResultSet rs = db.executeQuery(checkSql, answerKeyId, questionNumber)) {
            if (rs.next()) {
                // Update existing
                String updateSql = "UPDATE answer_key_items SET correct_answer = ? WHERE answer_key_id = ? AND question_number = ?";
                db.executeUpdate(updateSql, answer, answerKeyId, questionNumber);
            } else {
                // Insert new
                String insertSql = "INSERT INTO answer_key_items (answer_key_id, question_number, correct_answer) VALUES (?, ?, ?)";
                db.executeUpdate(insertSql, answerKeyId, questionNumber, answer);
            }
        }
    }

    // =========================================
    // DELETE Operations
    // =========================================

    /**
     * Delete an answer key and all its items.
     */
    public boolean delete(long id) throws SQLException {
        String sql = "DELETE FROM answer_keys WHERE id = ?";
        int affected = db.executeUpdate(sql, id);
        return affected > 0;
    }

    /**
     * Delete all answer keys.
     */
    public int deleteAll() throws SQLException {
        String sql = "DELETE FROM answer_keys";
        return db.executeUpdate(sql);
    }

    // =========================================
    // Private Helper Methods
    // =========================================

    /**
     * Find all items for an answer key.
     */
    private List<AnswerKeyItem> findItemsByAnswerKeyId(long answerKeyId) throws SQLException {
        String sql = "SELECT * FROM answer_key_items WHERE answer_key_id = ? ORDER BY question_number";
        List<AnswerKeyItem> items = new ArrayList<>();
        
        try (ResultSet rs = db.executeQuery(sql, answerKeyId)) {
            while (rs.next()) {
                AnswerKeyItem item = new AnswerKeyItem();
                item.setId(rs.getLong("id"));
                item.setAnswerKeyId(rs.getLong("answer_key_id"));
                item.setQuestionNumber(rs.getInt("question_number"));
                item.setCorrectAnswer(rs.getString("correct_answer"));
                items.add(item);
            }
        }
        return items;
    }

    /**
     * Insert answer key items.
     */
    private void insertItems(long answerKeyId, List<AnswerKeyItem> items) throws SQLException {
        String sql = "INSERT INTO answer_key_items (answer_key_id, question_number, correct_answer) VALUES (?, ?, ?)";
        
        try (PreparedStatement pstmt = db.getConnection().prepareStatement(sql)) {
            for (AnswerKeyItem item : items) {
                if (item.getCorrectAnswer() != null) {
                    pstmt.setLong(1, answerKeyId);
                    pstmt.setInt(2, item.getQuestionNumber());
                    pstmt.setString(3, item.getCorrectAnswer());
                    pstmt.addBatch();
                }
            }
            pstmt.executeBatch();
        }
    }

    /**
     * Delete all items for an answer key.
     */
    private void deleteItemsByAnswerKeyId(long answerKeyId) throws SQLException {
        String sql = "DELETE FROM answer_key_items WHERE answer_key_id = ?";
        db.executeUpdate(sql, answerKeyId);
    }

    /**
     * Map a ResultSet row to an AnswerKey object.
     */
    private AnswerKey mapResultSetToAnswerKey(ResultSet rs) throws SQLException {
        AnswerKey key = new AnswerKey();
        key.setId(rs.getLong("id"));
        key.setName(rs.getString("name"));
        key.setTestId(rs.getString("test_id"));
        key.setTotalQuestions(rs.getInt("total_questions"));
        
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            key.setCreatedAt(createdAt.toLocalDateTime());
        }
        
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            key.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        
        return key;
    }
}

