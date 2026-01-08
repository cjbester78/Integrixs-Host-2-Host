package com.integrixs.shared.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Retention Configuration Entity
 * 
 * Stores configuration settings for automated data retention policies.
 * Supports different data types (log files, database tables) with 
 * configurable retention periods and scheduling.
 * Plain POJO for native SQL operations.
 */
public class DataRetentionConfig {

    private UUID id;
    private DataType dataType;
    private String name;
    private String description;
    private Integer retentionDays;
    private Integer archiveDays; // Only for LOG_FILES type
    private String scheduleCron; // Only for SCHEDULE type
    private String executorClass; // The job class that will execute this retention policy
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime lastExecution;
    private String lastExecutionStatus;
    private Integer lastItemsProcessed;

    // Constructors
    public DataRetentionConfig() {
        this.enabled = true;
    }

    public DataRetentionConfig(DataType dataType, String name, String description, 
                              Integer retentionDays, Boolean enabled) {
        this.dataType = dataType;
        this.name = name;
        this.description = description;
        this.retentionDays = retentionDays;
        this.enabled = enabled != null ? enabled : true;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public DataType getDataType() {
        return dataType;
    }

    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(Integer retentionDays) {
        this.retentionDays = retentionDays;
    }

    public Integer getArchiveDays() {
        return archiveDays;
    }

    public void setArchiveDays(Integer archiveDays) {
        this.archiveDays = archiveDays;
    }

    public String getScheduleCron() {
        return scheduleCron;
    }

    public void setScheduleCron(String scheduleCron) {
        this.scheduleCron = scheduleCron;
    }

    public String getExecutorClass() {
        return executorClass;
    }

    public void setExecutorClass(String executorClass) {
        this.executorClass = executorClass;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getLastExecution() {
        return lastExecution;
    }

    public void setLastExecution(LocalDateTime lastExecution) {
        this.lastExecution = lastExecution;
    }

    public String getLastExecutionStatus() {
        return lastExecutionStatus;
    }

    public void setLastExecutionStatus(String lastExecutionStatus) {
        this.lastExecutionStatus = lastExecutionStatus;
    }

    public Integer getLastItemsProcessed() {
        return lastItemsProcessed;
    }

    public void setLastItemsProcessed(Integer lastItemsProcessed) {
        this.lastItemsProcessed = lastItemsProcessed;
    }

    /**
     * Data types that can be managed by retention policies
     */
    public enum DataType {
        LOG_FILES("Log Files", "Manages log file archiving and deletion"),
        SYSTEM_LOGS("System Logs", "Manages system_logs table cleanup"),
        TRANSACTION_LOGS("Transaction Logs", "Manages transaction_logs table cleanup"),
        SCHEDULE("Schedule", "Defines when retention cleanup should run");

        private final String displayName;
        private final String description;

        DataType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Validation helper - checks if configuration is valid
     */
    public boolean isValid() {
        if (dataType == null || name == null || enabled == null) {
            return false;
        }

        switch (dataType) {
            case LOG_FILES:
                return retentionDays != null && retentionDays > 0 &&
                       archiveDays != null && archiveDays > 0 &&
                       archiveDays >= retentionDays;

            case SYSTEM_LOGS:
            case TRANSACTION_LOGS:
                return retentionDays != null && retentionDays > 0;

            case SCHEDULE:
                return scheduleCron != null && !scheduleCron.trim().isEmpty();

            default:
                return false;
        }
    }

    /**
     * Get a user-friendly summary of this configuration
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(name).append(": ");

        switch (dataType) {
            case LOG_FILES:
                summary.append("Keep logs for ").append(retentionDays)
                       .append(" days, archive for ").append(archiveDays).append(" days");
                break;

            case SYSTEM_LOGS:
            case TRANSACTION_LOGS:
                summary.append("Keep records for ").append(retentionDays).append(" days");
                break;

            case SCHEDULE:
                summary.append("Schedule: ").append(scheduleCron);
                break;
        }

        return summary.toString();
    }

    @Override
    public String toString() {
        return "DataRetentionConfig{" +
                "id=" + id +
                ", dataType=" + dataType +
                ", name='" + name + '\'' +
                ", retentionDays=" + retentionDays +
                ", archiveDays=" + archiveDays +
                ", enabled=" + enabled +
                '}';
    }
}