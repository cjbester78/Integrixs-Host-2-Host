package com.integrixs.backend.controller;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.backend.service.SshKeyManagementService;
import com.integrixs.shared.model.SshKey;
import com.integrixs.shared.util.SecurityContextHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST API controller for SSH key generation and management
 */
@RestController
@RequestMapping("/api/ssh-keys")
public class SshKeyManagementController {
    
    private static final Logger log = LoggerFactory.getLogger(SshKeyManagementController.class);
    
    @Autowired
    private SshKeyManagementService sshKeyManagementService;
    

    /**
     * Generate new RSA SSH key pair
     * POST /api/ssh-keys/generate/rsa
     */
    @PostMapping("/generate/rsa")
    public ResponseEntity<SshKeyResponse> generateRSAKey(@RequestBody GenerateRSAKeyRequest request, HttpServletRequest httpRequest) {
        log.info("Generating RSA SSH key - name: {}, size: {}", request.getName(), request.getKeySize());
        
        try {
            // Get user from JWT or session
            UUID createdBy = UUID.fromString(getAuthenticatedUser(httpRequest));
            
            SshKey sshKey = sshKeyManagementService.generateRSAKey(
                request.getName(),
                request.getDescription(),
                request.getKeySize() != null ? request.getKeySize() : 2048,
                request.getComment(),
                createdBy
            );
            
            SshKeyResponse response = convertToResponse(sshKey);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid RSA key generation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("RSA SSH key generation failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Generate new DSA SSH key pair
     * POST /api/ssh-keys/generate/dsa
     */
    @PostMapping("/generate/dsa")
    public ResponseEntity<SshKeyResponse> generateDSAKey(@RequestBody GenerateDSAKeyRequest request, HttpServletRequest httpRequest) {
        log.info("Generating DSA SSH key - name: {}, size: {}", request.getName(), request.getKeySize());
        
        try {
            // Get user from JWT or session
            UUID createdBy = UUID.fromString(getAuthenticatedUser(httpRequest));
            
            SshKey sshKey = sshKeyManagementService.generateDSAKey(
                request.getName(),
                request.getDescription(),
                request.getKeySize() != null ? request.getKeySize() : 1024,
                request.getComment(),
                createdBy
            );
            
            SshKeyResponse response = convertToResponse(sshKey);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid DSA key generation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("DSA SSH key generation failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Import existing SSH key pair
     * POST /api/ssh-keys/import
     */
    @PostMapping("/import")
    public ResponseEntity<SshKeyResponse> importKeyPair(@RequestBody ImportKeyRequest request, HttpServletRequest httpRequest) {
        log.info("Importing SSH key pair - name: {}", request.getName());
        
        try {
            // Get user from JWT or session
            UUID createdBy = UUID.fromString(getAuthenticatedUser(httpRequest));
            
            SshKey sshKey = sshKeyManagementService.importKeyPair(
                request.getName(),
                request.getDescription(),
                request.getPrivateKey(),
                request.getPublicKey(),
                createdBy
            );
            
            SshKeyResponse response = convertToResponse(sshKey);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid key import request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("SSH key import failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get all SSH keys
     * GET /api/ssh-keys
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SshKeyResponse>>> getAllKeys(@RequestParam(value = "enabledOnly", defaultValue = "false") boolean enabledOnly) {
        log.debug("Retrieving SSH keys - enabledOnly: {}", enabledOnly);

        try {
            List<SshKey> sshKeys = enabledOnly ?
                sshKeyManagementService.getEnabledKeys() :
                sshKeyManagementService.getAllKeys();

            List<SshKeyResponse> responses = sshKeys.stream()
                .map(this::convertToResponse)
                .toList();

            return ResponseEntity.ok(ApiResponse.success("SSH keys retrieved successfully", responses));

        } catch (Exception e) {
            log.error("Failed to retrieve SSH keys: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("Failed to retrieve SSH keys"));
        }
    }
    
    /**
     * Get SSH key by ID
     * GET /api/ssh-keys/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SshKeyResponse>> getKeyById(@PathVariable UUID id) {
        log.debug("Retrieving SSH key - id: {}", id);

        try {
            Optional<SshKey> sshKey = sshKeyManagementService.getKeyById(id);

            if (sshKey.isPresent()) {
                SshKeyResponse response = convertToResponse(sshKey.get());
                return ResponseEntity.ok(ApiResponse.success("SSH key retrieved successfully", response));
            } else {
                return ResponseEntity.status(404)
                    .body(ApiResponse.error("SSH key not found"));
            }

        } catch (Exception e) {
            log.error("Failed to retrieve SSH key {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve SSH key"));
        }
    }
    
    // SSH keys cannot be updated - they are cryptographic certificates
    // To modify a key, delete it and generate a new one
    
    /**
     * Toggle SSH key status (enable/disable)
     * PUT /api/ssh-keys/{id}/status
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<SshKeyResponse> toggleKeyStatus(@PathVariable UUID id, @RequestBody ToggleStatusRequest request, HttpServletRequest httpRequest) {
        log.info("Toggling SSH key status - id: {}, enabled: {}", id, request.isEnabled());
        
        try {
            // Get user from JWT or session
            UUID updatedBy = UUID.fromString(getAuthenticatedUser(httpRequest));
            
            sshKeyManagementService.toggleKeyStatus(id, request.isEnabled());
            
            // Fetch the updated key to return
            Optional<SshKey> updatedKey = sshKeyManagementService.getKeyById(id);
            if (updatedKey.isPresent()) {
                SshKeyResponse response = convertToResponse(updatedKey.get());
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid status toggle request for {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("SSH key status toggle failed for {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Delete SSH key
     * DELETE /api/ssh-keys/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteKey(@PathVariable UUID id) {
        log.info("Deleting SSH key - id: {}", id);
        
        try {
            sshKeyManagementService.deleteKey(id);
            return ResponseEntity.ok().build();
            
        } catch (IllegalArgumentException e) {
            log.warn("SSH key not found for deletion: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("SSH key deletion failed for {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Download private key
     * GET /api/ssh-keys/{id}/download/private
     */
    @GetMapping("/{id}/download/private")
    public ResponseEntity<ByteArrayResource> downloadPrivateKey(@PathVariable UUID id) {
        log.info("Downloading private key - id: {}", id);
        
        try {
            Optional<SshKey> sshKeyOpt = sshKeyManagementService.getKeyById(id);
            
            if (sshKeyOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            SshKey sshKey = sshKeyOpt.get();
            
            // No usage tracking needed
            
            byte[] keyData = sshKey.getPrivateKey().getBytes();
            ByteArrayResource resource = new ByteArrayResource(keyData);
            
            String filename = sanitizeFilename(sshKey.getName()) + "_private.key";
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
            headers.add("X-Filename", filename);
            headers.add("X-Timestamp", timestamp);
            
            return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(keyData.length)
                .body(resource);
                
        } catch (Exception e) {
            log.error("Private key download failed for {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Download public key
     * GET /api/ssh-keys/{id}/download/public
     */
    @GetMapping("/{id}/download/public")
    public ResponseEntity<ByteArrayResource> downloadPublicKey(@PathVariable UUID id) {
        log.info("Downloading public key - id: {}", id);
        
        try {
            Optional<SshKey> sshKeyOpt = sshKeyManagementService.getKeyById(id);
            
            if (sshKeyOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            SshKey sshKey = sshKeyOpt.get();
            
            // No usage tracking needed
            
            byte[] keyData = sshKey.getPublicKey().getBytes();
            ByteArrayResource resource = new ByteArrayResource(keyData);
            
            String filename = sanitizeFilename(sshKey.getName()) + "_public.pub";
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
            headers.add("X-Filename", filename);
            headers.add("X-Timestamp", timestamp);
            
            return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.TEXT_PLAIN)
                .contentLength(keyData.length)
                .body(resource);
                
        } catch (Exception e) {
            log.error("Public key download failed for {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get SSH key generation options
     * GET /api/ssh-keys/generation-options
     */
    @GetMapping("/generation-options")
    public ResponseEntity<ApiResponse<SshKeyManagementService.KeyGenerationOptions>> getGenerationOptions() {
        try {
            SshKeyManagementService.KeyGenerationOptions options = sshKeyManagementService.getGenerationOptions();
            return ResponseEntity.ok(ApiResponse.success("Generation options retrieved successfully", options));
        } catch (Exception e) {
            log.error("Failed to get generation options: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to get generation options"));
        }
    }
    
    // Private helper methods
    
    private String getAuthenticatedUser(HttpServletRequest request) {
        // Extract current user ID from Spring Security context
        return SecurityContextHelper.getCurrentUserIdAsString();
    }
    
    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
    
    private SshKeyResponse convertToResponse(SshKey sshKey) {
        return new SshKeyResponse(
            sshKey.getId(),
            sshKey.getName(),
            sshKey.getDescription(),
            sshKey.getKeyType(),
            sshKey.getKeySize(),
            sshKey.getFingerprint(),
            sshKey.getPublicKey(),
            sshKey.isActive(),
            sshKey.getCreatedAt(),
            sshKey.getCreatedBy() != null ? sshKey.getCreatedBy().toString() : null,
            sshKey.getExpiresAt()
        );
    }
    
    // Request/Response DTOs
    
    public static class GenerateRSAKeyRequest {
        private String name;
        private String description;
        private Integer keySize;
        private String comment;
        
        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Integer getKeySize() { return keySize; }
        public void setKeySize(Integer keySize) { this.keySize = keySize; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }
    
    public static class GenerateDSAKeyRequest {
        private String name;
        private String description;
        private Integer keySize;
        private String comment;
        
        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Integer getKeySize() { return keySize; }
        public void setKeySize(Integer keySize) { this.keySize = keySize; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }
    
    public static class ImportKeyRequest {
        private String name;
        private String description;
        private String privateKey;
        private String publicKey;
        
        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getPrivateKey() { return privateKey; }
        public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
        public String getPublicKey() { return publicKey; }
        public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
    }
    
    public static class UpdateKeyRequest {
        private String name;
        private String description;
        
        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
    
    public static class ToggleStatusRequest {
        private boolean enabled;
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
    
    public static class SshKeyResponse {
        private final UUID id;
        private final String name;
        private final String description;
        private final String keyType;
        private final int keySize;
        private final String fingerprint;
        private final String publicKey;
        private final boolean enabled;
        private final LocalDateTime createdAt;
        private final String createdBy;
        private final LocalDateTime expiresAt;
        
        public SshKeyResponse(UUID id, String name, String description, String keyType, 
                             int keySize, String fingerprint, String publicKey, boolean enabled,
                             LocalDateTime createdAt, String createdBy, LocalDateTime expiresAt) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.keyType = keyType;
            this.keySize = keySize;
            this.fingerprint = fingerprint;
            this.publicKey = publicKey;
            this.enabled = enabled;
            this.createdAt = createdAt;
            this.createdBy = createdBy;
            this.expiresAt = expiresAt;
        }
        
        // Getters
        public UUID getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getKeyType() { return keyType; }
        public int getKeySize() { return keySize; }
        public String getFingerprint() { return fingerprint; }
        public String getPublicKey() { return publicKey; }
        public boolean isEnabled() { return enabled; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public String getCreatedBy() { return createdBy; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
    }
}