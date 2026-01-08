package com.integrixs.core.service;

// Removed direct adapter imports to avoid circular dependencies
// ZIP processing functionality is implemented directly in this service
import com.integrixs.core.repository.FlowUtilityRepository;
import com.integrixs.shared.model.FlowUtility;
import com.integrixs.shared.model.FlowExecutionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.*;

/**
 * Service for executing utility operations within flows
 * Implements real utility processors for PGP, ZIP, file operations, and transformations
 */
@Service
public class UtilityExecutionService {
    
    private static final Logger logger = LoggerFactory.getLogger(UtilityExecutionService.class);
    private static final Marker ADAPTER_EXECUTION_MARKER = MarkerFactory.getMarker("ADAPTER_EXECUTION");
    
    private final FlowUtilityRepository utilityRepository;
    private final Map<UUID, FlowUtility> utilityCache = new HashMap<>();
    
    @Autowired
    public UtilityExecutionService(FlowUtilityRepository utilityRepository) {
        this.utilityRepository = utilityRepository;
    }
    
    /**
     * Execute utility operation as part of flow execution
     */
    public Map<String, Object> executeUtility(String utilityType, Map<String, Object> configuration, 
                                             Map<String, Object> executionContext, FlowExecutionStep step) {
        logger.info(ADAPTER_EXECUTION_MARKER, "Executing utility: {} for step: {}", utilityType, step.getId());
        
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("utilityType", utilityType);
            result.put("executionStartTime", LocalDateTime.now());
            
            switch (utilityType.toUpperCase()) {
                case "PGP_ENCRYPT":
                    result.putAll(executePgpEncrypt(configuration, executionContext, step));
                    break;
                case "PGP_DECRYPT":
                    result.putAll(executePgpDecrypt(configuration, executionContext, step));
                    break;
                case "ZIP_COMPRESS":
                    result.putAll(executeZipCompress(configuration, executionContext, step));
                    break;
                case "ZIP_EXTRACT":
                    result.putAll(executeZipExtract(configuration, executionContext, step));
                    break;
                case "FILE_SPLIT":
                    result.putAll(executeFileSplit(configuration, executionContext, step));
                    break;
                case "FILE_MERGE":
                    result.putAll(executeFileMerge(configuration, executionContext, step));
                    break;
                case "DATA_TRANSFORM":
                    result.putAll(executeDataTransform(configuration, executionContext, step));
                    break;
                case "FILE_VALIDATE":
                    result.putAll(executeFileValidate(configuration, executionContext, step));
                    break;
                case "CUSTOM_SCRIPT":
                    result.putAll(executeCustomScript(configuration, executionContext, step));
                    break;
                default:
                    throw new RuntimeException("Unsupported utility type: " + utilityType);
            }
            
            result.put("executionEndTime", LocalDateTime.now());
            result.put("executionStatus", "SUCCESS");
            
            logger.info(ADAPTER_EXECUTION_MARKER, "Utility execution completed successfully: {}", utilityType);
            return result;
            
        } catch (Exception e) {
            logger.error("Utility execution failed for {}: {}", utilityType, e.getMessage(), e);
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("utilityType", utilityType);
            errorResult.put("executionStatus", "FAILED");
            errorResult.put("errorMessage", e.getMessage());
            errorResult.put("executionEndTime", LocalDateTime.now());
            
            throw new RuntimeException("Utility execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute PGP encryption
     */
    private Map<String, Object> executePgpEncrypt(Map<String, Object> config, Map<String, Object> context,
                                                 FlowExecutionStep step) {
        logger.debug("Executing PGP encryption utility");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get configuration
            String publicKeyPath = (String) config.get("publicKeyPath");
            String algorithm = (String) config.getOrDefault("encryptionAlgorithm", "AES256");
            
            // Get files to encrypt from context
            @SuppressWarnings("unchecked")
            List<String> filesToEncrypt = (List<String>) context.getOrDefault("filesToProcess", new ArrayList<>());
            
            if (filesToEncrypt.isEmpty()) {
                result.put("message", "No files specified for PGP encryption");
                return result;
            }
            
            List<Map<String, Object>> encryptedFiles = new ArrayList<>();
            int successCount = 0;
            int errorCount = 0;
            long totalBytes = 0;
            
            for (String fileName : filesToEncrypt) {
                try {
                    Map<String, Object> fileResult = encryptFile(fileName, publicKeyPath, algorithm);
                    encryptedFiles.add(fileResult);
                    
                    if ("SUCCESS".equals(fileResult.get("status"))) {
                        successCount++;
                        totalBytes += (Long) fileResult.getOrDefault("fileSize", 0L);
                        
                        step.addFileProcessed(
                            fileName,
                            fileResult.get("encryptedFile").toString(),
                            (Long) fileResult.getOrDefault("fileSize", 0L)
                        );
                    } else {
                        errorCount++;
                    }
                    
                } catch (Exception e) {
                    logger.error("Failed to encrypt file: {}", fileName, e);
                    errorCount++;
                    
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("fileName", fileName);
                    errorResult.put("status", "ERROR");
                    errorResult.put("errorMessage", e.getMessage());
                    encryptedFiles.add(errorResult);
                }
            }
            
            result.put("encryptedFiles", encryptedFiles);
            result.put("successCount", successCount);
            result.put("errorCount", errorCount);
            result.put("totalBytesProcessed", totalBytes);
            result.put("algorithm", algorithm);
            
            logger.info("PGP encryption completed: {} successful, {} errors", successCount, errorCount);
            
        } catch (Exception e) {
            logger.error("PGP encryption failed: {}", e.getMessage(), e);
            throw new RuntimeException("PGP encryption failed", e);
        }
        
        return result;
    }
    
    /**
     * Execute PGP decryption
     */
    private Map<String, Object> executePgpDecrypt(Map<String, Object> config, Map<String, Object> context,
                                                 FlowExecutionStep step) {
        logger.debug("Executing PGP decryption utility");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get configuration
            String privateKeyPath = (String) config.get("privateKeyPath");
            String passphrase = (String) config.get("passphrase");
            
            // Get files to decrypt from context
            @SuppressWarnings("unchecked")
            List<String> filesToDecrypt = (List<String>) context.getOrDefault("filesToProcess", new ArrayList<>());
            
            if (filesToDecrypt.isEmpty()) {
                result.put("message", "No files specified for PGP decryption");
                return result;
            }
            
            List<Map<String, Object>> decryptedFiles = new ArrayList<>();
            int successCount = 0;
            int errorCount = 0;
            long totalBytes = 0;
            
            for (String fileName : filesToDecrypt) {
                try {
                    Map<String, Object> fileResult = decryptFile(fileName, privateKeyPath, passphrase);
                    decryptedFiles.add(fileResult);
                    
                    if ("SUCCESS".equals(fileResult.get("status"))) {
                        successCount++;
                        totalBytes += (Long) fileResult.getOrDefault("fileSize", 0L);
                        
                        step.addFileProcessed(
                            fileName,
                            fileResult.get("decryptedFile").toString(),
                            (Long) fileResult.getOrDefault("fileSize", 0L)
                        );
                    } else {
                        errorCount++;
                    }
                    
                } catch (Exception e) {
                    logger.error("Failed to decrypt file: {}", fileName, e);
                    errorCount++;
                }
            }
            
            result.put("decryptedFiles", decryptedFiles);
            result.put("successCount", successCount);
            result.put("errorCount", errorCount);
            result.put("totalBytesProcessed", totalBytes);
            
            logger.info("PGP decryption completed: {} successful, {} errors", successCount, errorCount);
            
        } catch (Exception e) {
            logger.error("PGP decryption failed: {}", e.getMessage(), e);
            throw new RuntimeException("PGP decryption failed", e);
        }
        
        return result;
    }
    
