package com.integrixs.core.service;

import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.shared.model.Adapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for adapter analytics, statistics, and reporting
 * Handles performance metrics and analytics following Single Responsibility Principle
 */
@Service
public class AdapterAnalyticsService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdapterAnalyticsService.class);
    
    private final AdapterRepository adapterRepository;
    
    @Autowired
    public AdapterAnalyticsService(AdapterRepository adapterRepository) {
        this.adapterRepository = adapterRepository;
    }
    
    /**
     * Get comprehensive adapter statistics
     */
    public Map<String, Object> getAdapterStatistics() {
        logger.debug("Retrieving comprehensive adapter statistics");
        return adapterRepository.getAdapterStatistics();
    }
    
    /**
     * Get adapter performance summary
     */
    public AdapterPerformanceSummary getAdapterPerformanceSummary() {
        logger.debug("Calculating adapter performance summary");
        
        List<Adapter> allAdapters = adapterRepository.findAll();
        
        if (allAdapters.isEmpty()) {
            return new AdapterPerformanceSummary(0, 0, 0.0, 0.0, 0, 0);
        }
        
        int totalAdapters = allAdapters.size();
        int activeAdapters = (int) allAdapters.stream().filter(Adapter::getActive).count();
        
        // Calculate average execution time (excluding null/zero values)
        double avgExecutionTime = allAdapters.stream()
            .filter(a -> a.getAverageExecutionTimeMs() != null && a.getAverageExecutionTimeMs() > 0)
            .mapToLong(Adapter::getAverageExecutionTimeMs)
            .average()
            .orElse(0.0);
        
        // Calculate average success rate (excluding null values)
        double avgSuccessRate = allAdapters.stream()
            .filter(a -> a.getSuccessRatePercent() != null)
            .mapToDouble(a -> a.getSuccessRatePercent().doubleValue())
            .average()
            .orElse(0.0);
        
        // Count validated adapters
        int validatedAdapters = (int) allAdapters.stream()
            .filter(a -> a.getConnectionValidated() != null && a.getConnectionValidated())
            .count();
        
        // Count running adapters
        int runningAdapters = (int) allAdapters.stream()
            .filter(a -> a.getStatus() == Adapter.AdapterStatus.STARTED)
            .count();
        
        return new AdapterPerformanceSummary(
            totalAdapters,
            activeAdapters,
            avgExecutionTime,
            avgSuccessRate,
            validatedAdapters,
            runningAdapters
        );
    }
    
    /**
     * Get adapters grouped by type with statistics
     */
    public Map<String, AdapterTypeStats> getAdapterStatsByType() {
        logger.debug("Calculating adapter statistics by type");
        
        List<Adapter> allAdapters = adapterRepository.findAll();
        
        return allAdapters.stream()
            .collect(Collectors.groupingBy(Adapter::getAdapterType))
            .entrySet()
            .stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> calculateStatsForAdapterList(entry.getValue())
            ));
    }
    
    /**
     * Get adapters grouped by direction with statistics
     */
    public Map<String, AdapterTypeStats> getAdapterStatsByDirection() {
        logger.debug("Calculating adapter statistics by direction");
        
        List<Adapter> allAdapters = adapterRepository.findAll();
        
        return allAdapters.stream()
            .collect(Collectors.groupingBy(Adapter::getDirection))
            .entrySet()
            .stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> calculateStatsForAdapterList(entry.getValue())
            ));
    }
    
    /**
     * Get top performing adapters by success rate
     */
    public List<AdapterPerformanceMetric> getTopPerformingAdapters(int limit) {
        logger.debug("Retrieving top {} performing adapters by success rate", limit);
        
        List<Adapter> allAdapters = adapterRepository.findAll();
        
        return allAdapters.stream()
            .filter(a -> a.getSuccessRatePercent() != null)
            .sorted((a1, a2) -> a2.getSuccessRatePercent().compareTo(a1.getSuccessRatePercent()))
            .limit(limit)
            .map(this::createPerformanceMetric)
            .collect(Collectors.toList());
    }
    
    /**
     * Get worst performing adapters by success rate
     */
    public List<AdapterPerformanceMetric> getWorstPerformingAdapters(int limit) {
        logger.debug("Retrieving worst {} performing adapters by success rate", limit);
        
        List<Adapter> allAdapters = adapterRepository.findAll();
        
        return allAdapters.stream()
            .filter(a -> a.getSuccessRatePercent() != null)
            .sorted(Comparator.comparing(Adapter::getSuccessRatePercent))
            .limit(limit)
            .map(this::createPerformanceMetric)
            .collect(Collectors.toList());
    }
    
    /**
     * Get fastest adapters by execution time
     */
    public List<AdapterPerformanceMetric> getFastestAdapters(int limit) {
        logger.debug("Retrieving {} fastest adapters by execution time", limit);
        
        List<Adapter> allAdapters = adapterRepository.findAll();
        
        return allAdapters.stream()
            .filter(a -> a.getAverageExecutionTimeMs() != null && a.getAverageExecutionTimeMs() > 0)
            .sorted(Comparator.comparing(Adapter::getAverageExecutionTimeMs))
            .limit(limit)
            .map(this::createPerformanceMetric)
            .collect(Collectors.toList());
    }
    
    /**
     * Get slowest adapters by execution time
     */
    public List<AdapterPerformanceMetric> getSlowestAdapters(int limit) {
        logger.debug("Retrieving {} slowest adapters by execution time", limit);
        
        List<Adapter> allAdapters = adapterRepository.findAll();
        
        return allAdapters.stream()
            .filter(a -> a.getAverageExecutionTimeMs() != null && a.getAverageExecutionTimeMs() > 0)
            .sorted((a1, a2) -> a2.getAverageExecutionTimeMs().compareTo(a1.getAverageExecutionTimeMs()))
            .limit(limit)
            .map(this::createPerformanceMetric)
            .collect(Collectors.toList());
    }
    
    /**
     * Update adapter performance metrics
     */
    public void updateAdapterPerformanceMetrics(UUID adapterId, long executionTimeMs, BigDecimal successRate) {
        logger.info("Updating performance metrics for adapter: {} - execution time: {}ms, success rate: {}%", 
            adapterId, executionTimeMs, successRate);
        
        adapterRepository.updatePerformanceMetrics(adapterId, executionTimeMs, successRate);
    }
    
    /**
     * Get adapters that need attention (low success rate or not validated)
     */
    public List<AdapterHealthStatus> getAdaptersNeedingAttention() {
        logger.debug("Identifying adapters that need attention");
        
        List<Adapter> allAdapters = adapterRepository.findAll();
        List<AdapterHealthStatus> needingAttention = new ArrayList<>();
        
        for (Adapter adapter : allAdapters) {
            List<String> issues = new ArrayList<>();
            
            // Check if not validated
            if (adapter.getConnectionValidated() == null || !adapter.getConnectionValidated()) {
                issues.add("Connection not validated");
            }
            
            // Check low success rate
            if (adapter.getSuccessRatePercent() != null && 
                adapter.getSuccessRatePercent().compareTo(BigDecimal.valueOf(95.0)) < 0) {
                issues.add("Low success rate: " + adapter.getSuccessRatePercent() + "%");
            }
            
            // Check if adapter is active but not running
            if (adapter.getActive() && adapter.getStatus() != Adapter.AdapterStatus.STARTED) {
                issues.add("Active but not running (status: " + adapter.getStatus() + ")");
            }
            
            if (!issues.isEmpty()) {
                needingAttention.add(new AdapterHealthStatus(
                    adapter.getId(),
                    adapter.getName(),
                    adapter.getAdapterType(),
                    adapter.getDirection(),
                    issues
                ));
            }
        }
        
        return needingAttention;
    }
    
    /**
     * Calculate statistics for a list of adapters
     */
    private AdapterTypeStats calculateStatsForAdapterList(List<Adapter> adapters) {
        if (adapters.isEmpty()) {
            return new AdapterTypeStats(0, 0, 0.0, 0.0, 0);
        }
        
        int total = adapters.size();
        int active = (int) adapters.stream().filter(Adapter::getActive).count();
        
        double avgExecutionTime = adapters.stream()
            .filter(a -> a.getAverageExecutionTimeMs() != null && a.getAverageExecutionTimeMs() > 0)
            .mapToLong(Adapter::getAverageExecutionTimeMs)
            .average()
            .orElse(0.0);
        
        double avgSuccessRate = adapters.stream()
            .filter(a -> a.getSuccessRatePercent() != null)
            .mapToDouble(a -> a.getSuccessRatePercent().doubleValue())
            .average()
            .orElse(0.0);
        
        int validated = (int) adapters.stream()
            .filter(a -> a.getConnectionValidated() != null && a.getConnectionValidated())
            .count();
        
        return new AdapterTypeStats(total, active, avgExecutionTime, avgSuccessRate, validated);
    }
    
    /**
     * Create performance metric from adapter
     */
    private AdapterPerformanceMetric createPerformanceMetric(Adapter adapter) {
        return new AdapterPerformanceMetric(
            adapter.getId(),
            adapter.getName(),
            adapter.getAdapterType(),
            adapter.getDirection(),
            adapter.getAverageExecutionTimeMs(),
            adapter.getSuccessRatePercent()
        );
    }
    
    /**
     * Data transfer object for adapter performance summary
     */
    public static class AdapterPerformanceSummary {
        public final int totalAdapters;
        public final int activeAdapters;
        public final double averageExecutionTimeMs;
        public final double averageSuccessRatePercent;
        public final int validatedAdapters;
        public final int runningAdapters;
        
        public AdapterPerformanceSummary(int totalAdapters, int activeAdapters, 
                                       double averageExecutionTimeMs, double averageSuccessRatePercent,
                                       int validatedAdapters, int runningAdapters) {
            this.totalAdapters = totalAdapters;
            this.activeAdapters = activeAdapters;
            this.averageExecutionTimeMs = averageExecutionTimeMs;
            this.averageSuccessRatePercent = averageSuccessRatePercent;
            this.validatedAdapters = validatedAdapters;
            this.runningAdapters = runningAdapters;
        }
    }
    
    /**
     * Data transfer object for adapter type statistics
     */
    public static class AdapterTypeStats {
        public final int totalCount;
        public final int activeCount;
        public final double averageExecutionTimeMs;
        public final double averageSuccessRatePercent;
        public final int validatedCount;
        
        public AdapterTypeStats(int totalCount, int activeCount, double averageExecutionTimeMs,
                              double averageSuccessRatePercent, int validatedCount) {
            this.totalCount = totalCount;
            this.activeCount = activeCount;
            this.averageExecutionTimeMs = averageExecutionTimeMs;
            this.averageSuccessRatePercent = averageSuccessRatePercent;
            this.validatedCount = validatedCount;
        }
    }
    
    /**
     * Data transfer object for adapter performance metrics
     */
    public static class AdapterPerformanceMetric {
        public final UUID adapterId;
        public final String adapterName;
        public final String adapterType;
        public final String direction;
        public final Long executionTimeMs;
        public final BigDecimal successRatePercent;
        
        public AdapterPerformanceMetric(UUID adapterId, String adapterName, String adapterType,
                                      String direction, Long executionTimeMs, BigDecimal successRatePercent) {
            this.adapterId = adapterId;
            this.adapterName = adapterName;
            this.adapterType = adapterType;
            this.direction = direction;
            this.executionTimeMs = executionTimeMs;
            this.successRatePercent = successRatePercent;
        }
    }
    
    /**
     * Data transfer object for adapter health status
     */
    public static class AdapterHealthStatus {
        public final UUID adapterId;
        public final String adapterName;
        public final String adapterType;
        public final String direction;
        public final List<String> issues;
        
        public AdapterHealthStatus(UUID adapterId, String adapterName, String adapterType,
                                 String direction, List<String> issues) {
            this.adapterId = adapterId;
            this.adapterName = adapterName;
            this.adapterType = adapterType;
            this.direction = direction;
            this.issues = issues;
        }
    }
}