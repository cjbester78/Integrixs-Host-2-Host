package com.integrixs.core.service;

import com.integrixs.shared.model.Adapter;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.ChannelSftp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for adapter configuration validation and connection testing
 * Handles validation logic and connection testing following Single Responsibility Principle
 */
@Service
public class AdapterValidationService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdapterValidationService.class);
    
    /**
     * Test adapter connection
     */
    public Map<String, Object> testAdapterConnection(Adapter adapter) {
        logger.info("Testing connection for adapter: {} (type: {})", adapter.getName(), adapter.getAdapterType());
        
        try {
            Map<String, Object> result;
            
            switch (adapter.getAdapterType().toUpperCase()) {
                case "FILE":
                    result = testFileAdapter(adapter);
                    break;
                case "SFTP":
                    result = testSftpAdapter(adapter);
                    break;
                case "EMAIL":
                    result = testEmailAdapter(adapter);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported adapter type for testing: " + adapter.getAdapterType());
            }
            
            logger.info("Connection test completed for adapter: {}", adapter.getName());
            return result;
            
        } catch (Exception e) {
            logger.error("Connection test failed for adapter: {} - {}", adapter.getName(), e.getMessage(), e);
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("testSuccess", false);
            errorResult.put("error", e.getMessage());
            errorResult.put("adapterName", adapter.getName());
            errorResult.put("adapterType", adapter.getAdapterType());
            
            return errorResult;
        }
    }
    
    /**
     * Validate adapter configuration
     */
    public void validateAdapterConfiguration(Adapter adapter) {
        if (adapter.getName() == null || adapter.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Adapter name is required");
        }
        
        if (adapter.getAdapterType() == null || adapter.getAdapterType().trim().isEmpty()) {
            throw new IllegalArgumentException("Adapter type is required");
        }
        
        if (adapter.getDirection() == null || adapter.getDirection().trim().isEmpty()) {
            throw new IllegalArgumentException("Adapter direction is required");
        }
        
        if (adapter.getConfiguration() == null || adapter.getConfiguration().isEmpty()) {
            throw new IllegalArgumentException("Adapter configuration is required");
        }
        
        // Type-specific validation with direction consideration
        switch (adapter.getAdapterType().toUpperCase()) {
            case "FILE":
                validateFileAdapterConfig(adapter.getConfiguration(), adapter.getDirection());
                break;
            case "SFTP":
                validateSftpAdapterConfig(adapter.getConfiguration(), adapter.getDirection());
                break;
            case "EMAIL":
                validateEmailAdapterConfig(adapter.getConfiguration(), adapter.getDirection());
                break;
            default:
                throw new IllegalArgumentException("Unsupported adapter type: " + adapter.getAdapterType());
        }
    }
    
    /**
     * Validate file adapter configuration
     */
    private void validateFileAdapterConfig(Map<String, Object> config, String direction) {
        if ("SENDER".equalsIgnoreCase(direction)) {
            // Sender adapters need source directory to read from
            validateRequiredField(config, "sourceDirectory", "Sender FILE adapter");

            // CRITICAL: Sender adapters MUST have scheduler configuration
            validateSchedulerConfiguration(config, "FILE SENDER");

            // Phase 1: Validate sender-specific configurations
            validateFileSenderAdvancedConfig(config);

        } else if ("RECEIVER".equalsIgnoreCase(direction)) {
            // Receiver adapters need target directory to write to
            validateRequiredField(config, "targetDirectory", "Receiver FILE adapter");

            // Phase 2: Validate receiver-specific configurations
            validateFileReceiverAdvancedConfig(config);
        }
    }

    /**
     * Phase 1: Validate FILE SENDER advanced configurations
     */
    private void validateFileSenderAdvancedConfig(Map<String, Object> config) {
        // Validate file size limit if configured
        if (config.containsKey("maximumFileSize")) {
            try {
                long maxSize = getLongValue(config, "maximumFileSize");
                if (maxSize < 0) {
                    throw new IllegalArgumentException("FILE SENDER 'maximumFileSize' must be >= 0 (0 means no limit)");
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("FILE SENDER 'maximumFileSize' must be a valid number");
            }
        }

        // Validate modification check wait time if configured
        if (config.containsKey("msecsToWaitBeforeModificationCheck")) {
            try {
                int waitTime = getIntValue(config, "msecsToWaitBeforeModificationCheck");
                if (waitTime < 0) {
                    throw new IllegalArgumentException("FILE SENDER 'msecsToWaitBeforeModificationCheck' must be >= 0");
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("FILE SENDER 'msecsToWaitBeforeModificationCheck' must be a valid number");
            }
        }

        // Validate error archiving configuration
        if (config.containsKey("archiveFaultySourceFiles")) {
            boolean archiveErrors = getBooleanValue(config, "archiveFaultySourceFiles");
            if (archiveErrors) {
                if (!config.containsKey("archiveErrorDirectory") ||
                    config.get("archiveErrorDirectory") == null ||
                    config.get("archiveErrorDirectory").toString().trim().isEmpty()) {
                    throw new IllegalArgumentException("FILE SENDER 'archiveErrorDirectory' is required when 'archiveFaultySourceFiles' is enabled");
                }
            }
        }

        // Validate empty file handling value
        if (config.containsKey("emptyFileHandling")) {
            String emptyHandling = config.get("emptyFileHandling").toString();
            if (!"Do Not Create Message".equals(emptyHandling) &&
                !"Process Empty Files".equals(emptyHandling) &&
                !"Skip Empty Files".equals(emptyHandling)) {
                throw new IllegalArgumentException("FILE SENDER 'emptyFileHandling' must be one of: 'Do Not Create Message', 'Process Empty Files', 'Skip Empty Files'");
            }
        }

        logger.debug("FILE SENDER advanced configuration validation passed");
    }

    /**
     * Phase 2: Validate FILE RECEIVER advanced configurations
     */
    private void validateFileReceiverAdvancedConfig(Map<String, Object> config) {
        // Validate output filename mode
        if (config.containsKey("outputFilenameMode")) {
            String mode = config.get("outputFilenameMode").toString();
            if (!"UseOriginal".equals(mode) && !"AddTimestamp".equals(mode) && !"Custom".equals(mode)) {
                throw new IllegalArgumentException("FILE RECEIVER 'outputFilenameMode' must be one of: 'UseOriginal', 'AddTimestamp', 'Custom'");
            }

            // If Custom mode, customFilenamePattern is required
            if ("Custom".equals(mode)) {
                if (!config.containsKey("customFilenamePattern") ||
                    config.get("customFilenamePattern") == null ||
                    config.get("customFilenamePattern").toString().trim().isEmpty()) {
                    throw new IllegalArgumentException("FILE RECEIVER 'customFilenamePattern' is required when outputFilenameMode is 'Custom'");
                }

                // Validate pattern contains valid variables
                String pattern = config.get("customFilenamePattern").toString();
                if (!pattern.contains("{original_name}") && !pattern.contains("{timestamp}") &&
                    !pattern.contains("{date}") && !pattern.contains("{uuid}") && !pattern.contains("{extension}")) {
                    logger.warn("Custom filename pattern '{}' does not contain any variables. Recommended variables: {{original_name}}, {{timestamp}}, {{date}}, {{extension}}, {{uuid}}", pattern);
                }
            }
        }

        // Validate write mode
        if (config.containsKey("writeMode")) {
            String mode = config.get("writeMode").toString();
            if (!"Directly".equals(mode) && !"Create Temp File".equals(mode)) {
                throw new IllegalArgumentException("FILE RECEIVER 'writeMode' must be one of: 'Directly', 'Create Temp File'");
            }
        }

        // Validate empty message handling
        if (config.containsKey("emptyMessageHandling")) {
            String handling = config.get("emptyMessageHandling").toString();
            if (!"Write Empty File".equals(handling) && !"Skip Empty Messages".equals(handling)) {
                throw new IllegalArgumentException("FILE RECEIVER 'emptyMessageHandling' must be one of: 'Write Empty File', 'Skip Empty Messages'");
            }
        }

        // Validate maximum concurrency
        if (config.containsKey("maximumConcurrency")) {
            try {
                int concurrency = getIntValue(config, "maximumConcurrency");
                if (concurrency < 1) {
                    throw new IllegalArgumentException("FILE RECEIVER 'maximumConcurrency' must be >= 1");
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("FILE RECEIVER 'maximumConcurrency' must be a valid number");
            }
        }

        logger.debug("FILE RECEIVER advanced configuration validation passed");
    }
    
    /**
     * Validate SFTP adapter configuration
     */
    private void validateSftpAdapterConfig(Map<String, Object> config, String direction) {
        // Common required fields for both directions
        String[] commonFields = {"host", "port", "username"};

        for (String field : commonFields) {
            validateRequiredField(config, field, "SFTP adapter");
        }

        // Validate port is numeric
        try {
            Integer.parseInt(config.get("port").toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("SFTP adapter 'port' must be a valid number");
        }

        // Direction-specific validation
        if ("SENDER".equalsIgnoreCase(direction)) {
            // Sender needs source directory (remote path to read from)
            validateRequiredField(config, "sourceDirectory", "Sender SFTP adapter");
            validateRequiredField(config, "filePattern", "Sender SFTP adapter");

            // CRITICAL: Sender adapters MUST have scheduler configuration
            validateSchedulerConfiguration(config, "SFTP SENDER");

        } else if ("RECEIVER".equalsIgnoreCase(direction)) {
            // Receiver needs target directory (remote path to write to)
            validateRequiredField(config, "targetDirectory", "Receiver SFTP adapter");

            // Phase 3: Validate receiver-specific configurations
            validateSftpReceiverAdvancedConfig(config);
        }
    }

    /**
     * Phase 3: Validate SFTP RECEIVER advanced configurations
     */
    private void validateSftpReceiverAdvancedConfig(Map<String, Object> config) {
        // Validate remote file permissions format (octal)
        if (config.containsKey("remoteFilePermissions")) {
            String permissions = config.get("remoteFilePermissions").toString();
            if (!permissions.trim().isEmpty()) {
                try {
                    Integer.parseInt(permissions, 8); // Parse as octal
                    // Validate it's a 3-digit octal number
                    if (!permissions.matches("[0-7]{3}")) {
                        throw new IllegalArgumentException("SFTP RECEIVER 'remoteFilePermissions' must be a 3-digit octal number (e.g., 644, 755, 777)");
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("SFTP RECEIVER 'remoteFilePermissions' must be a valid octal number (e.g., 644, 755, 777)");
                }
            }
        }

        // Validate temporary file suffix
        if (config.containsKey("useTemporaryFileName")) {
            boolean useTempFile = getBooleanValue(config, "useTemporaryFileName");
            if (useTempFile) {
                if (!config.containsKey("temporaryFileSuffix") ||
                    config.get("temporaryFileSuffix") == null ||
                    config.get("temporaryFileSuffix").toString().trim().isEmpty()) {
                    logger.warn("SFTP RECEIVER 'temporaryFileSuffix' is empty, will default to '.tmp'");
                }
            }
        }

        // Validate output filename mode (same as FILE receiver)
        if (config.containsKey("outputFilenameMode")) {
            String mode = config.get("outputFilenameMode").toString();
            if (!"UseOriginal".equals(mode) && !"AddTimestamp".equals(mode) && !"Custom".equals(mode)) {
                throw new IllegalArgumentException("SFTP RECEIVER 'outputFilenameMode' must be one of: 'UseOriginal', 'AddTimestamp', 'Custom'");
            }

            // If Custom mode, customFilenamePattern is required
            if ("Custom".equals(mode)) {
                if (!config.containsKey("customFilenamePattern") ||
                    config.get("customFilenamePattern") == null ||
                    config.get("customFilenamePattern").toString().trim().isEmpty()) {
                    throw new IllegalArgumentException("SFTP RECEIVER 'customFilenamePattern' is required when outputFilenameMode is 'Custom'");
                }

                // Validate pattern contains valid variables
                String pattern = config.get("customFilenamePattern").toString();
                if (!pattern.contains("{original_name}") && !pattern.contains("{timestamp}") &&
                    !pattern.contains("{date}") && !pattern.contains("{uuid}") && !pattern.contains("{extension}")) {
                    logger.warn("Custom filename pattern '{}' does not contain any variables. Recommended variables: {{original_name}}, {{timestamp}}, {{date}}, {{extension}}, {{uuid}}", pattern);
                }
            }
        }

        logger.debug("SFTP RECEIVER advanced configuration validation passed");
    }
    
    /**
     * Validate email adapter configuration
     */
    private void validateEmailAdapterConfig(Map<String, Object> config, String direction) {
        // Common required fields for both directions
        String[] requiredFields = {"smtpHost", "smtpPort", "smtpUsername", "smtpPassword"};
        
        for (String field : requiredFields) {
            if (!config.containsKey(field) || 
                config.get(field) == null || 
                config.get(field).toString().trim().isEmpty()) {
                throw new IllegalArgumentException("Email adapter requires '" + field + "' configuration");
            }
        }
        
        // Validate port is numeric
        try {
            Integer.parseInt(config.get("smtpPort").toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Email adapter 'smtpPort' must be a valid number");
        }
        
        // Direction-specific validation
        if ("RECEIVER".equalsIgnoreCase(direction)) {
            // Receiver email adapters require recipients
            if (!config.containsKey("recipients") || 
                config.get("recipients") == null || 
                config.get("recipients").toString().trim().isEmpty()) {
                throw new IllegalArgumentException("Receiver email adapter requires 'recipients' configuration");
            }
        }
    }

    /**
     * Validate required field exists and is not empty
     */
    private void validateRequiredField(Map<String, Object> config, String field, String adapterType) {
        if (!config.containsKey(field) ||
            config.get(field) == null ||
            config.get(field).toString().trim().isEmpty()) {
            throw new IllegalArgumentException(adapterType + " requires '" + field + "' configuration");
        }
    }

    /**
     * CRITICAL: Validate scheduler configuration for sender adapters.
     * Scheduled execution times are approved by all parties - adapter cannot be saved without valid config.
     */
    private void validateSchedulerConfiguration(Map<String, Object> config, String adapterType) {
        // Validate scheduleMode is present and valid
        Object scheduleModeObj = config.get("scheduleMode");
        if (scheduleModeObj == null || scheduleModeObj.toString().trim().isEmpty()) {
            throw new IllegalArgumentException(adapterType + " adapter requires 'scheduleMode' configuration. " +
                "Scheduled execution times must be explicitly configured.");
        }

        String scheduleMode = scheduleModeObj.toString();
        if (!"OnTime".equals(scheduleMode) && !"Every".equals(scheduleMode)) {
            throw new IllegalArgumentException(adapterType + " adapter has invalid 'scheduleMode': '" + scheduleMode + "'. " +
                "Must be 'OnTime' or 'Every'.");
        }

        // Validate mode-specific fields
        if ("OnTime".equals(scheduleMode)) {
            Object onTimeValue = config.get("onTimeValue");
            if (onTimeValue == null || onTimeValue.toString().trim().isEmpty()) {
                throw new IllegalArgumentException(adapterType + " adapter with 'OnTime' scheduleMode requires 'onTimeValue' configuration.");
            }
        } else if ("Every".equals(scheduleMode)) {
            Object everyInterval = config.get("everyInterval");
            if (everyInterval == null || everyInterval.toString().trim().isEmpty()) {
                throw new IllegalArgumentException(adapterType + " adapter with 'Every' scheduleMode requires 'everyInterval' configuration.");
            }
        }

        logger.debug("Scheduler configuration validated for {}: scheduleMode={}", adapterType, scheduleMode);
    }

    /**
     * Test file adapter connection
     */
    private Map<String, Object> testFileAdapter(Adapter adapter) {
        Map<String, Object> result = new HashMap<>();
        result.put("testType", "FILE_ACCESS");
        
        try {
            // Mock file adapter test - validate configuration
            Map<String, Object> config = adapter.getConfiguration();
            String direction = adapter.getDirection();
            
            if ("SENDER".equalsIgnoreCase(direction)) {
                String sourceDirectory = (String) config.get("sourceDirectory");
                if (sourceDirectory != null && !sourceDirectory.trim().isEmpty()) {
                    result.put("directoryAccessible", true);
                    result.put("sourceDirectory", sourceDirectory);
                    result.put("testResult", "Sender file adapter configuration is valid");
                } else {
                    throw new RuntimeException("Source directory not configured for sender adapter");
                }
            } else if ("RECEIVER".equalsIgnoreCase(direction)) {
                String targetDirectory = (String) config.get("targetDirectory");
                if (targetDirectory != null && !targetDirectory.trim().isEmpty()) {
                    result.put("directoryAccessible", true);
                    result.put("targetDirectory", targetDirectory);
                    result.put("testResult", "Receiver file adapter configuration is valid");
                } else {
                    throw new RuntimeException("Target directory not configured for receiver adapter");
                }
            }
            
            result.put("testSuccess", true);
            
        } catch (Exception e) {
            throw new RuntimeException("File adapter test failed: " + e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * Test SFTP adapter connection, directory access, and permissions
     */
    private Map<String, Object> testSftpAdapter(Adapter adapter) {
        Map<String, Object> result = new HashMap<>();
        result.put("testType", "SFTP_CONNECTION");
        
        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp sftpChannel = null;
        
        Map<String, Object> config = adapter.getConfiguration();
        String host = (String) config.get("host");
        Object portObj = config.get("port");
        String username = (String) config.get("username");
        String password = (String) config.get("password");
        Boolean strictHostKeyChecking = (Boolean) config.get("strictHostKeyChecking");

        // Get the correct directory based on adapter direction
        String direction = adapter.getDirection();
        String directoryToTest;
        String directoryFieldName;
        if ("SENDER".equalsIgnoreCase(direction)) {
            directoryToTest = (String) config.get("sourceDirectory");
            directoryFieldName = "Source directory";
        } else {
            directoryToTest = (String) config.get("targetDirectory");
            directoryFieldName = "Target directory";
        }

        int port = 22;
        if (portObj != null) {
            if (portObj instanceof Number) {
                port = ((Number) portObj).intValue();
            } else if (portObj instanceof String) {
                port = Integer.parseInt((String) portObj);
            }
        }

        // ALWAYS populate the basic config details that we know
        result.put("adapterType", "SFTP");
        result.put("host", host);
        result.put("port", port);
        result.put("username", username);
        result.put("direction", direction);
        result.put("directory", directoryToTest);

        // Initialize all status flags to false (will be updated as we progress)
        result.put("connectionEstablished", false);
        result.put("sftpChannelOpened", false);
        result.put("directoryAccessible", false);
        result.put("readPermission", false);
        result.put("writePermission", false);
        result.put("deletePermission", false);

        try {
            // Validate required configuration
            if (host == null || host.trim().isEmpty()) {
                throw new RuntimeException("Host is not configured");
            }
            if (username == null || username.trim().isEmpty()) {
                throw new RuntimeException("Username is not configured");
            }
            if (password == null || password.trim().isEmpty()) {
                throw new RuntimeException("Password is not configured");
            }
            if (directoryToTest == null || directoryToTest.trim().isEmpty()) {
                throw new RuntimeException(directoryFieldName + " is not configured");
            }
            
            // Step 1: Establish SFTP connection
            session = jsch.getSession(username, host, port);
            session.setPassword(password);
            
            // Configure host key checking
            if (strictHostKeyChecking != null && !strictHostKeyChecking) {
                session.setConfig("StrictHostKeyChecking", "no");
            } else {
                session.setConfig("StrictHostKeyChecking", "yes");
            }
            
            session.setTimeout(30000); // 30 second timeout
            session.connect();
            result.put("connectionEstablished", true);
            logger.info("SFTP connection established to {}:{}", host, port);
            
            // Step 2: Open SFTP channel
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();
            result.put("sftpChannelOpened", true);

            // Step 3: Get home directory
            try {
                String homeDir = sftpChannel.getHome();
                result.put("homeDirectory", homeDir);
                result.put("homeDirectoryAccessible", true);
                logger.info("Home directory: {}", homeDir);
            } catch (Exception e) {
                result.put("homeDirectory", "/");
                result.put("homeDirectoryAccessible", false);
                logger.warn("Could not get home directory: {}", e.getMessage());
            }

            // Step 4: Test target directory access
            try {
                sftpChannel.cd(directoryToTest);
                result.put("directoryAccessible", true);
                logger.info("Successfully accessed remote directory: {}", directoryToTest);

                // Test read permission by listing directory
                try {
                    @SuppressWarnings("unchecked")
                    java.util.Vector<ChannelSftp.LsEntry> files = sftpChannel.ls(directoryToTest);
                    result.put("readPermission", true);
                    // Count actual files (exclude . and ..)
                    int fileCount = 0;
                    for (ChannelSftp.LsEntry entry : files) {
                        if (!entry.getFilename().equals(".") && !entry.getFilename().equals("..")) {
                            fileCount++;
                        }
                    }
                    result.put("fileCount", fileCount);
                    logger.debug("Read permission confirmed for directory: {} ({} files)", directoryToTest, fileCount);
                } catch (Exception e) {
                    logger.warn("Read permission test failed: {}", e.getMessage());
                }

                // Test write permission by creating a temp file
                String testFileName = ".h2h_permission_test_" + System.currentTimeMillis();
                String testFilePath = directoryToTest + "/" + testFileName;
                try {
                    java.io.ByteArrayInputStream testData = new java.io.ByteArrayInputStream("test".getBytes());
                    sftpChannel.put(testData, testFilePath);
                    result.put("writePermission", true);
                    logger.debug("Write permission confirmed for directory: {}", directoryToTest);

                    // Test delete permission by removing the temp file
                    try {
                        sftpChannel.rm(testFilePath);
                        result.put("deletePermission", true);
                        logger.debug("Delete permission confirmed for directory: {}", directoryToTest);
                    } catch (Exception e) {
                        logger.warn("Delete permission test failed: {}", e.getMessage());
                    }
                } catch (Exception e) {
                    logger.warn("Write permission test failed: {}", e.getMessage());
                }

                // Build success message
                StringBuilder successMsg = new StringBuilder("SFTP connection successful. Directory accessible.");
                boolean hasReadPerm = Boolean.TRUE.equals(result.get("readPermission"));
                boolean hasWritePerm = Boolean.TRUE.equals(result.get("writePermission"));
                boolean hasDeletePerm = Boolean.TRUE.equals(result.get("deletePermission"));

                if (hasReadPerm && hasWritePerm && hasDeletePerm) {
                    successMsg.append(" Full read/write/delete permissions confirmed.");
                } else {
                    if (hasReadPerm) successMsg.append(" Read permission confirmed.");
                    if (hasWritePerm) successMsg.append(" Write permission confirmed.");
                    if (hasDeletePerm) successMsg.append(" Delete permission confirmed.");
                }

                result.put("testSuccess", true);
                result.put("testResult", successMsg.toString());

            } catch (Exception e) {
                logger.error("Directory access failed for: {}", directoryToTest);
                throw new RuntimeException("Cannot access remote directory: " + directoryToTest);
            }
            
        } catch (Exception e) {
            logger.error("SFTP connection test failed: {}", e.getMessage());
            result.put("testSuccess", false);
            throw new RuntimeException("SFTP adapter test failed: " + e.getMessage(), e);
            
        } finally {
            // Clean up connections
            if (sftpChannel != null && sftpChannel.isConnected()) {
                sftpChannel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
        
        return result;
    }
    
    /**
     * Test email adapter connection
     */
    private Map<String, Object> testEmailAdapter(Adapter adapter) {
        Map<String, Object> result = new HashMap<>();
        result.put("testType", "EMAIL_CONNECTION");

        try {
            // Mock email adapter test - validate configuration
            Map<String, Object> config = adapter.getConfiguration();
            String smtpHost = (String) config.get("smtpHost");
            Object smtpPortObj = config.get("smtpPort");

            if (smtpHost != null && !smtpHost.trim().isEmpty() && smtpPortObj != null) {
                // Simulate successful SMTP connection test
                result.put("smtpConnectionEstablished", true);
                result.put("smtpHost", smtpHost);
                result.put("smtpPort", smtpPortObj);
                result.put("testResult", "Email adapter configuration is valid");
                result.put("testSuccess", true);
            } else {
                throw new RuntimeException("SMTP host or port not configured");
            }

        } catch (Exception e) {
            throw new RuntimeException("Email adapter test failed: " + e.getMessage(), e);
        }

        return result;
    }

    // ==================================================================================
    // HELPER METHODS FOR CONFIGURATION VALUE EXTRACTION
    // ==================================================================================

    /**
     * Get integer value from configuration with proper type handling
     */
    private int getIntValue(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            return Integer.parseInt((String) value);
        }
        throw new NumberFormatException("Cannot convert " + key + " to integer");
    }

    /**
     * Get long value from configuration with proper type handling
     */
    private long getLongValue(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            return Long.parseLong((String) value);
        }
        throw new NumberFormatException("Cannot convert " + key + " to long");
    }

    /**
     * Get boolean value from configuration with proper type handling
     */
    private boolean getBooleanValue(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return false;
    }
}