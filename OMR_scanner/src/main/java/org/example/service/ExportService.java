package org.example.service;

import org.example.model.AnswerKey;
import org.example.model.Scan;
import org.example.model.ScanAnswer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for exporting data to CSV format.
 * Handles exporting scans, answer keys, and multiple results.
 */
public class ExportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Export a single scan result to CSV.
     * 
     * Format: Student ID, Test ID, Answer Key, Date, Score, Correct, Wrong, Empty, Invalid, Q1-Q60
     * 
     * @param scan The scan to export
     * @param outputFile The output CSV file
     * @throws IOException if writing fails
     */
    public void exportScan(Scan scan, File outputFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            // Write header
            writer.print("Student ID,Test ID,Answer Key,Date,Score (%),Correct,Wrong,Empty,Invalid");
            for (int i = 1; i <= 60; i++) {
                writer.print(",Q" + i);
            }
            writer.println();
            
            // Write data
            writeScanRow(writer, scan);
        }
    }

    /**
     * Export multiple scan results to CSV.
     * 
     * @param scans List of scans to export
     * @param outputFile The output CSV file
     * @throws IOException if writing fails
     */
    public void exportScans(List<Scan> scans, File outputFile) throws IOException {
        if (scans == null || scans.isEmpty()) {
            throw new IllegalArgumentException("No scans to export");
        }
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            // Write header
            writer.print("Student ID,Test ID,Answer Key,Date,Score (%),Correct,Wrong,Empty,Invalid");
            for (int i = 1; i <= 60; i++) {
                writer.print(",Q" + i);
            }
            writer.println();
            
            // Write each scan
            for (Scan scan : scans) {
                writeScanRow(writer, scan);
            }
        }
    }

    /**
     * Export an answer key to CSV.
     * 
     * Format: Question, Correct Answer
     * 
     * @param key The answer key to export
     * @param outputFile The output CSV file
     * @throws IOException if writing fails
     */
    public void exportAnswerKey(AnswerKey key, File outputFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            // Write header
            writer.println("Question,Correct Answer");
            
            // Write each answer
            for (int i = 1; i <= key.getTotalQuestions(); i++) {
                String answer = key.getAnswer(i);
                writer.println(i + "," + (answer != null ? answer : ""));
            }
        }
    }

    /**
     * Write a single scan row to CSV.
     */
    private void writeScanRow(PrintWriter writer, Scan scan) {
        // Basic info
        writer.print(csvEscape(scan.getStudentId()));
        writer.print(",");
        writer.print(csvEscape(scan.getTestId()));
        writer.print(",");
        writer.print(csvEscape(scan.getAnswerKey() != null ? scan.getAnswerKey().getName() : "N/A"));
        writer.print(",");
        writer.print(csvEscape(scan.getCreatedAt() != null ? scan.getCreatedAt().format(DATE_FORMATTER) : ""));
        writer.print(",");
        writer.print(String.format("%.1f", scan.getScorePercentage()));
        writer.print(",");
        writer.print(scan.getScoreCorrect());
        writer.print(",");
        writer.print(scan.getScoreWrong());
        writer.print(",");
        writer.print(scan.getScoreEmpty());
        writer.print(",");
        writer.print(scan.getScoreInvalid());
        
        // Answers (Q1-Q60)
        List<ScanAnswer> answers = scan.getAnswers();
        for (int i = 1; i <= 60; i++) {
            writer.print(",");
            if (answers != null && i <= answers.size()) {
                ScanAnswer ans = answers.get(i - 1);
                String detected = ans.getDetectedAnswer();
                writer.print(detected != null ? detected : "");
            }
        }
        
        writer.println();
    }

    /**
     * Escape CSV values (handle commas, quotes, newlines).
     */
    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        
        // If contains comma, quote, or newline, wrap in quotes and escape internal quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        
        return value;
    }
}

