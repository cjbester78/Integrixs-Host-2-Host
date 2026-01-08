package com.integrixs.core.adapter.sftp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Connection pool for SFTP connections with lifecycle management.
 * Implements proper pooling patterns with size limits, idle timeout, and health monitoring.
 * Follows OOP principles with thread-safe operations and resource management.
 */
public class SftpConnectionPool {
    
    private static final Logger logger = LoggerFactory.getLogger(SftpConnectionPool.class);
    
    private final Map<String, Object> connectionConfig;
    private final PoolConfiguration poolConfig;
    private final BlockingQueue<SftpConnection> availableConnections;
    private final Map<SftpConnection, LocalDateTime> borrowedConnections;
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final ReentrantLock poolLock = new ReentrantLock();
    private final LocalDateTime poolCreatedTime;
    private volatile LocalDateTime lastMaintenanceTime;
    private volatile boolean closed = false;
    
    public SftpConnectionPool(Map<String, Object> connectionConfig, PoolConfiguration poolConfig) {
        this.connectionConfig = Map.copyOf(connectionConfig);
        this.poolConfig = poolConfig;
        this.availableConnections = new LinkedBlockingQueue<>();
        this.borrowedConnections = new ConcurrentHashMap<>();
        this.poolCreatedTime = LocalDateTime.now();
        this.lastMaintenanceTime = poolCreatedTime;
        
        logger.debug("Created SFTP connection pool with max size: {}, min size: {}", 
                    poolConfig.maxPoolSize, poolConfig.minPoolSize);
    }
    
