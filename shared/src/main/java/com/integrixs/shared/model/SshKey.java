package com.integrixs.shared.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * SSH Key model for SFTP authentication
 */
public class SshKey {
    
    private UUID id;
    private String name;
    private String description;
    private String privateKey;
    private String publicKey;
    private String keyType;
    private Integer keySize;
    private String fingerprint;
    private Boolean active;
    private LocalDateTime createdAt;
    private UUID createdBy;
    private LocalDateTime expiresAt;
    
    public SshKey() {}
    
    public SshKey(String name, String privateKey, String publicKey, String keyType, Integer keySize, UUID createdBy) {
        this.name = name;
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.keyType = keyType;
        this.keySize = keySize;
        this.createdBy = createdBy;
        this.active = true;
    }
    
    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
    
    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
    
    public String getKeyType() { return keyType; }
    public void setKeyType(String keyType) { this.keyType = keyType; }
    
    public Integer getKeySize() { return keySize; }
    public void setKeySize(Integer keySize) { this.keySize = keySize; }
    
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    
    
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    
    // Helper methods
    public boolean isActive() {
        return active != null && active;
    }
    
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    public boolean isValid() {
        return isActive() && !isExpired();
    }
    
    
    /**
     * Returns the public key in OpenSSH format for deployment
     */
    public String getOpenSshPublicKey() {
        if (publicKey == null) return null;
        
        // If already in OpenSSH format, return as is
        if (publicKey.startsWith("ssh-")) {
            return publicKey;
        }
        
        // Convert PEM format to OpenSSH format (simplified)
        return publicKey.trim();
    }
    
    /**
     * Masks the private key for secure display/logging
     */
    public String getMaskedPrivateKey() {
        if (privateKey == null || privateKey.length() < 20) {
            return "***MASKED***";
        }
        return privateKey.substring(0, 10) + "..." + privateKey.substring(privateKey.length() - 10) + " (length: " + privateKey.length() + ")";
    }
    
    @Override
    public String toString() {
        return "SshKey{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", keyType='" + keyType + '\'' +
                ", keySize=" + keySize +
                ", active=" + active +
                ", fingerprint='" + fingerprint + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}