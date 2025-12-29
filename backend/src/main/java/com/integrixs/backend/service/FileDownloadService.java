package com.integrixs.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service for downloading processed files from server storage
 */
@Service
public class FileDownloadService {
    
    private static final Logger log = LoggerFactory.getLogger(FileDownloadService.class);
    
    // Configuration properties
    @Value("${h2h.file-storage.base-directory:/data}")
    private String baseStorageDirectory;
    
    @Value("${h2h.file-storage.max-file-size:100MB}")
    private String maxFileSizeStr;
    
    @Value("${h2h.file-storage.max-zip-size:500MB}")
    private String maxZipSizeStr;
    
    @Value("${h2h.file-storage.download-timeout:300}")
    private int downloadTimeoutSeconds;
    
    // Download tracking
    private final Map<String, DownloadSession> activeSessions = new ConcurrentHashMap<>();
    
    /**
     * List available files in receiver directory
     */
    public List<FileInfo> listReceiverFiles(String adapterName, String dateFilter) {
        log.info("Listing receiver files - adapter: {}, date: {}", adapterName, dateFilter);
        
        try {
            Path receiverPath = getReceiverPath(adapterName);
            if (!Files.exists(receiverPath)) {
                log.warn("Receiver directory does not exist: {}", receiverPath);
                return List.of();
            }
            
            return scanDirectory(receiverPath, dateFilter, "receiver");
            
        } catch (Exception e) {
            log.error("Failed to list receiver files for adapter {}: {}", adapterName, e.getMessage(), e);
            throw new RuntimeException("Failed to list receiver files: " + e.getMessage(), e);
        }
    }
    
    /**
     * List available files in sender directory
     */
    public List<FileInfo> listSenderFiles(String adapterName, String dateFilter) {
        log.info("Listing sender files - adapter: {}, date: {}", adapterName, dateFilter);
        
        try {
            Path senderPath = getSenderPath(adapterName);
            if (!Files.exists(senderPath)) {
                log.warn("Sender directory does not exist: {}", senderPath);
                return List.of();
            }
            
            return scanDirectory(senderPath, dateFilter, "sender");
            
        } catch (Exception e) {
            log.error("Failed to list sender files for adapter {}: {}", adapterName, e.getMessage(), e);
            throw new RuntimeException("Failed to list sender files: " + e.getMessage(), e);
        }
    }
    
