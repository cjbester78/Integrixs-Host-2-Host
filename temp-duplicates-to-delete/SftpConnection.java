package com.integrixs.core.adapter.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wrapper class for SFTP connection with proper resource management.
 * Provides thread-safe access to SFTP channel with lifecycle tracking.
 * Follows OOP principles with encapsulation and proper resource cleanup.
 */
public class SftpConnection {
    
    private static final Logger logger = LoggerFactory.getLogger(SftpConnection.class);
    
    private final ChannelSftp channel;
    private final Session session;
    private final LocalDateTime createdTime;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile LocalDateTime lastUsedTime;
    
    public SftpConnection(ChannelSftp channel, Session session, LocalDateTime createdTime) {
        this.channel = channel;
        this.session = session;
        this.createdTime = createdTime;
        this.lastUsedTime = createdTime;
    }
    
    /**
     * Get the SFTP channel for operations.
     * 
     * @return the SFTP channel
     * @throws IllegalStateException if connection is closed
     */
    public ChannelSftp getChannel() {
        if (closed.get()) {
            throw new IllegalStateException("Connection is closed");
        }
        
        updateLastUsedTime();
        return channel;
    }
    
    /**
     * Get the SSH session.
     * 
     * @return the SSH session
     */
    public Session getSession() {
        return session;
    }
    
    /**
     * Get connection creation time.
     * 
     * @return creation timestamp
     */
    public LocalDateTime getCreatedTime() {
        return createdTime;
    }
    
    /**
     * Get last used time.
     * 
     * @return last used timestamp
     */
    public LocalDateTime getLastUsedTime() {
        return lastUsedTime;
    }
    
    /**
     * Update last used time to current time.
     */
    public void updateLastUsedTime() {
        this.lastUsedTime = LocalDateTime.now();
    }
    
    /**
     * Check if connection is valid and usable.
     * 
     * @return true if connection is valid
     */
    public boolean isValid() {
        if (closed.get()) {
            return false;
        }
        
        try {
            // Check if channel and session are connected
            if (!channel.isConnected() || !session.isConnected()) {
                logger.debug("Connection validation failed: channel or session disconnected");
                return false;
            }
            
            // Perform a lightweight operation to verify connectivity
            channel.pwd();
            updateLastUsedTime();
            
            return true;
            
        } catch (Exception e) {
            logger.debug("Connection validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if connection is closed.
     * 
     * @return true if connection is closed
     */
    public boolean isClosed() {
        return closed.get();
    }
    
    /**
     * Check if connection is idle for the given duration.
     * 
     * @param maxIdleTimeMs maximum idle time in milliseconds
     * @return true if connection has been idle longer than specified time
     */
    public boolean isIdleFor(long maxIdleTimeMs) {
        if (closed.get()) {
            return true;
        }
        
        long idleTimeMs = java.time.Duration.between(lastUsedTime, LocalDateTime.now()).toMillis();
        return idleTimeMs > maxIdleTimeMs;
    }
    
    /**
     * Check if connection is older than the maximum age.
     * 
     * @param maxAgeMs maximum age in milliseconds
     * @return true if connection is older than specified age
     */
    public boolean isOlderThan(long maxAgeMs) {
        long ageMs = java.time.Duration.between(createdTime, LocalDateTime.now()).toMillis();
        return ageMs > maxAgeMs;
    }
    
    /**
     * Close the connection and release resources.
     */
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                if (channel != null && channel.isConnected()) {
                    channel.disconnect();
                    logger.debug("SFTP channel disconnected");
                }
                
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
     * Get connection age in milliseconds.
     * 
     * @return connection age in milliseconds
     */
    public long getAgeMs() {
        return java.time.Duration.between(createdTime, LocalDateTime.now()).toMillis();
    }
    
    /**
     * Get idle time in milliseconds.
     * 
     * @return idle time in milliseconds
     */
    public long getIdleTimeMs() {
        return java.time.Duration.between(lastUsedTime, LocalDateTime.now()).toMillis();
    }
    
    /**
     * Get connection information for monitoring.
     * 
     * @return connection info string
     */
    public String getConnectionInfo() {
        if (session == null) {
            return "INVALID_SESSION";
        }
        
        return String.format("%s@%s:%d [age: %dms, idle: %dms, closed: %s]",
                           session.getUserName(),
                           session.getHost(),
                           session.getPort(),
                           getAgeMs(),
                           getIdleTimeMs(),
                           closed.get());
    }
    
    @Override
    public String toString() {
        return "SftpConnection{" + getConnectionInfo() + "}";
    }
}