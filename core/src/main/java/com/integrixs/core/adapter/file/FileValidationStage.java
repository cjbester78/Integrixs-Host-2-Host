package com.integrixs.core.adapter.file;

import com.integrixs.core.adapter.file.FileProcessingPipeline.ProcessingContext;
import com.integrixs.core.adapter.file.FileProcessingPipeline.ProcessingStage;
import com.integrixs.core.adapter.file.FileProcessingPipeline.ProcessingStageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * File validation stage for processing pipeline.
 * Validates files according to configured validation strategy.
 * Follows OOP principles with immutable results and proper encapsulation.
 */
public class FileValidationStage implements ProcessingStage, ProcessingStage.StatefulStage {
    
    private static final Logger logger = LoggerFactory.getLogger(FileValidationStage.class);
    
    private final FileValidationStrategy validationStrategy;
    private FileProcessingStageResult stageResult;
    private int filesProcessed = 0;
    private int filesSuccessful = 0;
    private int filesFailed = 0;
    private LocalDateTime stageStartTime;
    private LocalDateTime stageEndTime;
    
    public FileValidationStage(FileValidationStrategy validationStrategy) {
        this.validationStrategy = validationStrategy;
        this.stageStartTime = LocalDateTime.now();
    }
    
    @Override
    public String getStageName() {
        return "FILE_VALIDATION";
    }
    
    @Override
    public boolean isCriticalStage() {
        return true; // Validation failures should stop pipeline
    }
    
    @Override
    public ProcessingStageResult process(ProcessingContext context) {
        if (stageStartTime == null) {
            stageStartTime = LocalDateTime.now();
        }
        
        try {
            FileValidationStrategy.FileValidationResult validationResult = 
                validationStrategy.validateFile(context.getFilePath(), context.getConfig());
            
            filesProcessed++;
            
            Map<String, Object> processingData = new HashMap<>();
            processingData.put("validationStrategy", validationStrategy.getStrategyName());
            processingData.put("validationCategory", validationResult.getCategory().name());
            processingData.put("validationDetails", validationResult.getValidationDetails());
            
            if (validationResult.isValid()) {
                filesSuccessful++;
                
                if (validationResult.hasWarnings()) {
                    logger.debug("File {} validated with warnings: {}", 
                               validationResult.getFileName(), validationResult.getWarnings());
                    return ProcessingStageResult.successWithWarnings(validationResult.getWarnings(), 
                                                                   processingData);
                } else {
                    logger.debug("File {} validated successfully", validationResult.getFileName());
                    return ProcessingStageResult.success(processingData);
                }
                
            } else {
                filesFailed++;
                String errorMessage = "Validation failed: " + 
                                    String.join(", ", validationResult.getValidationMessages());
                
                logger.warn("File {} validation failed: {}", 
                           validationResult.getFileName(), errorMessage);
                
                return ProcessingStageResult.failure(errorMessage, true);
            }
            
        } catch (Exception e) {
            filesFailed++;
            String errorMessage = "Validation stage error: " + e.getMessage();
            logger.error("Validation stage failed for file {}: {}", 
                        context.getFilePath().getFileName(), e.getMessage(), e);
            
            return ProcessingStageResult.failure(errorMessage, true);
        } finally {
            stageEndTime = LocalDateTime.now();
        }
    }
    
    @Override
    public FileProcessingStageResult getStageResult() {
        if (stageResult == null && stageEndTime != null) {
            stageResult = new FileProcessingStageResult(
                getStageName(),
                filesProcessed,
                filesSuccessful, 
                filesFailed,
                stageStartTime,
                stageEndTime,
                null, // No stage-level warnings for now
                null, // No stage-level errors for now
                Map.of(
                    "validationStrategy", validationStrategy.getStrategyName(),
                    "validationDescription", validationStrategy.getValidationDescription()
                )
            );
        }
        return stageResult;
    }
}

/**
 * File reading stage for processing pipeline.
 * Reads file content into memory for processing.
 */
class FileReadingStage implements ProcessingStage, ProcessingStage.StatefulStage {
    
    private static final Logger logger = LoggerFactory.getLogger(FileReadingStage.class);
    
    private FileProcessingStageResult stageResult;
    private int filesProcessed = 0;
    private int filesSuccessful = 0;
    private int filesFailed = 0;
    private LocalDateTime stageStartTime;
    private LocalDateTime stageEndTime;
    private long totalBytesRead = 0;
    
    @Override
    public String getStageName() {
        return "FILE_READING";
    }
    
    @Override
    public boolean isCriticalStage() {
        return true; // Reading failures should stop pipeline
    }
    
