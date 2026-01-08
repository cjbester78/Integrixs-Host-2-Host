package com.integrixs.backend.controller;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.backend.dto.ExecutionValidationResult;
import com.integrixs.backend.dto.request.InterfaceListRequest;
import com.integrixs.backend.dto.request.InterfaceOperationRequest;
import com.integrixs.backend.dto.response.InterfaceDetailsResponse;
import com.integrixs.backend.dto.response.InterfaceSummaryResponse;
import com.integrixs.backend.exception.*;
import com.integrixs.backend.service.InterfaceRequestValidationService;
import com.integrixs.backend.service.ResponseStandardizationService;
import com.integrixs.core.service.AdapterManagementService;
import com.integrixs.core.service.DeployedFlowSchedulingService;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.util.AuditUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Interface Controller - Adapter Interface Management
 * Maps to /api/interfaces to match frontend expectations
 * Refactored to follow OOP principles with proper validation, DTOs, and error handling.
 * Part of Phase 4 controller layer refactoring.
 */
@RestController
@RequestMapping("/api/interfaces")
public class InterfaceController {
    
    private static final Logger logger = LoggerFactory.getLogger(InterfaceController.class);
    
    private final AdapterManagementService adapterManagementService;
    private final DeployedFlowSchedulingService deployedFlowSchedulingService;
    private final InterfaceRequestValidationService validationService;
    private final ResponseStandardizationService responseService;
    
    @Autowired
    public InterfaceController(AdapterManagementService adapterManagementService,
                              DeployedFlowSchedulingService deployedFlowSchedulingService,
                              InterfaceRequestValidationService validationService,
                              ResponseStandardizationService responseService) {
        this.adapterManagementService = adapterManagementService;
        this.deployedFlowSchedulingService = deployedFlowSchedulingService;
        this.validationService = validationService;
        this.responseService = responseService;
    }
    
