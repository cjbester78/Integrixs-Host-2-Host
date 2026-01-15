package com.integrixs.backend.controller;

import com.integrixs.backend.dto.PackageDto;
import com.integrixs.core.service.AdapterManagementService;
import com.integrixs.core.service.FlowStatisticsService;
import com.integrixs.core.service.PackageMetadataService;
import com.integrixs.core.service.PackageContainerService;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.IntegrationFlow;
import com.integrixs.shared.model.IntegrationPackage;
import com.integrixs.shared.util.AuditUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Base controller providing common functionality for package controllers
 * Contains shared utility methods and converter functions
 */
public abstract class BasePackageController {
    
    protected static final Logger logger = LoggerFactory.getLogger(BasePackageController.class);
    
    /**
     * Get current user ID for audit purposes
     */
    protected UUID getCurrentUserId() {
        try {
            String currentUserId = AuditUtils.getCurrentUserId();
            return UUID.fromString(currentUserId);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid user ID format from audit context, using default admin ID");
            // Fallback to a default admin user ID - in production this should be handled differently
            return UUID.fromString("00000000-0000-0000-0000-000000000000");
        }
    }
    
    /**
     * Convert IntegrationPackage entity to DTO (without asset counts)
     */
    protected PackageDto.Package convertToPackageDto(IntegrationPackage pkg) {
        PackageDto.Package dto = new PackageDto.Package();
        dto.setId(pkg.getId());
        dto.setName(pkg.getName());
        dto.setDescription(pkg.getDescription());
        dto.setVersion(pkg.getVersion());
        dto.setStatus(pkg.getStatus());
        dto.setConfiguration(pkg.getConfiguration());
        dto.setCreatedAt(pkg.getCreatedAt());
        dto.setUpdatedAt(pkg.getUpdatedAt());
        dto.setCreatedBy(pkg.getCreatedBy());
        dto.setUpdatedBy(pkg.getUpdatedBy());

        // Initialize counts to 0 (will be populated if asset counts are available)
        dto.setAdapterCount(0);
        dto.setActiveAdapterCount(0);
        dto.setFlowCount(0);
        dto.setActiveFlowCount(0);
        dto.setTotalAssetCount(0);
        dto.setTotalActiveAssetCount(0);

        return dto;
    }

    /**
     * Convert IntegrationPackage entity to DTO with asset counts
     */
    protected PackageDto.Package convertToPackageDtoWithCounts(
            IntegrationPackage pkg,
            int adapterCount,
            int activeAdapterCount,
            int flowCount,
            int activeFlowCount) {

        PackageDto.Package dto = convertToPackageDto(pkg);
        dto.setAdapterCount(adapterCount);
        dto.setActiveAdapterCount(activeAdapterCount);
        dto.setFlowCount(flowCount);
        dto.setActiveFlowCount(activeFlowCount);
        dto.setTotalAssetCount(adapterCount + flowCount);
        dto.setTotalActiveAssetCount(activeAdapterCount + activeFlowCount);

        return dto;
    }
    
    /**
     * Convert PackageContainer to DTO
     */
    protected PackageDto.Container convertToPackageContainerDto(PackageContainerService.PackageContainer container) {
        PackageDto.Container dto = new PackageDto.Container();
        dto.setPackageInfo(convertToPackageDto(container.getPackageInfo()));
        dto.setAdapters(container.getAdapters().stream()
            .map(this::convertToAdapterDto)
            .collect(java.util.stream.Collectors.toList()));
        dto.setFlows(container.getFlows().stream()
            .map(this::convertToFlowDto)
            .collect(java.util.stream.Collectors.toList()));
        dto.setTotalAssetCount(container.getTotalAssetCount());
        dto.setHasAssets(container.hasAssets());
        return dto;
    }
    
