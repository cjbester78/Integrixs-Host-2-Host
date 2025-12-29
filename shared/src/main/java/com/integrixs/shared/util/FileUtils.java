package com.integrixs.shared.util;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

/**
 * File utility methods
 */
public final class FileUtils {
    
    private static final String DATE_FORMAT = "yyyyMMdd_HHmmss";
    
    private FileUtils() {
        // Utility class
    }
    
    /**
     * Check if a file matches the given pattern
     */
    public static boolean matchesPattern(String filename, String pattern, String extension) {
        if (StringUtils.isBlank(filename)) {
            return false;
        }
        
        // Check extension if specified
        if (StringUtils.isNotBlank(extension)) {
            String fileExt = FilenameUtils.getExtension(filename);
            if (!extension.equalsIgnoreCase(fileExt)) {
                return false;
            }
        }
        
        // Check wildcard pattern if specified
        if (StringUtils.isNotBlank(pattern)) {
            return FilenameUtils.wildcardMatch(filename, pattern);
        }
        
        return true;
    }
    
    /**
     * Generate archive filename with timestamp
     */
    public static String generateArchiveFilename(String originalFilename) {
        String timestamp = new SimpleDateFormat(DATE_FORMAT).format(new Date());
        String baseName = FilenameUtils.getBaseName(originalFilename);
        String extension = FilenameUtils.getExtension(originalFilename);
        
        if (StringUtils.isNotBlank(extension)) {
            return baseName + "_" + timestamp + "." + extension;
        } else {
            return baseName + "_" + timestamp;
        }
    }
    
    /**
     * Ensure directory exists, create if necessary
     */
    public static void ensureDirectoryExists(String dirPath) throws IOException {
        if (StringUtils.isBlank(dirPath)) {
            return;
        }
        
        Path path = Paths.get(dirPath);
        Files.createDirectories(path);
    }
    
    /**
     * Move file to archive directory with timestamp
     */
    public static void archiveFile(String sourceFile, String archiveDir) throws IOException {
        ensureDirectoryExists(archiveDir);
        
        Path sourcePath = Paths.get(sourceFile);
        String originalFilename = sourcePath.getFileName().toString();
        String archiveFilename = generateArchiveFilename(originalFilename);
        
        Path archivePath = Paths.get(archiveDir, archiveFilename);
        Files.move(sourcePath, archivePath);
    }
    
    /**
     * Get file size in bytes
     */
    public static long getFileSize(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        return Files.size(path);
    }
    
    /**
     * List files in directory matching pattern
     */
    public static List<String> listFilesMatching(String directory, String pattern, String extension) throws IOException {
        Path dir = Paths.get(directory);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return List.of();
        }
        
        try (Stream<Path> paths = Files.list(dir)) {
            return paths
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(filename -> matchesPattern(filename, pattern, extension))
                .sorted()
                .toList();
        }
    }
    
    /**
     * Validate file path and extension
     */
    public static boolean isValidFile(String filePath, String[] allowedExtensions) {
        if (StringUtils.isBlank(filePath)) {
            return false;
        }
        
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            return false;
        }
        
        if (allowedExtensions != null && allowedExtensions.length > 0) {
            String extension = FilenameUtils.getExtension(filePath);
            for (String allowedExt : allowedExtensions) {
                if (allowedExt.equalsIgnoreCase(extension)) {
                    return true;
                }
            }
            return false;
        }
        
        return true;
    }
    
    /**
     * Format file size for display
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), units[exp]);
    }
}