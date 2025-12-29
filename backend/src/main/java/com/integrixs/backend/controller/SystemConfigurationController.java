package com.integrixs.backend.controller;

import com.integrixs.backend.model.User;
import com.integrixs.backend.service.SystemConfigurationService;
import com.integrixs.shared.model.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for system configuration management
 * Allows administrators to view and modify application settings
 */
@RestController
@RequestMapping("/api/configuration")
public class SystemConfigurationController {

    private static final Logger logger = LoggerFactory.getLogger(SystemConfigurationController.class);

    private final SystemConfigurationService configService;

    public SystemConfigurationController(SystemConfigurationService configService) {
        this.configService = configService;
    }

    /**
     * Get all configurations (Admin only)
     */
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<?> getAllConfigurations() {
        try {
            List<SystemConfiguration> configurations = configService.getAllConfigurations();
            
            Map<String, Object> response = new HashMap<>();
            response.put("configurations", configurations);
            response.put("total", configurations.size());
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting all configurations: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to retrieve configurations"));
        }
    }

    /**
     * Get configurations by category
     */
    @GetMapping("/category/{category}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<?> getConfigurationsByCategory(@PathVariable String category) {
        try {
            SystemConfiguration.ConfigCategory configCategory = SystemConfiguration.ConfigCategory.valueOf(category.toUpperCase());
            List<SystemConfiguration> configurations = configService.getConfigurationsByCategory(configCategory);
            
            Map<String, Object> response = new HashMap<>();
            response.put("category", configCategory);
            response.put("configurations", configurations);
            response.put("count", configurations.size());
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid category: " + category));
        } catch (Exception e) {
            logger.error("Error getting configurations by category '{}': {}", category, e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to retrieve configurations"));
        }
    }

    /**
     * Get dashboard refresh intervals for frontend use
     */
    @GetMapping("/dashboard/intervals")
    @PreAuthorize("hasAnyAuthority('ADMINISTRATOR', 'VIEWER')")
    public ResponseEntity<?> getDashboardRefreshIntervals() {
        try {
            Map<String, Integer> intervals = configService.getDashboardRefreshIntervals();
            
            Map<String, Object> response = new HashMap<>();
            response.put("intervals", intervals);
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting dashboard refresh intervals: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to retrieve dashboard intervals"));
        }
    }

    /**
     * Get configuration by key
     */
    @GetMapping("/key/{configKey}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<?> getConfigurationByKey(@PathVariable String configKey) {
        try {
            Optional<SystemConfiguration> configuration = configService.getConfigurationByKey(configKey);
            
            if (configuration.isPresent()) {
                return ResponseEntity.ok(configuration.get());
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            logger.error("Error getting configuration by key '{}': {}", configKey, e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to retrieve configuration"));
        }
    }

    /**
     * Update configuration value
     */
    @PutMapping("/key/{configKey}/value")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<?> updateConfigurationValue(
            @PathVariable String configKey, 
            @RequestBody Map<String, String> request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = (User) auth.getPrincipal();
            
            String newValue = request.get("value");
            if (newValue == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Value is required"));
            }

            configService.updateConfigurationValue(configKey, newValue, currentUser.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Configuration updated successfully");
            response.put("configKey", configKey);
            response.put("newValue", newValue);
            response.put("updatedBy", currentUser.getUsername());
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating configuration value for key '{}': {}", configKey, e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to update configuration"));
        }
    }

    /**
     * Reset configuration to default value
     */
    @PutMapping("/key/{configKey}/reset")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<?> resetConfigurationToDefault(@PathVariable String configKey) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = (User) auth.getPrincipal();

            configService.resetToDefault(configKey, currentUser.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Configuration reset to default value");
            response.put("configKey", configKey);
            response.put("resetBy", currentUser.getUsername());
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error resetting configuration '{}' to default: {}", configKey, e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to reset configuration"));
        }
    }

    /**
     * Create new configuration
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<?> createConfiguration(@RequestBody SystemConfiguration configuration) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = (User) auth.getPrincipal();

            SystemConfiguration saved = configService.saveConfiguration(configuration, currentUser.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Configuration created successfully");
            response.put("configuration", saved);
            response.put("createdBy", currentUser.getUsername());
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating configuration: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to create configuration"));
        }
    }

    /**
     * Update entire configuration
     */
    @PutMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<?> updateConfiguration(@RequestBody SystemConfiguration configuration) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = (User) auth.getPrincipal();

            SystemConfiguration saved = configService.saveConfiguration(configuration, currentUser.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Configuration updated successfully");
            response.put("configuration", saved);
            response.put("updatedBy", currentUser.getUsername());
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating configuration: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to update configuration"));
        }
    }

    /**
     * Delete configuration (if not readonly)
     */
    @DeleteMapping("/key/{configKey}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<?> deleteConfiguration(@PathVariable String configKey) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = (User) auth.getPrincipal();

            boolean deleted = configService.deleteConfiguration(configKey);
            
            if (deleted) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Configuration deleted successfully");
                response.put("configKey", configKey);
                response.put("deletedBy", currentUser.getUsername());
                response.put("timestamp", java.time.LocalDateTime.now());
                
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(403)
                        .body(Map.of("error", "Configuration cannot be deleted (may be readonly or not found)"));
            }

        } catch (Exception e) {
            logger.error("Error deleting configuration '{}': {}", configKey, e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to delete configuration"));
        }
    }

    /**
     * Get configuration statistics
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<?> getConfigurationStatistics() {
        try {
            Map<String, Object> statistics = configService.getConfigurationStatistics();
            statistics.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(statistics);

        } catch (Exception e) {
            logger.error("Error getting configuration statistics: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to retrieve configuration statistics"));
        }
    }

    /**
     * Clear configuration cache (for development/testing)
     */
    @PostMapping("/cache/clear")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<?> clearConfigurationCache() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = (User) auth.getPrincipal();

            configService.clearCache();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Configuration cache cleared successfully");
            response.put("clearedBy", currentUser.getUsername());
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error clearing configuration cache: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to clear configuration cache"));
        }
    }

    /**
     * Get available configuration categories and types
     */
    @GetMapping("/metadata")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<?> getConfigurationMetadata() {
        try {
            Map<String, Object> metadata = new HashMap<>();
            
            // Available categories
            Map<String, String> categories = new HashMap<>();
            for (SystemConfiguration.ConfigCategory category : SystemConfiguration.ConfigCategory.values()) {
                categories.put(category.name(), category.getDescription());
            }
            metadata.put("categories", categories);
            
            // Available types
            Map<String, String> types = new HashMap<>();
            for (SystemConfiguration.ConfigType type : SystemConfiguration.ConfigType.values()) {
                types.put(type.name(), type.getDescription());
            }
            metadata.put("types", types);
            
            metadata.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(metadata);

        } catch (Exception e) {
            logger.error("Error getting configuration metadata: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to retrieve configuration metadata"));
        }
    }
}