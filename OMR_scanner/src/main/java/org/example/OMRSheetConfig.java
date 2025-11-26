package org.example;

/**
 * Configuration constants for the OMR sheet layout.
 * 
 * Based on the template structure:
 * 
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ L (TL)                                                 L (TR)  │
 * │                                                                 │
 * │  ┌──────────────────────────────────────────────────────────┐  │
 * │  │  STUDENT ID (10 cols)        TEST ID (4 cols)            │  │
 * │  │  ┌─┬─┬─┬─┬─┬─┬─┬─┬─┬─┐      ┌─┬─┬─┬─┐                   │  │
 * │  │  │1│1│1│1│1│1│1│1│1│1│      │1│1│1│1│    Info...        │  │
 * │  │  │2│2│2│2│2│2│2│2│2│2│      │2│2│2│2│                   │  │
 * │  │  │ ...                      │ ...                        │  │
 * │  │  │0│0│0│0│0│0│0│0│0│0│      │0│0│0│0│                   │  │
 * │  │  └─┴─┴─┴─┴─┴─┴─┴─┴─┴─┘      └─┴─┴─┴─┘                   │  │
 * │  └──────────────────────────────────────────────────────────┘  │
 * │                                                                 │
 * │  ■ (TL)              ANSWER SECTION                    ■ (TR)  │
 * │  ┌──────────────────────────────────────────────────────────┐  │
 * │  │  1  (A)(B)(C)(D)  16 (A)(B)(C)(D)  31 (A)(B)(C)(D)  46  │  │
 * │  │  2  (A)(B)(C)(D)  17 (A)(B)(C)(D)  32 (A)(B)(C)(D)  47  │  │
 * │  │  ...              ...              ...              ...  │  │
 * │  │  15 (A)(B)(C)(D)  30 (A)(B)(C)(D)  45 (A)(B)(C)(D)  60  │  │
 * │  └──────────────────────────────────────────────────────────┘  │
 * │  ■ (BL)                                                ■ (BR)  │
 * │                                                                 │
 * │ L (BL)                                                 L (BR)  │
 * └─────────────────────────────────────────────────────────────────┘
 * 
 * Fiducials:
 * - L-shaped (L): 4 at outer page corners - for deskewing
 * - Rectangular (■): 4 at answer section corners - for answer region extraction
 */
public class OMRSheetConfig {

    // =========================================
    // Sheet Dimensions (A4 at 300 DPI)
    // =========================================
    public static final int SHEET_WIDTH = 2480;   // A4 width at 300 DPI
    public static final int SHEET_HEIGHT = 3508;  // A4 height at 300 DPI
    
    // Target dimensions after deskewing (scaled down for processing)
    public static final int TARGET_WIDTH = 1000;
    public static final int TARGET_HEIGHT = 1400;
    
    // =========================================
    // Student ID Configuration
    // =========================================
    public static final int STUDENT_ID_DIGITS = 10;
    public static final int STUDENT_ID_ROWS = 10;   // Digits 0-9 (or 1-9,0)
    
    // =========================================
    // Test ID Configuration
    // =========================================
    public static final int TEST_ID_DIGITS = 4;
    public static final int TEST_ID_ROWS = 10;      // Digits 0-9 (or 1-9,0)
    
    // =========================================
    // Answer Section Configuration
    // =========================================
    public static final int TOTAL_QUESTIONS = 60;
    public static final int QUESTIONS_PER_COLUMN = 15;
    public static final int ANSWER_COLUMNS = 4;     // Q1-15, Q16-30, Q31-45, Q46-60
    public static final int CHOICES_PER_QUESTION = 4; // A, B, C, D
    public static final String[] CHOICE_LABELS = {"A", "B", "C", "D"};
    
    // =========================================
    // Fiducial Marker Configuration
    // =========================================
    
    // L-shaped fiducials (page corners)
    public static final double L_FIDUCIAL_MIN_AREA = 500;
    public static final double L_FIDUCIAL_MAX_AREA = 15000;
    public static final double L_FIDUCIAL_MIN_SOLIDITY = 0.3;
    public static final double L_FIDUCIAL_MAX_SOLIDITY = 0.75;
    
    // Rectangular fiducials (answer section corners)
    public static final double RECT_FIDUCIAL_MIN_AREA = 100;
    public static final double RECT_FIDUCIAL_MAX_AREA = 3000;
    public static final double RECT_FIDUCIAL_MIN_ASPECT = 0.5;
    public static final double RECT_FIDUCIAL_MAX_ASPECT = 2.0;
    
    // =========================================
    // Bubble Detection Configuration
    // =========================================
    public static final double BUBBLE_MIN_AREA = 200;
    public static final double BUBBLE_MAX_AREA = 3000;
    public static final double BUBBLE_MIN_CIRCULARITY = 0.6;
    public static final double BUBBLE_FILL_THRESHOLD = 0.45; // 45% filled = marked
    
    // =========================================
    // Region Locations (as % of sheet dimensions)
    // These are approximate, actual extraction uses fiducials
    // =========================================
    
    // ID Section (contains both Student ID and Test ID)
    public static final double ID_SECTION_X_START = 0.03;
    public static final double ID_SECTION_Y_START = 0.08;
    public static final double ID_SECTION_WIDTH = 0.65;
    public static final double ID_SECTION_HEIGHT = 0.18;
    
    // Student ID within ID section
    public static final double STUDENT_ID_X_START = 0.05;
    public static final double STUDENT_ID_WIDTH = 0.45;
    
    // Test ID within ID section  
    public static final double TEST_ID_X_START = 0.55;
    public static final double TEST_ID_WIDTH = 0.20;
    
    // Answer Section
    public static final double ANSWER_SECTION_X_START = 0.03;
    public static final double ANSWER_SECTION_Y_START = 0.32;
    public static final double ANSWER_SECTION_WIDTH = 0.94;
    public static final double ANSWER_SECTION_HEIGHT = 0.62;
    
    // =========================================
    // Helper Methods
    // =========================================
    
    /**
     * Get question numbers for a specific column (0-indexed).
     * Column 0: Q1-15, Column 1: Q16-30, Column 2: Q31-45, Column 3: Q46-60
     */
    public static int[] getQuestionsInColumn(int column) {
        int[] questions = new int[QUESTIONS_PER_COLUMN];
        int start = column * QUESTIONS_PER_COLUMN + 1;
        for (int i = 0; i < QUESTIONS_PER_COLUMN; i++) {
            questions[i] = start + i;
        }
        return questions;
    }
    
    /**
     * Get the column index for a given question number (1-indexed).
     */
    public static int getColumnForQuestion(int questionNumber) {
        return (questionNumber - 1) / QUESTIONS_PER_COLUMN;
    }
    
    /**
     * Get the row index within a column for a given question number (0-indexed).
     */
    public static int getRowForQuestion(int questionNumber) {
        return (questionNumber - 1) % QUESTIONS_PER_COLUMN;
    }
    
    /**
     * Get the digit label for a row index (0-9 → "1"-"0").
     * In the template, rows are labeled 1,2,3,4,5,6,7,8,9,0
     */
    public static String getDigitLabel(int rowIndex) {
        if (rowIndex == 9) return "0";
        return String.valueOf(rowIndex + 1);
    }
    
    /**
     * Get the actual digit value for a row index.
     * Row 0 = digit 1, Row 1 = digit 2, ... Row 8 = digit 9, Row 9 = digit 0
     */
    public static int getDigitValue(int rowIndex) {
        if (rowIndex == 9) return 0;
        return rowIndex + 1;
    }
}

