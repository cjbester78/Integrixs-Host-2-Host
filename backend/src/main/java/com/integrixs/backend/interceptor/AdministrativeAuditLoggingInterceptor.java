package com.integrixs.backend.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit logging interceptor for administrative controller operations.
 * Provides comprehensive request/response tracking with correlation IDs.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
@Component
public class AdministrativeAuditLoggingInterceptor implements HandlerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(AdministrativeAuditLoggingInterceptor.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("ADMINISTRATIVE_AUDIT");
    
    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String START_TIME_KEY = "startTime";
    private static final String REQUEST_ID_KEY = "requestId";
    private static final String USER_ID_KEY = "userId";
    private static final String OPERATION_KEY = "operation";
    private static final String REQUEST_PATH_KEY = "requestPath";
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) 
            throws Exception {
        
        // Generate or extract correlation ID
        String correlationId = getOrGenerateCorrelationId(request);
        String requestId = UUID.randomUUID().toString();
        String requestPath = request.getRequestURI();
        String operation = extractOperation(request);
        String userId = extractUserId(request);
        
        // Set MDC context
        MDC.put(CORRELATION_ID_KEY, correlationId);
        MDC.put(REQUEST_ID_KEY, requestId);
        MDC.put(START_TIME_KEY, String.valueOf(System.currentTimeMillis()));
        MDC.put(REQUEST_PATH_KEY, requestPath);
        MDC.put(OPERATION_KEY, operation);
        if (userId != null) {
            MDC.put(USER_ID_KEY, userId);
        }
        
        // Add correlation ID to response header
        response.setHeader("X-Correlation-ID", correlationId);
        response.setHeader("X-Request-ID", requestId);
        
        // Log request start
        auditLogger.info("Administrative request started - Method: {}, Path: {}, Operation: {}, User: {}, " +
                        "RemoteAddr: {}, UserAgent: {}, RequestId: {}", 
                        request.getMethod(), requestPath, operation, userId != null ? userId : "anonymous",
                        getClientIpAddress(request), request.getHeader("User-Agent"), requestId);
        
        // Log request parameters for audit trail
        logRequestParameters(request, operation);
        
        return true;
    }
    
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, 
                          ModelAndView modelAndView) throws Exception {
        
        String correlationId = MDC.get(CORRELATION_ID_KEY);
        String requestId = MDC.get(REQUEST_ID_KEY);
        String operation = MDC.get(OPERATION_KEY);
        
        auditLogger.info("Administrative request completed - Operation: {}, Status: {}, RequestId: {}", 
                         operation, response.getStatus(), requestId);
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, 
                               Exception ex) throws Exception {
        
        try {
            String correlationId = MDC.get(CORRELATION_ID_KEY);
            String requestId = MDC.get(REQUEST_ID_KEY);
            String startTimeStr = MDC.get(START_TIME_KEY);
            String operation = MDC.get(OPERATION_KEY);
            String userId = MDC.get(USER_ID_KEY);
            
            if (startTimeStr != null) {
                long startTime = Long.parseLong(startTimeStr);
                long duration = System.currentTimeMillis() - startTime;
                
                if (ex != null) {
                    // Log error completion
                    auditLogger.error("Administrative request failed - Operation: {}, Duration: {}ms, " +
                                     "Status: {}, Error: {}, User: {}, RequestId: {}", 
                                     operation, duration, response.getStatus(), ex.getMessage(), 
                                     userId != null ? userId : "anonymous", requestId, ex);
                } else {
                    // Log successful completion
                    auditLogger.info("Administrative request finished - Operation: {}, Duration: {}ms, " +
                                     "Status: {}, User: {}, RequestId: {}", 
                                     operation, duration, response.getStatus(), 
                                     userId != null ? userId : "anonymous", requestId);
                }
                
                // Log performance warning for slow requests
                if (duration > 5000) { // 5 seconds
                    logger.warn("Slow administrative request detected - Operation: {}, Duration: {}ms, RequestId: {}", 
                               operation, duration, requestId);
                }
            }
            
        } finally {
            // Always clear MDC to prevent memory leaks
            MDC.clear();
        }
    }
    
    /**
     * Get or generate correlation ID from request.
     */
    private String getOrGenerateCorrelationId(HttpServletRequest request) {
        // Try to get correlation ID from headers
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = request.getHeader("X-Request-ID");
        }
        
        // Generate new correlation ID if not found
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }
        
        return correlationId;
    }
    
    /**
     * Extract operation type from request path and method.
     */
    private String extractOperation(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        // Extract operation from administrative paths
        if (path.contains("/admin/logs")) {
            if ("GET".equals(method)) {
                return path.contains("/export") ? "logs_export" : "logs_view";
            } else if ("DELETE".equals(method)) {
                return "logs_cleanup";
            }
            return "logs_operation";
        }
        
        if (path.contains("/admin/users")) {
            return switch (method) {
                case "GET" -> "user_list";
                case "POST" -> "user_create";
                case "PUT", "PATCH" -> "user_update";
                case "DELETE" -> "user_delete";
                default -> "user_operation";
            };
        }
        
        if (path.contains("/admin/system")) {
            if (path.contains("/health")) {
                return "system_health";
            } else if (path.contains("/metrics")) {
                return "system_metrics";
            } else if (path.contains("/statistics")) {
                return "system_statistics";
            } else if (path.contains("/cleanup")) {
                return "system_cleanup";
            }
            return "system_operation";
        }
        
        if (path.contains("/admin/config")) {
            return switch (method) {
                case "GET" -> "config_list";
                case "PUT", "PATCH" -> "config_update";
                default -> "config_operation";
            };
        }
        
        if (path.contains("/admin/data-retention")) {
            return switch (method) {
                case "GET" -> "retention_list";
                case "POST" -> "retention_create";
                case "PUT", "PATCH" -> "retention_update";
                case "DELETE" -> "retention_delete";
                default -> "retention_operation";
            };
        }
        
        if (path.contains("/admin/environment")) {
            return switch (method) {
                case "GET" -> "environment_status";
                case "PUT", "PATCH" -> "environment_update";
                default -> "environment_operation";
            };
        }
        
        // Default operation
        return "admin_" + method.toLowerCase();
    }
    
    /**
     * Extract user ID from request (from security context or session).
     */
    private String extractUserId(HttpServletRequest request) {
        // Try to get from request attribute (set by security filter)
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr != null) {
            return userIdAttr.toString();
        }
        
        // Try to get from principal name
        if (request.getUserPrincipal() != null) {
            return request.getUserPrincipal().getName();
        }
        
        // Try to get from authorization header (basic auth)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // Could decode JWT token here to extract user ID
            // For now, just indicate that there's a bearer token
            return "jwt_user";
        }
        
        return null; // Anonymous request
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
     * Log request parameters for audit trail.
     */
    private void logRequestParameters(HttpServletRequest request, String operation) {
        try {
            StringBuilder params = new StringBuilder();
            
            // Log query parameters
            if (request.getQueryString() != null && !request.getQueryString().isEmpty()) {
                params.append("Query: ").append(sanitizeParameters(request.getQueryString()));
            }
            
            // Log path parameters
            String pathInfo = request.getPathInfo();
            if (pathInfo != null && !pathInfo.isEmpty()) {
                if (params.length() > 0) params.append(", ");
                params.append("Path: ").append(pathInfo);
            }
            
            // Log content type for POST/PUT requests
            if ("POST".equals(request.getMethod()) || "PUT".equals(request.getMethod())) {
                String contentType = request.getContentType();
                if (contentType != null) {
                    if (params.length() > 0) params.append(", ");
                    params.append("ContentType: ").append(contentType);
                }
            }
            
            if (params.length() > 0) {
                auditLogger.debug("Administrative request parameters - Operation: {}, Parameters: {}", 
                                 operation, params.toString());
            }
            
        } catch (Exception e) {
            logger.debug("Failed to log request parameters for operation: {}", operation, e);
        }
    }
    
    /**
     * Sanitize parameters to remove sensitive information.
     */
    private String sanitizeParameters(String params) {
        if (params == null) return "";
        
        // Mask common sensitive parameter names
        return params
            .replaceAll("(?i)(password|secret|key|token)=[^&]*", "$1=***")
            .replaceAll("(?i)(pass|pwd)=[^&]*", "$1=***");
    }
}