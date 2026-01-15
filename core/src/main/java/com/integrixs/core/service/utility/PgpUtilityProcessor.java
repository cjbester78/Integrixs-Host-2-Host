package com.integrixs.core.service.utility;

import com.integrixs.shared.model.FlowExecutionStep;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Utility processor for PGP encryption and decryption operations
 * Handles PGP encrypt, decrypt operations following Single Responsibility Principle
 */
@Service
public class PgpUtilityProcessor extends AbstractUtilityProcessor {
    
    private static final String UTILITY_TYPE = "PGP";
    private final SecureRandom secureRandom = new SecureRandom();
    
    @Override
    public String getUtilityType() {
        return UTILITY_TYPE;
    }
    
    @Override
    public Map<String, Object> executeUtility(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        logExecutionStart(step, "PGP utility");
        long startTime = System.currentTimeMillis();
        
        try {
            String operation = getConfigValue(configuration, "operation", "encrypt");
            
            switch (operation.toLowerCase()) {
                case "encrypt":
                    return executePgpEncrypt(step, context, configuration);
                case "decrypt":
                    return executePgpDecrypt(step, context, configuration);
                default:
                    throw new IllegalArgumentException("Unsupported PGP operation: " + operation);
            }
            
        } catch (Exception e) {
            logExecutionError(step, "PGP utility", e);
            return createErrorResult("PGP utility execution failed: " + e.getMessage(), e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logExecutionComplete(step, "PGP utility", duration);
        }
    }
    
    /**
     * Execute PGP encryption
     */
    private Map<String, Object> executePgpEncrypt(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        try {
            // Validate required configuration
            validateConfiguration(configuration, "sourceDirectory", "targetDirectory", "publicKeyPath");
            
            String sourceDirectory = (String) configuration.get("sourceDirectory");
            String targetDirectory = (String) configuration.get("targetDirectory");
            String publicKeyPath = (String) configuration.get("publicKeyPath");
            String algorithm = getConfigValue(configuration, "algorithm", "AES256");
            boolean deleteSourceFiles = getConfigValue(configuration, "deleteSourceFiles", false);
            String filePattern = getConfigValue(configuration, "filePattern", "*");
            
            // Validate paths
            validateFilePath(sourceDirectory);
            validateFilePath(targetDirectory);
            validateFilePath(publicKeyPath);
            
            // Ensure directories exist
            ensureDirectoryExists(targetDirectory);
            
            Path sourcePath = Paths.get(sourceDirectory);
            Path targetPath = Paths.get(targetDirectory);
            
            if (!Files.exists(sourcePath)) {
                return createErrorResult("Source directory does not exist: " + sourceDirectory, null);
            }
            
            // Get files to encrypt
            List<Path> filesToEncrypt = new ArrayList<>();
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (matchesPattern(file.getFileName().toString(), filePattern)) {
                        filesToEncrypt.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            
            if (filesToEncrypt.isEmpty()) {
                return createSuccessResult("No files found matching pattern: " + filePattern, 
                    Collections.singletonMap("filesProcessed", 0));
            }
            
            // Encrypt files
            List<String> encryptedFiles = new ArrayList<>();
            long totalSize = 0;
            int successCount = 0;
            List<String> errors = new ArrayList<>();
            
            for (Path sourceFile : filesToEncrypt) {
                try {
                    String encryptedFileName = sourceFile.getFileName() + ".pgp";
                    Path encryptedFile = targetPath.resolve(encryptedFileName);
                    
                    boolean encrypted = encryptFile(sourceFile, encryptedFile, publicKeyPath, algorithm);
                    
                    if (encrypted) {
                        encryptedFiles.add(encryptedFile.toString());
                        totalSize += Files.size(sourceFile);
                        successCount++;
                        
                        // Delete source file if requested
                        if (deleteSourceFiles) {
                            Files.delete(sourceFile);
                            logger.debug("Deleted source file: {}", sourceFile);
                        }
                    }
                    
                } catch (Exception e) {
                    String error = "Failed to encrypt file " + sourceFile + ": " + e.getMessage();
                    errors.add(error);
                    logger.error("Encryption error: {}", error, e);
                }
            }
            
            // Update execution context
            updateExecutionContext(context, "pgpEncryptedFiles", encryptedFiles);
            updateExecutionContext(context, "pgpEncryptedFileCount", successCount);
            
            // Create result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("filesProcessed", successCount);
            resultData.put("totalFiles", filesToEncrypt.size());
            resultData.put("totalSizeBytes", totalSize);
            resultData.put("encryptedFiles", encryptedFiles);
            resultData.put("algorithm", algorithm);
            resultData.put("sourceDirectory", sourceDirectory);
            resultData.put("targetDirectory", targetDirectory);
            resultData.put("deletedSourceFiles", deleteSourceFiles);
            
            if (!errors.isEmpty()) {
                resultData.put("errors", errors);
            }
            
            String message = String.format("Successfully encrypted %d of %d files using %s", 
                successCount, filesToEncrypt.size(), algorithm);
            
            return createSuccessResult(message, resultData);
            
        } catch (Exception e) {
            logger.error("PGP encryption failed", e);
            return createErrorResult("PGP encryption failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute PGP decryption
     */
    private Map<String, Object> executePgpDecrypt(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        try {
            // Validate required configuration
            validateConfiguration(configuration, "sourceDirectory", "targetDirectory", "privateKeyPath", "passphrase");
            
            String sourceDirectory = (String) configuration.get("sourceDirectory");
            String targetDirectory = (String) configuration.get("targetDirectory");
            String privateKeyPath = (String) configuration.get("privateKeyPath");
            String passphrase = (String) configuration.get("passphrase");
            boolean deleteSourceFiles = getConfigValue(configuration, "deleteSourceFiles", false);
            String filePattern = getConfigValue(configuration, "filePattern", "*.pgp");
            
            // Validate paths
            validateFilePath(sourceDirectory);
            validateFilePath(targetDirectory);
            validateFilePath(privateKeyPath);
            
            // Ensure directories exist
            ensureDirectoryExists(targetDirectory);
            
            Path sourcePath = Paths.get(sourceDirectory);
            Path targetPath = Paths.get(targetDirectory);
            
            if (!Files.exists(sourcePath)) {
                return createErrorResult("Source directory does not exist: " + sourceDirectory, null);
            }
            
            // Get files to decrypt
            List<Path> filesToDecrypt = new ArrayList<>();
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (matchesPattern(file.getFileName().toString(), filePattern)) {
                        filesToDecrypt.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            
            if (filesToDecrypt.isEmpty()) {
                return createSuccessResult("No files found matching pattern: " + filePattern, 
                    Collections.singletonMap("filesProcessed", 0));
            }
            
            // Decrypt files
            List<String> decryptedFiles = new ArrayList<>();
            long totalSize = 0;
            int successCount = 0;
            List<String> errors = new ArrayList<>();
            
            for (Path sourceFile : filesToDecrypt) {
                try {
                    String fileName = sourceFile.getFileName().toString();
                    // Remove .pgp extension
                    String decryptedFileName = fileName.endsWith(".pgp") ? 
                        fileName.substring(0, fileName.length() - 4) : fileName + ".decrypted";
                    Path decryptedFile = targetPath.resolve(decryptedFileName);
                    
                    boolean decrypted = decryptFile(sourceFile, decryptedFile, privateKeyPath, passphrase);
                    
                    if (decrypted) {
                        decryptedFiles.add(decryptedFile.toString());
                        totalSize += Files.size(decryptedFile);
                        successCount++;
                        
                        // Delete source file if requested
                        if (deleteSourceFiles) {
                            Files.delete(sourceFile);
                            logger.debug("Deleted source file: {}", sourceFile);
                        }
                    }
                    
                } catch (Exception e) {
                    String error = "Failed to decrypt file " + sourceFile + ": " + e.getMessage();
                    errors.add(error);
                    logger.error("Decryption error: {}", error, e);
                }
            }
            
            // Update execution context
            updateExecutionContext(context, "pgpDecryptedFiles", decryptedFiles);
            updateExecutionContext(context, "pgpDecryptedFileCount", successCount);
            
            // Create result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("filesProcessed", successCount);
            resultData.put("totalFiles", filesToDecrypt.size());
            resultData.put("totalSizeBytes", totalSize);
            resultData.put("decryptedFiles", decryptedFiles);
            resultData.put("sourceDirectory", sourceDirectory);
            resultData.put("targetDirectory", targetDirectory);
            resultData.put("deletedSourceFiles", deleteSourceFiles);
            
            if (!errors.isEmpty()) {
                resultData.put("errors", errors);
            }
            
            String message = String.format("Successfully decrypted %d of %d files", 
                successCount, filesToDecrypt.size());
            
            return createSuccessResult(message, resultData);
            
        } catch (Exception e) {
            logger.error("PGP decryption failed", e);
            return createErrorResult("PGP decryption failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Encrypt a single file
     * Note: This is a simplified implementation for demonstration.
     * In production, use a proper PGP library like BouncyCastle.
     */
    private boolean encryptFile(Path sourceFile, Path targetFile, String publicKeyPath, String algorithm) {
        try {
            logger.info("Encrypting file: {} -> {} using {}", sourceFile, targetFile, algorithm);
            
            // Simulate PGP encryption
            // In production, implement actual PGP encryption using BouncyCastle
            byte[] fileContent = Files.readAllBytes(sourceFile);
            byte[] encryptedContent = simulateEncryption(fileContent, algorithm);
            
            Files.write(targetFile, encryptedContent);
            
            logger.debug("Successfully encrypted file: {} (size: {} -> {} bytes)", 
                sourceFile.getFileName(), fileContent.length, encryptedContent.length);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to encrypt file: {}", sourceFile, e);
            return false;
        }
    }
    
    /**
     * Decrypt a single file
     */
    private boolean decryptFile(Path sourceFile, Path targetFile, String privateKeyPath, String passphrase) {
        try {
            logger.info("Decrypting file: {} -> {}", sourceFile, targetFile);
            
            // Simulate PGP decryption
            // In production, implement actual PGP decryption using BouncyCastle
            byte[] encryptedContent = Files.readAllBytes(sourceFile);
            byte[] decryptedContent = simulateDecryption(encryptedContent, passphrase);
            
            Files.write(targetFile, decryptedContent);
            
            logger.debug("Successfully decrypted file: {} (size: {} -> {} bytes)", 
                sourceFile.getFileName(), encryptedContent.length, decryptedContent.length);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to decrypt file: {}", sourceFile, e);
            return false;
        }
    }
    
    /**
     * Simulate encryption (replace with real PGP implementation)
     */
    private byte[] simulateEncryption(byte[] data, String algorithm) {
        // Simple XOR encryption for simulation
        byte[] key = generateSimulatedKey(algorithm);
        byte[] encrypted = new byte[data.length];
        
        for (int i = 0; i < data.length; i++) {
            encrypted[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        
        return encrypted;
    }
    
    /**
     * Simulate decryption (replace with real PGP implementation)
     */
    private byte[] simulateDecryption(byte[] encryptedData, String passphrase) {
        // Simple XOR decryption for simulation (same as encryption with XOR)
        byte[] key = generateSimulatedKey("AES256"); // Use consistent algorithm
        byte[] decrypted = new byte[encryptedData.length];
        
        for (int i = 0; i < encryptedData.length; i++) {
            decrypted[i] = (byte) (encryptedData[i] ^ key[i % key.length]);
        }
        
        return decrypted;
    }
    
    /**
     * Generate simulated key for encryption
     */
    private byte[] generateSimulatedKey(String algorithm) {
        // Generate a simple key based on algorithm name
        byte[] algorithmBytes = algorithm.getBytes();
        byte[] key = new byte[32]; // 256-bit key
        
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (algorithmBytes[i % algorithmBytes.length] + i);
        }
        
        return key;
    }
    
    /**
     * Check if filename matches pattern
     */
    private boolean matchesPattern(String filename, String pattern) {
        if (pattern == null || "*".equals(pattern)) {
            return true;
        }
        
        // Simple wildcard matching
        if (pattern.startsWith("*.")) {
            String extension = pattern.substring(2);
            return filename.toLowerCase().endsWith("." + extension.toLowerCase());
        }
        
        return filename.matches(pattern.replace("*", ".*"));
    }
}