    @Override
    public ProcessingStageResult process(ProcessingContext context) {
        if (stageStartTime == null) {
            stageStartTime = LocalDateTime.now();
        }
        
        try {
            byte[] fileContent = java.nio.file.Files.readAllBytes(context.getFilePath());
            long fileSize = fileContent.length;
            
            filesProcessed++;
            filesSuccessful++;
            totalBytesRead += fileSize;
            
            Map<String, Object> processingData = new HashMap<>();
            processingData.put("fileContent", fileContent);
            processingData.put("fileSize", fileSize);
            processingData.put("bytesRead", fileSize);
            
            logger.debug("Successfully read file {} ({} bytes)", 
                        context.getFilePath().getFileName(), fileSize);
            
            return ProcessingStageResult.success(processingData);
            
        } catch (Exception e) {
            filesFailed++;
            String errorMessage = "File reading failed: " + e.getMessage();
            logger.error("Failed to read file {}: {}", 
                        context.getFilePath().getFileName(), e.getMessage(), e);
            
            return ProcessingStageResult.failure(errorMessage, true);
        } finally {
            stageEndTime = LocalDateTime.now();
        }
    }
    
    @Override
    public FileProcessingStageResult getStageResult() {
        if (stageResult == null && stageEndTime != null) {
            stageResult = new FileProcessingStageResult(
                getStageName(),
                filesProcessed,
                filesSuccessful,
                filesFailed,
                stageStartTime,
                stageEndTime,
                null,
                null,
                Map.of(
                    "totalBytesRead", totalBytesRead,
                    "averageFileSize", filesProcessed > 0 ? totalBytesRead / filesProcessed : 0
                )
            );
        }
        return stageResult;
    }
}

/**
 * Content processing stage for processing pipeline.
 * Performs basic content processing and validation.
 */
class ContentProcessingStage implements ProcessingStage, ProcessingStage.StatefulStage {
    
    private static final Logger logger = LoggerFactory.getLogger(ContentProcessingStage.class);
    
    private FileProcessingStageResult stageResult;
    private int filesProcessed = 0;
    private int filesSuccessful = 0;
    private int filesFailed = 0;
    private LocalDateTime stageStartTime;
    private LocalDateTime stageEndTime;
    
    @Override
    public String getStageName() {
        return "CONTENT_PROCESSING";
    }
    
    @Override
    public boolean isCriticalStage() {
        return false; // Content processing failures are not critical by default
    }
    
    @Override
    public ProcessingStageResult process(ProcessingContext context) {
        if (stageStartTime == null) {
            stageStartTime = LocalDateTime.now();
        }
        
        try {
            // Get file content from previous stage
            Object stageResults = context.getStageResults().get("FILE_READING");
            if (!(stageResults instanceof ProcessingStageResult)) {
                throw new IllegalStateException("FILE_READING stage result not found");
            }
            
            ProcessingStageResult readingResult = (ProcessingStageResult) stageResults;
            byte[] fileContent = (byte[]) readingResult.getProcessingData().get("fileContent");
            
            if (fileContent == null) {
                throw new IllegalStateException("File content not available from reading stage");
            }
            
            filesProcessed++;
            
            // Perform basic content processing
            Map<String, Object> processingData = new HashMap<>();
            processingData.put("contentLength", fileContent.length);
            processingData.put("contentHash", java.util.Arrays.hashCode(fileContent));
            
            // Check for empty content
            if (fileContent.length == 0) {
                logger.warn("File {} is empty", context.getFilePath().getFileName());
                return ProcessingStageResult.successWithWarnings(
                    java.util.List.of("File is empty (0 bytes)"), processingData);
            }
            
            // Basic content analysis
            boolean isTextContent = isLikelyTextContent(fileContent);
            processingData.put("isTextContent", isTextContent);
            
            if (isTextContent) {
                String content = new String(fileContent);
                processingData.put("lineCount", content.split("\n").length);
                processingData.put("characterCount", content.length());
            }
            
            filesSuccessful++;
            
            logger.debug("Content processing completed for file {} ({} bytes, text: {})", 
                        context.getFilePath().getFileName(), fileContent.length, isTextContent);
            
            return ProcessingStageResult.success(processingData);
            
        } catch (Exception e) {
            filesFailed++;
            String errorMessage = "Content processing failed: " + e.getMessage();
            logger.warn("Content processing failed for file {}: {}", 
                       context.getFilePath().getFileName(), e.getMessage(), e);
            
            return ProcessingStageResult.failure(errorMessage, false);
        } finally {
            stageEndTime = LocalDateTime.now();
        }
    }
    
    @Override
    public FileProcessingStageResult getStageResult() {
        if (stageResult == null && stageEndTime != null) {
            stageResult = new FileProcessingStageResult(
                getStageName(),
                filesProcessed,
                filesSuccessful,
                filesFailed,
                stageStartTime,
                stageEndTime,
                null,
                null,
                Map.of(
                    "processingType", "basic_content_analysis"
                )
            );
        }
        return stageResult;
    }
    
    /**
     * Simple heuristic to determine if content is likely text
     */
    private boolean isLikelyTextContent(byte[] content) {
        if (content.length == 0) return true;
        
        int textCharacters = 0;
        int sampleSize = Math.min(1024, content.length); // Sample first 1KB
        
        for (int i = 0; i < sampleSize; i++) {
            byte b = content[i];
            // Count printable ASCII characters and common whitespace
            if ((b >= 32 && b <= 126) || b == '\n' || b == '\r' || b == '\t') {
                textCharacters++;
            }
        }
        
        return (double) textCharacters / sampleSize > 0.7; // 70% threshold
    }
}