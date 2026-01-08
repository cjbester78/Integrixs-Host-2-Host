package com.integrixs.backend.service;

import com.integrixs.backend.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for managing system authentication context for automated operations
 * Provides functionality to set integrator authentication for flow execution
 */
@Service
public class SystemAuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(SystemAuthenticationService.class);

    private final UserService userService;

    public SystemAuthenticationService(UserService userService) {
        this.userService = userService;
    }

    /**
     * Set the integrator user as the current authenticated user in the security context
     * This is used for automated flow execution where no user is logged in
     */
    public boolean setIntegratorAuthentication() {
        try {
            Optional<User> integratorUser = userService.getSystemIntegrator();
            if (integratorUser.isPresent()) {
                User integrator = integratorUser.get();
                
                // Create authentication token for the integrator
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                    integrator,
                    null,
                    integrator.getAuthorities()
                );
                
                // Set the authentication in the security context
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
                
                logger.debug("Set integrator authentication context for user: {}", integrator.getUsername());
                return true;
            } else {
                logger.error("Could not find system integrator user for authentication");
                return false;
            }
        } catch (Exception e) {
            logger.error("Failed to set integrator authentication: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Clear the current security context
     */
    public void clearAuthentication() {
        SecurityContextHolder.clearContext();
        logger.debug("Cleared authentication context");
    }

    /**
     * Execute a runnable with integrator authentication context
     * Automatically cleans up the context after execution
     */
    public void runAsIntegrator(Runnable operation) {
        boolean authenticationSet = setIntegratorAuthentication();
        try {
            if (authenticationSet) {
                operation.run();
            } else {
                logger.error("Could not set integrator authentication, operation will not run");
                throw new IllegalStateException("Could not authenticate as integrator for system operation");
            }
        } finally {
            clearAuthentication();
        }
    }

    /**
     * Execute a callable with integrator authentication context
     * Automatically cleans up the context after execution
     */
    public <T> T callAsIntegrator(java.util.concurrent.Callable<T> operation) throws Exception {
        boolean authenticationSet = setIntegratorAuthentication();
        try {
            if (authenticationSet) {
                return operation.call();
            } else {
                logger.error("Could not set integrator authentication, operation will not run");
                throw new IllegalStateException("Could not authenticate as integrator for system operation");
            }
        } finally {
            clearAuthentication();
        }
    }

    /**
     * Check if the current authentication is the integrator user
     */
    public boolean isIntegratorAuthenticated() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                Object principal = authentication.getPrincipal();
                if (principal instanceof User) {
                    User user = (User) principal;
                    return user.isIntegrator() && "Integrator".equals(user.getUsername());
                }
            }
        } catch (Exception e) {
            logger.debug("Could not check integrator authentication: {}", e.getMessage());
        }
        return false;
    }
}