package com.integrixs.backend.service;

import com.integrixs.core.repository.SshKeyRepository;
import com.integrixs.shared.model.SshKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for SSH key management and SFTP authentication integration
 */
@Service
public class SshKeyService {
    
    private static final Logger logger = LoggerFactory.getLogger(SshKeyService.class);
    
    private final SshKeyRepository sshKeyRepository;
    
    @Autowired
    public SshKeyService(SshKeyRepository sshKeyRepository) {
        this.sshKeyRepository = sshKeyRepository;
    }
    
    /**
     * Get all SSH keys
     */
    public List<SshKey> getAllSshKeys() {
        logger.debug("Retrieving all SSH keys");
        return sshKeyRepository.findAll();
    }
    
    /**
     * Get all enabled SSH keys
     */
    public List<SshKey> getEnabledSshKeys() {
        logger.debug("Retrieving enabled SSH keys");
        return sshKeyRepository.findAllActive();
    }
    
    /**
     * Get SSH key by ID
     */
    public Optional<SshKey> getSshKeyById(UUID id) {
        logger.debug("Retrieving SSH key by ID: {}", id);
        return sshKeyRepository.findById(id);
    }
    
    /**
     * Get SSH key by name
     */
    public Optional<SshKey> getSshKeyByName(String name) {
        logger.debug("Retrieving SSH key by name: {}", name);
        return sshKeyRepository.findByName(name);
    }
    
    /**
     * Get SSH key by fingerprint
     */
    public Optional<SshKey> getSshKeyByFingerprint(String fingerprint) {
        logger.debug("Retrieving SSH key by fingerprint: {}", fingerprint);
        return sshKeyRepository.findByFingerprint(fingerprint);
    }
    
    /**
     * Get SSH keys created by a specific user
     */
    public List<SshKey> getSshKeysByUser(UUID userId) {
        logger.debug("Retrieving SSH keys for user: {}", userId);
        return sshKeyRepository.findByCreatedBy(userId);
    }
    
    /**
     * Create a new SSH key
     */
    public SshKey createSshKey(String name, String description, String privateKey, String publicKey, 
                               String keyType, Integer keySize, UUID createdBy) {
        
        logger.info("Creating new SSH key: {}", name);
        
        // Validate input
        validateSshKeyInput(name, privateKey, publicKey, keyType);
        
        // Check if name already exists
        if (sshKeyRepository.existsByName(name)) {
            throw new IllegalArgumentException("SSH key with name '" + name + "' already exists");
        }
        
        // Generate fingerprint
        String fingerprint = generateFingerprint(publicKey);
        
        // Check if fingerprint already exists
        if (sshKeyRepository.existsByFingerprint(fingerprint)) {
            throw new IllegalArgumentException("SSH key with this fingerprint already exists");
        }
        
        // Create SSH key
        SshKey sshKey = new SshKey(name, privateKey, publicKey, keyType, keySize, createdBy);
        sshKey.setDescription(description);
        sshKey.setFingerprint(fingerprint);
        
        UUID id = sshKeyRepository.save(sshKey);
        logger.info("Successfully created SSH key: {} with ID: {}", name, id);
        
        return sshKey;
    }
    
    /**
     * Update an existing SSH key
     */
    public SshKey updateSshKey(UUID id, String name, String description, String privateKey, 
                               String publicKey, String keyType, Integer keySize) {
        
        logger.info("Updating SSH key: {}", id);
        
        Optional<SshKey> existing = sshKeyRepository.findById(id);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("SSH key not found with ID: " + id);
        }
        
        SshKey sshKey = existing.get();
        
        // Update fields if provided
        if (name != null && !name.equals(sshKey.getName())) {
            if (sshKeyRepository.existsByName(name)) {
                throw new IllegalArgumentException("SSH key with name '" + name + "' already exists");
            }
            sshKey.setName(name);
        }
        
        if (description != null) {
            sshKey.setDescription(description);
        }
        
        if (privateKey != null && !privateKey.equals(sshKey.getPrivateKey())) {
            validatePrivateKey(privateKey);
            sshKey.setPrivateKey(privateKey);
        }
        
        if (publicKey != null && !publicKey.equals(sshKey.getPublicKey())) {
            validatePublicKey(publicKey);
            String newFingerprint = generateFingerprint(publicKey);
            
            if (!newFingerprint.equals(sshKey.getFingerprint()) && 
                sshKeyRepository.existsByFingerprint(newFingerprint)) {
                throw new IllegalArgumentException("SSH key with this fingerprint already exists");
            }
            
            sshKey.setPublicKey(publicKey);
            sshKey.setFingerprint(newFingerprint);
        }
        
