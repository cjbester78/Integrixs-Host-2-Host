package com.integrixs.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.UUID;

/**
 * Utility class for setting up system authentication context for automated operations
 * This is used when flows need to execute without a logged-in user
 */
public class SystemAuthenticationContext {

    private static final Logger logger = LoggerFactory.getLogger(SystemAuthenticationContext.class);

    // Standard system integrator ID - this should match the integrator user ID in the database
    private static final String SYSTEM_INTEGRATOR_USERNAME = "Integrator";
    private static final String SYSTEM_INTEGRATOR_ID = "system-integrator-id";

    /**
     * Set up a system authentication context for flow execution
     * This creates a minimal authentication that will satisfy security checks
     */
    public static void setSystemIntegratorAuthentication() {
        try {
            // Create a system user authentication with INTEGRATOR role
            SimpleIntegratorPrincipal principal = new SimpleIntegratorPrincipal();
            
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("INTEGRATOR"))
            );
            
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            
            logger.debug("Set system integrator authentication context");
        } catch (Exception e) {
            logger.error("Failed to set system integrator authentication: {}", e.getMessage(), e);
        }
    }

    /**
     * Clear the current security context
     */
    public static void clearAuthentication() {
        SecurityContextHolder.clearContext();
        logger.debug("Cleared authentication context");
    }

    /**
     * Execute a runnable with system integrator authentication context
     */
    public static void runAsSystemIntegrator(Runnable operation) {
        setSystemIntegratorAuthentication();
        try {
            operation.run();
        } finally {
            clearAuthentication();
        }
    }

    /**
     * Simple principal class for system integrator authentication
     */
    private static class SimpleIntegratorPrincipal {
        public String getUsername() {
            return SYSTEM_INTEGRATOR_USERNAME;
        }
        
        public UUID getId() {
            // This should ideally be the real integrator user ID from the database
            // For now, we'll use a deterministic UUID
            return UUID.nameUUIDFromBytes(SYSTEM_INTEGRATOR_ID.getBytes());
        }
        
        public boolean isIntegrator() {
            return true;
        }
        
        @Override
        public String toString() {
            return "SystemIntegrator{username='" + SYSTEM_INTEGRATOR_USERNAME + "'}";
        }
    }
}