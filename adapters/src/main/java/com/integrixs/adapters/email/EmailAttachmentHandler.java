package com.integrixs.adapters.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Handler for email attachment processing and management
 */
public class EmailAttachmentHandler {
    
    private static final Logger log = LoggerFactory.getLogger(EmailAttachmentHandler.class);
    private static final List<String> EXECUTABLE_EXTENSIONS = List.of(".exe", ".bat", ".cmd", ".sh", ".jar", ".war");
    private static final Pattern FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
    
    private final EmailAdapterConfig config;
    private final String correlationId;
    
    public EmailAttachmentHandler(EmailAdapterConfig config, String correlationId) {
        this.config = config;
        this.correlationId = correlationId;
    }
    
    /**
     * Find and validate attachments for email sending
     */
    public List<Path> findAttachments() {
        String attachmentDir = config.getAttachmentDirectory();
        if (attachmentDir == null || attachmentDir.trim().isEmpty()) {
            log.debug("No attachment directory configured");
            return List.of();
        }
        
        Path attachmentPath = Paths.get(attachmentDir);
        if (!Files.exists(attachmentPath) || !Files.isDirectory(attachmentPath)) {
            log.warn("Attachment directory does not exist or is not a directory: {}", attachmentPath);
            return List.of();
        }
        
        List<Path> attachments = new ArrayList<>();
        String pattern = config.getAttachmentPattern();
        
        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(attachmentPath)) {
                for (Path file : stream) {
                    if (Files.isRegularFile(file) && matcher.matches(file.getFileName())) {
                        if (validateAttachment(file)) {
                            attachments.add(file);
                            log.debug("Found attachment: {}", file.getFileName());
                            
                            if (attachments.size() >= config.getMaxAttachmentCount()) {
                                log.warn("Maximum attachment count reached ({}), stopping search", 
                                        config.getMaxAttachmentCount());
                                break;
                            }
                        }
                    }
                }
            }
            
        } catch (IOException e) {
            log.error("Failed to scan attachment directory {}: {}", attachmentPath, e.getMessage(), e);
            throw new EmailAdapterException("Failed to find attachments: " + e.getMessage(), e, "ATTACHMENT", null);
        }
        
        log.info("Found {} valid attachments for correlation: {}", attachments.size(), correlationId);
        return attachments;
    }
    
    /**
     * Validate individual attachment file
     */
    public boolean validateAttachment(Path filePath) {
        try {
            // Check if file exists and is readable
            if (!Files.exists(filePath) || !Files.isRegularFile(filePath) || !Files.isReadable(filePath)) {
                log.warn("Attachment is not a readable file: {}", filePath);
                return false;
            }
            
            // Check file size
            long fileSize = Files.size(filePath);
            if (fileSize > config.getMaxAttachmentSize()) {
                log.warn("Attachment exceeds maximum size ({} bytes): {} ({})", 
                        config.getMaxAttachmentSize(), filePath, fileSize);
                return false;
            }
            
            // Check filename security
            String fileName = filePath.getFileName().toString();
            if (!isSecureFileName(fileName)) {
                log.warn("Attachment has unsafe filename: {}", fileName);
                return false;
            }
            
            // Check for executable files if security is enabled
            if (config.isValidateAttachments() && isExecutableFile(fileName)) {
                log.warn("Attachment is an executable file: {}", fileName);
                return false;
            }
            
            return true;
            
        } catch (IOException e) {
            log.error("Failed to validate attachment {}: {}", filePath, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Archive processed attachments
     */
    public void archiveAttachments(List<Path> attachments, String operation) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        
        String processedDir = config.getProcessedDirectory();
        if (processedDir == null) {
            log.warn("Processed directory not configured, skipping attachment archival");
            return;
        }
        
        try {
            // Create archive directory with date and correlation
            String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            Path archiveDir = Paths.get(processedDir, datePath, operation, correlationId);
            Files.createDirectories(archiveDir);
            
            int archivedCount = 0;
            for (Path attachment : attachments) {
                if (Files.exists(attachment)) {
                    try {
                        Path archivedFile = archiveDir.resolve(attachment.getFileName());
                        
                        // Handle duplicate filenames
                        if (Files.exists(archivedFile)) {
                            String baseName = getBaseName(attachment.getFileName().toString());
                            String extension = getExtension(attachment.getFileName().toString());
                            int counter = 1;
                            
                            do {
                                String newName = baseName + "_" + counter + extension;
                                archivedFile = archiveDir.resolve(newName);
                                counter++;
                            } while (Files.exists(archivedFile));
                        }
                        
                        Files.move(attachment, archivedFile, StandardCopyOption.REPLACE_EXISTING);
                        archivedCount++;
                        
                        log.debug("Archived attachment: {} -> {}", attachment.getFileName(), archivedFile);
                        
                    } catch (IOException e) {
                        log.error("Failed to archive attachment {}: {}", attachment.getFileName(), e.getMessage(), e);
                    }
                }
            }
            
            log.info("Archived {} attachments for correlation: {}", archivedCount, correlationId);
            
        } catch (IOException e) {
            log.error("Failed to create archive directory for correlation {}: {}", correlationId, e.getMessage(), e);
        }
    }
    
    /**
     * Delete processed attachments
     */
    public void deleteAttachments(List<Path> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        
        int deletedCount = 0;
        for (Path attachment : attachments) {
            try {
                if (Files.exists(attachment)) {
                    Files.delete(attachment);
                    deletedCount++;
                    log.debug("Deleted attachment: {}", attachment.getFileName());
                }
            } catch (IOException e) {
                log.error("Failed to delete attachment {}: {}", attachment.getFileName(), e.getMessage(), e);
            }
        }
        
        log.info("Deleted {} attachments for correlation: {}", deletedCount, correlationId);
    }
    
    /**
     * Copy attachments to error directory
     */
    public void moveToErrorDirectory(List<Path> attachments, String errorReason) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        
        String errorDir = config.getErrorDirectory();
        if (errorDir == null) {
            log.warn("Error directory not configured, cannot move failed attachments");
            return;
        }
        
        try {
            String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            Path errorPath = Paths.get(errorDir, datePath, correlationId);
            Files.createDirectories(errorPath);
            
            // Create error report
            createErrorReport(errorPath, errorReason);
            
            int movedCount = 0;
            for (Path attachment : attachments) {
                try {
                    if (Files.exists(attachment)) {
                        Path errorFile = errorPath.resolve(attachment.getFileName());
                        Files.move(attachment, errorFile, StandardCopyOption.REPLACE_EXISTING);
                        movedCount++;
                        log.debug("Moved failed attachment to error directory: {}", attachment.getFileName());
                    }
                } catch (IOException e) {
                    log.error("Failed to move attachment {} to error directory: {}", 
                             attachment.getFileName(), e.getMessage(), e);
                }
            }
            
            log.info("Moved {} attachments to error directory for correlation: {}", movedCount, correlationId);
            
        } catch (IOException e) {
            log.error("Failed to create error directory for correlation {}: {}", correlationId, e.getMessage(), e);
        }
    }
    
    /**
     * Calculate total size of attachments
     */
    public long calculateTotalSize(List<Path> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return 0L;
        }
        
        long totalSize = 0L;
        for (Path attachment : attachments) {
            try {
                if (Files.exists(attachment)) {
                    totalSize += Files.size(attachment);
                }
            } catch (IOException e) {
                log.warn("Could not determine size of attachment {}: {}", attachment, e.getMessage());
            }
        }
        
        return totalSize;
    }
    
    /**
     * Get attachment metadata
     */
    public List<AttachmentMetadata> getAttachmentMetadata(List<Path> attachments) {
        List<AttachmentMetadata> metadata = new ArrayList<>();
        
        if (attachments == null) {
            return metadata;
        }
        
        for (Path attachment : attachments) {
            try {
                if (Files.exists(attachment)) {
                    BasicFileAttributes attrs = Files.readAttributes(attachment, BasicFileAttributes.class);
                    
                    AttachmentMetadata meta = new AttachmentMetadata(
                        attachment.getFileName().toString(),
                        attrs.size(),
                        attrs.lastModifiedTime().toInstant(),
                        getContentType(attachment.getFileName().toString())
                    );
                    
                    metadata.add(meta);
                }
            } catch (IOException e) {
                log.warn("Could not read metadata for attachment {}: {}", attachment, e.getMessage());
            }
        }
        
        return metadata;
    }
    
    // Helper methods
    private boolean isSecureFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        
        // Check for path traversal
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return false;
        }
        
        // Check for control characters
        if (fileName.chars().anyMatch(c -> c < 32 || c == 127)) {
            return false;
        }
        
        // Check filename pattern
        return FILENAME_PATTERN.matcher(fileName).matches();
    }
    
    private boolean isExecutableFile(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        return EXECUTABLE_EXTENSIONS.stream().anyMatch(lowerFileName::endsWith);
    }
    
    private String getBaseName(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }
    
    private String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot) : "";
    }
    
    private String getContentType(String fileName) {
        try {
            return Files.probeContentType(Paths.get(fileName));
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }
    
    private void createErrorReport(Path errorPath, String errorReason) throws IOException {
        Path reportFile = errorPath.resolve("error_report.txt");
        String report = String.format(
            "Error Report\n" +
            "Correlation ID: %s\n" +
            "Timestamp: %s\n" +
            "Error Reason: %s\n",
            correlationId,
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            errorReason
        );
        
        Files.write(reportFile, report.getBytes());
    }
    
    /**
     * Attachment metadata holder
     */
    public static class AttachmentMetadata {
        private final String fileName;
        private final long size;
        private final java.time.Instant lastModified;
        private final String contentType;
        
        public AttachmentMetadata(String fileName, long size, java.time.Instant lastModified, String contentType) {
            this.fileName = fileName;
            this.size = size;
            this.lastModified = lastModified;
            this.contentType = contentType;
        }
        
        public String getFileName() { return fileName; }
        public long getSize() { return size; }
        public java.time.Instant getLastModified() { return lastModified; }
        public String getContentType() { return contentType; }
        
        @Override
        public String toString() {
            return "AttachmentMetadata{fileName='" + fileName + "', size=" + size + 
                   ", contentType='" + contentType + "'}";
        }
    }
}