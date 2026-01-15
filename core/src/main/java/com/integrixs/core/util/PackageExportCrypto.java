package com.integrixs.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for encrypting and decrypting package export files.
 * Uses AES-256-GCM encryption for security and integrity.
 * Only the H2H application can decrypt these files.
 */
@Component
public class PackageExportCrypto {

    private static final Logger logger = LoggerFactory.getLogger(PackageExportCrypto.class);

    // AES-256-GCM configuration
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;

    // Application-specific encryption key (should be loaded from secure configuration)
    // In production, this should come from a secure key management system
    private static final String H2H_MASTER_KEY = "H2H_Package_Export_Master_Key_2024!"; // 32 chars for AES-256

    private final ObjectMapper objectMapper;
    private final SecretKey masterKey;

    public PackageExportCrypto() {
        this.objectMapper = new ObjectMapper();
        // Create master key from the fixed string (for consistency across app instances)
        byte[] keyBytes = H2H_MASTER_KEY.getBytes(StandardCharsets.UTF_8);
        // Ensure exactly 32 bytes for AES-256
        byte[] key = new byte[32];
        System.arraycopy(keyBytes, 0, key, 0, Math.min(keyBytes.length, 32));
        this.masterKey = new SecretKeySpec(key, ALGORITHM);

        logger.info("PackageExportCrypto initialized with AES-256-GCM encryption");
    }

    /**
     * Encrypt package export data to a secure format that only this application can read.
     *
     * @param packageExportData The package export data to encrypt
     * @return Encrypted data as Base64 string with metadata
     */
    public Map<String, Object> encryptPackageExport(Map<String, Object> packageExportData) {
        try {
            logger.info("Encrypting package export data for secure storage");

            // Convert package data to JSON
            String jsonData = objectMapper.writeValueAsString(packageExportData);
            byte[] plaintext = jsonData.getBytes(StandardCharsets.UTF_8);

            // Generate random IV for this encryption
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, parameterSpec);

            // Encrypt the data
            byte[] encryptedData = cipher.doFinal(plaintext);

            // Create encrypted export package
            Map<String, Object> encryptedExport = new HashMap<>();
            encryptedExport.put("version", "H2H_ENCRYPTED_PKG_V1");
            encryptedExport.put("algorithm", "AES-256-GCM");
            encryptedExport.put("iv", Base64.getEncoder().encodeToString(iv));
            encryptedExport.put("data", Base64.getEncoder().encodeToString(encryptedData));
            encryptedExport.put("encryptedAt", System.currentTimeMillis());
            encryptedExport.put("application", "Integrixs Host-2-Host");
            encryptedExport.put("type", "PACKAGE_EXPORT");

            // Add integrity check
            Map<String, Object> packageInfo = (Map<String, Object>) packageExportData.get("package");
            if (packageInfo != null) {
                String packageName = (String) packageInfo.get("name");
                encryptedExport.put("packageNameHash", hashPackageName(packageName));
            }

            logger.info("✓ Package export data encrypted successfully");
            return encryptedExport;

        } catch (Exception e) {
            logger.error("Failed to encrypt package export data: {}", e.getMessage(), e);
            throw new RuntimeException("Package export encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypt package export data that was encrypted by this application.
     *
     * @param encryptedExport The encrypted export data
     * @return Decrypted package export data
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> decryptPackageExport(Map<String, Object> encryptedExport) {
        try {
            logger.info("Decrypting package export data");

            // Validate encrypted export format
            validateEncryptedExportFormat(encryptedExport);

            // Extract encryption components
            String ivBase64 = (String) encryptedExport.get("iv");
            String dataBase64 = (String) encryptedExport.get("data");

            byte[] iv = Base64.getDecoder().decode(ivBase64);
            byte[] encryptedData = Base64.getDecoder().decode(dataBase64);

            // Initialize cipher for decryption
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, parameterSpec);

            // Decrypt the data
            byte[] decryptedData = cipher.doFinal(encryptedData);
            String jsonData = new String(decryptedData, StandardCharsets.UTF_8);

            // Parse back to Map
            Map<String, Object> packageExportData = objectMapper.readValue(jsonData, Map.class);

            // Verify integrity if hash is present
            if (encryptedExport.containsKey("packageNameHash")) {
                String expectedHash = (String) encryptedExport.get("packageNameHash");
                Map<String, Object> packageInfo = (Map<String, Object>) packageExportData.get("package");
                if (packageInfo != null) {
                    String packageName = (String) packageInfo.get("name");
                    if (!expectedHash.equals(hashPackageName(packageName))) {
                        throw new SecurityException("Package integrity check failed - data may be corrupted");
                    }
                }
            }

            logger.info("✓ Package export data decrypted successfully");
            return packageExportData;

        } catch (Exception e) {
            logger.error("Failed to decrypt package export data: {}", e.getMessage(), e);
            throw new RuntimeException("Package export decryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Check if the given data is an encrypted package export.
     */
    public boolean isEncryptedPackageExport(Map<String, Object> data) {
        return data.containsKey("version") &&
               data.containsKey("algorithm") &&
               data.containsKey("iv") &&
               data.containsKey("data") &&
               data.containsKey("type") &&
               "H2H_ENCRYPTED_PKG_V1".equals(data.get("version")) &&
               "PACKAGE_EXPORT".equals(data.get("type"));
    }

    /**
     * Validate that the encrypted export has the expected format.
     */
    private void validateEncryptedExportFormat(Map<String, Object> encryptedExport) {
        if (!isEncryptedPackageExport(encryptedExport)) {
            throw new IllegalArgumentException("Invalid encrypted package export format");
        }

        String version = (String) encryptedExport.get("version");
        if (!"H2H_ENCRYPTED_PKG_V1".equals(version)) {
            throw new IllegalArgumentException("Unsupported encrypted package export version: " + version);
        }

        String algorithm = (String) encryptedExport.get("algorithm");
        if (!"AES-256-GCM".equals(algorithm)) {
            throw new IllegalArgumentException("Unsupported encryption algorithm: " + algorithm);
        }

        String application = (String) encryptedExport.get("application");
        if (!"Integrixs Host-2-Host".equals(application)) {
            logger.warn("Package export from different application: {}", application);
        }
    }

    /**
     * Create a simple hash of the package name for integrity checking.
     */
    private String hashPackageName(String packageName) {
        if (packageName == null) return "";
        return Integer.toHexString(packageName.hashCode());
    }

    /**
     * Generate a new random encryption key (for key rotation scenarios).
     */
    public static SecretKey generateNewKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_LENGTH);
            return keyGenerator.generateKey();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate new encryption key", e);
        }
    }
}