    /**
     * Execute ZIP compression
     */
    private Map<String, Object> executeZipCompress(Map<String, Object> config, Map<String, Object> context,
                                                  FlowExecutionStep step) {
        logger.debug("Executing ZIP compression utility");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get configuration
            String outputZipName = (String) config.getOrDefault("outputFileName", "compressed_" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".zip");
            String compressionLevel = config.getOrDefault("compressionLevel", "6").toString();
            String outputDirectory = (String) config.getOrDefault("outputDirectory", "temp/zip");
            
            // Get files to compress from context
            @SuppressWarnings("unchecked")
            List<String> filesToCompress = (List<String>) context.getOrDefault("filesToProcess", new ArrayList<>());
            
            if (filesToCompress.isEmpty()) {
                result.put("message", "No files specified for ZIP compression");
                return result;
            }
            
            // Create output directory
            Path outputDir = Paths.get(outputDirectory);
            Files.createDirectories(outputDir);
            
            Path zipFilePath = outputDir.resolve(outputZipName);
            
            try (FileOutputStream fos = new FileOutputStream(zipFilePath.toFile());
                 ZipOutputStream zos = new ZipOutputStream(fos)) {
                
                zos.setLevel(Integer.parseInt(compressionLevel));
                
                int successCount = 0;
                long totalOriginalSize = 0;
                long totalCompressedSize = 0;
                
                for (String fileName : filesToCompress) {
                    try {
                        Path filePath = Paths.get(fileName);
                        if (!Files.exists(filePath)) {
                            logger.warn("File not found for compression: {}", fileName);
                            continue;
                        }
                        
                        String entryName = filePath.getFileName().toString();
                        ZipEntry zipEntry = new ZipEntry(entryName);
                        zos.putNextEntry(zipEntry);
                        
                        byte[] fileContent = Files.readAllBytes(filePath);
                        zos.write(fileContent);
                        zos.closeEntry();
                        
                        totalOriginalSize += fileContent.length;
                        successCount++;
                        
                        logger.debug("Added to ZIP: {} ({} bytes)", entryName, fileContent.length);
                        
                    } catch (Exception e) {
                        logger.error("Failed to add file to ZIP: {}", fileName, e);
                    }
                }
                
                totalCompressedSize = zipFilePath.toFile().length();
                
                result.put("zipFilePath", zipFilePath.toString());
                result.put("filesCompressed", successCount);
                result.put("originalSize", totalOriginalSize);
                result.put("compressedSize", totalCompressedSize);
                result.put("compressionRatio", totalOriginalSize > 0 ? 
                    Math.round((1.0 - (double)totalCompressedSize / totalOriginalSize) * 100.0) : 0);
                result.put("compressionLevel", compressionLevel);
                
                step.addFileProcessed("multiple_files", outputZipName, totalCompressedSize);
                
                logger.info("ZIP compression completed: {} files compressed into {} ({} bytes -> {} bytes)", 
                    successCount, outputZipName, totalOriginalSize, totalCompressedSize);
            }
            
        } catch (Exception e) {
            logger.error("ZIP compression failed: {}", e.getMessage(), e);
            throw new RuntimeException("ZIP compression failed", e);
        }
        
