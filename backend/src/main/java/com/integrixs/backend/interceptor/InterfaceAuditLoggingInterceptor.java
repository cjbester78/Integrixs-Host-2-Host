package com.integrixs.backend.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.integrixs.shared.util.AuditUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Audit logging interceptor for Interface Controller operations.
 * Provides comprehensive request/response tracking with correlation IDs.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
@Component
public class InterfaceAuditLoggingInterceptor implements HandlerInterceptor {
    
    private static final Logger auditLogger = LoggerFactory.getLogger("INTERFACE_AUDIT");
    private static final Logger logger = LoggerFactory.getLogger(InterfaceAuditLoggingInterceptor.class);
    
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String REQUEST_START_TIME_KEY = "requestStartTime";
    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String USER_ID_KEY = "userId";
    private static final String INTERFACE_OPERATION_KEY = "interfaceOperation";
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        
        // Only intercept Interface Controller methods
        if (!isInterfaceControllerMethod(handlerMethod)) {
            return true;
        }
        
        // Generate or extract correlation ID
        String correlationId = getOrGenerateCorrelationId(request);
        
        // Set up MDC for consistent logging
        MDC.put(CORRELATION_ID_KEY, correlationId);
        MDC.put(USER_ID_KEY, AuditUtils.getCurrentUserId());
        MDC.put(REQUEST_START_TIME_KEY, String.valueOf(System.currentTimeMillis()));
        
        // Extract interface operation context
        String interfaceOperation = extractInterfaceOperation(request, handlerMethod);
        MDC.put(INTERFACE_OPERATION_KEY, interfaceOperation);
        
        // Set correlation ID in response header
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        
        // Log request details
        logInterfaceRequest(request, handlerMethod, correlationId, interfaceOperation);
        
