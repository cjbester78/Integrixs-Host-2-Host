package com.integrixs.core.util;

import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Properties;

/**
 * Utility class for creating and managing SFTP connections.
 * Centralizes SFTP connection logic to eliminate code duplication
 * and provide consistent authentication handling.
 */
public class SftpConnectionUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(SftpConnectionUtil.class);
    
    /**
     * Create an SFTP connection using the provided configuration.
     * Handles all authentication methods: password, private key, and dual auth.
     * 
     * @param config Adapter configuration containing connection details
     * @return Connected SFTP channel
     * @throws Exception if connection fails
     */
    public static ChannelSftp createSftpConnection(Map<String, Object> config) throws Exception {
        String host = (String) config.get("host");
        Object portObj = config.get("port");
        String username = (String) config.get("username");
        String password = (String) config.get("password");
        
        // Validate required connection parameters
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("SFTP host is required");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("SFTP username is required");
        }
        
        int port = portObj != null ? ((Number) portObj).intValue() : 22;
        
        // Get timeout configurations from adapter config
        Object sessionTimeoutObj = config.get("sessionTimeout");
        int sessionTimeout = sessionTimeoutObj != null ? ((Number) sessionTimeoutObj).intValue() : 60000;
        Object channelTimeoutObj = config.get("channelTimeout");
        int channelTimeout = channelTimeoutObj != null ? ((Number) channelTimeoutObj).intValue() : 60000;
        
        logger.debug("Creating SFTP connection to {}:{} with user: {}", host, port, username);
        
        // Create JSch session
        JSch jsch = new JSch();
        Session session = jsch.getSession(username, host, port);
        
        // Configure authentication
        configureAuthentication(jsch, session, config);
        
        // Set SSH configuration
        Properties sshConfig = new Properties();
        sshConfig.put("StrictHostKeyChecking", "no");
        
        // Set preferred authentication methods based on auth type
        String authType = (String) config.getOrDefault("authType", "PASSWORD");
        if ("SSH_KEY".equals(authType) || "PRIVATE_KEY".equals(authType)) {
            sshConfig.put("PreferredAuthentications", "publickey");
        } else if ("PASSWORD".equals(authType)) {
            sshConfig.put("PreferredAuthentications", "password");
        } else if ("DUAL".equals(authType)) {
            sshConfig.put("PreferredAuthentications", "publickey,password");
        }
        
        session.setConfig(sshConfig);
        session.setTimeout(sessionTimeout);
        session.connect();
        
        logger.debug("SSH session connected successfully");
        
        // Open SFTP channel
        ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
        sftpChannel.connect(channelTimeout);
        
        logger.debug("SFTP channel connected successfully");
        return sftpChannel;
    }
    
    /**
     * Configure authentication based on the adapter configuration.
     * Supports password, private key, and dual authentication methods.
     */
    private static void configureAuthentication(JSch jsch, Session session, Map<String, Object> config) throws JSchException {
        String authType = (String) config.getOrDefault("authType", "PASSWORD");
        String password = (String) config.get("password");
        String privateKeyPath = (String) config.get("privateKeyPath");
        String privateKeyPassphrase = (String) config.get("privateKeyPassphrase");
        
        logger.debug("Configuring authentication type: {}", authType);
        
        switch (authType) {
            case "SSH_KEY":
            case "PRIVATE_KEY":
                // Private key authentication only
                if (privateKeyPath != null && !privateKeyPath.trim().isEmpty()) {
                    if (privateKeyPassphrase != null && !privateKeyPassphrase.trim().isEmpty()) {
                        jsch.addIdentity(privateKeyPath, privateKeyPassphrase);
                        logger.debug("Added private key with passphrase");
                    } else {
                        jsch.addIdentity(privateKeyPath);
                        logger.debug("Added private key without passphrase");
                    }
                } else {
                    throw new IllegalArgumentException("Private key path is required for SSH key authentication");
                }
                break;
                
            case "PASSWORD":
                // Password-only authentication
                if (password != null && !password.trim().isEmpty()) {
                    session.setPassword(password);
                    logger.debug("Password authentication configured");
                } else {
                    throw new IllegalArgumentException("Password is required for password authentication");
                }
                break;
                
            case "DUAL":
                // Dual authentication - both private key and password
                boolean hasPrivateKey = false;
                if (privateKeyPath != null && !privateKeyPath.trim().isEmpty()) {
                    if (privateKeyPassphrase != null && !privateKeyPassphrase.trim().isEmpty()) {
                        jsch.addIdentity(privateKeyPath, privateKeyPassphrase);
                    } else {
                        jsch.addIdentity(privateKeyPath);
                    }
                    hasPrivateKey = true;
                    logger.debug("Dual auth: private key configured");
                }
                
                if (password != null && !password.trim().isEmpty()) {
                    session.setPassword(password);
                    logger.debug("Dual auth: password configured");
                }
                
                if (!hasPrivateKey && (password == null || password.trim().isEmpty())) {
                    throw new IllegalArgumentException("Dual authentication requires either private key or password");
                }
                break;
                
            default:
                throw new IllegalArgumentException("Unsupported authentication type: " + authType);
        }
    }
    
    /**
     * Safely close SFTP connection and session.
     * 
     * @param sftpChannel The SFTP channel to close
     */
    public static void closeSftpConnection(ChannelSftp sftpChannel) {
        if (sftpChannel != null) {
            try {
                if (sftpChannel.isConnected()) {
                    sftpChannel.disconnect();
                    logger.debug("SFTP channel disconnected");
                }
                
                Session session = sftpChannel.getSession();
                if (session != null && session.isConnected()) {
                    session.disconnect();
                    logger.debug("SSH session disconnected");
                }
            } catch (Exception e) {
                logger.warn("Error closing SFTP connection: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Test SFTP connectivity with the given configuration.
     * 
     * @param config Adapter configuration
     * @return true if connection successful, false otherwise
     */
    public static boolean testConnection(Map<String, Object> config) {
        ChannelSftp sftpChannel = null;
        try {
            sftpChannel = createSftpConnection(config);
            
            // Try to list home directory to verify connection
            sftpChannel.ls(".");
            
            logger.info("SFTP connection test successful");
            return true;
            
        } catch (Exception e) {
            logger.error("SFTP connection test failed: {}", e.getMessage());
            return false;
        } finally {
            closeSftpConnection(sftpChannel);
        }
    }
}