package com.integrixs.backend.controller;

import com.integrixs.backend.model.User;
import com.integrixs.backend.security.JwtTokenService;
import com.integrixs.backend.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Authentication controller for login/logout operations
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager, 
                         JwtTokenService jwtTokenService,
                         UserService userService,
                         PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Login endpoint
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            logger.info("Login attempt for user: {}", loginRequest.getUsername());

            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getUsername(),
                    loginRequest.getPassword()
                )
            );

            // Get user details
            User user = (User) authentication.getPrincipal();
            
            // Update last login
            userService.updateLastLogin(user.getId());
            
            // Generate JWT tokens
            JwtTokenService.TokenResponse tokenResponse = jwtTokenService.createTokenResponse(user);
            
            logger.info("Successful login for user: {} (role: {})", user.getUsername(), user.getRole());
            
            return ResponseEntity.ok(tokenResponse);

        } catch (BadCredentialsException e) {
            logger.warn("Failed login attempt for user: {} - Invalid credentials", loginRequest.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                        "error", "Authentication failed",
                        "message", "Invalid username or password",
                        "timestamp", LocalDateTime.now()
                    ));
        } catch (Exception e) {
            logger.error("Login error for user: {} - {}", loginRequest.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Login failed",
                        "message", "An unexpected error occurred",
                        "timestamp", LocalDateTime.now()
                    ));
        }
    }

    /**
     * Refresh token endpoint
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody RefreshTokenRequest request) {
        try {
            String refreshToken = request.getRefreshToken();
            
            if (refreshToken == null || refreshToken.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Refresh token is required"));
            }

            // Validate refresh token
            if (!jwtTokenService.validateToken(refreshToken) || !jwtTokenService.isRefreshToken(refreshToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid or expired refresh token"));
            }

            // Get user from token
            String userId = jwtTokenService.getUserIdFromToken(refreshToken);
            Optional<User> userOptional = userService.findById(java.util.UUID.fromString(userId));
            
            if (userOptional.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not found"));
            }

            User user = userOptional.get();
            
            // Check if user is still active
            if (!user.isEnabled() || !user.isAccountNonLocked()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User account is disabled or locked"));
            }

            // Generate new tokens
            JwtTokenService.TokenResponse tokenResponse = jwtTokenService.createTokenResponse(user);
            
            logger.debug("Token refreshed for user: {}", user.getUsername());
            
            return ResponseEntity.ok(tokenResponse);

        } catch (Exception e) {
            logger.error("Token refresh error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Token refresh failed"));
        }
    }

    /**
     * Get current user info
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Not authenticated"));
            }

            User user = (User) authentication.getPrincipal();
            
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("email", user.getEmail());
            userInfo.put("fullName", user.getFullName());
            userInfo.put("role", user.getRole());
            userInfo.put("enabled", user.isEnabled());
            userInfo.put("lastLogin", user.getLastLogin());
            userInfo.put("timezone", user.getTimezone());
            
            return ResponseEntity.ok(userInfo);

        } catch (Exception e) {
            logger.error("Error getting current user: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get user information"));
        }
    }

    /**
     * Logout endpoint (client-side token invalidation)
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        try {
            // In a stateless JWT setup, logout is primarily handled on the client side
            // by removing the token from storage. However, we can log the logout event.
            
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                User user = (User) authentication.getPrincipal();
                logger.info("User logout: {}", user.getUsername());
            }
            
            return ResponseEntity.ok(Map.of(
                "message", "Logged out successfully",
                "timestamp", LocalDateTime.now()
            ));

        } catch (Exception e) {
            logger.error("Logout error: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "message", "Logout completed",
                "timestamp", LocalDateTime.now()
            ));
        }
    }

    /**
     * Verify JWT token validity
     */
    @GetMapping("/verify")
    @PreAuthorize("hasAnyAuthority('ADMINISTRATOR', 'VIEWER')")
    public ResponseEntity<?> verifyToken() {
        try {
            // If we reach here, the token is valid (Spring Security filtered it)
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) authentication.getPrincipal();
            
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "username", user.getUsername(),
                "role", user.getRole(),
                "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            logger.error("Token verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("valid", false, "error", "Token verification failed"));
        }
    }

    /**
     * Change password endpoint
     */
    @PostMapping("/change-password")
    @PreAuthorize("hasAnyAuthority('ADMINISTRATOR', 'VIEWER')")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) authentication.getPrincipal();
            
            // Verify current password
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Current password is incorrect"));
            }
            
            // Validate new password
            if (request.getNewPassword() == null || request.getNewPassword().length() < 8) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "New password must be at least 8 characters long"));
            }
            
            // Change password
            userService.changePassword(user.getId(), request.getNewPassword());
            
            logger.info("Password changed for user: {}", user.getUsername());
            
            return ResponseEntity.ok(Map.of(
                "message", "Password changed successfully",
                "timestamp", LocalDateTime.now()
            ));

        } catch (Exception e) {
            logger.error("Password change error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to change password"));
        }
    }

    // DTOs
    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class RefreshTokenRequest {
        private String refreshToken;

        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }

    public static class ChangePasswordRequest {
        private String currentPassword;
        private String newPassword;

        public String getCurrentPassword() { return currentPassword; }
        public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
        
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
}