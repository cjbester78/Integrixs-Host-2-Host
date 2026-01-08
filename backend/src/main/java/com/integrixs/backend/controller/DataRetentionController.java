package com.integrixs.backend.controller;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.backend.dto.ExecutionValidationResult;
import com.integrixs.backend.dto.request.AdminSystemRequest;
import com.integrixs.backend.dto.response.AdminSystemResponse;
import com.integrixs.backend.jobs.DataRetentionJobRegistry;
import com.integrixs.backend.service.AdministrativeRequestValidationService;
import com.integrixs.backend.service.DataRetentionService;
import com.integrixs.backend.service.ResponseStandardizationService;
import com.integrixs.core.repository.DataRetentionConfigRepository;
import com.integrixs.shared.model.DataRetentionConfig;
import com.integrixs.shared.util.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API Controller for Data Retention Management.
 * 
 * Provides endpoints for:
 * - Managing retention configurations
 * - Manual execution of retention policies
 * - Monitoring execution status
 * - Validating cron expressions
 * Refactored following OOP principles with proper validation, DTOs, and error handling.
 */
@RestController
@RequestMapping("/api/admin/data-retention")
public class DataRetentionController {

    private static final Logger logger = LoggerFactory.getLogger(DataRetentionController.class);
    
    private final DataRetentionConfigRepository retentionConfigRepository;
    private final DataRetentionService dataRetentionService;
    private final DataRetentionJobRegistry jobRegistry;
    private final AdministrativeRequestValidationService validationService;
    private final ResponseStandardizationService responseService;
    
    @Autowired
    public DataRetentionController(DataRetentionConfigRepository retentionConfigRepository, 
                                 DataRetentionService dataRetentionService,
                                 DataRetentionJobRegistry jobRegistry,
                                 AdministrativeRequestValidationService validationService,
                                 ResponseStandardizationService responseService) {
        this.retentionConfigRepository = retentionConfigRepository;
        this.dataRetentionService = dataRetentionService;
        this.jobRegistry = jobRegistry;
        this.validationService = validationService;
        this.responseService = responseService;
    }
    
    /**
     * Get current user ID from security context.
     */
    private UUID getCurrentUserId() {
        return SecurityContextHelper.getCurrentUserId();
    }

