package com.integrixs.backend.controller;

import com.integrixs.backend.service.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Properties;

/**
 * REST controller for configuration management
 */
@RestController
@RequestMapping("/api/configuration")
public class ConfigurationController {

    @Autowired
    private ConfigurationService configurationService;

    /**
     * Get configuration for a specific bank and operation type
     */
    @GetMapping("/bank/{bankName}/{operationType}/{direction}")
    public ResponseEntity<Map<String, String>> getBankConfiguration(
            @PathVariable String bankName,
            @PathVariable String operationType,
            @PathVariable String direction) {
        
        try {
            Map<String, String> config = configurationService.getBankConfiguration(bankName, operationType, direction);
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get application configuration
     */
    @GetMapping("/application")
    public ResponseEntity<Map<String, String>> getApplicationConfiguration() {
        try {
            Map<String, String> config = configurationService.getApplicationConfiguration();
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Update configuration for a specific bank and operation type
     */
    @PutMapping("/bank/{bankName}/{operationType}/{direction}")
    public ResponseEntity<String> updateBankConfiguration(
            @PathVariable String bankName,
            @PathVariable String operationType,
            @PathVariable String direction,
            @RequestBody Map<String, String> configuration) {
        
        try {
            configurationService.updateBankConfiguration(bankName, operationType, direction, configuration);
            return ResponseEntity.ok("Configuration updated successfully");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to update configuration: " + e.getMessage());
        }
    }

    /**
     * Validate configuration for a specific bank and operation type
     */
    @PostMapping("/validate/{bankName}/{operationType}/{direction}")
    public ResponseEntity<Map<String, Object>> validateConfiguration(
            @PathVariable String bankName,
            @PathVariable String operationType,
            @PathVariable String direction) {
        
        try {
            Map<String, Object> validationResult = configurationService.validateConfiguration(bankName, operationType, direction);
            return ResponseEntity.ok(validationResult);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get all available configuration templates
     */
    @GetMapping("/templates")
    public ResponseEntity<Map<String, Map<String, String>>> getConfigurationTemplates() {
        try {
            Map<String, Map<String, String>> templates = configurationService.getConfigurationTemplates();
            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}