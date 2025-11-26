package org.example.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Singleton service for SQLite database connection management.
 * 
 * Handles:
 * - Database file creation in DB folder inside application directory (portable)
 * - Connection pooling (single connection for SQLite)
 * - Schema initialization
 * - Connection lifecycle
 */
public class DatabaseService {
    
    private static final String DB_FILENAME = "omr_reader.db";
    private static final String SCHEMA_RESOURCE = "/db/schema.sql";
    
    private static DatabaseService instance;
    private Connection connection;
    private String dbPath;
    
    /**
     * Private constructor for singleton pattern.
     */
    private DatabaseService() {
    }
    
    /**
     * Get the singleton instance.
     */
    public static synchronized DatabaseService getInstance() {
        if (instance == null) {
            instance = new DatabaseService();
        }
        return instance;
    }
    
    /**
     * Initialize the database connection and schema.
     * Call this once at application startup.
     * 
     * @return true if initialization was successful
     */
    public boolean initialize() {
        try {
            // Determine database path
            dbPath = getDbPath();
            
            // Ensure parent directory exists
            Path dbFilePath = Paths.get(dbPath);
            Files.createDirectories(dbFilePath.getParent());
            
            // Create connection
            String url = "jdbc:sqlite:" + dbPath;
            connection = DriverManager.getConnection(url);
            
            // Enable foreign keys
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }
            
            // Initialize schema
            initializeSchema();
            
            System.out.println("Database initialized at: " + dbPath);
            return true;
            
        } catch (SQLException | IOException e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Get the database connection.
     * Automatically reconnects if connection was lost.
     * 
     * @return Active database connection
     * @throws SQLException if connection cannot be established
     */
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            String url = "jdbc:sqlite:" + dbPath;
            connection = DriverManager.getConnection(url);
            
            // Enable foreign keys
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }
        }
        return connection;
    }
    
    /**
     * Check if the database connection is active.
     */
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
    
    /**
     * Close the database connection.
     * Call this when the application exits.
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("Database connection closed.");
            } catch (SQLException e) {
                System.err.println("Error closing database: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get the full path to the database file.
     * Database is stored in a DB folder inside the application directory for portability.
     */
    public String getDbPath() {
        if (dbPath != null) {
            return dbPath;
        }
        
        // Get the application directory (where the JAR is running from)
        String appDir = System.getProperty("user.dir");
        
        // Create DB folder path
        String dbFolder = appDir + java.io.File.separator + "DB";
        String dbFile = dbFolder + java.io.File.separator + DB_FILENAME;
        
        return dbFile;
    }
    
    /**
     * Initialize the database schema from the SQL file.
     */
    private void initializeSchema() throws SQLException, IOException {
        String schema = loadSchemaFromResource();
        
        // Remove comments and split carefully
        // Handle triggers which contain semicolons inside BEGIN...END blocks
        List<String> statements = parseStatements(schema);
        
        try (Statement stmt = connection.createStatement()) {
            for (String sql : statements) {
                sql = sql.trim();
                if (!sql.isEmpty()) {
                    try {
                        stmt.execute(sql);
                    } catch (SQLException e) {
                        // Log but continue - some statements may fail if objects already exist
                        if (!e.getMessage().contains("already exists")) {
                            System.err.println("Schema warning: " + e.getMessage());
                        }
                    }
                }
            }
        }
        System.out.println("Database schema initialized successfully.");
    }
    
    /**
     * Parse SQL statements handling BEGIN...END blocks for triggers.
     */
    private List<String> parseStatements(String schema) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inTrigger = false;
        
        String[] lines = schema.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            
            // Skip comment-only lines
            if (trimmed.startsWith("--")) {
                continue;
            }
            
            // Remove inline comments
            int commentIdx = trimmed.indexOf("--");
            if (commentIdx > 0) {
                trimmed = trimmed.substring(0, commentIdx).trim();
            }
            
            if (trimmed.isEmpty()) {
                continue;
            }
            
            // Check for trigger start
            if (trimmed.toUpperCase().contains("CREATE TRIGGER")) {
                inTrigger = true;
            }
            
            current.append(trimmed).append(" ");
            
            // Check for statement end
            if (inTrigger) {
                if (trimmed.toUpperCase().startsWith("END")) {
                    statements.add(current.toString().trim());
                    current = new StringBuilder();
                    inTrigger = false;
                }
            } else if (trimmed.endsWith(";")) {
                // Remove trailing semicolon and add
                String stmt = current.toString().trim();
                if (stmt.endsWith(";")) {
                    stmt = stmt.substring(0, stmt.length() - 1).trim();
                }
                if (!stmt.isEmpty()) {
                    statements.add(stmt);
                }
                current = new StringBuilder();
            }
        }
        
        // Handle any remaining statement
        String remaining = current.toString().trim();
        if (!remaining.isEmpty()) {
            if (remaining.endsWith(";")) {
                remaining = remaining.substring(0, remaining.length() - 1).trim();
            }
            if (!remaining.isEmpty()) {
                statements.add(remaining);
            }
        }
        
        return statements;
    }
    
    /**
     * Load the schema SQL from resources.
     */
    private String loadSchemaFromResource() throws IOException {
        try (InputStream is = getClass().getResourceAsStream(SCHEMA_RESOURCE)) {
            if (is == null) {
                throw new IOException("Schema resource not found: " + SCHEMA_RESOURCE);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }
    
    /**
     * Execute a query and return the result set.
     * Caller is responsible for closing the ResultSet.
     */
    public ResultSet executeQuery(String sql, Object... params) throws SQLException {
        PreparedStatement pstmt = getConnection().prepareStatement(sql);
        setParameters(pstmt, params);
        return pstmt.executeQuery();
    }
    
    /**
     * Execute an update (INSERT, UPDATE, DELETE) and return affected row count.
     */
    public int executeUpdate(String sql, Object... params) throws SQLException {
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            setParameters(pstmt, params);
            return pstmt.executeUpdate();
        }
    }
    
    /**
     * Execute an INSERT and return the generated key.
     */
    public long executeInsert(String sql, Object... params) throws SQLException {
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql, 
                Statement.RETURN_GENERATED_KEYS)) {
            setParameters(pstmt, params);
            pstmt.executeUpdate();
            
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            return -1;
        }
    }
    
    /**
     * Set parameters on a prepared statement.
     */
    private void setParameters(PreparedStatement pstmt, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            if (param == null) {
                pstmt.setNull(i + 1, Types.NULL);
            } else if (param instanceof String) {
                pstmt.setString(i + 1, (String) param);
            } else if (param instanceof Integer) {
                pstmt.setInt(i + 1, (Integer) param);
            } else if (param instanceof Long) {
                pstmt.setLong(i + 1, (Long) param);
            } else if (param instanceof Double) {
                pstmt.setDouble(i + 1, (Double) param);
            } else if (param instanceof Boolean) {
                pstmt.setBoolean(i + 1, (Boolean) param);
            } else if (param instanceof java.util.Date) {
                pstmt.setTimestamp(i + 1, new Timestamp(((java.util.Date) param).getTime()));
            } else {
                pstmt.setObject(i + 1, param);
            }
        }
    }
    
    /**
     * Begin a transaction.
     */
    public void beginTransaction() throws SQLException {
        getConnection().setAutoCommit(false);
    }
    
    /**
     * Commit the current transaction.
     */
    public void commit() throws SQLException {
        Connection conn = getConnection();
        if (!conn.getAutoCommit()) {
            conn.commit();
            conn.setAutoCommit(true);
        }
    }
    
    /**
     * Rollback the current transaction.
     */
    public void rollback() {
        try {
            if (connection != null && !connection.getAutoCommit()) {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Rollback failed: " + e.getMessage());
        }
    }
}

