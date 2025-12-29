package com.integrixs.core.service;

import com.integrixs.shared.model.PgpKey;
import com.integrixs.core.repository.PgpKeyRepository;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.Features;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.PBESecretKeyEncryptor;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.bc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for PGP key generation, import, export and management
 */
@Service
public class PgpKeyService {
    
    private static final Logger logger = LoggerFactory.getLogger(PgpKeyService.class);
    private static final SecureRandom secureRandom = new SecureRandom();
    
    private final PgpKeyRepository pgpKeyRepository;
    
    @Autowired
    public PgpKeyService(PgpKeyRepository pgpKeyRepository) {
        this.pgpKeyRepository = pgpKeyRepository;
    }
    
    /**
     * Generate a new PGP key pair
     */
    public PgpKey generateKeyPair(String keyName, String userId, String passphrase, 
                                  PgpKey.KeyType keyType, int keySize, 
                                  LocalDateTime expiresAt, UUID createdBy) {
        
        try {
            logger.info("Generating PGP key pair: {} for user: {}", keyName, userId);
            
            // Validate input
            validateKeyGeneration(keyName, userId, keySize);
            
            // Generate key pair based on type
            PGPKeyPair keyPair;
            switch (keyType) {
                case RSA:
                    keyPair = generateRSAKeyPair(keySize);
                    break;
                case ECC:
                    throw new UnsupportedOperationException("ECC key generation not yet implemented");
                case DSA:
                    throw new UnsupportedOperationException("DSA key generation not yet implemented");
                default:
                    throw new IllegalArgumentException("Unsupported key type: " + keyType);
            }
            
            // Create PGP key ring
            PGPKeyRingGenerator keyRingGenerator = createKeyRingGenerator(keyPair, userId, passphrase);
            
            // Generate key ring
            PGPSecretKeyRing secretKeyRing = keyRingGenerator.generateSecretKeyRing();
            PGPPublicKeyRing publicKeyRing = keyRingGenerator.generatePublicKeyRing();
            
            // Extract key information
            PGPPublicKey publicKey = publicKeyRing.getPublicKey();
            String fingerprint = bytesToHex(publicKey.getFingerprint());
            String keyId = Long.toHexString(publicKey.getKeyID()).substring(8).toUpperCase();
            
            // Create PgpKey entity
            PgpKey pgpKey = new PgpKey(keyName, userId, keyType, keySize);
            pgpKey.setFingerprint(fingerprint);
            pgpKey.setKeyId(keyId);
            pgpKey.setAlgorithm(getAlgorithmName(publicKey.getAlgorithm()));
            pgpKey.setExpiresAt(expiresAt);
            pgpKey.setCreatedBy(createdBy);
            pgpKey.setUpdatedBy(createdBy);
            
            // Export keys as ASCII armored strings
            pgpKey.setPublicKey(exportPublicKeyArmored(publicKeyRing));
            pgpKey.setPrivateKey(exportSecretKeyArmored(secretKeyRing));
            
            // Save to database
            PgpKey savedKey = pgpKeyRepository.save(pgpKey);
            
            logger.info("Generated PGP key pair successfully: {} ({})", keyName, fingerprint);
            return savedKey;
            
        } catch (Exception e) {
            logger.error("Failed to generate PGP key pair '{}': {}", keyName, e.getMessage(), e);
            throw new RuntimeException("Failed to generate PGP key pair: " + e.getMessage(), e);
        }
    }
    
