package com.integrixs.core.service;

import com.integrixs.shared.model.UserManagementError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Service for assessing security threats and determining threat levels
 * Provides intelligent threat analysis based on authentication patterns
 */
@Service
public class ThreatAssessmentService {
    
    private static final Logger logger = LoggerFactory.getLogger(ThreatAssessmentService.class);
    
    private final UserManagementErrorService errorService;
    private final UserSessionService sessionService;
    
    // Known malicious IP patterns
    private static final List<String> KNOWN_MALICIOUS_PATTERNS = Arrays.asList(
        "127.0.0.1", // Obviously not malicious, just for testing
        "10.0.0", // Example patterns - update with real threat intelligence
        "192.168"  // Local network ranges for testing
    );
    
    // Suspicious user agent patterns
    private static final List<String> SUSPICIOUS_USER_AGENTS = Arrays.asList(
        "curl", "wget", "python", "bot", "crawler", "scanner"
    );
    
    public ThreatAssessmentService(UserManagementErrorService errorService, UserSessionService sessionService) {
        this.errorService = errorService;
        this.sessionService = sessionService;
    }
    
    /**
     * Assess threat level for authentication failure
     */
    public UserManagementError.ThreatLevel assessAuthenticationThreat(String ipAddress, String username, 
                                                                     String failureReason, String userAgent) {
        try {
            UserManagementError.ThreatLevel threatLevel = UserManagementError.ThreatLevel.LOW;
            
            // Check for known malicious IP patterns
            if (isKnownMaliciousIp(ipAddress)) {
                threatLevel = UserManagementError.ThreatLevel.CRITICAL;
                logger.warn("CRITICAL threat detected - Known malicious IP: {}", ipAddress);
                return threatLevel;
            }
            
            // Check for suspicious user agent
            if (isSuspiciousUserAgent(userAgent)) {
                threatLevel = UserManagementError.ThreatLevel.HIGH;
                logger.warn("HIGH threat detected - Suspicious user agent: {} from IP: {}", userAgent, ipAddress);
            }
            
            // Check for repeated failures from IP
            if (hasRepeatedFailures(ipAddress)) {
                threatLevel = UserManagementError.ThreatLevel.HIGH;
                logger.warn("HIGH threat detected - Repeated failures from IP: {}", ipAddress);
            }
            
            // Check for brute force patterns
            if (isBruteForcePattern(ipAddress, username)) {
                threatLevel = UserManagementError.ThreatLevel.HIGH;
                logger.warn("HIGH threat detected - Brute force pattern from IP: {} targeting user: {}", ipAddress, username);
            }
            
            // Check for credential stuffing patterns
            if (isCredentialStuffingPattern(ipAddress)) {
                threatLevel = UserManagementError.ThreatLevel.MEDIUM;
                logger.warn("MEDIUM threat detected - Credential stuffing pattern from IP: {}", ipAddress);
            }
            
            // Check for suspicious failure reasons
            if (isSuspiciousFailureReason(failureReason)) {
                if (threatLevel.ordinal() < UserManagementError.ThreatLevel.MEDIUM.ordinal()) {
                    threatLevel = UserManagementError.ThreatLevel.MEDIUM;
                }
                logger.warn("MEDIUM threat detected - Suspicious failure reason: {} from IP: {}", failureReason, ipAddress);
            }
            
            return threatLevel;
            
        } catch (Exception e) {
            logger.error("Error assessing authentication threat for IP: {} - {}", ipAddress, e.getMessage(), e);
            return UserManagementError.ThreatLevel.LOW; // Fail safe
        }
    }
    
    /**
     * Assess threat level for session management
     */
    public UserManagementError.ThreatLevel assessSessionThreat(String ipAddress, String username, String userAgent) {
        try {
            UserManagementError.ThreatLevel threatLevel = UserManagementError.ThreatLevel.LOW;
            
            // Check for suspicious session activity
            if (sessionService.isSuspiciousIpActivity(ipAddress, 10)) {
                threatLevel = UserManagementError.ThreatLevel.MEDIUM;
                logger.warn("MEDIUM threat detected - Suspicious session activity from IP: {}", ipAddress);
            }
            
            // Check for session hijacking patterns
            if (isPotentialSessionHijacking(ipAddress, username)) {
                threatLevel = UserManagementError.ThreatLevel.HIGH;
                logger.warn("HIGH threat detected - Potential session hijacking for user: {} from IP: {}", username, ipAddress);
            }
            
            return threatLevel;
            
        } catch (Exception e) {
            logger.error("Error assessing session threat for IP: {} - {}", ipAddress, e.getMessage(), e);
            return UserManagementError.ThreatLevel.LOW; // Fail safe
        }
    }
    
