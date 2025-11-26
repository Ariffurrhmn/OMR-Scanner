package org.example.service;

import org.example.model.*;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing Scans in the database.
 * Integrates with IOMRProcessor for image processing.
 */
public class ScanService {
    
    private final DatabaseService db;
    private final AnswerKeyService answerKeyService;
    private IOMRProcessor processor;
    
    public ScanService() {
        this.db = DatabaseService.getInstance();
        this.answerKeyService = new AnswerKeyService();
        // Try to use real processor, fall back to mock if OpenCV not available
        try {
            OMRProcessor realProcessor = new OMRProcessor();
            if (realProcessor.isReady()) {
                this.processor = realProcessor;
                System.out.println("✓ Using OMRProcessor (OpenCV)");
            } else {
                System.err.println("⚠ OpenCV not available, using MockOMRProcessor");
                this.processor = new MockOMRProcessor();
            }
        } catch (Exception e) {
            System.err.println("✗ Failed to initialize OMRProcessor, using MockOMRProcessor: " + e.getMessage());
            e.printStackTrace();
            this.processor = new MockOMRProcessor();
        }
    }

    /**
     * Set the OMR processor implementation.
     * Allows swapping between MockOMRProcessor and real processor.
     */
    public void setProcessor(IOMRProcessor processor) {
        this.processor = processor;
    }

    /**
     * Get the current processor.
     */
    public IOMRProcessor getProcessor() {
        return processor;
    }

    // =========================================
    // Processing Operations
    // =========================================

    /**
     * Process an image file and optionally save the result.
     * 
     * @param imageFile The image file to process
     * @param answerKey Optional answer key for grading (null for auto-detect)
     * @param saveToDb Whether to save the result to database
     * @return The Scan result
     */
    public Scan processImage(File imageFile, AnswerKey answerKey, boolean saveToDb) throws SQLException {
        // Process the image
        OMRResult result = processor.processImage(imageFile);
        
        // Check if processing failed
        if (!result.isSuccessful()) {
            throw new RuntimeException("OMR processing failed: " + 
                (result.getErrorMessage() != null ? result.getErrorMessage() : "Unknown error"));
        }
        
        // Create Scan object
        Scan scan = new Scan();
        scan.setImagePath(imageFile.getAbsolutePath());
        
        // Auto-detect answer key if not provided
        // Note: Test ID is now entered manually, so auto-detect happens in UI
        // This is kept for backward compatibility
        if (answerKey == null && result.isSuccessful() && result.getTestId() != null) {
            Optional<AnswerKey> detected = answerKeyService.findByTestId(result.getTestId());
            answerKey = detected.orElse(null);
        }
        
        // Ensure answer key items are loaded before grading
        if (answerKey != null && (answerKey.getItems() == null || answerKey.getItems().isEmpty())) {
            try {
                Optional<AnswerKey> keyWithItems = answerKeyService.findById(answerKey.getId());
                if (keyWithItems.isPresent()) {
                    answerKey = keyWithItems.get();
                }
            } catch (SQLException e) {
                System.err.println("Failed to load answer key items: " + e.getMessage());
            }
        }
        
        // Populate from result and grade
        scan.populateFromResult(result, answerKey);
        
        // Save to database if requested
        if (saveToDb) {
            scan = save(scan);
        }
        
        return scan;
    }

    /**
     * Process an image file with auto-detect answer key.
     */
    public Scan processImage(File imageFile) throws SQLException {
        return processImage(imageFile, null, true);
    }

    // =========================================
    // CREATE Operations
    // =========================================

