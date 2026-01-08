package com.integrixs.backend.service;

import com.integrixs.shared.model.DataRetentionConfig;
import com.integrixs.core.repository.DataRetentionConfigRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Data Retention Service - Manages automated cleanup of log files and database tables
 * 
 * Features:
 * - Configurable retention periods for different data types
 * - Spring @Scheduled execution with configurable cron expressions
 * - File-based log cleanup with compression and archiving
 * - Database table cleanup for system_logs and transaction_logs
 * - Web UI configuration and manual execution
 * - Comprehensive logging and monitoring
 */
@Service
@ConditionalOnProperty(value = "integrix.data-retention.enabled", havingValue = "true", matchIfMissing = true)
public class DataRetentionService {

    private static final Logger log = Logger.getLogger(DataRetentionService.class.getName());

    private final DataRetentionConfigRepository retentionConfigRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TaskScheduler taskScheduler;

    @Value("${logging.file.path:logs}")
    private String logPath;

    private volatile LocalDateTime lastExecution;
    private volatile String lastExecutionStatus = "Never executed";
    private volatile AtomicInteger lastFilesProcessed = new AtomicInteger(0);
    private volatile AtomicLong lastDatabaseRecordsDeleted = new AtomicLong(0);

    // Track scheduled tasks for dynamic management
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public DataRetentionService(DataRetentionConfigRepository retentionConfigRepository, 
                               JdbcTemplate jdbcTemplate,
                               TaskScheduler taskScheduler) {
        this.retentionConfigRepository = retentionConfigRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.taskScheduler = taskScheduler;
    }

    /**
     * Initialize dynamic scheduling on application startup
     */
    @EventListener(ContextRefreshedEvent.class)
    public void initializeDynamicScheduling() {
        log.info("Initializing dynamic data retention scheduling...");
        refreshScheduledTasks();
    }

    /**
     * Refresh scheduled tasks based on current database configuration
     */
    public void refreshScheduledTasks() {
        // Cancel all existing scheduled tasks
        scheduledTasks.values().forEach(task -> task.cancel(false));
        scheduledTasks.clear();

        // Load all SCHEDULE configurations from database
        List<DataRetentionConfig> scheduleConfigs = retentionConfigRepository.findByDataTypeAndEnabledTrue(
            DataRetentionConfig.DataType.SCHEDULE
        );

        for (DataRetentionConfig config : scheduleConfigs) {
            if (config.getScheduleCron() != null && !config.getScheduleCron().trim().isEmpty()) {
                scheduleRetentionTask(config);
            }
        }

        log.info("Initialized " + scheduledTasks.size() + " dynamic retention schedules");
    }

