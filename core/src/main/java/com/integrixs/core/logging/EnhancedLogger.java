package com.integrixs.core.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
/**
 * Enhanced logger with correlation context and structured logging
 */
public class EnhancedLogger {
    
    private final Logger logger;
    
    public EnhancedLogger(String name) {
        this.logger = LoggerFactory.getLogger(name);
    }
    
    public EnhancedLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }
    
    public static EnhancedLogger getLogger(String name) {
        return new EnhancedLogger(name);
    }
    
    public static EnhancedLogger getLogger(Class<?> clazz) {
        return new EnhancedLogger(clazz);
    }
    
    private void updateMDC() {
        MDC.put("correlationId", CorrelationContext.getCorrelationId());
        String operationId = CorrelationContext.getOperationId();
        if (operationId != null) {
            MDC.put("operationId", operationId);
        }
        String bankName = CorrelationContext.getBankName();
        if (bankName != null) {
            MDC.put("bankName", bankName);
        }
        String messageId = CorrelationContext.getMessageId();
        if (messageId != null) {
            MDC.put("messageId", messageId);
        }
        String sessionId = CorrelationContext.getSessionId();
        if (sessionId != null) {
            MDC.put("sessionId", sessionId);
        }
        String executionId = CorrelationContext.getExecutionId();
        if (executionId != null) {
            MDC.put("executionId", executionId);
        }
        String flowId = CorrelationContext.getFlowId();
        if (flowId != null) {
            MDC.put("flowId", flowId);
        }
        String flowName = CorrelationContext.getFlowName();
        if (flowName != null) {
            MDC.put("flowName", flowName);
        }
    }
    
    public void info(String message) {
        updateMDC();
        logger.info(message);
    }
    
    public void info(String message, Object... args) {
        updateMDC();
        logger.info(message, args);
    }
    
    public void warn(String message) {
        updateMDC();
        logger.warn(message);
    }
    
    public void warn(String message, Object... args) {
        updateMDC();
        logger.warn(message, args);
    }
    
    public void warn(String message, Throwable throwable) {
        updateMDC();
        logger.warn(message, throwable);
    }
    
    public void error(String message) {
        updateMDC();
        logger.error(message);
    }
    
    public void error(String message, Object... args) {
        updateMDC();
        logger.error(message, args);
    }
    
    public void error(String message, Throwable throwable) {
        updateMDC();
        logger.error(message, throwable);
    }
    
    public void debug(String message) {
        updateMDC();
        logger.debug(message);
    }
    
    public void debug(String message, Object... args) {
        updateMDC();
        logger.debug(message, args);
    }
    
    public void trace(String message) {
        updateMDC();
        logger.trace(message);
    }
    
    public void trace(String message, Object... args) {
        updateMDC();
        logger.trace(message, args);
    }
    
    // Structured logging methods for specific events
    public void logOperationStart(String operation, String details) {
        info("OPERATION_START: {} - {}", operation, details);
    }
    
    public void logOperationEnd(String operation, String details) {
        info("OPERATION_END: {} - {}", operation, details);
    }
    
    public void logOperationEnd(String operation, String details, long durationMs) {
        info("OPERATION_END: {} - {} (duration: {}ms)", operation, details, durationMs);
    }
    
    public void logFileOperation(String action, String fileName, String details) {
        info("FILE_{}: {} - {}", action.toUpperCase(), fileName, details);
    }
    
    public void logConnectionEvent(String event, String details) {
        info("CONNECTION_{}: {}", event.toUpperCase(), details);
    }
    
    public void logError(String operation, String errorMessage, Throwable throwable) {
        error("ERROR in {}: {}", operation, errorMessage, throwable);
    }
    
    public void logMetric(String metricName, Object value) {
        info("METRIC: {} = {}", metricName, value);
    }
    
    public void logPerformance(String operation, long durationMs, int itemCount) {
        if (itemCount > 0) {
            double avgPerItem = (double) durationMs / itemCount;
            info("PERFORMANCE: {} completed {} items in {}ms (avg: {:.2f}ms/item)", 
                 operation, itemCount, durationMs, avgPerItem);
        } else {
            info("PERFORMANCE: {} completed in {}ms", operation, durationMs);
        }
    }
    
    // Audit logging methods
    public void auditInfo(String event, String details) {
        info("AUDIT: {} - {}", event, details);
    }
    
    public void auditWarning(String event, String details) {
        warn("AUDIT_WARNING: {} - {}", event, details);
    }
    
    public void auditError(String event, String details) {
        error("AUDIT_ERROR: {} - {}", event, details);
    }
    
    // Security logging
    public void securityInfo(String event, String details) {
        info("SECURITY: {} - {}", event, details);
    }
    
    public void securityWarning(String event, String details) {
        warn("SECURITY_WARNING: {} - {}", event, details);
    }
    
    public void securityError(String event, String details) {
        error("SECURITY_ERROR: {} - {}", event, details);
    }
    
    // Business logging
    public void businessEvent(String event, String details) {
        info("BUSINESS: {} - {}", event, details);
    }
    
    // Check if logging level is enabled
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }
    
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }
    
    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }
    
    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }
    
    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }
    
    // Access to underlying SLF4J logger if needed
    public Logger getUnderlyingLogger() {
        return logger;
    }
    
    // Enterprise-style detailed processing logging methods
    
    /**
     * Log adapter message processing entry
     */
    public void adapterMessageEntry(String adapterType, String username) {
        updateMDC();
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss:SSS"));
        info("{}    Information    {}: Request message entering the adapter processing with user {}", 
             timestamp, adapterType.toUpperCase(), username);
    }
    
    /**
     * Log module processing
     */
    public void moduleProcessing(String modulePath) {
        updateMDC();
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss:SSS"));
        info("{}    Information    MP: processing local module {}", timestamp, modulePath);
    }
    
    /**
     * Log application sending message
     */
    public void applicationSendMessage(String connectionName) {
        updateMDC();
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss:SSS"));
        info("{}    Information    Application attempting to send message asynchronously using connection {}", 
             timestamp, connectionName);
    }
    
    /**
     * Log queue operations
     */
    public void queueOperation(String operation, String details) {
        updateMDC();
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss:SSS"));
        info("{}    Information    {}", timestamp, operation);
        if (details != null && !details.isEmpty()) {
            info("{}    Information    {}", timestamp, details);
        }
    }
    
    /**
     * Log processing completion
     */
    public void processingCompleted(String adapterType) {
        updateMDC();
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss:SSS"));
        info("{}    Information    {}: Processing completed", timestamp, adapterType.toUpperCase());
    }
    
    /**
     * Log message status changes
     */
    public void messageStatus(String status) {
        updateMDC();
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss:SSS"));
        info("{}    Information    Message status set to {}", timestamp, status);
    }
    
    /**
     * Log file operations with detailed information
     */
    public void fileOperationDetailed(String operation, String filename, String details) {
        updateMDC();
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss:SSS"));
        info("{}    Information    {}: {} - {}", timestamp, operation.toUpperCase(), filename, details);
    }
    
    /**
     * Log directory scanning
     */
    public void directoryScanning(String directory, String pattern, int fileCount) {
        updateMDC();
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss:SSS"));
        info("{}    Information    FILE: Scanning directory {} for files matching {}", timestamp, directory, pattern);
        if (fileCount > 0) {
            info("{}    Information    FILE: Found {} files for processing", timestamp, fileCount);
        }
    }
    
    /**
     * Log SFTP connection operations
     */
    public void sftpConnection(String operation, String host, int port, String username) {
        updateMDC();
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss:SSS"));
        info("{}    Information    SFTP: {} {}:{} with user {}", timestamp, operation, host, port, username);
    }
    
    /**
     * Log email operations
     */
    public void emailOperation(String operation, String details) {
        updateMDC();
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss:SSS"));
        info("{}    Information    EMAIL: {} - {}", timestamp, operation, details);
    }
    
    /**
     * Log archival operations
     */
    public void archiveOperation(String filename, String archivePath) {
        updateMDC();
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss:SSS"));
        info("{}    Information    ARCHIVE: Moving file {} to {}", timestamp, filename, archivePath);
    }
    
    /**
     * Log flow execution steps
     */
    public void flowExecutionStep(String stepType, String stepName, String details) {
        updateMDC();
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss:SSS"));
        info("{}    Information    {}: {} - {}", timestamp, stepType.toUpperCase(), stepName, details);
    }
    
    /**
     * Log mapping execution
     */
    public void mappingExecution(String mappingType, String mappingName) {
        updateMDC();
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss:SSS"));
        info("{}    Information    Executing {} Mapping '{}'", timestamp, mappingType, mappingName);
    }
    
    /**
     * Log delivery to channel
     */
    public void channelDelivery(String channelName) {
        updateMDC();
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss:SSS"));
        info("{}    Information    Delivering to channel: {}", timestamp, channelName);
    }
    
    /**
     * Generate enterprise-style message ID for tracing
     */
    public static String generateMessageId() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String timestamp = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String sequence = String.format("%03d", (int)(Math.random() * 1000));
        return "MSG-" + timestamp + "-" + sequence;
    }
}