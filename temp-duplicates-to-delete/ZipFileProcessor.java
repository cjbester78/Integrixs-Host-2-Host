package com.integrixs.adapters.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class ZipFileProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(ZipFileProcessor.class);
    
    private final FileAdapterConfig config;
    private final Path tempDirectory;
    
    public ZipFileProcessor(FileAdapterConfig config) throws IOException {
        this.config = config;
        this.tempDirectory = createTempDirectory();
    }
    
    /**
     * Processes a ZIP file by extracting its contents and processing each file
     */
    public ZipProcessingResult processZipFile(Path zipFilePath, FileProcessingResult parentResult) {
        return processZipFile(zipFilePath, parentResult, null);
    }
    
    /**
     * Processes a ZIP file with execution context for detailed logging
     */
    public ZipProcessingResult processZipFile(Path zipFilePath, FileProcessingResult parentResult, String executionId) {
        // Set execution context for logging
        if (executionId != null) {
            MDC.put("executionId", executionId);
            MDC.put("logCategory", "ADAPTER_EXECUTION");
        }
        logger.info("=== UNZIP UTILITY PROCESSING ===");
        logger.info("Starting ZIP file extraction: {}", zipFilePath.getFileName());
        try {
            logger.info("ZIP file size: {} bytes", Files.size(zipFilePath));
        } catch (Exception e) {
            logger.info("ZIP file size: unknown");
        }
        logger.info("Temp directory: {}", tempDirectory);
        
        ZipProcessingResult zipResult = new ZipProcessingResult();
        zipResult.setZipFilePath(zipFilePath);
        zipResult.setParentResult(parentResult);
        
        try {
            logger.info("=== STEP 1: ZIP CONTENT ANALYSIS ===");
            // Extract ZIP contents
            List<ExtractedFile> extractedFiles = extractZipContents(zipFilePath, executionId);
            zipResult.setExtractedFiles(extractedFiles);
            
            logger.info("=== STEP 2: EXTRACTION COMPLETE ===");
            logger.info("Successfully extracted {} files from ZIP", extractedFiles.size());
            
            // Log details of each extracted file
            for (int i = 0; i < extractedFiles.size(); i++) {
                ExtractedFile file = extractedFiles.get(i);
                logger.info("  {}. {} ({} bytes, compressed from {} bytes)", 
                           i + 1, file.getOriginalEntryName(), 
                           file.getOriginalSize(), file.getCompressedSize());
            }
            
            logger.info("=== STEP 3: PROCESSING EXTRACTED FILES ===");
            // Process each extracted file
            for (int i = 0; i < extractedFiles.size(); i++) {
                ExtractedFile extractedFile = extractedFiles.get(i);
                logger.info("Processing extracted file {}/{}: {}", 
                           i + 1, extractedFiles.size(), extractedFile.getOriginalEntryName());
                try {
                    FileProcessingResult fileResult = processExtractedFile(extractedFile, executionId);
                    zipResult.addFileResult(fileResult);
                    logger.info("✓ Successfully processed: {}", extractedFile.getOriginalEntryName());
                    
                } catch (Exception e) {
                    logger.error("✗ Failed to process extracted file: {}", extractedFile.getOriginalEntryName(), e);
                    
                    FileProcessingResult errorResult = new FileProcessingResult();
                    errorResult.setFilePath(extractedFile.getExtractedPath());
                    errorResult.markAsFailed("Error processing extracted file: " + e.getMessage());
                    zipResult.addFileResult(errorResult);
                }
            }
            
            logger.info("=== STEP 4: ZIP PROCESSING SUMMARY ===");
            // Update parent result with ZIP processing metadata
            updateParentResultWithZipInfo(parentResult, zipResult);
            
            logger.info("✓ ZIP processing completed successfully");
            logger.info("Total files extracted: {}", zipResult.getTotalCount());
            logger.info("Files processed successfully: {}", zipResult.getSuccessfulCount());
            logger.info("Files failed: {}", zipResult.getFailedCount());
            
            if (executionId != null) {
                logger.info("=== PASSING DATA TO RECEIVER ADAPTER ===");
                logger.info("ZIP extraction completed, {} files ready for receiver processing", zipResult.getSuccessfulCount());
                logger.info("Execution context: {}", executionId);
            }
            
        } catch (Exception e) {
            logger.error("=== ZIP PROCESSING FAILED ===");
            logger.error("✗ Failed to process ZIP file: {}", zipFilePath.getFileName(), e);
            logger.error("✗ Error: {}", e.getMessage());
            zipResult.setError("ZIP processing failed: " + e.getMessage());
        } finally {
            logger.info("=== CLEANUP ===");
            // Cleanup temp files
            cleanupTempFiles(zipResult.getExtractedFiles());
            logger.info("Temporary files cleaned up");
            
            // Clear MDC if we set it
            if (executionId != null) {
                MDC.remove("executionId");
                MDC.remove("logCategory");
            }
        }
        
        return zipResult;
    }
    
    /**
     * Extracts all files from a ZIP archive
     */
    private List<ExtractedFile> extractZipContents(Path zipFilePath) throws IOException {
        return extractZipContents(zipFilePath, null);
    }
    
    /**
     * Extracts all files from a ZIP archive with detailed logging
     */
    private List<ExtractedFile> extractZipContents(Path zipFilePath, String executionId) throws IOException {
        List<ExtractedFile> extractedFiles = new ArrayList<>();
        
        logger.info("Opening ZIP file for reading: {}", zipFilePath.getFileName());
        
        try (ZipFile zipFile = new ZipFile(zipFilePath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            logger.info("ZIP file opened successfully, scanning entries...");
            
            int totalEntries = 0;
            int processedEntries = 0;
            int skippedDirectories = 0;
            int skippedFiles = 0;
            
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                totalEntries++;
                
                if (entry.isDirectory()) {
                    skippedDirectories++;
                    logger.debug("Skipping directory: {}", entry.getName());
                    continue; // Skip directories
                }
                
                // Filter files based on pattern if configured
                if (shouldProcessEntry(entry)) {
                    logger.info("Extracting file: {} ({} bytes)", entry.getName(), entry.getSize());
                    ExtractedFile extractedFile = extractEntry(zipFile, entry);
                    extractedFiles.add(extractedFile);
                    processedEntries++;
                    logger.info("✓ Extracted: {} -> {}", entry.getName(), extractedFile.getExtractedPath().getFileName());
                } else {
                    skippedFiles++;
                    logger.debug("✗ Skipped file (filtered): {}", entry.getName());
                }
            }
            
            logger.info("ZIP scan completed:");
            logger.info("  Total entries: {}", totalEntries);
            logger.info("  Directories skipped: {}", skippedDirectories);
            logger.info("  Files extracted: {}", processedEntries);
            logger.info("  Files skipped: {}", skippedFiles);
        }
        
        return extractedFiles;
    }
    
    /**
     * Determines if a ZIP entry should be processed based on configuration
     */
    private boolean shouldProcessEntry(ZipEntry entry) {
        String entryName = entry.getName();
        
        // Skip hidden files and system files
        if (entryName.startsWith(".") || entryName.startsWith("__MACOSX")) {
            return false;
        }
        
        // Check file pattern if configured for ZIP contents
        String pattern = config.getString("zipFilePattern");
        if (pattern != null && !pattern.isEmpty()) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            Path entryPath = Paths.get(entryName);
            return matcher.matches(entryPath.getFileName());
        }
        
        // Check file size limits
        long maxSize = config.getMaxFileSize();
        if (maxSize > 0 && entry.getSize() > maxSize) {
            logger.warn("Skipping large file in ZIP: {} (size: {} bytes)", entryName, entry.getSize());
            return false;
        }
        
        return true;
    }
    
    /**
     * Extracts a single ZIP entry to a temporary file
     */
    private ExtractedFile extractEntry(ZipFile zipFile, ZipEntry entry) throws IOException {
        // Create safe filename for extraction
        String safeFileName = createSafeFileName(entry.getName());
        Path extractedPath = tempDirectory.resolve(safeFileName);
        
        // Ensure parent directories exist
        Files.createDirectories(extractedPath.getParent());
        
        // Extract file content
        try (InputStream entryStream = zipFile.getInputStream(entry);
             OutputStream outputStream = Files.newOutputStream(extractedPath)) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;
            
            while ((bytesRead = entryStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
                
                // Safety check to prevent zip bombs
                if (totalBytes > config.getMaxFileSize() * 2) {
                    throw new IOException("Extracted file too large, possible zip bomb: " + entry.getName());
                }
            }
        }
        
        ExtractedFile extractedFile = new ExtractedFile();
        extractedFile.setOriginalEntryName(entry.getName());
        extractedFile.setExtractedPath(extractedPath);
        extractedFile.setOriginalSize(entry.getSize());
        extractedFile.setCompressedSize(entry.getCompressedSize());
        extractedFile.setLastModified(entry.getLastModifiedTime());
        
        return extractedFile;
    }
    
    /**
     * Processes an extracted file using the same logic as regular files
     */
    private FileProcessingResult processExtractedFile(ExtractedFile extractedFile) throws IOException {
        return processExtractedFile(extractedFile, null);
    }
    
    /**
     * Processes an extracted file with execution context
     */
    private FileProcessingResult processExtractedFile(ExtractedFile extractedFile, String executionId) throws IOException {
        Path filePath = extractedFile.getExtractedPath();
        
        logger.debug("Processing extracted file: {}", extractedFile.getOriginalEntryName());
        logger.debug("Temporary file location: {}", filePath);
        
        FileProcessingResult result = new FileProcessingResult();
        result.setFilePath(filePath);
        result.setAdapterInterfaceId(null); // Will be set by parent
        
        // Read file content
        byte[] content = Files.readAllBytes(filePath);
        result.setFileSize(content.length);
        logger.debug("Read {} bytes from extracted file: {}", content.length, extractedFile.getOriginalEntryName());
        
        // Add ZIP-specific metadata
        result.addMetadata("extractedFromZip", true);
        result.addMetadata("originalEntryName", extractedFile.getOriginalEntryName());
        result.addMetadata("originalSize", extractedFile.getOriginalSize());
        result.addMetadata("compressedSize", extractedFile.getCompressedSize());
        
        // Basic file processing
        result.addMetadata("fileType", getFileType(filePath));
        result.addMetadata("encoding", detectEncoding(content));
        result.addMetadata("lineCount", countLines(content));
        
        result.markAsSuccess();
        
        logger.debug("✓ Processed extracted file: {} ({} bytes)", extractedFile.getOriginalEntryName(), content.length);
        
        return result;
    }
    
    /**
     * Updates parent result with ZIP processing information
     */
    private void updateParentResultWithZipInfo(FileProcessingResult parentResult, ZipProcessingResult zipResult) {
        parentResult.addMetadata("zipExtractedFiles", zipResult.getTotalCount());
        parentResult.addMetadata("zipSuccessfulFiles", zipResult.getSuccessfulCount());
        parentResult.addMetadata("zipFailedFiles", zipResult.getFailedCount());
        parentResult.addMetadata("zipProcessingError", zipResult.getError());
        
        // Add individual file results as metadata
        List<Map<String, Object>> fileResults = new ArrayList<>();
        for (FileProcessingResult fileResult : zipResult.getFileResults()) {
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("fileName", fileResult.getFileName());
            resultMap.put("status", fileResult.getStatus().name());
            resultMap.put("fileSize", fileResult.getFileSize());
            resultMap.put("processingTime", fileResult.getProcessingTimeMs());
            if (fileResult.getErrorMessage() != null) {
                resultMap.put("error", fileResult.getErrorMessage());
            }
            fileResults.add(resultMap);
        }
        parentResult.addMetadata("zipFileResults", fileResults);
    }
    
    /**
     * Creates a safe filename for extraction to prevent path traversal attacks
     */
    private String createSafeFileName(String originalName) {
        // Remove path traversal attempts
        String safeName = originalName.replaceAll("\\.\\./", "").replaceAll("\\.\\.", "");
        
        // Replace invalid filename characters
        safeName = safeName.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        // Ensure filename is not too long
        if (safeName.length() > 200) {
            String extension = getFileExtension(safeName);
            safeName = safeName.substring(0, 200 - extension.length()) + extension;
        }
        
        // Add timestamp to ensure uniqueness
        String timestamp = String.valueOf(System.currentTimeMillis());
        String extension = getFileExtension(safeName);
        String nameWithoutExtension = safeName.substring(0, safeName.length() - extension.length());
        
        return nameWithoutExtension + "_" + timestamp + extension;
    }
    
    /**
     * Cleans up temporary extracted files
     */
    private void cleanupTempFiles(List<ExtractedFile> extractedFiles) {
        logger.info("Cleaning up {} temporary files", extractedFiles == null ? 0 : extractedFiles.size());
        
        if (extractedFiles == null) {
            return;
        }
        int deleted = 0;
        for (ExtractedFile extractedFile : extractedFiles) {
            try {
                if (Files.deleteIfExists(extractedFile.getExtractedPath())) {
                    deleted++;
                    logger.debug("✓ Deleted temp file: {}", extractedFile.getExtractedPath().getFileName());
                }
            } catch (IOException e) {
                logger.warn("✗ Failed to cleanup temp file: {}", extractedFile.getExtractedPath().getFileName(), e);
            }
        }
        
        logger.info("✓ Cleaned up {}/{} temporary files", deleted, extractedFiles.size());
        
        // Try to cleanup temp directory
        try {
            if (Files.deleteIfExists(tempDirectory)) {
                logger.debug("✓ Deleted temp directory: {}", tempDirectory.getFileName());
            }
        } catch (IOException e) {
            logger.debug("Temp directory not empty or deletion failed: {}", tempDirectory.getFileName());
        }
    }
    
    /**
     * Creates a temporary directory for ZIP extraction
     */
    private Path createTempDirectory() throws IOException {
        Path tempDir = Files.createTempDirectory("h2h-zip-extract-");
        logger.debug("Created temp directory for ZIP extraction: {}", tempDir);
        return tempDir;
    }
    
    // Helper methods
    private String getFileType(Path filePath) {
        String fileName = filePath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1).toUpperCase() : "UNKNOWN";
    }
    
    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex) : "";
    }
    
    private String detectEncoding(byte[] content) {
        return "UTF-8"; // Simplified encoding detection
    }
    
    private int countLines(byte[] content) {
        int lines = 1;
        for (byte b : content) {
            if (b == '\n') lines++;
        }
        return lines;
    }
}