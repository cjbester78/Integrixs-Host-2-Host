package com.integrixs.backend.jobs;

import com.integrixs.shared.model.DataRetentionConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * File System Cleanup: Temporary Files
 * 
 * Deletes temporary files from the file system that are older than the retention period.
 * This is a FILE SYSTEM operation, not database.
 */
@Component
public class CleanupTempFilesJob implements DataRetentionJob {
    
    private static final Logger log = Logger.getLogger(CleanupTempFilesJob.class.getName());
    
    @Value("${integrix.temp.path:./backend/temp}")
    private String tempPath;
    
    @Override
    public int execute(DataRetentionConfig config) {
        log.info("Starting cleanup of temporary files older than " + config.getRetentionDays() + " days");
        
        Path tempDir = Paths.get(tempPath);
        AtomicInteger filesDeleted = new AtomicInteger(0);
        
        if (!Files.exists(tempDir)) {
            log.warning("Temporary files directory does not exist: " + tempPath);
            return 0;
        }
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(config.getRetentionDays());
        
        try {
            Files.walkFileTree(tempDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    LocalDateTime fileModTime = LocalDateTime.ofInstant(
                        attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());
                    
                    if (fileModTime.isBefore(cutoffDate)) {
                        Files.delete(file);
                        filesDeleted.incrementAndGet();
                        log.info("Deleted temporary file: " + file.getFileName());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            
            log.info("Cleanup temporary files completed: " + filesDeleted.get() + " files deleted");
            return filesDeleted.get();
            
        } catch (IOException e) {
            log.severe("Error during temporary files cleanup: " + e.getMessage());
            throw new RuntimeException("Failed to cleanup temporary files", e);
        }
    }
    
    @Override
    public String getDisplayName() {
        return "File System: Cleanup Temporary Files";
    }
    
    @Override
    public String getDescription() {
        return "Delete TEMPORARY FILES from the file system (not database) that are older than the retention period";
    }
    
    @Override
    public String getJobIdentifier() {
        return "CleanupTempFilesJob";
    }
    
    @Override
    public boolean validateConfiguration(DataRetentionConfig config) {
        return config.getRetentionDays() != null && config.getRetentionDays() > 0;
    }
}