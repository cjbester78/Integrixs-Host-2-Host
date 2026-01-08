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
 * File System Cleanup: Report Files
 * 
 * Deletes generated report files from the file system that are older than the retention period.
 * This is a FILE SYSTEM operation, not database.
 */
@Component
public class CleanupReportFilesJob implements DataRetentionJob {
    
    private static final Logger log = Logger.getLogger(CleanupReportFilesJob.class.getName());
    
    @Value("${integrix.reports.path:./backend/reports}")
    private String reportsPath;
    
    @Override
    public int execute(DataRetentionConfig config) {
        log.info("Starting cleanup of report files older than " + config.getRetentionDays() + " days");
        
        Path reportsDir = Paths.get(reportsPath);
        AtomicInteger filesDeleted = new AtomicInteger(0);
        
        if (!Files.exists(reportsDir)) {
            log.warning("Reports directory does not exist: " + reportsPath);
            return 0;
        }
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(config.getRetentionDays());
        
        try {
            Files.walkFileTree(reportsDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String fileName = file.getFileName().toString();
                    
                    // Only process common report file types
                    if (fileName.endsWith(".pdf") || fileName.endsWith(".csv") || 
                        fileName.endsWith(".xlsx") || fileName.endsWith(".txt")) {
                        
                        LocalDateTime fileModTime = LocalDateTime.ofInstant(
                            attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());
                        
                        if (fileModTime.isBefore(cutoffDate)) {
                            Files.delete(file);
                            filesDeleted.incrementAndGet();
                            log.info("Deleted report file: " + file.getFileName());
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            
            log.info("Cleanup report files completed: " + filesDeleted.get() + " files deleted");
            return filesDeleted.get();
            
        } catch (IOException e) {
            log.severe("Error during report files cleanup: " + e.getMessage());
            throw new RuntimeException("Failed to cleanup report files", e);
        }
    }
    
    @Override
    public String getDisplayName() {
        return "File System: Cleanup Report Files";
    }
    
    @Override
    public String getDescription() {
        return "Delete REPORT FILES (.pdf, .csv, .xlsx) from the file system (not database) that are older than the retention period";
    }
    
    @Override
    public String getJobIdentifier() {
        return "CleanupReportFilesJob";
    }
    
    @Override
    public boolean validateConfiguration(DataRetentionConfig config) {
        return config.getRetentionDays() != null && config.getRetentionDays() > 0;
    }
}