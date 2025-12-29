package com.integrixs.backend.service;

import com.integrixs.core.repository.SshKeyRepository;
import com.integrixs.core.service.SshKeyGeneratorService;
import com.integrixs.shared.model.SshKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Enhanced SSH key management service with generation capabilities
 */
@Service
public class SshKeyManagementService {
    
    private static final Logger log = LoggerFactory.getLogger(SshKeyManagementService.class);
    
    @Autowired
    private SshKeyRepository sshKeyRepository;
    
    @Autowired
    private SshKeyGeneratorService sshKeyGeneratorService;
    
    /**
     * Generate and save new RSA SSH key pair
     */
    public SshKey generateRSAKey(String name, String description, int keySize, String comment, UUID createdBy) {
        log.info("Generating new RSA SSH key - name: {}, size: {}", name, keySize);
        
        try {
            // Check if name already exists
            if (isNameExists(name)) {
                throw new IllegalArgumentException("SSH key name already exists: " + name);
            }
            
            // Generate key pair
            SshKeyGeneratorService.SshKeyPair keyPair = sshKeyGeneratorService.generateRSAKeyPair(keySize, comment);
            
            // Check if fingerprint already exists
            if (isFingerprintExists(keyPair.getFingerprint())) {
                throw new IllegalStateException("Generated SSH key fingerprint already exists (very unlikely!)");
            }
            
            // Create SshKey entity
            SshKey sshKey = new SshKey();
            // Do NOT set ID here - let repository handle it
            sshKey.setName(name);
            sshKey.setDescription(description);
            sshKey.setPrivateKey(keyPair.getPrivateKey());
            sshKey.setPublicKey(keyPair.getPublicKey());
            sshKey.setKeyType(keyPair.getAlgorithm());
            sshKey.setKeySize(keyPair.getKeySize());
            sshKey.setFingerprint(keyPair.getFingerprint());
            sshKey.setActive(true);
            sshKey.setCreatedAt(LocalDateTime.now());
            sshKey.setCreatedBy(createdBy);
            // Set expiration to 2 years from creation
            sshKey.setExpiresAt(LocalDateTime.now().plusYears(2));
            
            // Save to database
            log.debug("About to save SSH key to database - name: {}", name);
            UUID savedId = sshKeyRepository.save(sshKey);
            sshKey.setId(savedId);
            log.debug("SSH key save operation completed - savedId: {}", savedId);
            
            // Verify the key was actually saved by trying to retrieve it
            Optional<SshKey> verification = sshKeyRepository.findById(savedId);
            if (verification.isPresent()) {
                log.info("RSA SSH key generated and VERIFIED saved - id: {}, fingerprint: {}", sshKey.getId(), sshKey.getFingerprint());
            } else {
                log.error("SSH key save verification FAILED - key not found in database after save: {}", savedId);
                throw new RuntimeException("SSH key was not persisted to database");
            }
            
            return sshKey;
            
        } catch (Exception e) {
            log.error("Failed to generate RSA SSH key {}: {}", name, e.getMessage(), e);
            throw new RuntimeException("Failed to generate RSA SSH key: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate and save new DSA SSH key pair
     */
    public SshKey generateDSAKey(String name, String description, int keySize, String comment, UUID createdBy) {
        log.info("Generating new DSA SSH key - name: {}, size: {}", name, keySize);
        
        try {
            // Check if name already exists
            if (isNameExists(name)) {
                throw new IllegalArgumentException("SSH key name already exists: " + name);
            }
            
            // Generate key pair
            SshKeyGeneratorService.SshKeyPair keyPair = sshKeyGeneratorService.generateDSAKeyPair(keySize, comment);
            
            // Check if fingerprint already exists
            if (isFingerprintExists(keyPair.getFingerprint())) {
                throw new IllegalStateException("Generated SSH key fingerprint already exists (very unlikely!)");
            }
            
            // Create SshKey entity
            SshKey sshKey = new SshKey();
            // Do NOT set ID here - let repository handle it
            sshKey.setName(name);
            sshKey.setDescription(description);
            sshKey.setPrivateKey(keyPair.getPrivateKey());
            sshKey.setPublicKey(keyPair.getPublicKey());
            sshKey.setKeyType(keyPair.getAlgorithm());
            sshKey.setKeySize(keyPair.getKeySize());
            sshKey.setFingerprint(keyPair.getFingerprint());
            sshKey.setActive(true);
            sshKey.setCreatedAt(LocalDateTime.now());
            sshKey.setCreatedBy(createdBy);
            // Set expiration to 2 years from creation
            sshKey.setExpiresAt(LocalDateTime.now().plusYears(2));
            
            // Save to database
            UUID savedId = sshKeyRepository.save(sshKey);
            sshKey.setId(savedId);
            
            log.info("DSA SSH key generated and saved - id: {}, fingerprint: {}", sshKey.getId(), sshKey.getFingerprint());
            return sshKey;
            
        } catch (Exception e) {
            log.error("Failed to generate DSA SSH key {}: {}", name, e.getMessage(), e);
            throw new RuntimeException("Failed to generate DSA SSH key: " + e.getMessage(), e);
        }
    }
    
    /**
     * Import existing SSH key pair
     */
    public SshKey importKeyPair(String name, String description, String privateKey, 
                               String publicKey, UUID createdBy) {
        log.info("Importing SSH key pair - name: {}", name);
        
        try {
            // Validate keys
            if (!sshKeyGeneratorService.validatePrivateKey(privateKey)) {
                throw new IllegalArgumentException("Invalid private key format");
            }
            
            if (!sshKeyGeneratorService.validatePublicKey(publicKey)) {
                throw new IllegalArgumentException("Invalid public key format");
            }
            
            // Check if name already exists
            if (isNameExists(name)) {
                throw new IllegalArgumentException("SSH key name already exists: " + name);
            }
            
            // Extract key metadata
            String keyType = sshKeyGeneratorService.getKeyAlgorithm(publicKey);
            String comment = sshKeyGeneratorService.getKeyComment(publicKey);
            
            // Generate fingerprint from public key
            // For imported keys, we'll create a simple hash-based fingerprint
            String fingerprint = generateImportedKeyFingerprint(publicKey);
            
            // Check if fingerprint already exists
            if (isFingerprintExists(fingerprint)) {
                throw new IllegalArgumentException("SSH key with this fingerprint already exists");
            }
            
            // Create SshKey entity
            SshKey sshKey = new SshKey();
            // Do NOT set ID here - let repository handle it
            sshKey.setName(name);
            sshKey.setDescription(description + (comment != null ? " (Comment: " + comment + ")" : ""));
            sshKey.setPrivateKey(privateKey);
            sshKey.setPublicKey(publicKey);
            sshKey.setKeyType(keyType);
            sshKey.setKeySize(extractKeySize(publicKey));
            sshKey.setFingerprint(fingerprint);
            sshKey.setActive(true);
            sshKey.setCreatedAt(LocalDateTime.now());
            sshKey.setCreatedBy(createdBy);
            // Set expiration to 2 years from creation
            sshKey.setExpiresAt(LocalDateTime.now().plusYears(2));
            
            // Save to database
            UUID savedId = sshKeyRepository.save(sshKey);
            sshKey.setId(savedId);
            
            log.info("SSH key pair imported and saved - id: {}, fingerprint: {}", sshKey.getId(), sshKey.getFingerprint());
            return sshKey;
            
        } catch (Exception e) {
            log.error("Failed to import SSH key pair {}: {}", name, e.getMessage(), e);
            throw new RuntimeException("Failed to import SSH key pair: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get all SSH keys
     */
    public List<SshKey> getAllKeys() {
        try {
            return sshKeyRepository.findAll();
        } catch (Exception e) {
            log.error("Failed to retrieve all SSH keys: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve SSH keys", e);
        }
    }
    
    /**
     * Get all enabled SSH keys
     */
    public List<SshKey> getEnabledKeys() {
        try {
            return sshKeyRepository.findAllActive();
        } catch (Exception e) {
            log.error("Failed to retrieve enabled SSH keys: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve enabled SSH keys", e);
        }
    }
    
    /**
     * Get SSH key by ID
     */
    public Optional<SshKey> getKeyById(UUID keyId) {
        try {
            return sshKeyRepository.findById(keyId);
        } catch (Exception e) {
            log.error("Failed to retrieve SSH key {}: {}", keyId, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve SSH key", e);
        }
    }
    
    /**
     * Get SSH key by name
     */
    public Optional<SshKey> getKeyByName(String name) {
        try {
            return sshKeyRepository.findByName(name);
        } catch (Exception e) {
            log.error("Failed to retrieve SSH key by name {}: {}", name, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve SSH key by name", e);
        }
    }
    
    /**
     * Get SSH key by fingerprint
     */
    public Optional<SshKey> getKeyByFingerprint(String fingerprint) {
        try {
            return sshKeyRepository.findByFingerprint(fingerprint);
        } catch (Exception e) {
            log.error("Failed to retrieve SSH key by fingerprint {}: {}", fingerprint, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve SSH key by fingerprint", e);
        }
    }
    
    // SSH keys are cryptographic certificates and cannot be updated
    // To change a key, delete it and generate a new one
    
    /**
     * Enable/disable SSH key
     */
    public void toggleKeyStatus(UUID keyId, boolean enabled) {
        log.info("Toggling SSH key status - id: {}, enabled: {}", keyId, enabled);
        
        try {
            Optional<SshKey> existing = sshKeyRepository.findById(keyId);
            if (existing.isEmpty()) {
                throw new IllegalArgumentException("SSH key not found: " + keyId);
            }
            
            sshKeyRepository.setActive(keyId, enabled);
            
            log.info("SSH key status updated - id: {}, enabled: {}", keyId, enabled);
            
        } catch (Exception e) {
            log.error("Failed to toggle SSH key status {}: {}", keyId, e.getMessage(), e);
            throw new RuntimeException("Failed to toggle SSH key status: " + e.getMessage(), e);
        }
    }
    
    /**
     * Delete SSH key
     */
    public void deleteKey(UUID keyId) {
        log.info("Deleting SSH key - id: {}", keyId);
        
        try {
            Optional<SshKey> existing = sshKeyRepository.findById(keyId);
            if (existing.isEmpty()) {
                throw new IllegalArgumentException("SSH key not found: " + keyId);
            }
            
            sshKeyRepository.delete(keyId);
            
            log.info("SSH key deleted - id: {}", keyId);
            
        } catch (Exception e) {
            log.error("Failed to delete SSH key {}: {}", keyId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete SSH key: " + e.getMessage(), e);
        }
    }
    
    // Usage tracking removed - not needed for SSH key management
    
    /**
     * Get SSH key generation options
     */
    public KeyGenerationOptions getGenerationOptions() {
        return new KeyGenerationOptions();
    }
    
    // Private helper methods
    
    private boolean isNameExists(String name) {
        return sshKeyRepository.findByName(name).isPresent();
    }
    
    private boolean isFingerprintExists(String fingerprint) {
        return sshKeyRepository.findByFingerprint(fingerprint).isPresent();
    }
    
    private String generateImportedKeyFingerprint(String publicKey) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(publicKey.getBytes("UTF-8"));
            String base64 = java.util.Base64.getEncoder().encodeToString(digest);
            return "IMPORTED:" + base64.substring(0, 32);
        } catch (Exception e) {
            return "IMPORTED:" + Integer.toHexString(publicKey.hashCode());
        }
    }
    
    private int extractKeySize(String publicKey) {
        try {
            // Simple extraction for common key types
            if (publicKey.contains("ssh-rsa")) {
                // For RSA keys, estimate size based on base64 length
                String[] parts = publicKey.split("\\s+");
                if (parts.length >= 2) {
                    int base64Length = parts[1].length();
                    // Rough estimate: 2048-bit keys have ~370 chars, 4096-bit keys have ~720 chars
                    if (base64Length > 600) return 4096;
                    if (base64Length > 300) return 2048;
                    return 1024;
                }
            }
            return 2048; // Default
        } catch (Exception e) {
            return 2048; // Default
        }
    }
    
    /**
     * SSH key generation options
     */
    public static class KeyGenerationOptions {
        public List<String> getSupportedAlgorithms() {
            return List.of("RSA", "DSA");
        }
        
        public List<Integer> getSupportedKeySizes(String algorithm) {
            return switch (algorithm.toUpperCase()) {
                case "RSA" -> List.of(1024, 2048, 4096);
                case "DSA" -> List.of(1024, 2048, 3072);
                default -> List.of(2048);
            };
        }
        
        public int getDefaultKeySize(String algorithm) {
            return 2048;
        }
        
        public String getRecommendedAlgorithm() {
            return "RSA";
        }
        
        public int getRecommendedKeySize() {
            return 2048;
        }
    }
}