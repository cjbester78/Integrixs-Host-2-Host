package com.integrixs.shared.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Adapter entity representing reusable adapter components (SFTP/File/Email)
 * Used for both sender and receiver data processing in integration flows
 */
public class Adapter {
    
    // Enum for adapter runtime status
    public enum AdapterStatus {
        STARTED("Adapter is running and actively processing"),
        STOPPED("Adapter is stopped and not processing");
        
        private final String description;
        
        AdapterStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean canProcess() {
            return this == STARTED;
        }
        
        public boolean isTransitioning() {
            return false; // No transitioning states anymore
        }
    }
    
    private UUID id;
    private String name;
    private String bank;
    private String description;
    private String adapterType; // SFTP, FILE, EMAIL
    private String direction; // SENDER, RECEIVER
    
    // Configuration and testing
    private Map<String, Object> configuration;
    private Boolean connectionValidated;
    private LocalDateTime lastTestAt;
    private String testResult;
    
    // Status and control
    private Boolean active;
    private AdapterStatus status;
    
    // Import tracking
    private UUID originalAdapterId;  // ID of the original adapter when imported
    
    // Performance tracking
    private Long averageExecutionTimeMs;
    private BigDecimal successRatePercent;
    
    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
    
    // Constructors
    public Adapter() {
        this.active = true;
        this.status = AdapterStatus.STOPPED;
        this.connectionValidated = false;
        this.averageExecutionTimeMs = 0L;
        this.successRatePercent = new BigDecimal("100.0");
        this.createdAt = LocalDateTime.now();
        // updatedAt and updatedBy should be NULL on creation - only set on actual updates
        this.updatedAt = null;
        this.updatedBy = null;
    }
    
    public Adapter(String name, String bank, String adapterType, String direction, Map<String, Object> configuration, UUID createdBy) {
        this();
        this.name = name;
        this.bank = bank;
        this.adapterType = adapterType;
        this.direction = direction;
        this.configuration = configuration;
        this.createdBy = createdBy;
        // Do not set updatedBy on creation - only set on actual updates
    }
    
    /**
     * Mark entity as updated by specified user. Should be called for all business logic updates.
     * This properly maintains the audit trail for UPDATE operations.
     */
    public void markAsUpdated(UUID updatedBy) {
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = Objects.requireNonNull(updatedBy, "Updated by cannot be null");
    }
    
    // Business logic methods
    public boolean isSender() {
        return "SENDER".equalsIgnoreCase(direction);
    }
    
    public boolean isReceiver() {
        return "RECEIVER".equalsIgnoreCase(direction);
    }
    
    // Legacy method names for backward compatibility during transition
    @Deprecated
    public boolean isInbound() {
        return isSender(); // SENDER adapters send files out
    }
    
    @Deprecated
    public boolean isOutbound() {
        return isReceiver(); // RECEIVER adapters receive files in
    }
    
    public boolean isSftpAdapter() {
        return "SFTP".equalsIgnoreCase(adapterType);
    }
    
    public boolean isFileAdapter() {
        return "FILE".equalsIgnoreCase(adapterType);
    }
    
    public boolean isEmailAdapter() {
        return "EMAIL".equalsIgnoreCase(adapterType);
    }
    
    public boolean isValid() {
        return active && connectionValidated != null && connectionValidated;
    }
    
    public String getDisplayName() {
        return String.format("%s (%s %s)", name, adapterType, direction);
    }
    
