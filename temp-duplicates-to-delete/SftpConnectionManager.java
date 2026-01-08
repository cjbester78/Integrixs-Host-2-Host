package com.integrixs.adapters.sftp;

import com.integrixs.core.repository.SystemConfigurationRepository;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing SFTP connections with connection pooling and health monitoring.
 * Implements proper OOP patterns with dependency injection and resource management.
 * Follows SOLID principles with clear separation of concerns and immutable results.
 */
@Service
public class SftpConnectionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(SftpConnectionManager.class);
    
    private final SystemConfigurationRepository configRepository;
    private final ConcurrentHashMap<String, SftpConnectionPool> connectionPools = new ConcurrentHashMap<>();
    private final ScheduledExecutorService poolMaintenanceExecutor;
    
    @Autowired
    public SftpConnectionManager(SystemConfigurationRepository configRepository) {
        this.configRepository = configRepository;
        this.poolMaintenanceExecutor = Executors.newSingleThreadScheduledExecutor();
        
        // Schedule regular pool maintenance
        poolMaintenanceExecutor.scheduleWithFixedDelay(this::performPoolMaintenance, 
                                                      30, 30, TimeUnit.SECONDS);
        
        logger.info("SFTP Connection Manager initialized with pool maintenance");
    }
    
    /**
     * Get a connection from the pool or create a new one.
     * 
     * @param connectionConfig SFTP connection configuration
     * @return immutable connection result
     */
    public SftpConnectionResult getConnection(Map<String, Object> connectionConfig) {
        try {
            String connectionKey = generateConnectionKey(connectionConfig);
            SftpConnectionPool pool = connectionPools.computeIfAbsent(connectionKey, 
                k -> new SftpConnectionPool(connectionConfig, getPoolConfiguration()));
            
            SftpConnection connection = pool.borrowConnection();
            
            if (connection != null && connection.isValid()) {
                logger.debug("Retrieved valid SFTP connection from pool for: {}", connectionKey);
                return SftpConnectionResult.success(connection, "Connection retrieved from pool");
            } else {
                // Pool returned invalid connection, create new one
                connection = createNewConnection(connectionConfig);
                return SftpConnectionResult.success(connection, "New connection created");
            }
            
        } catch (Exception e) {
            logger.error("Failed to get SFTP connection: {}", e.getMessage(), e);
            return SftpConnectionResult.failure("Failed to get connection: " + e.getMessage(), e);
        }
    }
    
    /**
     * Return a connection to the pool.
     * 
     * @param connection the connection to return
     * @param connectionConfig the original connection configuration  
     */
    public void returnConnection(SftpConnection connection, Map<String, Object> connectionConfig) {
        if (connection == null) return;
        
        try {
            String connectionKey = generateConnectionKey(connectionConfig);
            SftpConnectionPool pool = connectionPools.get(connectionKey);
            
            if (pool != null) {
                pool.returnConnection(connection);
                logger.debug("Returned SFTP connection to pool for: {}", connectionKey);
            } else {
                // Pool doesn't exist, close the connection
                connection.close();
                logger.debug("Closed connection as pool no longer exists for: {}", connectionKey);
            }
            
        } catch (Exception e) {
            logger.warn("Error returning connection to pool: {}", e.getMessage(), e);
            try {
                connection.close();
            } catch (Exception closeException) {
                logger.warn("Error closing connection during return: {}", closeException.getMessage());
            }
        }
    }
    
    /**
     * Test connectivity with given configuration.
     * 
     * @param connectionConfig SFTP connection configuration
     * @return immutable test result
     */
    public SftpConnectionTestResult testConnection(Map<String, Object> connectionConfig) {
        LocalDateTime testStartTime = LocalDateTime.now();
        
        try {
            SftpConnection testConnection = createNewConnection(connectionConfig);
            
            // Perform basic connectivity test
            testConnection.getChannel().ls(".");
            
            // Test successful
            testConnection.close();
            
            long testDurationMs = java.time.Duration.between(testStartTime, LocalDateTime.now()).toMillis();
            
            return SftpConnectionTestResult.success(testDurationMs, "Connection test successful");
            
        } catch (Exception e) {
            long testDurationMs = java.time.Duration.between(testStartTime, LocalDateTime.now()).toMillis();
            logger.error("SFTP connection test failed: {}", e.getMessage(), e);
            return SftpConnectionTestResult.failure(testDurationMs, "Connection test failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get connection pool statistics.
     * 
     * @return immutable pool statistics
     */
    public SftpPoolStatistics getPoolStatistics() {
        int totalPools = connectionPools.size();
        int totalConnections = 0;
        int activeConnections = 0;
        int idleConnections = 0;
        
        for (SftpConnectionPool pool : connectionPools.values()) {
            SftpConnectionPool.PoolStats stats = pool.getPoolStatistics();
            totalConnections += stats.getTotalConnections();
            activeConnections += stats.getActiveConnections();
            idleConnections += stats.getIdleConnections();
        }
        
        return new SftpPoolStatistics(totalPools, totalConnections, activeConnections, 
                                    idleConnections, LocalDateTime.now());
    }
    
    /**
     * Shutdown the connection manager and clean up resources.
     */
    public void shutdown() {
        logger.info("Shutting down SFTP Connection Manager");
        
        // Close all pools
        connectionPools.values().forEach(SftpConnectionPool::close);
        connectionPools.clear();
        
        // Shutdown maintenance executor
        poolMaintenanceExecutor.shutdown();
        try {
            if (!poolMaintenanceExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                poolMaintenanceExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            poolMaintenanceExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("SFTP Connection Manager shutdown complete");
    }
    
    /**
     * Create a new SFTP connection.
     */
    private SftpConnection createNewConnection(Map<String, Object> config) throws Exception {
        String host = (String) config.get("host");
        Object portObj = config.get("port");
        String username = (String) config.get("username");
        
        // Validate required parameters
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("SFTP host is required");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("SFTP username is required");
        }
        
        int port = portObj != null ? ((Number) portObj).intValue() : 22;
        
        // Get timeouts from configuration or database
        int sessionTimeout = getConnectionTimeout("sftp.connection.session-timeout", 60000);
        int channelTimeout = getConnectionTimeout("sftp.connection.channel-timeout", 60000);
        
        logger.debug("Creating new SFTP connection to {}:{} with user: {}", host, port, username);
        
        // Create JSch session
        JSch jsch = new JSch();
        Session session = jsch.getSession(username, host, port);
        
        // Configure authentication
        configureAuthentication(jsch, session, config);
        
        // Set SSH configuration
        Properties sshConfig = new Properties();
        sshConfig.put("StrictHostKeyChecking", "no");
        
        // Configure authentication methods
        String authType = (String) config.getOrDefault("authType", "PASSWORD");
        configureAuthenticationMethods(sshConfig, authType);
        
        session.setConfig(sshConfig);
        session.setTimeout(sessionTimeout);
        session.connect();
        
        logger.debug("SSH session connected successfully");
        
        // Open SFTP channel
        ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
        sftpChannel.connect(channelTimeout);
        
        logger.debug("SFTP channel connected successfully");
        
        return new SftpConnection(sftpChannel, session, LocalDateTime.now());
    }
    
    /**
     * Configure authentication based on configuration.
     */
    private void configureAuthentication(JSch jsch, Session session, Map<String, Object> config) throws JSchException {
        String authType = (String) config.getOrDefault("authType", "PASSWORD");
        String password = (String) config.get("password");
        String privateKeyPath = (String) config.get("privateKeyPath");
        String privateKeyPassphrase = (String) config.get("privateKeyPassphrase");
        
        logger.debug("Configuring authentication type: {}", authType);
        
        switch (authType) {
            case "SSH_KEY":
            case "PRIVATE_KEY":
                if (privateKeyPath != null && !privateKeyPath.trim().isEmpty()) {
                    if (privateKeyPassphrase != null && !privateKeyPassphrase.trim().isEmpty()) {
                        jsch.addIdentity(privateKeyPath, privateKeyPassphrase);
                    } else {
                        jsch.addIdentity(privateKeyPath);
                    }
                    logger.debug("Private key authentication configured");
                } else {
                    throw new IllegalArgumentException("Private key path is required for SSH key authentication");
                }
                break;
                
            case "PASSWORD":
                if (password != null && !password.trim().isEmpty()) {
                    session.setPassword(password);
                    logger.debug("Password authentication configured");
                } else {
                    throw new IllegalArgumentException("Password is required for password authentication");
                }
                break;
                
            case "DUAL":
                boolean hasAuth = false;
                if (privateKeyPath != null && !privateKeyPath.trim().isEmpty()) {
                    if (privateKeyPassphrase != null && !privateKeyPassphrase.trim().isEmpty()) {
                        jsch.addIdentity(privateKeyPath, privateKeyPassphrase);
                    } else {
                        jsch.addIdentity(privateKeyPath);
                    }
                    hasAuth = true;
                    logger.debug("Dual auth: private key configured");
                }
                
                if (password != null && !password.trim().isEmpty()) {
                    session.setPassword(password);
                    hasAuth = true;
                    logger.debug("Dual auth: password configured");
                }
                
                if (!hasAuth) {
                    throw new IllegalArgumentException("Dual authentication requires either private key or password");
                }
                break;
                
            default:
                throw new IllegalArgumentException("Unsupported authentication type: " + authType);
        }
    }
    
    /**
     * Configure SSH authentication methods.
     */
    private void configureAuthenticationMethods(Properties sshConfig, String authType) {
        switch (authType) {
            case "SSH_KEY":
            case "PRIVATE_KEY":
                sshConfig.put("PreferredAuthentications", "publickey");
                break;
            case "PASSWORD":
                sshConfig.put("PreferredAuthentications", "password");
                break;
            case "DUAL":
                sshConfig.put("PreferredAuthentications", "publickey,password");
                break;
        }
    }
    
    /**
     * Generate unique key for connection pooling.
     */
    private String generateConnectionKey(Map<String, Object> config) {
        String host = (String) config.get("host");
        Object portObj = config.get("port");
        String username = (String) config.get("username");
        String authType = (String) config.getOrDefault("authType", "PASSWORD");
        
        int port = portObj != null ? ((Number) portObj).intValue() : 22;
        
        return String.format("%s:%d@%s[%s]", username, port, host, authType);
    }
    
    /**
     * Get pool configuration from database or defaults.
     */
    private SftpConnectionPool.PoolConfiguration getPoolConfiguration() {
        int maxPoolSize = getIntegerConfig("sftp.connection.pool.max-size", 10);
        int minPoolSize = getIntegerConfig("sftp.connection.pool.min-size", 2);
        long maxIdleTimeMs = getLongConfig("sftp.connection.pool.max-idle-time-ms", 300000L); // 5 minutes
        long connectionMaxAgeMs = getLongConfig("sftp.connection.pool.max-age-ms", 3600000L); // 1 hour
        
        return new SftpConnectionPool.PoolConfiguration(maxPoolSize, minPoolSize, 
                                                       maxIdleTimeMs, connectionMaxAgeMs);
    }
    
    /**
     * Get connection timeout from database configuration.
     */
    private int getConnectionTimeout(String configKey, int defaultValue) {
        try {
            return configRepository.getIntegerValue(configKey, defaultValue);
        } catch (Exception e) {
            logger.debug("Using default timeout for {}: {}", configKey, e.getMessage());
            return defaultValue;
        }
    }
    
    /**
     * Get integer configuration value from database.
     */
    private int getIntegerConfig(String configKey, int defaultValue) {
        try {
            return configRepository.getIntegerValue(configKey, defaultValue);
        } catch (Exception e) {
            logger.debug("Using default value for {}: {}", configKey, e.getMessage());
            return defaultValue;
        }
    }
    
    /**
     * Get long configuration value from database.
     */
    private long getLongConfig(String configKey, long defaultValue) {
        try {
            return configRepository.getIntegerValue(configKey, (int) defaultValue).longValue();
        } catch (Exception e) {
            logger.debug("Using default value for {}: {}", configKey, e.getMessage());
            return defaultValue;
        }
    }
    
    /**
     * Perform regular pool maintenance.
     */
    private void performPoolMaintenance() {
        try {
            logger.debug("Performing SFTP connection pool maintenance");
            
            connectionPools.entrySet().removeIf(entry -> {
                SftpConnectionPool pool = entry.getValue();
                pool.performMaintenance();
                
                // Remove empty pools that have been idle
                if (pool.isEmpty() && pool.isIdleForRemoval()) {
                    logger.debug("Removing idle empty pool: {}", entry.getKey());
                    pool.close();
                    return true;
                }
                return false;
            });
            
            logger.debug("Pool maintenance completed. Active pools: {}", connectionPools.size());
            
        } catch (Exception e) {
            logger.error("Error during pool maintenance: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Immutable SFTP connection result.
     */
    public static class SftpConnectionResult {
        private final boolean successful;
        private final Optional<SftpConnection> connection;
        private final String message;
        private final Optional<Exception> exception;
        
        private SftpConnectionResult(boolean successful, SftpConnection connection, 
                                   String message, Exception exception) {
            this.successful = successful;
            this.connection = Optional.ofNullable(connection);
            this.message = message;
            this.exception = Optional.ofNullable(exception);
        }
        
        public boolean isSuccessful() { return successful; }
        public Optional<SftpConnection> getConnection() { return connection; }
        public String getMessage() { return message; }
        public Optional<Exception> getException() { return exception; }
        
        public static SftpConnectionResult success(SftpConnection connection, String message) {
            return new SftpConnectionResult(true, connection, message, null);
        }
        
        public static SftpConnectionResult failure(String message, Exception exception) {
            return new SftpConnectionResult(false, null, message, exception);
        }
    }
    
    /**
     * Immutable SFTP connection test result.
     */
    public static class SftpConnectionTestResult {
        private final boolean successful;
        private final long testDurationMs;
        private final String message;
        private final Optional<Exception> exception;
        
        private SftpConnectionTestResult(boolean successful, long testDurationMs, 
                                       String message, Exception exception) {
            this.successful = successful;
            this.testDurationMs = testDurationMs;
            this.message = message;
            this.exception = Optional.ofNullable(exception);
        }
        
        public boolean isSuccessful() { return successful; }
        public long getTestDurationMs() { return testDurationMs; }
        public String getMessage() { return message; }
        public Optional<Exception> getException() { return exception; }
        
        public static SftpConnectionTestResult success(long testDurationMs, String message) {
            return new SftpConnectionTestResult(true, testDurationMs, message, null);
        }
        
        public static SftpConnectionTestResult failure(long testDurationMs, String message, Exception exception) {
            return new SftpConnectionTestResult(false, testDurationMs, message, exception);
        }
    }
    
    /**
     * Immutable SFTP pool statistics.
     */
    public static class SftpPoolStatistics {
        private final int totalPools;
        private final int totalConnections;
        private final int activeConnections;
        private final int idleConnections;
        private final LocalDateTime statisticsTime;
        
        public SftpPoolStatistics(int totalPools, int totalConnections, int activeConnections,
                                int idleConnections, LocalDateTime statisticsTime) {
            this.totalPools = totalPools;
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.idleConnections = idleConnections;
            this.statisticsTime = statisticsTime;
        }
        
        public int getTotalPools() { return totalPools; }
        public int getTotalConnections() { return totalConnections; }
        public int getActiveConnections() { return activeConnections; }
        public int getIdleConnections() { return idleConnections; }
        public LocalDateTime getStatisticsTime() { return statisticsTime; }
        
        public double getUtilizationRate() {
            return totalConnections > 0 ? (double) activeConnections / totalConnections * 100.0 : 0.0;
        }
    }
}