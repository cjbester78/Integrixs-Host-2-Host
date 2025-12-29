package com.integrixs.backend.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * APPLICATION log filter
 * Routes infrastructure and general application logs to APPLICATION tier (file only)
 * 
 * This filter captures:
 * - Spring Framework startup/shutdown logs
 * - Infrastructure components (database, security, cache)
 * - General application debug/info logs
 * - System configuration logs
 * - Non-business transaction logs
 */
public class ApplicationLogFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        String loggerName = event.getLoggerName();
        
        // Skip authentication and file processing logs (these go to TRANSACTIONAL tier)
        if (isTransactionalLog(loggerName)) {
            return FilterReply.DENY;
        }
        
        // Accept application logs
        if (isApplicationLog(loggerName)) {
            return FilterReply.ACCEPT;
        }
        
        // Default: accept all other logs for APPLICATION tier
        return FilterReply.ACCEPT;
    }
    
    /**
     * Check if this is a transactional log that should go to TRANSACTIONAL tier
     */
    private boolean isTransactionalLog(String loggerName) {
        // Authentication logs
        if (loggerName.contains("H2HAuthenticationLogger") || 
            loggerName.contains("AuthenticationEventListener")) {
            return true;
        }
        
        // File processing and adapter logs
        if (loggerName.contains("adapters") || loggerName.contains("flows")) {
            return true;
        }
        
        // Business transaction logs
        if (loggerName.equals("BUSINESS_FLOW") || loggerName.contains("flow.execution")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if this is an application log for APPLICATION tier
     */
    private boolean isApplicationLog(String loggerName) {
        // Spring Framework
        if (loggerName.startsWith("org.springframework") || 
            loggerName.startsWith("org.apache") ||
            loggerName.startsWith("org.hibernate") ||
            loggerName.startsWith("com.zaxxer.hikari")) {
            return true;
        }
        
        // Application infrastructure
        if (loggerName.contains("config") || 
            loggerName.contains("security.config") ||
            loggerName.contains("repository") ||
            loggerName.contains("TransactionLogService")) {
            return true;
        }
        
        // General application logs
        if (loggerName.startsWith("com.integrixs") && !isTransactionalLog(loggerName)) {
            return true;
        }
        
        return false;
    }
}