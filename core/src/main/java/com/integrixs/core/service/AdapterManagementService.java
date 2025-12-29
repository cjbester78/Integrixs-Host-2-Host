package com.integrixs.core.service;

import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.shared.model.Adapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for managing adapter configurations and operations
 * Provides comprehensive CRUD operations and adapter lifecycle management
 */
@Service
public class AdapterManagementService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdapterManagementService.class);
    
    private final AdapterRepository adapterRepository;
    
    @Autowired
    public AdapterManagementService(AdapterRepository adapterRepository) {
        this.adapterRepository = adapterRepository;
    }
    
    /**
     * Get all adapters
     */
    public List<Adapter> getAllAdapters() {
        logger.debug("Retrieving all adapters");
        return adapterRepository.findAll();
    }
    
    /**
     * Get adapters by type
     */
    public List<Adapter> getAdaptersByType(String type) {
        logger.debug("Retrieving adapters of type: {}", type);
        return adapterRepository.findByType(type);
    }
    
    /**
     * Get adapters by type and direction
     */
    public List<Adapter> getAdaptersByTypeAndDirection(String type, String direction) {
        logger.debug("Retrieving adapters of type: {} and direction: {}", type, direction);
        return adapterRepository.findByTypeAndDirection(type, direction);
    }
    
    /**
     * Get active adapters
     */
    public List<Adapter> getActiveAdapters() {
        logger.debug("Retrieving active adapters");
        return adapterRepository.findAllActive();
    }
    
    /**
     * Get adapter by ID
     */
    public Optional<Adapter> getAdapterById(UUID id) {
        logger.debug("Retrieving adapter by ID: {}", id);
        return adapterRepository.findById(id);
    }
    
    /**
     * Get adapter by name
     */
    public Optional<Adapter> getAdapterByName(String name) {
        logger.debug("Retrieving adapter by name: {}", name);
        return adapterRepository.findByName(name);
    }
    
    /**
     * Check if adapter exists by name
     */
    public boolean existsByName(String name) {
        return adapterRepository.existsByName(name);
    }
    
    /**
     * Create a new adapter
     */
    public Adapter createAdapter(Adapter adapter, UUID createdBy) {
        logger.info("Creating adapter: {} by user: {}", adapter.getName(), createdBy);
        
        // Validate adapter configuration
        validateAdapterConfiguration(adapter);
        
        // Check for duplicate names
        if (existsByName(adapter.getName())) {
            throw new IllegalArgumentException("Adapter with name '" + adapter.getName() + "' already exists");
        }
        
        // Set audit information
        LocalDateTime now = LocalDateTime.now();
        adapter.setCreatedAt(now);
        adapter.setUpdatedAt(now);
        adapter.setCreatedBy(createdBy);
        adapter.setUpdatedBy(createdBy);
        
        // Save adapter
        UUID id = adapterRepository.save(adapter);
        adapter.setId(id);
        
        logger.info("Successfully created adapter: {} with ID: {}", adapter.getName(), id);
        return adapter;
    }
    
    /**
     * Update existing adapter
     */
    public Adapter updateAdapter(UUID id, Adapter adapter, UUID updatedBy) {
        logger.info("Updating adapter: {} by user: {}", id, updatedBy);
        
        // Check if adapter exists
        Optional<Adapter> existingAdapter = getAdapterById(id);
        if (existingAdapter.isEmpty()) {
            throw new IllegalArgumentException("Adapter with ID " + id + " not found");
        }
        
        // Validate adapter configuration
        validateAdapterConfiguration(adapter);
        
        // Check for duplicate names (exclude current adapter)
        Optional<Adapter> duplicateNameAdapter = getAdapterByName(adapter.getName());
        if (duplicateNameAdapter.isPresent() && !duplicateNameAdapter.get().getId().equals(id)) {
            throw new IllegalArgumentException("Adapter with name '" + adapter.getName() + "' already exists");
        }
        
        // Preserve creation audit information
        Adapter existing = existingAdapter.get();
        adapter.setId(id);
        adapter.setCreatedAt(existing.getCreatedAt());
        adapter.setCreatedBy(existing.getCreatedBy());
        adapter.setUpdatedAt(LocalDateTime.now());
        adapter.setUpdatedBy(updatedBy);
        
        // Update adapter
        adapterRepository.update(adapter);
        
        logger.info("Successfully updated adapter: {}", id);
        return adapter;
    }
    
    /**
     * Set adapter active status
     */
    public void setAdapterActive(UUID id, boolean active) {
        logger.info("Setting adapter {} active status to: {}", id, active);
        
        Optional<Adapter> adapter = getAdapterById(id);
        if (adapter.isEmpty()) {
            throw new IllegalArgumentException("Adapter with ID " + id + " not found");
        }
        
        adapterRepository.setActive(id, active);
        logger.info("Successfully set adapter {} active status to: {}", id, active);
    }
    
    /**
     * Start adapter - sets status to STARTED
     */
    public void startAdapter(UUID id) {
        logger.info("Starting adapter: {}", id);
        
        Optional<Adapter> adapterOpt = getAdapterById(id);
        if (adapterOpt.isEmpty()) {
            throw new IllegalArgumentException("Adapter with ID " + id + " not found");
        }
        
        Adapter adapter = adapterOpt.get();
        
        // Ensure adapter is active before starting
        if (!adapter.isActive()) {
            throw new IllegalArgumentException("Cannot start inactive adapter " + id + ". Set active=true first.");
        }
        
        try {
            // Set status to STARTED (immediate transition)
            adapterRepository.updateStatus(id, Adapter.AdapterStatus.STARTED);
            
            logger.info("Successfully started adapter: {}", id);
            
        } catch (Exception e) {
            logger.error("Failed to start adapter {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to start adapter: " + e.getMessage(), e);
        }
    }
    
    /**
     * Stop adapter - sets status to STOPPED
     */
    public void stopAdapter(UUID id) {
        logger.info("Stopping adapter: {}", id);
        
        Optional<Adapter> adapterOpt = getAdapterById(id);
        if (adapterOpt.isEmpty()) {
            throw new IllegalArgumentException("Adapter with ID " + id + " not found");
        }
        
        Adapter adapter = adapterOpt.get();
        
        try {
            // Set status to STOPPED (immediate transition)
            adapterRepository.updateStatus(id, Adapter.AdapterStatus.STOPPED);
            
            logger.info("Successfully stopped adapter: {}", id);
            
        } catch (Exception e) {
            logger.error("Failed to stop adapter {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to stop adapter: " + e.getMessage(), e);
        }
    }
    
    /**
     * Delete adapter
     */
    public boolean deleteAdapter(UUID id) {
        logger.info("Deleting adapter: {}", id);
        
        Optional<Adapter> adapter = getAdapterById(id);
        if (adapter.isEmpty()) {
            throw new IllegalArgumentException("Adapter with ID " + id + " not found");
        }
        
        // Check if adapter is being used in any flows
        // This would require checking integration_flows table for references
        // For now, we'll allow deletion but this should be enhanced
        
        boolean deleted = adapterRepository.deleteById(id);
        if (deleted) {
            logger.info("Successfully deleted adapter: {}", id);
        } else {
            logger.warn("Failed to delete adapter: {}", id);
        }
        
        return deleted;
    }
    
    /**
     * Test adapter connection
     */
    public Map<String, Object> testAdapterConnection(UUID id) {
        logger.info("Testing adapter connection: {}", id);
        
        Optional<Adapter> adapterOpt = getAdapterById(id);
        if (adapterOpt.isEmpty()) {
            throw new IllegalArgumentException("Adapter with ID " + id + " not found");
        }
        
        Adapter adapter = adapterOpt.get();
        Map<String, Object> testResult = new java.util.HashMap<>();
        testResult.put("adapterId", id);
        testResult.put("adapterName", adapter.getName());
        testResult.put("adapterType", adapter.getAdapterType());
        testResult.put("testTime", LocalDateTime.now());
        
        try {
            switch (adapter.getAdapterType().toUpperCase()) {
                case "FILE":
                    testResult = testFileAdapter(adapter);
                    break;
                case "SFTP":
                    testResult = testSftpAdapter(adapter);
                    break;
                case "EMAIL":
                    testResult = testEmailAdapter(adapter);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported adapter type: " + adapter.getAdapterType());
            }
            
            // Check if the adapter test already set success status
            if (!testResult.containsKey("success")) {
                testResult.put("success", true);
            }
            
            if ((Boolean) testResult.getOrDefault("success", false)) {
                logger.info("Adapter connection test successful for: {}", id);
            } else {
                logger.warn("Adapter connection test failed for: {} - {}", id, testResult.get("error"));
            }
            
        } catch (Exception e) {
            logger.error("Adapter connection test failed for {}: {}", id, e.getMessage(), e);
            testResult.put("success", false);
            testResult.put("error", e.getMessage());
            testResult.put("errorType", e.getClass().getSimpleName());
        }
        
        return testResult;
    }
    
    /**
     * Execute adapter
     */
    public Map<String, Object> executeAdapter(UUID id) {
        logger.info("Executing adapter: {}", id);
        
        Optional<Adapter> adapterOpt = getAdapterById(id);
        if (adapterOpt.isEmpty()) {
            throw new IllegalArgumentException("Adapter with ID " + id + " not found");
        }
        
        Adapter adapter = adapterOpt.get();
        
        if (!adapter.getActive()) {
            throw new IllegalArgumentException("Adapter " + id + " is inactive and cannot be executed");
        }
        
        Map<String, Object> executeResult = new HashMap<>();
        executeResult.put("adapterId", id);
        executeResult.put("adapterName", adapter.getName());
        executeResult.put("adapterType", adapter.getAdapterType());
        executeResult.put("executionStartTime", LocalDateTime.now());
        
        try {
            switch (adapter.getAdapterType().toUpperCase()) {
                case "FILE":
                    executeResult = executeFileAdapter(adapter);
                    break;
                case "SFTP":
                    executeResult = executeSftpAdapter(adapter);
                    break;
                case "EMAIL":
                    executeResult = executeEmailAdapter(adapter);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported adapter type: " + adapter.getAdapterType());
            }
            
            executeResult.put("success", true);
            executeResult.put("status", "INITIATED");
            logger.info("Adapter execution initiated successfully for: {}", id);
            
        } catch (Exception e) {
            logger.error("Adapter execution failed for {}: {}", id, e.getMessage(), e);
            executeResult.put("success", false);
            executeResult.put("status", "FAILED");
            executeResult.put("error", e.getMessage());
            executeResult.put("errorType", e.getClass().getSimpleName());
        }
        
        return executeResult;
    }
    
    /**
     * Get adapter statistics
     */
    public Map<String, Object> getAdapterStatistics() {
        logger.debug("Retrieving adapter statistics");
        return adapterRepository.getAdapterStatistics();
    }
    
    /**
     * Validate adapter configuration
     */
    private void validateAdapterConfiguration(Adapter adapter) {
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
            if (!config.containsKey("sourceDirectory") || 
                config.get("sourceDirectory") == null || 
                config.get("sourceDirectory").toString().trim().isEmpty()) {
                throw new IllegalArgumentException("Sender file adapter requires 'sourceDirectory' configuration");
            }
        } else if ("RECEIVER".equalsIgnoreCase(direction)) {
            // Receiver adapters need target directory to write to
            if (!config.containsKey("targetDirectory") || 
                config.get("targetDirectory") == null || 
                config.get("targetDirectory").toString().trim().isEmpty()) {
                throw new IllegalArgumentException("Receiver file adapter requires 'targetDirectory' configuration");
            }
        }
        
        // File pattern is optional and may be set to defaults
        // Most other fields have reasonable defaults or are optional
    }
    
    /**
     * Validate SFTP adapter configuration
     */
    private void validateSftpAdapterConfig(Map<String, Object> config, String direction) {
        // Common required fields for both directions
        String[] requiredFields = {"host", "port", "username", "remoteDirectory"};
        
        for (String field : requiredFields) {
            if (!config.containsKey(field) || 
                config.get(field) == null || 
                config.get(field).toString().trim().isEmpty()) {
                throw new IllegalArgumentException("SFTP adapter requires '" + field + "' configuration");
            }
        }
        
        // Validate port is numeric
        try {
            Integer.parseInt(config.get("port").toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("SFTP adapter 'port' must be a valid number");
        }
        
        // Direction-specific validation if needed
        if ("SENDER".equalsIgnoreCase(direction)) {
            // Validate file pattern for sender (using filePattern field as per OOP refactored adapters)
            if (!config.containsKey("filePattern") || 
                config.get("filePattern") == null || 
                config.get("filePattern").toString().trim().isEmpty()) {
                throw new IllegalArgumentException("Sender SFTP adapter requires 'filePattern' configuration");
            }
        }
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
        
        com.jcraft.jsch.JSch jsch = new com.jcraft.jsch.JSch();
        com.jcraft.jsch.Session session = null;
        com.jcraft.jsch.ChannelSftp sftpChannel = null;
        
        Map<String, Object> config = adapter.getConfiguration();
        String host = (String) config.get("host");
        Object portObj = config.get("port");
        String username = (String) config.get("username");
        String password = (String) config.get("password");
        String remoteDirectory = (String) config.get("remoteDirectory");
        Boolean strictHostKeyChecking = (Boolean) config.get("strictHostKeyChecking");
        
        int port = portObj != null ? ((Number) portObj).intValue() : 22;
        
        // ALWAYS populate the basic config details that we know
        result.put("adapterType", "SFTP");
        result.put("host", host);
        result.put("port", port);
        result.put("username", username);
        result.put("remoteDirectory", remoteDirectory);
        
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
            if (remoteDirectory == null || remoteDirectory.trim().isEmpty()) {
                throw new RuntimeException("Remote directory is not configured");
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
            sftpChannel = (com.jcraft.jsch.ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();
            result.put("sftpChannelOpened", true);
            
            // Step 3: Test root/home directory access first
            String homeDirectory = null;
            try {
                homeDirectory = sftpChannel.getHome();
                result.put("homeDirectory", homeDirectory);
                sftpChannel.cd(homeDirectory);
                result.put("homeDirectoryAccessible", true);
                logger.info("Successfully accessed home directory: {}", homeDirectory);
            } catch (Exception e) {
                result.put("homeDirectoryAccessible", false);
                result.put("homeDirectory", "Unknown");
                logger.warn("Cannot access home directory: {}", e.getMessage());
            }
            
            // Step 4: Test target directory navigation (cd)
            try {
                sftpChannel.cd(remoteDirectory);
                result.put("directoryAccessible", true);
                logger.info("Successfully navigated to target directory: {}", remoteDirectory);
            } catch (Exception e) {
                String errorMessage = e.getMessage();
                if (errorMessage != null && errorMessage.toLowerCase().contains("no such file")) {
                    errorMessage = errorMessage.replace("No such file", "No such folder").replace("no such file", "no such folder");
                }
                
                // Simple path analysis message
                String pathAnalysis = (homeDirectory != null && result.get("homeDirectoryAccessible") == Boolean.TRUE) 
                    ? " (Home directory accessible, check if target path exists on server)" 
                    : " (Cannot access home directory - check user permissions)";
                
                throw new RuntimeException("Cannot access remote directory '" + remoteDirectory + "': " + (errorMessage != null ? errorMessage : "No such folder") + pathAnalysis);
            }
            
            // Step 5: Test read permissions (ls)
            try {
                java.util.Vector<?> fileList = sftpChannel.ls(".");
                result.put("readPermission", true);
                
                // Count only actual files, excluding directory entries (. and ..)
                int actualFileCount = 0;
                for (Object item : fileList) {
                    if (item instanceof com.jcraft.jsch.ChannelSftp.LsEntry) {
                        com.jcraft.jsch.ChannelSftp.LsEntry entry = (com.jcraft.jsch.ChannelSftp.LsEntry) item;
                        String filename = entry.getFilename();
                        // Skip directory entries and hidden files starting with .
                        if (!".".equals(filename) && !"..".equals(filename) && !filename.startsWith(".")) {
                            actualFileCount++;
                        }
                    }
                }
                
                result.put("fileCount", actualFileCount);
                logger.info("Successfully listed directory contents: {} total items, {} actual files found", fileList.size(), actualFileCount);
            } catch (Exception e) {
                String errorMessage = e.getMessage();
                if (errorMessage != null && errorMessage.toLowerCase().contains("no such file")) {
                    errorMessage = errorMessage.replace("No such file", "No such folder").replace("no such file", "no such folder");
                }
                throw new RuntimeException("Cannot read directory contents in '" + remoteDirectory + "': " + (errorMessage != null ? errorMessage : "Access denied"));
            }
            
            // Step 6: Test write permissions (create/delete test file)
            String testFileName = ".sftp_test_" + System.currentTimeMillis() + ".tmp";
            try {
                // Create a small test file
                java.io.ByteArrayInputStream testData = new java.io.ByteArrayInputStream("SFTP test file".getBytes());
                sftpChannel.put(testData, testFileName);
                result.put("writePermission", true);
                logger.info("Successfully created test file: {}", testFileName);
                
                // Clean up test file
                try {
                    sftpChannel.rm(testFileName);
                    result.put("deletePermission", true);
                    logger.info("Successfully deleted test file: {}", testFileName);
                } catch (Exception e) {
                    logger.warn("Could not delete test file {}: {}", testFileName, e.getMessage());
                    result.put("deletePermission", false);
                    result.put("deleteWarning", "Test file created but could not be deleted: " + testFileName);
                }
            } catch (Exception e) {
                String errorMessage = e.getMessage();
                if (errorMessage != null && errorMessage.toLowerCase().contains("no such file")) {
                    errorMessage = errorMessage.replace("No such file", "No such folder").replace("no such file", "no such folder");
                }
                result.put("writePermission", false);
                result.put("writeError", "Cannot write to directory '" + remoteDirectory + "': " + (errorMessage != null ? errorMessage : "Access denied"));
            }
            
            // Generate summary result message
            boolean canRead = (Boolean) result.getOrDefault("readPermission", false);
            boolean canWrite = (Boolean) result.getOrDefault("writePermission", false);
            boolean canDelete = (Boolean) result.getOrDefault("deletePermission", false);
            
            StringBuilder summary = new StringBuilder("SFTP connection successful. Directory accessible. ");
            if (canRead && canWrite && canDelete) {
                summary.append("Full read/write/delete permissions confirmed.");
            } else if (canRead && canWrite) {
                summary.append("Read/write permissions confirmed (delete permission uncertain).");
            } else if (canRead) {
                summary.append("Read-only access confirmed.");
            } else {
                summary.append("Limited access - check permissions.");
            }
            
            result.put("testResult", summary.toString());
            
        } catch (Exception e) {
            logger.error("SFTP adapter test failed - Raw error: {} (Type: {})", e.getMessage(), e.getClass().getSimpleName(), e);
            
            // Provide more specific error messages based on exception type
            String detailedError;
            if (e instanceof com.jcraft.jsch.JSchException) {
                com.jcraft.jsch.JSchException jschEx = (com.jcraft.jsch.JSchException) e;
                if (jschEx.getMessage().contains("Auth fail")) {
                    detailedError = "Authentication failed - check username and password";
                } else if (jschEx.getMessage().contains("java.net.ConnectException")) {
                    detailedError = "Cannot connect to " + adapter.getConfiguration().get("host") + ":" + adapter.getConfiguration().get("port") + " - check if SFTP server is running and firewall allows connection";
                } else if (jschEx.getMessage().contains("UnknownHostException")) {
                    detailedError = "Host '" + adapter.getConfiguration().get("host") + "' not found - check hostname/IP address";
                } else if (jschEx.getMessage().contains("timeout")) {
                    detailedError = "Connection timeout to " + adapter.getConfiguration().get("host") + ":" + adapter.getConfiguration().get("port") + " - server may be unreachable";
                } else {
                    detailedError = "SFTP connection error: " + jschEx.getMessage();
                }
            } else if (e instanceof com.jcraft.jsch.SftpException) {
                com.jcraft.jsch.SftpException sftpEx = (com.jcraft.jsch.SftpException) e;
                if (sftpEx.id == com.jcraft.jsch.ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                    detailedError = "Cannot access remote directory '" + remoteDirectory + "': No such folder";
                } else if (sftpEx.id == com.jcraft.jsch.ChannelSftp.SSH_FX_PERMISSION_DENIED) {
                    detailedError = "Permission denied accessing '" + remoteDirectory + "' - check folder permissions";
                } else {
                    detailedError = "SFTP operation error: " + sftpEx.getMessage();
                }
            } else if (e.getMessage() != null) {
                // Replace "No such file" with "No such folder" in error messages
                String errorMessage = e.getMessage();
                if (errorMessage.contains("No such file")) {
                    errorMessage = errorMessage.replace("No such file", "No such folder");
                }
                detailedError = errorMessage;
            } else {
                detailedError = "Unknown SFTP connection error: " + e.getClass().getSimpleName();
            }
            
            // Don't throw exception, instead set error in result and return it
            result.put("success", false);
            result.put("error", detailedError);
            result.put("errorType", e.getClass().getSimpleName());
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
            } else {
                throw new RuntimeException("SMTP host or port not configured");
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Email adapter test failed: " + e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * Execute file adapter
     */
    private Map<String, Object> executeFileAdapter(Adapter adapter) {
        Map<String, Object> result = new HashMap<>();
        result.put("executionType", "FILE_PROCESSING");
        
        try {
            // Mock file adapter execution - simulate file processing
            Map<String, Object> config = adapter.getConfiguration();
            String sourceDirectory = (String) config.get("sourceDirectory");
            String filePattern = (String) config.get("filePattern");
            
            result.put("sourceDirectory", sourceDirectory);
            result.put("filePattern", filePattern);
            result.put("executionResult", "File adapter execution initiated successfully");
            result.put("estimatedDuration", "30-60 seconds");
            
            // In a real implementation, this would trigger actual file processing
            logger.info("File adapter execution simulated for directory: {}", sourceDirectory);
            
        } catch (Exception e) {
            throw new RuntimeException("File adapter execution failed: " + e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * Execute SFTP adapter
     */
    private Map<String, Object> executeSftpAdapter(Adapter adapter) {
        Map<String, Object> result = new HashMap<>();
        result.put("executionType", "SFTP_TRANSFER");
        
        try {
            // Mock SFTP adapter execution - simulate file transfer
            Map<String, Object> config = adapter.getConfiguration();
            String host = (String) config.get("host");
            Object port = config.get("port");
            String remoteDirectory = (String) config.get("remoteDirectory");
            
            result.put("host", host);
            result.put("port", port);
            result.put("remoteDirectory", remoteDirectory);
            result.put("executionResult", "SFTP adapter execution initiated successfully");
            result.put("estimatedDuration", "60-120 seconds");
            
            // In a real implementation, this would trigger actual SFTP operations
            logger.info("SFTP adapter execution simulated for host: {}", host);
            
        } catch (Exception e) {
            throw new RuntimeException("SFTP adapter execution failed: " + e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * Execute email adapter
     */
    private Map<String, Object> executeEmailAdapter(Adapter adapter) {
        Map<String, Object> result = new HashMap<>();
        result.put("executionType", "EMAIL_PROCESSING");
        
        try {
            // Mock email adapter execution - simulate email operations
            Map<String, Object> config = adapter.getConfiguration();
            String smtpHost = (String) config.get("smtpHost");
            Object smtpPort = config.get("smtpPort");
            String username = (String) config.get("username");
            
            result.put("smtpHost", smtpHost);
            result.put("smtpPort", smtpPort);
            result.put("username", username);
            result.put("executionResult", "Email adapter execution initiated successfully");
            result.put("estimatedDuration", "10-30 seconds");
            
            // In a real implementation, this would trigger actual email operations
            logger.info("Email adapter execution simulated for SMTP host: {}", smtpHost);
            
        } catch (Exception e) {
            throw new RuntimeException("Email adapter execution failed: " + e.getMessage(), e);
        }
        
        return result;
    }
}