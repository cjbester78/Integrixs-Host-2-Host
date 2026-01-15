package com.integrixs.backend.controller;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.backend.dto.PackageDto;
import com.integrixs.core.service.PackageContainerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Package Container Management Service
 * Handles package containerization, deployment readiness, and dependency validation
 * 
 * This service was extracted from the monolithic PackageController (1,059 lines)
 * to follow Single Responsibility Principle and improve maintainability.
 * Called through PackageController facade, not directly as REST endpoints.
 */
@Component
public class PackageContainerController extends BasePackageController {
    
    private final PackageContainerService containerService;
    
    @Autowired
    public PackageContainerController(PackageContainerService containerService) {
        this.containerService = containerService;
    }
    
    /**
     * Get complete package container with all assets
     * GET /api/packages/{id}/container
     */
    @GetMapping("/{id}/container")
    public ResponseEntity<ApiResponse<PackageDto.Container>> getPackageContainer(@PathVariable UUID id) {
        try {
            logger.debug("Retrieving package container: {}", id);
            
            PackageContainerService.PackageContainer container = containerService.getPackageContainer(id);
            PackageDto.Container containerDto = convertToPackageContainerDto(container);
            
            return ResponseEntity.ok(ApiResponse.success(containerDto));
            
        } catch (IllegalStateException e) {
            logger.warn("Package not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Package not found: " + id));
        } catch (Exception e) {
            logger.error("Failed to retrieve package container: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to retrieve package container: " + e.getMessage()));
        }
    }
    
    /**
     * Get package deployment readiness assessment
     * GET /api/packages/{id}/deployment-readiness
     */
    @GetMapping("/{id}/deployment-readiness")
    public ResponseEntity<ApiResponse<PackageDto.DeploymentReadiness>> getDeploymentReadiness(@PathVariable UUID id) {
        try {
            logger.debug("Assessing deployment readiness for package: {}", id);
            
            PackageContainerService.PackageDeploymentReadiness readiness = 
                containerService.getDeploymentReadiness(id);
            PackageDto.DeploymentReadiness readinessDto = convertToDeploymentReadinessDto(readiness);
            
            return ResponseEntity.ok(ApiResponse.success(readinessDto));
            
        } catch (IllegalStateException e) {
            logger.warn("Package not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Package not found: " + id));
        } catch (Exception e) {
            logger.error("Failed to assess deployment readiness: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to assess deployment readiness: " + e.getMessage()));
        }
    }
    
    /**
     * Validate package dependencies
     * GET /api/packages/{id}/validate-dependencies
     */
    @GetMapping("/{id}/validate-dependencies")
    public ResponseEntity<ApiResponse<PackageDto.DependencyValidation>> validateDependencies(@PathVariable UUID id) {
        try {
            logger.debug("Validating dependencies for package: {}", id);
            
            PackageContainerService.PackageDependencyValidation validation = 
                containerService.validatePackageDependencies(id);
            PackageDto.DependencyValidation validationDto = convertToDependencyValidationDto(validation);
            
            return ResponseEntity.ok(ApiResponse.success(validationDto));
            
        } catch (IllegalStateException e) {
            logger.warn("Package not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Package not found: " + id));
        } catch (Exception e) {
            logger.error("Failed to validate dependencies: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to validate dependencies: " + e.getMessage()));
        }
    }
}