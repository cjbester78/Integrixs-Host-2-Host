package com.integrixs.backend.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import com.integrixs.core.repository.SystemLogRepository;
import com.integrixs.shared.model.SystemLog;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Custom Logback appender that duplicates all application logs to the database
 * This allows for database-based querying and categorization of logs
 */
@Component
public class DatabaseLogAppender extends AppenderBase<ILoggingEvent> {
    
    private SystemLogRepository systemLogRepository;
    private ApplicationContext applicationContext;
    private boolean initialized = false;
    
    /**
     * Set the application context to resolve dependencies
     */
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    /**
     * Initialize the appender with Spring dependencies
     */
    private void initializeIfNeeded() {
        if (!initialized && applicationContext != null) {
            try {
                this.systemLogRepository = applicationContext.getBean(SystemLogRepository.class);
                this.initialized = true;
            } catch (Exception e) {
                // Repository not available yet - this is expected during startup
                // We'll try again on the next log event
            }
        }
    }
    
    @Override
    protected void append(ILoggingEvent eventObject) {
        initializeIfNeeded();
        
        // Skip if repository is not available yet (during application startup)
        if (systemLogRepository == null) {
            return;
        }
        
        try {
            SystemLog log = convertToSystemLog(eventObject);
            systemLogRepository.insertLog(log);
        } catch (Exception e) {
            // Don't let logging errors break the application
            // We could optionally log this error to a different appender
            addError("Failed to append log to database", e);
        }
    }
    
    /**
     * Convert Logback ILoggingEvent to SystemLog entity
     */
    private SystemLog convertToSystemLog(ILoggingEvent event) {
        SystemLog log = new SystemLog();
        
        // Basic log information
        log.setTimestamp(LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(event.getTimeStamp()), 
            ZoneId.systemDefault()));
        log.setLogLevel(mapLogLevel(event.getLevel()));
        log.setLoggerName(event.getLoggerName());
        log.setThreadName(event.getThreadName());
        log.setMessage(event.getMessage());
        log.setFormattedMessage(event.getFormattedMessage());
        
        // Determine log category based on logger name and MDC
        log.setLogCategory(determineLogCategory(event));
        
        // Extract context information from MDC
        extractMdcData(log, event);
        
        // Extract web request context if available
        extractWebContext(log);
        
        // Extract exception information
        extractExceptionInfo(log, event);
        
        // Set application context
        log.setApplicationName("h2h-backend");
        log.setEnvironment(getEnvironment());
        log.setServerHostname(getServerHostname());
        
