package com.integrixs.backend.controller;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.core.service.AdapterManagementService;
import com.integrixs.core.service.DeployedFlowSchedulingService;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.util.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Interface Controller - Adapter Interface Management
 * Maps to /api/interfaces to match frontend expectations
 * This is essentially an alias/proxy for AdapterController functionality
 */
@RestController
@RequestMapping("/api/interfaces")
public class InterfaceController {
    
    private static final Logger logger = LoggerFactory.getLogger(InterfaceController.class);
    
    private final AdapterManagementService adapterManagementService;
    private final DeployedFlowSchedulingService deployedFlowSchedulingService;
    
    @Autowired
    public InterfaceController(AdapterManagementService adapterManagementService,
                              DeployedFlowSchedulingService deployedFlowSchedulingService) {
        this.adapterManagementService = adapterManagementService;
        this.deployedFlowSchedulingService = deployedFlowSchedulingService;
    }
    
    @GetMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<List<Adapter>>> getAllInterfaces(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "direction", required = false) String direction,
            @RequestParam(value = "enabled", required = false) Boolean enabled) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting interfaces (type: {}, direction: {}, enabled: {})", 
                   currentUser, type, direction, enabled);
        
        try {
            List<Adapter> adapters;
            
            if (enabled != null && enabled) {
                adapters = adapterManagementService.getActiveAdapters();
            } else if (type != null && direction != null) {
                adapters = adapterManagementService.getAdaptersByTypeAndDirection(type.toUpperCase(), direction.toUpperCase());
            } else if (type != null) {
                adapters = adapterManagementService.getAdaptersByType(type.toUpperCase());
            } else {
                adapters = adapterManagementService.getAllAdapters();
            }
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Interfaces retrieved successfully", 
                adapters
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid parameter from user {}: {}", currentUser, e.getMessage());
            return ResponseEntity.status(400)
                .body(new ApiResponse<>(false, "Invalid parameter: " + e.getMessage(), null));
        } catch (Exception e) {
            logger.error("Error retrieving interfaces for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to retrieve interfaces", null));
        }
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Adapter>> getInterfaceById(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting interface: {}", currentUser, id);
        
        try {
            UUID adapterId = UUID.fromString(id);
            Optional<Adapter> adapter = adapterManagementService.getAdapterById(adapterId);
            
            if (adapter.isEmpty()) {
                return ResponseEntity.status(404)
                    .body(new ApiResponse<>(false, "Interface not found", null));
            }
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Interface retrieved successfully", 
                adapter.get()
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid interface ID format: {}", id);
            return ResponseEntity.status(400)
                .body(new ApiResponse<>(false, "Invalid interface ID format", null));
        } catch (Exception e) {
            logger.error("Error retrieving interface {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to retrieve interface", null));
        }
    }
    
    @PostMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Adapter>> createInterface(@RequestBody Adapter adapterData) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} creating interface: {}", currentUser, adapterData.getName());
        
        try {
            UUID createdBy = UUID.fromString(currentUser);
            Adapter createdAdapter = adapterManagementService.createAdapter(adapterData, createdBy);
            
            logger.info("Successfully created interface for user {}", currentUser);
            return ResponseEntity.status(201)
                .body(new ApiResponse<>(
                    true, 
                    "Interface created successfully", 
                    createdAdapter
                ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid interface data from user {}: {}", currentUser, e.getMessage());
            return ResponseEntity.status(400)
                .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            logger.error("Error creating interface for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to create interface", null));
        }
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Adapter>> updateInterface(
            @PathVariable String id, 
            @RequestBody Adapter adapterData) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} updating interface: {}", currentUser, id);
        
        try {
            UUID adapterId = UUID.fromString(id);
            UUID updatedBy = UUID.fromString(currentUser);
            Adapter updatedAdapter = adapterManagementService.updateAdapter(adapterId, adapterData, updatedBy);
            
            logger.info("Successfully updated interface {} for user {}", id, currentUser);
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Interface updated successfully", 
                updatedAdapter
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for interface {}: {}", currentUser, id, e.getMessage());
            return ResponseEntity.status(400)
                .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            logger.error("Error updating interface {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to update interface", null));
        }
    }
    
    @PutMapping("/{id}/enabled")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Void>> setInterfaceEnabled(@PathVariable String id, @RequestParam(value = "enabled") boolean enabled) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} setting interface {} enabled status to: {}", currentUser, id, enabled);
        
        try {
            UUID adapterId = UUID.fromString(id);
            adapterManagementService.setAdapterActive(adapterId, enabled);
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Interface enabled status updated successfully", 
                null
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for interface {}: {}", currentUser, id, e.getMessage());
            return ResponseEntity.status(400)
                .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            logger.error("Error updating interface enabled status {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to update interface enabled status", null));
        }
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Void>> deleteInterface(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} deleting interface: {}", currentUser, id);
        
        try {
            UUID adapterId = UUID.fromString(id);
            boolean deleted = adapterManagementService.deleteAdapter(adapterId);
            
            if (deleted) {
                logger.info("Successfully deleted interface {} for user {}", id, currentUser);
                return ResponseEntity.ok(new ApiResponse<>(
                    true, 
                    "Interface deleted successfully", 
                    null
                ));
            } else {
                return ResponseEntity.status(404)
                    .body(new ApiResponse<>(false, "Interface not found", null));
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for interface {}: {}", currentUser, id, e.getMessage());
            return ResponseEntity.status(400)
                .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            logger.error("Error deleting interface {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to delete interface", null));
        }
    }
    
    @PostMapping("/{id}/test")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testInterface(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} testing interface connection: {}", currentUser, id);
        
        try {
            UUID adapterId = UUID.fromString(id);
            Map<String, Object> testResult = adapterManagementService.testAdapterConnection(adapterId);
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Interface connection test completed", 
                testResult
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for interface {}: {}", currentUser, id, e.getMessage());
            return ResponseEntity.status(400)
                .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            logger.error("Error testing interface {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to test interface connection", null));
        }
    }
    
    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeInterface(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} executing interface: {}", currentUser, id);
        
        try {
            UUID adapterId = UUID.fromString(id);
            UUID currentUserId = UUID.fromString(currentUser);
            
            // Use the real flow execution instead of simulation mode
            Map<String, Object> executeResult = deployedFlowSchedulingService.manuallyTriggerAdapterExecution(adapterId, currentUserId);
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Interface execution initiated successfully", 
                executeResult
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for interface {}: {}", currentUser, id, e.getMessage());
            return ResponseEntity.status(400)
                .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            logger.error("Error executing interface {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to execute interface", null));
        }
    }
    
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Void>> startInterface(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} starting interface: {}", currentUser, id);
        
        try {
            UUID adapterId = UUID.fromString(id);
            adapterManagementService.startAdapter(adapterId);
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Interface started successfully", 
                null
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for interface {}: {}", currentUser, id, e.getMessage());
            return ResponseEntity.status(400)
                .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            logger.error("Error starting interface {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to start interface", null));
        }
    }
    
    @PostMapping("/{id}/stop")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Void>> stopInterface(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} stopping interface: {}", currentUser, id);
        
        try {
            UUID adapterId = UUID.fromString(id);
            adapterManagementService.stopAdapter(adapterId);
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Interface stopped successfully", 
                null
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for interface {}: {}", currentUser, id, e.getMessage());
            return ResponseEntity.status(400)
                .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            logger.error("Error stopping interface {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to stop interface", null));
        }
    }
    
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInterfaceStatistics() {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting interface statistics", currentUser);
        
        try {
            Map<String, Object> statistics = adapterManagementService.getAdapterStatistics();
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Interface statistics retrieved successfully", 
                statistics
            ));
        } catch (Exception e) {
            logger.error("Error retrieving interface statistics for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to retrieve interface statistics", null));
        }
    }
    
    @GetMapping("/types")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<List<String>>> getSupportedTypes() {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting supported interface types", currentUser);
        
        try {
            // Return supported adapter types
            List<String> supportedTypes = List.of("FILE", "SFTP", "EMAIL");
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Supported interface types retrieved successfully", 
                supportedTypes
            ));
        } catch (Exception e) {
            logger.error("Error retrieving supported interface types for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to retrieve supported interface types", null));
        }
    }
}