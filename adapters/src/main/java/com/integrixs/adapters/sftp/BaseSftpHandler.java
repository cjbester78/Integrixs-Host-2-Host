package com.integrixs.adapters.sftp;

import com.integrixs.core.config.ConfigurationException;
import com.integrixs.core.config.ConfigurationManager;
import com.integrixs.core.logging.EnhancedLogger;
import com.integrixs.shared.constants.H2HConstants;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 * Base class for SFTP operations with common connection and configuration handling
 */
public abstract class BaseSftpHandler {
    
    @Autowired
    protected ConfigurationManager configManager;
    
    protected final EnhancedLogger logger;
    protected final String bankName;
    
    protected Properties config;
    protected Session jschSession;
    protected ChannelSftp channelSftp;
    
    // Configuration properties
    protected String host;
    protected int port;
    protected String username;
    protected String sshKeyPath;
    protected int sessionTimeout;
    protected int channelTimeout;
    protected String logsDir;
    
    public BaseSftpHandler(String bankName, ConfigurationManager configManager) {
        this.bankName = bankName;
        this.configManager = configManager;
        this.logger = EnhancedLogger.getLogger(bankName + "SftpHandler");
    }
    
    protected void initializeConfiguration(String configFileName) throws SftpOperationException {
        try {
            this.config = configManager.loadConfiguration(configFileName);
            loadConfigurationProperties();
        } catch (ConfigurationException e) {
            throw new SftpOperationException("Failed to initialize configuration: " + configFileName, e);
        }
    }
    
    private void loadConfigurationProperties() {
        host = config.getProperty("host");
        port = Integer.parseInt(config.getProperty("port", H2HConstants.DEFAULT_SFTP_PORT));
        username = config.getProperty("username");
        sshKeyPath = configManager.resolvePath(config.getProperty("pk_alias"));
        sessionTimeout = Integer.parseInt(config.getProperty("session_timeout", String.valueOf(H2HConstants.DEFAULT_SESSION_TIMEOUT)));
        channelTimeout = Integer.parseInt(config.getProperty("channel_timeout", String.valueOf(H2HConstants.DEFAULT_CHANNEL_TIMEOUT)));
        logsDir = configManager.resolvePath(config.getProperty("logsDir", H2HConstants.DEFAULT_LOGS_DIR));
    }
    
    protected void connectToSftp() throws SftpOperationException {
        int retryCount = 0;
        Exception lastException = null;
        
        while (retryCount < H2HConstants.DEFAULT_RETRY_COUNT) {
            try {
                logger.info("Attempting SFTP connection (attempt {}/{})", retryCount + 1, H2HConstants.DEFAULT_RETRY_COUNT);
                
                JSch jsch = new JSch();
                jschSession = jsch.getSession(username, host, port);
                jschSession.setConfig("StrictHostKeyChecking", "no");
                
                // Validate SSH key exists
                if (!Files.exists(Paths.get(sshKeyPath))) {
                    throw new SftpOperationException("SSH key file not found: " + sshKeyPath);
                }
                
                jsch.addIdentity(sshKeyPath);
                logger.info("SSH private key loaded: {}", sshKeyPath);
                
                logger.info("Connecting to server: {}:{} with username: {}", host, port, username);
                jschSession.connect(sessionTimeout);
                logger.info("SSH session connected");
                
                Channel channel = jschSession.openChannel("sftp");
                channel.connect(channelTimeout);
                channelSftp = (ChannelSftp) channel;
                logger.info("SFTP channel connected");
                
                // Test connection
                if (!channelSftp.isConnected()) {
                    throw new SftpOperationException("SFTP channel failed to connect");
                }
                
                logger.logConnectionEvent("ESTABLISHED", String.format("Connected to %s:%d", host, port));
                return;
                
            } catch (JSchException | SftpOperationException e) {
                lastException = e;
                retryCount++;
                logger.warn("SFTP connection attempt {} failed: {}", retryCount, e.getMessage());
                
                // Clean up failed connection
                cleanup();
                
                if (retryCount < H2HConstants.DEFAULT_RETRY_COUNT) {
                    try {
                        logger.info("Waiting {}ms before retry...", H2HConstants.DEFAULT_RETRY_DELAY_MS);
                        Thread.sleep(H2HConstants.DEFAULT_RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SftpOperationException("Connection retry interrupted", ie);
                    }
                }
            }
        }
        
        throw new SftpOperationException("Failed to connect to SFTP after " + H2HConstants.DEFAULT_RETRY_COUNT + " attempts", lastException);
    }
    
    protected void ensureDirectoryExists(String dirPath) throws SftpOperationException {
        try {
            String resolvedPath = configManager.resolvePath(dirPath);
            configManager.createDirectoryIfNotExists(resolvedPath);
        } catch (IOException e) {
            throw new SftpOperationException("Failed to create directory: " + dirPath, e);
        }
    }
    
    protected void cleanup() {
        if (channelSftp != null && channelSftp.isConnected()) {
            try {
                channelSftp.exit();
                logger.debug("SFTP channel closed");
            } catch (Exception e) {
                logger.warn("Error closing SFTP channel: {}", e.getMessage());
            }
            channelSftp = null;
        }
        
        if (jschSession != null && jschSession.isConnected()) {
            try {
                jschSession.disconnect();
                logger.debug("SSH session closed");
            } catch (Exception e) {
                logger.warn("Error closing SSH session: {}", e.getMessage());
            }
            jschSession = null;
        }
    }
    
    protected int getIntProperty(String key, int defaultValue) {
        String value = config.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for property {}: {}. Using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }
    
    protected String getStringProperty(String key, String defaultValue) {
        String value = config.getProperty(key, defaultValue);
        return configManager.resolvePath(value);
    }
    
    protected void logOperationStart(String operation) {
        logger.logOperationStart(operation, String.format("Bank: %s", bankName));
    }
    
    protected void logOperationEnd(String operation) {
        logger.logOperationEnd(operation, String.format("Bank: %s", bankName));
    }
    
    protected void logOperationEnd(String operation, long durationMs) {
        logger.logOperationEnd(operation, String.format("Bank: %s", bankName), durationMs);
    }
    
    protected void logFileCount(int count, String operation) {
        if (count == 0) {
            logger.info("No files found for {}", operation);
        } else if (count == 1) {
            logger.info("{} completed successfully: {} file processed", operation, count);
        } else {
            logger.info("{} completed successfully: {} files processed", operation, count);
        }
    }
    
    protected void logDirectoryInfo(String operation, String localDir, String remoteDir, String archiveDir) {
        logger.info("=== {} Directory Configuration ===", operation);
        if (localDir != null && !localDir.isEmpty()) {
            logger.info("Local directory: {}", localDir);
        }
        if (remoteDir != null && !remoteDir.isEmpty()) {
            logger.info("Remote directory: {}", remoteDir);
        }
        if (archiveDir != null && !archiveDir.isEmpty()) {
            logger.info("Archive directory: {}", archiveDir);
        }
        logger.info("============================================");
    }
    
    protected String formatCurrentTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
    
    // Abstract methods that subclasses must implement
    public abstract void execute() throws SftpOperationException;
    
    // Getters for configuration access
    public String getBankName() { return bankName; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getUsername() { return username; }
}