    @GetMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<List<InterfaceSummaryResponse>>> getAllInterfaces(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "direction", required = false) String direction,
            @RequestParam(value = "enabled", required = false) Boolean enabled) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        InterfaceListRequest listRequest = InterfaceListRequest.builder()
            .type(type)
            .direction(direction)
            .enabled(enabled)
            .requestedBy(currentUserId)
            .build();
        
        // Validate request using strategy pattern
        Map<String, Object> validationParams = Map.of(
            "type", type != null ? type : "",
            "direction", direction != null ? direction : "",
            "enabled", enabled != null ? enabled : ""
        );
        ExecutionValidationResult validation = validationService.validateListRequest(validationParams);
        if (!validation.isValid()) {
            throw new InterfaceValidationException(validation);
        }
        
        List<Adapter> adapters = fetchAdapters(listRequest);
        List<InterfaceSummaryResponse> interfaceSummaries = adapters.stream()
            .map(this::mapToSummaryResponse)
            .collect(Collectors.toList());
            
        return responseService.success(interfaceSummaries, "Interfaces retrieved successfully");
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<InterfaceDetailsResponse>> getInterfaceById(@PathVariable String id) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Validate request using strategy pattern
        ExecutionValidationResult validation = validationService.validateDetailsRequest(id);
        if (!validation.isValid()) {
            throw new InterfaceValidationException(validation);
        }
        
        UUID adapterId = UUID.fromString(id);
        Optional<Adapter> adapter = adapterManagementService.getAdapterById(adapterId);
        
        if (adapter.isEmpty()) {
            throw new InterfaceNotFoundException(adapterId);
        }
        
        InterfaceDetailsResponse interfaceDetails = mapToDetailsResponse(adapter.get());
        
        return responseService.success(interfaceDetails, "Interface retrieved successfully");
    }
    
    @PostMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<InterfaceDetailsResponse>> createInterface(@RequestBody Adapter adapterData) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Validate request using strategy pattern
        ExecutionValidationResult validation = validationService.validateCreateRequest(adapterData);
        if (!validation.isValid()) {
            throw new InterfaceValidationException(validation);
        }
        
        Adapter createdAdapter = adapterManagementService.createAdapter(adapterData, currentUserId);
        InterfaceDetailsResponse interfaceDetails = mapToDetailsResponse(createdAdapter);
        
        return responseService.created(interfaceDetails, "Interface created successfully");
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<InterfaceDetailsResponse>> updateInterface(
            @PathVariable String id, 
            @RequestBody Adapter adapterData) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Validate request using strategy pattern
        ExecutionValidationResult validation = validationService.validateUpdateRequest(id, adapterData);
        if (!validation.isValid()) {
            throw new InterfaceValidationException(validation);
        }
        
        UUID adapterId = UUID.fromString(id);
        Adapter updatedAdapter = adapterManagementService.updateAdapter(adapterId, adapterData, currentUserId);
        InterfaceDetailsResponse interfaceDetails = mapToDetailsResponse(updatedAdapter);
        
        return responseService.success(interfaceDetails, "Interface updated successfully");
    }
    
    @PutMapping("/{id}/enabled")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Void>> setInterfaceEnabled(@PathVariable String id, @RequestParam(value = "enabled") boolean enabled) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create operation request
        InterfaceOperationRequest operationRequest = InterfaceOperationRequest.enableRequest(
            UUID.fromString(id), enabled, currentUserId
        );
        
        // Validate request using strategy pattern
        ExecutionValidationResult validation = validationService.validateLifecycleRequest(id, operationRequest.getOperation());
        if (!validation.isValid()) {
            throw new InterfaceValidationException(validation);
        }
        
        UUID adapterId = UUID.fromString(id);
        adapterManagementService.setAdapterActive(adapterId, enabled);
        
        return responseService.success(null, "Interface enabled status updated successfully");
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Void>> deleteInterface(@PathVariable String id) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Validate request using strategy pattern
        ExecutionValidationResult validation = validationService.validateDeleteRequest(id);
        if (!validation.isValid()) {
            throw new InterfaceValidationException(validation);
        }
        
        UUID adapterId = UUID.fromString(id);
        boolean deleted = adapterManagementService.deleteAdapter(adapterId);
        
        if (!deleted) {
            throw new InterfaceNotFoundException(adapterId);
        }
        
        return responseService.success(null, "Interface deleted successfully");
    }
    
    @PostMapping("/{id}/test")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testInterface(@PathVariable String id) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create operation request
        InterfaceOperationRequest operationRequest = InterfaceOperationRequest.testRequest(
            UUID.fromString(id), currentUserId
        );
        
        // Validate request using strategy pattern
        ExecutionValidationResult validation = validationService.validateTestRequest(id);
        if (!validation.isValid()) {
            throw new InterfaceValidationException(validation);
        }
        
        UUID adapterId = UUID.fromString(id);
        Map<String, Object> testResult = adapterManagementService.testAdapterConnection(adapterId);
        
        return responseService.success(testResult, "Interface connection test completed");
    }
    
    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeInterface(@PathVariable String id) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create operation request
        InterfaceOperationRequest operationRequest = InterfaceOperationRequest.executeRequest(
            UUID.fromString(id), currentUserId
        );
        
        // Validate request using strategy pattern
        ExecutionValidationResult validation = validationService.validateExecuteRequest(id);
        if (!validation.isValid()) {
            throw new InterfaceValidationException(validation);
        }
        
        UUID adapterId = UUID.fromString(id);
        Map<String, Object> executeResult = deployedFlowSchedulingService.manuallyTriggerAdapterExecution(adapterId, currentUserId);
        
        return responseService.success(executeResult, "Interface execution initiated successfully");
    }
    
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Void>> startInterface(@PathVariable String id) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create operation request
        InterfaceOperationRequest operationRequest = InterfaceOperationRequest.startRequest(
            UUID.fromString(id), currentUserId
        );
        
        // Validate request using strategy pattern
        ExecutionValidationResult validation = validationService.validateLifecycleRequest(id, "start");
        if (!validation.isValid()) {
            throw new InterfaceValidationException(validation);
        }
        
        UUID adapterId = UUID.fromString(id);
        adapterManagementService.startAdapter(adapterId);
        
        return responseService.success(null, "Interface started successfully");
    }
    
    @PostMapping("/{id}/stop")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Void>> stopInterface(@PathVariable String id) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create operation request
        InterfaceOperationRequest operationRequest = InterfaceOperationRequest.stopRequest(
            UUID.fromString(id), currentUserId
        );
        
        // Validate request using strategy pattern
        ExecutionValidationResult validation = validationService.validateLifecycleRequest(id, "stop");
        if (!validation.isValid()) {
            throw new InterfaceValidationException(validation);
        }
        
        UUID adapterId = UUID.fromString(id);
        adapterManagementService.stopAdapter(adapterId);
        
        return responseService.success(null, "Interface stopped successfully");
    }
    
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInterfaceStatistics() {
        
        Map<String, Object> statistics = adapterManagementService.getAdapterStatistics();
        
        return responseService.success(statistics, "Interface statistics retrieved successfully");
    }
    
    @GetMapping("/types")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<List<String>>> getSupportedTypes() {
        
        List<String> supportedTypes = List.of("FILE", "SFTP", "EMAIL");
        
        return responseService.success(supportedTypes, "Supported interface types retrieved successfully");
    }
    
    // ===============================
    // Private Helper Methods - OOP Refactoring Support
    // ===============================
    
    /**
     * Get current user ID using AuditUtils for consistency.
     */
    private UUID getCurrentUserId() {
        try {
            String userIdString = AuditUtils.getCurrentUserId();
            return UUID.fromString(userIdString);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid user ID format", e);
        }
    }
    
    /**
     * Fetch adapters based on list request filters.
     */
    private List<Adapter> fetchAdapters(InterfaceListRequest listRequest) {
        if (listRequest.getEnabled() != null && listRequest.getEnabled()) {
            return adapterManagementService.getActiveAdapters();
        } else if (listRequest.getType() != null && listRequest.getDirection() != null) {
            return adapterManagementService.getAdaptersByTypeAndDirection(
                listRequest.getType().toUpperCase(), 
                listRequest.getDirection().toUpperCase()
            );
        } else if (listRequest.getType() != null) {
            return adapterManagementService.getAdaptersByType(listRequest.getType().toUpperCase());
        } else {
            return adapterManagementService.getAllAdapters();
        }
    }
    
    /**
     * Map Adapter entity to InterfaceSummaryResponse DTO.
     */
    private InterfaceSummaryResponse mapToSummaryResponse(Adapter adapter) {
        return InterfaceSummaryResponse.builder()
            .id(adapter.getId())
            .name(adapter.getName())
            .adapterType(adapter.getAdapterType())
            .direction(adapter.getDirection())
            .isActive(adapter.isActive())
            .status(adapter.isActive() ? "ACTIVE" : "INACTIVE")
            .description(adapter.getDescription())
            .createdAt(adapter.getCreatedAt())
            .updatedAt(adapter.getUpdatedAt())
            .build();
    }
    
    /**
     * Map Adapter entity to InterfaceDetailsResponse DTO.
     */
    private InterfaceDetailsResponse mapToDetailsResponse(Adapter adapter) {
        return InterfaceDetailsResponse.builder()
            .id(adapter.getId())
            .name(adapter.getName())
            .description(adapter.getDescription())
            .adapterType(adapter.getAdapterType())
            .direction(adapter.getDirection())
            .isActive(adapter.isActive())
            .status(adapter.isActive() ? "ACTIVE" : "INACTIVE")
            .configuration(adapter.getConfiguration())
            .createdBy(adapter.getCreatedBy())
            .updatedBy(adapter.getUpdatedBy())
            .createdAt(adapter.getCreatedAt())
            .updatedAt(adapter.getUpdatedAt())
            .version("1.0") // Could be derived from adapter metadata
            .build();
    }
}