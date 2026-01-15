package com.integrixs.backend.controller;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.backend.dto.PackageDto;
import com.integrixs.backend.dto.PackageRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Main Package Controller - Facade for focused package controllers
 * Delegates to specialized controllers following the facade pattern and Single Responsibility Principle
 * 
 * This controller has been refactored from a monolithic 1,059-line class into focused controllers:
 * - PackageLifecycleController: Basic CRUD operations
 * - PackageContainerController: Container and deployment operations
 * - PackageAssetController: Asset management operations  
 * - PackageAnalyticsController: Statistics and reporting
 * 
 * This facade maintains backward compatibility while providing clean separation of concerns.
 */
@RestController
@RequestMapping("/api/packages")
@CrossOrigin(origins = "*")
public class PackageController {
    
    private final PackageLifecycleController lifecycleController;
    private final PackageContainerController containerController;
    private final PackageAssetController assetController;
    private final PackageAnalyticsController analyticsController;
    
    @Autowired
    public PackageController(
            PackageLifecycleController lifecycleController,
            PackageContainerController containerController,
            PackageAssetController assetController,
            PackageAnalyticsController analyticsController) {
        this.lifecycleController = lifecycleController;
        this.containerController = containerController;
        this.assetController = assetController;
        this.analyticsController = analyticsController;
    }
    
    // ===== Package Lifecycle Operations (delegated to PackageLifecycleController) =====
    
    /**
     * Get all active packages
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PackageDto.Package>>> getAllPackages() {
        return lifecycleController.getAllPackages();
    }
    
    /**
     * Search packages with filtering and sorting
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Map<String, Object>>> searchPackages(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false, defaultValue = "updatedAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return lifecycleController.searchPackages(searchTerm, sortBy, sortOrder, page, size);
    }
    
    /**
     * Get package by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PackageDto.Package>> getPackageById(@PathVariable UUID id) {
        return lifecycleController.getPackageById(id);
    }
    
    /**
     * Create new package
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PackageDto.Package>> createPackage(
            @Valid @RequestBody PackageRequest.Create request) {
        return lifecycleController.createPackage(request);
    }
    
    /**
     * Update existing package
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PackageDto.Package>> updatePackage(
            @PathVariable UUID id,
            @Valid @RequestBody PackageRequest.Update request) {
        return lifecycleController.updatePackage(id, request);
    }
    
    /**
     * Delete package (hard delete)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deletePackage(@PathVariable UUID id) {
        return lifecycleController.deletePackage(id);
    }
    
    /**
     * Check package name availability
     */
    @GetMapping("/check-name/{name}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkNameAvailability(@PathVariable String name) {
        return lifecycleController.checkNameAvailability(name);
    }
    
    // ===== Container Operations (delegated to PackageContainerController) =====
    
    /**
     * Get complete package container with all assets
     */
    @GetMapping("/{id}/container")
    public ResponseEntity<ApiResponse<PackageDto.Container>> getPackageContainer(@PathVariable UUID id) {
        return containerController.getPackageContainer(id);
    }
    
    /**
     * Get package deployment readiness assessment
     */
    @GetMapping("/{id}/deployment-readiness")
    public ResponseEntity<ApiResponse<PackageDto.DeploymentReadiness>> getDeploymentReadiness(@PathVariable UUID id) {
        return containerController.getDeploymentReadiness(id);
    }
    
    /**
     * Validate package dependencies
     */
    @GetMapping("/{id}/validate-dependencies")
    public ResponseEntity<ApiResponse<PackageDto.DependencyValidation>> validateDependencies(@PathVariable UUID id) {
        return containerController.validateDependencies(id);
    }
    
    // ===== Asset Management Operations (delegated to PackageAssetController) =====
    
    /**
     * Get adapters in package
     */
    @GetMapping("/{id}/adapters")
    public ResponseEntity<ApiResponse<List<PackageDto.Adapter>>> getPackageAdapters(
            @PathVariable UUID id,
            @RequestParam(required = false) Boolean active) {
        return assetController.getPackageAdapters(id, active);
    }
    
    /**
     * Get flows in package
     */
    @GetMapping("/{id}/flows")
    public ResponseEntity<ApiResponse<List<PackageDto.Flow>>> getPackageFlows(
            @PathVariable UUID id,
            @RequestParam(required = false) Boolean active) {
        return assetController.getPackageFlows(id, active);
    }
    
    /**
     * Move asset between packages
     */
    @PostMapping("/{fromPackageId}/move-asset")
    public ResponseEntity<ApiResponse<String>> moveAsset(
            @PathVariable UUID fromPackageId,
            @RequestBody PackageRequest.MoveAsset request) {
        return assetController.moveAsset(fromPackageId, request);
    }
    
    /**
     * Search assets across all packages
     */
    @GetMapping("/search/assets")
    public ResponseEntity<ApiResponse<List<PackageDto.Asset>>> searchAssets(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) Set<String> assetTypes) {
        return assetController.searchAssets(searchTerm, assetTypes);
    }
    
    // ===== Analytics Operations (delegated to PackageAnalyticsController) =====
    
    /**
     * Get package summary with asset counts and statistics
     */
    @GetMapping("/{id}/summary")
    public ResponseEntity<ApiResponse<PackageDto.Summary>> getPackageSummary(@PathVariable UUID id) {
        return analyticsController.getPackageSummary(id);
    }
    
    /**
     * Get package statistics for dashboard
     */
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPackageStatistics() {
        return analyticsController.getPackageStatistics();
    }
}