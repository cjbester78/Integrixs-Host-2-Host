package com.integrixs.core.service;

import com.integrixs.core.repository.DeployedFlowRepository;
import com.integrixs.core.repository.FlowExecutionRepository;
import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.core.repository.SystemConfigurationRepository;
import com.integrixs.shared.model.DeployedFlow;
import com.integrixs.shared.model.FlowExecution;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.util.SecurityContextHelper;
import com.integrixs.core.service.SystemAuthenticationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Service responsible for managing deployed flow lifecycle:
 * - Starting/stopping sender and receiver adapters when flows are deployed/undeployed
 * - Scheduling automatic execution based on sender adapter configuration
 * - Managing adapter polling and file processing
 */
@Service
@EnableScheduling
public class DeployedFlowSchedulingService {
    
    private static final Logger logger = LoggerFactory.getLogger(DeployedFlowSchedulingService.class);
    
    private final DeployedFlowRepository deployedFlowRepository;
    private final FlowExecutionRepository flowExecutionRepository;
    private final AdapterRepository adapterRepository;
    private final FlowExecutionService flowExecutionService;
    private final AdapterExecutionService adapterExecutionService;
    private final SystemConfigurationRepository configRepository;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final java.util.concurrent.Executor adapterExecutor;

    // Track scheduled tasks for each deployment
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> scheduledAdapterTasks = new ConcurrentHashMap<>();
    
    // Track running executions to enforce concurrency limits
    private final ConcurrentHashMap<UUID, Set<UUID>> runningExecutionsByFlow = new ConcurrentHashMap<>();
    
    @Autowired
    public DeployedFlowSchedulingService(DeployedFlowRepository deployedFlowRepository,
                                       FlowExecutionRepository flowExecutionRepository,
                                       AdapterRepository adapterRepository,
                                       FlowExecutionService flowExecutionService,
                                       AdapterExecutionService adapterExecutionService,
                                       SystemConfigurationRepository configRepository,
                                       @org.springframework.beans.factory.annotation.Qualifier("adapterTaskExecutor")
                                       java.util.concurrent.Executor adapterExecutor) {
        this.deployedFlowRepository = deployedFlowRepository;
        this.flowExecutionRepository = flowExecutionRepository;
        this.adapterRepository = adapterRepository;
        this.flowExecutionService = flowExecutionService;
        this.adapterExecutionService = adapterExecutionService;
        this.configRepository = configRepository;
        this.adapterExecutor = adapterExecutor;
        this.taskScheduler = createTaskScheduler();
    }
    
    @PostConstruct
    public void initialize() {
        logger.info("Initializing Deployed Flow Scheduling Service...");
        
        // Load existing running executions to track concurrency
        loadRunningExecutions();
        
        logger.info("Deployed Flow Scheduling Service initialized successfully");
    }
    
    @EventListener(ContextRefreshedEvent.class)
    public void onContextRefreshed() {
        logger.info("Spring context refreshed - starting adapters for existing deployed flows...");
        
        // Start adapters for all currently deployed flows after full Spring context initialization
        startAdaptersForDeployedFlows();
    }
    
