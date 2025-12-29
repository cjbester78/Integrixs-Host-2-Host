package com.integrixs.adapters.sftp;

import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.SshKey;
import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * SFTP adapter for secure file transfers using SSH key authentication
 * Provides upload/download capabilities with connection pooling and retry logic
 */
public class SftpAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(SftpAdapter.class);
    
    private final Adapter adapter;
    private final SftpAdapterConfig config;
    private final Map<String, ChannelSftp> connectionPool;
    private final Function<String, Optional<SshKey>> sshKeyProvider;
    private JSch jsch;
    private volatile boolean initialized = false;
    
    public SftpAdapter(Adapter adapter, Function<String, Optional<SshKey>> sshKeyProvider) {
        this.adapter = adapter;
        this.config = new SftpAdapterConfig(adapter.getConfiguration());
        this.connectionPool = new ConcurrentHashMap<>();
        this.sshKeyProvider = sshKeyProvider;
        logger.info("Created SFTP adapter for interface: {} - Host: {}", 
                   adapter.getName(), config.getHost());
    }
    
    // Legacy constructor for backward compatibility
    public SftpAdapter(Adapter adapter) {
        this(adapter, keyName -> Optional.empty());
    }
    
    @PostConstruct
    public void initialize() {
        try {
            logger.info("Initializing SFTP adapter: {}", adapter.getName());
            
            // Initialize JSch
            this.jsch = new JSch();
            
            // Add SSH key - first try database, then fallback to file path
            String keyName = config.getString("sshKeyName");
            if (keyName != null && !keyName.isEmpty()) {
                addSshKeyFromDatabase(keyName);
            } else if (config.getPrivateKeyPath() != null && !config.getPrivateKeyPath().isEmpty()) {
                addPrivateKeyFromPath(config.getPrivateKeyPath(), config.getPrivateKeyPassphrase());
            }
            
            // Test connection
            testConnection();
            
            this.initialized = true;
            logger.info("SFTP adapter initialized successfully: {}", adapter.getName());
            
        } catch (Exception e) {
            logger.error("Failed to initialize SFTP adapter: {}", adapter.getName(), e);
            throw new SftpAdapterException("Failed to initialize SFTP adapter", e);
        }
    }
    
    @PreDestroy
    public void cleanup() {
        logger.info("Cleaning up SFTP adapter: {}", adapter.getName());
        
        // Close all connections in pool
        connectionPool.values().forEach(channel -> {
            try {
                if (channel != null && channel.isConnected()) {
                    channel.disconnect();
                }
                Session session = channel != null ? channel.getSession() : null;
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            } catch (Exception e) {
                logger.warn("Error closing SFTP connection", e);
            }
        });
        
        connectionPool.clear();
        this.initialized = false;
        logger.info("SFTP adapter cleanup completed: {}", adapter.getName());
    }
    
    /**
     * Uploads a file to the remote SFTP server
     */
    public SftpOperationResult uploadFile(Path localFilePath, String remoteFilePath) {
        return uploadFile(localFilePath, remoteFilePath, null);
    }
    
    /**
     * Uploads a file with execution context for detailed logging
     */
    public SftpOperationResult uploadFile(Path localFilePath, String remoteFilePath, String executionId) {
        // Set execution context for logging
        if (executionId != null) {
            MDC.put("executionId", executionId);
            MDC.put("adapterId", adapter.getId().toString());
            MDC.put("adapterName", adapter.getName());
            MDC.put("logCategory", "ADAPTER_EXECUTION");
        }
        if (!initialized) {
            throw new SftpAdapterException("SFTP adapter not initialized");
        }
        
        logger.info("=== STARTING RECEIVER SFTP ADAPTER EXECUTION ===");
        logger.info("Adapter: {} (ID: {})", adapter.getName(), adapter.getId());
        logger.info("Direction: RECEIVER");
        logger.info("Host: {}@{}", config.getUsername(), config.getHost());
        logger.info("Local file: {}", localFilePath.toAbsolutePath());
        logger.info("Remote destination: {}", remoteFilePath);
        
        SftpOperationResult result = new SftpOperationResult();
        result.setOperation(SftpOperation.UPLOAD);
        result.setLocalPath(localFilePath);
        result.setRemotePath(remoteFilePath);
        result.setStartTime(LocalDateTime.now());
        
        ChannelSftp sftpChannel = null;
        
        try {
            logger.info("=== STEP 1: SFTP CONNECTION ===");
            // Get SFTP channel
            sftpChannel = getSftpChannel();
            logger.info("✓ SFTP connection established successfully");
            
            logger.info("=== STEP 2: REMOTE DIRECTORY PREPARATION ===");
            String remoteDir = getParentDirectory(remoteFilePath);
            logger.info("Ensuring remote directory exists: {}", remoteDir);
            // Ensure remote directory exists
            ensureRemoteDirectory(sftpChannel, remoteDir);
            logger.info("✓ Remote directory ready: {}", remoteDir);
            
            logger.info("=== STEP 3: FILE TRANSFER ===");
            long fileSize = Files.size(localFilePath);
            logger.info("Uploading file: {} ({} bytes)", localFilePath.getFileName(), fileSize);
            logger.info("Transfer destination: {}", remoteFilePath);
            
            long startTime = System.currentTimeMillis();
            // Upload file
            try (InputStream inputStream = Files.newInputStream(localFilePath)) {
                sftpChannel.put(inputStream, remoteFilePath);
            }
            long transferTime = System.currentTimeMillis() - startTime;
            logger.info("✓ File transfer completed in {}ms", transferTime);
            
            logger.info("=== STEP 4: UPLOAD VERIFICATION ===");
            // Verify upload
            SftpATTRS attrs = sftpChannel.lstat(remoteFilePath);
            long localSize = Files.size(localFilePath);
            long remoteSize = attrs.getSize();
            
            logger.info("Verifying upload:");
            logger.info("  Local file size:  {} bytes", localSize);
            logger.info("  Remote file size: {} bytes", remoteSize);
            
            if (remoteSize == localSize) {
                result.setStatus(SftpOperationStatus.SUCCESS);
                result.setMessage("File uploaded successfully");
                result.setBytesTransferred(localSize);
                logger.info("✓ Upload verification successful - file sizes match");
            } else {
                result.setStatus(SftpOperationStatus.FAILED);
                result.setErrorMessage("File size mismatch after upload");
                logger.error("✗ Upload verification failed - file size mismatch");
            }
            
            logger.info("=== RECEIVER SFTP ADAPTER COMPLETE ===");
            logger.info("✓ File upload completed successfully: {}", localFilePath.getFileName());
            logger.info("✓ Final status: {}", result.getStatus());
            logger.info("✓ Bytes transferred: {}", result.getBytesTransferred());
            logger.info("✓ Duration: {}ms", result.getDurationMs());
            
            if (executionId != null) {
                logger.info("=== END OF EXECUTION FLOW ===");
                logger.info("Flow execution completed successfully");
                logger.info("Execution context: {}", executionId);
            }
            
        } catch (Exception e) {
            logger.error("=== RECEIVER SFTP ADAPTER FAILED ===");
            logger.error("✗ File upload failed: {}", localFilePath.getFileName());
            logger.error("✗ Error: {}", e.getMessage());
            logger.error("✗ Exception type: {}", e.getClass().getSimpleName());
            if (logger.isDebugEnabled()) {
                logger.error("✗ Full stack trace:", e);
            }
            
            result.setStatus(SftpOperationStatus.FAILED);
            result.setErrorMessage("Upload failed: " + e.getMessage());
            result.setException(e);
        } finally {
            result.setEndTime(LocalDateTime.now());
            if (sftpChannel != null) {
                returnSftpChannel(sftpChannel);
            }
            
            // Clear MDC if we set it
            if (executionId != null) {
                MDC.remove("executionId");
                MDC.remove("adapterId");
                MDC.remove("adapterName");
                MDC.remove("logCategory");
            }
        }
        
        return result;
    }
    
    /**
     * Downloads a file from the remote SFTP server
     */
    public SftpOperationResult downloadFile(String remoteFilePath, Path localFilePath) {
        return downloadFile(remoteFilePath, localFilePath, null);
    }
    
    /**
     * Downloads a file with execution context for detailed logging
     */
    public SftpOperationResult downloadFile(String remoteFilePath, Path localFilePath, String executionId) {
        // Set execution context for logging
        if (executionId != null) {
            MDC.put("executionId", executionId);
            MDC.put("adapterId", adapter.getId().toString());
            MDC.put("adapterName", adapter.getName());
            MDC.put("logCategory", "ADAPTER_EXECUTION");
        }
        if (!initialized) {
            throw new SftpAdapterException("SFTP adapter not initialized");
        }
        
        logger.info("=== STARTING SENDER SFTP ADAPTER EXECUTION ===");
        logger.info("Adapter: {} (ID: {})", adapter.getName(), adapter.getId());
        logger.info("Direction: SENDER");
        logger.info("Host: {}@{}", config.getUsername(), config.getHost());
        logger.info("Remote file: {}", remoteFilePath);
        logger.info("Local destination: {}", localFilePath.toAbsolutePath());
        
        SftpOperationResult result = new SftpOperationResult();
        result.setOperation(SftpOperation.DOWNLOAD);
        result.setLocalPath(localFilePath);
        result.setRemotePath(remoteFilePath);
        result.setStartTime(LocalDateTime.now());
        
        ChannelSftp sftpChannel = null;
        
        try {
            logger.info("=== STEP 1: SFTP CONNECTION ===");
            // Get SFTP channel
            sftpChannel = getSftpChannel();
            logger.info("✓ SFTP connection established successfully");
            
            logger.info("=== STEP 2: LOCAL DIRECTORY PREPARATION ===");
            Path localDir = localFilePath.getParent();
            logger.info("Ensuring local directory exists: {}", localDir);
            // Ensure local directory exists
            Files.createDirectories(localDir);
            logger.info("✓ Local directory ready: {}", localDir);
            
            logger.info("=== STEP 3: FILE TRANSFER ===");
            // Check remote file first
            SftpATTRS remoteAttrs = sftpChannel.lstat(remoteFilePath);
            long remoteSize = remoteAttrs.getSize();
            logger.info("Downloading file: {} ({} bytes)", Paths.get(remoteFilePath).getFileName(), remoteSize);
            logger.info("Transfer destination: {}", localFilePath);
            
            long startTime = System.currentTimeMillis();
            // Download file
            try (OutputStream outputStream = Files.newOutputStream(localFilePath)) {
                sftpChannel.get(remoteFilePath, outputStream);
            }
            long transferTime = System.currentTimeMillis() - startTime;
            logger.info("✓ File transfer completed in {}ms", transferTime);
            
            logger.info("=== STEP 4: DOWNLOAD VERIFICATION ===");
            // Verify download
            SftpATTRS attrs = sftpChannel.lstat(remoteFilePath);
            long localSize = Files.size(localFilePath);
            long remoteSizeVerify = attrs.getSize();
            
            logger.info("Verifying download:");
            logger.info("  Remote file size: {} bytes", remoteSizeVerify);
            logger.info("  Local file size:  {} bytes", localSize);
            
            if (remoteSizeVerify == localSize) {
                result.setStatus(SftpOperationStatus.SUCCESS);
                result.setMessage("File downloaded successfully");
                result.setBytesTransferred(localSize);
                logger.info("✓ Download verification successful - file sizes match");
            } else {
                result.setStatus(SftpOperationStatus.FAILED);
                result.setErrorMessage("File size mismatch after download");
                logger.error("✗ Download verification failed - file size mismatch");
            }
            
            logger.info("=== SENDER SFTP ADAPTER COMPLETE ===");
            logger.info("✓ File download completed successfully: {}", Paths.get(remoteFilePath).getFileName());
            logger.info("✓ Final status: {}", result.getStatus());
            logger.info("✓ Bytes transferred: {}", result.getBytesTransferred());
            logger.info("✓ Duration: {}ms", result.getDurationMs());
            
            if (executionId != null) {
                logger.info("=== PASSING DATA TO NEXT FLOW STEP ===");
                logger.info("File downloaded successfully, ready for processing");
                logger.info("Local file: {}", localFilePath);
                logger.info("File size: {} bytes", result.getBytesTransferred());
                logger.info("Execution context: {}", executionId);
            }
            
        } catch (Exception e) {
            logger.error("=== SENDER SFTP ADAPTER FAILED ===");
            logger.error("✗ File download failed: {}", Paths.get(remoteFilePath).getFileName());
            logger.error("✗ Error: {}", e.getMessage());
            logger.error("✗ Exception type: {}", e.getClass().getSimpleName());
            if (logger.isDebugEnabled()) {
                logger.error("✗ Full stack trace:", e);
            }
            
            result.setStatus(SftpOperationStatus.FAILED);
            result.setErrorMessage("Download failed: " + e.getMessage());
            result.setException(e);
        } finally {
            result.setEndTime(LocalDateTime.now());
            if (sftpChannel != null) {
                returnSftpChannel(sftpChannel);
            }
            
            // Clear MDC if we set it
            if (executionId != null) {
                MDC.remove("executionId");
                MDC.remove("adapterId");
                MDC.remove("adapterName");
                MDC.remove("logCategory");
            }
        }
        
        return result;
    }
    
    /**
     * Lists files in a remote directory
     */
    public List<SftpFileInfo> listRemoteFiles(String remoteDirectory) {
        return listRemoteFiles(remoteDirectory, config.getFilePattern());
    }
    
    /**
     * Lists files in a remote directory with pattern matching
     */
    public List<SftpFileInfo> listRemoteFiles(String remoteDirectory, String pattern) {
        if (!initialized) {
            throw new SftpAdapterException("SFTP adapter not initialized");
        }
        
        logger.debug("Listing files in remote directory: {} with pattern: {}", remoteDirectory, pattern);
        
        List<SftpFileInfo> fileInfoList = new ArrayList<>();
        ChannelSftp sftpChannel = null;
        
        try {
            sftpChannel = getSftpChannel();
            
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = sftpChannel.ls(remoteDirectory);
            
            for (ChannelSftp.LsEntry entry : entries) {
                if (!entry.getAttrs().isDir() && 
                    !entry.getFilename().equals(".") && 
                    !entry.getFilename().equals("..")) {
                    
                    if (pattern == null || matchesPattern(entry.getFilename(), pattern)) {
                        SftpFileInfo fileInfo = new SftpFileInfo();
                        fileInfo.setFileName(entry.getFilename());
                        fileInfo.setFullPath(remoteDirectory + "/" + entry.getFilename());
                        fileInfo.setSize(entry.getAttrs().getSize());
                        fileInfo.setModifiedTime(new Date(entry.getAttrs().getMTime() * 1000L));
                        fileInfo.setPermissions(entry.getAttrs().getPermissionsString());
                        
                        fileInfoList.add(fileInfo);
                    }
                }
            }
            
            logger.debug("Found {} files in remote directory: {}", fileInfoList.size(), remoteDirectory);
            
        } catch (Exception e) {
            logger.error("Failed to list remote files in directory: {}", remoteDirectory, e);
            throw new SftpAdapterException("Failed to list remote files", e);
        } finally {
            if (sftpChannel != null) {
                returnSftpChannel(sftpChannel);
            }
        }
        
        return fileInfoList;
    }
    
    /**
     * Tests SFTP connection
     */
    public void testConnection() {
        logger.info("Testing SFTP connection to: {}@{}", config.getUsername(), config.getHost());
        
        Session session = null;
        ChannelSftp sftpChannel = null;
        
        try {
            session = createSession();
            session.connect(config.getSessionTimeout());
            
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect(config.getChannelTimeout());
            
            // Test by listing home directory
            String homeDir = sftpChannel.getHome();
            logger.info("SFTP connection test successful - Home directory: {}", homeDir);
            
        } catch (Exception e) {
            logger.error("SFTP connection test failed", e);
            throw new SftpAdapterException("SFTP connection test failed", e);
        } finally {
            if (sftpChannel != null && sftpChannel.isConnected()) {
                sftpChannel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
    
    // Private helper methods
    
    /**
     * Add SSH key from database by name
     */
    private void addSshKeyFromDatabase(String keyName) throws JSchException, IOException {
        logger.debug("Adding SSH key from database: {}", keyName);
        
        Optional<SshKey> sshKeyOpt = sshKeyProvider.apply(keyName);
        if (sshKeyOpt.isEmpty()) {
            throw new SftpAdapterException("SSH key not found in database: " + keyName);
        }
        
        SshKey sshKey = sshKeyOpt.get();
        if (!sshKey.isActive()) {
            throw new SftpAdapterException("SSH key is inactive: " + keyName);
        }
        
        // Add key directly from memory
        addPrivateKeyFromMemory(sshKey.getPrivateKey(), null, sshKey.getPublicKey());
        logger.info("Successfully loaded SSH key from database: {} ({})", keyName, sshKey.getKeyType());
    }
    
    /**
     * Add SSH key from file path (legacy method)
     */
    private void addPrivateKeyFromPath(String keyPath, String passphrase) throws JSchException, IOException {
        logger.debug("Adding private key from path: {}", keyPath);
        
        if (passphrase != null && !passphrase.isEmpty()) {
            jsch.addIdentity(keyPath, passphrase);
        } else {
            jsch.addIdentity(keyPath);
        }
    }
    
    /**
     * Add SSH key from memory (private key content)
     */
    private void addPrivateKeyFromMemory(String privateKey, String passphrase, String publicKey) throws JSchException {
        logger.debug("Adding SSH key from memory");
        
        byte[] privateKeyBytes = privateKey.getBytes();
        byte[] publicKeyBytes = publicKey != null ? publicKey.getBytes() : null;
        byte[] passphraseBytes = passphrase != null ? passphrase.getBytes() : null;
        
        jsch.addIdentity("database-key", privateKeyBytes, publicKeyBytes, passphraseBytes);
    }
    
    private Session createSession() throws Exception {
        Session session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
        
        // Configure session properties
        Properties sessionProps = new Properties();
        sessionProps.put("StrictHostKeyChecking", config.isStrictHostKeyChecking() ? "yes" : "no");
        sessionProps.put("PreferredAuthentications", "publickey");
        sessionProps.put("ServerAliveInterval", "30");
        sessionProps.put("ServerAliveCountMax", "3");
        
        session.setConfig(sessionProps);
        session.setTimeout(config.getSessionTimeout());
        
        return session;
    }
    
    private ChannelSftp getSftpChannel() {
        String poolKey = Thread.currentThread().getName();
        ChannelSftp channel = connectionPool.get(poolKey);
        
        try {
            if (channel == null || !channel.isConnected() || 
                channel.getSession() == null || !channel.getSession().isConnected()) {
                
                Session session = createSession();
                session.connect(config.getSessionTimeout());
                
                channel = (ChannelSftp) session.openChannel("sftp");
                channel.connect(config.getChannelTimeout());
                
                connectionPool.put(poolKey, channel);
                logger.debug("Created new SFTP channel for thread: {}", poolKey);
            }
        } catch (Exception e) {
            logger.error("Failed to create SFTP channel", e);
            throw new SftpAdapterException("Failed to create SFTP channel", e);
        }
        
        return channel;
    }
    
    private void returnSftpChannel(ChannelSftp channel) {
        // In this implementation, we keep channels in the pool for reuse
        // Channels are only closed during cleanup
    }
    
    private void ensureRemoteDirectory(ChannelSftp sftpChannel, String directory) {
        if (directory == null || directory.isEmpty() || directory.equals("/")) {
            logger.debug("No remote directory creation needed (root or empty path)");
            return;
        }
        
        logger.debug("Checking remote directory: {}", directory);
        
        try {
            SftpATTRS attrs = sftpChannel.lstat(directory);
            if (attrs.isDir()) {
                logger.debug("✓ Remote directory exists: {}", directory);
            } else {
                logger.warn("✗ Path exists but is not a directory: {}", directory);
            }
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                logger.debug("Remote directory does not exist, creating: {}", directory);
                try {
                    // Create parent directories first
                    ensureRemoteDirectory(sftpChannel, getParentDirectory(directory));
                    
                    // Create the directory
                    sftpChannel.mkdir(directory);
                    logger.info("✓ Created remote directory: {}", directory);
                    
                } catch (Exception ex) {
                    logger.error("✗ Failed to create remote directory: {}", directory, ex);
                    throw new SftpAdapterException("Failed to create remote directory: " + directory, ex);
                }
            } else {
                logger.error("✗ Failed to check remote directory: {} ({})", directory, e.getMessage());
                throw new SftpAdapterException("Failed to check remote directory: " + directory, e);
            }
        }
    }
    
    private String getParentDirectory(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "/";
        }
        
        return path.substring(0, lastSlash);
    }
    
    private boolean matchesPattern(String filename, String pattern) {
        if (pattern == null || pattern.isEmpty() || pattern.equals("*")) {
            return true;
        }
        
        // Simple glob pattern matching
        String regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".");
        
        return filename.matches(regex);
    }
    
    // Getters
    public Adapter getAdapter() { return adapter; }
    public SftpAdapterConfig getConfig() { return config; }
    public boolean isInitialized() { return initialized; }
}