    /**
     * Save a scan with its answers.
     */
    public Scan save(Scan scan) throws SQLException {
        String sql = """
            INSERT INTO scans (student_id, test_id, answer_key_id, image_path, 
                score_correct, score_wrong, score_empty, score_invalid, 
                score_percentage, status, processing_time_ms, error_message)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        try {
            db.beginTransaction();
            
            // Insert scan
            long id = db.executeInsert(sql,
                scan.getStudentId(),
                scan.getTestId(),
                scan.getAnswerKeyId(),
                scan.getImagePath(),
                scan.getScoreCorrect(),
                scan.getScoreWrong(),
                scan.getScoreEmpty(),
                scan.getScoreInvalid(),
                scan.getScorePercentage(),
                scan.getStatus().getValue(),
                scan.getProcessingTimeMs(),
                scan.getErrorMessage());
            
            scan.setId(id);
            
            // Insert scan answers
            if (scan.getAnswers() != null && !scan.getAnswers().isEmpty()) {
                insertAnswers(id, scan.getAnswers());
            }
            
            db.commit();
            return scan;
            
        } catch (SQLException e) {
            db.rollback();
            throw e;
        }
    }

    // =========================================
    // READ Operations
    // =========================================

    /**
     * Find a scan by its ID.
     */
    public Optional<Scan> findById(long id) throws SQLException {
        String sql = "SELECT * FROM scans WHERE id = ?";
        
        try (ResultSet rs = db.executeQuery(sql, id)) {
            if (rs.next()) {
                Scan scan = mapResultSetToScan(rs);
                scan.setAnswers(findAnswersByScanId(id));
                return Optional.of(scan);
            }
        }
        return Optional.empty();
    }

    /**
     * Get all scans (without answers for performance).
     */
    public List<Scan> findAll() throws SQLException {
        String sql = "SELECT * FROM scans ORDER BY created_at DESC";
        List<Scan> scans = new ArrayList<>();
        
        try (ResultSet rs = db.executeQuery(sql)) {
            while (rs.next()) {
                scans.add(mapResultSetToScan(rs));
            }
        }
        return scans;
    }

    /**
     * Get recent scans ordered by date.
     * 
     * @param limit Maximum number of scans to return
     * @return List of recent scans
     */
    public List<Scan> findRecent(int limit) throws SQLException {
        String sql = "SELECT * FROM scans ORDER BY created_at DESC LIMIT ?";
        List<Scan> scans = new ArrayList<>();
        
        try (ResultSet rs = db.executeQuery(sql, limit)) {
            while (rs.next()) {
                scans.add(mapResultSetToScan(rs));
            }
        }
        return scans;
    }

    /**
     * Get scans with pagination.
     */
    public List<Scan> findPage(int page, int pageSize) throws SQLException {
        String sql = "SELECT * FROM scans ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<Scan> scans = new ArrayList<>();
        
        int offset = page * pageSize;
        try (ResultSet rs = db.executeQuery(sql, pageSize, offset)) {
            while (rs.next()) {
                scans.add(mapResultSetToScan(rs));
            }
        }
        return scans;
    }

    /**
     * Find scans by student ID.
     */
    public List<Scan> findByStudentId(String studentId) throws SQLException {
        String sql = "SELECT * FROM scans WHERE student_id = ? ORDER BY created_at DESC";
        List<Scan> scans = new ArrayList<>();
        
        try (ResultSet rs = db.executeQuery(sql, studentId)) {
            while (rs.next()) {
                scans.add(mapResultSetToScan(rs));
            }
        }
        return scans;
    }

    /**
     * Find scans by test ID.
     */
    public List<Scan> findByTestId(String testId) throws SQLException {
        String sql = "SELECT * FROM scans WHERE test_id = ? ORDER BY created_at DESC";
        List<Scan> scans = new ArrayList<>();
        
        try (ResultSet rs = db.executeQuery(sql, testId)) {
            while (rs.next()) {
                scans.add(mapResultSetToScan(rs));
            }
        }
        return scans;
    }

    /**
     * Find scans by status.
     */
    public List<Scan> findByStatus(Scan.ScanStatus status) throws SQLException {
        String sql = "SELECT * FROM scans WHERE status = ? ORDER BY created_at DESC";
        List<Scan> scans = new ArrayList<>();
        
        try (ResultSet rs = db.executeQuery(sql, status.getValue())) {
            while (rs.next()) {
                scans.add(mapResultSetToScan(rs));
            }
        }
        return scans;
    }

    /**
     * Search scans with multiple filters.
     */
    public List<Scan> search(String studentId, String testId, Scan.ScanStatus status,
                             LocalDateTime fromDate, LocalDateTime toDate) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM scans WHERE 1=1");
        List<Object> params = new ArrayList<>();
        
        if (studentId != null && !studentId.isEmpty()) {
            sql.append(" AND student_id LIKE ?");
            params.add("%" + studentId + "%");
        }
        if (testId != null && !testId.isEmpty()) {
            sql.append(" AND test_id = ?");
            params.add(testId);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status.getValue());
        }
        if (fromDate != null) {
            sql.append(" AND created_at >= ?");
            params.add(Timestamp.valueOf(fromDate));
        }
        if (toDate != null) {
            sql.append(" AND created_at <= ?");
            params.add(Timestamp.valueOf(toDate));
        }
        
        sql.append(" ORDER BY created_at DESC");
        
        List<Scan> scans = new ArrayList<>();
        try (ResultSet rs = db.executeQuery(sql.toString(), params.toArray())) {
            while (rs.next()) {
                scans.add(mapResultSetToScan(rs));
            }
        }
        return scans;
    }

    /**
     * Get total count of scans.
     */
    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM scans";
        try (ResultSet rs = db.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Get statistics for scans.
     */
    public ScanStatistics getStatistics() throws SQLException {
        return getStatistics(null);
    }

    /**
     * Get statistics for scans filtered by test ID.
     */
    public ScanStatistics getStatistics(String testId) throws SQLException {
        String sql;
        Object[] params;
        
        if (testId != null && !testId.isEmpty()) {
            sql = """
                SELECT COUNT(*) as total, AVG(score_percentage) as avg_score,
                       MAX(score_percentage) as max_score, MIN(score_percentage) as min_score
                FROM scans WHERE test_id = ? AND status != 'failed'
                """;
            params = new Object[]{testId};
        } else {
            sql = """
                SELECT COUNT(*) as total, AVG(score_percentage) as avg_score,
                       MAX(score_percentage) as max_score, MIN(score_percentage) as min_score
                FROM scans WHERE status != 'failed'
                """;
            params = new Object[]{};
        }
        
        try (ResultSet rs = db.executeQuery(sql, params)) {
            if (rs.next()) {
                ScanStatistics stats = new ScanStatistics();
                stats.totalScans = rs.getInt("total");
                stats.averageScore = rs.getDouble("avg_score");
                stats.highestScore = rs.getDouble("max_score");
                stats.lowestScore = rs.getDouble("min_score");
                return stats;
            }
        }
        return new ScanStatistics();
    }

    // =========================================
    // DELETE Operations
    // =========================================

    /**
     * Delete a scan and all its answers.
     */
    public boolean delete(long id) throws SQLException {
        String sql = "DELETE FROM scans WHERE id = ?";
        int affected = db.executeUpdate(sql, id);
        return affected > 0;
    }

    /**
     * Delete multiple scans.
     */
    public int deleteMultiple(List<Long> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) return 0;
        
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        String sql = "DELETE FROM scans WHERE id IN (" + placeholders + ")";
        
        return db.executeUpdate(sql, ids.toArray());
    }

    // =========================================
    // Private Helper Methods
    // =========================================

    /**
     * Find all answers for a scan.
     */
    private List<ScanAnswer> findAnswersByScanId(long scanId) throws SQLException {
        String sql = "SELECT * FROM scan_answers WHERE scan_id = ? ORDER BY question_number";
        List<ScanAnswer> answers = new ArrayList<>();
        
        try (ResultSet rs = db.executeQuery(sql, scanId)) {
            while (rs.next()) {
                ScanAnswer ans = new ScanAnswer();
                ans.setId(rs.getLong("id"));
                ans.setScanId(rs.getLong("scan_id"));
                ans.setQuestionNumber(rs.getInt("question_number"));
                ans.setDetectedAnswer(rs.getString("detected_answer"));
                ans.setCorrectAnswer(rs.getString("correct_answer"));
                ans.setStatus(ScanAnswer.AnswerStatus.fromValue(rs.getString("status")));
                ans.setConfidence(rs.getDouble("confidence"));
                ans.setCorrect(rs.getInt("is_correct") == 1);
                answers.add(ans);
            }
        }
        return answers;
    }

    /**
     * Map ScanAnswer.AnswerStatus to database constraint values.
     * Database expects: 'valid', 'empty', 'multiple', 'uncertain', 'error'
     */
    private String mapStatusToDatabase(ScanAnswer.AnswerStatus status, String detectedAnswer, double confidence) {
        if (status == null) {
            return "valid";
        }
        
        // Check if detected answer is "MULTIPLE" (from OMRResult)
        if ("MULTIPLE".equals(detectedAnswer)) {
            return "multiple";
        }
        
        switch (status) {
            case CORRECT:
            case WRONG:
                // Check if confidence is low - map to 'uncertain'
                if (confidence < 0.7) {
                    return "uncertain";
                }
                // Graded answers with high confidence are considered 'valid' in the database
                return "valid";
            case EMPTY:
                return "empty";
            case INVALID:
                // INVALID status - map to 'error' for database
                return "error";
            case VALID:
                // Check if confidence is low - map to 'uncertain'
                if (confidence < 0.7) {
                    return "uncertain";
                }
                return "valid";
            default:
                return "valid";
        }
    }
    
    /**
     * Insert scan answers.
     */
    private void insertAnswers(long scanId, List<ScanAnswer> answers) throws SQLException {
        String sql = """
            INSERT INTO scan_answers (scan_id, question_number, detected_answer, 
                correct_answer, status, confidence, is_correct)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        
        try (PreparedStatement pstmt = db.getConnection().prepareStatement(sql)) {
            for (ScanAnswer ans : answers) {
                // Ensure status is never null
                ScanAnswer.AnswerStatus status = ans.getStatus();
                if (status == null) {
                    status = ScanAnswer.AnswerStatus.VALID;
                }
                
                // Map ScanAnswer.AnswerStatus to database constraint values
                // Database expects: 'valid', 'empty', 'multiple', 'uncertain', 'error'
                String statusValue = mapStatusToDatabase(status, ans.getDetectedAnswer(), ans.getConfidence());
                
                pstmt.setLong(1, scanId);
                pstmt.setInt(2, ans.getQuestionNumber());
                pstmt.setString(3, ans.getDetectedAnswer());
                pstmt.setString(4, ans.getCorrectAnswer());
                pstmt.setString(5, statusValue);
                pstmt.setDouble(6, ans.getConfidence());
                pstmt.setInt(7, ans.isCorrect() ? 1 : 0);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    /**
     * Map a ResultSet row to a Scan object.
     */
    private Scan mapResultSetToScan(ResultSet rs) throws SQLException {
        Scan scan = new Scan();
        scan.setId(rs.getLong("id"));
        scan.setStudentId(rs.getString("student_id"));
        scan.setTestId(rs.getString("test_id"));
        
        long answerKeyId = rs.getLong("answer_key_id");
        if (!rs.wasNull()) {
            scan.setAnswerKeyId(answerKeyId);
        }
        
        scan.setImagePath(rs.getString("image_path"));
        scan.setScoreCorrect(rs.getInt("score_correct"));
        scan.setScoreWrong(rs.getInt("score_wrong"));
        scan.setScoreEmpty(rs.getInt("score_empty"));
        scan.setScoreInvalid(rs.getInt("score_invalid"));
        scan.setScorePercentage(rs.getDouble("score_percentage"));
        scan.setStatus(Scan.ScanStatus.fromValue(rs.getString("status")));
        scan.setProcessingTimeMs(rs.getLong("processing_time_ms"));
        scan.setErrorMessage(rs.getString("error_message"));
        
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            scan.setCreatedAt(createdAt.toLocalDateTime());
        }
        
        return scan;
    }

    /**
     * Statistics container class.
     */
    public static class ScanStatistics {
        public int totalScans;
        public double averageScore;
        public double highestScore;
        public double lowestScore;
        
        public String getAverageDisplay() {
            return String.format("%.1f%%", averageScore);
        }
        
        public String getHighestDisplay() {
            return String.format("%.1f%%", highestScore);
        }
        
        public String getLowestDisplay() {
            return String.format("%.1f%%", lowestScore);
        }
    }
}

