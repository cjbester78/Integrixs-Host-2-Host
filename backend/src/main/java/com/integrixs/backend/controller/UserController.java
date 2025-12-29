package com.integrixs.backend.controller;

import com.integrixs.backend.model.User;
import com.integrixs.backend.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * User management controller for CRUD operations
 * Provides endpoints for user administration
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Get all users (Admin only)
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<?> getAllUsers() {
        try {
            List<User> users = userService.findAll();
            
            // Convert to safe DTOs (without passwords)
            List<Map<String, Object>> userDTOs = users.stream()
                    .map(this::convertToUserDTO)
                    .toList();
            
            return ResponseEntity.ok(Map.of(
                "users", userDTOs,
                "total", userDTOs.size(),
                "timestamp", LocalDateTime.now()
            ));

        } catch (Exception e) {
            logger.error("Error getting all users: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve users"));
        }
    }

    /**
     * Get user by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or #id == authentication.principal.id.toString()")
    public ResponseEntity<?> getUserById(@PathVariable String id) {
        try {
            UUID userId = UUID.fromString(id);
            Optional<User> userOptional = userService.findById(userId);
            
            if (userOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            User user = userOptional.get();
            return ResponseEntity.ok(convertToUserDTO(user));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid user ID format"));
        } catch (Exception e) {
            logger.error("Error getting user by ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve user"));
        }
    }

    /**
     * Create new user (Admin only)
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
        try {
            // Validate request
            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Username is required"));
            }
            
            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Email is required"));
            }
            
            if (request.getPassword() == null || request.getPassword().length() < 8) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Password must be at least 8 characters long"));
            }

            // Check if username or email already exists
            if (userService.existsByUsername(request.getUsername())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Username already exists"));
            }
            
            if (userService.existsByEmail(request.getEmail())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Email already exists"));
            }

            // Create user
            User newUser = new User(
                request.getUsername(),
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getFullName() != null ? request.getFullName() : request.getUsername(),
                request.getRole() != null ? request.getRole() : User.UserRole.VIEWER
            );
            
            if (request.getTimezone() != null) {
                newUser.setTimezone(request.getTimezone());
            }

            User savedUser = userService.saveUser(newUser);
            
            logger.info("Created new user: {} by admin", savedUser.getUsername());
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                        "message", "User created successfully",
                        "user", convertToUserDTO(savedUser),
                        "timestamp", LocalDateTime.now()
                    ));

        } catch (Exception e) {
            logger.error("Error creating user: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create user"));
        }
    }

    /**
     * Update user (Admin only or own profile)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or #id == authentication.principal.id.toString()")
    public ResponseEntity<?> updateUser(@PathVariable String id, @RequestBody UpdateUserRequest request) {
        try {
            UUID userId = UUID.fromString(id);
            Optional<User> userOptional = userService.findById(userId);
            
            if (userOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            User user = userOptional.get();
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = (User) auth.getPrincipal();
            
            // Check permissions for role changes
            if (request.getRole() != null && !user.getRole().equals(request.getRole())) {
                if (!currentUser.hasFullAccess()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "Only administrators can change user roles"));
                }
                user.setRole(request.getRole());
            }
            
            // Update fields
            if (request.getFullName() != null) {
                user.setFullName(request.getFullName());
            }
            
            if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
                if (userService.existsByEmail(request.getEmail())) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Email already exists"));
                }
                user.setEmail(request.getEmail());
            }
            
            if (request.getTimezone() != null) {
                user.setTimezone(request.getTimezone());
            }
            
            // Admin-only fields
            if (currentUser.hasFullAccess()) {
                if (request.getEnabled() != null) {
                    user.setEnabled(request.getEnabled());
                }
            }

            User updatedUser = userService.saveUser(user);
            
            logger.info("Updated user: {} by {}", updatedUser.getUsername(), currentUser.getUsername());
            
            return ResponseEntity.ok(Map.of(
                "message", "User updated successfully",
                "user", convertToUserDTO(updatedUser),
                "timestamp", LocalDateTime.now()
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid user ID format"));
        } catch (Exception e) {
            logger.error("Error updating user {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update user"));
        }
    }

    /**
     * Delete user (Admin only)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<?> deleteUser(@PathVariable String id) {
        try {
            UUID userId = UUID.fromString(id);
            Optional<User> userOptional = userService.findById(userId);
            
            if (userOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            User user = userOptional.get();
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = (User) auth.getPrincipal();
            
            // Prevent self-deletion
            if (userId.equals(currentUser.getId())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Cannot delete your own account"));
            }
            
            userService.deleteUser(userId);
            
            logger.info("Deleted user: {} by admin {}", user.getUsername(), currentUser.getUsername());
            
            return ResponseEntity.ok(Map.of(
                "message", "User deleted successfully",
                "timestamp", LocalDateTime.now()
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid user ID format"));
        } catch (Exception e) {
            logger.error("Error deleting user {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete user"));
        }
    }

    /**
     * Get user statistics (Admin only)
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<?> getUserStatistics() {
        try {
            Map<String, Long> stats = userService.getUserStatistics();
            
            return ResponseEntity.ok(Map.of(
                "statistics", stats,
                "timestamp", LocalDateTime.now()
            ));

        } catch (Exception e) {
            logger.error("Error getting user statistics: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve statistics"));
        }
    }

    /**
     * Search users (Admin only)
     */
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<?> searchUsers(@RequestParam String query) {
        try {
            List<User> users = userService.searchUsers(query);
            
            List<Map<String, Object>> userDTOs = users.stream()
                    .map(this::convertToUserDTO)
                    .toList();
            
            return ResponseEntity.ok(Map.of(
                "users", userDTOs,
                "total", userDTOs.size(),
                "query", query,
                "timestamp", LocalDateTime.now()
            ));

        } catch (Exception e) {
            logger.error("Error searching users: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to search users"));
        }
    }

    /**
     * Lock/unlock user account (Admin only)
     */
    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<?> toggleUserLock(@PathVariable String id, @RequestBody Map<String, Boolean> request) {
        try {
            UUID userId = UUID.fromString(id);
            Optional<User> userOptional = userService.findById(userId);
            
            if (userOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Boolean lock = request.get("lock");
            if (lock == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Lock status is required"));
            }
            
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = (User) auth.getPrincipal();
            
            // Prevent self-locking
            if (userId.equals(currentUser.getId())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Cannot lock/unlock your own account"));
            }
            
            if (lock) {
                userService.lockUserAccount(userId);
            } else {
                userService.unlockUserAccount(userId);
            }
            
            User user = userService.findById(userId).get();
            String action = lock ? "locked" : "unlocked";
            
            logger.info("User account {} {} by admin {}", user.getUsername(), action, currentUser.getUsername());
            
            return ResponseEntity.ok(Map.of(
                "message", "User account " + action + " successfully",
                "user", convertToUserDTO(user),
                "timestamp", LocalDateTime.now()
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid user ID format"));
        } catch (Exception e) {
            logger.error("Error toggling user lock {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update user lock status"));
        }
    }

    /**
     * Get current user profile
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = (User) auth.getPrincipal();
            
            return ResponseEntity.ok(Map.of(
                "user", convertToUserDTO(currentUser),
                "timestamp", LocalDateTime.now()
            ));

        } catch (Exception e) {
            logger.error("Error getting current user profile: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve user profile"));
        }
    }

    /**
     * Change user password
     */
    @PutMapping("/{id}/password")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or #id == authentication.principal.id.toString()")
    public ResponseEntity<?> changePassword(@PathVariable String id, @RequestBody ChangePasswordRequest request) {
        try {
            UUID userId = UUID.fromString(id);
            Optional<User> userOptional = userService.findById(userId);
            
            if (userOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            User user = userOptional.get();
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = (User) auth.getPrincipal();
            
            // For non-admin users, verify current password
            if (!currentUser.hasFullAccess() && !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Current password is incorrect"));
            }
            
            // Validate new password
            if (request.getNewPassword() == null || request.getNewPassword().length() < 8) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "New password must be at least 8 characters long"));
            }
            
            // Update password
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
            userService.saveUser(user);
            
            logger.info("Password changed for user: {} by {}", user.getUsername(), currentUser.getUsername());
            
            return ResponseEntity.ok(Map.of(
                "message", "Password changed successfully",
                "timestamp", LocalDateTime.now()
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid user ID format"));
        } catch (Exception e) {
            logger.error("Error changing password for user {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to change password"));
        }
    }

    /**
     * Convert User to safe DTO (without password)
     */
    private Map<String, Object> convertToUserDTO(User user) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", user.getId());
        dto.put("username", user.getUsername());
        dto.put("email", user.getEmail());
        dto.put("fullName", user.getFullName());
        dto.put("role", user.getRole());
        dto.put("enabled", user.isEnabled());
        dto.put("accountNonLocked", user.isAccountNonLocked());
        dto.put("accountNonExpired", user.isAccountNonExpired());
        dto.put("credentialsNonExpired", user.isCredentialsNonExpired());
        dto.put("timezone", user.getTimezone());
        dto.put("createdAt", user.getCreatedAt());
        dto.put("updatedAt", user.getUpdatedAt());
        dto.put("lastLogin", user.getLastLogin());
        return dto;
    }

    // DTOs
    public static class CreateUserRequest {
        private String username;
        private String email;
        private String password;
        private String fullName;
        private User.UserRole role;
        private String timezone;

        // Getters and setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public User.UserRole getRole() { return role; }
        public void setRole(User.UserRole role) { this.role = role; }

        public String getTimezone() { return timezone; }
        public void setTimezone(String timezone) { this.timezone = timezone; }
    }

    public static class UpdateUserRequest {
        private String email;
        private String fullName;
        private User.UserRole role;
        private Boolean enabled;
        private String timezone;

        // Getters and setters
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public User.UserRole getRole() { return role; }
        public void setRole(User.UserRole role) { this.role = role; }

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }

        public String getTimezone() { return timezone; }
        public void setTimezone(String timezone) { this.timezone = timezone; }
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