        return log;
    }
    
    /**
     * Map Logback log level to SystemLog.LogLevel
     */
    private SystemLog.LogLevel mapLogLevel(ch.qos.logback.classic.Level level) {
        return switch (level.toString()) {
            case "TRACE" -> SystemLog.LogLevel.TRACE;
            case "DEBUG" -> SystemLog.LogLevel.DEBUG;
            case "INFO" -> SystemLog.LogLevel.INFO;
            case "WARN" -> SystemLog.LogLevel.WARN;
            case "ERROR" -> SystemLog.LogLevel.ERROR;
            default -> SystemLog.LogLevel.INFO;
        };
    }
    
    /**
     * Determine the log category based on logger name and context
     */
    private SystemLog.LogCategory determineLogCategory(ILoggingEvent event) {
        String loggerName = event.getLoggerName().toLowerCase();
        Map<String, String> mdcMap = event.getMDCPropertyMap();
        
        // Check MDC for explicit category
        if (mdcMap != null) {
            String mdcCategory = mdcMap.get("logCategory");
            if (mdcCategory != null) {
                try {
                    return SystemLog.LogCategory.valueOf(mdcCategory.toUpperCase());
                } catch (IllegalArgumentException e) {
                    // Invalid category in MDC, fall through to logic below
                }
            }
            
            // Check for execution IDs to determine category
            if (mdcMap.containsKey("adapterId") || mdcMap.containsKey("adapterName")) {
                return SystemLog.LogCategory.ADAPTER_EXECUTION;
            }
            if (mdcMap.containsKey("flowId") || mdcMap.containsKey("flowName")) {
                return SystemLog.LogCategory.FLOW_EXECUTION;
            }
        }
        
        // Determine category based on logger name patterns
        if (loggerName.contains("adapter") && loggerName.contains("execution")) {
            return SystemLog.LogCategory.ADAPTER_EXECUTION;
        }
        if (loggerName.contains("flow") && loggerName.contains("execution")) {
            return SystemLog.LogCategory.FLOW_EXECUTION;
        }
        if (loggerName.contains("security") || loggerName.contains("auth")) {
            return SystemLog.LogCategory.AUTHENTICATION;
        }
        if (loggerName.contains("repository") || loggerName.contains("database") || loggerName.contains("jdbc")) {
            return SystemLog.LogCategory.DATABASE;
        }
        if (loggerName.contains("scheduler") || loggerName.contains("task")) {
            return SystemLog.LogCategory.SCHEDULER;
        }
        if (loggerName.contains("controller") || loggerName.contains("rest") || loggerName.contains("api")) {
            return SystemLog.LogCategory.API;
        }
        
        return SystemLog.LogCategory.SYSTEM;
    }
    
    /**
     * Extract data from MDC (Mapped Diagnostic Context)
     */
    private void extractMdcData(SystemLog log, ILoggingEvent event) {
        Map<String, String> mdcMap = event.getMDCPropertyMap();
        if (mdcMap == null || mdcMap.isEmpty()) {
            return;
        }
        
        // Extract specific MDC properties
        String correlationId = mdcMap.get("correlationId");
        if (correlationId != null) {
            try {
                log.setCorrelationId(UUID.fromString(correlationId));
            } catch (IllegalArgumentException e) {
                // Invalid UUID format
            }
        }
        
        String userId = mdcMap.get("userId");
        if (userId != null) {
            try {
                log.setUserId(UUID.fromString(userId));
            } catch (IllegalArgumentException e) {
                // Invalid UUID format
            }
        }
        
        String adapterId = mdcMap.get("adapterId");
        if (adapterId != null) {
            try {
                log.setAdapterId(UUID.fromString(adapterId));
            } catch (IllegalArgumentException e) {
                // Invalid UUID format
            }
        }
        
        String adapterName = mdcMap.get("adapterName");
        if (adapterName != null) {
            log.setAdapterName(adapterName);
        }
        
        String flowId = mdcMap.get("flowId");
        if (flowId != null) {
            try {
                log.setFlowId(UUID.fromString(flowId));
            } catch (IllegalArgumentException e) {
                // Invalid UUID format
            }
        }
        
        String flowName = mdcMap.get("flowName");
        if (flowName != null) {
            log.setFlowName(flowName);
        }
        
        String executionId = mdcMap.get("executionId");
        if (executionId != null) {
            try {
                log.setExecutionId(UUID.fromString(executionId));
            } catch (IllegalArgumentException e) {
                // Invalid UUID format
            }
        }
        
        String requestId = mdcMap.get("requestId");
        if (requestId != null) {
            log.setRequestId(requestId);
        }
        
        String sessionId = mdcMap.get("sessionId");
        if (sessionId != null) {
            log.setSessionId(sessionId);
        }
        
        String executionTime = mdcMap.get("executionTimeMs");
        if (executionTime != null) {
            try {
                log.setExecutionTimeMs(Long.parseLong(executionTime));
            } catch (NumberFormatException e) {
                // Invalid number format
            }
        }
        
        // Store all MDC data as JSON
        Map<String, Object> mdcData = new HashMap<>(mdcMap);
        log.setMdcData(mdcData);
    }
    
    /**
     * Extract web request context information
     */
    private void extractWebContext(SystemLog log) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                
                log.setRequestMethod(request.getMethod());
                log.setRequestUri(request.getRequestURI());
                log.setRemoteAddress(getClientIpAddress(request));
                log.setUserAgent(request.getHeader("User-Agent"));
                
                // Session ID
                if (log.getSessionId() == null) {
                    try {
                        log.setSessionId(request.getSession(false) != null ? 
                                       request.getSession(false).getId() : null);
                    } catch (Exception e) {
                        // Session not available
                    }
                }
                
                // Request ID from header or generate one
                String requestId = request.getHeader("X-Request-ID");
                if (requestId == null) {
                    requestId = request.getHeader("X-Correlation-ID");
                }
                if (requestId != null && log.getRequestId() == null) {
                    log.setRequestId(requestId);
                }
            }
        } catch (Exception e) {
            // Web context not available (not in web request thread)
        }
    }
    
    /**
     * Extract exception information
     */
    private void extractExceptionInfo(SystemLog log, ILoggingEvent event) {
        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy != null) {
            log.setExceptionClass(throwableProxy.getClassName());
            log.setExceptionMessage(throwableProxy.getMessage());
            
            // Generate stack trace
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            
            // Build stack trace string
            StringBuilder stackTrace = new StringBuilder();
            stackTrace.append(throwableProxy.getClassName())
                     .append(": ")
                     .append(throwableProxy.getMessage())
                     .append("\n");
            
            if (throwableProxy.getStackTraceElementProxyArray() != null) {
                for (int i = 0; i < Math.min(throwableProxy.getStackTraceElementProxyArray().length, 50); i++) {
                    stackTrace.append("\tat ")
                             .append(throwableProxy.getStackTraceElementProxyArray()[i].toString())
                             .append("\n");
                }
            }
            
            log.setStackTrace(stackTrace.toString());
        }
    }
    
    /**
     * Get client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Get the current environment
     */
    private String getEnvironment() {
        return System.getProperty("spring.profiles.active", "dev");
    }
    
    /**
     * Get the server hostname
     */
    private String getServerHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}