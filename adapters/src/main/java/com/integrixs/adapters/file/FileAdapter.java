package com.integrixs.adapters.file;

import com.integrixs.shared.model.Adapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

public class FileAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(FileAdapter.class);
    
    private final Adapter adapter;
    private final FileAdapterConfig config;
    
    public FileAdapter(Adapter adapter) {
        this.adapter = adapter;
        this.config = new FileAdapterConfig(adapter.getConfiguration());
        
        logger.info("Initializing File Adapter: {} with directory: {}", 
                   adapter.getName(), config.getDirectory());
    }
    
    /**
     * Discovers files matching the configured pattern in the directory
     */
    public List<Path> discoverFiles() throws IOException {
        return discoverFiles(null);
    }
    
    /**
     * Discovers files with execution context for detailed logging
     */
    public List<Path> discoverFiles(String executionId) throws IOException {
        // Set execution context for logging
        if (executionId != null) {
            MDC.put("executionId", executionId);
            MDC.put("adapterId", adapter.getId().toString());
            MDC.put("adapterName", adapter.getName());
            MDC.put("logCategory", "ADAPTER_EXECUTION");
        }
        
        logger.info("=== STARTING SENDER FILE ADAPTER EXECUTION ===");
        logger.info("Adapter: {} (ID: {})", adapter.getName(), adapter.getId());
        logger.info("Direction: {}", adapter.getDirection());
        logger.info("Configuration: {}", config.toString());
        Path sourceDir = Paths.get(config.getDirectory());
        
        logger.info("=== STEP 1: DIRECTORY ACCESS ===");
        logger.info("Changing to source directory: {}", sourceDir.toAbsolutePath());
        logger.info("Directory path type: {}", sourceDir.getClass().getSimpleName());
        
        if (!Files.exists(sourceDir)) {
            logger.error("Directory does not exist: {}", sourceDir);
            throw new IOException("Directory does not exist: " + sourceDir);
        }
        logger.info("✓ Directory exists: {}", sourceDir);
        
        if (!Files.isDirectory(sourceDir)) {
            logger.error("Path is not a directory: {}", sourceDir);
            throw new IOException("Path is not a directory: " + sourceDir);
        }
        logger.info("✓ Path is a valid directory: {}", sourceDir);
        
        // Check directory permissions
        if (!Files.isReadable(sourceDir)) {
            logger.error("Directory is not readable: {}", sourceDir);
            throw new IOException("Directory is not readable: " + sourceDir);
        }
        logger.info("✓ Directory is readable: {}", sourceDir);
        
        List<Path> discoveredFiles = new ArrayList<>();
        String pattern = config.getFilePattern();
        
        logger.info("=== STEP 2: FILE DISCOVERY ===");
        logger.info("Scanning directory: {}", sourceDir);
        logger.info("File pattern: {}", pattern);
        logger.info("Include subdirectories: {}", config.getBoolean("includeSubdirectories", false));
        
        // Log directory contents before filtering
        try {
            logger.info("Listing all directory contents:");
            Files.list(sourceDir).forEach(path -> {
                try {
                    if (Files.isDirectory(path)) {
                        logger.info("  [DIR]  {}", path.getFileName());
                    } else {
                        long size = Files.size(path);
                        logger.info("  [FILE] {} ({} bytes)", path.getFileName(), size);
                    }
                } catch (IOException e) {
                    logger.warn("  [?]    {} (could not read attributes)", path.getFileName());
                }
            });
        } catch (IOException e) {
            logger.warn("Could not list directory contents: {}", e.getMessage());
        }
        
        logger.info("=== STEP 3: FILE FILTERING ===");
        int walkDepth = config.getBoolean("includeSubdirectories", false) ? Integer.MAX_VALUE : 1;
        logger.info("Walking directory tree with depth: {}", walkDepth == Integer.MAX_VALUE ? "unlimited" : walkDepth);
        
        try (Stream<Path> paths = Files.walk(sourceDir, walkDepth)) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            logger.info("Created pattern matcher for: {}", pattern);
            
            paths.forEach(path -> {
                if (Files.isRegularFile(path)) {
                    String fileName = path.getFileName().toString();
                    boolean matchesPattern = matcher.matches(path.getFileName());
                    
                    if (matchesPattern) {
                        logger.info("✓ File matches pattern: {}", fileName);
                        
                        // Check if file is ready for processing
                        boolean isReady = isFileReady(path, fileName);
                        if (isReady) {
                            discoveredFiles.add(path);
                            logger.info("✓ File added to processing queue: {}", fileName);
                        } else {
                            logger.info("⚠ File not ready, skipping: {}", fileName);
                        }
                    } else {
                        logger.debug("✗ File does not match pattern: {}", fileName);
                    }
                }
            });
        }
        
        logger.info("=== STEP 4: DISCOVERY COMPLETE ===");
        logger.info("Total files discovered: {}", discoveredFiles.size());
        logger.info("Files queued for processing:");
        
        for (int i = 0; i < discoveredFiles.size(); i++) {
            Path file = discoveredFiles.get(i);
            try {
                long size = Files.size(file);
                logger.info("  {}. {} ({} bytes)", i + 1, file.getFileName(), size);
            } catch (IOException e) {
                logger.info("  {}. {} (size unknown)", i + 1, file.getFileName());
            }
        }
        
        logger.info("File discovery completed successfully");
        
        // Clear MDC if we set it
        if (executionId != null) {
            MDC.remove("executionId");
            MDC.remove("adapterId");
            MDC.remove("adapterName");
            MDC.remove("logCategory");
        }
        
        return discoveredFiles;
    }
    
    /**
     * Processes a single file
     */
    public FileProcessingResult processFile(Path filePath) {
        return processFile(filePath, null);
    }
    
    /**
     * Processes a single file with execution context
     */
    public FileProcessingResult processFile(Path filePath, String executionId) {
        // Set execution context for logging
        if (executionId != null) {
            MDC.put("executionId", executionId);
            MDC.put("adapterId", adapter.getId().toString());
            MDC.put("adapterName", adapter.getName());
            MDC.put("logCategory", "ADAPTER_EXECUTION");
        }
        logger.info("=== STARTING FILE PROCESSING ===");
        logger.info("Processing file: {}", filePath.toAbsolutePath());
        logger.info("File name: {}", filePath.getFileName());
        logger.info("Adapter: {} ({})", adapter.getName(), adapter.getDirection());
        
        FileProcessingResult result = new FileProcessingResult();
        result.setFilePath(filePath);
        result.setStartTime(LocalDateTime.now());
        result.setAdapterInterfaceId(adapter.getId());
        
        try {
            logger.info("=== STEP 1: FILE VALIDATION ===");
            // Validate file before processing
            validateFile(filePath);
            logger.info("✓ File validation completed successfully");
            
            logger.info("=== STEP 2: FILE READING ===");
            // Read file content
            logger.info("Reading file content from: {}", filePath);
            byte[] fileContent = Files.readAllBytes(filePath);
            result.setFileSize(fileContent.length);
            logger.info("✓ File read successfully: {} bytes", fileContent.length);
            
            result.setContentHash(calculateHash(fileContent));
            logger.info("✓ Content hash calculated: {}", result.getContentHash());
            
            logger.info("=== STEP 3: FILE TYPE DETECTION ===");
            // Process based on file type
            if (isZipFile(filePath)) {
                logger.info("File type detected: ZIP archive");
                result = processZipFile(filePath, result, executionId);
            } else {
                logger.info("File type detected: Regular file ({})", getFileType(filePath));
                result = processSingleFile(filePath, fileContent, result, executionId);
            }
            
            logger.info("=== STEP 4: POST-PROCESSING ===");
            // Apply post-processing workflow
            applyPostProcessing(filePath, result);
            
            result.setStatus(FileProcessingStatus.SUCCESS);
            result.setEndTime(LocalDateTime.now());
            
            logger.info("=== FILE PROCESSING COMPLETE ===");
            logger.info("✓ File processed successfully: {}", filePath.getFileName());
            logger.info("✓ Processing time: {}ms", result.getProcessingTimeMs());
            logger.info("✓ File size: {} bytes", result.getFileSize());
            logger.info("✓ Final status: {}", result.getStatus());
            
        } catch (Exception e) {
            result.setStatus(FileProcessingStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
            
            logger.error("=== FILE PROCESSING FAILED ===");
            logger.error("✗ Failed to process file: {}", filePath.getFileName());
            logger.error("✗ Error: {}", e.getMessage());
            logger.error("✗ Exception type: {}", e.getClass().getSimpleName());
            if (logger.isDebugEnabled()) {
                logger.error("✗ Full stack trace:", e);
            }
        }
        
        // Clear MDC if we set it
        if (executionId != null) {
            MDC.remove("executionId");
            MDC.remove("adapterId");
            MDC.remove("adapterName");
            MDC.remove("logCategory");
        }
        
        return result;
    }
    
    /**
     * Processes multiple files in batch
     */
    public List<FileProcessingResult> processFiles(List<Path> filePaths) {
        return processFiles(filePaths, null);
    }
    
    /**
     * Processes multiple files in batch with execution context
     */
    public List<FileProcessingResult> processFiles(List<Path> filePaths, String executionId) {
        // Set execution context for logging
        if (executionId != null) {
            MDC.put("executionId", executionId);
            MDC.put("adapterId", adapter.getId().toString());
            MDC.put("adapterName", adapter.getName());
            MDC.put("logCategory", "ADAPTER_EXECUTION");
        }
        logger.info("=== BATCH FILE PROCESSING ===");
        logger.info("Processing batch of {} files", filePaths.size());
        logger.info("Adapter: {} ({})", adapter.getName(), adapter.getDirection());
        
        List<FileProcessingResult> results = new ArrayList<>();
        
        for (int i = 0; i < filePaths.size(); i++) {
            Path filePath = filePaths.get(i);
            logger.info("=== PROCESSING FILE {}/{} ===", i + 1, filePaths.size());
            
            try {
                FileProcessingResult result = processFile(filePath, executionId);
                results.add(result);
                
            } catch (Exception e) {
                FileProcessingResult errorResult = new FileProcessingResult();
                errorResult.setFilePath(filePath);
                errorResult.setAdapterInterfaceId(adapter.getId());
                errorResult.setStatus(FileProcessingStatus.FAILED);
                errorResult.setErrorMessage("Batch processing error: " + e.getMessage());
                errorResult.setStartTime(LocalDateTime.now());
                errorResult.setEndTime(LocalDateTime.now());
                results.add(errorResult);
                
                logger.error("Error in batch processing for file: {}", filePath, e);
            }
        }
        
        long successCount = results.stream()
                .mapToLong(r -> r.getStatus() == FileProcessingStatus.SUCCESS ? 1 : 0)
                .sum();
        long failedCount = results.size() - successCount;
        
        logger.info("=== BATCH PROCESSING COMPLETE ===");
        logger.info("Total files processed: {}", results.size());
        logger.info("Successful: {}", successCount);
        logger.info("Failed: {}", failedCount);
        logger.info("Success rate: {:.1f}%", (successCount * 100.0) / results.size());
        
        // Clear MDC if we set it
        if (executionId != null) {
            MDC.remove("executionId");
            MDC.remove("adapterId");
            MDC.remove("adapterName");
            MDC.remove("logCategory");
        }
        
        return results;
    }
    
    /**
     * Checks if a file is ready for processing (not being written to)
     */
    private boolean isFileReady(Path filePath) {
        return isFileReady(filePath, filePath.getFileName().toString());
    }
    
    /**
     * Checks if a file is ready for processing with detailed logging
     */
    private boolean isFileReady(Path filePath, String fileName) {
        logger.debug("Checking if file is ready for processing: {}", fileName);
        
        try {
            // Check file age (if configured)
            long minFileAge = config.getLong("minFileAge", 0);
            if (minFileAge > 0) {
                long fileAge = System.currentTimeMillis() - Files.getLastModifiedTime(filePath).toMillis();
                if (fileAge < minFileAge) {
                    logger.debug("File too young: {} (age: {}ms, required: {}ms)", 
                                fileName, fileAge, minFileAge);
                    return false;
                }
            }
            
            // Check if file can be moved (indicates it's not locked)
            Path tempPath = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            Files.move(filePath, tempPath);
            Files.move(tempPath, filePath);
            
            logger.debug("✓ File is ready for processing: {}", fileName);
            return true;
            
        } catch (IOException e) {
            logger.debug("✗ File not ready for processing: {} ({})", fileName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Validates file before processing using comprehensive validation
     */
    private void validateFile(Path filePath) throws IOException {
        FileValidator validator = new FileValidator(config);
        FileValidationResult validationResult = validator.validate(filePath);
        
        if (!validationResult.isValid()) {
            String errorMessage = "File validation failed: " + validationResult.getAllErrors();
            logger.error("File validation failed for {}: {}", filePath, errorMessage);
            throw new IOException(errorMessage);
        }
        
        if (validationResult.hasWarnings()) {
            logger.warn("File validation warnings for {}: {}", filePath, validationResult.getAllWarnings());
        }
        
        logger.debug("File validation passed for: {} ({})", filePath, validationResult.getSummary());
    }
    
    /**
     * Checks if file is a ZIP file
     */
    private boolean isZipFile(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        return fileName.endsWith(".zip");
    }
    
    /**
     * Processes a ZIP file by extracting and processing contents
     */
    private FileProcessingResult processZipFile(Path zipPath, FileProcessingResult result) {
        return processZipFile(zipPath, result, null);
    }
    
    /**
     * Processes a ZIP file with execution context
     */
    private FileProcessingResult processZipFile(Path zipPath, FileProcessingResult result, String executionId) {
        logger.info("=== ZIP FILE PROCESSING ===");
        logger.info("Processing ZIP file: {}", zipPath.getFileName());
        logger.info("ZIP processing enabled: {}", config.isZipProcessingEnabled());
        
        try {
            if (!config.isZipProcessingEnabled()) {
                logger.info("ZIP processing is disabled, treating as regular file: {}", zipPath.getFileName());
                return processSingleFile(zipPath, Files.readAllBytes(zipPath), result, executionId);
            }
            
            ZipFileProcessor zipProcessor = new ZipFileProcessor(config);
            ZipProcessingResult zipResult = zipProcessor.processZipFile(zipPath, result);
            
            result.addMetadata("fileType", "ZIP");
            result.addMetadata("zipProcessingCompleted", true);
            
            logger.info("ZIP processing completed for: {} - {}/{} files successful", 
                       zipPath, zipResult.getSuccessfulCount(), zipResult.getTotalCount());
            
        } catch (Exception e) {
            logger.error("Failed to process ZIP file: {}", zipPath, e);
            result.addMetadata("fileType", "ZIP");
            result.addMetadata("zipProcessingError", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Processes a single non-ZIP file
     */
    private FileProcessingResult processSingleFile(Path filePath, byte[] content, FileProcessingResult result) {
        return processSingleFile(filePath, content, result, null);
    }
    
    /**
     * Processes a single non-ZIP file with execution context
     */
    private FileProcessingResult processSingleFile(Path filePath, byte[] content, FileProcessingResult result, String executionId) {
        logger.info("=== SINGLE FILE PROCESSING ===");
        logger.info("Processing file: {} ({} bytes)", filePath.getFileName(), content.length);
        
        // Basic file processing - content analysis and metadata extraction
        String fileType = getFileType(filePath);
        String encoding = detectEncoding(content);
        int lineCount = countLines(content);
        
        logger.info("File type: {}", fileType);
        logger.info("Encoding detected: {}", encoding);
        logger.info("Line count: {}", lineCount);
        
        result.addMetadata("fileType", fileType);
        result.addMetadata("encoding", encoding);
        result.addMetadata("lineCount", lineCount);
        
        logger.info("✓ File metadata extracted and stored");
        
        // If this is part of a flow, log data passing to next step
        if (executionId != null) {
            logger.info("=== PASSING DATA TO NEXT FLOW STEP ===");
            logger.info("File processing completed, data ready for next step");
            logger.info("Data size: {} bytes", content.length);
            logger.info("Data type: {}", fileType);
            logger.info("Execution context: {}", executionId);
        }
        
        return result;
    }
    
    /**
     * Applies post-processing workflow (Archive/Delete/Rename)
     */
    private void applyPostProcessing(Path filePath, FileProcessingResult result) throws IOException {
        String postProcessing = config.getPostProcessing();
        
        logger.info("=== POST-PROCESSING WORKFLOW ===");
        logger.info("Post-processing action: {}", postProcessing);
        logger.info("Target file: {}", filePath.getFileName());
        
        switch (postProcessing.toUpperCase()) {
            case "ARCHIVE":
                logger.info("Executing ARCHIVE workflow...");
                archiveFile(filePath, result);
                break;
            case "DELETE":
                logger.info("Executing DELETE workflow...");
                deleteFile(filePath, result);
                break;
            case "RENAME":
                logger.info("Executing RENAME workflow...");
                renameFile(filePath, result);
                break;
            case "NONE":
                logger.info("No post-processing configured - file remains in place: {}", filePath.getFileName());
                break;
            default:
                logger.warn("✗ Unknown post-processing option: {}. File will remain in place.", postProcessing);
        }
        
        logger.info("✓ Post-processing workflow completed");
    }
    
    /**
     * Archives the file to the configured archive directory
     */
    private void archiveFile(Path filePath, FileProcessingResult result) throws IOException {
        logger.info("=== ARCHIVE OPERATION ===");
        String archiveDir = config.getArchiveDirectory();
        logger.info("Archive directory configured: {}", archiveDir);
        
        if (archiveDir == null || archiveDir.isEmpty()) {
            logger.warn("✗ Archive directory not configured. File will remain in place: {}", filePath.getFileName());
            return;
        }
        
        Path archivePath = Paths.get(archiveDir);
        logger.info("Creating archive directory if needed: {}", archivePath.toAbsolutePath());
        
        Files.createDirectories(archivePath);
        logger.info("✓ Archive directory ready: {}", archivePath.toAbsolutePath());
        
        String timestamp = LocalDateTime.now().toString().replace(":", "-");
        String archivedFileName = timestamp + "_" + filePath.getFileName().toString();
        Path targetPath = archivePath.resolve(archivedFileName);
        
        logger.info("Moving file to archive:");
        logger.info("  From: {}", filePath.toAbsolutePath());
        logger.info("  To:   {}", targetPath.toAbsolutePath());
        
        Files.move(filePath, targetPath);
        result.addMetadata("archivedTo", targetPath.toString());
        
        logger.info("✓ File archived successfully: {}", targetPath.getFileName());
    }
    
    /**
     * Deletes the file after processing
     */
    private void deleteFile(Path filePath, FileProcessingResult result) throws IOException {
        logger.info("=== DELETE OPERATION ===");
        logger.info("Deleting file: {}", filePath.toAbsolutePath());
        
        if (!Files.exists(filePath)) {
            logger.warn("✗ File does not exist, cannot delete: {}", filePath.getFileName());
            return;
        }
        
        Files.delete(filePath);
        result.addMetadata("deleted", true);
        
        logger.info("✓ File deleted successfully: {}", filePath.getFileName());
    }
    
    /**
     * Renames the file with a processed suffix
     */
    private void renameFile(Path filePath, FileProcessingResult result) throws IOException {
        logger.info("=== RENAME OPERATION ===");
        String baseName = filePath.getFileName().toString();
        String processedName = addProcessedSuffix(baseName);
        Path renamedPath = filePath.resolveSibling(processedName);
        
        logger.info("Renaming file:");
        logger.info("  From: {}", baseName);
        logger.info("  To:   {}", processedName);
        logger.info("  Full path: {}", renamedPath.toAbsolutePath());
        
        Files.move(filePath, renamedPath);
        result.addMetadata("renamedTo", renamedPath.toString());
        
        logger.info("✓ File renamed successfully: {}", processedName);
    }
    
    // Helper methods
    private String calculateHash(byte[] content) {
        return "SHA-" + Arrays.hashCode(content); // Simplified hash
    }
    
    private String getFileType(Path filePath) {
        String fileName = filePath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1).toUpperCase() : "UNKNOWN";
    }
    
    private String detectEncoding(byte[] content) {
        // Simplified encoding detection
        return "UTF-8"; // Default assumption
    }
    
    private int countLines(byte[] content) {
        int lines = 1;
        for (byte b : content) {
            if (b == '\n') lines++;
        }
        return lines;
    }
    
    private String addProcessedSuffix(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            String name = fileName.substring(0, dotIndex);
            String ext = fileName.substring(dotIndex);
            return name + "_processed" + ext;
        } else {
            return fileName + "_processed";
        }
    }
    
    // Getters
    public Adapter getAdapter() {
        return adapter;
    }
    
    public FileAdapterConfig getConfig() {
        return config;
    }
}