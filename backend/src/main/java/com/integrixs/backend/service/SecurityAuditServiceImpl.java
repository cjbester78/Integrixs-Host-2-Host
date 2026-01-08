package com.integrixs.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of Security Audit Service
 * Provides comprehensive security event logging and analysis
 */
@Service
public class SecurityAuditServiceImpl implements SecurityAuditService {

    private static final Logger logger = LoggerFactory.getLogger(SecurityAuditServiceImpl.class);
    private static final Logger securityLogger = LoggerFactory.getLogger("SECURITY");
    
    // In-memory storage for demonstration - in production, use database
    private final Map<String, List<SecurityEvent>> userSecurityEvents = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastActivityMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> failureCountMap = new ConcurrentHashMap<>();
    
    private final SecurityAnalyzer securityAnalyzer;

    public SecurityAuditServiceImpl() {
        this.securityAnalyzer = new SecurityAnalyzer();
    }

    @Override
    public void logAuthenticationAttempt(AuthenticationAuditEvent event) {
        try {
            // Set MDC for structured logging
            setSecurityMDC(event.username(), "AUTHENTICATION", event.timestamp());
            
            if (event.result() == AuthenticationResult.SUCCESS) {
                securityLogger.info("Authentication successful for user: {} from IP: {}", 
                    event.username(), event.ipAddress());
                resetFailureCount(event.username());
            } else {
                securityLogger.warn("Authentication failed for user: {} from IP: {} - Reason: {}", 
                    event.username(), event.ipAddress(), event.failureReason());
                incrementFailureCount(event.username());
            }
            
            // Store event for analysis
            storeSecurityEvent(event.username(), SecurityEvent.fromAuthentication(event));
            
        } finally {
            MDC.clear();
        }
    }

    @Override
    public void logAuthorizationDecision(AuthorizationAuditEvent event) {
        try {
            setSecurityMDC(event.username(), "AUTHORIZATION", event.timestamp());
            
            if (event.result() == AuthorizationResult.GRANTED) {
                securityLogger.info("Authorization granted for user: {} to access: {} with action: {}", 
                    event.username(), event.resource(), event.action());
            } else {
                securityLogger.warn("Authorization denied for user: {} to access: {} with action: {} - Reason: {}", 
                    event.username(), event.resource(), event.action(), event.denialReason());
            }
            
            storeSecurityEvent(event.userId(), SecurityEvent.fromAuthorization(event));
            
        } finally {
            MDC.clear();
        }
    }

    @Override
    public void logSecurityViolation(SecurityViolationEvent event) {
        try {
            setSecurityMDC(event.userId(), "VIOLATION", event.timestamp());
            
            securityLogger.error("Security violation detected - Type: {} - Severity: {} - Description: {} - User: {} - IP: {}", 
                event.violationType(), event.severity(), event.description(), 
                event.userId(), event.ipAddress());
            
            storeSecurityEvent(event.userId(), SecurityEvent.fromViolation(event));
            
        } finally {
            MDC.clear();
        }
    }

    @Override
    public void logTokenOperation(TokenAuditEvent event) {
        try {
            setSecurityMDC(event.username(), "TOKEN", event.timestamp());
            
            if (event.successful()) {
                securityLogger.info("Token operation successful - User: {} - Operation: {} - Type: {} - IP: {}", 
                    event.username(), event.operation(), event.tokenType(), event.ipAddress());
            } else {
                securityLogger.warn("Token operation failed - User: {} - Operation: {} - Type: {} - IP: {} - Reason: {}", 
                    event.username(), event.operation(), event.tokenType(), 
                    event.ipAddress(), event.failureReason());
            }
            
            storeSecurityEvent(event.userId(), SecurityEvent.fromToken(event));
            
        } finally {
            MDC.clear();
        }
    }

    @Override
    public void logConfigurationSecurityEvent(ConfigurationSecurityEvent event) {
        try {
            setSecurityMDC(event.userId(), "CONFIGURATION", event.timestamp());
            
            if (event.authorized()) {
                securityLogger.info("Configuration change authorized - User: {} - Key: {} - Operation: {}", 
                    event.userId(), event.configKey(), event.operation());
            } else {
                securityLogger.warn("Configuration change denied - User: {} - Key: {} - Operation: {} - Reason: {}", 
                    event.userId(), event.configKey(), event.operation(), event.denialReason());
            }
            
            storeSecurityEvent(event.userId(), SecurityEvent.fromConfiguration(event));
            
        } finally {
            MDC.clear();
        }
    }

    @Override
    public SecurityEventSummary getSecurityEventsForUser(String userId, LocalDateTime fromDate, LocalDateTime toDate) {
        List<SecurityEvent> events = userSecurityEvents.getOrDefault(userId, new ArrayList<>())
            .stream()
            .filter(event -> event.timestamp().isAfter(fromDate) && event.timestamp().isBefore(toDate))
            .collect(Collectors.toList());
        
        return createSummary(events);
    }

