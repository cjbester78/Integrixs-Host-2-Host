package com.integrixs.shared.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FlowExecutionStep entity representing detailed step-by-step execution tracking
 * Provides granular visibility into each step of a flow execution
 */
public class FlowExecutionStep {
    
    private UUID id;
    private UUID executionId;
    
    // Step identification
    private String stepId; // From flow definition
    private String stepName;
    private StepType stepType;
    private Integer stepOrder; // Execution sequence
    
    // Step configuration (snapshot at execution time)
    private Map<String, Object> stepConfiguration;
    
    // Execution status
    private StepStatus stepStatus;
    
    // Timing
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long durationMs;
    
    // Step data
    private Map<String, Object> inputData; // Data received by this step
    private Map<String, Object> outputData; // Data produced by this step
    
    // File tracking for this step
    private List<String> inputFiles; // Array of file paths processed
    private List<String> outputFiles; // Array of files produced
    private Integer filesCount;
    private Long bytesProcessed;
    
    // Error information
    private String errorMessage;
    private Map<String, Object> errorDetails;
    private Integer exitCode; // For utility steps
    
    // Performance metrics
    private BigDecimal cpuUsagePercent;
    private Integer memoryUsageMb;
    
    // Correlation
    private UUID correlationId;
    
    // Enums
    public enum StepType {
        ADAPTER_SENDER("Sender adapter step for sending data"),
        ADAPTER_RECEIVER("Receiver adapter step for receiving data"),
        UTILITY("Utility processing step (PGP, ZIP, etc.)"),
        DECISION("Decision/branching step"),
        SPLIT("Split data into parallel paths"),
        MERGE("Merge parallel paths back together"),
        WAIT("Wait/delay step"),
        NOTIFICATION("Send notification step");
        
        private final String description;
        
        StepType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean isAdapter() {
            return this == ADAPTER_SENDER || this == ADAPTER_RECEIVER;
        }
        
        public boolean isUtility() {
            return this == UTILITY;
        }
        
        public boolean isControl() {
            return this == DECISION || this == SPLIT || this == MERGE || this == WAIT;
        }
    }
    
    public enum StepStatus {
        PENDING("Step is waiting to be executed"),
        RUNNING("Step is currently executing"),
        COMPLETED("Step completed successfully"),
        FAILED("Step failed with errors"),
        SKIPPED("Step was skipped due to conditions"),
        CANCELLED("Step was cancelled"),
        TIMEOUT("Step exceeded timeout limit");
        
        private final String description;
        
        StepStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean isTerminal() {
            return this == COMPLETED || this == FAILED || this == SKIPPED || this == CANCELLED || this == TIMEOUT;
        }
        
        public boolean isActive() {
            return this == PENDING || this == RUNNING;
        }
        
        public boolean isSuccessful() {
            return this == COMPLETED || this == SKIPPED;
        }
    }
    
    // Constructors
    public FlowExecutionStep() {
        this.id = UUID.randomUUID();
        this.stepStatus = StepStatus.PENDING;
        this.filesCount = 0;
        this.bytesProcessed = 0L;
        this.durationMs = 0L;
    }
    
    public FlowExecutionStep(UUID executionId, String stepId, String stepName, StepType stepType, Integer stepOrder) {
        this();
        this.executionId = executionId;
        this.stepId = stepId;
        this.stepName = stepName;
        this.stepType = stepType;
        this.stepOrder = stepOrder;
    }
    
    // Business logic methods
    public void start() {
        if (this.stepStatus == StepStatus.PENDING) {
            this.stepStatus = StepStatus.RUNNING;
            this.startedAt = LocalDateTime.now();
        }
    }
    
    public void complete() {
        if (this.stepStatus == StepStatus.RUNNING) {
            this.stepStatus = StepStatus.COMPLETED;
            this.completedAt = LocalDateTime.now();
            calculateDuration();
        }
    }
    
    public void fail(String errorMessage) {
        fail(errorMessage, null, null);
    }
    
    public void fail(String errorMessage, Map<String, Object> errorDetails, Integer exitCode) {
        this.stepStatus = StepStatus.FAILED;
        this.errorMessage = errorMessage;
        this.errorDetails = errorDetails;
        this.exitCode = exitCode;
        this.completedAt = LocalDateTime.now();
        calculateDuration();
    }
    
    public void skip(String reason) {
        this.stepStatus = StepStatus.SKIPPED;
        this.errorMessage = "Skipped: " + reason;
        this.completedAt = LocalDateTime.now();
        calculateDuration();
    }
    
