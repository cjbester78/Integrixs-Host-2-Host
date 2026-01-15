package com.integrixs.core.service.utility;

import com.integrixs.shared.model.FlowExecutionStep;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;

/**
 * Utility processor for compression operations
 * Handles ZIP compress, extract operations following Single Responsibility Principle
 */
@Service
public class CompressionUtilityProcessor extends AbstractUtilityProcessor {
    
    private static final String UTILITY_TYPE = "COMPRESSION";
    private static final int BUFFER_SIZE = 8192;
    
    @Override
    public String getUtilityType() {
        return UTILITY_TYPE;
    }
    
    @Override
    public Map<String, Object> executeUtility(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        logExecutionStart(step, "Compression utility");
        long startTime = System.currentTimeMillis();
        
        try {
            String operation = getConfigValue(configuration, "operation", "compress");
            
            switch (operation.toLowerCase()) {
                case "compress":
                case "zip":
                    return executeCompress(step, context, configuration);
                case "extract":
                case "unzip":
                    return executeExtract(step, context, configuration);
                case "list":
                    return executeListContents(step, context, configuration);
                case "validate":
                    return executeValidateArchive(step, context, configuration);
                default:
                    throw new IllegalArgumentException("Unsupported compression operation: " + operation);
            }
            
        } catch (Exception e) {
            logExecutionError(step, "Compression utility", e);
            return createErrorResult("Compression utility execution failed: " + e.getMessage(), e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logExecutionComplete(step, "Compression utility", duration);
        }
    }
    
    /**
     * Execute compression (ZIP) operation
     */
    private Map<String, Object> executeCompress(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        try {
            validateConfiguration(configuration, "sourceDirectory", "targetFile");
            
            String sourceDirectory = (String) configuration.get("sourceDirectory");
            String targetFile = (String) configuration.get("targetFile");
            String filePattern = getConfigValue(configuration, "filePattern", "*");
            Integer compressionLevel = getConfigValue(configuration, "compressionLevel", Deflater.DEFAULT_COMPRESSION);
            boolean includeDirectories = getConfigValue(configuration, "includeDirectories", true);
            boolean deleteSource = getConfigValue(configuration, "deleteSource", false);
            String password = getConfigValue(configuration, "password", null);
            
            validateFilePath(sourceDirectory);
            validateFilePath(targetFile);
            
            Path sourcePath = Paths.get(sourceDirectory);
            Path targetPath = Paths.get(targetFile);
            
            if (!Files.exists(sourcePath)) {
                return createErrorResult("Source directory does not exist: " + sourceDirectory, null);
            }
            
            ensureDirectoryExists(targetPath.getParent().toString());
            
            // Collect files to compress
            List<Path> filesToCompress = new ArrayList<>();
            Set<Path> directoriesToInclude = new HashSet<>();
            
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (matchesPattern(file.getFileName().toString(), filePattern)) {
                        filesToCompress.add(file);
                        
                        // Track directories if needed
                        if (includeDirectories) {
                            Path dir = file.getParent();
                            while (dir != null && !dir.equals(sourcePath)) {
                                directoriesToInclude.add(dir);
                                dir = dir.getParent();
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (includeDirectories && !dir.equals(sourcePath)) {
                        directoriesToInclude.add(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            
            if (filesToCompress.isEmpty()) {
                return createSuccessResult("No files found matching pattern: " + filePattern, 
                    Collections.singletonMap("filesCompressed", 0));
            }
            
            // Create ZIP archive
            long originalSize = 0;
            long compressedSize = 0;
            int filesCompressed = 0;
            int directoriesCreated = 0;
            List<String> compressedFiles = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(targetPath))) {
                zos.setLevel(compressionLevel);
                
                // Add directories first
                if (includeDirectories) {
                    List<Path> sortedDirs = new ArrayList<>(directoriesToInclude);
                    sortedDirs.sort(Comparator.naturalOrder());
                    
                    for (Path dir : sortedDirs) {
                        try {
                            String relativePath = sourcePath.relativize(dir).toString().replace('\\', '/') + "/";
                            ZipEntry entry = new ZipEntry(relativePath);
                            entry.setTime(Files.getLastModifiedTime(dir).toMillis());
                            zos.putNextEntry(entry);
                            zos.closeEntry();
                            directoriesCreated++;
                            logger.debug("Added directory to ZIP: {}", relativePath);
                        } catch (Exception e) {
                            String error = "Failed to add directory " + dir + ": " + e.getMessage();
                            errors.add(error);
                            logger.error("Compression error: {}", error, e);
                        }
                    }
                }
                
                // Add files
                for (Path file : filesToCompress) {
                    try {
                        String relativePath = sourcePath.relativize(file).toString().replace('\\', '/');
                        ZipEntry entry = new ZipEntry(relativePath);
                        entry.setTime(Files.getLastModifiedTime(file).toMillis());
                        zos.putNextEntry(entry);
                        
                        long fileSize = Files.size(file);
                        try (InputStream fis = Files.newInputStream(file);
                             BufferedInputStream bis = new BufferedInputStream(fis)) {
                            
                            byte[] buffer = new byte[BUFFER_SIZE];
                            int bytesRead;
                            
                            while ((bytesRead = bis.read(buffer)) != -1) {
                                zos.write(buffer, 0, bytesRead);
                            }
                        }
                        
                        zos.closeEntry();
                        filesCompressed++;
                        originalSize += fileSize;
                        compressedFiles.add(relativePath);
                        
                        logger.debug("Added file to ZIP: {} ({} bytes)", relativePath, fileSize);
                        
                    } catch (Exception e) {
                        String error = "Failed to compress file " + file + ": " + e.getMessage();
                        errors.add(error);
                        logger.error("Compression error: {}", error, e);
                    }
                }
            }
            
            compressedSize = Files.size(targetPath);
            double compressionRatio = originalSize > 0 ? (double) compressedSize / originalSize : 0;
            
            // Delete source files if requested
            List<String> deletedFiles = new ArrayList<>();
            if (deleteSource && filesCompressed > 0) {
                for (Path file : filesToCompress) {
                    try {
                        Files.delete(file);
                        deletedFiles.add(file.toString());
                        logger.debug("Deleted source file: {}", file);
                    } catch (Exception e) {
                        logger.warn("Failed to delete source file: {}", file, e);
                    }
                }
                
                // Delete empty directories if possible
                if (includeDirectories) {
                    List<Path> sortedDirs = new ArrayList<>(directoriesToInclude);
                    sortedDirs.sort(Collections.reverseOrder()); // Delete deepest first
                    
                    for (Path dir : sortedDirs) {
                        try {
                            if (Files.exists(dir) && isDirectoryEmpty(dir)) {
                                Files.delete(dir);
                                logger.debug("Deleted empty directory: {}", dir);
                            }
                        } catch (Exception e) {
                            // Ignore - directory not empty or other issue
                        }
                    }
                }
            }
            
            // Update execution context
            updateExecutionContext(context, "compressionResult", targetFile);
            updateExecutionContext(context, "compressionStats", Map.of(
                "filesCompressed", filesCompressed,
                "originalSize", originalSize,
                "compressedSize", compressedSize,
                "compressionRatio", compressionRatio
            ));
            
            // Create result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("targetFile", targetFile);
            resultData.put("filesCompressed", filesCompressed);
            resultData.put("directoriesCreated", directoriesCreated);
            resultData.put("originalSize", originalSize);
            resultData.put("compressedSize", compressedSize);
            resultData.put("compressionRatio", compressionRatio);
            resultData.put("compressionLevel", compressionLevel);
            resultData.put("compressedFiles", compressedFiles);
            resultData.put("sourceDirectory", sourceDirectory);
            resultData.put("deletedFiles", deletedFiles);
            resultData.put("deletedSource", deleteSource);
            
            if (!errors.isEmpty()) {
                resultData.put("errors", errors);
            }
            
            String message = String.format("Successfully compressed %d files and %d directories (%.1f%% compression)", 
                filesCompressed, directoriesCreated, (1 - compressionRatio) * 100);
            
            return createSuccessResult(message, resultData);
            
        } catch (Exception e) {
            logger.error("Compression failed", e);
            return createErrorResult("Compression failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute extraction (UNZIP) operation
     */
    private Map<String, Object> executeExtract(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        try {
            validateConfiguration(configuration, "sourceFile", "targetDirectory");
            
            String sourceFile = (String) configuration.get("sourceFile");
            String targetDirectory = (String) configuration.get("targetDirectory");
            String filePattern = getConfigValue(configuration, "filePattern", "*");
            boolean overwrite = getConfigValue(configuration, "overwrite", false);
            boolean preserveTimestamps = getConfigValue(configuration, "preserveTimestamps", true);
            boolean deleteSource = getConfigValue(configuration, "deleteSource", false);
            String password = getConfigValue(configuration, "password", null);
            
            validateFilePath(sourceFile);
            validateFilePath(targetDirectory);
            
            Path sourcePath = Paths.get(sourceFile);
            Path targetPath = Paths.get(targetDirectory);
            
            if (!Files.exists(sourcePath)) {
                return createErrorResult("Source file does not exist: " + sourceFile, null);
            }
            
            ensureDirectoryExists(targetDirectory);
            
            // Extract ZIP archive
            long originalSize = Files.size(sourcePath);
            long extractedSize = 0;
            int filesExtracted = 0;
            int directoriesCreated = 0;
            List<String> extractedFiles = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(sourcePath))) {
                ZipEntry entry;
                
                while ((entry = zis.getNextEntry()) != null) {
                    try {
                        String entryName = entry.getName();
                        
                        // Check if entry matches pattern (for files)
                        if (!entry.isDirectory() && !matchesPattern(Paths.get(entryName).getFileName().toString(), filePattern)) {
                            continue;
                        }
                        
                        // Security check - prevent directory traversal
                        Path entryPath = targetPath.resolve(entryName).normalize();
                        if (!entryPath.startsWith(targetPath)) {
                            throw new SecurityException("Entry would escape target directory: " + entryName);
                        }
                        
                        if (entry.isDirectory()) {
                            // Create directory
                            if (!Files.exists(entryPath)) {
                                Files.createDirectories(entryPath);
                                directoriesCreated++;
                                logger.debug("Created directory: {}", entryPath);
                            }
                        } else {
                            // Extract file
                            if (Files.exists(entryPath) && !overwrite) {
                                logger.warn("Skipping existing file: {}", entryPath);
                                continue;
                            }
                            
                            // Ensure parent directories exist
                            Path parentDir = entryPath.getParent();
                            if (parentDir != null && !Files.exists(parentDir)) {
                                Files.createDirectories(parentDir);
                            }
                            
                            // Extract file content
                            try (OutputStream fos = Files.newOutputStream(entryPath);
                                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                                
                                byte[] buffer = new byte[BUFFER_SIZE];
                                int bytesRead;
                                long fileSize = 0;
                                
                                while ((bytesRead = zis.read(buffer)) != -1) {
                                    bos.write(buffer, 0, bytesRead);
                                    fileSize += bytesRead;
                                }
                                
                                extractedSize += fileSize;
                                filesExtracted++;
                                extractedFiles.add(entryPath.toString());
                                
                                logger.debug("Extracted file: {} ({} bytes)", entryPath, fileSize);
                            }
                            
                            // Preserve timestamps if requested
                            if (preserveTimestamps && entry.getTime() != -1) {
                                Files.setLastModifiedTime(entryPath, 
                                    java.nio.file.attribute.FileTime.fromMillis(entry.getTime()));
                            }
                        }
                        
                    } catch (Exception e) {
                        String error = "Failed to extract entry " + entry.getName() + ": " + e.getMessage();
                        errors.add(error);
                        logger.error("Extraction error: {}", error, e);
                    } finally {
                        zis.closeEntry();
                    }
                }
            }
            
            // Delete source file if requested
            if (deleteSource && filesExtracted > 0) {
                Files.delete(sourcePath);
                logger.debug("Deleted source file: {}", sourcePath);
            }
            
            // Update execution context
            updateExecutionContext(context, "extractionResult", targetDirectory);
            updateExecutionContext(context, "extractionStats", Map.of(
                "filesExtracted", filesExtracted,
                "directoriesCreated", directoriesCreated,
                "extractedSize", extractedSize
            ));
            
            // Create result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("sourceFile", sourceFile);
            resultData.put("targetDirectory", targetDirectory);
            resultData.put("filesExtracted", filesExtracted);
            resultData.put("directoriesCreated", directoriesCreated);
            resultData.put("originalSize", originalSize);
            resultData.put("extractedSize", extractedSize);
            resultData.put("extractedFiles", extractedFiles);
            resultData.put("overwrite", overwrite);
            resultData.put("preserveTimestamps", preserveTimestamps);
            resultData.put("deletedSource", deleteSource);
            
            if (!errors.isEmpty()) {
                resultData.put("errors", errors);
            }
            
            String message = String.format("Successfully extracted %d files and created %d directories", 
                filesExtracted, directoriesCreated);
            
            return createSuccessResult(message, resultData);
            
        } catch (Exception e) {
            logger.error("Extraction failed", e);
            return createErrorResult("Extraction failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute list contents operation
     */
    private Map<String, Object> executeListContents(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        try {
            validateConfiguration(configuration, "sourceFile");
            
            String sourceFile = (String) configuration.get("sourceFile");
            String filePattern = getConfigValue(configuration, "filePattern", "*");
            boolean includeDirectories = getConfigValue(configuration, "includeDirectories", true);
            
            validateFilePath(sourceFile);
            
            Path sourcePath = Paths.get(sourceFile);
            
            if (!Files.exists(sourcePath)) {
                return createErrorResult("Source file does not exist: " + sourceFile, null);
            }
            
            // List ZIP contents
            List<Map<String, Object>> entries = new ArrayList<>();
            long totalSize = 0;
            long compressedSize = 0;
            int fileCount = 0;
            int directoryCount = 0;
            
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(sourcePath))) {
                ZipEntry entry;
                
                while ((entry = zis.getNextEntry()) != null) {
                    try {
                        String entryName = entry.getName();
                        boolean isDirectory = entry.isDirectory();
                        
                        // Filter by pattern and directory inclusion
                        if (!isDirectory && !matchesPattern(Paths.get(entryName).getFileName().toString(), filePattern)) {
                            continue;
                        }
                        
                        if (isDirectory && !includeDirectories) {
                            continue;
                        }
                        
                        Map<String, Object> entryInfo = new HashMap<>();
                        entryInfo.put("name", entryName);
                        entryInfo.put("isDirectory", isDirectory);
                        entryInfo.put("size", entry.getSize());
                        entryInfo.put("compressedSize", entry.getCompressedSize());
                        entryInfo.put("method", entry.getMethod() == ZipEntry.DEFLATED ? "DEFLATED" : "STORED");
                        entryInfo.put("crc", entry.getCrc());
                        
                        if (entry.getTime() != -1) {
                            entryInfo.put("lastModified", new Date(entry.getTime()).toString());
                        }
                        
                        if (entry.getComment() != null) {
                            entryInfo.put("comment", entry.getComment());
                        }
                        
                        entries.add(entryInfo);
                        
                        if (isDirectory) {
                            directoryCount++;
                        } else {
                            fileCount++;
                            totalSize += entry.getSize();
                            compressedSize += entry.getCompressedSize();
                        }
                        
                    } finally {
                        zis.closeEntry();
                    }
                }
            }
            
            // Calculate compression statistics
            double compressionRatio = totalSize > 0 ? (double) compressedSize / totalSize : 0;
            
            // Update execution context
            updateExecutionContext(context, "zipContents", entries);
            updateExecutionContext(context, "zipStats", Map.of(
                "fileCount", fileCount,
                "directoryCount", directoryCount,
                "totalSize", totalSize,
                "compressedSize", compressedSize
            ));
            
            // Create result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("sourceFile", sourceFile);
            resultData.put("entries", entries);
            resultData.put("fileCount", fileCount);
            resultData.put("directoryCount", directoryCount);
            resultData.put("totalSize", totalSize);
            resultData.put("compressedSize", compressedSize);
            resultData.put("compressionRatio", compressionRatio);
            resultData.put("archiveSize", Files.size(sourcePath));
            
            String message = String.format("Listed %d files and %d directories from archive", 
                fileCount, directoryCount);
            
            return createSuccessResult(message, resultData);
            
        } catch (Exception e) {
            logger.error("List contents failed", e);
            return createErrorResult("List contents failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute validate archive operation
     */
    private Map<String, Object> executeValidateArchive(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        try {
            validateConfiguration(configuration, "sourceFile");
            
            String sourceFile = (String) configuration.get("sourceFile");
            boolean validateCrc = getConfigValue(configuration, "validateCrc", true);
            
            validateFilePath(sourceFile);
            
            Path sourcePath = Paths.get(sourceFile);
            
            if (!Files.exists(sourcePath)) {
                return createErrorResult("Source file does not exist: " + sourceFile, null);
            }
            
            // Validate ZIP archive
            boolean isValid = true;
            List<String> validationErrors = new ArrayList<>();
            List<Map<String, Object>> entryResults = new ArrayList<>();
            int validEntries = 0;
            int invalidEntries = 0;
            
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(sourcePath))) {
                ZipEntry entry;
                
                while ((entry = zis.getNextEntry()) != null) {
                    Map<String, Object> entryResult = new HashMap<>();
                    entryResult.put("name", entry.getName());
                    entryResult.put("isDirectory", entry.isDirectory());
                    
                    boolean entryValid = true;
                    List<String> entryErrors = new ArrayList<>();
                    
                    try {
                        // Read entry content to validate
                        if (!entry.isDirectory()) {
                            byte[] buffer = new byte[BUFFER_SIZE];
                            CRC32 crc = validateCrc ? new CRC32() : null;
                            long actualSize = 0;
                            
                            while (zis.read(buffer) != -1) {
                                actualSize += buffer.length;
                                if (crc != null) {
                                    crc.update(buffer);
                                }
                            }
                            
                            // Validate size
                            if (entry.getSize() != -1 && entry.getSize() != actualSize) {
                                entryValid = false;
                                entryErrors.add(String.format("Size mismatch: expected %d, actual %d", 
                                    entry.getSize(), actualSize));
                            }
                            
                            // Validate CRC
                            if (validateCrc && entry.getCrc() != -1 && crc.getValue() != entry.getCrc()) {
                                entryValid = false;
                                entryErrors.add(String.format("CRC mismatch: expected %d, actual %d", 
                                    entry.getCrc(), crc.getValue()));
                            }
                            
                            entryResult.put("actualSize", actualSize);
                            if (crc != null) {
                                entryResult.put("actualCrc", crc.getValue());
                            }
                        }
                        
                    } catch (Exception e) {
                        entryValid = false;
                        entryErrors.add("Validation error: " + e.getMessage());
                    }
                    
                    entryResult.put("valid", entryValid);
                    if (!entryErrors.isEmpty()) {
                        entryResult.put("errors", entryErrors);
                        validationErrors.addAll(entryErrors);
                    }
                    
                    entryResults.add(entryResult);
                    
                    if (entryValid) {
                        validEntries++;
                    } else {
                        invalidEntries++;
                        isValid = false;
                    }
                    
                    zis.closeEntry();
                }
                
            } catch (Exception e) {
                isValid = false;
                validationErrors.add("Archive structure error: " + e.getMessage());
            }
            
            // Update execution context
            updateExecutionContext(context, "archiveValidation", Map.of(
                "isValid", isValid,
                "validEntries", validEntries,
                "invalidEntries", invalidEntries
            ));
            
            // Create result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("sourceFile", sourceFile);
            resultData.put("isValid", isValid);
            resultData.put("validEntries", validEntries);
            resultData.put("invalidEntries", invalidEntries);
            resultData.put("totalEntries", validEntries + invalidEntries);
            resultData.put("entryResults", entryResults);
            resultData.put("validateCrc", validateCrc);
            
            if (!validationErrors.isEmpty()) {
                resultData.put("errors", validationErrors);
            }
            
            String message = String.format("Archive validation %s - %d valid, %d invalid entries", 
                isValid ? "passed" : "failed", validEntries, invalidEntries);
            
            return isValid ? createSuccessResult(message, resultData) : 
                createResult(false, message, resultData);
            
        } catch (Exception e) {
            logger.error("Archive validation failed", e);
            return createErrorResult("Archive validation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if filename matches pattern
     */
    private boolean matchesPattern(String filename, String pattern) {
        if (pattern == null || "*".equals(pattern)) {
            return true;
        }
        
        // Simple wildcard matching
        if (pattern.startsWith("*.")) {
            String extension = pattern.substring(2);
            return filename.toLowerCase().endsWith("." + extension.toLowerCase());
        }
        
        return filename.matches(pattern.replace("*", ".*"));
    }
    
    /**
     * Check if directory is empty
     */
    private boolean isDirectoryEmpty(Path directory) throws IOException {
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(directory)) {
            return !directoryStream.iterator().hasNext();
        }
    }
}