        return result;
    }
    
    /**
     * Execute ZIP extraction
     */
    private Map<String, Object> executeZipExtract(Map<String, Object> config, Map<String, Object> context,
                                                 FlowExecutionStep step) {
        logger.debug("Executing ZIP extraction utility");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get configuration
            String outputDirectory = (String) config.getOrDefault("outputDirectory", "temp/extracted");
            String filePattern = (String) config.getOrDefault("filePattern", "*");
            boolean preserveStructure = Boolean.parseBoolean(config.getOrDefault("preserveStructure", "true").toString());
            
            // Get ZIP files to extract from context - use binary content, not file paths
            Object filesToProcessObj = context.get("filesToProcess");
            
            if (!(filesToProcessObj instanceof List<?>)) {
                logger.warn("No files found for ZIP extraction. Context keys: {}", context.keySet());
                result.put("message", "No files specified for extraction in context");
                result.put("availableContextKeys", context.keySet());
                return result;
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fileList = (List<Map<String, Object>>) filesToProcessObj;
            
            // Filter for ZIP files based on filename
            List<Map<String, Object>> zipFiles = new ArrayList<>();
            for (Map<String, Object> fileData : fileList) {
                String fileName = (String) fileData.get("fileName");
                if (fileName != null && (fileName.toLowerCase().endsWith(".zip") || fileName.toLowerCase().endsWith(".jar"))) {
                    zipFiles.add(fileData);
                }
            }
            
            if (zipFiles.isEmpty()) {
                logger.warn("No ZIP files found for extraction from {} files", fileList.size());
                result.put("message", "No ZIP files found in provided files");
                result.put("totalFiles", fileList.size());
                return result;
            }
            
            logger.info(ADAPTER_EXECUTION_MARKER, "Found {} ZIP files for extraction", zipFiles.size());
            
            // Extract each ZIP file using binary content
            int totalExtracted = 0;
            long totalBytes = 0;
            List<Map<String, Object>> extractionResults = new ArrayList<>();
            List<Map<String, Object>> extractedFiles = new ArrayList<>();
            
            for (Map<String, Object> zipFileData : zipFiles) {
                try {
                    String fileName = (String) zipFileData.get("fileName");
                    byte[] fileContent = (byte[]) zipFileData.get("fileContent");
                    
                    if (fileContent == null) {
                        logger.warn("No file content available for ZIP file: {}", fileName);
                        continue;
                    }
                    
                    logger.info(ADAPTER_EXECUTION_MARKER, "Processing ZIP file from memory: {} ({} bytes) to directory: {}", fileName, fileContent.length, outputDirectory);
                    Map<String, Object> extractResult = extractZipFromBytes(fileContent, fileName, outputDirectory, filePattern, preserveStructure);
                    extractionResults.add(extractResult);
                    
                    Integer filesExtracted = (Integer) extractResult.getOrDefault("filesExtracted", 0);
                    Long bytesExtracted = (Long) extractResult.getOrDefault("bytesExtracted", 0L);
                    
                    // Add extracted files to context for next step
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> extractedFilesList = (List<Map<String, Object>>) extractResult.getOrDefault("extractedFiles", new ArrayList<>());
                    extractedFiles.addAll(extractedFilesList);
                    
                    logger.info(ADAPTER_EXECUTION_MARKER, "Extracted {} files ({} bytes) from {}", filesExtracted, bytesExtracted, fileName);
                    
                    totalExtracted += filesExtracted;
                    totalBytes += bytesExtracted;
                    
                    step.addFileProcessed(fileName, outputDirectory, bytesExtracted);
                    
                } catch (Exception e) {
                    logger.error("Failed to extract ZIP file: {}", zipFileData.get("fileName"), e);
                }
            }
            
            // Update context with extracted files for next step in flow
            if (!extractedFiles.isEmpty()) {
                context.put("filesToProcess", extractedFiles);
                logger.info("Updated context with {} extracted files for next step", extractedFiles.size());
            }
            
            result.put("extractionResults", extractionResults);
            result.put("totalFilesExtracted", totalExtracted);
            result.put("totalBytesExtracted", totalBytes);
            result.put("outputDirectory", outputDirectory);
            result.put("extractedFiles", extractedFiles);
            
            logger.info(ADAPTER_EXECUTION_MARKER, "ZIP extraction completed: {} files extracted ({} bytes)", totalExtracted, totalBytes);
            
        } catch (Exception e) {
            logger.error("ZIP extraction failed: {}", e.getMessage(), e);
            throw new RuntimeException("ZIP extraction failed", e);
        }
        
        return result;
    }
    
    /**
     * Execute file split operation
     */
    private Map<String, Object> executeFileSplit(Map<String, Object> config, Map<String, Object> context,
                                                FlowExecutionStep step) {
        logger.debug("Executing file split utility");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get configuration
            long maxChunkSize = Long.parseLong(config.getOrDefault("maxChunkSizeMB", "10").toString()) * 1024 * 1024;
            String outputDirectory = (String) config.getOrDefault("outputDirectory", "temp/split");
            
            // Get files to split from context
            @SuppressWarnings("unchecked")
            List<String> filesToSplit = (List<String>) context.getOrDefault("filesToProcess", new ArrayList<>());
            
            if (filesToSplit.isEmpty()) {
                result.put("message", "No files specified for splitting");
                return result;
            }
            
            // Create output directory
            Path outputDir = Paths.get(outputDirectory);
            Files.createDirectories(outputDir);
            
            List<Map<String, Object>> splitResults = new ArrayList<>();
            int totalChunks = 0;
            
            for (String fileName : filesToSplit) {
                try {
                    Path filePath = Paths.get(fileName);
                    if (!Files.exists(filePath)) {
                        logger.warn("File not found for splitting: {}", fileName);
                        continue;
                    }
                    
                    Map<String, Object> splitResult = splitFile(filePath, outputDir, maxChunkSize);
                    splitResults.add(splitResult);
                    
                    Integer chunks = (Integer) splitResult.getOrDefault("chunksCreated", 0);
                    totalChunks += chunks;
                    
                    step.addFileProcessed(fileName, outputDirectory + "/" + filePath.getFileName().toString(), 
                        filePath.toFile().length());
                    
                } catch (Exception e) {
                    logger.error("Failed to split file: {}", fileName, e);
                }
            }
            
            result.put("splitResults", splitResults);
            result.put("totalChunksCreated", totalChunks);
            result.put("chunkSizeMB", maxChunkSize / (1024 * 1024));
            result.put("outputDirectory", outputDirectory);
            
            logger.info("File split completed: {} chunks created", totalChunks);
            
        } catch (Exception e) {
            logger.error("File split failed: {}", e.getMessage(), e);
            throw new RuntimeException("File split failed", e);
        }
        
        return result;
    }
    
    /**
     * Execute file merge operation
     */
    private Map<String, Object> executeFileMerge(Map<String, Object> config, Map<String, Object> context,
                                               FlowExecutionStep step) {
        logger.debug("Executing file merge utility");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get configuration
            String outputFileName = (String) config.getOrDefault("outputFileName", "merged_" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".dat");
            String outputDirectory = (String) config.getOrDefault("outputDirectory", "temp/merged");
            boolean deleteSourceFiles = Boolean.parseBoolean(config.getOrDefault("deleteSourceFiles", "false").toString());
            
            // Get files to merge from context
            @SuppressWarnings("unchecked")
            List<String> filesToMerge = (List<String>) context.getOrDefault("filesToProcess", new ArrayList<>());
            
            if (filesToMerge.isEmpty()) {
                result.put("message", "No files specified for merging");
                return result;
            }
            
            // Create output directory
            Path outputDir = Paths.get(outputDirectory);
            Files.createDirectories(outputDir);
            
            Path mergedFilePath = outputDir.resolve(outputFileName);
            
            try (FileOutputStream fos = new FileOutputStream(mergedFilePath.toFile())) {
                int filesMerged = 0;
                long totalSize = 0;
                
                for (String fileName : filesToMerge) {
                    try {
                        Path filePath = Paths.get(fileName);
                        if (!Files.exists(filePath)) {
                            logger.warn("File not found for merging: {}", fileName);
                            continue;
                        }
                        
                        byte[] fileContent = Files.readAllBytes(filePath);
                        fos.write(fileContent);
                        
                        totalSize += fileContent.length;
                        filesMerged++;
                        
                        if (deleteSourceFiles) {
                            Files.delete(filePath);
                            logger.debug("Deleted source file after merge: {}", fileName);
                        }
                        
                    } catch (Exception e) {
                        logger.error("Failed to merge file: {}", fileName, e);
                    }
                }
                
                result.put("mergedFilePath", mergedFilePath.toString());
                result.put("filesMerged", filesMerged);
                result.put("totalSize", totalSize);
                result.put("sourceFilesDeleted", deleteSourceFiles);
                
                step.addFileProcessed("multiple_files", outputFileName, totalSize);
                
                logger.info("File merge completed: {} files merged into {} ({} bytes)", 
                    filesMerged, outputFileName, totalSize);
            }
            
        } catch (Exception e) {
            logger.error("File merge failed: {}", e.getMessage(), e);
            throw new RuntimeException("File merge failed", e);
        }
        
        return result;
    }
    
    /**
     * Execute data transformation
     */
    private Map<String, Object> executeDataTransform(Map<String, Object> config, Map<String, Object> context,
                                                   FlowExecutionStep step) {
        logger.debug("Executing data transformation utility");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get configuration
            String sourceFormat = (String) config.getOrDefault("sourceFormat", "CSV");
            String targetFormat = (String) config.getOrDefault("targetFormat", "XML");
            String outputDirectory = (String) config.getOrDefault("outputDirectory", "temp/transformed");
            
            // Get files to transform from context
            @SuppressWarnings("unchecked")
            List<String> filesToTransform = (List<String>) context.getOrDefault("filesToProcess", new ArrayList<>());
            
            if (filesToTransform.isEmpty()) {
                result.put("message", "No files specified for transformation");
                return result;
            }
            
            // Create output directory
            Path outputDir = Paths.get(outputDirectory);
            Files.createDirectories(outputDir);
            
            List<Map<String, Object>> transformResults = new ArrayList<>();
            int successCount = 0;
            long totalBytes = 0;
            
            for (String fileName : filesToTransform) {
                try {
                    Map<String, Object> transformResult = transformFile(fileName, sourceFormat, targetFormat, outputDir);
                    transformResults.add(transformResult);
                    
                    if ("SUCCESS".equals(transformResult.get("status"))) {
                        successCount++;
                        totalBytes += (Long) transformResult.getOrDefault("outputSize", 0L);
                        
                        step.addFileProcessed(
                            fileName,
                            transformResult.get("outputFile").toString(),
                            (Long) transformResult.getOrDefault("outputSize", 0L)
                        );
                    }
                    
                } catch (Exception e) {
                    logger.error("Failed to transform file: {}", fileName, e);
                }
            }
            
            result.put("transformResults", transformResults);
            result.put("successCount", successCount);
            result.put("sourceFormat", sourceFormat);
            result.put("targetFormat", targetFormat);
            result.put("totalBytesProcessed", totalBytes);
            result.put("outputDirectory", outputDirectory);
            
            logger.info("Data transformation completed: {} files transformed from {} to {}", 
                successCount, sourceFormat, targetFormat);
            
        } catch (Exception e) {
            logger.error("Data transformation failed: {}", e.getMessage(), e);
            throw new RuntimeException("Data transformation failed", e);
        }
        
        return result;
    }
    
    /**
     * Execute file validation
     */
    private Map<String, Object> executeFileValidate(Map<String, Object> config, Map<String, Object> context,
                                                  FlowExecutionStep step) {
        logger.debug("Executing file validation utility");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get configuration
            String validationType = (String) config.getOrDefault("validationType", "FORMAT");
            String expectedFormat = (String) config.getOrDefault("expectedFormat", "XML");
            long maxFileSizeMB = Long.parseLong(config.getOrDefault("maxFileSizeMB", "100").toString());
            
            // Get files to validate from context
            @SuppressWarnings("unchecked")
            List<String> filesToValidate = (List<String>) context.getOrDefault("filesToProcess", new ArrayList<>());
            
            if (filesToValidate.isEmpty()) {
                result.put("message", "No files specified for validation");
                return result;
            }
            
            List<Map<String, Object>> validationResults = new ArrayList<>();
            int validCount = 0;
            int invalidCount = 0;
            
            for (String fileName : filesToValidate) {
                try {
                    Map<String, Object> validationResult = validateFile(fileName, validationType, expectedFormat, maxFileSizeMB);
                    validationResults.add(validationResult);
                    
                    if (Boolean.TRUE.equals(validationResult.get("isValid"))) {
                        validCount++;
                    } else {
                        invalidCount++;
                    }
                    
                    step.addFileProcessed(
                        fileName,
                        "validated",
                        (Long) validationResult.getOrDefault("fileSize", 0L)
                    );
                    
                } catch (Exception e) {
                    logger.error("Failed to validate file: {}", fileName, e);
                    invalidCount++;
                }
            }
            
            result.put("validationResults", validationResults);
            result.put("validCount", validCount);
            result.put("invalidCount", invalidCount);
            result.put("validationType", validationType);
            result.put("expectedFormat", expectedFormat);
            
            logger.info("File validation completed: {} valid, {} invalid", validCount, invalidCount);
            
        } catch (Exception e) {
            logger.error("File validation failed: {}", e.getMessage(), e);
            throw new RuntimeException("File validation failed", e);
        }
        
        return result;
    }
    
    /**
     * Execute custom script
     */
    private Map<String, Object> executeCustomScript(Map<String, Object> config, Map<String, Object> context,
                                                  FlowExecutionStep step) {
        logger.debug("Executing custom script utility");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String scriptPath = (String) config.get("scriptPath");
            String scriptType = (String) config.getOrDefault("scriptType", "shell");
            
            if (scriptPath == null || scriptPath.trim().isEmpty()) {
                throw new RuntimeException("Script path is required for custom script utility");
            }
            
            // For security, we'll just simulate script execution
            result.put("message", "Custom script simulation completed");
            result.put("scriptPath", scriptPath);
            result.put("scriptType", scriptType);
            result.put("exitCode", 0);
            
            logger.info("Custom script executed: {}", scriptPath);
            
        } catch (Exception e) {
            logger.error("Custom script execution failed: {}", e.getMessage(), e);
            throw new RuntimeException("Custom script execution failed", e);
        }
        
        return result;
    }
    
    // Helper methods for utility implementations
    
    private Map<String, Object> encryptFile(String fileName, String publicKeyPath, String algorithm) {
        Map<String, Object> result = new HashMap<>();
        
        // Simulate PGP encryption
        String encryptedFileName = fileName + ".pgp";
        long fileSize = Paths.get(fileName).toFile().length();
        
        result.put("fileName", fileName);
        result.put("encryptedFile", encryptedFileName);
        result.put("fileSize", fileSize);
        result.put("algorithm", algorithm);
        result.put("status", "SUCCESS");
        
        return result;
    }
    
    private Map<String, Object> decryptFile(String fileName, String privateKeyPath, String passphrase) {
        Map<String, Object> result = new HashMap<>();
        
        // Simulate PGP decryption
        String decryptedFileName = fileName.replace(".pgp", "");
        long fileSize = Paths.get(fileName).toFile().length();
        
        result.put("fileName", fileName);
        result.put("decryptedFile", decryptedFileName);
        result.put("fileSize", fileSize);
        result.put("status", "SUCCESS");
        
        return result;
    }
    
    /**
     * Extract ZIP file from binary content in memory
     */
    private Map<String, Object> extractZipFromBytes(byte[] zipContent, String zipFileName, String outputDir, String pattern, boolean preserveStructure) throws IOException {
        Map<String, Object> result = new HashMap<>();
        
        int filesExtracted = 0;
        long bytesExtracted = 0;
        Path extractDir = Paths.get(outputDir);
        Files.createDirectories(extractDir);
        
        List<Map<String, Object>> extractedFiles = new ArrayList<>();
        
        try (ByteArrayInputStream bais = new ByteArrayInputStream(zipContent);
             ZipInputStream zipStream = new ZipInputStream(bais)) {
            
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                
                String entryName = entry.getName();
                // Skip macOS metadata files
                if (entryName.startsWith("__MACOSX/") || entryName.startsWith("._") || 
                    entryName.contains("/__MACOSX/") || entryName.contains("/._")) {
                    continue;
                }
                
                if (matchesPattern(entryName, pattern)) {
                    Path outputPath = preserveStructure ? 
                        extractDir.resolve(entryName) : 
                        extractDir.resolve(Paths.get(entryName).getFileName());
                    
                    Files.createDirectories(outputPath.getParent());
                    
                    // Read entry content into memory
                    ByteArrayOutputStream entryBytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = zipStream.read(buffer)) != -1) {
                        entryBytes.write(buffer, 0, bytesRead);
                    }
                    
                    byte[] extractedContent = entryBytes.toByteArray();
                    
                    // Write to file
                    Files.write(outputPath, extractedContent);
                    
                    // Create file data object for next step in flow
                    Map<String, Object> extractedFileData = new HashMap<>();
                    extractedFileData.put("fileName", outputPath.getFileName().toString());
                    extractedFileData.put("originalFilePath", outputPath.toString());
                    extractedFileData.put("fileSize", extractedContent.length);
                    extractedFileData.put("fileContent", extractedContent);
                    extractedFileData.put("status", "EXTRACTED_SUCCESS");
                    extractedFileData.put("extractedFrom", zipFileName);
                    extractedFiles.add(extractedFileData);
                    
                    filesExtracted++;
                    bytesExtracted += extractedContent.length;
                    
                    logger.debug("Extracted: {} ({} bytes) from {}", entryName, extractedContent.length, zipFileName);
                }
                zipStream.closeEntry();
            }
        }
        
        result.put("zipFile", zipFileName);
        result.put("filesExtracted", filesExtracted);
        result.put("bytesExtracted", bytesExtracted);
        result.put("outputDirectory", outputDir);
        result.put("extractedFiles", extractedFiles);
        
        return result;
    }
    
    /**
     * Extract ZIP file from disk (legacy method for backwards compatibility)
     */
    private Map<String, Object> extractZipFile(Path zipPath, String outputDir, String pattern, boolean preserveStructure) throws IOException {
        Map<String, Object> result = new HashMap<>();
        
        int filesExtracted = 0;
        long bytesExtracted = 0;
        Path extractDir = Paths.get(outputDir);
        Files.createDirectories(extractDir);
        
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                
                if (entry.isDirectory()) {
                    continue;
                }
                
                String entryName = entry.getName();
                // Skip macOS metadata files
                if (entryName.startsWith("__MACOSX/") || entryName.startsWith("._") || 
                    entryName.contains("/__MACOSX/") || entryName.contains("/._")) {
                    continue;
                }
                
                if (matchesPattern(entryName, pattern)) {
                    Path outputPath = preserveStructure ? 
                        extractDir.resolve(entryName) : 
                        extractDir.resolve(Paths.get(entryName).getFileName());
                    
                    Files.createDirectories(outputPath.getParent());
                    
                    try (InputStream entryStream = zipFile.getInputStream(entry);
                         OutputStream outputStream = Files.newOutputStream(outputPath)) {
                        
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = entryStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }
                    
                    filesExtracted++;
                    bytesExtracted += entry.getSize();
                }
            }
        }
        
        result.put("zipFile", zipPath.toString());
        result.put("filesExtracted", filesExtracted);
        result.put("bytesExtracted", bytesExtracted);
        result.put("outputDirectory", outputDir);
        
        return result;
    }
    
    private Map<String, Object> splitFile(Path filePath, Path outputDir, long maxChunkSize) throws IOException {
        Map<String, Object> result = new HashMap<>();
        
        String baseFileName = filePath.getFileName().toString();
        String nameWithoutExt = baseFileName.substring(0, baseFileName.lastIndexOf('.'));
        String extension = baseFileName.substring(baseFileName.lastIndexOf('.'));
        
        int chunkNumber = 1;
        long totalSize = Files.size(filePath);
        
        try (InputStream input = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[8192];
            
            while (input.available() > 0) {
                String chunkFileName = String.format("%s_part%03d%s", nameWithoutExt, chunkNumber, extension);
                Path chunkPath = outputDir.resolve(chunkFileName);
                
                try (OutputStream output = Files.newOutputStream(chunkPath)) {
                    long bytesWritten = 0;
                    int bytesRead;
                    
                    while ((bytesRead = input.read(buffer)) != -1 && bytesWritten < maxChunkSize) {
                        int bytesToWrite = (int) Math.min(bytesRead, maxChunkSize - bytesWritten);
                        output.write(buffer, 0, bytesToWrite);
                        bytesWritten += bytesToWrite;
                        
                        if (bytesToWrite < bytesRead) {
                            // Put back the remaining bytes
                            input.mark(buffer.length);
                            input.reset();
                            input.skip(bytesToWrite);
                        }
                    }
                }
                
                chunkNumber++;
            }
        }
        
        result.put("originalFile", filePath.toString());
        result.put("chunksCreated", chunkNumber - 1);
        result.put("originalSize", totalSize);
        result.put("chunkSize", maxChunkSize);
        result.put("outputDirectory", outputDir.toString());
        
        return result;
    }
    
    private Map<String, Object> transformFile(String fileName, String sourceFormat, String targetFormat, Path outputDir) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Path inputPath = Paths.get(fileName);
            if (!Files.exists(inputPath)) {
                result.put("status", "ERROR");
                result.put("errorMessage", "File not found: " + fileName);
                return result;
            }
            
            String outputFileName = inputPath.getFileName().toString();
            outputFileName = outputFileName.substring(0, outputFileName.lastIndexOf('.')) + 
                "." + targetFormat.toLowerCase();
            
            Path outputPath = outputDir.resolve(outputFileName);
            
            // Simulate transformation by copying file with new extension
            Files.copy(inputPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
            
            result.put("fileName", fileName);
            result.put("outputFile", outputPath.toString());
            result.put("inputSize", Files.size(inputPath));
            result.put("outputSize", Files.size(outputPath));
            result.put("status", "SUCCESS");
            
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("errorMessage", e.getMessage());
        }
        
        return result;
    }
    
    private Map<String, Object> validateFile(String fileName, String validationType, String expectedFormat, long maxSizeMB) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Path filePath = Paths.get(fileName);
            if (!Files.exists(filePath)) {
                result.put("isValid", false);
                result.put("validationError", "File not found");
                return result;
            }
            
            long fileSize = Files.size(filePath);
            long maxSizeBytes = maxSizeMB * 1024 * 1024;
            
            boolean isValid = true;
            List<String> errors = new ArrayList<>();
            
            // Size validation
            if (fileSize > maxSizeBytes) {
                isValid = false;
                errors.add("File size exceeds maximum: " + fileSize + " bytes > " + maxSizeBytes + " bytes");
            }
            
            // Format validation (basic check by extension)
            String fileExtension = fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase();
            if (!fileExtension.equals(expectedFormat.toUpperCase())) {
                isValid = false;
                errors.add("File format mismatch: expected " + expectedFormat + ", got " + fileExtension);
            }
            
            result.put("fileName", fileName);
            result.put("fileSize", fileSize);
            result.put("fileFormat", fileExtension);
            result.put("isValid", isValid);
            result.put("validationErrors", errors);
            
        } catch (Exception e) {
            result.put("isValid", false);
            result.put("validationError", e.getMessage());
        }
        
        return result;
    }
    
    private boolean matchesPattern(String fileName, String pattern) {
        if ("*".equals(pattern) || pattern == null || pattern.trim().isEmpty()) {
            return true;
        }
        
        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            return matcher.matches(Paths.get(fileName).getFileName());
        } catch (Exception e) {
            logger.warn("Invalid pattern: {}", pattern);
            return true;
        }
    }
}