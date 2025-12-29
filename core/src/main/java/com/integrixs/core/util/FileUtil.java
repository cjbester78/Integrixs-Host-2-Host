package com.integrixs.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Utility class for common file operations used across adapters.
 * Provides file pattern matching, directory operations, and file discovery.
 */
public class FileUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);
    
    /**
     * Find files matching the given pattern in a directory.
     * Supports glob patterns like *.xml, *.csv, file_*.txt, etc.
     * 
     * @param directory The directory to search
     * @param pattern The file pattern (glob format)
     * @return List of matching file paths
     */
    public static List<Path> findMatchingFiles(Path directory, String pattern) {
        List<Path> matchingFiles = new ArrayList<>();
        
        if (!Files.exists(directory)) {
            logger.warn("Directory does not exist: {}", directory);
            return matchingFiles;
        }
        
        if (!Files.isDirectory(directory)) {
            logger.warn("Path is not a directory: {}", directory);
            return matchingFiles;
        }
        
        try {
            // Convert glob pattern to regex for PathMatcher
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            
            try (Stream<Path> files = Files.walk(directory, 1)) { // Only immediate children, not recursive
                files.filter(Files::isRegularFile)
                     .filter(path -> matcher.matches(path.getFileName()))
                     .forEach(matchingFiles::add);
            }
            
            logger.debug("Found {} files matching pattern '{}' in directory '{}'", 
                        matchingFiles.size(), pattern, directory);
            
        } catch (IOException e) {
            logger.error("Error searching for files in directory '{}': {}", directory, e.getMessage(), e);
        }
        
        return matchingFiles;
    }
    
    /**
     * Check if a filename matches the given pattern.
     * Supports glob patterns like *.xml, *.csv, file_*.txt, etc.
     * 
     * @param fileName The filename to check
     * @param pattern The pattern to match against
     * @return true if filename matches pattern
     */
    public static boolean matchesPattern(String fileName, String pattern) {
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
     * Ensure directory exists, creating it if necessary.
     * Creates parent directories as needed.
     * 
     * @param directory The directory path
     * @return The created/existing directory path
     * @throws IOException if directory cannot be created
     */
    public static Path ensureDirectoryExists(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
            logger.debug("Created directory: {}", directory);
        } else if (!Files.isDirectory(directory)) {
            throw new IOException("Path exists but is not a directory: " + directory);
        }
        return directory;
    }
    
    /**
     * Ensure directory exists from string path.
     * 
     * @param directoryPath The directory path as string
     * @return The created/existing directory path
     * @throws IOException if directory cannot be created
     */
    public static Path ensureDirectoryExists(String directoryPath) throws IOException {
        if (directoryPath == null || directoryPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Directory path cannot be null or empty");
        }
        return ensureDirectoryExists(Paths.get(directoryPath));
    }
    
    /**
     * Copy file from source to destination, creating parent directories if needed.
     * 
     * @param source Source file path
     * @param destination Destination file path
     * @param replaceExisting Whether to replace existing destination file
     * @return The destination path
     * @throws IOException if copy fails
     */
    public static Path copyFile(Path source, Path destination, boolean replaceExisting) throws IOException {
        // Ensure destination directory exists
        if (destination.getParent() != null) {
            ensureDirectoryExists(destination.getParent());
        }
        
        CopyOption[] options = replaceExisting ? 
            new CopyOption[]{StandardCopyOption.REPLACE_EXISTING} : 
            new CopyOption[]{};
            
        Path result = Files.copy(source, destination, options);
        logger.debug("Copied file from '{}' to '{}'", source, destination);
        return result;
    }
    
    /**
     * Move file from source to destination, creating parent directories if needed.
     * 
     * @param source Source file path
     * @param destination Destination file path
     * @param replaceExisting Whether to replace existing destination file
     * @return The destination path
     * @throws IOException if move fails
     */
    public static Path moveFile(Path source, Path destination, boolean replaceExisting) throws IOException {
        // Ensure destination directory exists
        if (destination.getParent() != null) {
            ensureDirectoryExists(destination.getParent());
        }
        
        CopyOption[] options = replaceExisting ? 
            new CopyOption[]{StandardCopyOption.REPLACE_EXISTING} : 
            new CopyOption[]{};
            
        Path result = Files.move(source, destination, options);
        logger.debug("Moved file from '{}' to '{}'", source, destination);
        return result;
    }
    
    /**
     * Archive a file to the specified directory with optional timestamp.
     * 
     * @param filePath The file to archive
     * @param archiveDirectory The archive directory
     * @param addTimestamp Whether to add timestamp to filename
     * @return The archived file path
     * @throws IOException if archiving fails
     */
    public static Path archiveFile(Path filePath, String archiveDirectory, boolean addTimestamp) throws IOException {
        Path archivePath = ensureDirectoryExists(archiveDirectory);
        
        String fileName = filePath.getFileName().toString();
        if (addTimestamp) {
            String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
            String extension = fileName.substring(fileName.lastIndexOf('.'));
            fileName = baseName + "_" + System.currentTimeMillis() + extension;
        }
        
        Path archivedFile = archivePath.resolve(fileName);
        return moveFile(filePath, archivedFile, false);
    }
    
    /**
     * Get file size in bytes.
     * 
     * @param filePath The file path
     * @return File size in bytes, or 0 if file doesn't exist
     */
    public static long getFileSize(Path filePath) {
        try {
            return Files.size(filePath);
        } catch (IOException e) {
            logger.warn("Could not get size for file '{}': {}", filePath, e.getMessage());
            return 0;
        }
    }
    
    /**
     * Check if file is readable and not empty.
     * 
     * @param filePath The file path to check
     * @return true if file is readable and has content
     */
    public static boolean isValidFile(Path filePath) {
        return Files.exists(filePath) && 
               Files.isRegularFile(filePath) && 
               Files.isReadable(filePath) && 
               getFileSize(filePath) > 0;
    }
    
    /**
     * Add a suffix to filename while preserving extension.
     * Example: "test.xml" with suffix "_processed" becomes "test_processed.xml"
     * 
     * @param fileName Original filename
     * @param suffix Suffix to add
     * @return Modified filename
     */
    public static String addFilenameSuffix(String fileName, String suffix) {
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
}