    /**
     * Convert Adapter entity to DTO
     */
    protected PackageDto.Adapter convertToAdapterDto(Adapter adapter) {
        PackageDto.Adapter dto = new PackageDto.Adapter();
        dto.setId(adapter.getId());
        dto.setName(adapter.getName());
        dto.setDescription(adapter.getDescription());
        dto.setAdapterType(adapter.getAdapterType());
        dto.setDirection(adapter.getDirection());
        dto.setActive(adapter.getActive());
        dto.setConnectionValidated(adapter.getConnectionValidated());
        dto.setPackageId(adapter.getPackageId());
        dto.setCreatedAt(adapter.getCreatedAt());
        dto.setUpdatedAt(adapter.getUpdatedAt());
        return dto;
    }
    
    /**
     * Convert IntegrationFlow entity to DTO
     */
    protected PackageDto.Flow convertToFlowDto(IntegrationFlow flow) {
        PackageDto.Flow dto = new PackageDto.Flow();
        dto.setId(flow.getId());
        dto.setName(flow.getName());
        dto.setDescription(flow.getDescription());
        dto.setFlowType(flow.getFlowType());
        dto.setActive(flow.getActive());
        dto.setScheduleEnabled(flow.getScheduleEnabled());
        dto.setPackageId(flow.getPackageId());
        dto.setCreatedAt(flow.getCreatedAt());
        dto.setUpdatedAt(flow.getUpdatedAt());
        return dto;
    }
    
    /**
     * Convert PackageAsset to DTO
     */
    protected PackageDto.Asset convertToPackageAssetDto(PackageContainerService.PackageAsset asset) {
        PackageDto.Asset dto = new PackageDto.Asset();
        dto.setAssetType(asset.getAssetType().name());
        dto.setAssetId(asset.getAssetId());
        dto.setName(asset.getName());
        dto.setPackageId(asset.getPackageId());
        dto.setActive(asset.getActive());
        dto.setCreatedAt(asset.getCreatedAt());
        dto.setUpdatedAt(asset.getUpdatedAt());
        return dto;
    }
    
    /**
     * Convert deployment readiness to DTO
     */
    protected PackageDto.DeploymentReadiness convertToDeploymentReadinessDto(
            PackageContainerService.PackageDeploymentReadiness readiness) {
        PackageDto.DeploymentReadiness dto = new PackageDto.DeploymentReadiness();
        dto.setPackageId(readiness.getPackageInfo().getId());
        dto.setPackageName(readiness.getPackageInfo().getName());
        dto.setReady(readiness.isReady());
        dto.setIssues(readiness.getIssues());
        dto.setReadinessMetrics(readiness.getReadinessMetrics());
        return dto;
    }
    
    /**
     * Convert dependency validation to DTO
     */
    protected PackageDto.DependencyValidation convertToDependencyValidationDto(
            PackageContainerService.PackageDependencyValidation validation) {
        PackageDto.DependencyValidation dto = new PackageDto.DependencyValidation();
        dto.setPackageId(validation.getPackageId());
        dto.setValid(validation.isValid());
        dto.setHasCircularDependencies(validation.hasCircularDependencies());
        dto.setCircularDependencyCount(validation.getCircularDependencies().size());
        return dto;
    }
    
    /**
     * Create package summary DTO from multiple service responses
     */
    protected PackageDto.Summary createPackageSummary(
            PackageMetadataService.PackageSummary packageSummary,
            AdapterManagementService.PackageAdapterSummary adapterSummary,
            FlowStatisticsService.PackageFlowSummary flowSummary) {
        
        PackageDto.Summary dto = new PackageDto.Summary();
        dto.setPackageId(packageSummary.getPackageInfo().getId());
        dto.setPackageName(packageSummary.getPackageInfo().getName());
        dto.setTotalAdapters(adapterSummary.getTotalAdapters());
        dto.setActiveAdapters(adapterSummary.getActiveAdapters());
        dto.setTotalFlows(flowSummary.getTotalFlows());
        dto.setActiveFlows(flowSummary.getActiveFlows());
        dto.setScheduledFlows(flowSummary.getScheduledFlows());
        dto.setDeployedFlows(flowSummary.getDeployedFlows());
        dto.setDependencies(packageSummary.getDependencyCount());
        dto.setAdapterSuccessRate(adapterSummary.getValidatedPercentage());
        dto.setFlowSuccessRate(flowSummary.getSuccessRate());
        
        return dto;
    }
}