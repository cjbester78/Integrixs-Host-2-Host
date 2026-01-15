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
 * Utility class for encrypting and decrypting flow export files.
 * Uses AES-256-GCM encryption for security and integrity.
 * Only the H2H application can decrypt these files.
 */
@Component
public class FlowExportCrypto {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowExportCrypto.class);
    
    // AES-256-GCM configuration
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    
    // Application-specific encryption key (should be loaded from secure configuration)
    // In production, this should come from a secure key management system
    private static final String H2H_MASTER_KEY = "H2H_Flow_Export_Master_Key_2024_Secure!"; // 32 chars for AES-256
    
    private final ObjectMapper objectMapper;
    private final SecretKey masterKey;
    
    public FlowExportCrypto() {
        this.objectMapper = new ObjectMapper();
        // Create master key from the fixed string (for consistency across app instances)
        byte[] keyBytes = H2H_MASTER_KEY.getBytes(StandardCharsets.UTF_8);
        // Ensure exactly 32 bytes for AES-256
        byte[] key = new byte[32];
        System.arraycopy(keyBytes, 0, key, 0, Math.min(keyBytes.length, 32));
        this.masterKey = new SecretKeySpec(key, ALGORITHM);
        
        logger.info("FlowExportCrypto initialized with AES-256-GCM encryption");
    }
    
    /**
     * Encrypt flow export data to a secure format that only this application can read.
     * 
     * @param flowExportData The flow export data to encrypt
     * @return Encrypted data as Base64 string with metadata
     */
    public Map<String, Object> encryptFlowExport(Map<String, Object> flowExportData) {
        try {
            logger.info("Encrypting flow export data for secure storage");
            
            // Convert flow data to JSON
            String jsonData = objectMapper.writeValueAsString(flowExportData);
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
            encryptedExport.put("version", "H2H_ENCRYPTED_FLOW_V1");
            encryptedExport.put("algorithm", "AES-256-GCM");
            encryptedExport.put("iv", Base64.getEncoder().encodeToString(iv));
            encryptedExport.put("data", Base64.getEncoder().encodeToString(encryptedData));
            encryptedExport.put("encryptedAt", System.currentTimeMillis());
            encryptedExport.put("application", "Integrixs Host-2-Host");
            encryptedExport.put("type", "FLOW_EXPORT");

            // Add integrity check
            String flowName = (String) flowExportData.get("name");
            encryptedExport.put("flowNameHash", hashFlowName(flowName));
            
            logger.info("✓ Flow export data encrypted successfully");
            return encryptedExport;
            
        } catch (Exception e) {
            logger.error("Failed to encrypt flow export data: {}", e.getMessage(), e);
            throw new RuntimeException("Flow export encryption failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Decrypt flow export data that was encrypted by this application.
     * 
     * @param encryptedExport The encrypted export data
     * @return Decrypted flow export data
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> decryptFlowExport(Map<String, Object> encryptedExport) {
        try {
            logger.info("Decrypting flow export data");
            
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
            Map<String, Object> flowExportData = objectMapper.readValue(jsonData, Map.class);
            
            // Verify integrity if hash is present
            if (encryptedExport.containsKey("flowNameHash")) {
                String expectedHash = (String) encryptedExport.get("flowNameHash");
                String flowName = (String) flowExportData.get("name");
                if (!expectedHash.equals(hashFlowName(flowName))) {
                    throw new SecurityException("Flow integrity check failed - data may be corrupted");
                }
            }
            
            logger.info("✓ Flow export data decrypted successfully");
            return flowExportData;
            
        } catch (Exception e) {
            logger.error("Failed to decrypt flow export data: {}", e.getMessage(), e);
            throw new RuntimeException("Flow export decryption failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if the given data is an encrypted flow export.
     */
    public boolean isEncryptedFlowExport(Map<String, Object> data) {
        return data.containsKey("version") &&
               data.containsKey("algorithm") &&
               data.containsKey("iv") &&
               data.containsKey("data") &&
               data.containsKey("type") &&
               ("H2H_ENCRYPTED_FLOW_V1".equals(data.get("version")) || "H2H_ENCRYPTED_V1".equals(data.get("version"))) &&
               "FLOW_EXPORT".equals(data.get("type"));
    }
    
    /**
     * Validate that the encrypted export has the expected format.
     */
    private void validateEncryptedExportFormat(Map<String, Object> encryptedExport) {
        if (!isEncryptedFlowExport(encryptedExport)) {
            throw new IllegalArgumentException("Invalid encrypted flow export format");
        }

        String version = (String) encryptedExport.get("version");
        if (!"H2H_ENCRYPTED_FLOW_V1".equals(version) && !"H2H_ENCRYPTED_V1".equals(version)) {
            throw new IllegalArgumentException("Unsupported encrypted flow export version: " + version);
        }

        String algorithm = (String) encryptedExport.get("algorithm");
        if (!"AES-256-GCM".equals(algorithm)) {
            throw new IllegalArgumentException("Unsupported encryption algorithm: " + algorithm);
        }

        String type = (String) encryptedExport.get("type");
        if (!"FLOW_EXPORT".equals(type)) {
            logger.warn("Invalid export type for flow: {}", type);
            throw new IllegalArgumentException("Export type must be FLOW_EXPORT for flow imports");
        }

        String application = (String) encryptedExport.get("application");
        if (!"Integrixs Host-2-Host".equals(application)) {
            logger.warn("Flow export from different application: {}", application);
        }
    }
    
    /**
     * Create a simple hash of the flow name for integrity checking.
     */
    private String hashFlowName(String flowName) {
        if (flowName == null) return "";
        return Integer.toHexString(flowName.hashCode());
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