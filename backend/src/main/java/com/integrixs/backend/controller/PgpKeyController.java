package com.integrixs.backend.controller;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.shared.model.PgpKey;
import com.integrixs.core.service.PgpKeyService;
import com.integrixs.shared.util.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for PGP key management operations
 */
@RestController
@RequestMapping("/api/admin/pgp-keys")
@PreAuthorize("hasAuthority('ADMINISTRATOR')")
public class PgpKeyController {
    
    private static final Logger logger = LoggerFactory.getLogger(PgpKeyController.class);
    private final PgpKeyService pgpKeyService;
    
    @Autowired
    public PgpKeyController(PgpKeyService pgpKeyService) {
        this.pgpKeyService = pgpKeyService;
    }
    
    /**
     * Get all PGP keys
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PgpKey>>> getAllKeys() {
        try {
            List<PgpKey> keys = pgpKeyService.getAllKeys();
            return ResponseEntity.ok(ApiResponse.success(keys));
            
        } catch (Exception e) {
            logger.error("Failed to retrieve PGP keys", e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to retrieve PGP keys: " + e.getMessage()));
        }
    }
    
    /**
     * Get PGP key by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PgpKey>> getKeyById(@PathVariable UUID id) {
        try {
            Optional<PgpKey> key = pgpKeyService.getKeyById(id);
            
            if (key.isPresent()) {
                return ResponseEntity.ok(ApiResponse.success(key.get()));
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("Failed to retrieve PGP key: {}", id, e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to retrieve PGP key: " + e.getMessage()));
        }
    }
    
    /**
     * Generate new PGP key pair
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<PgpKey>> generateKeyPair(@RequestBody GeneratePgpKeyRequest request) {
        try {
            UUID currentUserId = SecurityContextHelper.getCurrentUserId();
            
            // Parse expiry date if provided
            LocalDateTime expiresAt = null;
            if (request.expiresAt != null && !request.expiresAt.trim().isEmpty()) {
                expiresAt = LocalDateTime.parse(request.expiresAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
            
            // Generate key pair
            PgpKey pgpKey = pgpKeyService.generateKeyPair(
                request.keyName,
                request.userId,
                request.passphrase,
                request.keyType,
                request.keySize,
                expiresAt,
                currentUserId
            );
            
            logger.info("Generated PGP key pair: {} for user: {}", request.keyName, currentUserId);
            return ResponseEntity.ok(ApiResponse.success(pgpKey));
            
        } catch (Exception e) {
            logger.error("Failed to generate PGP key pair", e);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Failed to generate PGP key: " + e.getMessage()));
        }
    }
    
    /**
     * Import PGP key from armored text
     */
    @PostMapping("/import")
    public ResponseEntity<ApiResponse<PgpKey>> importKey(@RequestBody ImportPgpKeyRequest request) {
        try {
            UUID currentUserId = SecurityContextHelper.getCurrentUserId();
            
            PgpKey pgpKey = pgpKeyService.importKey(
                request.keyName,
                request.armoredKey,
                request.description,
                currentUserId
            );
            
            logger.info("Imported PGP key: {} for user: {}", request.keyName, currentUserId);
            return ResponseEntity.ok(ApiResponse.success(pgpKey));
            
        } catch (Exception e) {
            logger.error("Failed to import PGP key", e);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Failed to import PGP key: " + e.getMessage()));
        }
    }
    
    /**
     * Export public key
     */
    @GetMapping("/{id}/export/public")
    public ResponseEntity<String> exportPublicKey(@PathVariable UUID id) {
        try {
            String publicKey = pgpKeyService.exportPublicKey(id);
            
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"public-key.asc\"")
                .body(publicKey);
            
        } catch (Exception e) {
            logger.error("Failed to export public key: {}", id, e);
            return ResponseEntity.badRequest()
                .body("Failed to export public key: " + e.getMessage());
        }
    }
    
    /**
     * Export private key (admin only)
     */
    @GetMapping("/{id}/export/private")
    public ResponseEntity<String> exportPrivateKey(@PathVariable UUID id) {
        try {
            String privateKey = pgpKeyService.exportPrivateKey(id);
            
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"private-key.asc\"")
                .body(privateKey);
            
        } catch (Exception e) {
            logger.error("Failed to export private key: {}", id, e);
            return ResponseEntity.badRequest()
                .body("Failed to export private key: " + e.getMessage());
        }
    }
    
    /**
     * Revoke PGP key
     */
    @PostMapping("/{id}/revoke")
    public ResponseEntity<ApiResponse<Void>> revokeKey(@PathVariable UUID id, @RequestBody RevokeKeyRequest request) {
        try {
            UUID currentUserId = SecurityContextHelper.getCurrentUserId();
            
            pgpKeyService.revokeKey(id, request.reason, currentUserId);
            
            logger.info("Revoked PGP key: {} by user: {}", id, currentUserId);
            return ResponseEntity.ok(ApiResponse.success(null));
            
        } catch (Exception e) {
            logger.error("Failed to revoke PGP key: {}", id, e);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Failed to revoke PGP key: " + e.getMessage()));
        }
    }
    
    /**
     * Delete PGP key
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteKey(@PathVariable UUID id) {
        try {
            pgpKeyService.deleteKey(id);
            
            UUID currentUserId = SecurityContextHelper.getCurrentUserId();
            logger.info("Deleted PGP key: {} by user: {}", id, currentUserId);
            
            return ResponseEntity.ok(ApiResponse.success(null));
            
        } catch (Exception e) {
            logger.error("Failed to delete PGP key: {}", id, e);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Failed to delete PGP key: " + e.getMessage()));
        }
    }
    
    /**
     * Get expired keys
     */
    @GetMapping("/expired")
    public ResponseEntity<ApiResponse<List<PgpKey>>> getExpiredKeys() {
        try {
            List<PgpKey> expiredKeys = pgpKeyService.getExpiredKeys();
            return ResponseEntity.ok(ApiResponse.success(expiredKeys));
            
        } catch (Exception e) {
            logger.error("Failed to retrieve expired PGP keys", e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to retrieve expired PGP keys: " + e.getMessage()));
        }
    }
    
    // Request DTOs
    
    public static class GeneratePgpKeyRequest {
        public String keyName;
        public String userId; // PGP User ID (email/name)
        public String passphrase;
        public PgpKey.KeyType keyType;
        public int keySize;
        public String expiresAt; // ISO date-time string
        public String description;
    }
    
    public static class ImportPgpKeyRequest {
        public String keyName;
        public String armoredKey; // ASCII armored PGP key
        public String description;
    }
    
    public static class RevokeKeyRequest {
        public String reason;
    }
}