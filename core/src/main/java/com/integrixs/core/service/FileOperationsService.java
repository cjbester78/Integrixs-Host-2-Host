package com.integrixs.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Service for file operations with proper OOP design.
 * Follows OOP principles:
 * - Single Responsibility: Only handles file operations
 * - Dependency Injection: Injectable Spring service
 * - Immutability: Result objects are immutable
 * - Error Handling: Proper exception handling and validation
 */
@Service
public class FileOperationsService {
    
    private static final Logger logger = LoggerFactory.getLogger(FileOperationsService.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    /**
     * Immutable file operation result
     */
    public static class FileOperationResult {
        private final boolean successful;
        private final String message;
        private final Path resultPath;
        private final Exception error;
        
        private FileOperationResult(boolean successful, String message, Path resultPath, Exception error) {
            this.successful = successful;
            this.message = message;
            this.resultPath = resultPath;
            this.error = error;
        }
        
        public static FileOperationResult success(String message, Path resultPath) {
            return new FileOperationResult(true, message, resultPath, null);
        }
        
        public static FileOperationResult failure(String message, Exception error) {
            return new FileOperationResult(false, message, null, error);
        }
        
        public boolean isSuccessful() { return successful; }
        public String getMessage() { return message; }
        public Optional<Path> getResultPath() { return Optional.ofNullable(resultPath); }
        public Optional<Exception> getError() { return Optional.ofNullable(error); }
    }
    
    /**
     * Immutable file search criteria
     */
    public static class FileSearchCriteria {
        private final Path directory;
        private final String pattern;
        private final boolean recursive;
        private final String[] allowedExtensions;
        
        private FileSearchCriteria(Path directory, String pattern, boolean recursive, String[] allowedExtensions) {
            this.directory = directory;
            this.pattern = pattern;
            this.recursive = recursive;
            this.allowedExtensions = allowedExtensions != null ? allowedExtensions.clone() : null;
        }
        
        public static FileSearchCriteria of(Path directory, String pattern) {
            return new FileSearchCriteria(directory, pattern, false, null);
        }
        
        public static FileSearchCriteria recursive(Path directory, String pattern) {
            return new FileSearchCriteria(directory, pattern, true, null);
        }
        
        public static FileSearchCriteria withExtensions(Path directory, String pattern, String... extensions) {
            return new FileSearchCriteria(directory, pattern, false, extensions);
        }
        
        public Path getDirectory() { return directory; }
        public String getPattern() { return pattern; }
        public boolean isRecursive() { return recursive; }
        public Optional<String[]> getAllowedExtensions() { 
            return allowedExtensions != null ? Optional.of(allowedExtensions.clone()) : Optional.empty(); 
        }
    }
    
    /**
     * File archiving options
     */
    public static class ArchiveOptions {
        private final boolean addTimestamp;
        private final boolean createBackup;
        private final boolean replaceExisting;
        
        private ArchiveOptions(boolean addTimestamp, boolean createBackup, boolean replaceExisting) {
            this.addTimestamp = addTimestamp;
            this.createBackup = createBackup;
            this.replaceExisting = replaceExisting;
        }
        
        public static ArchiveOptions defaultOptions() {
            return new ArchiveOptions(true, false, false);
        }
        
        public static ArchiveOptions withTimestamp() {
            return new ArchiveOptions(true, false, false);
        }
        
        public static ArchiveOptions withBackup() {
            return new ArchiveOptions(false, true, false);
        }
        
        public static ArchiveOptions replaceExisting() {
            return new ArchiveOptions(false, false, true);
        }
        
        public boolean shouldAddTimestamp() { return addTimestamp; }
        public boolean shouldCreateBackup() { return createBackup; }
        public boolean shouldReplaceExisting() { return replaceExisting; }
    }
    
    /**
     * Find files matching search criteria
     */
    public List<Path> findFiles(FileSearchCriteria criteria) {
        validateSearchCriteria(criteria);
        
        if (!Files.exists(criteria.getDirectory())) {
            logger.warn("Directory does not exist: {}", criteria.getDirectory());
            return List.of();
        }
        
        if (!Files.isDirectory(criteria.getDirectory())) {
            logger.warn("Path is not a directory: {}", criteria.getDirectory());
            return List.of();
        }
        
        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + criteria.getPattern());
            int maxDepth = criteria.isRecursive() ? Integer.MAX_VALUE : 1;
            
            try (Stream<Path> files = Files.walk(criteria.getDirectory(), maxDepth)) {
                List<Path> matchingFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> matcher.matches(path.getFileName()))
                    .filter(path -> matchesExtensionCriteria(path, criteria))
                    .sorted()
                    .toList();
                    
                logger.debug("Found {} files matching pattern '{}' in directory '{}'", 
                           matchingFiles.size(), criteria.getPattern(), criteria.getDirectory());
                           
                return matchingFiles;
            }
            
        } catch (IOException e) {
            logger.error("Error searching for files in directory '{}': {}", 
                        criteria.getDirectory(), e.getMessage(), e);
            return List.of();
        }
    }
    
    /**
     * Check if filename matches pattern
     */
    public boolean matchesPattern(String fileName, String pattern) {
        if (fileName == null || pattern == null) {
            return false;
        }
        
        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            return matcher.matches(Paths.get(fileName));
        } catch (Exception e) {
            logger.warn("Error matching pattern '{}' against filename '{}': {}", pattern, fileName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Ensure directory exists with proper error handling
     */
    public FileOperationResult ensureDirectory(Path directory) {
        if (directory == null) {
            return FileOperationResult.failure("Directory path cannot be null", 
                new IllegalArgumentException("Directory path is null"));
        }
        
        try {
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
                logger.debug("Created directory: {}", directory);
                return FileOperationResult.success("Directory created successfully", directory);
            } else if (!Files.isDirectory(directory)) {
                return FileOperationResult.failure("Path exists but is not a directory: " + directory,
                    new IOException("Path is not a directory"));
            } else {
                return FileOperationResult.success("Directory already exists", directory);
            }
        } catch (IOException e) {
            logger.error("Failed to create directory '{}': {}", directory, e.getMessage(), e);
            return FileOperationResult.failure("Failed to create directory: " + e.getMessage(), e);
        }
    }
    
    /**
     * Archive file with configurable options
     */
    public FileOperationResult archiveFile(Path sourceFile, Path archiveDirectory, ArchiveOptions options) {
        if (!Files.exists(sourceFile) || !Files.isRegularFile(sourceFile)) {
            return FileOperationResult.failure("Source file does not exist or is not a regular file",
                new IllegalArgumentException("Invalid source file: " + sourceFile));
        }
        
        // Ensure archive directory exists
        FileOperationResult dirResult = ensureDirectory(archiveDirectory);
        if (!dirResult.isSuccessful()) {
            return dirResult;
        }
        
        try {
            String fileName = sourceFile.getFileName().toString();
            
            if (options.shouldAddTimestamp()) {
                fileName = addTimestampToFileName(fileName);
            }
            
            Path targetPath = archiveDirectory.resolve(fileName);
            
            if (Files.exists(targetPath) && !options.shouldReplaceExisting()) {
                return FileOperationResult.failure("Target file already exists and replace not allowed",
                    new IOException("File already exists: " + targetPath));
            }
            
            if (options.shouldCreateBackup() && Files.exists(targetPath)) {
                createBackup(targetPath);
            }
            
            Path result = Files.move(sourceFile, targetPath, 
                options.shouldReplaceExisting() ? StandardCopyOption.REPLACE_EXISTING : StandardCopyOption.ATOMIC_MOVE);
            
            logger.info("Archived file from '{}' to '{}'", sourceFile, result);
            return FileOperationResult.success("File archived successfully", result);
            
        } catch (IOException e) {
            logger.error("Failed to archive file '{}' to '{}': {}", sourceFile, archiveDirectory, e.getMessage(), e);
            return FileOperationResult.failure("Archive operation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Copy file with proper error handling
     */
    public FileOperationResult copyFile(Path source, Path destination, boolean replaceExisting) {
        if (!Files.exists(source) || !Files.isRegularFile(source)) {
            return FileOperationResult.failure("Source file does not exist or is not a regular file",
                new IllegalArgumentException("Invalid source file: " + source));
        }
        
        try {
            // Ensure destination directory exists
            if (destination.getParent() != null) {
                FileOperationResult dirResult = ensureDirectory(destination.getParent());
                if (!dirResult.isSuccessful()) {
                    return dirResult;
                }
            }
            
            CopyOption[] options = replaceExisting ? 
                new CopyOption[]{StandardCopyOption.REPLACE_EXISTING} : 
                new CopyOption[]{};
                
            Path result = Files.copy(source, destination, options);
            logger.debug("Copied file from '{}' to '{}'", source, destination);
            return FileOperationResult.success("File copied successfully", result);
            
        } catch (IOException e) {
            logger.error("Failed to copy file '{}' to '{}': {}", source, destination, e.getMessage(), e);
            return FileOperationResult.failure("Copy operation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get file size safely
     */
    public long getFileSize(Path filePath) {
        try {
            return Files.size(filePath);
        } catch (IOException e) {
            logger.warn("Could not get size for file '{}': {}", filePath, e.getMessage());
            return 0L;
        }
    }
    
    /**
     * Validate file existence and readability
     */
    public boolean isValidFile(Path filePath) {
        return Files.exists(filePath) && 
               Files.isRegularFile(filePath) && 
               Files.isReadable(filePath) && 
               getFileSize(filePath) > 0;
    }
    
    /**
     * Add suffix to filename while preserving extension
     */
    public String addFilenameSuffix(String fileName, String suffix) {
        if (fileName == null || suffix == null) {
            return fileName;
        }
        
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1) {
            return fileName + suffix;
        } else {
            return fileName.substring(0, lastDot) + suffix + fileName.substring(lastDot);
        }
    }
    
    /**
     * Format file size for human-readable display
     */
    public String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), units[Math.min(exp, units.length - 1)]);
    }
    
    // Private helper methods
    
    private void validateSearchCriteria(FileSearchCriteria criteria) {
        if (criteria == null) {
            throw new IllegalArgumentException("Search criteria cannot be null");
        }
        
        if (criteria.getDirectory() == null) {
            throw new IllegalArgumentException("Directory cannot be null");
        }
        
        if (criteria.getPattern() == null || criteria.getPattern().trim().isEmpty()) {
            throw new IllegalArgumentException("Pattern cannot be null or empty");
        }
    }
    
    private boolean matchesExtensionCriteria(Path path, FileSearchCriteria criteria) {
        Optional<String[]> allowedExtensions = criteria.getAllowedExtensions();
        if (allowedExtensions.isEmpty()) {
            return true;
        }
        
        String fileName = path.getFileName().toString();
        String extension = getFileExtension(fileName);
        
        for (String allowed : allowedExtensions.get()) {
            if (allowed.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        
        return false;
    }
    
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot == -1 ? "" : fileName.substring(lastDot + 1);
    }
    
    private String addTimestampToFileName(String fileName) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        return addFilenameSuffix(fileName, "_" + timestamp);
    }
    
    private void createBackup(Path targetPath) throws IOException {
        String backupName = addFilenameSuffix(targetPath.getFileName().toString(), ".backup");
        Path backupPath = targetPath.getParent().resolve(backupName);
        Files.copy(targetPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        logger.debug("Created backup: {}", backupPath);
    }
}