package com.integrixs.backend.service;

import com.integrixs.backend.model.User;
import com.integrixs.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * User service for managing users with database persistence
 * Uses JDBC repository for data storage
 */
@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        initializeDefaultUsers();
    }

    /**
     * Initialize default admin user
     */
    private void initializeDefaultUsers() {
        // Create default admin user if not exists
        if (!userRepository.existsByUsername("Administrator")) {
            User admin = new User(
                "Administrator",
                "admin@integrixlab.com", 
                passwordEncoder.encode("Int3grix@01"),
                "System Administrator",
                User.UserRole.ADMINISTRATOR
            );
            admin.setTimezone("UTC");
            saveUser(admin);
            logger.info("Created default admin user: Administrator");
        }
    }

    /**
     * Save or update user
     */
    public User saveUser(User user) {
        // Check for duplicate username/email for new users or different users
        if (user.getId() == null) {
            if (existsByUsername(user.getUsername())) {
                throw new IllegalArgumentException("Username already exists: " + user.getUsername());
            }
            if (existsByEmail(user.getEmail())) {
                throw new IllegalArgumentException("Email already exists: " + user.getEmail());
            }
        } else {
            // For existing users, check if username/email conflicts with other users
            Optional<User> existingUserByUsername = userRepository.findByUsername(user.getUsername());
            if (existingUserByUsername.isPresent() && !existingUserByUsername.get().getId().equals(user.getId())) {
                throw new IllegalArgumentException("Username already exists: " + user.getUsername());
            }
            
            Optional<User> existingUserByEmail = userRepository.findByEmail(user.getEmail());
            if (existingUserByEmail.isPresent() && !existingUserByEmail.get().getId().equals(user.getId())) {
                throw new IllegalArgumentException("Email already exists: " + user.getEmail());
            }
        }

        User savedUser = userRepository.save(user);
        logger.debug("Saved user: {}", savedUser.getUsername());
        return savedUser;
    }

    /**
     * Find user by ID
     */
    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    /**
     * Find user by username
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Find user by email
     */
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Find user by username or email
     */
    public Optional<User> findByUsernameOrEmail(String usernameOrEmail) {
        return userRepository.findByUsernameOrEmail(usernameOrEmail);
    }

    /**
     * Check if username exists
     */
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * Check if email exists
     */
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * Find all users
     */
    public List<User> findAll() {
        return userRepository.findAll();
    }

    /**
     * Find users by role
     */
    public List<User> findByRole(User.UserRole role) {
        return userRepository.findByRole(role);
    }

    /**
     * Find active users
     */
    public List<User> findActiveUsers() {
        return userRepository.findActiveUsers();
    }

    /**
     * Find administrators
     */
    public List<User> findActiveAdministrators() {
        return userRepository.findActiveAdministrators();
    }

    /**
     * Find viewers
     */
    public List<User> findActiveViewers() {
        return userRepository.findActiveViewers();
    }

    /**
     * Delete user
     */
    public void deleteUser(UUID userId) {
        userRepository.deleteById(userId);
        logger.info("Deleted user with ID: {}", userId);
    }

    /**
     * Update last login
     */
    public void updateLastLogin(UUID userId) {
        userRepository.updateLastLogin(userId);
        logger.debug("Updated last login for user ID: {}", userId);
    }

    /**
     * Lock user account
     */
    public void lockUserAccount(UUID userId) {
        userRepository.lockAccount(userId);
        logger.info("Locked user account with ID: {}", userId);
    }

    /**
     * Unlock user account
     */
    public void unlockUserAccount(UUID userId) {
        userRepository.unlockAccount(userId);
        logger.info("Unlocked user account with ID: {}", userId);
    }

    /**
     * Change user password
     */
    public void changePassword(UUID userId, String newPassword) {
        String encodedPassword = passwordEncoder.encode(newPassword);
        userRepository.changePassword(userId, encodedPassword);
        logger.info("Changed password for user ID: {}", userId);
    }

    /**
     * Get user statistics
     */
    public Map<String, Long> getUserStatistics() {
        UserRepository.UserStatistics stats = userRepository.getUserStatistics();
        Map<String, Long> result = new HashMap<>();
        result.put("totalUsers", stats.totalUsers());
        result.put("enabledUsers", stats.enabledUsers());
        result.put("administrators", stats.administrators());
        result.put("viewers", stats.viewers());
        result.put("lockedUsers", stats.lockedUsers());
        return result;
    }

    /**
     * Search users by username, email, or full name
     */
    public List<User> searchUsers(String searchTerm) {
        return userRepository.searchUsers(searchTerm);
    }

    /**
     * Count users by role
     */
    public long countByRole(User.UserRole role) {
        return userRepository.countByRole(role);
    }

    /**
     * Find users created after date
     */
    public List<User> findUsersCreatedAfter(LocalDateTime date) {
        return userRepository.findUsersCreatedAfter(date);
    }

    /**
     * Find users who never logged in
     */
    public List<User> findUsersWhoNeverLoggedIn() {
        return userRepository.findUsersWhoNeverLoggedIn();
    }
}