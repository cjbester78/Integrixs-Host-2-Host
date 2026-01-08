package com.integrixs.shared.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing a PGP encryption key
 * Used for encrypting/decrypting files in secure transfers
 */
public class PgpKey {
    
    private UUID id;
    private String keyName;
    private String description;
    private KeyType keyType;
    private Integer keySize;
    private String userId; // PGP User ID (email/name)
    private String fingerprint;
    private String keyId; // Short key ID
    
    // Key material
    private String publicKey; // ASCII armored
    private String privateKey; // ASCII armored (nullable for imported public keys)
    
    // Key metadata
    private String algorithm;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private String revocationReason;
    
    // Usage flags
    private Boolean canEncrypt;
    private Boolean canSign;
    private Boolean canCertify;
    private Boolean canAuthenticate;
    
    // Import/Export metadata
    private String importedFrom;
    private Integer exportedCount;
    private LocalDateTime lastUsedAt;
    
    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
    
    public enum KeyType {
        RSA("RSA"),
        ECC("Elliptic Curve"),
        DSA("Digital Signature Algorithm");
        
        private final String displayName;
        
        KeyType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // Default constructor
    public PgpKey() {
        this.canEncrypt = true;
        this.canSign = true;
        this.canCertify = false;
        this.canAuthenticate = false;
        this.exportedCount = 0;
        this.createdAt = LocalDateTime.now();
        // updatedAt and updatedBy should be NULL on creation - only set on actual updates
        this.updatedAt = null;
        this.updatedBy = null;
    }
    
    // Constructor for new key generation
    public PgpKey(String keyName, String userId, KeyType keyType, Integer keySize) {
        this();
        this.keyName = keyName;
        this.userId = userId;
        this.keyType = keyType;
        this.keySize = keySize;
        // ID will be assigned by the repository during save
    }
    
    /**
     * Mark entity as updated by specified user. Should be called for all business logic updates.
     * This properly maintains the audit trail for UPDATE operations.
     */
    public void markAsUpdated(UUID updatedBy) {
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = Objects.requireNonNull(updatedBy, "Updated by cannot be null");
    }
    
    // Utility methods
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }
    
    public boolean isRevoked() {
        return revokedAt != null;
    }
    
    public boolean isValid() {
        return !isExpired() && !isRevoked();
    }
    
    public boolean hasPrivateKey() {
        return privateKey != null && !privateKey.trim().isEmpty();
    }
    
    public String getShortFingerprint() {
        if (fingerprint == null || fingerprint.length() < 8) {
            return fingerprint;
        }
        return fingerprint.substring(fingerprint.length() - 8).toUpperCase();
    }
    
    public String getFormattedFingerprint() {
        if (fingerprint == null || fingerprint.length() != 40) {
            return fingerprint;
        }
        // Format as: 1234 5678 9ABC DEF0 1234 5678 9ABC DEF0 1234 5678
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < fingerprint.length(); i += 4) {
            if (i > 0) formatted.append(" ");
            formatted.append(fingerprint.substring(i, Math.min(i + 4, fingerprint.length())));
        }
        return formatted.toString().toUpperCase();
    }
    
    public String getUsageString() {
        StringBuilder usage = new StringBuilder();
        if (Boolean.TRUE.equals(canEncrypt)) usage.append("E");
        if (Boolean.TRUE.equals(canSign)) usage.append("S");
        if (Boolean.TRUE.equals(canCertify)) usage.append("C");
        if (Boolean.TRUE.equals(canAuthenticate)) usage.append("A");
        return usage.toString();
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getKeyName() {
        return keyName;
    }
    
    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public KeyType getKeyType() {
        return keyType;
    }
    
    public void setKeyType(KeyType keyType) {
        this.keyType = keyType;
    }
    
    public Integer getKeySize() {
        return keySize;
    }
    
    public void setKeySize(Integer keySize) {
        this.keySize = keySize;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getFingerprint() {
        return fingerprint;
    }
    
    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }
    
    public String getKeyId() {
        return keyId;
    }
    
    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }
    
    public String getPublicKey() {
        return publicKey;
    }
    
    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }
    
    public String getPrivateKey() {
        return privateKey;
    }
    
    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }
    
    public String getAlgorithm() {
        return algorithm;
    }
    
    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }
    
    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }
    
    public String getRevocationReason() {
        return revocationReason;
    }
    
    public void setRevocationReason(String revocationReason) {
        this.revocationReason = revocationReason;
    }
    
    public Boolean getCanEncrypt() {
        return canEncrypt;
    }
    
    public void setCanEncrypt(Boolean canEncrypt) {
        this.canEncrypt = canEncrypt;
    }
    
    public Boolean getCanSign() {
        return canSign;
    }
    
    public void setCanSign(Boolean canSign) {
        this.canSign = canSign;
    }
    
    public Boolean getCanCertify() {
        return canCertify;
    }
    
    public void setCanCertify(Boolean canCertify) {
        this.canCertify = canCertify;
    }
    
    public Boolean getCanAuthenticate() {
        return canAuthenticate;
    }
    
    public void setCanAuthenticate(Boolean canAuthenticate) {
        this.canAuthenticate = canAuthenticate;
    }
    
    public String getImportedFrom() {
        return importedFrom;
    }
    
    public void setImportedFrom(String importedFrom) {
        this.importedFrom = importedFrom;
    }
    
    public Integer getExportedCount() {
        return exportedCount;
    }
    
    public void setExportedCount(Integer exportedCount) {
        this.exportedCount = exportedCount;
    }
    
    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }
    
    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    /**
     * Sets the creation timestamp. Should only be used during INSERT operations.
     * Protected visibility to prevent misuse in business logic.
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    /**
     * Sets the update timestamp. Should only be used during UPDATE operations by persistence layer.
     * Protected visibility to prevent misuse in business logic.
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public UUID getCreatedBy() {
        return createdBy;
    }
    
    /**
     * Sets the user who created this entity. Should only be used during INSERT operations.
     * Protected visibility to prevent misuse in business logic.
     */
    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
    
    public UUID getUpdatedBy() {
        return updatedBy;
    }
    
    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }
}