    @Override
    public SecurityEventSummary getRecentSecurityViolations(int hours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hours);
        
        List<SecurityEvent> allViolations = userSecurityEvents.values()
            .stream()
            .flatMap(List::stream)
            .filter(event -> event.timestamp().isAfter(cutoff))
            .filter(event -> event.eventType() == SecurityEventType.SECURITY_VIOLATION)
            .collect(Collectors.toList());
        
        return createSummary(allViolations);
    }

    @Override
    public SuspiciousActivityReport checkSuspiciousActivity(String userId, int hoursToCheck) {
        List<SecurityEvent> events = userSecurityEvents.getOrDefault(userId, new ArrayList<>())
            .stream()
            .filter(event -> event.timestamp().isAfter(LocalDateTime.now().minusHours(hoursToCheck)))
            .collect(Collectors.toList());
        
        return securityAnalyzer.analyzeActivity(userId, events);
    }

    // Private helper methods

    private void setSecurityMDC(String username, String eventType, LocalDateTime timestamp) {
        MDC.put("security.username", username);
        MDC.put("security.eventType", eventType);
        MDC.put("security.timestamp", timestamp.toString());
        MDC.put("security.correlationId", UUID.randomUUID().toString());
    }

    private void storeSecurityEvent(String userId, SecurityEvent event) {
        userSecurityEvents.computeIfAbsent(userId, k -> new ArrayList<>()).add(event);
        lastActivityMap.put(userId, event.timestamp());
        
        // Keep only last 1000 events per user to prevent memory issues
        List<SecurityEvent> events = userSecurityEvents.get(userId);
        if (events.size() > 1000) {
            events.subList(0, events.size() - 1000).clear();
        }
    }

    private void incrementFailureCount(String username) {
        failureCountMap.merge(username, 1, Integer::sum);
    }

    private void resetFailureCount(String username) {
        failureCountMap.remove(username);
    }

    private SecurityEventSummary createSummary(List<SecurityEvent> events) {
        int authFailures = (int) events.stream()
            .filter(e -> e.eventType() == SecurityEventType.AUTHENTICATION_FAILURE)
            .count();
        
        int authzDenials = (int) events.stream()
            .filter(e -> e.eventType() == SecurityEventType.AUTHORIZATION_DENIAL)
            .count();
        
        int violations = (int) events.stream()
            .filter(e -> e.eventType() == SecurityEventType.SECURITY_VIOLATION)
            .count();
        
        int tokenFailures = (int) events.stream()
            .filter(e -> e.eventType() == SecurityEventType.TOKEN_FAILURE)
            .count();
        
        Optional<LocalDateTime> lastSuspicious = events.stream()
            .filter(e -> e.eventType() == SecurityEventType.SECURITY_VIOLATION)
            .map(SecurityEvent::timestamp)
            .max(LocalDateTime::compareTo);
        
        return new SecurityEventSummary(
            events.size(), authFailures, authzDenials, violations, 
            tokenFailures, LocalDateTime.now(), lastSuspicious
        );
    }

    // Internal classes

    private enum SecurityEventType {
        AUTHENTICATION_SUCCESS, AUTHENTICATION_FAILURE,
        AUTHORIZATION_GRANTED, AUTHORIZATION_DENIAL,
        SECURITY_VIOLATION, TOKEN_SUCCESS, TOKEN_FAILURE,
        CONFIGURATION_CHANGE
    }

    private record SecurityEvent(
        SecurityEventType eventType,
        String description,
        LocalDateTime timestamp,
        Map<String, Object> metadata
    ) {
        
        public static SecurityEvent fromAuthentication(AuthenticationAuditEvent event) {
            SecurityEventType type = event.result() == AuthenticationResult.SUCCESS ? 
                SecurityEventType.AUTHENTICATION_SUCCESS : SecurityEventType.AUTHENTICATION_FAILURE;
            
            Map<String, Object> metadata = Map.of(
                "ipAddress", event.ipAddress(),
                "userAgent", event.userAgent(),
                "result", event.result().toString()
            );
            
            return new SecurityEvent(type, 
                "Authentication " + event.result().toString().toLowerCase(), 
                event.timestamp(), metadata);
        }
        
        public static SecurityEvent fromAuthorization(AuthorizationAuditEvent event) {
            SecurityEventType type = event.result() == AuthorizationResult.GRANTED ?
                SecurityEventType.AUTHORIZATION_GRANTED : SecurityEventType.AUTHORIZATION_DENIAL;
            
            Map<String, Object> metadata = Map.of(
                "resource", event.resource(),
                "action", event.action(),
                "role", event.role()
            );
            
            return new SecurityEvent(type, 
                "Authorization " + event.result().toString().toLowerCase(), 
                event.timestamp(), metadata);
        }
        
        public static SecurityEvent fromViolation(SecurityViolationEvent event) {
            Map<String, Object> metadata = Map.of(
                "violationType", event.violationType().toString(),
                "severity", event.severity().toString(),
                "ipAddress", event.ipAddress(),
                "resource", event.resource()
            );
            
            return new SecurityEvent(SecurityEventType.SECURITY_VIOLATION,
                event.description(), event.timestamp(), metadata);
        }
        
        public static SecurityEvent fromToken(TokenAuditEvent event) {
            SecurityEventType type = event.successful() ?
                SecurityEventType.TOKEN_SUCCESS : SecurityEventType.TOKEN_FAILURE;
            
            Map<String, Object> metadata = Map.of(
                "operation", event.operation().toString(),
                "tokenType", event.tokenType().toString(),
                "ipAddress", event.ipAddress()
            );
            
            return new SecurityEvent(type,
                "Token " + event.operation().toString().toLowerCase(),
                event.timestamp(), metadata);
        }
        
        public static SecurityEvent fromConfiguration(ConfigurationSecurityEvent event) {
            Map<String, Object> metadata = Map.of(
                "configKey", event.configKey(),
                "operation", event.operation().toString(),
                "authorized", event.authorized()
            );
            
            return new SecurityEvent(SecurityEventType.CONFIGURATION_CHANGE,
                "Configuration " + event.operation().toString().toLowerCase(),
                event.timestamp(), metadata);
        }
    }

    private static class SecurityAnalyzer {
        
        public SuspiciousActivityReport analyzeActivity(String userId, List<SecurityEvent> events) {
            if (events.isEmpty()) {
                return new SuspiciousActivityReport(userId, false, 0, null, 
                    "No activity to analyze", 0, LocalDateTime.now(), Map.of());
            }
            
            Map<SuspiciousActivityType, Integer> activityBreakdown = new EnumMap<>(SuspiciousActivityType.class);
            int suspicionScore = 0;
            SuspiciousActivityType primaryConcern = null;
            
            // Analyze rapid failed logins
            int failedLogins = countRecentFailures(events, 15); // Last 15 minutes
            if (failedLogins >= 5) {
                suspicionScore += 30;
                activityBreakdown.put(SuspiciousActivityType.RAPID_FAILED_LOGINS, failedLogins);
                primaryConcern = SuspiciousActivityType.RAPID_FAILED_LOGINS;
            }
            
            // Analyze unusual access patterns
            int offHoursActivity = countOffHoursActivity(events);
            if (offHoursActivity > 0) {
                suspicionScore += 15;
                activityBreakdown.put(SuspiciousActivityType.OFF_HOURS_ACTIVITY, offHoursActivity);
                if (primaryConcern == null) primaryConcern = SuspiciousActivityType.OFF_HOURS_ACTIVITY;
            }
            
            // Analyze security violations
            int violations = countViolations(events);
            if (violations > 0) {
                suspicionScore += violations * 20;
                activityBreakdown.put(SuspiciousActivityType.PRIVILEGE_ESCALATION_ATTEMPTS, violations);
                primaryConcern = SuspiciousActivityType.PRIVILEGE_ESCALATION_ATTEMPTS;
            }
            
            // Cap suspicion score at 100
            suspicionScore = Math.min(100, suspicionScore);
            
            String description = generateSuspicionDescription(suspicionScore, primaryConcern, activityBreakdown);
            
            return new SuspiciousActivityReport(userId, suspicionScore > 0, suspicionScore,
                primaryConcern, description, events.size(), LocalDateTime.now(), activityBreakdown);
        }
        
        private int countRecentFailures(List<SecurityEvent> events, int minutes) {
            LocalDateTime cutoff = LocalDateTime.now().minus(minutes, ChronoUnit.MINUTES);
            return (int) events.stream()
                .filter(e -> e.eventType() == SecurityEventType.AUTHENTICATION_FAILURE)
                .filter(e -> e.timestamp().isAfter(cutoff))
                .count();
        }
        
        private int countOffHoursActivity(List<SecurityEvent> events) {
            return (int) events.stream()
                .filter(e -> {
                    int hour = e.timestamp().getHour();
                    return hour < 6 || hour > 22; // Before 6 AM or after 10 PM
                })
                .count();
        }
        
        private int countViolations(List<SecurityEvent> events) {
            return (int) events.stream()
                .filter(e -> e.eventType() == SecurityEventType.SECURITY_VIOLATION)
                .count();
        }
        
        private String generateSuspicionDescription(int score, SuspiciousActivityType primary,
                                                   Map<SuspiciousActivityType, Integer> breakdown) {
            if (score == 0) {
                return "No suspicious activity detected";
            }
            
            StringBuilder description = new StringBuilder();
            description.append("Suspicion score: ").append(score).append("/100. ");
            
            if (primary != null) {
                description.append("Primary concern: ").append(primary.toString().replace("_", " "));
            }
            
            if (!breakdown.isEmpty()) {
                description.append(" (");
                breakdown.entrySet().stream()
                    .map(entry -> entry.getKey().toString().replace("_", " ") + ": " + entry.getValue())
                    .collect(Collectors.joining(", "));
                description.append(")");
            }
            
            return description.toString();
        }
    }
}