    public void cancel() {
        if (this.stepStatus.isActive()) {
            this.stepStatus = StepStatus.CANCELLED;
            this.completedAt = LocalDateTime.now();
            calculateDuration();
        }
    }
    
    public void timeout() {
        if (this.stepStatus.isActive()) {
            this.stepStatus = StepStatus.TIMEOUT;
            this.completedAt = LocalDateTime.now();
            calculateDuration();
        }
    }
    
    public boolean isRunning() {
        return this.stepStatus == StepStatus.RUNNING;
    }
    
    public boolean isCompleted() {
        return this.stepStatus == StepStatus.COMPLETED;
    }
    
    public boolean isFailed() {
        return this.stepStatus == StepStatus.FAILED;
    }
    
    public boolean isTerminal() {
        return this.stepStatus.isTerminal();
    }
    
    public boolean isSuccessful() {
        return this.stepStatus.isSuccessful();
    }
    
    public void addFileProcessed(String inputFile, String outputFile, long bytes) {
        if (inputFiles != null && !inputFiles.contains(inputFile)) {
            inputFiles.add(inputFile);
        }
        if (outputFile != null && outputFiles != null && !outputFiles.contains(outputFile)) {
            outputFiles.add(outputFile);
        }
        
        this.filesCount = (this.filesCount != null ? this.filesCount : 0) + 1;
        this.bytesProcessed = (this.bytesProcessed != null ? this.bytesProcessed : 0L) + bytes;
    }
    
    public void updatePerformanceMetrics(BigDecimal cpuPercent, Integer memoryMb) {
        this.cpuUsagePercent = cpuPercent;
        this.memoryUsageMb = memoryMb;
    }
    
    private void calculateDuration() {
        if (this.startedAt != null && this.completedAt != null) {
            this.durationMs = java.time.Duration.between(this.startedAt, this.completedAt).toMillis();
        }
    }
    
    public String getFormattedDuration() {
        if (durationMs == null || durationMs == 0) {
            return "0ms";
        }
        
        if (durationMs < 1000) {
            return durationMs + "ms";
        } else if (durationMs < 60000) {
            return String.format("%.1fs", durationMs / 1000.0);
        } else {
            long minutes = durationMs / 60000;
            long seconds = (durationMs % 60000) / 1000;
            return String.format("%dm %ds", minutes, seconds);
        }
    }
    
