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
 * File System Cleanup: Application Log Files  
 * 
 * Archives and deletes PHYSICAL LOG FILES from the file system based on
 * retention and archive periods. This is a FILE SYSTEM operation, not database.
 * Archives files first, then deletes old archives.
 */
@Component
public class CleanupLogFilesJob implements DataRetentionJob {
    
    private static final Logger log = Logger.getLogger(CleanupLogFilesJob.class.getName());
    
    @Value("${integrix.logs.path:./backend/logs}")
    private String logPath;
    
    @Override
    public int execute(DataRetentionConfig config) {
        log.info("Starting cleanup of log files - retention: " + config.getRetentionDays() + 
                " days, archive: " + config.getArchiveDays() + " days");
        
        Path logsDir = Paths.get(logPath);
        AtomicInteger filesProcessed = new AtomicInteger(0);
        
        if (!Files.exists(logsDir)) {
            log.warning("Logs directory does not exist: " + logPath);
            return 0;
        }
        
        try {
            // Step 1: Archive logs older than retention period
            if (config.getRetentionDays() != null && config.getRetentionDays() > 0) {
                archiveOldLogFiles(logsDir, config.getRetentionDays(), filesProcessed);
            }
            
            // Step 2: Delete archived logs older than archive period
            if (config.getArchiveDays() != null && config.getArchiveDays() > 0) {
                deleteOldArchivedLogs(logsDir, config.getArchiveDays(), filesProcessed);
            }
            
            log.info("Cleanup log files completed: " + filesProcessed.get() + " files processed");
            return filesProcessed.get();
            
        } catch (IOException e) {
            log.severe("Error during log files cleanup: " + e.getMessage());
            throw new RuntimeException("Failed to cleanup log files", e);
        }
    }
    
    private void archiveOldLogFiles(Path logsDir, int retentionDays, AtomicInteger counter) throws IOException {
        Path backupDir = logsDir.resolve("backup");
        Files.createDirectories(backupDir);
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        
        Files.walkFileTree(logsDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getParent().equals(backupDir)) {
                    return FileVisitResult.CONTINUE; // Skip backup directory
                }
                
                String fileName = file.getFileName().toString();
                if (fileName.endsWith(".log")) {
                    LocalDateTime fileModTime = LocalDateTime.ofInstant(
                        attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());
                    
                    if (fileModTime.isBefore(cutoffDate)) {
                        // Move to backup directory
                        Path backupFile = backupDir.resolve(fileName);
                        Files.move(file, backupFile, StandardCopyOption.REPLACE_EXISTING);
                        counter.incrementAndGet();
                        log.info("Archived log file: " + fileName + " -> " + backupFile.getFileName());
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    private void deleteOldArchivedLogs(Path logsDir, int archiveDays, AtomicInteger counter) throws IOException {
        Path backupDir = logsDir.resolve("backup");
        if (!Files.exists(backupDir)) {
            return; // No backup directory, nothing to delete
        }
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(archiveDays);
        
        Files.walkFileTree(backupDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                LocalDateTime fileModTime = LocalDateTime.ofInstant(
                    attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());
                
                if (fileModTime.isBefore(cutoffDate)) {
                    Files.delete(file);
                    counter.incrementAndGet();
                    log.info("Deleted archived log file: " + file.getFileName());
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    @Override
    public String getDisplayName() {
        return "File System: Cleanup Application Log Files";
    }
    
    @Override
    public String getDescription() {
        return "Archive and delete PHYSICAL LOG FILES from the file system (not database) based on retention periods";
    }
    
    @Override
    public String getJobIdentifier() {
        return "CleanupLogFilesJob";
    }
    
    @Override
    public boolean validateConfiguration(DataRetentionConfig config) {
        boolean hasRetentionDays = config.getRetentionDays() != null && config.getRetentionDays() > 0;
        boolean hasArchiveDays = config.getArchiveDays() != null && config.getArchiveDays() > 0;
        
        // Must have at least one retention period configured
        return hasRetentionDays || hasArchiveDays;
    }
}