package com.integrixs.backend.controller;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.backend.dto.PackageDto;
import com.integrixs.core.service.AdapterManagementService;
import com.integrixs.core.service.FlowStatisticsService;
import com.integrixs.core.service.PackageMetadataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Package Analytics and Reporting Service
 * Handles package statistics, summaries, and reporting operations
 * 
 * This service was extracted from the monolithic PackageController (1,059 lines)
 * to follow Single Responsibility Principle and improve maintainability.
 * Called through PackageController facade, not directly as REST endpoints.
 */
@Component
public class PackageAnalyticsController extends BasePackageController {
    
    private final PackageMetadataService packageService;
    private final AdapterManagementService adapterService;
    private final FlowStatisticsService flowStatisticsService;
    
    @Autowired
    public PackageAnalyticsController(
            PackageMetadataService packageService,
            AdapterManagementService adapterService,
            FlowStatisticsService flowStatisticsService) {
        this.packageService = packageService;
        this.adapterService = adapterService;
        this.flowStatisticsService = flowStatisticsService;
    }
    
    /**
     * Get package summary with asset counts and statistics
     * GET /api/packages/{id}/summary
     */
    @GetMapping("/{id}/summary")
    public ResponseEntity<ApiResponse<PackageDto.Summary>> getPackageSummary(@PathVariable UUID id) {
        try {
            logger.debug("Retrieving package summary: {}", id);
            
            PackageMetadataService.PackageSummary summary = packageService.getPackageSummary(id);
            AdapterManagementService.PackageAdapterSummary adapterSummary = 
                adapterService.getPackageAdapterSummary(id);
            FlowStatisticsService.PackageFlowSummary flowSummary = 
                flowStatisticsService.getPackageFlowSummary(id);
            
            PackageDto.Summary summaryDto = createPackageSummary(summary, adapterSummary, flowSummary);
            
            return ResponseEntity.ok(ApiResponse.success(summaryDto));
            
        } catch (IllegalStateException e) {
            logger.warn("Package not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Package not found: " + id));
        } catch (Exception e) {
            logger.error("Failed to retrieve package summary: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to retrieve package summary: " + e.getMessage()));
        }
    }
    
    /**
     * Get package statistics for dashboard
     * GET /api/packages/statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPackageStatistics() {
        try {
            logger.debug("Retrieving package statistics");
            
            Map<String, Object> stats = packageService.getPackageStatistics();
            
            return ResponseEntity.ok(ApiResponse.success(stats));
            
        } catch (Exception e) {
            logger.error("Failed to retrieve package statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to retrieve package statistics: " + e.getMessage()));
        }
    }
}