    /**
     * Check if IP address is in known malicious patterns
     */
    public boolean isKnownMaliciousIp(String ipAddress) {
        if (ipAddress == null) return false;
        
        return KNOWN_MALICIOUS_PATTERNS.stream()
            .anyMatch(pattern -> ipAddress.contains(pattern));
    }
    
    /**
     * Check for suspicious user agent patterns
     */
    public boolean isSuspiciousUserAgent(String userAgent) {
        if (userAgent == null) return false;
        
        String lowerUserAgent = userAgent.toLowerCase();
        return SUSPICIOUS_USER_AGENTS.stream()
            .anyMatch(pattern -> lowerUserAgent.contains(pattern.toLowerCase()));
    }
    
    /**
     * Check for repeated authentication failures
     */
    public boolean hasRepeatedFailures(String ipAddress) {
        try {
            LocalDateTime fifteenMinutesAgo = LocalDateTime.now().minusMinutes(15);
            List<UserManagementError> recentErrors = errorService.getIpErrors(ipAddress, 1);
            
            long authFailures = recentErrors.stream()
                .filter(error -> error.getErrorType() == UserManagementError.ErrorType.AUTHENTICATION)
                .filter(error -> error.getOccurredAt().isAfter(fifteenMinutesAgo))
                .count();
            
            return authFailures > 5; // More than 5 failures in 15 minutes
        } catch (Exception e) {
            logger.error("Error checking repeated failures for IP: {} - {}", ipAddress, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Check for brute force attack patterns
     */
    public boolean isBruteForcePattern(String ipAddress, String username) {
        try {
            // Check if this IP is targeting multiple usernames
            List<UserManagementError> recentErrors = errorService.getIpErrors(ipAddress, 1);
            
            long uniqueUsernames = recentErrors.stream()
                .filter(error -> error.getErrorType() == UserManagementError.ErrorType.AUTHENTICATION)
                .map(UserManagementError::getUsername)
                .distinct()
                .count();
            
            // Brute force pattern: Same IP targeting multiple usernames
            return uniqueUsernames > 3 && recentErrors.size() > 10;
        } catch (Exception e) {
            logger.error("Error checking brute force pattern for IP: {} - {}", ipAddress, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Check for credential stuffing patterns
     */
    public boolean isCredentialStuffingPattern(String ipAddress) {
        try {
            // Credential stuffing: High volume of login attempts from single IP
            return errorService.hasSuspiciousErrorPattern(ipAddress, 20);
        } catch (Exception e) {
            logger.error("Error checking credential stuffing pattern for IP: {} - {}", ipAddress, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Check for suspicious failure reasons that might indicate attacks
     */
    public boolean isSuspiciousFailureReason(String failureReason) {
        if (failureReason == null) return false;
        
        String lowerReason = failureReason.toLowerCase();
        List<String> suspiciousReasons = Arrays.asList(
            "sql injection", "script", "injection", "overflow", "exploit",
            "malformed", "invalid format", "parsing error"
        );
        
        return suspiciousReasons.stream()
            .anyMatch(pattern -> lowerReason.contains(pattern));
    }
    
    /**
     * Check for potential session hijacking
     */
    public boolean isPotentialSessionHijacking(String ipAddress, String username) {
        try {
            // Check if user has concurrent sessions from different IPs
            // This would be implemented with user session tracking
            return sessionService.isSuspiciousIpActivity(ipAddress, 5);
        } catch (Exception e) {
            logger.error("Error checking session hijacking for user: {} from IP: {} - {}", username, ipAddress, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Get overall security risk assessment for IP
     */
    public SecurityRiskLevel assessIpRisk(String ipAddress) {
        try {
            int riskScore = 0;
            
            if (isKnownMaliciousIp(ipAddress)) {
                riskScore += 50;
            }
            
            if (hasRepeatedFailures(ipAddress)) {
                riskScore += 30;
            }
            
            if (isCredentialStuffingPattern(ipAddress)) {
                riskScore += 20;
            }
            
            if (sessionService.isSuspiciousIpActivity(ipAddress, 10)) {
                riskScore += 15;
            }
            
            if (riskScore >= 50) {
                return SecurityRiskLevel.CRITICAL;
            } else if (riskScore >= 30) {
                return SecurityRiskLevel.HIGH;
            } else if (riskScore >= 15) {
                return SecurityRiskLevel.MEDIUM;
            } else {
                return SecurityRiskLevel.LOW;
            }
            
        } catch (Exception e) {
            logger.error("Error assessing IP risk for: {} - {}", ipAddress, e.getMessage(), e);
            return SecurityRiskLevel.LOW;
        }
    }
    
    /**
     * Security risk levels for comprehensive assessment
     */
    public enum SecurityRiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}