        if (keyType != null) {
            validateKeyType(keyType);
            sshKey.setKeyType(keyType);
        }
        
        if (keySize != null) {
            sshKey.setKeySize(keySize);
        }
        
        sshKeyRepository.save(sshKey);
        logger.info("Successfully updated SSH key: {}", id);
        
        return sshKey;
    }
    
    /**
     * Delete an SSH key
     */
    public void deleteSshKey(UUID id) {
        logger.info("Deleting SSH key: {}", id);
        
        Optional<SshKey> existing = sshKeyRepository.findById(id);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("SSH key not found with ID: " + id);
        }
        
        sshKeyRepository.deleteById(id);
        logger.info("Successfully deleted SSH key: {}", id);
    }
    
    /**
     * Enable/disable an SSH key
     */
    public void setSshKeyEnabled(UUID id, boolean enabled) {
        logger.info("Setting SSH key enabled status: {} -> {}", id, enabled);
        
        Optional<SshKey> existing = sshKeyRepository.findById(id);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("SSH key not found with ID: " + id);
        }
        
        sshKeyRepository.setActive(id, enabled);
        logger.info("Successfully updated SSH key enabled status: {}", id);
    }
    
    /**
     * Usage tracking removed - not needed for SSH key management
     */
    public void recordSshKeyUsage(UUID id) {
        logger.debug("SSH key usage tracking disabled for: {}", id);
        // No operation - usage tracking removed
    }
    
    /**
     * Get SSH key for SFTP adapter configuration
     * This method is used by adapters to retrieve key data for authentication
     */
    public Optional<SshKey> getSshKeyForAdapter(String keyName) {
        logger.debug("Retrieving SSH key for adapter: {}", keyName);
        
        Optional<SshKey> sshKey = sshKeyRepository.findByName(keyName);
        
        if (sshKey.isPresent() && sshKey.get().isActive()) {
            // Record usage
            recordSshKeyUsage(sshKey.get().getId());
            return sshKey;
        }
        
        return Optional.empty();
    }
    
    /**
     * Validate SSH key input data
     */
    private void validateSshKeyInput(String name, String privateKey, String publicKey, String keyType) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("SSH key name is required");
        }
        
        if (name.length() > 255) {
            throw new IllegalArgumentException("SSH key name must not exceed 255 characters");
        }
        
        validatePrivateKey(privateKey);
        validatePublicKey(publicKey);
        validateKeyType(keyType);
    }
    
    /**
     * Validate private key format
     */
    private void validatePrivateKey(String privateKey) {
        if (privateKey == null || privateKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Private key is required");
        }
        
        // Basic format validation
        String trimmed = privateKey.trim();
        if (!trimmed.startsWith("-----BEGIN") || !trimmed.contains("PRIVATE KEY")) {
            throw new IllegalArgumentException("Invalid private key format - must be in PEM format");
        }
    }
    
    /**
     * Validate public key format
     */
    private void validatePublicKey(String publicKey) {
        if (publicKey == null || publicKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Public key is required");
        }
        
        // Basic format validation - can be OpenSSH or PEM format
        String trimmed = publicKey.trim();
        if (!trimmed.startsWith("ssh-") && !trimmed.startsWith("-----BEGIN")) {
            throw new IllegalArgumentException("Invalid public key format - must be in OpenSSH or PEM format");
        }
    }
    
    /**
     * Validate key type
     */
    private void validateKeyType(String keyType) {
        if (keyType == null || keyType.trim().isEmpty()) {
            throw new IllegalArgumentException("Key type is required");
        }
        
        String upperKeyType = keyType.toUpperCase();
        if (!List.of("RSA", "DSA", "ECDSA", "ED25519").contains(upperKeyType)) {
            throw new IllegalArgumentException("Invalid key type - must be RSA, DSA, ECDSA, or ED25519");
        }
    }
    
    /**
     * Generate fingerprint for SSH public key
     */
    private String generateFingerprint(String publicKey) {
        try {
            // For simplicity, generate MD5 hash of the public key content
            // In production, this should be a proper SSH key fingerprint calculation
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(publicKey.getBytes());
            
            StringBuilder fingerprint = new StringBuilder();
            for (int i = 0; i < digest.length; i++) {
                if (i > 0) fingerprint.append(":");
                fingerprint.append(String.format("%02x", digest[i] & 0xff));
            }
            
            return fingerprint.toString();
            
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to generate fingerprint", e);
            throw new RuntimeException("Failed to generate SSH key fingerprint", e);
        }
    }
}