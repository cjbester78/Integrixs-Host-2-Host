package com.integrixs.shared.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User Management Error Entity for H2H File Transfer System
 * Tracks security-related errors and threats for comprehensive audit trail
 * Simple POJO for native SQL operations
 */
public class UserManagementError {

    private UUID id;
    private ErrorType errorType;
    private String errorCode;
    private String action;
    private String errorMessage;
    private String username;
    private String ipAddress;
    private String userAgent;
    private ThreatLevel threatLevel = ThreatLevel.LOW;
    private UUID transactionLogId; // Links to transaction_logs
    private LocalDateTime occurredAt;
    private boolean isResolved = false;
    private String resolutionNotes;

    /**
     * Error types for user management errors
     */
    public enum ErrorType {
        AUTHENTICATION, AUTHORIZATION, SESSION_MANAGEMENT, USER_MANAGEMENT
    }

    /**
     * Threat levels for security assessment
     */
    public enum ThreatLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    // Constructors

    public UserManagementError() {}

    public UserManagementError(ErrorType errorType, String errorCode, String action, String errorMessage, 
                              String username, String ipAddress) {
        this.errorType = errorType;
        this.errorCode = errorCode;
        this.action = action;
        this.errorMessage = errorMessage;
        this.username = username;
        this.ipAddress = ipAddress;
        this.occurredAt = LocalDateTime.now();
        this.threatLevel = ThreatLevel.LOW;
        this.isResolved = false;
    }

    /**
     * Initialize timestamps if null
     */
    public void initializeTimestamps() {
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public void setErrorType(ErrorType errorType) {
        this.errorType = errorType;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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

    public ThreatLevel getThreatLevel() {
        return threatLevel;
    }

    public void setThreatLevel(ThreatLevel threatLevel) {
        this.threatLevel = threatLevel;
    }

    public UUID getTransactionLogId() {
        return transactionLogId;
    }

    public void setTransactionLogId(UUID transactionLogId) {
        this.transactionLogId = transactionLogId;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public boolean isResolved() {
        return isResolved;
    }

    public void setResolved(boolean resolved) {
        isResolved = resolved;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }

    @Override
    public String toString() {
        return "UserManagementError{" +
                "id=" + id +
                ", errorType=" + errorType +
                ", errorCode='" + errorCode + '\'' +
                ", action='" + action + '\'' +
                ", username='" + username + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", threatLevel=" + threatLevel +
                ", occurredAt=" + occurredAt +
                ", isResolved=" + isResolved +
                '}';
    }
}