    public void updatePerformanceMetrics(long executionTimeMs, boolean success) {
        // Simple moving average calculation - in real implementation this would be more sophisticated
        if (this.averageExecutionTimeMs == null || this.averageExecutionTimeMs == 0) {
            this.averageExecutionTimeMs = executionTimeMs;
        } else {
            // Weighted average: 70% previous + 30% current
            this.averageExecutionTimeMs = (long) (this.averageExecutionTimeMs * 0.7 + executionTimeMs * 0.3);
        }
        
        // Update success rate - simplified calculation
        if (success && this.successRatePercent.compareTo(new BigDecimal("100.0")) < 0) {
            this.successRatePercent = this.successRatePercent.add(new BigDecimal("1.0"));
            if (this.successRatePercent.compareTo(new BigDecimal("100.0")) > 0) {
                this.successRatePercent = new BigDecimal("100.0");
            }
        } else if (!success && this.successRatePercent.compareTo(BigDecimal.ZERO) > 0) {
            this.successRatePercent = this.successRatePercent.subtract(new BigDecimal("2.0"));
            if (this.successRatePercent.compareTo(BigDecimal.ZERO) < 0) {
                this.successRatePercent = BigDecimal.ZERO;
            }
        }
    }
    
    public void markTestResult(boolean successful, String result) {
        this.connectionValidated = successful;
        this.testResult = result;
        this.lastTestAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getBank() {
        return bank;
    }
    
    public void setBank(String bank) {
        this.bank = bank;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getAdapterType() {
        return adapterType;
    }
    
    public void setAdapterType(String adapterType) {
        this.adapterType = adapterType;
    }
    
    public String getDirection() {
        return direction;
    }
    
    public void setDirection(String direction) {
        this.direction = direction;
    }
    
    public Map<String, Object> getConfiguration() {
        return configuration;
    }
    
    public void setConfiguration(Map<String, Object> configuration) {
        this.configuration = configuration;
    }
    
    public Boolean getConnectionValidated() {
        return connectionValidated;
    }
    
    public void setConnectionValidated(Boolean connectionValidated) {
        this.connectionValidated = connectionValidated;
    }
    
    public LocalDateTime getLastTestAt() {
        return lastTestAt;
    }
    
    public void setLastTestAt(LocalDateTime lastTestAt) {
        this.lastTestAt = lastTestAt;
    }
    
    public String getTestResult() {
        return testResult;
    }
    
    public void setTestResult(String testResult) {
        this.testResult = testResult;
    }
    
    public Boolean getActive() {
        return active;
    }
    
    public void setActive(Boolean active) {
        this.active = active;
    }
    
    public UUID getOriginalAdapterId() {
        return originalAdapterId;
    }
    
    public void setOriginalAdapterId(UUID originalAdapterId) {
        this.originalAdapterId = originalAdapterId;
    }
    
    public boolean isActive() {
        return active != null && active;
    }
    
    public AdapterStatus getStatus() {
        return status;
    }
    
    public void setStatus(AdapterStatus status) {
        this.status = status;
    }
    
    public boolean canProcess() {
        return isActive() && status != null && status.canProcess();
    }
    
    public Long getAverageExecutionTimeMs() {
        return averageExecutionTimeMs;
    }
    
    public void setAverageExecutionTimeMs(Long averageExecutionTimeMs) {
        this.averageExecutionTimeMs = averageExecutionTimeMs;
    }
    
    public BigDecimal getSuccessRatePercent() {
        return successRatePercent;
    }
    
    public void setSuccessRatePercent(BigDecimal successRatePercent) {
        this.successRatePercent = successRatePercent;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    /**
     * Sets the creation timestamp. Should only be used during INSERT operations.
     * Protected visibility to prevent misuse in business logic.
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    /**
     * Sets the update timestamp. Should only be used during UPDATE operations by persistence layer.
     * Protected visibility to prevent misuse in business logic.
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public UUID getCreatedBy() {
        return createdBy;
    }
    
    /**
     * Sets the user who created this entity. Should only be used during INSERT operations.
     * Protected visibility to prevent misuse in business logic.
     */
    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
    
    public UUID getUpdatedBy() {
        return updatedBy;
    }
    
    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }
    
    @Override
    public String toString() {
        return "Adapter{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", adapterType='" + adapterType + '\'' +
                ", direction='" + direction + '\'' +
                ", active=" + active +
                ", status=" + status +
                ", connectionValidated=" + connectionValidated +
                ", createdAt=" + createdAt +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Adapter adapter = (Adapter) o;
        return id != null && id.equals(adapter.id);
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}