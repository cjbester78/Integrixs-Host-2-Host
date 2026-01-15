package com.integrixs.core.service;

import com.integrixs.core.repository.FlowExecutionRepository;
import com.integrixs.core.repository.FlowExecutionStepRepository;
import com.integrixs.shared.model.FlowExecution;
import com.integrixs.shared.model.FlowExecutionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for monitoring flow execution performance and metrics
 * Handles performance tracking, statistics, and monitoring following Single Responsibility Principle
 */
@Service
public class FlowExecutionMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowExecutionMonitor.class);
    
    private final FlowExecutionRepository executionRepository;
    private final FlowExecutionStepRepository stepRepository;
    private final FlowWebSocketService webSocketService;
    
    @Autowired
    public FlowExecutionMonitor(FlowExecutionRepository executionRepository,
                               FlowExecutionStepRepository stepRepository,
                               FlowWebSocketService webSocketService) {
        this.executionRepository = executionRepository;
        this.stepRepository = stepRepository;
        this.webSocketService = webSocketService;
    }
    
    /**
     * Get comprehensive execution statistics
     */
    public Map<String, Object> getExecutionStatistics() {
        logger.debug("Retrieving comprehensive execution statistics");
        
        List<FlowExecution> allExecutions = executionRepository.findAll();
        
        Map<String, Object> stats = new HashMap<>();
        
        // Basic counts
        stats.put("totalExecutions", allExecutions.size());
        stats.put("runningExecutions", countExecutionsByStatus(allExecutions, FlowExecution.ExecutionStatus.RUNNING));
        stats.put("completedExecutions", countExecutionsByStatus(allExecutions, FlowExecution.ExecutionStatus.COMPLETED));
        stats.put("failedExecutions", countExecutionsByStatus(allExecutions, FlowExecution.ExecutionStatus.FAILED));
        stats.put("cancelledExecutions", countExecutionsByStatus(allExecutions, FlowExecution.ExecutionStatus.CANCELLED));
        stats.put("scheduledRetryExecutions", countExecutionsByStatus(allExecutions, FlowExecution.ExecutionStatus.RETRY_PENDING));
        
        // Success rate
        long successful = (Long) stats.get("completedExecutions");
        long total = allExecutions.size();
        double successRate = total > 0 ? (double) successful / total * 100 : 0;
        stats.put("successRate", Math.round(successRate * 100.0) / 100.0);
        
        // Performance metrics
        Map<String, Object> performanceMetrics = calculatePerformanceMetrics(allExecutions);
        stats.putAll(performanceMetrics);
        
        // Recent activity
        List<FlowExecution> recentExecutions = getRecentExecutions(24); // Last 24 hours
        stats.put("recentExecutions", recentExecutions.size());
        stats.put("recentSuccessRate", calculateSuccessRate(recentExecutions));
        
        // Trigger type distribution
        Map<String, Long> triggerTypes = allExecutions.stream()
            .collect(Collectors.groupingBy(
                execution -> execution.getTriggerType().name(),
                Collectors.counting()
            ));
        stats.put("triggerTypeDistribution", triggerTypes);
        
        // Flow distribution (top flows by execution count)
        Map<String, Long> flowDistribution = allExecutions.stream()
            .collect(Collectors.groupingBy(
                FlowExecution::getFlowName,
                Collectors.counting()
            ));
        stats.put("flowDistribution", flowDistribution);
        
        return stats;
    }
    
    /**
     * Get execution statistics for a specific flow
     */
    public Map<String, Object> getFlowExecutionStatistics(UUID flowId) {
        Objects.requireNonNull(flowId, "Flow ID cannot be null");
        
        logger.debug("Retrieving execution statistics for flow: {}", flowId);
        
        List<FlowExecution> flowExecutions = executionRepository.findByFlowId(flowId);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("flowId", flowId);
        stats.put("totalExecutions", flowExecutions.size());
        
        if (flowExecutions.isEmpty()) {
            return stats;
        }
        
        // Status distribution
        stats.put("completedExecutions", countExecutionsByStatus(flowExecutions, FlowExecution.ExecutionStatus.COMPLETED));
        stats.put("failedExecutions", countExecutionsByStatus(flowExecutions, FlowExecution.ExecutionStatus.FAILED));
        stats.put("runningExecutions", countExecutionsByStatus(flowExecutions, FlowExecution.ExecutionStatus.RUNNING));
        stats.put("cancelledExecutions", countExecutionsByStatus(flowExecutions, FlowExecution.ExecutionStatus.CANCELLED));
        
        // Success rate
        double successRate = calculateSuccessRate(flowExecutions);
        stats.put("successRate", successRate);
        
        // Performance metrics
        Map<String, Object> performanceMetrics = calculatePerformanceMetrics(flowExecutions);
        stats.putAll(performanceMetrics);
        
        // Recent activity
        List<FlowExecution> recentExecutions = flowExecutions.stream()
            .filter(exec -> exec.getStartedAt() != null && 
                           exec.getStartedAt().isAfter(LocalDateTime.now().minus(24, ChronoUnit.HOURS)))
            .collect(Collectors.toList());
        
        stats.put("recentExecutions", recentExecutions.size());
        stats.put("recentSuccessRate", calculateSuccessRate(recentExecutions));
        
        // Last execution info
        Optional<FlowExecution> lastExecution = flowExecutions.stream()
            .filter(exec -> exec.getStartedAt() != null)
            .max(Comparator.comparing(FlowExecution::getStartedAt));
        
        if (lastExecution.isPresent()) {
            FlowExecution lastExec = lastExecution.get();
            Map<String, Object> lastExecutionInfo = new HashMap<>();
            lastExecutionInfo.put("executionId", lastExec.getId());
            lastExecutionInfo.put("status", lastExec.getExecutionStatus().name());
            lastExecutionInfo.put("startedAt", lastExec.getStartedAt());
            lastExecutionInfo.put("completedAt", lastExec.getCompletedAt());
            lastExecutionInfo.put("triggeredBy", lastExec.getTriggeredBy());
            lastExecutionInfo.put("triggerType", lastExec.getTriggerType().name());
            stats.put("lastExecution", lastExecutionInfo);
        }
        
        return stats;
    }
    
    /**
     * Get real-time execution metrics for monitoring dashboard
     */
    public Map<String, Object> getRealTimeMetrics() {
        logger.debug("Retrieving real-time execution metrics");
        
        Map<String, Object> metrics = new HashMap<>();
        
        // Running executions
        List<FlowExecution> runningExecutions = executionRepository.findByStatus(FlowExecution.ExecutionStatus.RUNNING);
        metrics.put("currentlyRunning", runningExecutions.size());
        
        // Failed executions needing attention
        List<FlowExecution> failedExecutions = executionRepository.findByStatus(FlowExecution.ExecutionStatus.FAILED);
        long recentFailures = failedExecutions.stream()
            .filter(exec -> exec.getCompletedAt() != null && 
                           exec.getCompletedAt().isAfter(LocalDateTime.now().minus(1, ChronoUnit.HOURS)))
            .count();
        metrics.put("recentFailures", recentFailures);
        
        // Scheduled retries
        List<FlowExecution> scheduledRetries = executionRepository.findByStatus(FlowExecution.ExecutionStatus.RETRY_PENDING);
        metrics.put("scheduledRetries", scheduledRetries.size());
        
        // Average execution time (last hour)
        List<FlowExecution> recentCompleted = getRecentCompletedExecutions(1);
        double avgExecutionTime = calculateAverageExecutionTime(recentCompleted);
        metrics.put("averageExecutionTimeMinutes", Math.round(avgExecutionTime * 100.0) / 100.0);
        
        // Throughput (executions per hour)
        List<FlowExecution> lastHourExecutions = getRecentExecutions(1);
        metrics.put("executionsPerHour", lastHourExecutions.size());
        
        // System health indicators
        double systemHealth = calculateSystemHealth();
        metrics.put("systemHealth", Math.round(systemHealth * 100.0) / 100.0);
        
        // Active flow count
        Set<UUID> activeFlows = runningExecutions.stream()
            .map(FlowExecution::getFlowId)
            .collect(Collectors.toSet());
        metrics.put("activeFlowCount", activeFlows.size());
        
        return metrics;
    }
    
    /**
     * Monitor long-running executions
     */
    public List<FlowExecution> getLongRunningExecutions(int thresholdMinutes) {
        logger.debug("Finding long-running executions (threshold: {} minutes)", thresholdMinutes);
        
        List<FlowExecution> runningExecutions = executionRepository.findByStatus(FlowExecution.ExecutionStatus.RUNNING);
        LocalDateTime threshold = LocalDateTime.now().minus(thresholdMinutes, ChronoUnit.MINUTES);
        
        return runningExecutions.stream()
            .filter(exec -> exec.getStartedAt() != null && exec.getStartedAt().isBefore(threshold))
            .collect(Collectors.toList());
    }
    
    /**
     * Get performance trend data for analytics
     */
    public Map<String, Object> getPerformanceTrends(int days) {
        logger.debug("Retrieving performance trends for last {} days", days);
        
        LocalDateTime startDate = LocalDateTime.now().minus(days, ChronoUnit.DAYS);
        List<FlowExecution> executions = executionRepository.findAll().stream()
            .filter(exec -> exec.getStartedAt() != null && exec.getStartedAt().isAfter(startDate))
            .collect(Collectors.toList());
        
        Map<String, Object> trends = new HashMap<>();
        
        // Group by day
        Map<LocalDateTime, List<FlowExecution>> executionsByDay = executions.stream()
            .filter(exec -> exec.getStartedAt() != null)
            .collect(Collectors.groupingBy(exec -> exec.getStartedAt().toLocalDate().atStartOfDay()));
        
        // Calculate daily metrics
        List<Map<String, Object>> dailyMetrics = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDateTime day = LocalDateTime.now().minus(i, ChronoUnit.DAYS).toLocalDate().atStartOfDay();
            List<FlowExecution> dayExecutions = executionsByDay.getOrDefault(day, Collections.emptyList());
            
            Map<String, Object> dayMetrics = new HashMap<>();
            dayMetrics.put("date", day.toLocalDate());
            dayMetrics.put("totalExecutions", dayExecutions.size());
            dayMetrics.put("successfulExecutions", 
                countExecutionsByStatus(dayExecutions, FlowExecution.ExecutionStatus.COMPLETED));
            dayMetrics.put("failedExecutions", 
                countExecutionsByStatus(dayExecutions, FlowExecution.ExecutionStatus.FAILED));
            dayMetrics.put("successRate", calculateSuccessRate(dayExecutions));
            dayMetrics.put("averageExecutionTime", calculateAverageExecutionTime(dayExecutions));
            
            dailyMetrics.add(dayMetrics);
        }
        
        trends.put("dailyMetrics", dailyMetrics);
        trends.put("overallTrend", calculateOverallTrend(dailyMetrics));
        
        return trends;
    }
    
    /**
     * Send real-time update via WebSocket
     */
    public void sendRealTimeUpdate(FlowExecution execution, String eventType) {
        try {
            Map<String, Object> update = new HashMap<>();
            update.put("eventType", eventType);
            update.put("executionId", execution.getId());
            update.put("flowId", execution.getFlowId());
            update.put("flowName", execution.getFlowName());
            update.put("status", execution.getExecutionStatus().name());
            update.put("triggerType", execution.getTriggerType().name());
            update.put("startedAt", execution.getStartedAt());
            update.put("completedAt", execution.getCompletedAt());
            update.put("timestamp", LocalDateTime.now());
            
            if (execution.getExecutionStatus() == FlowExecution.ExecutionStatus.FAILED) {
                update.put("errorMessage", execution.getErrorMessage());
            }
            
            webSocketService.sendFlowExecutionUpdate(execution);
            
        } catch (Exception e) {
            logger.warn("Failed to send real-time update for execution {}: {}", 
                execution.getId(), e.getMessage());
        }
    }
    
    // Private helper methods
    
    private long countExecutionsByStatus(List<FlowExecution> executions, FlowExecution.ExecutionStatus status) {
        return executions.stream()
            .filter(exec -> exec.getExecutionStatus() == status)
            .count();
    }
    
    private double calculateSuccessRate(List<FlowExecution> executions) {
        if (executions.isEmpty()) {
            return 0.0;
        }
        
        long successful = countExecutionsByStatus(executions, FlowExecution.ExecutionStatus.COMPLETED);
        return (double) successful / executions.size() * 100;
    }
    
    private Map<String, Object> calculatePerformanceMetrics(List<FlowExecution> executions) {
        Map<String, Object> metrics = new HashMap<>();
        
        List<FlowExecution> completedExecutions = executions.stream()
            .filter(exec -> exec.getExecutionStatus() == FlowExecution.ExecutionStatus.COMPLETED)
            .filter(exec -> exec.getStartedAt() != null && exec.getCompletedAt() != null)
            .collect(Collectors.toList());
        
        if (completedExecutions.isEmpty()) {
            metrics.put("averageExecutionTime", 0.0);
            metrics.put("minExecutionTime", 0.0);
            metrics.put("maxExecutionTime", 0.0);
            return metrics;
        }
        
        List<Double> executionTimes = completedExecutions.stream()
            .map(exec -> (double) ChronoUnit.SECONDS.between(exec.getStartedAt(), exec.getCompletedAt()) / 60.0)
            .collect(Collectors.toList());
        
        double avgTime = executionTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double minTime = executionTimes.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double maxTime = executionTimes.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        
        metrics.put("averageExecutionTime", Math.round(avgTime * 100.0) / 100.0);
        metrics.put("minExecutionTime", Math.round(minTime * 100.0) / 100.0);
        metrics.put("maxExecutionTime", Math.round(maxTime * 100.0) / 100.0);
        
        return metrics;
    }
    
    private double calculateAverageExecutionTime(List<FlowExecution> executions) {
        List<FlowExecution> completedExecutions = executions.stream()
            .filter(exec -> exec.getExecutionStatus() == FlowExecution.ExecutionStatus.COMPLETED)
            .filter(exec -> exec.getStartedAt() != null && exec.getCompletedAt() != null)
            .collect(Collectors.toList());
        
        if (completedExecutions.isEmpty()) {
            return 0.0;
        }
        
        double totalTime = completedExecutions.stream()
            .mapToDouble(exec -> ChronoUnit.SECONDS.between(exec.getStartedAt(), exec.getCompletedAt()) / 60.0)
            .sum();
        
        return totalTime / completedExecutions.size();
    }
    
    private List<FlowExecution> getRecentExecutions(int hours) {
        LocalDateTime since = LocalDateTime.now().minus(hours, ChronoUnit.HOURS);
        return executionRepository.findAll().stream()
            .filter(exec -> exec.getStartedAt() != null && exec.getStartedAt().isAfter(since))
            .collect(Collectors.toList());
    }
    
    private List<FlowExecution> getRecentCompletedExecutions(int hours) {
        LocalDateTime since = LocalDateTime.now().minus(hours, ChronoUnit.HOURS);
        return executionRepository.findAll().stream()
            .filter(exec -> exec.getStartedAt() != null && exec.getStartedAt().isAfter(since))
            .filter(exec -> exec.getExecutionStatus() == FlowExecution.ExecutionStatus.COMPLETED)
            .collect(Collectors.toList());
    }
    
    private double calculateSystemHealth() {
        // Simple system health calculation based on recent success rate and performance
        List<FlowExecution> recentExecutions = getRecentExecutions(1);
        
        if (recentExecutions.isEmpty()) {
            return 100.0; // No activity = healthy
        }
        
        double successRate = calculateSuccessRate(recentExecutions);
        
        // Factor in long-running executions (reduce health if many long-running)
        List<FlowExecution> longRunning = getLongRunningExecutions(60); // 1 hour threshold
        double longRunningPenalty = Math.min(longRunning.size() * 10, 50); // Max 50% penalty
        
        double health = successRate - longRunningPenalty;
        return Math.max(0.0, Math.min(100.0, health));
    }
    
    private String calculateOverallTrend(List<Map<String, Object>> dailyMetrics) {
        if (dailyMetrics.size() < 2) {
            return "STABLE";
        }
        
        // Compare recent vs older metrics
        int halfPoint = dailyMetrics.size() / 2;
        double recentAvgSuccess = dailyMetrics.subList(0, halfPoint).stream()
            .mapToDouble(m -> (Double) m.get("successRate"))
            .average().orElse(0.0);
        
        double olderAvgSuccess = dailyMetrics.subList(halfPoint, dailyMetrics.size()).stream()
            .mapToDouble(m -> (Double) m.get("successRate"))
            .average().orElse(0.0);
        
        double difference = recentAvgSuccess - olderAvgSuccess;
        
        if (difference > 5.0) {
            return "IMPROVING";
        } else if (difference < -5.0) {
            return "DECLINING";
        } else {
            return "STABLE";
        }
    }
}