package com.integrixs.backend.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * TRANSACTIONAL log filter
 * Routes business transaction and security logs to TRANSACTIONAL tier (file + database)
 * 
 * This filter captures:
 * - Authentication events (login, logout, failures)
 * - File processing transactions
 * - Adapter execution events
 * - Business flow events
 * - Security monitoring events
 * - Error conditions requiring business analysis
 */
public class TransactionalLogFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        String loggerName = event.getLoggerName();
        String message = event.getFormattedMessage();
        
        // Accept authentication logs
        if (isAuthenticationLog(loggerName, message)) {
            return FilterReply.ACCEPT;
        }
        
        // Accept file processing logs
        if (isFileProcessingLog(loggerName, message)) {
            return FilterReply.ACCEPT;
        }
        
        // Accept adapter execution logs
        if (isAdapterLog(loggerName, message)) {
            return FilterReply.ACCEPT;
        }
        
        // Accept business flow logs
        if (isBusinessFlowLog(loggerName, message)) {
            return FilterReply.ACCEPT;
        }
        
        // Accept security monitoring logs
        if (isSecurityLog(loggerName, message)) {
            return FilterReply.ACCEPT;
        }
        
        // Accept error logs that need business analysis
        if (isBusinessErrorLog(event)) {
            return FilterReply.ACCEPT;
        }
        
        // Deny all other logs (they go to APPLICATION tier)
        return FilterReply.DENY;
    }
    
    /**
     * Check if this is an authentication-related log
     */
    private boolean isAuthenticationLog(String loggerName, String message) {
        // Direct authentication loggers
        if (loggerName.contains("H2HAuthenticationLogger") || 
            loggerName.contains("AuthenticationEventListener")) {
            return true;
        }
        
        // Authentication-related messages
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("authentication") || 
                   lowerMessage.contains("login") || 
                   lowerMessage.contains("logout") ||
                   lowerMessage.contains("auth_") ||
                   lowerMessage.contains("session");
        }
        
        return false;
    }
    
    /**
     * Check if this is a file processing log
     */
    private boolean isFileProcessingLog(String loggerName, String message) {
        // File processing components
        if (loggerName.contains("file") && !loggerName.contains("config")) {
            return true;
        }
        
        // File processing messages
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("file processing") ||
                   lowerMessage.contains("file transfer") ||
                   lowerMessage.contains("processing file") ||
                   lowerMessage.contains("file_");
        }
        
        return false;
    }
    
    /**
     * Check if this is an adapter execution log
     */
    private boolean isAdapterLog(String loggerName, String message) {
        // Adapter components
        if (loggerName.contains("adapters") || loggerName.contains("adapter.execution")) {
            return true;
        }
        
        // Adapter execution messages
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("adapter") ||
                   lowerMessage.contains("sftp") ||
                   lowerMessage.contains("email") ||
                   lowerMessage.contains("execution");
        }
        
        return false;
    }
    
    /**
     * Check if this is a business flow log
     */
    private boolean isBusinessFlowLog(String loggerName, String message) {
        // Business flow loggers
        if (loggerName.equals("BUSINESS_FLOW") || 
            loggerName.contains("flows") ||
            loggerName.contains("flow.execution")) {
            return true;
        }
        
        // Business flow messages
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("flow") ||
                   lowerMessage.contains("workflow") ||
                   lowerMessage.contains("process");
        }
        
        return false;
    }
    
    /**
     * Check if this is a security-related log
     */
    private boolean isSecurityLog(String loggerName, String message) {
        // Security components
        if (loggerName.contains("security") && !loggerName.contains("config")) {
            return true;
        }
        
        // Security messages
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("security") ||
                   lowerMessage.contains("unauthorized") ||
                   lowerMessage.contains("forbidden") ||
                   lowerMessage.contains("jwt") ||
                   lowerMessage.contains("token");
        }
        
        return false;
    }
    
    /**
     * Check if this is a business error requiring analysis
     */
    private boolean isBusinessErrorLog(ILoggingEvent event) {
        // Only ERROR level logs
        if (!event.getLevel().toString().equals("ERROR")) {
            return false;
        }
        
        String loggerName = event.getLoggerName();
        String message = event.getFormattedMessage();
        
        // Business components that have errors
        if (loggerName.contains("com.integrixs") && 
            !loggerName.contains("config") &&
            !loggerName.contains("TransactionLogService")) {
            return true;
        }
        
        // Business error messages
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("business") ||
                   lowerMessage.contains("transaction") ||
                   lowerMessage.contains("processing failed") ||
                   lowerMessage.contains("execution failed");
        }
        
        return false;
    }
}