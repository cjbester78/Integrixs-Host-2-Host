package com.integrixs.core.sftp;

import com.integrixs.core.logging.EnhancedLogger;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Connection pool for SFTP connections to improve performance and resource management
 */
@Component
public class SftpConnectionPool {
    
    private static final EnhancedLogger logger = EnhancedLogger.getLogger(SftpConnectionPool.class);
    
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<SftpConnection>> connectionPools;
    private final ScheduledExecutorService cleanupExecutor;
    
    // Pool configuration
    private final int maxConnectionsPerBank = 3;
    private final int maxIdleTimeMinutes = 10;
    private final int cleanupIntervalMinutes = 5;
    
    public SftpConnectionPool() {
        this.connectionPools = new ConcurrentHashMap<>();
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SftpConnectionPool-Cleanup");
            t.setDaemon(true);
            return t;
        });
        
        // Schedule cleanup task
        cleanupExecutor.scheduleWithFixedDelay(
            this::cleanupIdleConnections,
            cleanupIntervalMinutes,
            cleanupIntervalMinutes,
            TimeUnit.MINUTES
        );
        
        logger.info("SFTP Connection Pool initialized with maxConnections={}, maxIdleTime={}min", 
                   maxConnectionsPerBank, maxIdleTimeMinutes);
    }
    
    /**
     * Get a connection for the specified bank
     */
    public SftpConnection borrowConnection(String bankName) {
        ConcurrentLinkedQueue<SftpConnection> pool = connectionPools.computeIfAbsent(
            bankName, k -> new ConcurrentLinkedQueue<>()
        );
        
        // Try to get an existing connection
        SftpConnection connection = pool.poll();
        while (connection != null) {
            if (connection.isConnected()) {
                connection.setInUse(true);
                logger.debug("Borrowed existing connection for bank: {}", bankName);
                return connection;
            } else {
                // Connection is stale, discard it
                connection.disconnect();
                connection = pool.poll();
            }
        }
        
        logger.debug("No available connections for bank: {}, will create new connection", bankName);
        return null; // Caller will create new connection
    }
    
    /**
     * Return a connection to the pool
     */
    public void returnConnection(SftpConnection connection) {
        if (connection == null) return;
        
        String bankName = connection.getBankName();
        ConcurrentLinkedQueue<SftpConnection> pool = connectionPools.get(bankName);
        
        if (pool == null) {
            connection.disconnect();
            return;
        }
        
        connection.setInUse(false);
        
        // Check pool size limit
        if (pool.size() >= maxConnectionsPerBank) {
            logger.debug("Pool for bank {} is full, discarding connection", bankName);
            connection.disconnect();
        } else if (connection.isConnected()) {
            pool.offer(connection);
            logger.debug("Returned connection to pool for bank: {}", bankName);
        } else {
            connection.disconnect();
            logger.debug("Discarded disconnected connection for bank: {}", bankName);
        }
    }
    
    /**
     * Add a new connection to the pool (for newly created connections)
     */
    public void addConnection(SftpConnection connection) {
        returnConnection(connection);
    }
    
    /**
     * Close all connections for a specific bank
     */
    public void closeBankConnections(String bankName) {
        ConcurrentLinkedQueue<SftpConnection> pool = connectionPools.remove(bankName);
        if (pool != null) {
            while (!pool.isEmpty()) {
                SftpConnection connection = pool.poll();
                if (connection != null) {
                    connection.disconnect();
                }
            }
            logger.info("Closed all connections for bank: {}", bankName);
        }
    }
    
    /**
     * Close all connections and shutdown the pool
     */
    public void shutdown() {
        logger.info("Shutting down SFTP connection pool");
        
        connectionPools.values().forEach(pool -> {
            while (!pool.isEmpty()) {
                SftpConnection connection = pool.poll();
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
        
        connectionPools.clear();
        cleanupExecutor.shutdown();
        
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("SFTP connection pool shutdown completed");
    }
    
    /**
     * Get pool statistics
     */
    public PoolStatistics getStatistics() {
        PoolStatistics stats = new PoolStatistics();
        
        connectionPools.forEach((bankName, pool) -> {
            int totalConnections = pool.size();
            int activeConnections = (int) pool.stream()
                .mapToLong(conn -> conn.isInUse() ? 1 : 0)
                .sum();
            
            stats.addBankStats(bankName, totalConnections, activeConnections);
        });
        
        return stats;
    }
    
    /**
     * Cleanup idle connections periodically
     */
    private void cleanupIdleConnections() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(maxIdleTimeMinutes);
            int totalClosed = 0;
            
            for (String bankName : connectionPools.keySet()) {
                ConcurrentLinkedQueue<SftpConnection> pool = connectionPools.get(bankName);
                if (pool == null) continue;
                
                int closedCount = 0;
                ConcurrentLinkedQueue<SftpConnection> activeConnections = new ConcurrentLinkedQueue<>();
                
                while (!pool.isEmpty()) {
                    SftpConnection connection = pool.poll();
                    if (connection != null) {
                        if (connection.isInUse() || 
                            connection.getLastUsedTime().isAfter(cutoffTime) && connection.isConnected()) {
                            // Keep active or recently used connections
                            activeConnections.offer(connection);
                        } else {
                            // Close idle or disconnected connections
                            connection.disconnect();
                            closedCount++;
                        }
                    }
                }
                
                // Replace pool contents with active connections
                pool.addAll(activeConnections);
                
                if (closedCount > 0) {
                    logger.debug("Cleaned up {} idle connections for bank: {}", closedCount, bankName);
                    totalClosed += closedCount;
                }
            }
            
            if (totalClosed > 0) {
                logger.info("Connection pool cleanup completed: {} connections closed", totalClosed);
            }
            
        } catch (Exception e) {
            logger.error("Error during connection pool cleanup", e);
        }
    }
    
    /**
     * Pool statistics holder
     */
    public static class PoolStatistics {
        private final ConcurrentHashMap<String, BankStats> bankStats = new ConcurrentHashMap<>();
        
        public void addBankStats(String bankName, int totalConnections, int activeConnections) {
            bankStats.put(bankName, new BankStats(totalConnections, activeConnections));
        }
        
        public int getTotalConnections() {
            return bankStats.values().stream()
                .mapToInt(stats -> stats.totalConnections)
                .sum();
        }
        
        public int getActiveConnections() {
            return bankStats.values().stream()
                .mapToInt(stats -> stats.activeConnections)
                .sum();
        }
        
        public ConcurrentHashMap<String, BankStats> getBankStats() {
            return bankStats;
        }
        
        public static class BankStats {
            public final int totalConnections;
            public final int activeConnections;
            
            public BankStats(int totalConnections, int activeConnections) {
                this.totalConnections = totalConnections;
                this.activeConnections = activeConnections;
            }
        }
    }
}