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
 * File System Cleanup: Backup Files
 * 
 * Deletes backup files from the file system that are older than the retention period.
 * This is a FILE SYSTEM operation, not database.
 */
@Component
public class CleanupBackupFilesJob implements DataRetentionJob {
    
    private static final Logger log = Logger.getLogger(CleanupBackupFilesJob.class.getName());
    
    @Value("${integrix.backup.path:./backend/backup}")
    private String backupPath;
    
    @Override
    public int execute(DataRetentionConfig config) {
        log.info("Starting cleanup of backup files older than " + config.getRetentionDays() + " days");
        
        Path backupDir = Paths.get(backupPath);
        AtomicInteger filesDeleted = new AtomicInteger(0);
        
        if (!Files.exists(backupDir)) {
            log.warning("Backup directory does not exist: " + backupPath);
            return 0;
        }
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(config.getRetentionDays());
        
        try {
            Files.walkFileTree(backupDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    LocalDateTime fileModTime = LocalDateTime.ofInstant(
                        attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());
                    
                    if (fileModTime.isBefore(cutoffDate)) {
                        Files.delete(file);
                        filesDeleted.incrementAndGet();
                        log.info("Deleted backup file: " + file.getFileName());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            
            log.info("Cleanup backup files completed: " + filesDeleted.get() + " files deleted");
            return filesDeleted.get();
            
        } catch (IOException e) {
            log.severe("Error during backup files cleanup: " + e.getMessage());
            throw new RuntimeException("Failed to cleanup backup files", e);
        }
    }
    
    @Override
    public String getDisplayName() {
        return "File System: Cleanup Backup Files";
    }
    
    @Override
    public String getDescription() {
        return "Delete BACKUP FILES from the file system (not database) that are older than the retention period";
    }
    
    @Override
    public String getJobIdentifier() {
        return "CleanupBackupFilesJob";
    }
    
    @Override
    public boolean validateConfiguration(DataRetentionConfig config) {
        return config.getRetentionDays() != null && config.getRetentionDays() > 0;
    }
}