    /**
     * List available files in archive directory
     */
    public List<FileInfo> listArchivedFiles(String adapterName, String dateFilter) {
        log.info("Listing archived files - adapter: {}, date: {}", adapterName, dateFilter);
        
        try {
            Path archivePath = getArchivePath(adapterName);
            if (!Files.exists(archivePath)) {
                log.warn("Archive directory does not exist: {}", archivePath);
                return List.of();
            }
            
            return scanDirectory(archivePath, dateFilter, "archive");
            
        } catch (Exception e) {
            log.error("Failed to list archived files for adapter {}: {}", adapterName, e.getMessage(), e);
            throw new RuntimeException("Failed to list archived files: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get file info for specific file
     */
    public Optional<FileInfo> getFileInfo(String filePath) {
        log.debug("Getting file info - path: {}", filePath);
        
        try {
            Path path = Paths.get(filePath);
            
            // Security check - ensure path is within allowed directories
            if (!isPathAllowed(path)) {
                log.warn("Attempted access to restricted path: {}", filePath);
                return Optional.empty();
            }
            
            if (!Files.exists(path)) {
                return Optional.empty();
            }
            
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            
            FileInfo fileInfo = new FileInfo(
                path.getFileName().toString(),
                path.toString(),
                attrs.size(),
                attrs.lastModifiedTime().toInstant(),
                attrs.creationTime().toInstant(),
                determineFileType(path),
                determineCategory(path),
                Files.isReadable(path),
                generateChecksum(path)
            );
            
            return Optional.of(fileInfo);
            
        } catch (Exception e) {
            log.error("Failed to get file info for {}: {}", filePath, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Download single file
     */
    public DownloadResult downloadFile(String filePath) {
        log.info("Downloading file - path: {}", filePath);
        
        try {
            Path path = Paths.get(filePath);
            
            // Security validation
            if (!isPathAllowed(path)) {
                throw new SecurityException("Access denied to path: " + filePath);
            }
            
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("File not found: " + filePath);
            }
            
            // Size validation
            long fileSize = Files.size(path);
            long maxFileSize = parseSize(maxFileSizeStr);
            if (fileSize > maxFileSize) {
                throw new IllegalArgumentException("File too large: " + formatSize(fileSize) + " > " + maxFileSizeStr);
            }
            
            // Create download session
            String downloadId = UUID.randomUUID().toString();
            DownloadSession session = new DownloadSession(
                downloadId,
                DownloadType.SINGLE_FILE,
                List.of(path),
                LocalDateTime.now()
            );
            
            activeSessions.put(downloadId, session);
            
            // Prepare file data
            byte[] fileData = Files.readAllBytes(path);
            
            log.info("File download prepared - downloadId: {}, size: {}", downloadId, formatSize(fileSize));
            
            return new DownloadResult(
                downloadId,
                path.getFileName().toString(),
                fileData,
                "application/octet-stream",
                fileSize,
                LocalDateTime.now()
            );
            
        } catch (Exception e) {
            log.error("File download failed for {}: {}", filePath, e.getMessage(), e);
            throw new RuntimeException("File download failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Download multiple files as ZIP archive
     */
    public CompletableFuture<DownloadResult> downloadFilesAsZip(List<String> filePaths, String zipName) {
        log.info("Downloading files as ZIP - count: {}, zipName: {}", filePaths.size(), zipName);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate all files
                List<Path> validPaths = new ArrayList<>();
                long totalSize = 0;
                
                for (String filePath : filePaths) {
                    Path path = Paths.get(filePath);
                    
                    if (!isPathAllowed(path)) {
                        throw new SecurityException("Access denied to path: " + filePath);
                    }
                    
                    if (!Files.exists(path)) {
                        throw new IllegalArgumentException("File not found: " + filePath);
                    }
                    
                    long fileSize = Files.size(path);
                    totalSize += fileSize;
                    validPaths.add(path);
                }
                
                // Size validation
                long maxZipSize = parseSize(maxZipSizeStr);
                if (totalSize > maxZipSize) {
                    throw new IllegalArgumentException("Total size too large: " + formatSize(totalSize) + " > " + maxZipSizeStr);
                }
                
                // Create download session
                String downloadId = UUID.randomUUID().toString();
                DownloadSession session = new DownloadSession(
                    downloadId,
                    DownloadType.ZIP_ARCHIVE,
                    validPaths,
                    LocalDateTime.now()
                );
                
                activeSessions.put(downloadId, session);
                session.setStatus(DownloadStatus.PROCESSING);
                
                // Create ZIP file
                byte[] zipData = createZipArchive(validPaths);
                session.setStatus(DownloadStatus.COMPLETED);
                
                log.info("ZIP download prepared - downloadId: {}, files: {}, size: {}", 
                        downloadId, filePaths.size(), formatSize(zipData.length));
                
                return new DownloadResult(
                    downloadId,
                    zipName.endsWith(".zip") ? zipName : zipName + ".zip",
                    zipData,
                    "application/zip",
                    zipData.length,
                    LocalDateTime.now()
                );
                
            } catch (Exception e) {
                log.error("ZIP download failed: {}", e.getMessage(), e);
                throw new RuntimeException("ZIP download failed: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Get download session status
     */
    public Optional<DownloadSession> getDownloadSession(String downloadId) {
        return Optional.ofNullable(activeSessions.get(downloadId));
    }
    
    /**
     * Cancel download session
     */
    public boolean cancelDownload(String downloadId) {
        DownloadSession session = activeSessions.get(downloadId);
        if (session != null) {
            session.setStatus(DownloadStatus.CANCELLED);
            activeSessions.remove(downloadId);
            log.info("Download cancelled - downloadId: {}", downloadId);
            return true;
        }
        return false;
    }
    
    /**
     * Search files by pattern
     */
    public List<FileInfo> searchFiles(String adapterName, String pattern, String directory) {
        log.info("Searching files - adapter: {}, pattern: {}, directory: {}", adapterName, pattern, directory);
        
        try {
            Path searchPath = getDirectoryPath(adapterName, directory);
            if (!Files.exists(searchPath)) {
                return List.of();
            }
            
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            List<FileInfo> results = new ArrayList<>();
            
            try (Stream<Path> paths = Files.walk(searchPath, 3)) {  // Limit depth for security
                paths.filter(Files::isRegularFile)
                     .filter(path -> matcher.matches(path.getFileName()))
                     .forEach(path -> {
                         try {
                             BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                             FileInfo fileInfo = new FileInfo(
                                 path.getFileName().toString(),
                                 path.toString(),
                                 attrs.size(),
                                 attrs.lastModifiedTime().toInstant(),
                                 attrs.creationTime().toInstant(),
                                 determineFileType(path),
                                 directory,
                                 Files.isReadable(path),
                                 null // Skip checksum for search results
                             );
                             results.add(fileInfo);
                         } catch (IOException e) {
                             log.warn("Failed to read file attributes for {}: {}", path, e.getMessage());
                         }
                     });
            }
            
            log.info("File search completed - found {} files", results.size());
            return results;
            
        } catch (Exception e) {
            log.error("File search failed: {}", e.getMessage(), e);
            throw new RuntimeException("File search failed: " + e.getMessage(), e);
        }
    }
    
    // Private helper methods
    
    private List<FileInfo> scanDirectory(Path directory, String dateFilter, String category) throws IOException {
        List<FileInfo> files = new ArrayList<>();
        
        try (Stream<Path> paths = Files.walk(directory, 2)) {  // Limit depth
            paths.filter(Files::isRegularFile)
                 .forEach(path -> {
                     try {
                         BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                         
                         // Apply date filter if specified
                         if (dateFilter != null && !dateFilter.isEmpty()) {
                             String fileDate = attrs.lastModifiedTime().toInstant()
                                 .toString().substring(0, 10); // YYYY-MM-DD
                             if (!fileDate.equals(dateFilter)) {
                                 return;
                             }
                         }
                         
                         FileInfo fileInfo = new FileInfo(
                             path.getFileName().toString(),
                             path.toString(),
                             attrs.size(),
                             attrs.lastModifiedTime().toInstant(),
                             attrs.creationTime().toInstant(),
                             determineFileType(path),
                             category,
                             Files.isReadable(path),
                             null // Skip checksum for listing
                         );
                         
                         files.add(fileInfo);
                     } catch (IOException e) {
                         log.warn("Failed to read file attributes for {}: {}", path, e.getMessage());
                     }
                 });
        }
        
        // Sort by modification time (newest first)
        files.sort((a, b) -> b.getLastModified().compareTo(a.getLastModified()));
        
        return files;
    }
    
    private Path getReceiverPath(String adapterName) {
        return Paths.get(baseStorageDirectory, adapterName, "receiver");
    }
    
    private Path getSenderPath(String adapterName) {
        return Paths.get(baseStorageDirectory, adapterName, "sender");
    }
    
    private Path getArchivePath(String adapterName) {
        return Paths.get(baseStorageDirectory, adapterName, "archive");
    }
    
    private Path getDirectoryPath(String adapterName, String directory) {
        return switch (directory.toLowerCase()) {
            case "receiver" -> getReceiverPath(adapterName);
            case "sender" -> getSenderPath(adapterName);
            case "archive" -> getArchivePath(adapterName);
            default -> throw new IllegalArgumentException("Invalid directory: " + directory);
        };
    }
    
    private boolean isPathAllowed(Path path) {
        try {
            Path basePath = Paths.get(baseStorageDirectory).normalize();
            Path normalizedPath = path.normalize();
            return normalizedPath.startsWith(basePath);
        } catch (Exception e) {
            return false;
        }
    }
    
    private String determineFileType(Path path) {
        try {
            String contentType = Files.probeContentType(path);
            if (contentType != null) {
                return contentType;
            }
        } catch (Exception e) {
            // Fallback to extension-based detection
        }
        
        String fileName = path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".xml")) return "application/xml";
        if (fileName.endsWith(".json")) return "application/json";
        if (fileName.endsWith(".csv")) return "text/csv";
        if (fileName.endsWith(".txt")) return "text/plain";
        if (fileName.endsWith(".zip")) return "application/zip";
        if (fileName.endsWith(".pdf")) return "application/pdf";
        
        return "application/octet-stream";
    }
    
    private String determineCategory(Path path) {
        Path parent = path.getParent();
        if (parent != null) {
            String parentName = parent.getFileName().toString().toLowerCase();
            if (parentName.contains("receiver")) return "receiver";
            if (parentName.contains("sender")) return "sender";
            if (parentName.contains("archive")) return "archive";
            if (parentName.contains("error")) return "error";
        }
        return "unknown";
    }
    
    private String generateChecksum(Path path) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(path);
            byte[] digest = md.digest(fileBytes);
            
            StringBuilder checksum = new StringBuilder();
            for (byte b : digest) {
                checksum.append(String.format("%02x", b));
            }
            
            return checksum.toString();
        } catch (Exception e) {
            log.debug("Failed to generate checksum for {}: {}", path, e.getMessage());
            return null;
        }
    }
    
    private byte[] createZipArchive(List<Path> files) throws IOException {
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            
            for (Path file : files) {
                String entryName = file.getFileName().toString();
                ZipEntry zipEntry = new ZipEntry(entryName);
                zos.putNextEntry(zipEntry);
                
                Files.copy(file, zos);
                zos.closeEntry();
            }
            
            zos.finish();
            return baos.toByteArray();
        }
    }
    
    private long parseSize(String sizeStr) {
        try {
            String upper = sizeStr.toUpperCase();
            long multiplier = 1;
            
            if (upper.endsWith("KB")) {
                multiplier = 1024;
                sizeStr = sizeStr.substring(0, sizeStr.length() - 2);
            } else if (upper.endsWith("MB")) {
                multiplier = 1024 * 1024;
                sizeStr = sizeStr.substring(0, sizeStr.length() - 2);
            } else if (upper.endsWith("GB")) {
                multiplier = 1024 * 1024 * 1024;
                sizeStr = sizeStr.substring(0, sizeStr.length() - 2);
            }
            
            return Long.parseLong(sizeStr.trim()) * multiplier;
        } catch (Exception e) {
            return 100 * 1024 * 1024; // Default 100MB
        }
    }
    
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    // Data classes
    
    public static class FileInfo {
        private final String name;
        private final String path;
        private final long size;
        private final java.time.Instant lastModified;
        private final java.time.Instant created;
        private final String contentType;
        private final String category;
        private final boolean readable;
        private final String checksum;
        
        public FileInfo(String name, String path, long size, java.time.Instant lastModified,
                       java.time.Instant created, String contentType, String category,
                       boolean readable, String checksum) {
            this.name = name;
            this.path = path;
            this.size = size;
            this.lastModified = lastModified;
            this.created = created;
            this.contentType = contentType;
            this.category = category;
            this.readable = readable;
            this.checksum = checksum;
        }
        
        // Getters
        public String getName() { return name; }
        public String getPath() { return path; }
        public long getSize() { return size; }
        public java.time.Instant getLastModified() { return lastModified; }
        public java.time.Instant getCreated() { return created; }
        public String getContentType() { return contentType; }
        public String getCategory() { return category; }
        public boolean isReadable() { return readable; }
        public String getChecksum() { return checksum; }
    }
    
    public static class DownloadResult {
        private final String downloadId;
        private final String fileName;
        private final byte[] data;
        private final String contentType;
        private final long size;
        private final LocalDateTime timestamp;
        
        public DownloadResult(String downloadId, String fileName, byte[] data, 
                             String contentType, long size, LocalDateTime timestamp) {
            this.downloadId = downloadId;
            this.fileName = fileName;
            this.data = data;
            this.contentType = contentType;
            this.size = size;
            this.timestamp = timestamp;
        }
        
        // Getters
        public String getDownloadId() { return downloadId; }
        public String getFileName() { return fileName; }
        public byte[] getData() { return data; }
        public String getContentType() { return contentType; }
        public long getSize() { return size; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    public static class DownloadSession {
        private final String id;
        private final DownloadType type;
        private final List<Path> files;
        private final LocalDateTime created;
        private DownloadStatus status;
        
        public DownloadSession(String id, DownloadType type, List<Path> files, LocalDateTime created) {
            this.id = id;
            this.type = type;
            this.files = files;
            this.created = created;
            this.status = DownloadStatus.PENDING;
        }
        
        // Getters and setters
        public String getId() { return id; }
        public DownloadType getType() { return type; }
        public List<Path> getFiles() { return files; }
        public LocalDateTime getCreated() { return created; }
        public DownloadStatus getStatus() { return status; }
        public void setStatus(DownloadStatus status) { this.status = status; }
    }
    
    public enum DownloadType {
        SINGLE_FILE,
        ZIP_ARCHIVE
    }
    
    public enum DownloadStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}