    /**
     * Schedule a retention task based on configuration
     */
    private void scheduleRetentionTask(DataRetentionConfig config) {
        try {
            CronTrigger cronTrigger = new CronTrigger(config.getScheduleCron());
            
            Runnable task = () -> {
                log.info("Executing scheduled data retention: " + config.getName() + " (" + config.getScheduleCron() + ")");
                executeDataRetentionForConfig(config);
            };

            ScheduledFuture<?> scheduledTask = taskScheduler.schedule(task, cronTrigger);
            scheduledTasks.put(config.getId().toString(), scheduledTask);
            
            log.info("Scheduled retention task: " + config.getName() + " with cron: " + config.getScheduleCron());
        } catch (Exception e) {
            log.severe("Failed to schedule retention task for " + config.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Execute data retention for a specific configuration
     */
    private void executeDataRetentionForConfig(DataRetentionConfig scheduleConfig) {
        try {
            executeDataRetention();
        } catch (Exception e) {
            log.severe("Error during scheduled data retention execution: " + e.getMessage());
        }
    }

    /**
     * Main retention execution method
     */
    public void executeDataRetention() {
        log.info("=== STARTING DATA RETENTION EXECUTION ===");
        
        try {
            lastExecution = LocalDateTime.now();
            lastExecutionStatus = "In Progress";
            
            // Get active retention configurations
            List<DataRetentionConfig> configs = retentionConfigRepository.findByEnabledTrue();
            
            if (configs.isEmpty()) {
                log.warning("No active data retention configurations found. Skipping execution.");
                lastExecutionStatus = "Skipped - No active configurations";
                return;
            }

            int totalFilesProcessed = 0;
            long totalDbRecordsDeleted = 0;

            // Process each retention configuration
            for (DataRetentionConfig config : configs) {
                switch (config.getDataType()) {
                    case LOG_FILES:
                        totalFilesProcessed += processLogFileRetention(config);
                        break;
                    case SYSTEM_LOGS:
                        totalDbRecordsDeleted += processDatabaseRetention(config, "system_logs");
                        break;
                    case TRANSACTION_LOGS:
                        totalDbRecordsDeleted += processDatabaseRetention(config, "transaction_logs");
                        break;
                    case SCHEDULE:
                        // Skip - this is just configuration
                        break;
                    default:
                        log.warning("Unknown data type: " + config.getDataType());
                }
            }

            lastFilesProcessed.set(totalFilesProcessed);
            lastDatabaseRecordsDeleted.set(totalDbRecordsDeleted);
            lastExecutionStatus = String.format("Completed - Files: %d, DB Records: %d", 
                totalFilesProcessed, totalDbRecordsDeleted);

            log.info("=== DATA RETENTION EXECUTION COMPLETED ===");
            log.info("Files processed: " + totalFilesProcessed);
            log.info("Database records deleted: " + totalDbRecordsDeleted);

        } catch (Exception e) {
            log.severe("Error during data retention execution: " + e.getMessage());
            lastExecutionStatus = "Failed: " + e.getMessage();
        }
    }

    /**
     * Process log file retention
     */
    private int processLogFileRetention(DataRetentionConfig config) {
        log.info("Processing log file retention - Retain: " + config.getRetentionDays() + 
                " days, Archive: " + config.getArchiveDays() + " days");

        AtomicInteger filesProcessed = new AtomicInteger(0);
        Path logsDir = Paths.get(logPath);

        if (!Files.exists(logsDir)) {
            log.warning("Logs directory does not exist: " + logPath);
            return 0;
        }

        try {
            // Step 1: Archive logs older than retention period
            archiveOldLogFiles(logsDir, config.getRetentionDays(), filesProcessed);
            
            // Step 2: Delete archived logs older than archive period
            deleteOldArchivedLogs(logsDir, config.getArchiveDays(), filesProcessed);

        } catch (IOException e) {
            log.severe("Error processing log file retention: " + e.getMessage());
        }

        return filesProcessed.get();
    }

    /**
     * Archive log files older than retention days
     */
    private void archiveOldLogFiles(Path logsDir, int retentionDays, AtomicInteger counter) throws IOException {
        Path backupDir = logsDir.resolve("backup");
        Files.createDirectories(backupDir);

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);

        Files.walkFileTree(logsDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getParent().equals(backupDir)) {
                    return FileVisitResult.CONTINUE; // Skip backup directory
                }

                String fileName = file.getFileName().toString();
                if (fileName.endsWith(".log")) {
                    LocalDateTime fileModTime = LocalDateTime.ofInstant(
                        attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());

                    if (fileModTime.isBefore(cutoffDate)) {
                        // Move to backup directory
                        Path backupFile = backupDir.resolve(fileName);
                        Files.move(file, backupFile, StandardCopyOption.REPLACE_EXISTING);
                        counter.incrementAndGet();
                        log.info("Archived log file: " + fileName + " -> " + backupFile.getFileName());
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Delete archived log files older than archive days
     */
    private void deleteOldArchivedLogs(Path logsDir, int archiveDays, AtomicInteger counter) throws IOException {
        Path backupDir = logsDir.resolve("backup");
        if (!Files.exists(backupDir)) {
            return;
        }

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(archiveDays);

        Files.walkFileTree(backupDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                LocalDateTime fileModTime = LocalDateTime.ofInstant(
                    attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());

                if (fileModTime.isBefore(cutoffDate)) {
                    Files.delete(file);
                    counter.incrementAndGet();
                    log.info("Deleted old archived log: " + file.getFileName());
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Process database table retention
     */
    private long processDatabaseRetention(DataRetentionConfig config, String tableName) {
        log.info("Processing database retention for table: " + tableName + 
                " - Retain: " + config.getRetentionDays() + " days");

        try {
            String deleteSql = String.format(
                "DELETE FROM %s WHERE created_at < NOW() - INTERVAL '%d days'",
                tableName, config.getRetentionDays()
            );

            int deletedRows = jdbcTemplate.update(deleteSql);
            log.info("Deleted " + deletedRows + " records from table: " + tableName);
            return deletedRows;

        } catch (Exception e) {
            log.severe("Error deleting old records from table: " + tableName + " - " + e.getMessage());
            return 0;
        }
    }

    /**
     * Manual execution method for web UI
     */
    public void executeManualRetention() {
        log.info("Manual data retention execution triggered");
        executeDataRetention();
    }

    /**
     * Get the current schedule cron expression from configuration
     */
    public String getScheduleCron() {
        return retentionConfigRepository.getScheduleCron();
    }

    /**
     * Validate cron expression
     */
    public boolean isValidCronExpression(String cronExpression) {
        try {
            CronExpression.parse(cronExpression);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get execution status for monitoring
     */
    public DataRetentionStatus getExecutionStatus() {
        DataRetentionStatus status = new DataRetentionStatus();
        status.setLastExecution(lastExecution);
        status.setStatus(lastExecutionStatus);
        status.setFilesProcessed(lastFilesProcessed.get());
        status.setDatabaseRecordsDeleted(lastDatabaseRecordsDeleted.get());
        status.setNextExecution(getNextExecutionTime());
        return status;
    }

    /**
     * Calculate next execution time based on cron expression
     */
    private LocalDateTime getNextExecutionTime() {
        try {
            // Find the earliest next execution time from all scheduled tasks
            LocalDateTime earliestNext = null;
            
            List<DataRetentionConfig> scheduleConfigs = retentionConfigRepository.findByDataTypeAndEnabledTrue(
                DataRetentionConfig.DataType.SCHEDULE
            );
            
            for (DataRetentionConfig config : scheduleConfigs) {
                if (config.getScheduleCron() != null && !config.getScheduleCron().trim().isEmpty()) {
                    try {
                        CronExpression cron = CronExpression.parse(config.getScheduleCron());
                        LocalDateTime next = cron.next(LocalDateTime.now());
                        if (next != null && (earliestNext == null || next.isBefore(earliestNext))) {
                            earliestNext = next;
                        }
                    } catch (Exception e) {
                        log.warning("Error parsing cron for config " + config.getName() + ": " + e.getMessage());
                    }
                }
            }
            
            return earliestNext;
        } catch (Exception e) {
            log.warning("Error calculating next execution time: " + e.getMessage());
            return null;
        }
    }

    /**
     * Cleanup scheduled tasks on shutdown
     */
    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up scheduled data retention tasks...");
        scheduledTasks.values().forEach(task -> task.cancel(false));
        scheduledTasks.clear();
    }

    /**
     * Get information about currently scheduled tasks
     */
    public Map<String, String> getScheduledTasksInfo() {
        Map<String, String> info = new ConcurrentHashMap<>();
        scheduledTasks.forEach((id, task) -> {
            info.put(id, task.isDone() ? "COMPLETED" : task.isCancelled() ? "CANCELLED" : "RUNNING");
        });
        return info;
    }

    /**
     * Data retention status DTO
     */
    public static class DataRetentionStatus {
        private LocalDateTime lastExecution;
        private String status;
        private int filesProcessed;
        private long databaseRecordsDeleted;
        private LocalDateTime nextExecution;

        // Constructors
        public DataRetentionStatus() {}

        // Getters and Setters
        public LocalDateTime getLastExecution() {
            return lastExecution;
        }

        public void setLastExecution(LocalDateTime lastExecution) {
            this.lastExecution = lastExecution;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public int getFilesProcessed() {
            return filesProcessed;
        }

        public void setFilesProcessed(int filesProcessed) {
            this.filesProcessed = filesProcessed;
        }

        public long getDatabaseRecordsDeleted() {
            return databaseRecordsDeleted;
        }

        public void setDatabaseRecordsDeleted(long databaseRecordsDeleted) {
            this.databaseRecordsDeleted = databaseRecordsDeleted;
        }

        public LocalDateTime getNextExecution() {
            return nextExecution;
        }

        public void setNextExecution(LocalDateTime nextExecution) {
            this.nextExecution = nextExecution;
        }
    }
}