    /**
     * Borrow a connection from the pool.
     * 
     * @return a valid SFTP connection or null if none available
     */
    public SftpConnection borrowConnection() {
        if (closed) {
            logger.debug("Attempt to borrow from closed pool");
            return null;
        }
        
        // Try to get connection from available pool
        SftpConnection connection = availableConnections.poll();
        
        if (connection != null) {
            if (connection.isValid() && !isConnectionExpired(connection)) {
                borrowedConnections.put(connection, LocalDateTime.now());
                logger.debug("Borrowed existing connection from pool. Pool size: {}", availableConnections.size());
                return connection;
            } else {
                // Connection is invalid or expired, discard it
                logger.debug("Discarding invalid/expired connection from pool");
                connection.close();
                totalConnections.decrementAndGet();
            }
        }
        
        // No valid connection available, try to create a new one
        if (totalConnections.get() < poolConfig.maxPoolSize) {
            try {
                connection = createNewConnection();
                if (connection != null) {
                    totalConnections.incrementAndGet();
                    borrowedConnections.put(connection, LocalDateTime.now());
                    logger.debug("Created new connection for pool. Total connections: {}", totalConnections.get());
                    return connection;
                }
            } catch (Exception e) {
                logger.error("Failed to create new connection: {}", e.getMessage(), e);
            }
        } else {
            logger.debug("Pool at maximum size ({}), waiting for available connection", poolConfig.maxPoolSize);
            
            // Try to wait for a connection to become available
            try {
                connection = availableConnections.poll(5, TimeUnit.SECONDS);
                if (connection != null && connection.isValid() && !isConnectionExpired(connection)) {
                    borrowedConnections.put(connection, LocalDateTime.now());
                    return connection;
                } else if (connection != null) {
                    connection.close();
                    totalConnections.decrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.debug("Interrupted while waiting for connection");
            }
        }
        
        return null;
    }
    
    /**
     * Return a connection to the pool.
     * 
     * @param connection the connection to return
     */
    public void returnConnection(SftpConnection connection) {
        if (connection == null || closed) {
            return;
        }
        
        // Remove from borrowed connections
        borrowedConnections.remove(connection);
        
        if (connection.isValid() && !isConnectionExpired(connection)) {
            if (availableConnections.size() < poolConfig.maxPoolSize) {
                availableConnections.offer(connection);
                logger.debug("Returned connection to pool. Pool size: {}", availableConnections.size());
            } else {
                // Pool is full, close the connection
                logger.debug("Pool full, closing returned connection");
                connection.close();
                totalConnections.decrementAndGet();
            }
        } else {
            // Connection is invalid or expired, close it
            logger.debug("Closing invalid/expired returned connection");
            connection.close();
            totalConnections.decrementAndGet();
        }
    }
    
    /**
     * Perform pool maintenance operations.
     */
    public void performMaintenance() {
        if (closed) {
            return;
        }
        
        poolLock.lock();
        try {
            logger.debug("Performing pool maintenance");
            
            // Remove expired and invalid connections from available pool
            int removedConnections = 0;
            SftpConnection[] connectionsArray = availableConnections.toArray(new SftpConnection[0]);
            
            for (SftpConnection connection : connectionsArray) {
                if (!connection.isValid() || isConnectionExpired(connection)) {
                    if (availableConnections.remove(connection)) {
                        connection.close();
                        totalConnections.decrementAndGet();
                        removedConnections++;
                    }
                }
            }
            
            if (removedConnections > 0) {
                logger.debug("Removed {} expired/invalid connections during maintenance", removedConnections);
            }
            
            // Check for borrowed connections that have been out too long
            LocalDateTime cutoffTime = LocalDateTime.now().minus(
                java.time.Duration.ofMillis(poolConfig.maxIdleTimeMs * 2)); // Double idle time for borrowed
            
            int expiredBorrowedCount = 0;
            for (Map.Entry<SftpConnection, LocalDateTime> entry : borrowedConnections.entrySet()) {
                if (entry.getValue().isBefore(cutoffTime)) {
                    logger.warn("Connection borrowed for too long: {}", entry.getKey().getConnectionInfo());
                    expiredBorrowedCount++;
                }
            }
            
            if (expiredBorrowedCount > 0) {
                logger.warn("Found {} connections borrowed for excessive time", expiredBorrowedCount);
            }
            
            // Ensure minimum pool size
            ensureMinimumPoolSize();
            
            lastMaintenanceTime = LocalDateTime.now();
            logger.debug("Pool maintenance completed. Available: {}, Borrowed: {}, Total: {}", 
                        availableConnections.size(), borrowedConnections.size(), totalConnections.get());
            
        } finally {
            poolLock.unlock();
        }
    }
    
    /**
     * Get pool statistics.
     * 
     * @return immutable pool statistics
     */
    public PoolStats getPoolStatistics() {
        return new PoolStats(
            totalConnections.get(),
            borrowedConnections.size(),
            availableConnections.size(),
            poolCreatedTime,
            lastMaintenanceTime
        );
    }
    
    /**
     * Check if pool is empty.
     * 
     * @return true if pool has no connections
     */
    public boolean isEmpty() {
        return totalConnections.get() == 0;
    }
    
    /**
     * Check if pool is idle and can be removed.
     * 
     * @return true if pool has been idle for removal time
     */
    public boolean isIdleForRemoval() {
        if (!isEmpty()) {
            return false;
        }
        
        // Pool is idle if it's empty and hasn't been used for maintenance for a while
        long idleTimeMs = java.time.Duration.between(lastMaintenanceTime, LocalDateTime.now()).toMillis();
        return idleTimeMs > poolConfig.maxIdleTimeMs * 3; // 3x idle time before pool removal
    }
    
    /**
     * Close the pool and all connections.
     */
    public void close() {
        if (closed) {
            return;
        }
        
        poolLock.lock();
        try {
            closed = true;
            
            logger.debug("Closing SFTP connection pool");
            
            // Close all available connections
            SftpConnection connection;
            while ((connection = availableConnections.poll()) != null) {
                connection.close();
            }
            
            // Close all borrowed connections
            for (SftpConnection borrowedConnection : borrowedConnections.keySet()) {
                borrowedConnection.close();
            }
            borrowedConnections.clear();
            
            totalConnections.set(0);
            
            logger.debug("SFTP connection pool closed");
            
        } finally {
            poolLock.unlock();
        }
    }
    
    /**
     * Create a new connection for the pool.
     */
    private SftpConnection createNewConnection() throws Exception {
        // This would normally call SftpConnectionManager.createNewConnection()
        // For now, return null to avoid circular dependency
        // The actual implementation will be injected through the factory pattern
        throw new UnsupportedOperationException("Connection creation must be implemented by connection factory");
    }
    
    /**
     * Check if a connection is expired based on age or idle time.
     */
    private boolean isConnectionExpired(SftpConnection connection) {
        return connection.isOlderThan(poolConfig.connectionMaxAgeMs) || 
               connection.isIdleFor(poolConfig.maxIdleTimeMs);
    }
    
    /**
     * Ensure minimum pool size is maintained.
     */
    private void ensureMinimumPoolSize() {
        int currentTotal = totalConnections.get();
        int currentAvailable = availableConnections.size();
        
        if (currentTotal < poolConfig.minPoolSize) {
            int connectionsToCreate = Math.min(
                poolConfig.minPoolSize - currentTotal,
                poolConfig.maxPoolSize - currentTotal
            );
            
            for (int i = 0; i < connectionsToCreate; i++) {
                try {
                    SftpConnection connection = createNewConnection();
                    if (connection != null) {
                        availableConnections.offer(connection);
                        totalConnections.incrementAndGet();
                        logger.debug("Created connection to maintain minimum pool size");
                    }
                } catch (Exception e) {
                    logger.warn("Failed to create connection for minimum pool size: {}", e.getMessage());
                    break; // Stop trying if creation fails
                }
            }
        }
    }
    
    /**
     * Pool configuration class.
     */
    public static class PoolConfiguration {
        private final int maxPoolSize;
        private final int minPoolSize;
        private final long maxIdleTimeMs;
        private final long connectionMaxAgeMs;
        
        public PoolConfiguration(int maxPoolSize, int minPoolSize, 
                               long maxIdleTimeMs, long connectionMaxAgeMs) {
            if (maxPoolSize < 1) throw new IllegalArgumentException("Max pool size must be >= 1");
            if (minPoolSize < 0) throw new IllegalArgumentException("Min pool size must be >= 0");
            if (minPoolSize > maxPoolSize) throw new IllegalArgumentException("Min pool size cannot exceed max pool size");
            if (maxIdleTimeMs < 1000) throw new IllegalArgumentException("Max idle time must be >= 1 second");
            if (connectionMaxAgeMs < 60000) throw new IllegalArgumentException("Connection max age must be >= 1 minute");
            
            this.maxPoolSize = maxPoolSize;
            this.minPoolSize = minPoolSize;
            this.maxIdleTimeMs = maxIdleTimeMs;
            this.connectionMaxAgeMs = connectionMaxAgeMs;
        }
        
        public int getMaxPoolSize() { return maxPoolSize; }
        public int getMinPoolSize() { return minPoolSize; }
        public long getMaxIdleTimeMs() { return maxIdleTimeMs; }
        public long getConnectionMaxAgeMs() { return connectionMaxAgeMs; }
    }
    
    /**
     * Immutable pool statistics.
     */
    public static class PoolStats {
        private final int totalConnections;
        private final int activeConnections;
        private final int idleConnections;
        private final LocalDateTime poolCreatedTime;
        private final LocalDateTime lastMaintenanceTime;
        
        public PoolStats(int totalConnections, int activeConnections, int idleConnections,
                        LocalDateTime poolCreatedTime, LocalDateTime lastMaintenanceTime) {
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.idleConnections = idleConnections;
            this.poolCreatedTime = poolCreatedTime;
            this.lastMaintenanceTime = lastMaintenanceTime;
        }
        
        public int getTotalConnections() { return totalConnections; }
        public int getActiveConnections() { return activeConnections; }
        public int getIdleConnections() { return idleConnections; }
        public LocalDateTime getPoolCreatedTime() { return poolCreatedTime; }
        public LocalDateTime getLastMaintenanceTime() { return lastMaintenanceTime; }
        
        public double getUtilizationRate() {
            return totalConnections > 0 ? (double) activeConnections / totalConnections * 100.0 : 0.0;
        }
    }
}