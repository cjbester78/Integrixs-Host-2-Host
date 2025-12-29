package com.integrixs.backend.security;

import com.integrixs.backend.model.User;
import com.integrixs.backend.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * JWT Authentication Filter to validate tokens on each request
 * Extracts JWT token from Authorization header and validates it
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    
    private final JwtTokenService jwtTokenService;
    private final UserService userService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, UserService userService) {
        this.jwtTokenService = jwtTokenService;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, 
                                    @NonNull HttpServletResponse response, 
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = getJwtFromRequest(request);
            
            if (jwt != null && jwtTokenService.validateToken(jwt)) {
                // Only process access tokens, not refresh tokens
                if (!jwtTokenService.isAccessToken(jwt)) {
                    logger.warn("Attempted to use refresh token as access token");
                    filterChain.doFilter(request, response);
                    return;
                }

                String userId = jwtTokenService.getUserIdFromToken(jwt);
                
                if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    Optional<User> userOptional = userService.findById(UUID.fromString(userId));
                    
                    if (userOptional.isPresent()) {
                        User user = userOptional.get();
                        
                        // Check if user is still enabled
                        if (!user.isEnabled()) {
                            logger.warn("User {} is disabled but has valid JWT token", user.getUsername());
                            filterChain.doFilter(request, response);
                            return;
                        }

                        // Check if account is not locked
                        if (!user.isAccountNonLocked()) {
                            logger.warn("User {} account is locked but has valid JWT token", user.getUsername());
                            filterChain.doFilter(request, response);
                            return;
                        }

                        // Create authentication token
                        UsernamePasswordAuthenticationToken authentication = 
                            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        
                        // Set authentication in security context
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        
                        logger.debug("Set authentication for user: {}", user.getUsername());
                    } else {
                        logger.warn("User not found for JWT token with userId: {}", userId);
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context: {}", ex.getMessage());
            // Don't throw exception - let request continue without authentication
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from Authorization header
     * Expected format: "Bearer <token>"
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7); // Remove "Bearer " prefix
            
            // Basic validation - token should not be empty
            if (token.trim().isEmpty()) {
                logger.debug("Empty JWT token in Authorization header");
                return null;
            }
            
            return token;
        }
        
        return null;
    }

    /**
     * Should not filter certain paths (like login, health check)
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        
        // Skip JWT validation for these paths
        return path.startsWith("/api/auth/login") ||
               path.startsWith("/api/auth/register") ||
               path.startsWith("/api/auth/refresh") ||
               path.startsWith("/api/health") ||
               path.startsWith("/actuator") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/error") ||
               path.equals("/");
    }
}