    /**
     * Import PGP key from ASCII armored text
     */
    public PgpKey importKey(String keyName, String armoredKey, String description, UUID importedBy) {
        try {
            logger.info("Importing PGP key: {}", keyName);
            
            // Parse armored key
            PGPObjectFactory objectFactory = new PGPObjectFactory(
                PGPUtil.getDecoderStream(new ByteArrayInputStream(armoredKey.getBytes())),
                new BcKeyFingerprintCalculator()
            );
            
            Object obj = objectFactory.nextObject();
            PGPPublicKey publicKey = null;
            boolean hasPrivateKey = false;
            
            if (obj instanceof PGPPublicKeyRing) {
                PGPPublicKeyRing publicKeyRing = (PGPPublicKeyRing) obj;
                publicKey = publicKeyRing.getPublicKey();
            } else if (obj instanceof PGPSecretKeyRing) {
                PGPSecretKeyRing secretKeyRing = (PGPSecretKeyRing) obj;
                publicKey = secretKeyRing.getPublicKey();
                hasPrivateKey = true;
            } else {
                throw new IllegalArgumentException("Invalid PGP key format");
            }
            
            // Extract key information
            String fingerprint = bytesToHex(publicKey.getFingerprint());
            String keyId = Long.toHexString(publicKey.getKeyID()).substring(8).toUpperCase();
            
            // Check if key already exists
            Optional<PgpKey> existingKey = pgpKeyRepository.findByFingerprint(fingerprint);
            if (existingKey.isPresent()) {
                throw new IllegalArgumentException("Key with this fingerprint already exists: " + fingerprint);
            }
            
            // Get user ID from key
            String userId = "Unknown";
            Iterator<String> userIdIterator = publicKey.getUserIDs();
            if (userIdIterator.hasNext()) {
                userId = userIdIterator.next();
            }
            
            // Determine key type and size
            PgpKey.KeyType keyType = getKeyType(publicKey.getAlgorithm());
            int keySize = publicKey.getBitStrength();
            
            // Create PgpKey entity
            PgpKey pgpKey = new PgpKey(keyName, userId, keyType, keySize);
            pgpKey.setDescription(description);
            pgpKey.setFingerprint(fingerprint);
            pgpKey.setKeyId(keyId);
            pgpKey.setAlgorithm(getAlgorithmName(publicKey.getAlgorithm()));
            pgpKey.setImportedFrom("Manual Import");
            pgpKey.setCreatedBy(importedBy);
            pgpKey.setUpdatedBy(importedBy);
            
            // Set usage flags based on key capabilities
            setKeyUsageFlags(pgpKey, publicKey);
            
            // Store key material
            pgpKey.setPublicKey(armoredKey);
            if (hasPrivateKey) {
                pgpKey.setPrivateKey(armoredKey);
            }
            
            // Save to database
            PgpKey savedKey = pgpKeyRepository.save(pgpKey);
            
            logger.info("Imported PGP key successfully: {} ({})", keyName, fingerprint);
            return savedKey;
            
        } catch (Exception e) {
            logger.error("Failed to import PGP key '{}': {}", keyName, e.getMessage(), e);
            throw new RuntimeException("Failed to import PGP key: " + e.getMessage(), e);
        }
    }
    
    /**
     * Export public key as ASCII armored string
     */
    public String exportPublicKey(UUID keyId) {
        try {
            Optional<PgpKey> pgpKeyOpt = pgpKeyRepository.findById(keyId);
            if (pgpKeyOpt.isEmpty()) {
                throw new IllegalArgumentException("PGP key not found: " + keyId);
            }
            
            PgpKey pgpKey = pgpKeyOpt.get();
            
            // Update export count
            pgpKeyRepository.updateExportCount(keyId);
            
            logger.info("Exported public key: {}", pgpKey.getKeyName());
            return pgpKey.getPublicKey();
            
        } catch (Exception e) {
            logger.error("Failed to export public key '{}': {}", keyId, e.getMessage());
            throw new RuntimeException("Failed to export public key: " + e.getMessage(), e);
        }
    }
    
