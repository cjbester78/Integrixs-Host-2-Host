package com.integrixs.shared.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * System Audit Log Entity
 * 
 * Represents an audit log entry that tracks all database operations and system events.
 */
public class SystemAuditLog {
    
    private UUID id;
    private String eventType;
    private String eventCategory;
    private UUID userId;
    private String username; // Maps to username column in DB
    private String entityType; // Legacy field - can map to resourceType
    private UUID entityId; // Legacy field - can map to resourceId  
    private String resourceType; // Maps to resource_type column in DB
    private UUID resourceId; // Maps to resource_id column in DB
    private String resourceName; // Maps to resource_name column in DB
    private String eventDescription;
    private Map<String, Object> eventDetails;
    private String ipAddress;
    private String userAgent;
    private String sessionId;
    private UUID correlationId;
    private boolean success;
    private String errorMessage;
    private LocalDateTime createdAt;
    
    // Default constructor
    public SystemAuditLog() {
        this.id = UUID.randomUUID();
        this.createdAt = LocalDateTime.now();
    }
    
    // Constructor for database operations
    public SystemAuditLog(String eventType, String eventCategory, UUID userId, 
                         String entityType, UUID entityId, String eventDescription) {
        this();
        this.eventType = eventType;
        this.eventCategory = eventCategory;
        this.userId = userId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.eventDescription = eventDescription;
        this.success = true; // Default to true, can be overridden
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
    
    public String getEventCategory() {
        return eventCategory;
    }
    
    public void setEventCategory(String eventCategory) {
        this.eventCategory = eventCategory;
    }
    
    public UUID getUserId() {
        return userId;
    }
    
    public void setUserId(UUID userId) {
        this.userId = userId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getEntityType() {
        return entityType;
    }
    
    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }
    
    public UUID getEntityId() {
        return entityId;
    }
    
    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }
    
    public String getResourceType() {
        return resourceType != null ? resourceType : entityType; // Fallback to entityType for compatibility
    }
    
    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }
    
    public UUID getResourceId() {
        return resourceId != null ? resourceId : entityId; // Fallback to entityId for compatibility
    }
    
    public void setResourceId(UUID resourceId) {
        this.resourceId = resourceId;
    }
    
    public String getResourceName() {
        return resourceName;
    }
    
    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }
    
    public String getEventDescription() {
        return eventDescription;
    }
    
    public void setEventDescription(String eventDescription) {
        this.eventDescription = eventDescription;
    }
    
    public Map<String, Object> getEventDetails() {
        return eventDetails;
    }
    
    public void setEventDetails(Map<String, Object> eventDetails) {
        this.eventDetails = eventDetails;
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
    
    public UUID getCorrelationId() {
        return correlationId;
    }
    
    public void setCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    @Override
    public String toString() {
        return String.format("SystemAuditLog{id=%s, eventType='%s', eventCategory='%s', " +
                           "userId=%s, entityType='%s', entityId=%s, success=%s, createdAt=%s}",
                           id, eventType, eventCategory, userId, entityType, entityId, success, createdAt);
    }
}