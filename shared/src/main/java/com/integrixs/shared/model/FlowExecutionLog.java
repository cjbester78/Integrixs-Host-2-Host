package com.integrixs.shared.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * FlowExecutionLog entity representing detailed logs for each step in a flow execution
 * Provides granular logging information for troubleshooting and monitoring
 */
public class FlowExecutionLog {
    
    private UUID id;
    private UUID executionId;
    private UUID stepId; // Reference to FlowExecutionStep
    
    // Log metadata
    private LogLevel level;
    private LocalDateTime timestamp;
    private String category; // ADAPTER, UTILITY, SYSTEM, etc.
    
    // Log content
    private String message;
    private String details; // Additional details or stack trace
    
    // Context information
    private String className; // Java class that generated the log
    private String methodName; // Method that generated the log
    private Integer lineNumber; // Line number in source code
    
    // File/directory context
    private String directory; // Current working directory
    private String fileName; // File being processed
    private Long fileSize; // Size of file being processed
    
    // Correlation
    private UUID correlationId;
    private String threadName; // Thread that generated the log
    
    // Enums
    public enum LogLevel {
        TRACE("Detailed trace information"),
        DEBUG("Debug information for development"),
        INFO("General informational messages"),
        WARN("Warning messages for potential issues"),
        ERROR("Error messages for failures");
        
        private final String description;
        
        LogLevel(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean isErrorLevel() {
            return this == ERROR;
        }
        
        public boolean isWarnOrHigher() {
            return this == WARN || this == ERROR;
        }
        
        public boolean isInfoOrHigher() {
            return this == INFO || this == WARN || this == ERROR;
        }
    }
    
    // Constructors
    public FlowExecutionLog() {
        this.id = UUID.randomUUID();
        this.timestamp = LocalDateTime.now();
        this.level = LogLevel.INFO;
    }
    
    public FlowExecutionLog(UUID executionId, UUID stepId, LogLevel level, String message) {
        this();
        this.executionId = executionId;
        this.stepId = stepId;
        this.level = level;
        this.message = message;
    }
    
    public FlowExecutionLog(UUID executionId, UUID stepId, LogLevel level, String message, String details) {
        this(executionId, stepId, level, message);
        this.details = details;
    }
    
    // Static factory methods for common log types
    public static FlowExecutionLog info(UUID executionId, UUID stepId, String message) {
        return new FlowExecutionLog(executionId, stepId, LogLevel.INFO, message);
    }
    
    public static FlowExecutionLog info(UUID executionId, UUID stepId, String message, String details) {
        return new FlowExecutionLog(executionId, stepId, LogLevel.INFO, message, details);
    }
    
    public static FlowExecutionLog warn(UUID executionId, UUID stepId, String message) {
        return new FlowExecutionLog(executionId, stepId, LogLevel.WARN, message);
    }
    
    public static FlowExecutionLog warn(UUID executionId, UUID stepId, String message, String details) {
        return new FlowExecutionLog(executionId, stepId, LogLevel.WARN, message, details);
    }
    
    public static FlowExecutionLog error(UUID executionId, UUID stepId, String message) {
        return new FlowExecutionLog(executionId, stepId, LogLevel.ERROR, message);
    }
    
    public static FlowExecutionLog error(UUID executionId, UUID stepId, String message, String details) {
        return new FlowExecutionLog(executionId, stepId, LogLevel.ERROR, message, details);
    }
    
    public static FlowExecutionLog debug(UUID executionId, UUID stepId, String message) {
        return new FlowExecutionLog(executionId, stepId, LogLevel.DEBUG, message);
    }
    
    public static FlowExecutionLog debug(UUID executionId, UUID stepId, String message, String details) {
        return new FlowExecutionLog(executionId, stepId, LogLevel.DEBUG, message, details);
    }
    
    public static FlowExecutionLog trace(UUID executionId, UUID stepId, String message) {
        return new FlowExecutionLog(executionId, stepId, LogLevel.TRACE, message);
    }
    
    public static FlowExecutionLog trace(UUID executionId, UUID stepId, String message, String details) {
        return new FlowExecutionLog(executionId, stepId, LogLevel.TRACE, message, details);
    }
    
    // Builder pattern methods for additional context
    public FlowExecutionLog withCategory(String category) {
        this.category = category;
        return this;
    }
    
    public FlowExecutionLog withDirectory(String directory) {
        this.directory = directory;
        return this;
    }
    
    public FlowExecutionLog withFile(String fileName, Long fileSize) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        return this;
    }
    
    public FlowExecutionLog withSourceLocation(String className, String methodName, Integer lineNumber) {
        this.className = className;
        this.methodName = methodName;
        this.lineNumber = lineNumber;
        return this;
    }
    
    public FlowExecutionLog withCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
        return this;
    }
    
    public FlowExecutionLog withThread(String threadName) {
        this.threadName = threadName;
        return this;
    }
    
    // Utility methods
    public boolean isError() {
        return this.level == LogLevel.ERROR;
    }
    
    public boolean isWarningOrHigher() {
        return this.level.isWarnOrHigher();
    }
    
    public String getFormattedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(level.name()).append("] ");
        if (category != null) {
            sb.append("[").append(category).append("] ");
        }
        sb.append(message);
        
        if (fileName != null) {
            sb.append(" (File: ").append(fileName);
            if (fileSize != null) {
                sb.append(", Size: ").append(formatFileSize(fileSize));
            }
            sb.append(")");
        }
        
        if (directory != null) {
            sb.append(" (Dir: ").append(directory).append(")");
        }
        
        return sb.toString();
    }
    
    private String formatFileSize(Long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public UUID getExecutionId() {
        return executionId;
    }
    
    public void setExecutionId(UUID executionId) {
        this.executionId = executionId;
    }
    
    public UUID getStepId() {
        return stepId;
    }
    
    public void setStepId(UUID stepId) {
        this.stepId = stepId;
    }
    
    public LogLevel getLevel() {
        return level;
    }
    
    public void setLevel(LogLevel level) {
        this.level = level;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getDetails() {
        return details;
    }
    
    public void setDetails(String details) {
        this.details = details;
    }
    
    public String getClassName() {
        return className;
    }
    
    public void setClassName(String className) {
        this.className = className;
    }
    
    public String getMethodName() {
        return methodName;
    }
    
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }
    
    public Integer getLineNumber() {
        return lineNumber;
    }
    
    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }
    
    public String getDirectory() {
        return directory;
    }
    
    public void setDirectory(String directory) {
        this.directory = directory;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public Long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
    
    public UUID getCorrelationId() {
        return correlationId;
    }
    
    public void setCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
    }
    
    public String getThreadName() {
        return threadName;
    }
    
    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }
    
    @Override
    public String toString() {
        return "FlowExecutionLog{" +
                "id=" + id +
                ", level=" + level +
                ", timestamp=" + timestamp +
                ", category='" + category + '\'' +
                ", message='" + message + '\'' +
                ", fileName='" + fileName + '\'' +
                ", directory='" + directory + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlowExecutionLog that = (FlowExecutionLog) o;
        return id != null && id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}