    /**
     * Get all retention configurations.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> getAllConfigurations() {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminSystemRequest configsRequest = AdminSystemRequest.builder()
            .operation("list_data_retention_configs")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateDataRetentionRequest(Map.of());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid data retention list request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            List<DataRetentionConfig> configs = retentionConfigRepository.findAll();
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.builder()
                .operation("list_data_retention_configs")
                .status("SUCCESS")
                .totalRecords(configs.size())
                .statistics(Map.of("configurations", configs))
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get all data retention configurations for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to retrieve configurations", e);
        }
    }

    /**
     * Get retention configuration by ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> getConfiguration(@PathVariable UUID id) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminSystemRequest configRequest = AdminSystemRequest.builder()
            .operation("get_data_retention_config")
            .search(id.toString())
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateDataRetentionRequest(
            Map.of("configId", id.toString())
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid data retention config request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            DataRetentionConfig config = retentionConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Data retention configuration not found with ID: " + id));
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.builder()
                .operation("get_data_retention_config")
                .status("SUCCESS")
                .totalRecords(1)
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get data retention configuration {} for user: {}", id, currentUserId, e);
            throw new RuntimeException("Failed to retrieve configuration", e);
        }
    }

    /**
     * Create new retention configuration.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> createConfiguration(@RequestBody DataRetentionConfig config) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Validate configuration first
        if (!config.isValid()) {
            throw new IllegalArgumentException("Invalid configuration parameters");
        }
        
        // Check for duplicate names
        if (retentionConfigRepository.existsByName(config.getName())) {
            throw new IllegalArgumentException("Configuration with this name already exists");
        }
        
        // Validate cron expression for SCHEDULE type
        if (config.getDataType() == DataRetentionConfig.DataType.SCHEDULE) {
            if (!dataRetentionService.isValidCronExpression(config.getScheduleCron())) {
                throw new IllegalArgumentException("Invalid cron expression");
            }
        }
        
        // Create immutable request DTO
        AdminSystemRequest createRequest = AdminSystemRequest.builder()
            .operation("create_data_retention_config")
            .search(config.getName())
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateDataRetentionRequest(
            Map.of("configName", config.getName(), "dataType", config.getDataType().toString())
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid data retention create request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            // Set audit fields
            config.setId(null); // Ensure new ID is generated
            config.setCreatedBy(currentUserId.toString());
            config.setUpdatedBy(currentUserId.toString());

            DataRetentionConfig saved = retentionConfigRepository.save(config);
            
            // Refresh schedules if this is a SCHEDULE type configuration
            if (saved.getDataType() == DataRetentionConfig.DataType.SCHEDULE) {
                dataRetentionService.refreshScheduledTasks();
            }
            
            logger.info("Created data retention configuration: {} ({}) by user: {}", 
                       saved.getName(), saved.getDataType(), currentUserId);
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.builder()
                .operation("create_data_retention_config")
                .status("SUCCESS")
                .totalRecords(1)
                .build();
            
            return responseService.created(response, "Configuration created successfully");
            
        } catch (Exception e) {
            logger.error("Failed to create data retention configuration for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to create configuration", e);
        }
    }

    /**
     * Update existing retention configuration.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> updateConfiguration(@PathVariable UUID id, 
                                                @RequestBody DataRetentionConfig config) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Validate configuration first
        if (!config.isValid()) {
            throw new IllegalArgumentException("Invalid configuration parameters");
        }
        
        // Validate cron expression for SCHEDULE type
        if (config.getDataType() == DataRetentionConfig.DataType.SCHEDULE) {
            if (!dataRetentionService.isValidCronExpression(config.getScheduleCron())) {
                throw new IllegalArgumentException("Invalid cron expression");
            }
        }
        
        // Create immutable request DTO
        AdminSystemRequest updateRequest = AdminSystemRequest.builder()
            .operation("update_data_retention_config")
            .search(id.toString())
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateDataRetentionRequest(
            Map.of("configId", id.toString(), "configName", config.getName())
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid data retention update request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            DataRetentionConfig existing = retentionConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Data retention configuration not found with ID: " + id));
            
            // Check for duplicate names (excluding current record)
            if (!existing.getName().equals(config.getName()) && 
                retentionConfigRepository.existsByName(config.getName())) {
                throw new IllegalArgumentException("Configuration with this name already exists");
            }
            
            // Update fields
            existing.setName(config.getName());
            existing.setDescription(config.getDescription());
            existing.setRetentionDays(config.getRetentionDays());
            existing.setArchiveDays(config.getArchiveDays());
            existing.setScheduleCron(config.getScheduleCron());
            existing.setEnabled(config.getEnabled());
            existing.setUpdatedBy(currentUserId.toString());

            DataRetentionConfig saved = retentionConfigRepository.save(existing);
            
            // Refresh schedules if this is a SCHEDULE type configuration
            if (saved.getDataType() == DataRetentionConfig.DataType.SCHEDULE) {
                dataRetentionService.refreshScheduledTasks();
            }
            
            logger.info("Updated data retention configuration: {} ({}) by user: {}", 
                       saved.getName(), saved.getDataType(), currentUserId);
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.builder()
                .operation("update_data_retention_config")
                .status("SUCCESS")
                .totalRecords(1)
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to update data retention configuration {} for user: {}", id, currentUserId, e);
            throw new RuntimeException("Failed to update configuration", e);
        }
    }

    /**
     * Delete retention configuration.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> deleteConfiguration(@PathVariable UUID id) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminSystemRequest deleteRequest = AdminSystemRequest.builder()
            .operation("delete_data_retention_config")
            .search(id.toString())
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateDataRetentionRequest(
            Map.of("configId", id.toString())
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid data retention delete request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            DataRetentionConfig config = retentionConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Data retention configuration not found with ID: " + id));
            
            retentionConfigRepository.delete(config);
            
            // Refresh schedules if this was a SCHEDULE type configuration
            if (config.getDataType() == DataRetentionConfig.DataType.SCHEDULE) {
                dataRetentionService.refreshScheduledTasks();
            }
            
            logger.info("Deleted data retention configuration: {} ({}) by user: {}", 
                       config.getName(), config.getDataType(), currentUserId);
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.builder()
                .operation("delete_data_retention_config")
                .status("SUCCESS")
                .totalRecords(1)
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to delete data retention configuration {} for user: {}", id, currentUserId, e);
            throw new RuntimeException("Failed to delete configuration", e);
        }
    }

    /**
     * Enable/disable retention configuration.
     */
    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> toggleConfiguration(@PathVariable UUID id) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminSystemRequest toggleRequest = AdminSystemRequest.builder()
            .operation("toggle_data_retention_config")
            .search(id.toString())
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateDataRetentionRequest(
            Map.of("configId", id.toString())
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid data retention toggle request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            DataRetentionConfig config = retentionConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Data retention configuration not found with ID: " + id));
            
            boolean previouslyEnabled = config.getEnabled();
            config.setEnabled(!previouslyEnabled);
            config.setUpdatedBy(currentUserId.toString());
            
            DataRetentionConfig saved = retentionConfigRepository.save(config);
            
            // Refresh schedules if this is a SCHEDULE type configuration
            if (saved.getDataType() == DataRetentionConfig.DataType.SCHEDULE) {
                dataRetentionService.refreshScheduledTasks();
            }
            
            logger.info("Toggled data retention configuration: {} - enabled: {} by user: {}", 
                       saved.getName(), saved.getEnabled(), currentUserId);
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.builder()
                .operation("toggle_data_retention_config")
                .status("SUCCESS")
                .totalRecords(1)
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to toggle data retention configuration {} for user: {}", id, currentUserId, e);
            throw new RuntimeException("Failed to toggle configuration", e);
        }
    }

    /**
     * Get execution status and monitoring information.
     */
    @GetMapping("/status")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> getExecutionStatus() {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminSystemRequest statusRequest = AdminSystemRequest.builder()
            .operation("get_data_retention_status")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateDataRetentionRequest(Map.of());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid data retention status request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            DataRetentionService.DataRetentionStatus status = dataRetentionService.getExecutionStatus();
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.builder()
                .operation("get_data_retention_status")
                .status("SUCCESS")
                .totalRecords(1)
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get data retention execution status for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to retrieve execution status", e);
        }
    }

    /**
     * Manually execute data retention (for testing/admin purposes).
     */
    @PostMapping("/execute")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> executeManualRetention() {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminSystemRequest executeRequest = AdminSystemRequest.builder()
            .operation("execute_data_retention")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateDataRetentionRequest(Map.of());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid data retention execute request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            logger.info("Manual data retention execution requested by user: {}", currentUserId);
            dataRetentionService.executeManualRetention();
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.builder()
                .operation("execute_data_retention")
                .status("SUCCESS")
                .totalRecords(1)
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to execute manual data retention for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to execute retention", e);
        }
    }

    /**
     * Refresh all scheduled tasks.
     */
    @PostMapping("/refresh-schedules")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> refreshSchedules() {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminSystemRequest refreshRequest = AdminSystemRequest.builder()
            .operation("refresh_data_retention_schedules")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateDataRetentionRequest(Map.of());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid data retention schedule refresh request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            dataRetentionService.refreshScheduledTasks();
            Map<String, String> taskInfo = dataRetentionService.getScheduledTasksInfo();
            
            logger.info("Data retention schedules refreshed by user: {} - {} active schedules", 
                       currentUserId, taskInfo.size());
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.builder()
                .operation("refresh_data_retention_schedules")
                .status("SUCCESS")
                .totalRecords(taskInfo.size())
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to refresh data retention schedules for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to refresh schedules", e);
        }
    }

    /**
     * Validate cron expression.
     */
    @PostMapping("/validate-cron")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> validateCronExpression(@RequestBody Map<String, String> request) {
        
        UUID currentUserId = getCurrentUserId();
        String cronExpression = request.get("cron");
        
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            throw new IllegalArgumentException("Cron expression is required");
        }
        
        // Create immutable request DTO
        AdminSystemRequest validateRequest = AdminSystemRequest.builder()
            .operation("validate_cron_expression")
            .search(cronExpression)
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateDataRetentionRequest(
            Map.of("cronExpression", cronExpression)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid cron validation request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            boolean valid = dataRetentionService.isValidCronExpression(cronExpression);
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.builder()
                .operation("validate_cron_expression")
                .status(valid ? "SUCCESS" : "ERROR")
                .totalRecords(valid ? 1 : 0)
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to validate cron expression for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to validate cron expression", e);
        }
    }

    /**
     * Get available executor jobs for dropdown selection.
     */
    @GetMapping("/available-jobs")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> getAvailableJobs() {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminSystemRequest jobsRequest = AdminSystemRequest.builder()
            .operation("get_available_jobs")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateDataRetentionRequest(Map.of());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid available jobs request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            List<Map<String, String>> jobList = jobRegistry.getAllJobs().values().stream()
                .map(job -> Map.of(
                    "value", job.getJobIdentifier(),
                    "label", job.getDisplayName(),
                    "description", job.getDescription()
                ))
                .sorted((a, b) -> a.get("label").compareTo(b.get("label")))
                .toList();
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.builder()
                .operation("get_available_jobs")
                .status("SUCCESS")
                .totalRecords(jobList.size())
                .statistics(Map.of("availableJobs", jobList))
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get available jobs for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to retrieve available jobs", e);
        }
    }

    /**
     * Get available data types for dropdown lists.
     */
    @GetMapping("/data-types")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> getDataTypes() {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminSystemRequest dataTypesRequest = AdminSystemRequest.builder()
            .operation("get_data_types")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateDataRetentionRequest(Map.of());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid data types request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            List<Map<String, String>> dataTypes = List.of(
                Map.of("value", "LOG_FILES", "label", "Log Files", "description", "Manages log file archiving and deletion"),
                Map.of("value", "SYSTEM_LOGS", "label", "System Logs", "description", "Manages system_logs table cleanup"),
                Map.of("value", "TRANSACTION_LOGS", "label", "Transaction Logs", "description", "Manages transaction_logs table cleanup"),
                Map.of("value", "SCHEDULE", "label", "Schedule", "description", "Defines when retention cleanup should run")
            );
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.builder()
                .operation("get_data_types")
                .status("SUCCESS")
                .totalRecords(dataTypes.size())
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get data types for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to retrieve data types", e);
        }
    }

    /**
     * Get default configurations for initialization.
     */
    @GetMapping("/defaults")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> getDefaultConfigurations() {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminSystemRequest defaultsRequest = AdminSystemRequest.builder()
            .operation("get_default_configs")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateDataRetentionRequest(Map.of());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid default configurations request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            DataRetentionConfig logFiles = new DataRetentionConfig(
                DataRetentionConfig.DataType.LOG_FILES,
                "Log Files Retention",
                "Archive log files after 7 days, delete after 30 days",
                7,
                true
            );
            logFiles.setArchiveDays(30);

            DataRetentionConfig systemLogs = new DataRetentionConfig(
                DataRetentionConfig.DataType.SYSTEM_LOGS,
                "System Logs Cleanup",
                "Delete system log records older than 90 days",
                90,
                true
            );

            DataRetentionConfig transactionLogs = new DataRetentionConfig(
                DataRetentionConfig.DataType.TRANSACTION_LOGS,
                "Transaction Logs Cleanup",
                "Delete transaction log records older than 180 days",
                180,
                true
            );

            DataRetentionConfig schedule = new DataRetentionConfig(
                DataRetentionConfig.DataType.SCHEDULE,
                "Daily Schedule",
                "Run data retention daily at 2:00 AM",
                0,
                true
            );
            schedule.setScheduleCron("0 0 2 * * ?");

            List<DataRetentionConfig> defaults = List.of(logFiles, systemLogs, transactionLogs, schedule);
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.builder()
                .operation("get_default_configs")
                .status("SUCCESS")
                .totalRecords(defaults.size())
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get default configurations for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to retrieve default configurations", e);
        }
    }
}