    /**
     * Export private key as ASCII armored string (admin only)
     */
    public String exportPrivateKey(UUID keyId) {
        try {
            Optional<PgpKey> pgpKeyOpt = pgpKeyRepository.findById(keyId);
            if (pgpKeyOpt.isEmpty()) {
                throw new IllegalArgumentException("PGP key not found: " + keyId);
            }
            
            PgpKey pgpKey = pgpKeyOpt.get();
            if (pgpKey.getPrivateKey() == null) {
                throw new IllegalArgumentException("Private key not available for this key");
            }
            
            // Update export count
            pgpKeyRepository.updateExportCount(keyId);
            
            logger.info("Exported private key: {}", pgpKey.getKeyName());
            return pgpKey.getPrivateKey();
            
        } catch (Exception e) {
            logger.error("Failed to export private key '{}': {}", keyId, e.getMessage());
            throw new RuntimeException("Failed to export private key: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get all PGP keys
     */
    public List<PgpKey> getAllKeys() {
        return pgpKeyRepository.findAll();
    }
    
    /**
     * Get PGP key by ID
     */
    public Optional<PgpKey> getKeyById(UUID id) {
        return pgpKeyRepository.findById(id);
    }
    
    /**
     * Delete PGP key
     */
    public void deleteKey(UUID id) {
        pgpKeyRepository.delete(id);
    }
    
    /**
     * Revoke PGP key
     */
    public void revokeKey(UUID id, String reason, UUID revokedBy) {
        pgpKeyRepository.revoke(id, reason, revokedBy);
    }
    
    /**
     * Find expired keys
     */
    public List<PgpKey> getExpiredKeys() {
        return pgpKeyRepository.findExpiredKeys();
    }
    
    // Private helper methods
    
    private void validateKeyGeneration(String keyName, String userId, int keySize) {
        if (keyName == null || keyName.trim().isEmpty()) {
            throw new IllegalArgumentException("Key name is required");
        }
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (keySize < 1024 || keySize > 4096) {
            throw new IllegalArgumentException("Key size must be between 1024 and 4096 bits");
        }
        
        // Check if key name already exists
        Optional<PgpKey> existingKey = pgpKeyRepository.findByKeyName(keyName);
        if (existingKey.isPresent()) {
            throw new IllegalArgumentException("Key name already exists: " + keyName);
        }
    }
    
    private PGPKeyPair generateRSAKeyPair(int keySize) throws Exception {
        RSAKeyPairGenerator generator = new RSAKeyPairGenerator();
        generator.init(new RSAKeyGenerationParameters(
            BigInteger.valueOf(65537), 
            secureRandom, 
            keySize, 
            80
        ));
        
        return new BcPGPKeyPair(PGPPublicKey.RSA_GENERAL, generator.generateKeyPair(), new Date());
    }
    
    private PGPKeyRingGenerator createKeyRingGenerator(PGPKeyPair keyPair, String userId, String passphrase) throws Exception {
        // Create digest calculator
        PGPDigestCalculator sha1Calc = new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1);
        PGPDigestCalculator sha256Calc = new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA256);
        
        // Create secret key encryptor
        PBESecretKeyEncryptor secretKeyEncryptor = passphrase != null && !passphrase.isEmpty() 
            ? new BcPBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, sha256Calc)
                .build(passphrase.toCharArray())
            : null;
        
        // Create key ring generator
        PGPKeyRingGenerator keyRingGenerator = new PGPKeyRingGenerator(
            PGPSignature.POSITIVE_CERTIFICATION,
            keyPair,
            userId,
            sha1Calc,
            null, // No signature subpackets
            null, // No hashed subpackets
            new BcPGPContentSignerBuilder(keyPair.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256),
            secretKeyEncryptor
        );
        
        return keyRingGenerator;
    }
    
    private String exportPublicKeyArmored(PGPPublicKeyRing publicKeyRing) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ArmoredOutputStream armoredOutput = new ArmoredOutputStream(output)) {
            publicKeyRing.encode(armoredOutput);
        }
        return output.toString();
    }
    
    private String exportSecretKeyArmored(PGPSecretKeyRing secretKeyRing) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ArmoredOutputStream armoredOutput = new ArmoredOutputStream(output)) {
            secretKeyRing.encode(armoredOutput);
        }
        return output.toString();
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02X", b));
        }
        return hex.toString();
    }
    
    private String getAlgorithmName(int algorithm) {
        switch (algorithm) {
            case PGPPublicKey.RSA_GENERAL:
                return "RSA";
            case PGPPublicKey.DSA:
                return "DSA";
            case PGPPublicKey.ECDSA:
                return "ECDSA";
            case PGPPublicKey.ECDH:
                return "ECDH";
            default:
                return "Algorithm " + algorithm;
        }
    }
    
    private PgpKey.KeyType getKeyType(int algorithm) {
        switch (algorithm) {
            case PGPPublicKey.RSA_GENERAL:
                return PgpKey.KeyType.RSA;
            case PGPPublicKey.DSA:
                return PgpKey.KeyType.DSA;
            case PGPPublicKey.ECDSA:
            case PGPPublicKey.ECDH:
                return PgpKey.KeyType.ECC;
            default:
                return PgpKey.KeyType.RSA;
        }
    }
    
    private void setKeyUsageFlags(PgpKey pgpKey, PGPPublicKey publicKey) {
        // Default usage flags
        pgpKey.setCanEncrypt(true);
        pgpKey.setCanSign(true);
        pgpKey.setCanCertify(false);
        pgpKey.setCanAuthenticate(false);
        
        // TODO: Parse actual key usage flags from PGP key
        // This would require examining the key's signature subpackets
    }
}