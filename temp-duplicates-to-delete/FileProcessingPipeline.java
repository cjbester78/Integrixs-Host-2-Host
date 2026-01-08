package com.integrixs.adapters.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * File processing pipeline implementing the Pipeline pattern for file operations.
 * Provides structured, extensible file processing with immutable results.
 * Follows OOP principles with clear separation of concerns and type safety.
 */
@Service
public class FileProcessingPipeline {
    
    private static final Logger logger = LoggerFactory.getLogger(FileProcessingPipeline.class);
    
    /**
     * Execute a file processing pipeline with multiple stages.
     * 
     * @param files list of files to process
     * @param config processing configuration
     * @param stages list of processing stages to execute
     * @return immutable pipeline execution result
     */
    public FileProcessingResult processPipeline(List<Path> files, Map<String, Object> config,
                                              List<ProcessingStage> stages) {
        
        logger.info("Starting file processing pipeline with {} files and {} stages", 
                   files.size(), stages.size());
        
        LocalDateTime startTime = LocalDateTime.now();
        List<FileProcessingStageResult> stageResults = new ArrayList<>();
        List<FileProcessingItemResult> itemResults = new ArrayList<>();
        
        int totalProcessed = 0;
        int totalSuccessful = 0;
        int totalFailed = 0;
        long totalBytes = 0;
        List<String> pipelineErrors = new ArrayList<>();
        
        try {
            // Process each file through all stages
            for (Path file : files) {
                FileProcessingItemResult itemResult = processFileItem(file, config, stages);
                itemResults.add(itemResult);
                
                totalProcessed++;
                if (itemResult.isSuccessful()) {
                    totalSuccessful++;
                    totalBytes += itemResult.getBytesProcessed();
                } else {
                    totalFailed++;
                    if (itemResult.isCriticalFailure()) {
                        pipelineErrors.add("Critical failure processing " + file.getFileName() + ": " + 
                                         itemResult.getErrorMessage().orElse("Unknown error"));
                    }
                }
            }
            
            // Collect stage-level results
            for (ProcessingStage stage : stages) {
                if (stage instanceof ProcessingStage.StatefulStage) {
                    FileProcessingStageResult stageResult = ((ProcessingStage.StatefulStage) stage).getStageResult();
                    if (stageResult != null) {
                        stageResults.add(stageResult);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Pipeline execution failed: {}", e.getMessage(), e);
            pipelineErrors.add("Pipeline execution error: " + e.getMessage());
        }
        
        LocalDateTime endTime = LocalDateTime.now();
        
        return new FileProcessingResult(
            totalProcessed, totalSuccessful, totalFailed, totalBytes,
            startTime, endTime, itemResults, stageResults, pipelineErrors,
            totalFailed == 0 && pipelineErrors.isEmpty()
        );
    }
    
    /**
     * Process a single file through all pipeline stages.
     */
    private FileProcessingItemResult processFileItem(Path file, Map<String, Object> config,
                                                   List<ProcessingStage> stages) {
        
        String fileName = file.getFileName().toString();
        LocalDateTime itemStartTime = LocalDateTime.now();
        List<String> stageResults = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        try {
            long fileSize = java.nio.file.Files.size(file);
            ProcessingContext context = new ProcessingContext(file, config);
            
            // Execute each stage in sequence
            for (ProcessingStage stage : stages) {
                try {
                    ProcessingStageResult stageResult = stage.process(context);
                    
                    if (stageResult.isSuccessful()) {
                        stageResults.add(stage.getStageName() + ": SUCCESS");
                        
                        // Update context with stage results
                        context = context.withStageResult(stage.getStageName(), stageResult);
                        
                        if (stageResult.hasWarnings()) {
                            warnings.addAll(stageResult.getWarnings());
                        }
                        
                    } else {
                        String error = stage.getStageName() + ": FAILED - " + 
                                     stageResult.getErrorMessage().orElse("Unknown error");
                        stageResults.add(error);
                        
                        // Check if this is a critical stage failure
                        if (stageResult.isCriticalFailure() || stage.isCriticalStage()) {
                            return FileProcessingItemResult.failed(fileName, fileSize, 
                                                                 itemStartTime, LocalDateTime.now(),
                                                                 stageResults, warnings, 
                                                                 stageResult.getErrorMessage().orElse("Stage failure"),
                                                                 true);
                        }
                    }
                    
                } catch (Exception stageException) {
                    String error = stage.getStageName() + ": EXCEPTION - " + stageException.getMessage();
                    stageResults.add(error);
                    
                    if (stage.isCriticalStage()) {
                        return FileProcessingItemResult.failed(fileName, fileSize,
                                                             itemStartTime, LocalDateTime.now(),
                                                             stageResults, warnings,
                                                             "Stage exception: " + stageException.getMessage(),
                                                             true);
                    } else {
                        warnings.add("Non-critical stage failed: " + error);
                    }
                }
            }
            
            // All stages completed successfully
            return FileProcessingItemResult.successful(fileName, fileSize, 
                                                     itemStartTime, LocalDateTime.now(),
                                                     stageResults, warnings, context.getProcessingDetails());
            
        } catch (Exception e) {
            logger.error("Failed to process file {}: {}", fileName, e.getMessage(), e);
            return FileProcessingItemResult.failed(fileName, 0L, 
                                                 itemStartTime, LocalDateTime.now(),
                                                 stageResults, warnings,
                                                 "File processing error: " + e.getMessage(), false);
        }
    }
    
    /**
     * Create a standard file processing pipeline for common operations.
     */
    public static List<ProcessingStage> createStandardPipeline(FileValidationStrategy validationStrategy) {
        List<ProcessingStage> stages = new ArrayList<>();
        
        // Stage 1: File Validation
        stages.add(new FileValidationStage(validationStrategy));
        
        // Stage 2: File Reading
        stages.add(new FileReadingStage());
        
        // Stage 3: Content Processing (can be customized)
        stages.add(new ContentProcessingStage());
        
        return stages;
    }
    
    /**
     * Processing context passed between stages
     */
    public static class ProcessingContext {
        private final Path filePath;
        private final Map<String, Object> config;
        private final Map<String, Object> stageResults;
        private final Map<String, Object> processingDetails;
        
        public ProcessingContext(Path filePath, Map<String, Object> config) {
            this.filePath = filePath;
            this.config = Map.copyOf(config);
            this.stageResults = Map.of();
            this.processingDetails = Map.of();
        }
        
        private ProcessingContext(Path filePath, Map<String, Object> config,
                                Map<String, Object> stageResults, Map<String, Object> processingDetails) {
            this.filePath = filePath;
            this.config = config;
            this.stageResults = stageResults;
            this.processingDetails = processingDetails;
        }
        
        public Path getFilePath() { return filePath; }
        public Map<String, Object> getConfig() { return config; }
        public Map<String, Object> getStageResults() { return stageResults; }
        public Map<String, Object> getProcessingDetails() { return processingDetails; }
        
        public ProcessingContext withStageResult(String stageName, ProcessingStageResult result) {
            Map<String, Object> newResults = new java.util.HashMap<>(this.stageResults);
            newResults.put(stageName, result);
            
            Map<String, Object> newDetails = new java.util.HashMap<>(this.processingDetails);
            newDetails.putAll(result.getProcessingData());
            
            return new ProcessingContext(filePath, config, Map.copyOf(newResults), Map.copyOf(newDetails));
        }
    }
    
    /**
     * Interface for pipeline processing stages
     */
    public interface ProcessingStage {
        
        /**
         * Process a file in this stage.
         */
        ProcessingStageResult process(ProcessingContext context);
        
        /**
         * Get the stage name for identification.
         */
        String getStageName();
        
        /**
         * Check if this is a critical stage (failure stops pipeline).
         */
        default boolean isCriticalStage() {
            return false;
        }
        
        /**
         * Marker interface for stages that maintain state
         */
        interface StatefulStage extends ProcessingStage {
            FileProcessingStageResult getStageResult();
        }
    }
    
    /**
     * Result from a single processing stage
     */
    public static class ProcessingStageResult {
        private final boolean successful;
        private final Optional<String> errorMessage;
        private final List<String> warnings;
        private final boolean criticalFailure;
        private final Map<String, Object> processingData;
        
        private ProcessingStageResult(boolean successful, String errorMessage, List<String> warnings,
                                    boolean criticalFailure, Map<String, Object> processingData) {
            this.successful = successful;
            this.errorMessage = Optional.ofNullable(errorMessage);
            this.warnings = warnings != null ? List.copyOf(warnings) : List.of();
            this.criticalFailure = criticalFailure;
            this.processingData = processingData != null ? Map.copyOf(processingData) : Map.of();
        }
        
        public boolean isSuccessful() { return successful; }
        public Optional<String> getErrorMessage() { return errorMessage; }
        public List<String> getWarnings() { return warnings; }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
        public boolean isCriticalFailure() { return criticalFailure; }
        public Map<String, Object> getProcessingData() { return processingData; }
        
        public static ProcessingStageResult success(Map<String, Object> processingData) {
            return new ProcessingStageResult(true, null, null, false, processingData);
        }
        
        public static ProcessingStageResult successWithWarnings(List<String> warnings, 
                                                               Map<String, Object> processingData) {
            return new ProcessingStageResult(true, null, warnings, false, processingData);
        }
        
        public static ProcessingStageResult failure(String errorMessage, boolean critical) {
            return new ProcessingStageResult(false, errorMessage, null, critical, null);
        }
        
        public static ProcessingStageResult failureWithWarnings(String errorMessage, List<String> warnings, 
                                                               boolean critical) {
            return new ProcessingStageResult(false, errorMessage, warnings, critical, null);
        }
    }
}