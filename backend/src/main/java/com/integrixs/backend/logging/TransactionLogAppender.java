package com.integrixs.backend.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.integrixs.core.service.TransactionLogService;
import com.integrixs.shared.model.TransactionLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom Logback appender that writes transaction logs to the database
 * 
 * This appender captures business transaction events and stores them in the
 * transaction_logs table for security monitoring and business analytics.
 * 
 * Used by the TRANSACTIONAL tier for dual logging (files + database).
 */
public class TransactionLogAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private TransactionLogService transactionLogService;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void start() {
        // Try to get the TransactionLogService from Spring context
        try {
            // This will be injected by Spring when the appender is created
            super.start();
        } catch (Exception e) {
            addError("Failed to start TransactionLogAppender: " + e.getMessage(), e);
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        try {
            // Only process if we have the service available
            if (transactionLogService == null) {
                // Try to get it from Spring context through static access
                transactionLogService = getTransactionLogServiceFromContext();
                if (transactionLogService == null) {
                    return; // Skip logging if service not available
                }
            }

            // Create transaction log from the logging event
            TransactionLog transactionLog = createTransactionLogFromEvent(event);
            
            // Log asynchronously to avoid blocking the calling thread
            transactionLogService.logAsync(transactionLog);
            
        } catch (Exception e) {
            addError("Failed to append transaction log to database: " + e.getMessage(), e);
        }
    }

    /**
     * Create a TransactionLog entity from a Logback logging event
     */
    private TransactionLog createTransactionLogFromEvent(ILoggingEvent event) {
        String loggerName = event.getLoggerName();
        String message = event.getFormattedMessage();
        
        // Determine category from logger name and message
        String category = determineCategory(loggerName, message);
        
        // Determine component and source
        String component = determineComponent(loggerName);
        String source = determineSource(loggerName, message);
        
        // Create the transaction log
        TransactionLog log = new TransactionLog(category, component, source, message);
        
        // Set log level
        log.setLevel(TransactionLog.LogLevel.valueOf(event.getLevel().toString()));
        
        // Extract MDC context
        extractMDCContext(log);
        
        // Add event details
        addEventDetails(log, event);
        
        return log;
    }

    /**
     * Determine the log category from logger name and message
     */
    private String determineCategory(String loggerName, String message) {
        // Authentication logs
        if (loggerName.contains("AuthenticationLogger") || loggerName.contains("AuthenticationEventListener")) {
            if (message.contains("SUCCESS")) return "AUTH_SUCCESS";
            if (message.contains("FAILED")) return "AUTH_FAILED";
            if (message.contains("ATTEMPT")) return "AUTH_ATTEMPT";
            if (message.contains("LOGOUT")) return "LOGOUT";
            return "AUTHENTICATION";
        }
        
        // File processing logs
        if (loggerName.contains("file") || message.toLowerCase().contains("file")) {
            return "FILE_PROCESSING";
        }
        
        // Adapter logs
        if (loggerName.contains("adapter")) {
            return "ADAPTER_EXECUTION";
        }
        
        // Flow logs
        if (loggerName.contains("flow")) {
            return "FLOW_EXECUTION";
        }
        
        // Security logs
        if (loggerName.contains("security")) {
            return "SECURITY";
        }
        
        // Default
        return "APPLICATION";
    }

    /**
     * Determine the component from logger name
     */
    private String determineComponent(String loggerName) {
        if (loggerName.contains("authentication")) return "authentication";
        if (loggerName.contains("adapter")) return "adapter";
        if (loggerName.contains("flow")) return "flow";
        if (loggerName.contains("file")) return "file_processor";
        if (loggerName.contains("security")) return "security";
        
        // Extract package-based component
        String[] parts = loggerName.split("\\.");
        if (parts.length > 3) {
            return parts[3]; // com.integrixs.backend.[component]
        }
        
        return "application";
    }

    /**
     * Determine the source from logger name and message
     */
    private String determineSource(String loggerName, String message) {
        if (loggerName.contains("authentication") || message.toLowerCase().contains("auth")) {
            return "security";
        }
        if (loggerName.contains("adapter") || loggerName.contains("file")) {
            return "file_processing";
        }
        if (loggerName.contains("flow")) {
            return "workflow";
        }
        
        return "application";
    }

    /**
     * Extract context information from MDC (Mapped Diagnostic Context)
     */
    private void extractMDCContext(TransactionLog log) {
        try {
            // Get common MDC values
            log.setUsername(MDC.get("username"));
            log.setIpAddress(MDC.get("ipAddress"));
            log.setUserAgent(MDC.get("userAgent"));
            log.setSessionId(MDC.get("sessionId"));
            log.setCorrelationId(MDC.get("correlationId"));
            
            // Get adapter-specific values
            String adapterIdStr = MDC.get("adapterId");
            if (adapterIdStr != null) {
                try {
                    log.setAdapterId(java.util.UUID.fromString(adapterIdStr));
                } catch (Exception e) {
                    // Ignore invalid UUID
                }
            }
            
            String executionIdStr = MDC.get("executionId");
            if (executionIdStr != null) {
                try {
                    log.setExecutionId(java.util.UUID.fromString(executionIdStr));
                } catch (Exception e) {
                    // Ignore invalid UUID
                }
            }
            
            log.setFileName(MDC.get("fileName"));
            
        } catch (Exception e) {
            // Ignore MDC extraction errors
        }
    }

    /**
     * Add additional event details as JSON
     */
    private void addEventDetails(TransactionLog log, ILoggingEvent event) {
        try {
            Map<String, Object> details = new HashMap<>();
            
            // Add event timing
            details.put("timestamp", event.getTimeStamp());
            details.put("thread", event.getThreadName());
            details.put("level", event.getLevel().toString());
            details.put("logger", event.getLoggerName());
            
            // Add exception information if present
            if (event.getThrowableProxy() != null) {
                details.put("exception", event.getThrowableProxy().getClassName());
                details.put("exceptionMessage", event.getThrowableProxy().getMessage());
            }
            
            // Add marker information if present
            if (event.getMarker() != null) {
                details.put("marker", event.getMarker().getName());
            }
            
            // Convert to JSON
            log.setDetails(objectMapper.writeValueAsString(details));
            
        } catch (Exception e) {
            // Ignore details serialization errors
        }
    }

    /**
     * Get TransactionLogService from Spring context
     * This is a fallback method if dependency injection doesn't work
     */
    private TransactionLogService getTransactionLogServiceFromContext() {
        try {
            return com.integrixs.backend.util.SpringContext.getBean(TransactionLogService.class);
        } catch (Exception e) {
            // Silently ignore - logging appender should never cause application issues
            return null;
        }
    }

    // Setter for dependency injection
    public void setTransactionLogService(TransactionLogService transactionLogService) {
        this.transactionLogService = transactionLogService;
    }
}