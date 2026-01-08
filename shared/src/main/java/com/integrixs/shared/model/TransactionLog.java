package com.integrixs.shared.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transaction Log Entity for H2H File Transfer System
 * Stores all transactional events for security monitoring and business analytics
 * Simple POJO for native SQL operations
 */
public class TransactionLog {

    private UUID id;
    private LocalDateTime timestamp;
    private LogLevel level = LogLevel.INFO;
    private String category;
    private String component;
    private String source;
    private String message;
    
    // Authentication context
    private String username;
    private String ipAddress;
    private String userAgent;
    private String sessionId;
    
    // File processing context
    private String correlationId;
    private UUID adapterId;
    private UUID executionId;
    private String fileName;
    
    // Flexible additional data as JSON
    private String details;
    
    // Performance tracking
    private Long executionTimeMs;
    private LocalDateTime createdAt;

    /**
     * Log levels for transaction logs
     */
    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    // Constructors

    public TransactionLog() {}

    public TransactionLog(String category, String component, String source, String message) {
        this.category = category;
        this.component = component;
        this.source = source;
        this.message = message;
        this.timestamp = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Initialize timestamps if null. Should only be called during INSERT operations.
     */
    public void initializeTimestamps() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public LogLevel getLevel() {
        return level;
    }

    public void setLevel(LogLevel level) {
        this.level = level;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public UUID getAdapterId() {
        return adapterId;
    }

    public void setAdapterId(UUID adapterId) {
        this.adapterId = adapterId;
    }

    public UUID getExecutionId() {
        return executionId;
    }

    public void setExecutionId(UUID executionId) {
        this.executionId = executionId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(Long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "TransactionLog{" +
                "id=" + id +
                ", timestamp=" + timestamp +
                ", level=" + level +
                ", category='" + category + '\'' +
                ", component='" + component + '\'' +
                ", source='" + source + '\'' +
                ", username='" + username + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}