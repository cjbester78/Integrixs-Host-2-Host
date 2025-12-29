package com.integrixs.backend.security;

import com.integrixs.backend.logging.H2HAuthenticationLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Spring Security Authentication Event Listener
 * Automatically captures all authentication events and delegates to H2HAuthenticationLogger
 * 
 * This component integrates seamlessly with Spring Security's event system to ensure
 * all authentication activities are logged without requiring manual instrumentation.
 */
@Component
public class AuthenticationEventListener {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationEventListener.class);
    
    private final H2HAuthenticationLogger authLogger;
    private final JwtTokenService jwtTokenService;
    
    public AuthenticationEventListener(H2HAuthenticationLogger authLogger, JwtTokenService jwtTokenService) {
        this.authLogger = authLogger;
        this.jwtTokenService = jwtTokenService;
    }
    
    /**
     * Handle successful authentication events
     * Triggered when Spring Security successfully authenticates a user
     */
    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        try {
            Authentication auth = event.getAuthentication();
            String username = auth.getName();
            
            // Get user roles
            String roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
            
            // Generate session ID for tracking
            String sessionId = generateSessionId();
            
            // Get client context
            String ipAddress = getClientIpAddress();
            String userAgent = getUserAgent();
            
            // Calculate token expiry
            String tokenExpiry = calculateTokenExpiry();
            
            // Log authentication success
            authLogger.logAuthenticationSuccess(username, sessionId, ipAddress, userAgent, roles, tokenExpiry);
            
            logger.debug("Authentication success event logged for user: {}", username);
            
        } catch (Exception e) {
            logger.error("Failed to process authentication success event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Handle authentication failure events
     * Triggered when Spring Security fails to authenticate a user
     */
    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        try {
            // Extract username (may be null for invalid usernames)
            String username = extractUsername(event.getAuthentication());
            
            // Get failure reason
            String failureReason = event.getException().getMessage();
            
            // Get client context
            String ipAddress = getClientIpAddress();
            String userAgent = getUserAgent();
            
            // Log authentication failure
            authLogger.logAuthenticationFailure(username, failureReason, "JWT", ipAddress, userAgent);
            
            logger.debug("Authentication failure event logged for user: {} - Reason: {}", 
                        username != null ? username : "UNKNOWN", failureReason);
            
        } catch (Exception e) {
            logger.error("Failed to process authentication failure event: {}", e.getMessage(), e);
        }
    }
    
    // Helper methods
    
    /**
     * Generate a unique session ID for tracking user sessions
     */
    private String generateSessionId() {
        return "H2H-SID-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Extract client IP address from the current request
     * Handles X-Forwarded-For header for proxy/load balancer scenarios
     */
    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                
                // Check X-Forwarded-For header first (for load balancers/proxies)
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    // Get the first IP if there are multiple (client IP is first)
                    return xForwardedFor.split(",")[0].trim();
                }
                
                // Check X-Real-IP header (some proxies use this)
                String xRealIp = request.getHeader("X-Real-IP");
                if (xRealIp != null && !xRealIp.isEmpty()) {
                    return xRealIp.trim();
                }
                
                // Fall back to remote address
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            logger.debug("Could not retrieve client IP address: {}", e.getMessage());
        }
        return "unknown";
    }
    
    /**
     * Extract User-Agent from the current request
     */
    private String getUserAgent() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getHeader("User-Agent");
            }
        } catch (Exception e) {
            logger.debug("Could not retrieve User-Agent: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Extract username from authentication object
     * Handles cases where authentication object may be null or have null principal
     */
    private String extractUsername(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        
        try {
            return authentication.getName();
        } catch (Exception e) {
            // Some authentication failures don't have valid principals
            Object principal = authentication.getPrincipal();
            if (principal instanceof String) {
                return (String) principal;
            }
            return null;
        }
    }
    
    /**
     * Calculate JWT token expiry time
     */
    private String calculateTokenExpiry() {
        try {
            // Get token expiration from JWT service configuration
            long expirationMs = jwtTokenService.getExpirationTime();
            Instant expiryTime = Instant.now().plusMillis(expirationMs);
            
            return DateTimeFormatter.ISO_INSTANT
                .withZone(ZoneId.systemDefault())
                .format(expiryTime);
                
        } catch (Exception e) {
            logger.debug("Could not calculate token expiry: {}", e.getMessage());
            return "unknown";
        }
    }
    
    /**
     * Get current HTTP request URI for additional context
     */
    private String getRequestUri() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getRequestURI();
            }
        } catch (Exception e) {
            logger.debug("Could not retrieve request URI: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Get request method (GET, POST, etc.)
     */
    private String getRequestMethod() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getMethod();
            }
        } catch (Exception e) {
            logger.debug("Could not retrieve request method: {}", e.getMessage());
        }
        return null;
    }
}