        return true;
    }
    
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        // This method is called after the handler method but before view rendering
        // We don't need special handling here for API controllers
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        
        try {
            if (!(handler instanceof HandlerMethod)) {
                return;
            }
            
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            
            if (!isInterfaceControllerMethod(handlerMethod)) {
                return;
            }
            
            // Calculate request duration
            long requestDuration = calculateRequestDuration();
            
            // Log response details
            logInterfaceResponse(request, response, handlerMethod, requestDuration, ex);
            
        } finally {
            // Always clear MDC to prevent memory leaks
            MDC.clear();
        }
    }
    
    /**
     * Check if the handler method is from Interface Controller.
     */
    private boolean isInterfaceControllerMethod(HandlerMethod handlerMethod) {
        return handlerMethod.getBeanType().getSimpleName().equals("InterfaceController");
    }
    
    /**
     * Get or generate correlation ID from request.
     */
    private String getOrGenerateCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = "INT-REQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        return correlationId;
    }
    
    /**
     * Extract interface operation context from request.
     */
    private String extractInterfaceOperation(HttpServletRequest request, HandlerMethod handlerMethod) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String methodName = handlerMethod.getMethod().getName();
        
        // Extract interface ID from path if present
        String interfaceId = extractInterfaceIdFromPath(uri);
        
        // Determine operation type
        String operationType = determineOperationType(method, uri, methodName);
        
        if (interfaceId != null) {
            return String.format("%s_%s_%s", operationType, interfaceId, methodName);
        } else {
            return String.format("%s_%s", operationType, methodName);
        }
    }
    
    /**
     * Extract interface ID from request URI.
     */
    private String extractInterfaceIdFromPath(String uri) {
        if (uri.contains("/interfaces/") && uri.length() > uri.indexOf("/interfaces/") + 12) {
            String[] pathSegments = uri.split("/");
            for (int i = 0; i < pathSegments.length; i++) {
                if ("interfaces".equals(pathSegments[i]) && i + 1 < pathSegments.length) {
                    String potentialId = pathSegments[i + 1];
                    if (potentialId.matches("[a-fA-F0-9-]{36}")) {  // UUID pattern
                        return potentialId;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Determine operation type based on HTTP method and URI.
     */
    private String determineOperationType(String method, String uri, String methodName) {
        if (uri.contains("/test")) return "TEST";
        if (uri.contains("/execute")) return "EXECUTE";
        if (uri.contains("/start")) return "START";
        if (uri.contains("/stop")) return "STOP";
        if (uri.contains("/enable")) return "ENABLE";
        if (uri.contains("/disable")) return "DISABLE";
        
        switch (method.toUpperCase()) {
            case "GET": return uri.contains("/interfaces/") && !uri.endsWith("/interfaces") ? "GET_DETAILS" : "LIST";
            case "POST": return "CREATE";
            case "PUT": return "UPDATE";
            case "DELETE": return "DELETE";
            default: return "UNKNOWN";
        }
    }
    
    /**
     * Calculate request duration from MDC.
     */
    private long calculateRequestDuration() {
        try {
            String startTimeStr = MDC.get(REQUEST_START_TIME_KEY);
            if (startTimeStr != null) {
                long startTime = Long.parseLong(startTimeStr);
                return System.currentTimeMillis() - startTime;
            }
        } catch (NumberFormatException e) {
            logger.warn("Could not parse request start time from MDC");
        }
        return -1;
    }
    
    /**
     * Log interface request details.
     */
    private void logInterfaceRequest(HttpServletRequest request, HandlerMethod handlerMethod, String correlationId, String interfaceOperation) {
        try {
            Map<String, Object> auditLog = new HashMap<>();
            auditLog.put("eventType", "INTERFACE_REQUEST");
            auditLog.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            auditLog.put("correlationId", correlationId);
            auditLog.put("userId", AuditUtils.getCurrentUserId());
            auditLog.put("username", AuditUtils.getCurrentUsername());
            auditLog.put("httpMethod", request.getMethod());
            auditLog.put("requestUri", request.getRequestURI());
            auditLog.put("queryString", request.getQueryString());
            auditLog.put("userAgent", request.getHeader("User-Agent"));
            auditLog.put("remoteAddr", getClientIpAddress(request));
            auditLog.put("interfaceOperation", interfaceOperation);
            auditLog.put("controllerMethod", handlerMethod.getMethod().getName());
            auditLog.put("controllerClass", handlerMethod.getBeanType().getSimpleName());
            
            // Log request parameters (excluding sensitive data)
            Map<String, String[]> parameters = request.getParameterMap();
            if (!parameters.isEmpty()) {
                Map<String, String> sanitizedParams = sanitizeParameters(parameters);
                auditLog.put("requestParameters", sanitizedParams);
            }
            
            auditLogger.info("Interface Request: {}", objectMapper.writeValueAsString(auditLog));
            
        } catch (Exception e) {
            logger.error("Error logging interface request audit: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Log interface response details.
     */
    private void logInterfaceResponse(HttpServletRequest request, HttpServletResponse response, HandlerMethod handlerMethod, long duration, Exception exception) {
        try {
            Map<String, Object> auditLog = new HashMap<>();
            auditLog.put("eventType", "INTERFACE_RESPONSE");
            auditLog.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            auditLog.put("correlationId", MDC.get(CORRELATION_ID_KEY));
            auditLog.put("userId", MDC.get(USER_ID_KEY));
            auditLog.put("interfaceOperation", MDC.get(INTERFACE_OPERATION_KEY));
            auditLog.put("httpStatus", response.getStatus());
            auditLog.put("durationMs", duration);
            auditLog.put("success", exception == null && response.getStatus() < 400);
            
            if (exception != null) {
                auditLog.put("exceptionType", exception.getClass().getSimpleName());
                auditLog.put("exceptionMessage", exception.getMessage());
                auditLog.put("hasError", true);
            } else {
                auditLog.put("hasError", false);
            }
            
            // Add response headers that are useful for auditing
            auditLog.put("contentType", response.getContentType());
            auditLog.put("contentLength", response.getHeader("Content-Length"));
            
            String logLevel = exception != null || response.getStatus() >= 400 ? "WARN" : "INFO";
            if ("WARN".equals(logLevel)) {
                auditLogger.warn("Interface Response: {}", objectMapper.writeValueAsString(auditLog));
            } else {
                auditLogger.info("Interface Response: {}", objectMapper.writeValueAsString(auditLog));
            }
            
        } catch (Exception e) {
            logger.error("Error logging interface response audit: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get client IP address from request.
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
     * Sanitize request parameters to remove sensitive data.
     */
    private Map<String, String> sanitizeParameters(Map<String, String[]> parameters) {
        Map<String, String> sanitized = new HashMap<>();
        
        for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
            String paramName = entry.getKey().toLowerCase();
            String[] values = entry.getValue();
            
            // Skip sensitive parameters
            if (paramName.contains("password") || paramName.contains("secret") || 
                paramName.contains("token") || paramName.contains("auth")) {
                sanitized.put(entry.getKey(), "[REDACTED]");
            } else {
                sanitized.put(entry.getKey(), values.length > 0 ? values[0] : "");
            }
        }
        
        return sanitized;
    }
}