    /**
     * Called when a flow is deployed - starts the associated adapters
     */
    public void onFlowDeployed(DeployedFlow deployedFlow) {
        logger.info("Flow deployed, starting adapters for deployment: {}", deployedFlow.getId());
        
        try {
            // Start sender adapter if configured
            if (deployedFlow.getSenderAdapterId() != null) {
                startSenderAdapter(deployedFlow);
            }
            
            // Start receiver adapter if configured  
            if (deployedFlow.getReceiverAdapterId() != null) {
                startReceiverAdapter(deployedFlow);
            }
            
            logger.info("Successfully started adapters for deployed flow: {}", deployedFlow.getFlowId());
            
        } catch (Exception e) {
            logger.error("Failed to start adapters for deployed flow {}: {}", 
                        deployedFlow.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Called when a flow is undeployed - stops the associated adapters
     */
    public void onFlowUndeployed(UUID deploymentId) {
        logger.info("Flow undeployed, stopping adapters for deployment: {}", deploymentId);
        
        try {
            // Get the deployed flow to access adapter IDs
            Optional<DeployedFlow> deploymentOpt = deployedFlowRepository.findById(deploymentId);
            if (deploymentOpt.isPresent()) {
                DeployedFlow deployment = deploymentOpt.get();
                
                // Stop sender adapter if configured
                if (deployment.getSenderAdapterId() != null) {
                    adapterRepository.updateStatus(deployment.getSenderAdapterId(), Adapter.AdapterStatus.STOPPED);
                    logger.info("Stopped sender adapter: {}", deployment.getSenderAdapterId());
                }
                
                // Stop receiver adapter if configured  
                if (deployment.getReceiverAdapterId() != null) {
                    adapterRepository.updateStatus(deployment.getReceiverAdapterId(), Adapter.AdapterStatus.STOPPED);
                    logger.info("Stopped receiver adapter: {}", deployment.getReceiverAdapterId());
                }
            }
            
            // Stop scheduled adapter task
            ScheduledFuture<?> scheduledTask = scheduledAdapterTasks.remove(deploymentId);
            if (scheduledTask != null && !scheduledTask.isCancelled()) {
                scheduledTask.cancel(false);
                logger.info("Cancelled scheduled adapter task for deployment: {}", deploymentId);
            }
            
            // Clean up running executions tracking
            runningExecutionsByFlow.remove(deploymentId);
            
            logger.info("Successfully stopped adapters for undeployed flow: {}", deploymentId);
            
        } catch (Exception e) {
            logger.error("Failed to stop adapters for undeployed flow {}: {}", 
                        deploymentId, e.getMessage(), e);
        }
    }
    
    /**
     * Start adapters for all currently deployed flows on service startup
     */
    private void startAdaptersForDeployedFlows() {
        try {
            logger.info("Starting adapters for existing deployed flows...");
            
            List<DeployedFlow> deployedFlows = deployedFlowRepository.findExecutableFlows();
            
            for (DeployedFlow deployedFlow : deployedFlows) {
                try {
                    onFlowDeployed(deployedFlow);
                } catch (Exception e) {
                    logger.error("Failed to start adapters for existing deployment {}: {}", 
                               deployedFlow.getId(), e.getMessage(), e);
                }
            }
            
            logger.info("Started adapters for {} deployed flows", deployedFlows.size());
            
        } catch (Exception e) {
            logger.error("Error starting adapters for deployed flows: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Start sender adapter with polling schedule.
     * CRITICAL: Synchronized on deployment ID to prevent race conditions.
     */
    private synchronized void startSenderAdapter(DeployedFlow deployedFlow) {
        UUID senderAdapterId = deployedFlow.getSenderAdapterId();
        UUID deploymentId = deployedFlow.getId();

        logger.info("Starting sender adapter: {} for deployment: {}",
                   senderAdapterId, deploymentId);

        // CRITICAL: Check if this deployment already has a scheduled task
        // Synchronized method ensures no race condition during check-then-act
        ScheduledFuture<?> existingTask = scheduledAdapterTasks.get(deploymentId);
        if (existingTask != null && !existingTask.isCancelled() && !existingTask.isDone()) {
            logger.info("Sender adapter {} for deployment {} is already scheduled, skipping duplicate scheduling",
                       senderAdapterId, deploymentId);
            return;
        }

        try{
            // Get sender adapter configuration
            Optional<Adapter> adapterOpt = adapterRepository.findById(senderAdapterId);
            if (adapterOpt.isEmpty()) {
                throw new RuntimeException("Sender adapter not found: " + senderAdapterId);
            }
            
            Adapter senderAdapter = adapterOpt.get();
            if (!senderAdapter.isActive()) {
                throw new RuntimeException("Sender adapter " + senderAdapterId + " is inactive and cannot be started");
            }
            
            // Validation: adapter must be STARTED (ready for execution) to accept scheduled tasks
            if (senderAdapter.getStatus() != Adapter.AdapterStatus.STARTED) {
                throw new RuntimeException("Sender adapter " + senderAdapterId + " is not STARTED (current: " + 
                                         senderAdapter.getStatus() + ") and cannot accept scheduled executions");
            }
            
            logger.info("Starting sender adapter {} for deployment", senderAdapterId);

            // Get scheduler configuration from the adapter (read dynamically, not from snapshot)
            Map<String, Object> adapterConfig = senderAdapter.getConfiguration();
            SchedulerConfig schedulerConfig = new SchedulerConfig(adapterConfig);
            
            logger.info("Scheduling sender adapter {} with scheduler config: {} - {} mode", 
                       senderAdapterId, schedulerConfig.getScheduleType(), schedulerConfig.getScheduleMode());
            
            // Schedule the adapter task using the comprehensive scheduler
            ScheduledFuture<?> scheduledTask = scheduleWithSchedulerConfig(
                () -> executeSenderAdapter(deployedFlow, senderAdapter), 
                schedulerConfig
            );
            
            // Track the scheduled task
            scheduledAdapterTasks.put(deployedFlow.getId(), scheduledTask);
            
            // Adapter is already STARTED - no status update needed
            
            // Health check: verify the adapter is still ready for scheduled execution
            Optional<Adapter> adapterCheck = adapterRepository.findById(senderAdapterId);
            if (adapterCheck.isEmpty() || adapterCheck.get().getStatus() != Adapter.AdapterStatus.STARTED) {
                throw new RuntimeException("Sender adapter " + senderAdapterId + " is no longer in STARTED state - cannot schedule executions");
            }
            
            logger.info("Successfully started sender adapter: {}", senderAdapterId);
            
        } catch (Exception e) {
            logger.error("Failed to start sender adapter {}: {}", senderAdapterId, e.getMessage(), e);
            throw new RuntimeException("Failed to start sender adapter", e);
        }
    }
    
    /**
     * Start receiver adapter (typically triggered by flow execution)
     */
    private void startReceiverAdapter(DeployedFlow deployedFlow) {
        UUID receiverAdapterId = deployedFlow.getReceiverAdapterId();
        
        logger.info("Configuring receiver adapter: {} for deployment: {}", 
                   receiverAdapterId, deployedFlow.getId());
        
        try {
            // Get receiver adapter configuration
            Optional<Adapter> adapterOpt = adapterRepository.findById(receiverAdapterId);
            if (adapterOpt.isEmpty()) {
                throw new RuntimeException("Receiver adapter not found: " + receiverAdapterId);
            }
            
            Adapter receiverAdapter = adapterOpt.get();
            if (!receiverAdapter.isActive()) {
                throw new RuntimeException("Receiver adapter " + receiverAdapterId + " is inactive and cannot be started");
            }
            
            // Validation: adapter must be STARTED (ready for execution) to accept flow data
            if (receiverAdapter.getStatus() != Adapter.AdapterStatus.STARTED) {
                throw new RuntimeException("Receiver adapter " + receiverAdapterId + " is not STARTED (current: " + 
                                         receiverAdapter.getStatus() + ") and cannot accept flow execution data");
            }
            
            logger.info("Starting receiver adapter {} for deployment", receiverAdapterId);
            
            // Adapter is already STARTED - no status update needed
            
            // Health check: verify the adapter is still ready for flow execution
            Optional<Adapter> adapterCheck = adapterRepository.findById(receiverAdapterId);
            if (adapterCheck.isEmpty() || adapterCheck.get().getStatus() != Adapter.AdapterStatus.STARTED) {
                throw new RuntimeException("Receiver adapter " + receiverAdapterId + " is no longer in STARTED state - cannot accept flow data");
            }
            
            logger.info("Receiver adapter {} configured and started successfully", receiverAdapterId);
            
        } catch (Exception e) {
            logger.error("Failed to configure receiver adapter {}: {}", receiverAdapterId, e.getMessage(), e);
            throw new RuntimeException("Failed to configure receiver adapter", e);
        }
    }
    
    /**
     * Execute sender adapter - checks for files/data and triggers flow execution
     */
    private void executeSenderAdapter(DeployedFlow deployedFlow, Adapter senderAdapter) {
        UUID deploymentId = deployedFlow.getId();
        UUID flowId = deployedFlow.getFlowId();
        
        try {
            logger.debug("Executing sender adapter: {} for flow: {}", 
                        senderAdapter.getId(), flowId);
            
            // Check concurrency limits
            int currentRunning = getCurrentRunningExecutions(deploymentId);
            int maxConcurrent = deployedFlow.getMaxConcurrentExecutions() != null ? 
                               deployedFlow.getMaxConcurrentExecutions() : 1;
            
            if (currentRunning >= maxConcurrent) {
                logger.debug("Flow {} at concurrency limit: {}/{}, skipping adapter execution", 
                            flowId, currentRunning, maxConcurrent);
                return;
            }
            
            // Execute adapter using live configuration
            Map<String, Object> adapterContext = new HashMap<>();
            adapterContext.put("deploymentId", deploymentId);
            adapterContext.put("flowId", flowId);
            
            Map<String, Object> adapterResult = adapterExecutionService.executeAdapter(
                senderAdapter, adapterContext, null);
            
            // Check if adapter found files/data to process
            Boolean hasData = (Boolean) adapterResult.getOrDefault("hasData", false);
            
            if (hasData) {
                logger.info("Sender adapter {} found data, triggering flow execution for: {}", 
                           senderAdapter.getId(), flowId);
                
                // Trigger flow execution with adapter data
                triggerFlowExecution(deployedFlow, adapterResult);
            } else {
                logger.debug("Sender adapter {} found no data to process for flow: {}", 
                           senderAdapter.getId(), flowId);
            }
            
        } catch (Exception e) {
            logger.error("Error executing sender adapter {} for flow {}: {}", 
                        senderAdapter.getId(), flowId, e.getMessage(), e);
            
            // Update deployment error statistics
            try {
                deployedFlow.recordError("Sender adapter execution failed: " + e.getMessage());
                deployedFlowRepository.update(deployedFlow);
            } catch (Exception updateError) {
                logger.warn("Failed to update deployment error statistics: {}", updateError.getMessage());
            }
        }
    }
    
    /**
     * Trigger flow execution when adapter finds data
     */
    private void triggerFlowExecution(DeployedFlow deployedFlow, Map<String, Object> triggerData) {
        UUID deploymentId = deployedFlow.getId();
        UUID flowId = deployedFlow.getFlowId();
        
        try {
            // Track this execution as starting
            UUID executionId = UUID.randomUUID(); // Temporary ID
            addRunningExecution(deploymentId, executionId);
            
            // Update last execution time
            deployedFlow.setLastExecutionAt(LocalDateTime.now());
            deployedFlowRepository.update(deployedFlow);
            
            // Create system-triggered execution context
            // For system-triggered executions (polling), use the user who deployed the flow
            UUID systemUserId = deployedFlow.getDeployedBy();
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("triggerType", "SENDER_ADAPTER");
            payload.put("deploymentId", deploymentId);
            payload.put("triggerData", triggerData);
            payload.put("triggeredAt", LocalDateTime.now().toString());
            
            // Execute flow with system authentication context - use SCHEDULED trigger type for automatic executions
            FlowExecution execution = executeFlowWithSystemAuth(flowId, payload, systemUserId);
            
            // Update tracking with actual execution ID
            removeRunningExecution(deploymentId, executionId);
            addRunningExecution(deploymentId, execution.getId());
            
            logger.info("Triggered flow execution: {} for flow: {} (deployment: {})", 
                       execution.getId(), flowId, deploymentId);
            
        } catch (Exception e) {
            logger.error("Failed to trigger flow execution for {} (deployment: {}): {}", 
                       flowId, deploymentId, e.getMessage(), e);
            
            // Remove from running tracking on error  
            UUID tempExecutionId = UUID.randomUUID();
            removeRunningExecution(deploymentId, tempExecutionId);
            
            // Update deployment error statistics
            try {
                deployedFlow.recordError("Flow execution trigger failed: " + e.getMessage());
                deployedFlowRepository.update(deployedFlow);
            } catch (Exception updateError) {
                logger.warn("Failed to update deployment error statistics: {}", updateError.getMessage());
            }
        }
    }
    
    /**
     * Schedule a task using comprehensive scheduler configuration.
     * CRITICAL: No fallback scheduling - adapter must have valid scheduler config or it will not run.
     * This is required for compliance - scheduled execution times are approved by all parties.
     */
    private ScheduledFuture<?> scheduleWithSchedulerConfig(Runnable task, SchedulerConfig schedulerConfig) {
        String scheduleType = schedulerConfig.getScheduleType();
        String scheduleMode = schedulerConfig.getScheduleMode();

        // Validate scheduler configuration - no fallbacks allowed
        if (scheduleMode == null || scheduleMode.trim().isEmpty()) {
            throw new IllegalStateException("Scheduler configuration missing: scheduleMode is required. " +
                "Adapter cannot be scheduled without valid scheduler configuration.");
        }

        if (!"OnTime".equals(scheduleMode) && !"Every".equals(scheduleMode)) {
            throw new IllegalStateException("Invalid scheduleMode: '" + scheduleMode + "'. " +
                "Must be 'OnTime' or 'Every'. Adapter cannot be scheduled with invalid configuration.");
        }

        if ("OnTime".equals(scheduleMode)) {
            // Validate OnTime specific config
            String onTimeValue = schedulerConfig.getOnTimeValue();
            if (onTimeValue == null || onTimeValue.trim().isEmpty()) {
                throw new IllegalStateException("Scheduler configuration missing: onTimeValue is required for OnTime mode.");
            }
            logger.info("Scheduling OnTime execution for {} at {}", scheduleType, onTimeValue);
            return scheduleAtSpecificTime(task, schedulerConfig);

        } else if ("Every".equals(scheduleMode)) {
            // Validate Every specific config
            String everyInterval = schedulerConfig.getEveryInterval();
            if (everyInterval == null || everyInterval.trim().isEmpty()) {
                throw new IllegalStateException("Scheduler configuration missing: everyInterval is required for Every mode.");
            }
            long intervalMs = parseIntervalToMilliseconds(everyInterval);
            if (intervalMs <= 0) {
                throw new IllegalStateException("Invalid everyInterval: '" + everyInterval + "'. " +
                    "Could not parse to valid interval.");
            }
            logger.info("Scheduling Every execution with interval: {}ms ({})", intervalMs, everyInterval);
            return scheduleWithInterval(task, schedulerConfig);
        }

        // This should never be reached due to validation above
        throw new IllegalStateException("Unexpected scheduling error - no valid schedule mode configured.");
    }
    
    /**
     * Schedule task at specific time based on Daily/Weekly/Monthly
     */
    private ScheduledFuture<?> scheduleAtSpecificTime(Runnable task, SchedulerConfig schedulerConfig) {
        // For now, implement as fixed rate every minute and check time during execution
        // This is a simplified implementation - a full implementation would use cron expressions
        logger.info("Scheduling OnTime execution for {} at {}",
                   schedulerConfig.getScheduleType(), schedulerConfig.getOnTimeValue());

        return taskScheduler.scheduleAtFixedRate(() -> {
            if (shouldExecuteAtTime(schedulerConfig)) {
                task.run();
            }
        }, java.time.Duration.ofMinutes(1)); // Check every minute
    }
    
    /**
     * Schedule task with interval within time range
     */
    private ScheduledFuture<?> scheduleWithInterval(Runnable task, SchedulerConfig schedulerConfig) {
        long intervalMs = parseIntervalToMilliseconds(schedulerConfig.getEveryInterval());

        logger.info("SCHEDULER DEBUG: About to schedule with intervalMs = {}ms", intervalMs);
        logger.info("Scheduling Every execution with interval: {}ms", intervalMs);

        // Use scheduleAtFixedRate for consistent interval execution
        // This ensures tasks start at fixed intervals regardless of execution time
        // Wrap task in adapterExecutor to allow concurrent executions
        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(() -> {
            logger.info("SCHEDULER DEBUG: Task triggered at {}", java.time.LocalDateTime.now());
            if (shouldExecuteInTimeRange(schedulerConfig)) {
                // Execute task asynchronously in thread pool to allow concurrency
                adapterExecutor.execute(() -> {
                    try {
                        logger.info("SCHEDULER DEBUG: Task executing in thread: {}", Thread.currentThread().getName());
                        task.run();
                    } catch (Exception e) {
                        logger.error("Error executing adapter task: {}", e.getMessage(), e);
                    }
                });
            }
        }, java.time.Duration.ofMillis(intervalMs));

        logger.info("SCHEDULER DEBUG: Scheduled future created successfully");
        return future;
    }
    
    /**
     * Check if current time matches the OnTime schedule
     */
    private boolean shouldExecuteAtTime(SchedulerConfig schedulerConfig) {
        // Simplified implementation - just check if we're at the right time
        // A full implementation would properly handle Daily/Weekly/Monthly logic with time zones
        return true; // For now, always execute when checked
    }
    
    /**
     * Check if current time is within the Every time range
     */
    private boolean shouldExecuteInTimeRange(SchedulerConfig schedulerConfig) {
        // Simplified implementation - check if current time is between start and end times
        // A full implementation would properly parse and compare times
        return true; // For now, always execute
    }
    
    /**
     * Convert interval string to milliseconds.
     * CRITICAL: No fallbacks - invalid or missing configuration must fail loudly.
     * The scheduler MUST use only database-supplied configuration values.
     */
    private long parseIntervalToMilliseconds(String interval) {
        logger.info("SCHEDULER DEBUG: Parsing interval: '{}'", interval);

        // CRITICAL: No fallback for null - configuration is required
        if (interval == null || interval.trim().isEmpty()) {
            throw new IllegalStateException(
                "Scheduler configuration error: everyInterval is required but not found in adapter configuration. " +
                "Please configure the adapter with a valid interval (e.g., '1 min', '30 sec', '2 hour').");
        }

        try {
            if (interval.endsWith(" sec")) {
                int seconds = Integer.parseInt(interval.replace(" sec", "").trim());
                if (seconds <= 0) {
                    throw new IllegalStateException("Interval must be positive: " + interval);
                }
                long result = seconds * 1000L;
                logger.info("SCHEDULER DEBUG: Parsed '{}' as {} seconds = {}ms", interval, seconds, result);
                return result;
            } else if (interval.endsWith(" min")) {
                int minutes = Integer.parseInt(interval.replace(" min", "").trim());
                if (minutes <= 0) {
                    throw new IllegalStateException("Interval must be positive: " + interval);
                }
                long result = minutes * 60000L;
                logger.info("SCHEDULER DEBUG: Parsed '{}' as {} minutes = {}ms", interval, minutes, result);
                return result;
            } else if (interval.endsWith(" hour") || interval.endsWith(" hours")) {
                int hours = Integer.parseInt(interval.replace(" hour", "").replace(" hours", "").trim());
                if (hours <= 0) {
                    throw new IllegalStateException("Interval must be positive: " + interval);
                }
                long result = hours * 3600000L;
                logger.info("SCHEDULER DEBUG: Parsed '{}' as {} hours = {}ms", interval, hours, result);
                return result;
            }
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                "Scheduler configuration error: Invalid interval format '" + interval + "'. " +
                "Expected format: '<number> sec', '<number> min', or '<number> hour'. " +
                "Example: '1 min', '30 sec', '2 hour'", e);
        }

        // CRITICAL: No fallback for unrecognized format - must fail
        throw new IllegalStateException(
            "Scheduler configuration error: Unrecognized interval format '" + interval + "'. " +
            "Expected format: '<number> sec', '<number> min', or '<number> hour'. " +
            "Example: '1 min', '30 sec', '2 hour'");
    }
    
    /**
     * Wrapper class for scheduler configuration.
     * CRITICAL: No defaults for required fields - missing config must fail validation.
     */
    private static class SchedulerConfig {
        private final Map<String, Object> config;

        public SchedulerConfig(Map<String, Object> config) {
            this.config = config != null ? config : new HashMap<>();
        }

        // Required field - no default (must be explicitly configured)
        public String getScheduleType() {
            return getString("scheduleType");
        }

        // Required field - no default (must be explicitly configured)
        public String getScheduleMode() {
            return getString("scheduleMode");
        }

        // Required for OnTime mode - no default
        public String getOnTimeValue() {
            return getString("onTimeValue");
        }

        // Required for Every mode - no default
        public String getEveryInterval() {
            return getString("everyInterval");
        }

        // Optional with sensible defaults for time range
        public String getEveryStartTime() {
            return getStringWithDefault("everyStartTime", "00:00");
        }

        public String getEveryEndTime() {
            return getStringWithDefault("everyEndTime", "23:59");
        }

        public String getTimeZone() {
            return getStringWithDefault("timeZone", "UTC 0:00");
        }

        private String getString(String key) {
            Object value = config.get(key);
            return value != null ? value.toString() : null;
        }

        private String getStringWithDefault(String key, String defaultValue) {
            Object value = config.get(key);
            return value != null ? value.toString() : defaultValue;
        }
    }
    
    /**
     * Create task scheduler
     */
    private ThreadPoolTaskScheduler createTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10); // Allow up to 10 concurrent adapter polling tasks
        scheduler.setThreadNamePrefix("DeployedFlow-Adapter-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.initialize();
        return scheduler;
    }
    
    /**
     * Get current running executions for a deployment
     */
    private int getCurrentRunningExecutions(UUID deploymentId) {
        Set<UUID> runningExecutions = runningExecutionsByFlow.get(deploymentId);
        if (runningExecutions == null || runningExecutions.isEmpty()) {
            return 0;
        }

        // CRITICAL FIX: Verify executions are actually still running in database
        // Don't rely on stale in-memory tracking that waits 5 minutes for cleanup
        try {
            List<FlowExecution> stillRunning = flowExecutionRepository.findRunningExecutions();
            Set<UUID> stillRunningIds = new HashSet<>();
            for (FlowExecution execution : stillRunning) {
                stillRunningIds.add(execution.getId());
            }

            // Remove completed executions from tracking immediately
            runningExecutions.removeIf(executionId -> !stillRunningIds.contains(executionId));

            return runningExecutions.size();
        } catch (Exception e) {
            logger.warn("Error checking running executions, using cached count: {}", e.getMessage());
            return runningExecutions.size(); // Fallback to cached count on error
        }
    }
    
    /**
     * Add running execution to tracking
     */
    private void addRunningExecution(UUID deploymentId, UUID executionId) {
        runningExecutionsByFlow.computeIfAbsent(deploymentId, k -> ConcurrentHashMap.newKeySet())
                              .add(executionId != null ? executionId : UUID.randomUUID());
    }
    
    /**
     * Remove running execution from tracking
     */
    private void removeRunningExecution(UUID deploymentId, UUID executionId) {
        Set<UUID> runningExecutions = runningExecutionsByFlow.get(deploymentId);
        if (runningExecutions != null) {
            if (executionId != null) {
                runningExecutions.remove(executionId);
            } else {
                // Remove any placeholder entries if executionId is null
                runningExecutions.clear();
            }
            
            if (runningExecutions.isEmpty()) {
                runningExecutionsByFlow.remove(deploymentId);
            }
        }
    }
    
    /**
     * Load existing running executions to properly track concurrency
     */
    private void loadRunningExecutions() {
        try {
            logger.debug("Loading existing running executions for concurrency tracking...");
            
            List<FlowExecution> runningExecutions = flowExecutionRepository.findRunningExecutions();
            
            for (FlowExecution execution : runningExecutions) {
                // Extract deployment ID from execution context
                if (execution.getExecutionContext() != null) {
                    Object deploymentIdObj = execution.getExecutionContext().get("deploymentId");
                    if (deploymentIdObj instanceof UUID) {
                        UUID deploymentId = (UUID) deploymentIdObj;
                        addRunningExecution(deploymentId, execution.getId());
                    }
                }
            }
            
            logger.info("Loaded {} running executions for concurrency tracking", runningExecutions.size());
            
        } catch (Exception e) {
            logger.error("Failed to load running executions: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Clean up completed executions from tracking
     */
    private void cleanupCompletedExecutions() {
        try {
            // Get all currently running executions from database
            List<FlowExecution> stillRunning = flowExecutionRepository.findRunningExecutions();
            Set<UUID> stillRunningIds = new HashSet<>();
            
            for (FlowExecution execution : stillRunning) {
                stillRunningIds.add(execution.getId());
            }
            
            // Clean up our tracking map
            runningExecutionsByFlow.forEach((deploymentId, executionIds) -> {
                executionIds.removeIf(executionId -> !stillRunningIds.contains(executionId));
            });
            
            // Remove empty deployment entries
            runningExecutionsByFlow.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            
        } catch (Exception e) {
            logger.warn("Error during execution cleanup: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Periodic cleanup task - uses configurable interval from application properties
     * - Cleans up completed executions from tracking
     * - Monitors active deployments
     * - Logs scheduler status
     */
    @Scheduled(fixedRateString = "${h2h.scheduler.cleanup-interval:300000}") // Default 5 minutes
    public void periodicMaintenance() {
        try {
            logger.debug("Running periodic scheduler maintenance...");
            
            // Clean up completed executions
            cleanupCompletedExecutions();
            
            // Log scheduler status
            int activeDeployments = scheduledAdapterTasks.size();
            int totalRunningExecutions = runningExecutionsByFlow.values().stream()
                                                                .mapToInt(Set::size)
                                                                .sum();
            
            if (activeDeployments > 0 || totalRunningExecutions > 0) {
                logger.info("Scheduler status: {} active deployments, {} running executions", 
                          activeDeployments, totalRunningExecutions);
            }
            
            // Check for deployments that should be running but aren't scheduled
            List<DeployedFlow> executableFlows = deployedFlowRepository.findExecutableFlows();
            for (DeployedFlow flow : executableFlows) {
                if (!scheduledAdapterTasks.containsKey(flow.getId()) && 
                    flow.getSenderAdapterId() != null) {
                    logger.warn("Found unscheduled executable flow: {}, attempting to start...", flow.getId());
                    try {
                        startSenderAdapter(flow);
                    } catch (Exception e) {
                        logger.error("Failed to start unscheduled flow {}: {}", flow.getId(), e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error during periodic maintenance: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Manual trigger for a specific deployment (for testing/admin purposes)
     */
    public void triggerDeployedFlow(UUID deploymentId) {
        logger.info("Manual trigger requested for deployment: {}", deploymentId);
        
        try {
            Optional<DeployedFlow> deploymentOpt = deployedFlowRepository.findById(deploymentId);
            if (deploymentOpt.isEmpty()) {
                throw new IllegalArgumentException("Deployment not found: " + deploymentId);
            }
            
            DeployedFlow deployment = deploymentOpt.get();
            if (deployment.getRuntimeStatus() != DeployedFlow.RuntimeStatus.ACTIVE) {
                throw new IllegalArgumentException("Deployment is not active: " + deploymentId);
            }
            
            // Manually trigger flow execution
            Map<String, Object> triggerData = new HashMap<>();
            triggerData.put("manualTrigger", true);
            triggerFlowExecution(deployment, triggerData);
            
        } catch (Exception e) {
            logger.error("Failed to manually trigger deployment {}: {}", deploymentId, e.getMessage(), e);
            throw new RuntimeException("Failed to trigger deployment", e);
        }
    }
    
    /**
     * Manually trigger execution for a specific adapter
     * This mimics the scheduled execution but can be called via API
     */
    public Map<String, Object> manuallyTriggerAdapterExecution(UUID adapterId, UUID triggeredByUserId) {
        logger.info("Manual execution triggered for adapter: {} by user: {}", adapterId, triggeredByUserId);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Find deployed flow that uses this adapter as sender
            Optional<DeployedFlow> deployedFlowOpt = deployedFlowRepository.findExecutableFlows()
                .stream()
                .filter(df -> adapterId.equals(df.getSenderAdapterId()))
                .findFirst();
            
            if (deployedFlowOpt.isEmpty()) {
                throw new RuntimeException("No deployed flow found with adapter " + adapterId + " as sender");
            }
            
            DeployedFlow deployedFlow = deployedFlowOpt.get();
            
            // Check if flow can execute
            Map<String, Object> executionCheck = deployedFlowRepository.checkFlowExecution(deployedFlow.getFlowId());
            Boolean canExecute = (Boolean) executionCheck.get("canExecute");
            
            if (!canExecute) {
                String reason = (String) executionCheck.get("reason");
                throw new RuntimeException("Flow cannot execute: " + reason);
            }
            
            // Get sender adapter
            Optional<Adapter> adapterOpt = adapterRepository.findById(adapterId);
            if (adapterOpt.isEmpty()) {
                throw new RuntimeException("Adapter not found: " + adapterId);
            }
            
            Adapter senderAdapter = adapterOpt.get();
            
            // Execute the sender adapter using the same logic as scheduled execution
            executeSenderAdapter(deployedFlow, senderAdapter);
            
            result.put("success", true);
            result.put("message", "Manual execution completed successfully");
            result.put("flowId", deployedFlow.getFlowId());
            result.put("deploymentId", deployedFlow.getId());
            result.put("adapterId", adapterId);
            
        } catch (Exception e) {
            logger.error("Manual execution failed for adapter {}: {}", adapterId, e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
            throw new RuntimeException("Manual execution failed: " + e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * Get scheduling statistics for monitoring
     */
    public Map<String, Object> getSchedulingStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            List<DeployedFlow> activeDeployments = deployedFlowRepository.findExecutableFlows();
            
            stats.put("totalActiveDeployments", activeDeployments.size());
            stats.put("currentlyRunningExecutions", runningExecutionsByFlow.values().stream()
                                                                            .mapToInt(Set::size)
                                                                            .sum());
            stats.put("deploymentsWithRunningExecutions", runningExecutionsByFlow.size());
            
            // Add per-deployment running counts
            Map<String, Integer> runningByDeployment = new HashMap<>();
            runningExecutionsByFlow.forEach((deploymentId, executions) -> {
                runningByDeployment.put(deploymentId.toString(), executions.size());
            });
            stats.put("runningExecutionsByDeployment", runningByDeployment);
            
        } catch (Exception e) {
            logger.warn("Error collecting scheduling statistics: {}", e.getMessage(), e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }
    
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down Deployed Flow Scheduling Service...");
        
        // Cancel all scheduled adapter tasks
        scheduledAdapterTasks.forEach((deploymentId, task) -> {
            if (!task.isCancelled()) {
                task.cancel(false);
                logger.debug("Cancelled scheduled task for deployment: {}", deploymentId);
            }
        });
        scheduledAdapterTasks.clear();
        
        // Shutdown task scheduler
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
        
        logger.info("Deployed Flow Scheduling Service shutdown completed");
    }
    
    /**
     * Execute flow with system authentication context
     */
    private FlowExecution executeFlowWithSystemAuth(UUID flowId, Map<String, Object> payload, UUID systemUserId) {
        try {
            SystemAuthenticationContext.setSystemIntegratorAuthentication();
            return flowExecutionService.executeFlow(flowId, payload, systemUserId, FlowExecution.TriggerType.SCHEDULED);
        } finally {
            SystemAuthenticationContext.clearAuthentication();
        }
    }
}