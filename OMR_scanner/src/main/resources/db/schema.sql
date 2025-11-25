-- ============================================
-- OMR Reader V2 - Database Schema
-- SQLite Database
-- ============================================

-- ============================================
-- ANSWER KEYS
-- ============================================

-- Main answer keys table
CREATE TABLE IF NOT EXISTS answer_keys (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    test_id TEXT NOT NULL UNIQUE,
    total_questions INTEGER NOT NULL DEFAULT 60,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Answer key items (one per question)
CREATE TABLE IF NOT EXISTS answer_key_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    answer_key_id INTEGER NOT NULL,
    question_number INTEGER NOT NULL,
    correct_answer TEXT NOT NULL CHECK(correct_answer IN ('A', 'B', 'C', 'D')),
    FOREIGN KEY (answer_key_id) REFERENCES answer_keys(id) ON DELETE CASCADE,
    UNIQUE(answer_key_id, question_number)
);

-- Index for faster lookups by test_id
CREATE INDEX IF NOT EXISTS idx_answer_keys_test_id ON answer_keys(test_id);

-- ============================================
-- STUDENTS (Optional - for lookup/validation)
-- ============================================

CREATE TABLE IF NOT EXISTS students (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id TEXT NOT NULL UNIQUE,
    name TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Index for student ID lookup
CREATE INDEX IF NOT EXISTS idx_students_student_id ON students(student_id);

-- ============================================
-- TESTS (Optional - for grouping scans)
-- ============================================

CREATE TABLE IF NOT EXISTS tests (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    test_id TEXT NOT NULL UNIQUE,
    name TEXT,
    date DATE,
    answer_key_id INTEGER,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (answer_key_id) REFERENCES answer_keys(id)
);

-- Index for test ID lookup
CREATE INDEX IF NOT EXISTS idx_tests_test_id ON tests(test_id);

-- ============================================
-- SCANS (Main results table)
-- ============================================

CREATE TABLE IF NOT EXISTS scans (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id TEXT,
    test_id TEXT,
    answer_key_id INTEGER,
    image_path TEXT,
    score_correct INTEGER DEFAULT 0,
    score_wrong INTEGER DEFAULT 0,
    score_empty INTEGER DEFAULT 0,
    score_invalid INTEGER DEFAULT 0,
    score_percentage REAL DEFAULT 0.0,
    status TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending', 'success', 'review', 'failed')),
    processing_time_ms INTEGER,
    error_message TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (answer_key_id) REFERENCES answer_keys(id)
);

-- Indexes for scan lookups
CREATE INDEX IF NOT EXISTS idx_scans_student_id ON scans(student_id);
CREATE INDEX IF NOT EXISTS idx_scans_test_id ON scans(test_id);
CREATE INDEX IF NOT EXISTS idx_scans_status ON scans(status);
CREATE INDEX IF NOT EXISTS idx_scans_created_at ON scans(created_at);

-- ============================================
-- SCAN ANSWERS (Individual question results)
-- ============================================

CREATE TABLE IF NOT EXISTS scan_answers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scan_id INTEGER NOT NULL,
    question_number INTEGER NOT NULL,
    detected_answer TEXT CHECK(detected_answer IN ('A', 'B', 'C', 'D') OR detected_answer IS NULL),
    correct_answer TEXT CHECK(correct_answer IN ('A', 'B', 'C', 'D') OR correct_answer IS NULL),
    status TEXT NOT NULL DEFAULT 'valid' CHECK(status IN ('correct', 'wrong', 'empty', 'invalid', 'valid')),
    confidence REAL DEFAULT 1.0,
    is_correct INTEGER DEFAULT 0,
    FOREIGN KEY (scan_id) REFERENCES scans(id) ON DELETE CASCADE,
    UNIQUE(scan_id, question_number)
);

-- Index for faster lookups
CREATE INDEX IF NOT EXISTS idx_scan_answers_scan_id ON scan_answers(scan_id);

-- ============================================
-- BATCH PROCESSING
-- ============================================

CREATE TABLE IF NOT EXISTS batches (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT,
    source_path TEXT,
    answer_key_id INTEGER,
    total_files INTEGER DEFAULT 0,
    processed_files INTEGER DEFAULT 0,
    successful_files INTEGER DEFAULT 0,
    failed_files INTEGER DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending', 'running', 'paused', 'completed', 'cancelled')),
    started_at DATETIME,
    completed_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (answer_key_id) REFERENCES answer_keys(id)
);

-- Link scans to batches
CREATE TABLE IF NOT EXISTS batch_scans (
    batch_id INTEGER NOT NULL,
    scan_id INTEGER NOT NULL,
    file_index INTEGER,
    PRIMARY KEY (batch_id, scan_id),
    FOREIGN KEY (batch_id) REFERENCES batches(id) ON DELETE CASCADE,
    FOREIGN KEY (scan_id) REFERENCES scans(id) ON DELETE CASCADE
);

-- ============================================
-- SETTINGS (Application configuration)
-- ============================================

CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Insert default settings
INSERT OR IGNORE INTO settings (key, value) VALUES 
    ('default_total_questions', '60'),
    ('auto_detect_test_id', 'true'),
    ('confidence_threshold', '0.75'),
    ('app_version', '2.0');

-- ============================================
-- TRIGGERS
-- ============================================

-- Update answer_keys.updated_at on modification
CREATE TRIGGER IF NOT EXISTS update_answer_keys_timestamp 
AFTER UPDATE ON answer_keys
BEGIN
    UPDATE answer_keys SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

-- Update settings.updated_at on modification
CREATE TRIGGER IF NOT EXISTS update_settings_timestamp 
AFTER UPDATE ON settings
BEGIN
    UPDATE settings SET updated_at = CURRENT_TIMESTAMP WHERE key = NEW.key;
END;

