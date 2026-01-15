package com.integrixs.backend.controller;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.backend.dto.PackageDto;
import com.integrixs.backend.dto.PackageRequest;
import com.integrixs.core.service.AdapterManagementService;
import com.integrixs.core.service.FlowDefinitionService;
import com.integrixs.core.service.FlowDeploymentService;
import com.integrixs.core.service.PackageContainerService;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.IntegrationFlow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Package Asset Management Service
 * Handles adapter and flow assets within packages, asset movement, and search
 * 
 * This service was extracted from the monolithic PackageController (1,059 lines)
 * to follow Single Responsibility Principle and improve maintainability.
 * Called through PackageController facade, not directly as REST endpoints.
 */
@Component
public class PackageAssetController extends BasePackageController {

    private final AdapterManagementService adapterService;
    private final FlowDefinitionService flowService;
    private final FlowDeploymentService flowDeploymentService;
    private final PackageContainerService containerService;

    @Autowired
    public PackageAssetController(
            AdapterManagementService adapterService,
            FlowDefinitionService flowService,
            FlowDeploymentService flowDeploymentService,
            PackageContainerService containerService) {
        this.adapterService = adapterService;
        this.flowService = flowService;
        this.flowDeploymentService = flowDeploymentService;
        this.containerService = containerService;
    }
    
    /**
     * Get adapters in package
     * GET /api/packages/{id}/adapters
     */
    @GetMapping("/{id}/adapters")
    public ResponseEntity<ApiResponse<List<PackageDto.Adapter>>> getPackageAdapters(
            @PathVariable UUID id,
            @RequestParam(required = false) Boolean active) {
        try {
            logger.debug("Retrieving adapters for package: {} (active: {})", id, active);
            
            List<Adapter> adapters;
            if (active != null && active) {
                adapters = adapterService.getActiveAdaptersByPackageId(id);
            } else {
                adapters = adapterService.getAdaptersByPackageId(id);
            }
            
            List<PackageDto.Adapter> adapterDtos = adapters.stream()
                .map(this::convertToAdapterDto)
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(ApiResponse.success(adapterDtos));
            
        } catch (Exception e) {
            logger.error("Failed to retrieve package adapters: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to retrieve package adapters: " + e.getMessage()));
        }
    }
    
    /**
     * Get flows in package
     * GET /api/packages/{id}/flows
     */
    @GetMapping("/{id}/flows")
    public ResponseEntity<ApiResponse<List<PackageDto.Flow>>> getPackageFlows(
            @PathVariable UUID id,
            @RequestParam(required = false) Boolean active) {
        try {
            logger.debug("Retrieving flows for package: {} (active: {})", id, active);
            
            List<IntegrationFlow> flows;
            if (active != null && active) {
                flows = flowService.getActiveFlowsByPackage(id);
            } else {
                flows = flowService.getFlowsByPackageId(id);
            }

            List<PackageDto.Flow> flowDtos = flows.stream()
                .map(flow -> {
                    PackageDto.Flow dto = convertToFlowDto(flow);
                    // Add deployment status
                    try {
                        Map<String, Object> deploymentStatus = flowDeploymentService.getDeploymentStatus(flow.getId());
                        dto.setDeployed((Boolean) deploymentStatus.get("isDeployed"));
                    } catch (Exception e) {
                        logger.warn("Could not get deployment status for flow {}: {}", flow.getId(), e.getMessage());
                        dto.setDeployed(false);
                    }
                    return dto;
                })
                .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(flowDtos));
            
        } catch (Exception e) {
            logger.error("Failed to retrieve package flows: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to retrieve package flows: " + e.getMessage()));
        }
    }
    
    /**
     * Move asset between packages
     * POST /api/packages/{fromPackageId}/move-asset
     */
    @PostMapping("/{fromPackageId}/move-asset")
    public ResponseEntity<ApiResponse<String>> moveAsset(
            @PathVariable UUID fromPackageId,
            @RequestBody PackageRequest.MoveAsset request) {
        try {
            logger.info("Moving asset {} from package {} to package {}", 
                request.getAssetId(), fromPackageId, request.getToPackageId());
            
            UUID currentUserId = getCurrentUserId();
            
            PackageContainerService.AssetType assetType = 
                PackageContainerService.AssetType.valueOf(request.getAssetType().toUpperCase());
            
            boolean moved = containerService.moveAssetBetweenPackages(
                assetType, 
                request.getAssetId(), 
                fromPackageId, 
                request.getToPackageId(), 
                currentUserId
            );
            
            if (moved) {
                return ResponseEntity.ok(ApiResponse.success("Asset moved successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Failed to move asset"));
            }
            
        } catch (IllegalArgumentException e) {
            logger.warn("Asset move failed - validation error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Asset move failed: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to move asset", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to move asset: " + e.getMessage()));
        }
    }
    
    /**
     * Search assets across all packages
     * GET /api/packages/search/assets
     */
    @GetMapping("/search/assets")
    public ResponseEntity<ApiResponse<List<PackageDto.Asset>>> searchAssets(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) Set<String> assetTypes) {
        try {
            logger.debug("Searching assets - term: {}, types: {}", searchTerm, assetTypes);
            
            Set<PackageContainerService.AssetType> typeFilter = null;
            if (assetTypes != null && !assetTypes.isEmpty()) {
                typeFilter = assetTypes.stream()
                    .map(type -> PackageContainerService.AssetType.valueOf(type.toUpperCase()))
                    .collect(Collectors.toSet());
            }
            
            List<PackageContainerService.PackageAsset> assets = 
                containerService.searchAssetsAcrossPackages(searchTerm, typeFilter);
            
            List<PackageDto.Asset> assetDtos = assets.stream()
                .map(this::convertToPackageAssetDto)
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(ApiResponse.success(assetDtos));
            
        } catch (Exception e) {
            logger.error("Failed to search assets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to search assets: " + e.getMessage()));
        }
    }
}