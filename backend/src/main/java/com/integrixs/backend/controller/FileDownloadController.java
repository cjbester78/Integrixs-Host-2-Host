package com.integrixs.backend.controller;

import com.integrixs.backend.service.FileDownloadService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * REST API controller for file download operations
 */
@RestController
@RequestMapping("/api/files")
public class FileDownloadController {
    
    private static final Logger log = LoggerFactory.getLogger(FileDownloadController.class);
    
    @Autowired
    private FileDownloadService fileDownloadService;
    
    /**
     * List receiver files for adapter
     * GET /api/files/{adapterName}/receiver
     */
    @GetMapping("/{adapterName}/receiver")
    public ResponseEntity<List<FileDownloadService.FileInfo>> listReceiverFiles(
            @PathVariable String adapterName,
            @RequestParam(required = false) String date) {
        
        log.info("Listing receiver files - adapter: {}, date: {}", adapterName, date);
        
        try {
            List<FileDownloadService.FileInfo> files = fileDownloadService.listReceiverFiles(adapterName, date);
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            log.error("Failed to list receiver files for {}: {}", adapterName, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * List sender files for adapter
     * GET /api/files/{adapterName}/sender
     */
    @GetMapping("/{adapterName}/sender")
    public ResponseEntity<List<FileDownloadService.FileInfo>> listSenderFiles(
            @PathVariable String adapterName,
            @RequestParam(required = false) String date) {
        
        log.info("Listing sender files - adapter: {}, date: {}", adapterName, date);
        
        try {
            List<FileDownloadService.FileInfo> files = fileDownloadService.listSenderFiles(adapterName, date);
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            log.error("Failed to list sender files for {}: {}", adapterName, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * List archived files for adapter
     * GET /api/files/{adapterName}/archive
     */
    @GetMapping("/{adapterName}/archive")
    public ResponseEntity<List<FileDownloadService.FileInfo>> listArchivedFiles(
            @PathVariable String adapterName,
            @RequestParam(required = false) String date) {
        
        log.info("Listing archived files - adapter: {}, date: {}", adapterName, date);
        
        try {
            List<FileDownloadService.FileInfo> files = fileDownloadService.listArchivedFiles(adapterName, date);
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            log.error("Failed to list archived files for {}: {}", adapterName, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get file information
     * GET /api/files/info
     */
    @GetMapping("/info")
    public ResponseEntity<FileDownloadService.FileInfo> getFileInfo(@RequestParam String filePath) {
        log.debug("Getting file info - path: {}", filePath);
        
        try {
            Optional<FileDownloadService.FileInfo> fileInfo = fileDownloadService.getFileInfo(filePath);
            
            if (fileInfo.isPresent()) {
                return ResponseEntity.ok(fileInfo.get());
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Failed to get file info for {}: {}", filePath, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Download single file
     * GET /api/files/download
     */
    @GetMapping("/download")
    public ResponseEntity<ByteArrayResource> downloadFile(@RequestParam String filePath) {
        log.info("Downloading single file - path: {}", filePath);
        
        try {
            FileDownloadService.DownloadResult result = fileDownloadService.downloadFile(filePath);
            
            ByteArrayResource resource = new ByteArrayResource(result.getData());
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, 
                       "attachment; filename=\"" + result.getFileName() + "\"");
            headers.add("X-Download-ID", result.getDownloadId());
            headers.add("X-Timestamp", timestamp);
            headers.add("X-File-Size", String.valueOf(result.getSize()));
            
            return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(result.getContentType()))
                .contentLength(result.getSize())
                .body(resource);
                
        } catch (SecurityException e) {
            log.warn("Security violation in file download: {}", e.getMessage());
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid file download request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("File download failed for {}: {}", filePath, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Download multiple files as ZIP
     * POST /api/files/download-zip
     */
    @PostMapping("/download-zip")
    public ResponseEntity<String> downloadFilesAsZip(@RequestBody DownloadZipRequest request) {
        log.info("Downloading files as ZIP - count: {}, zipName: {}", 
                request.getFilePaths().size(), request.getZipName());
        
        try {
            CompletableFuture<FileDownloadService.DownloadResult> future = 
                fileDownloadService.downloadFilesAsZip(request.getFilePaths(), request.getZipName());
            
            // Return download ID for tracking
            String downloadId = java.util.UUID.randomUUID().toString();
            
            // Process async result
            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("ZIP download failed: {}", throwable.getMessage(), throwable);
                } else {
                    log.info("ZIP download completed - downloadId: {}, size: {}", 
                            result.getDownloadId(), result.getSize());
                }
            });
            
            return ResponseEntity.accepted().body(downloadId);
            
        } catch (SecurityException e) {
            log.warn("Security violation in ZIP download: {}", e.getMessage());
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid ZIP download request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("ZIP download initiation failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get download session status
     * GET /api/files/download-status/{downloadId}
     */
    @GetMapping("/download-status/{downloadId}")
    public ResponseEntity<DownloadStatusResponse> getDownloadStatus(@PathVariable String downloadId) {
        log.debug("Checking download status - downloadId: {}", downloadId);
        
        try {
            Optional<FileDownloadService.DownloadSession> session = 
                fileDownloadService.getDownloadSession(downloadId);
            
            if (session.isPresent()) {
                FileDownloadService.DownloadSession s = session.get();
                DownloadStatusResponse response = new DownloadStatusResponse(
                    s.getId(),
                    s.getType().toString(),
                    s.getStatus().toString(),
                    s.getFiles().size(),
                    s.getCreated()
                );
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Failed to get download status for {}: {}", downloadId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Cancel download session
     * DELETE /api/files/download/{downloadId}
     */
    @DeleteMapping("/download/{downloadId}")
    public ResponseEntity<Void> cancelDownload(@PathVariable String downloadId) {
        log.info("Cancelling download - downloadId: {}", downloadId);
        
        try {
            boolean cancelled = fileDownloadService.cancelDownload(downloadId);
            if (cancelled) {
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Failed to cancel download {}: {}", downloadId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Search files by pattern
     * GET /api/files/{adapterName}/search
     */
    @GetMapping("/{adapterName}/search")
    public ResponseEntity<List<FileDownloadService.FileInfo>> searchFiles(
            @PathVariable String adapterName,
            @RequestParam String pattern,
            @RequestParam String directory) {
        
        log.info("Searching files - adapter: {}, pattern: {}, directory: {}", 
                adapterName, pattern, directory);
        
        try {
            List<FileDownloadService.FileInfo> files = 
                fileDownloadService.searchFiles(adapterName, pattern, directory);
            return ResponseEntity.ok(files);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid search request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("File search failed for {}: {}", adapterName, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get file storage statistics
     * GET /api/files/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<FileStorageStats> getFileStorageStats() {
        log.debug("Getting file storage statistics");
        
        try {
            // This would typically aggregate statistics from the file system
            // For now, return basic statistics
            FileStorageStats stats = new FileStorageStats(
                0L, // totalFiles - would be calculated
                0L, // totalSize - would be calculated
                0L, // receiverFiles
                0L, // senderFiles
                0L, // archivedFiles
                LocalDateTime.now() // lastUpdated
            );
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to get file storage statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    // Request/Response DTOs
    
    public static class DownloadZipRequest {
        private List<String> filePaths;
        private String zipName;
        
        // Getters and setters
        public List<String> getFilePaths() { return filePaths; }
        public void setFilePaths(List<String> filePaths) { this.filePaths = filePaths; }
        public String getZipName() { return zipName; }
        public void setZipName(String zipName) { this.zipName = zipName; }
    }
    
    public static class DownloadStatusResponse {
        private final String downloadId;
        private final String type;
        private final String status;
        private final int fileCount;
        private final LocalDateTime created;
        
        public DownloadStatusResponse(String downloadId, String type, String status, 
                                    int fileCount, LocalDateTime created) {
            this.downloadId = downloadId;
            this.type = type;
            this.status = status;
            this.fileCount = fileCount;
            this.created = created;
        }
        
        // Getters
        public String getDownloadId() { return downloadId; }
        public String getType() { return type; }
        public String getStatus() { return status; }
        public int getFileCount() { return fileCount; }
        public LocalDateTime getCreated() { return created; }
    }
    
    public static class FileStorageStats {
        private final long totalFiles;
        private final long totalSize;
        private final long receiverFiles;
        private final long senderFiles;
        private final long archivedFiles;
        private final LocalDateTime lastUpdated;
        
        public FileStorageStats(long totalFiles, long totalSize, long receiverFiles,
                              long senderFiles, long archivedFiles, LocalDateTime lastUpdated) {
            this.totalFiles = totalFiles;
            this.totalSize = totalSize;
            this.receiverFiles = receiverFiles;
            this.senderFiles = senderFiles;
            this.archivedFiles = archivedFiles;
            this.lastUpdated = lastUpdated;
        }
        
        // Getters
        public long getTotalFiles() { return totalFiles; }
        public long getTotalSize() { return totalSize; }
        public long getReceiverFiles() { return receiverFiles; }
        public long getSenderFiles() { return senderFiles; }
        public long getArchivedFiles() { return archivedFiles; }
        public LocalDateTime getLastUpdated() { return lastUpdated; }
    }
}