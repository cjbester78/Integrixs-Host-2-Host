package com.integrixs.shared.constants;

/**
 * Constants used throughout the H2H application
 */
public final class H2HConstants {
    
    // SFTP Configuration
    public static final int DEFAULT_RETRY_COUNT = 3;
    public static final int DEFAULT_RETRY_DELAY_MS = 5000;
    public static final int DEFAULT_SESSION_TIMEOUT = 60000;
    public static final int DEFAULT_CHANNEL_TIMEOUT = 60000;
    public static final String DEFAULT_SFTP_PORT = "22";
    
    // Configuration Directories
    public static final String DEFAULT_CONFIG_DIR = "config";
    public static final String DEFAULT_SSH_DIR = "ssh";
    public static final String DEFAULT_LOGS_DIR = "logs";
    
    // Configuration Files
    public static final String APP_CONFIG_FILE = "app.config.properties";
    
    // Bank Names
    public static final String BANK_FNB = "FNB";
    public static final String BANK_STANBIC = "Stanbic";
    
    // Operation Types
    public static final String OPERATION_UPLOAD = "upload";
    public static final String OPERATION_DOWNLOAD = "download";
    
    // Log Retention
    public static final int DEFAULT_LOG_RETENTION_DAYS = 30;
    
    // Environment Variables
    public static final String ENV_APP_ENVIRONMENT = "APP_ENVIRONMENT";
    public static final String PROP_APP_ENVIRONMENT = "app.environment";
    
    // Default Environment
    public static final String DEFAULT_ENVIRONMENT = "PRD";
    
    // File Extensions
    public static final String[] SUPPORTED_FILE_EXTENSIONS = {".xml", ".txt", ".csv", ".zip"};
    
    private H2HConstants() {
        // Utility class - prevent instantiation
    }
}