package com.integrixs.backend.controller;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.backend.dto.PackageDto;
import com.integrixs.backend.dto.PackageRequest;
import com.integrixs.core.service.PackageMetadataService;
import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.core.repository.IntegrationFlowRepository;
import com.integrixs.shared.model.IntegrationPackage;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Package Lifecycle Management Service
 * Handles basic CRUD operations for integration packages
 * 
 * This service was extracted from the monolithic PackageController (1,059 lines)
 * to follow Single Responsibility Principle and improve maintainability.
 * Called through PackageController facade, not directly as REST endpoints.
 */
@Component
public class PackageLifecycleController extends BasePackageController {

    private final PackageMetadataService packageService;
    private final AdapterRepository adapterRepository;
    private final IntegrationFlowRepository flowRepository;

    @Autowired
    public PackageLifecycleController(
            PackageMetadataService packageService,
            AdapterRepository adapterRepository,
            IntegrationFlowRepository flowRepository) {
        this.packageService = packageService;
        this.adapterRepository = adapterRepository;
        this.flowRepository = flowRepository;
    }
    
    /**
     * Get all active packages
     * GET /api/packages
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PackageDto.Package>>> getAllPackages() {
        try {
            logger.debug("Retrieving all active packages");

            List<IntegrationPackage> packages = packageService.findAllActivePackages();
            List<PackageDto.Package> packageDtos = packages.stream()
                .map(pkg -> {
                    // Get adapter counts for this package
                    int adapterCount = adapterRepository.findByPackageId(pkg.getId()).size();
                    int activeAdapterCount = (int) adapterRepository.findByPackageId(pkg.getId()).stream()
                        .filter(a -> a.getActive() != null && a.getActive())
                        .count();

                    // Get flow counts for this package
                    int flowCount = flowRepository.findByPackageId(pkg.getId()).size();
                    int activeFlowCount = (int) flowRepository.findByPackageId(pkg.getId()).stream()
                        .filter(f -> f.getActive() != null && f.getActive())
                        .count();

                    return convertToPackageDtoWithCounts(pkg, adapterCount, activeAdapterCount, flowCount, activeFlowCount);
                })
                .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(packageDtos));

        } catch (Exception e) {
            logger.error("Failed to retrieve packages", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to retrieve packages: " + e.getMessage()));
        }
    }
    
    /**
     * Get package by ID
     * GET /api/packages/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PackageDto.Package>> getPackageById(@PathVariable UUID id) {
        try {
            logger.debug("Retrieving package: {}", id);
            
            IntegrationPackage pkg = packageService.findPackageById(id);
            PackageDto.Package packageDto = convertToPackageDto(pkg);
            
            return ResponseEntity.ok(ApiResponse.success(packageDto));
            
        } catch (IllegalStateException e) {
            logger.warn("Package not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Package not found: " + id));
        } catch (Exception e) {
            logger.error("Failed to retrieve package: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to retrieve package: " + e.getMessage()));
        }
    }
    
    /**
     * Create new package
     * POST /api/packages
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PackageDto.Package>> createPackage(
            @Valid @RequestBody PackageRequest.Create request) {
        try {
            logger.info("Creating package: {}", request.getName());
            
            UUID currentUserId = getCurrentUserId();
            
            PackageMetadataService.PackageCreationRequest creationRequest = 
                new PackageMetadataService.PackageCreationRequest(
                    request.getName(), 
                    request.getDescription(), 
                    request.getVersion(), 
                    null
                );
            
            if (request.getConfiguration() != null) {
                creationRequest.setConfiguration(request.getConfiguration());
            }
            
            IntegrationPackage createdPackage = packageService.createPackage(creationRequest);
            PackageDto.Package packageDto = convertToPackageDto(createdPackage);
            
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Package created successfully", packageDto));
                
        } catch (IllegalArgumentException e) {
            logger.warn("Package creation failed - validation error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Package creation failed: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to create package", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to create package: " + e.getMessage()));
        }
    }
    
    /**
     * Update existing package
     * PUT /api/packages/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PackageDto.Package>> updatePackage(
            @PathVariable UUID id,
            @Valid @RequestBody PackageRequest.Update request) {
        try {
            logger.info("Updating package: {}", id);
            
            PackageMetadataService.PackageUpdateRequest updateRequest = 
                new PackageMetadataService.PackageUpdateRequest();
            
            updateRequest.setName(request.getName());
            updateRequest.setDescription(request.getDescription());
            updateRequest.setVersion(request.getVersion());
            updateRequest.setStatus(request.getStatus());
            updateRequest.setConfiguration(request.getConfiguration());
            
            IntegrationPackage updatedPackage = packageService.updatePackage(id, updateRequest);
            PackageDto.Package packageDto = convertToPackageDto(updatedPackage);
            
            return ResponseEntity.ok(ApiResponse.success("Package updated successfully", packageDto));
            
        } catch (IllegalStateException e) {
            logger.warn("Package not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Package not found: " + id));
        } catch (IllegalArgumentException e) {
            logger.warn("Package update failed - validation error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Package update failed: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to update package: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to update package: " + e.getMessage()));
        }
    }
    
    /**
     * Delete package (hard delete)
     * DELETE /api/packages/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deletePackage(@PathVariable UUID id) {
        try {
            logger.info("Deleting package: {}", id);
            
            boolean deleted = packageService.deletePackage(id);
            
            if (deleted) {
                return ResponseEntity.ok(ApiResponse.success("Package deleted successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Package not found: " + id));
            }
            
        } catch (IllegalStateException e) {
            logger.warn("Cannot delete package: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Cannot delete package: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to delete package: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to delete package: " + e.getMessage()));
        }
    }
    
    /**
     * Check package name availability
     * GET /api/packages/check-name/{name}
     */
    @GetMapping("/check-name/{name}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkNameAvailability(@PathVariable String name) {
        try {
            logger.debug("Checking name availability: {}", name);
            
            boolean available = packageService.isPackageNameAvailable(name);
            
            Map<String, Object> result = new HashMap<>();
            result.put("name", name);
            result.put("available", available);
            result.put("message", available ? "Name is available" : "Name is already in use");
            
            return ResponseEntity.ok(ApiResponse.success(result));
            
        } catch (Exception e) {
            logger.error("Failed to check name availability: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to check name availability: " + e.getMessage()));
        }
    }
    
    /**
     * Search packages with filtering and sorting
     * GET /api/packages/search
     */
    public ResponseEntity<ApiResponse<Map<String, Object>>> searchPackages(
            String searchTerm, String sortBy, String sortOrder, int page, int size) {
        try {
            logger.debug("Searching packages with term: {}, sortBy: {}, sortOrder: {}", searchTerm, sortBy, sortOrder);
            
            // Get packages using the existing service
            List<IntegrationPackage> packages = packageService.findAllActivePackages();
            
            // Apply search filtering if search term provided
            if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                String lowerSearchTerm = searchTerm.toLowerCase().trim();
                packages = packages.stream()
                    .filter(pkg -> pkg.getName().toLowerCase().contains(lowerSearchTerm) ||
                                  (pkg.getDescription() != null && pkg.getDescription().toLowerCase().contains(lowerSearchTerm)))
                    .collect(Collectors.toList());
            }
            
            // Apply sorting
            if ("name".equals(sortBy)) {
                packages = packages.stream()
                    .sorted("desc".equals(sortOrder) ?
                        Comparator.comparing(IntegrationPackage::getName).reversed() :
                        Comparator.comparing(IntegrationPackage::getName))
                    .collect(Collectors.toList());
            } else if ("updatedAt".equals(sortBy)) {
                // If updatedAt is null, use createdAt for sorting
                packages = packages.stream()
                    .sorted("desc".equals(sortOrder) ?
                        Comparator.comparing((IntegrationPackage pkg) ->
                            pkg.getUpdatedAt() != null ? pkg.getUpdatedAt() : pkg.getCreatedAt()).reversed() :
                        Comparator.comparing((IntegrationPackage pkg) ->
                            pkg.getUpdatedAt() != null ? pkg.getUpdatedAt() : pkg.getCreatedAt()))
                    .collect(Collectors.toList());
            }
            
            // Store total count before pagination
            int totalItems = packages.size();
            
            // Apply pagination
            int startIndex = page * size;
            int endIndex = Math.min(startIndex + size, totalItems);
            if (startIndex < totalItems) {
                packages = packages.subList(startIndex, endIndex);
            } else {
                packages = new ArrayList<>();
            }
            
            // Convert to DTOs with asset counts
            List<PackageDto.Package> packageDtos = packages.stream()
                .map(pkg -> {
                    // Get adapter counts for this package
                    int adapterCount = adapterRepository.findByPackageId(pkg.getId()).size();
                    int activeAdapterCount = (int) adapterRepository.findByPackageId(pkg.getId()).stream()
                        .filter(a -> a.getActive() != null && a.getActive())
                        .count();

                    // Get flow counts for this package
                    int flowCount = flowRepository.findByPackageId(pkg.getId()).size();
                    int activeFlowCount = (int) flowRepository.findByPackageId(pkg.getId()).stream()
                        .filter(f -> f.getActive() != null && f.getActive())
                        .count();

                    return convertToPackageDtoWithCounts(pkg, adapterCount, activeAdapterCount, flowCount, activeFlowCount);
                })
                .collect(Collectors.toList());
            
            // Create response with pagination info
            Map<String, Object> result = new HashMap<>();
            result.put("packages", packageDtos);
            result.put("totalItems", totalItems);
            result.put("currentPage", page);
            result.put("itemsPerPage", size);
            result.put("searchTerm", searchTerm);
            result.put("sortBy", sortBy);
            result.put("sortOrder", sortOrder);
            
            return ResponseEntity.ok(ApiResponse.success(result));
            
        } catch (Exception e) {
            logger.error("Failed to search packages", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to search packages: " + e.getMessage()));
        }
    }
}