    public String getFormattedBytes() {
        if (bytesProcessed == null || bytesProcessed < 1024) {
            return bytesProcessed + " B";
        }
        if (bytesProcessed < 1024 * 1024) {
            return String.format("%.1f KB", bytesProcessed / 1024.0);
        }
        if (bytesProcessed < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytesProcessed / (1024.0 * 1024.0));
        }
        return String.format("%.1f GB", bytesProcessed / (1024.0 * 1024.0 * 1024.0));
    }
    
    public String getStatusIcon() {
        switch (stepStatus) {
            case PENDING: return "⏳";
            case RUNNING: return "🟡";
            case COMPLETED: return "✅";
            case FAILED: return "❌";
            case SKIPPED: return "⏭️";
            case CANCELLED: return "⏹️";
            case TIMEOUT: return "⏰";
            default: return "❓";
        }
    }
    
    public String getTypeIcon() {
        switch (stepType) {
            case ADAPTER_SENDER: return "📤";
            case ADAPTER_RECEIVER: return "📥";
            case UTILITY: return "⚙️";
            case DECISION: return "🔀";
            case SPLIT: return "🔀";
            case MERGE: return "🔗";
            case WAIT: return "⏱️";
            case NOTIFICATION: return "📢";
            default: return "🔧";
        }
    }
    
    public String getDisplayName() {
        return String.format("%s (%s)", stepName, stepType.name());
    }
    
    public double getThroughputMbps() {
        if (durationMs == null || durationMs == 0 || bytesProcessed == null || bytesProcessed == 0) {
            return 0.0;
        }
        
        double seconds = durationMs / 1000.0;
        double megabytes = bytesProcessed / (1024.0 * 1024.0);
        return megabytes / seconds;
    }
    
    /**
     * Calculate step progress as percentage
     * Based on step status and file processing progress
     */
    public double getProgress() {
        switch (this.stepStatus) {
            case PENDING:
                return 0.0;
            case RUNNING:
                // If we have file processing data, use it for more granular progress
                if (filesCount != null && filesCount > 0) {
                    // For running steps, estimate progress based on files processed
                    // This is a simple calculation - could be enhanced with more detailed metrics
                    return Math.min(90.0, (double) filesCount * 15.0);
                }
                return 50.0; // Generic running progress
            case COMPLETED:
                return 100.0;
            case FAILED:
            case CANCELLED:
            case TIMEOUT:
                // For failed/cancelled states, show progress up to failure point
                if (filesCount != null && filesCount > 0) {
                    return Math.min(95.0, (double) filesCount * 15.0);
                }
                return 75.0; // Generic failure progress
            case SKIPPED:
                return 100.0; // Skipped steps are considered complete
            default:
                return 0.0;
        }
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public UUID getExecutionId() {
        return executionId;
    }
    
    public void setExecutionId(UUID executionId) {
        this.executionId = executionId;
    }
    
    public String getStepId() {
        return stepId;
    }
    
    public void setStepId(String stepId) {
        this.stepId = stepId;
    }
    
    public String getStepName() {
        return stepName;
    }
    
    public void setStepName(String stepName) {
        this.stepName = stepName;
    }
    
    public StepType getStepType() {
        return stepType;
    }
    
    public void setStepType(StepType stepType) {
        this.stepType = stepType;
    }
    
    public Integer getStepOrder() {
        return stepOrder;
    }
    
    public void setStepOrder(Integer stepOrder) {
        this.stepOrder = stepOrder;
    }
    
    public Map<String, Object> getStepConfiguration() {
        return stepConfiguration;
    }
    
    public void setStepConfiguration(Map<String, Object> stepConfiguration) {
        this.stepConfiguration = stepConfiguration;
    }
    
    public StepStatus getStepStatus() {
        return stepStatus;
    }
    
    public void setStepStatus(StepStatus stepStatus) {
        this.stepStatus = stepStatus;
    }
    
    public LocalDateTime getStartedAt() {
        return startedAt;
    }
    
    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
    
    public Long getDurationMs() {
        return durationMs;
    }
    
    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }
    
    public Map<String, Object> getInputData() {
        return inputData;
    }
    
    public void setInputData(Map<String, Object> inputData) {
        this.inputData = inputData;
    }
    
    public Map<String, Object> getOutputData() {
        return outputData;
    }
    
    public void setOutputData(Map<String, Object> outputData) {
        this.outputData = outputData;
    }
    
    public List<String> getInputFiles() {
        return inputFiles;
    }
    
    public void setInputFiles(List<String> inputFiles) {
        this.inputFiles = inputFiles;
    }
    
    public List<String> getOutputFiles() {
        return outputFiles;
    }
    
    public void setOutputFiles(List<String> outputFiles) {
        this.outputFiles = outputFiles;
    }
    
    public Integer getFilesCount() {
        return filesCount;
    }
    
    public void setFilesCount(Integer filesCount) {
        this.filesCount = filesCount;
    }
    
    public Long getBytesProcessed() {
        return bytesProcessed;
    }
    
    public void setBytesProcessed(Long bytesProcessed) {
        this.bytesProcessed = bytesProcessed;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public Map<String, Object> getErrorDetails() {
        return errorDetails;
    }
    
    public void setErrorDetails(Map<String, Object> errorDetails) {
        this.errorDetails = errorDetails;
    }
    
    public Integer getExitCode() {
        return exitCode;
    }
    
    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }
    
    public BigDecimal getCpuUsagePercent() {
        return cpuUsagePercent;
    }
    
    public void setCpuUsagePercent(BigDecimal cpuUsagePercent) {
        this.cpuUsagePercent = cpuUsagePercent;
    }
    
    public Integer getMemoryUsageMb() {
        return memoryUsageMb;
    }
    
    public void setMemoryUsageMb(Integer memoryUsageMb) {
        this.memoryUsageMb = memoryUsageMb;
    }
    
    public UUID getCorrelationId() {
        return correlationId;
    }
    
    public void setCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
    }
    
    @Override
    public String toString() {
        return "FlowExecutionStep{" +
                "id=" + id +
                ", stepId='" + stepId + '\'' +
                ", stepName='" + stepName + '\'' +
                ", stepType=" + stepType +
                ", stepStatus=" + stepStatus +
                ", duration=" + getFormattedDuration() +
                ", filesProcessed=" + filesCount +
                ", bytesProcessed=" + getFormattedBytes() +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlowExecutionStep that = (FlowExecutionStep) o;
        return id != null && id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}