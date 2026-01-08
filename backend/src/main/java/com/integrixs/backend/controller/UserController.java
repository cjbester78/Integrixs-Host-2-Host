package com.integrixs.backend.controller;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.backend.dto.ExecutionValidationResult;
import com.integrixs.backend.dto.request.AdminUserRequest;
import com.integrixs.backend.dto.response.AdminUserResponse;
import com.integrixs.backend.model.User;
import com.integrixs.backend.service.AdministrativeRequestValidationService;
import com.integrixs.backend.service.ResponseStandardizationService;
import com.integrixs.backend.service.UserService;
import com.integrixs.shared.util.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * User management controller for CRUD operations.
 * Provides endpoints for user administration.
 * Refactored following OOP principles with proper validation, DTOs, and error handling.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AdministrativeRequestValidationService validationService;
    private final ResponseStandardizationService responseService;

    @Autowired
    public UserController(UserService userService, 
                         PasswordEncoder passwordEncoder,
                         AdministrativeRequestValidationService validationService,
                         ResponseStandardizationService responseService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.validationService = validationService;
        this.responseService = responseService;
    }
    
    /**
     * Get current user ID from security context.
     */
    private UUID getCurrentUserId() {
        return SecurityContextHelper.getCurrentUserId();
    }

    /**
     * Get all users (Admin only).
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<List<User>> getAllUsers() {
        
        UUID currentUserId = getCurrentUserId();
        logger.info("User {} requesting all users", currentUserId);
        
        try {
            List<User> users = userService.findAll();
            logger.info("Retrieved {} users for admin: {}", users.size(), currentUserId);
            return ResponseEntity.ok(users);
            
        } catch (Exception e) {
            logger.error("Failed to get all users for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to retrieve users", e);
        }
    }

    /**
     * Get user by ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or #id == authentication.principal.id.toString()")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUserById(@PathVariable String id) {
        
        UUID currentUserId = getCurrentUserId();
        UUID userId;
        
        try {
            userId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid user ID format");
        }
        
        // Create immutable request DTO
        AdminUserRequest userRequest = AdminUserRequest.builder()
            .operation("get_user")
            .userId(userId)
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateUserListRequest(
            Map.of("userId", userId.toString())
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid user details request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            Optional<User> userOptional = userService.findById(userId);
            
            if (userOptional.isEmpty()) {
                throw new IllegalArgumentException("User not found with ID: " + userId);
            }
            
            User user = userOptional.get();
            
            // Create response using builder pattern
            AdminUserResponse response = AdminUserResponse.detailsResponse(
                user.getId(), user.getUsername(), user.getEmail(), 
                user.getRole().toString(), user.isEnabled(), 
                user.getCreatedAt(), user.getLastLogin()
            );
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get user by ID {} for user: {}", id, currentUserId, e);
            throw new RuntimeException("Failed to retrieve user", e);
        }
    }

    /**
     * Create new user (Admin only).
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminUserResponse>> createUser(@RequestBody CreateUserRequest request) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminUserRequest createRequest = AdminUserRequest.createRequest(
            request.getUsername(),
            request.getEmail(),
            request.getRole() != null ? request.getRole().toString() : "VIEWER",
            request.getPassword(),
            currentUserId
        );
        
        // Create user for validation
        User userData = new User(
            request.getUsername(),
            request.getEmail(),
            request.getPassword(),
            request.getFullName() != null ? request.getFullName() : request.getUsername(),
            request.getRole() != null ? request.getRole() : User.UserRole.VIEWER
        );
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateUserCreateRequest(userData);
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid user create request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            // Check if username or email already exists
            if (userService.existsByUsername(request.getUsername())) {
                throw new IllegalArgumentException("Username already exists");
            }
            
            if (userService.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("Email already exists");
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
            
            logger.info("Created new user: {} by admin: {}", savedUser.getUsername(), currentUserId);
            
            // Create response using builder pattern
            AdminUserResponse response = AdminUserResponse.createdResponse(
                savedUser.getId(), savedUser.getUsername(), savedUser.getEmail(), savedUser.getRole().toString()
            );
            
            return responseService.created(response, "User created successfully");
            
        } catch (Exception e) {
            logger.error("Failed to create user for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to create user", e);
        }
    }

    /**
     * Update user (Admin only or own profile).
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or #id == authentication.principal.id.toString()")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUser(@PathVariable String id, @RequestBody UpdateUserRequest request) {
        
        UUID currentUserId = getCurrentUserId();
        UUID userId;
        
        try {
            userId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid user ID format");
        }
        
        // Get existing user for validation
        Optional<User> userOptional = userService.findById(userId);
        if (userOptional.isEmpty()) {
            throw new IllegalArgumentException("User not found with ID: " + userId);
        }
        User existingUser = userOptional.get();
        
        // Create immutable request DTO
        AdminUserRequest updateRequest = AdminUserRequest.builder()
            .operation("update")
            .userId(userId)
            .email(request.getEmail())
            .role(request.getRole() != null ? request.getRole().toString() : existingUser.getRole().toString())
            .enabled(request.getEnabled())
            .requestedBy(currentUserId)
            .build();
        
        // Create updated user for validation
        User updatedUser = new User(
            existingUser.getUsername(),
            request.getEmail() != null ? request.getEmail() : existingUser.getEmail(),
            existingUser.getPassword(),
            request.getFullName() != null ? request.getFullName() : existingUser.getFullName(),
            request.getRole() != null ? request.getRole() : existingUser.getRole()
        );
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateUserUpdateRequest(userId.toString(), updatedUser);
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid user update request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            User user = existingUser;
            User currentUser = userService.findById(currentUserId).orElseThrow();
            
            // Check permissions for role changes
            if (request.getRole() != null && !user.getRole().equals(request.getRole())) {
                if (!currentUser.hasFullAccess()) {
                    throw new IllegalArgumentException("Only administrators can change user roles");
                }
                user.setRole(request.getRole());
            }
            
            // Update fields
            if (request.getFullName() != null) {
                user.setFullName(request.getFullName());
            }
            
            if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
                if (userService.existsByEmail(request.getEmail())) {
                    throw new IllegalArgumentException("Email already exists");
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

            User savedUser = userService.saveUser(user);
            
            logger.info("Updated user: {} by {}", savedUser.getUsername(), currentUser.getUsername());
            
            // Create response using builder pattern
            AdminUserResponse response = AdminUserResponse.updatedResponse(
                savedUser.getId(), "User updated successfully"
            );
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to update user {} for user: {}", id, currentUserId, e);
            throw new RuntimeException("Failed to update user", e);
        }
    }

    /**
     * Delete user (Admin only).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminUserResponse>> deleteUser(@PathVariable String id) {
        
        UUID currentUserId = getCurrentUserId();
        UUID userId;
        
        try {
            userId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid user ID format");
        }
        
        // Create immutable request DTO
        AdminUserRequest deleteRequest = AdminUserRequest.deleteRequest(userId, "User deletion", currentUserId);
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateUserDeleteRequest(userId.toString());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid user delete request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            Optional<User> userOptional = userService.findById(userId);
            
            if (userOptional.isEmpty()) {
                throw new IllegalArgumentException("User not found with ID: " + userId);
            }
            
            User user = userOptional.get();
            
            // Prevent self-deletion
            if (userId.equals(currentUserId)) {
                throw new IllegalArgumentException("Cannot delete your own account");
            }
            
            userService.deleteUser(userId);
            
            logger.info("Deleted user: {} by admin {}", user.getUsername(), currentUserId);
            
            // Create response using builder pattern
            AdminUserResponse response = AdminUserResponse.deletedResponse(userId, user.getUsername());
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to delete user {} for user: {}", id, currentUserId, e);
            throw new RuntimeException("Failed to delete user", e);
        }
    }

    /**
     * Get user statistics (Admin only).
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUserStatistics() {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminUserRequest statsRequest = AdminUserRequest.builder()
            .operation("user_statistics")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateUserListRequest(Map.of());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid user statistics request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            Map<String, Long> stats = userService.getUserStatistics();
            
            // Create response using builder pattern
            AdminUserResponse response = AdminUserResponse.builder()
                .operation("user_statistics")
                .status("SUCCESS")
                .message("Retrieved user statistics")
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get user statistics for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to retrieve statistics", e);
        }
    }

    /**
     * Search users (Admin only).
     */
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<List<User>>> searchUsers(@RequestParam String query) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminUserRequest searchRequest = AdminUserRequest.builder()
            .operation("search_users")
            .username(query)
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateUserListRequest(
            Map.of("query", query != null ? query : "")
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid user search request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            List<User> users = userService.searchUsers(query);
            
            logger.info("Found {} users matching query '{}' for admin: {}", users.size(), query, currentUserId);
            
            return ResponseEntity.ok(ApiResponse.success("Found " + users.size() + " users matching query: " + query, users));
            
        } catch (Exception e) {
            logger.error("Failed to search users with query '{}' for user: {}", query, currentUserId, e);
            throw new RuntimeException("Failed to search users", e);
        }
    }

    /**
     * Lock/unlock user account (Admin only).
     */
    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminUserResponse>> toggleUserLock(@PathVariable String id, @RequestBody Map<String, Boolean> request) {
        
        UUID currentUserId = getCurrentUserId();
        UUID userId;
        
        try {
            userId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid user ID format");
        }
        
        Boolean lock = request.get("lock");
        if (lock == null) {
            throw new IllegalArgumentException("Lock status is required");
        }
        
        // Create immutable request DTO
        AdminUserRequest lockRequest = AdminUserRequest.statusChangeRequest(userId, !lock, currentUserId);
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateUserListRequest(
            Map.of("userId", userId.toString(), "lock", lock.toString())
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid user lock request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            Optional<User> userOptional = userService.findById(userId);
            
            if (userOptional.isEmpty()) {
                throw new IllegalArgumentException("User not found with ID: " + userId);
            }
            
            // Prevent self-locking
            if (userId.equals(currentUserId)) {
                throw new IllegalArgumentException("Cannot lock/unlock your own account");
            }
            
            if (lock) {
                userService.lockUserAccount(userId);
            } else {
                userService.unlockUserAccount(userId);
            }
            
            User user = userService.findById(userId).get();
            String action = lock ? "locked" : "unlocked";
            
            logger.info("User account {} {} by admin {}", user.getUsername(), action, currentUserId);
            
            // Create response using builder pattern
            AdminUserResponse response = AdminUserResponse.statusChangeResponse(
                user.getId(), user.getUsername(), user.isAccountNonLocked()
            );
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to toggle user lock {} for user: {}", id, currentUserId, e);
            throw new RuntimeException("Failed to update user lock status", e);
        }
    }

    /**
     * Get current user profile.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getCurrentUser() {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminUserRequest profileRequest = AdminUserRequest.builder()
            .operation("get_profile")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateUserListRequest(Map.of());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid user profile request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            User currentUser = userService.findById(currentUserId).orElseThrow(
                () -> new IllegalArgumentException("Current user not found")
            );
            
            // Create response using builder pattern
            AdminUserResponse response = AdminUserResponse.detailsResponse(
                currentUser.getId(), currentUser.getUsername(), currentUser.getEmail(),
                currentUser.getRole().toString(), currentUser.isEnabled(),
                currentUser.getCreatedAt(), currentUser.getLastLogin()
            );
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get current user profile for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to retrieve user profile", e);
        }
    }

    /**
     * Change user password.
     */
    @PutMapping("/{id}/password")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or #id == authentication.principal.id.toString()")
    public ResponseEntity<ApiResponse<AdminUserResponse>> changePassword(@PathVariable String id, @RequestBody ChangePasswordRequest request) {
        
        UUID currentUserId = getCurrentUserId();
        UUID userId;
        
        try {
            userId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid user ID format");
        }
        
        // Create immutable request DTO using password reset (closest available method)
        AdminUserRequest passwordRequest = AdminUserRequest.passwordResetRequest(
            userId, request.getNewPassword(), currentUserId
        );
        
        // Validate request using general user list validation
        ExecutionValidationResult validation = validationService.validateUserListRequest(
            Map.of(
                "userId", userId.toString(),
                "newPassword", request.getNewPassword() != null ? request.getNewPassword() : ""
            )
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid password change request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            Optional<User> userOptional = userService.findById(userId);
            
            if (userOptional.isEmpty()) {
                throw new IllegalArgumentException("User not found with ID: " + userId);
            }
            
            User user = userOptional.get();
            User currentUser = userService.findById(currentUserId).orElseThrow();
            
            // For non-admin users, verify current password
            if (!currentUser.hasFullAccess() && !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new IllegalArgumentException("Current password is incorrect");
            }
            
            // Update password
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
            userService.saveUser(user);
            
            logger.info("Password changed for user: {} by {}", user.getUsername(), currentUser.getUsername());
            
            // Create response using builder pattern
            AdminUserResponse response = AdminUserResponse.passwordResetResponse(
                user.getId(), user.getUsername()
            );
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to change password for user {} for user: {}", id, currentUserId, e);
            throw new RuntimeException("Failed to change password", e);
        }
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