package com.integrixs.shared.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entity representing a system log entry
 * Duplicates file-based logging into database for better querying and management
 * Categorized by log category to support separate views for different log types
 */
public class SystemLog {
    
    // Primary key
    private UUID id;
    
    // Log metadata
    private LocalDateTime timestamp;
    private LogLevel logLevel;
    private LogCategory logCategory;
    private String loggerName;
    private String threadName;
    
    // Log content
    private String message;
    private String formattedMessage;
    
    // Context information
    private UUID correlationId;
    private String sessionId;
    private UUID userId;
    
    // Execution context (for ADAPTER_EXECUTION and FLOW_EXECUTION categories)
    private UUID adapterId;
    private String adapterName;
    private UUID flowId;
    private String flowName;
    private UUID executionId;
    
    // Request context
    private String requestId;
    private String requestMethod;
    private String requestUri;
    private String remoteAddress;
    private String userAgent;
    
    // Application context
    private String applicationName = "h2h-backend";
    private String environment;
    private String serverHostname;
    
    // Exception details
    private String exceptionClass;
    private String exceptionMessage;
    private String stackTrace;
    
    // Additional metadata
    private Map<String, Object> mdcData;
    private String marker;
    
    // Performance tracking
    private Long executionTimeMs;
    
    // Audit fields
    private LocalDateTime createdAt;
    
    /**
     * Log levels enum
     */
    public enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR, FATAL
    }
    
    /**
     * Log categories enum for organizing different types of logs
     */
    public enum LogCategory {
        SYSTEM,              // General system logs
        ADAPTER_EXECUTION,   // Adapter execution logs (for future adapter execution page)
        FLOW_EXECUTION,      // Flow execution logs (for future flow execution page)
        AUTHENTICATION,      // Authentication and authorization logs
        DATABASE,            // Database operation logs
        SCHEDULER,           // Scheduled task logs
        API                  // API request/response logs
    }
    
    // Constructors
    public SystemLog() {
        this.id = UUID.randomUUID();
        this.timestamp = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.logCategory = LogCategory.SYSTEM; // Default category
    }
    
    public SystemLog(LogLevel logLevel, LogCategory logCategory, String loggerName, String message) {
        this();
        this.logLevel = logLevel;
        this.logCategory = logCategory;
        this.loggerName = loggerName;
        this.message = message;
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
    
    public LogLevel getLogLevel() {
        return logLevel;
    }
    
    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }
    
    public LogCategory getLogCategory() {
        return logCategory;
    }
    
    public void setLogCategory(LogCategory logCategory) {
        this.logCategory = logCategory;
    }
    
    public String getLoggerName() {
        return loggerName;
    }
    
    public void setLoggerName(String loggerName) {
        this.loggerName = loggerName;
    }
    
    public String getThreadName() {
        return threadName;
    }
    
    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getFormattedMessage() {
        return formattedMessage;
    }
    
    public void setFormattedMessage(String formattedMessage) {
        this.formattedMessage = formattedMessage;
    }
    
    public UUID getCorrelationId() {
        return correlationId;
    }
    
    public void setCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public UUID getUserId() {
        return userId;
    }
    
    public void setUserId(UUID userId) {
        this.userId = userId;
    }
    
    public UUID getAdapterId() {
        return adapterId;
    }
    
    public void setAdapterId(UUID adapterId) {
        this.adapterId = adapterId;
    }
    
    public String getAdapterName() {
        return adapterName;
    }
    
    public void setAdapterName(String adapterName) {
        this.adapterName = adapterName;
    }
    
    public UUID getFlowId() {
        return flowId;
    }
    
    public void setFlowId(UUID flowId) {
        this.flowId = flowId;
    }
    
    public String getFlowName() {
        return flowName;
    }
    
    public void setFlowName(String flowName) {
        this.flowName = flowName;
    }
    
    public UUID getExecutionId() {
        return executionId;
    }
    
    public void setExecutionId(UUID executionId) {
        this.executionId = executionId;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    public String getRequestMethod() {
        return requestMethod;
    }
    
    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }
    
    public String getRequestUri() {
        return requestUri;
    }
    
    public void setRequestUri(String requestUri) {
        this.requestUri = requestUri;
    }
    
    public String getRemoteAddress() {
        return remoteAddress;
    }
    
    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    
    public String getApplicationName() {
        return applicationName;
    }
    
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }
    
    public String getEnvironment() {
        return environment;
    }
    
    public void setEnvironment(String environment) {
        this.environment = environment;
    }
    
    public String getServerHostname() {
        return serverHostname;
    }
    
    public void setServerHostname(String serverHostname) {
        this.serverHostname = serverHostname;
    }
    
    public String getExceptionClass() {
        return exceptionClass;
    }
    
    public void setExceptionClass(String exceptionClass) {
        this.exceptionClass = exceptionClass;
    }
    
    public String getExceptionMessage() {
        return exceptionMessage;
    }
    
    public void setExceptionMessage(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }
    
    public String getStackTrace() {
        return stackTrace;
    }
    
    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }
    
    public Map<String, Object> getMdcData() {
        return mdcData;
    }
    
    public void setMdcData(Map<String, Object> mdcData) {
        this.mdcData = mdcData;
    }
    
    public String getMarker() {
        return marker;
    }
    
    public void setMarker(String marker) {
        this.marker = marker;
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
    
    /**
     * Helper methods to create specific log types
     */
    
    /**
     * Create an adapter execution log
     */
    public static SystemLog createAdapterLog(LogLevel level, String loggerName, String message, 
                                           UUID adapterId, String adapterName, UUID executionId) {
        SystemLog log = new SystemLog(level, LogCategory.ADAPTER_EXECUTION, loggerName, message);
        log.setAdapterId(adapterId);
        log.setAdapterName(adapterName);
        log.setExecutionId(executionId);
        return log;
    }
    
    /**
     * Create a flow execution log
     */
    public static SystemLog createFlowLog(LogLevel level, String loggerName, String message,
                                        UUID flowId, String flowName, UUID executionId) {
        SystemLog log = new SystemLog(level, LogCategory.FLOW_EXECUTION, loggerName, message);
        log.setFlowId(flowId);
        log.setFlowName(flowName);
        log.setExecutionId(executionId);
        return log;
    }
    
    /**
     * Create an authentication log
     */
    public static SystemLog createAuthLog(LogLevel level, String loggerName, String message, UUID userId) {
        SystemLog log = new SystemLog(level, LogCategory.AUTHENTICATION, loggerName, message);
        log.setUserId(userId);
        return log;
    }
    
    /**
     * Create a database operation log
     */
    public static SystemLog createDatabaseLog(LogLevel level, String loggerName, String message) {
        return new SystemLog(level, LogCategory.DATABASE, loggerName, message);
    }
    
    /**
     * Create an API log
     */
    public static SystemLog createApiLog(LogLevel level, String loggerName, String message,
                                       String requestMethod, String requestUri, String requestId) {
        SystemLog log = new SystemLog(level, LogCategory.API, loggerName, message);
        log.setRequestMethod(requestMethod);
        log.setRequestUri(requestUri);
        log.setRequestId(requestId);
        return log;
    }
    
    @Override
    public String toString() {
        return String.format("SystemLog[%s] %s [%s] %s: %s", 
                           timestamp, logLevel, logCategory, loggerName, message);
    }
}