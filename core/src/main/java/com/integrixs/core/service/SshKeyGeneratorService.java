package com.integrixs.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Service for generating SSH key pairs
 */
@Service
public class SshKeyGeneratorService {
    
    private static final Logger log = LoggerFactory.getLogger(SshKeyGeneratorService.class);
    
    private static final int DEFAULT_KEY_SIZE = 2048;
    private static final BigInteger DEFAULT_PUBLIC_EXPONENT = RSAKeyGenParameterSpec.F4;
    
    /**
     * Generate RSA key pair for SSH authentication
     */
    public SshKeyPair generateRSAKeyPair(int keySize, String comment) {
        try {
            log.info("Generating RSA SSH key pair - size: {}, comment: {}", keySize, comment);
            
            // Generate RSA key pair
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            RSAKeyGenParameterSpec spec = new RSAKeyGenParameterSpec(keySize, DEFAULT_PUBLIC_EXPONENT);
            keyPairGenerator.initialize(spec, new SecureRandom());
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            
            // Convert to SSH format
            String privateKeyPEM = convertToPrivateKeyPEM(privateKey);
            String publicKeyOpenSSH = convertToPublicKeyOpenSSH(publicKey, comment);
            String fingerprint = generateFingerprint(publicKey);
            
            SshKeyPair sshKeyPair = new SshKeyPair(
                "RSA",
                keySize,
                privateKeyPEM,
                publicKeyOpenSSH,
                fingerprint,
                comment
            );
            
            log.info("SSH key pair generated successfully - fingerprint: {}", fingerprint);
            return sshKeyPair;
            
        } catch (Exception e) {
            log.error("Failed to generate SSH key pair: {}", e.getMessage(), e);
            throw new RuntimeException("SSH key generation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate RSA key pair with default size
     */
    public SshKeyPair generateRSAKeyPair(String comment) {
        return generateRSAKeyPair(DEFAULT_KEY_SIZE, comment);
    }
    
    /**
     * Generate DSA key pair for SSH authentication
     */
    public SshKeyPair generateDSAKeyPair(int keySize, String comment) {
        try {
            log.info("Generating DSA SSH key pair - size: {}, comment: {}", keySize, comment);
            
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("DSA");
            keyPairGenerator.initialize(keySize, new SecureRandom());
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            
            PrivateKey privateKey = keyPair.getPrivate();
            PublicKey publicKey = keyPair.getPublic();
            
            // Convert to SSH format
            String privateKeyPEM = convertToPrivateKeyPEM(privateKey);
            String publicKeyOpenSSH = convertToPublicKeyOpenSSH(publicKey, comment);
            String fingerprint = generateFingerprint(publicKey);
            
            SshKeyPair sshKeyPair = new SshKeyPair(
                "DSA",
                keySize,
                privateKeyPEM,
                publicKeyOpenSSH,
                fingerprint,
                comment
            );
            
            log.info("SSH key pair generated successfully - fingerprint: {}", fingerprint);
            return sshKeyPair;
            
        } catch (Exception e) {
            log.error("Failed to generate DSA SSH key pair: {}", e.getMessage(), e);
            throw new RuntimeException("DSA SSH key generation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert private key to PEM format
     */
    private String convertToPrivateKeyPEM(PrivateKey privateKey) throws Exception {
        byte[] encoded = privateKey.getEncoded();
        String base64 = Base64.getEncoder().encodeToString(encoded);
        
        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN PRIVATE KEY-----\n");
        
        // Split into 64-character lines
        for (int i = 0; i < base64.length(); i += 64) {
            int end = Math.min(i + 64, base64.length());
            pem.append(base64, i, end).append("\n");
        }
        
        pem.append("-----END PRIVATE KEY-----\n");
        return pem.toString();
    }
    
    /**
     * Convert public key to OpenSSH format
     */
    private String convertToPublicKeyOpenSSH(PublicKey publicKey, String comment) throws Exception {
        if (publicKey instanceof RSAPublicKey) {
            return convertRSAToOpenSSH((RSAPublicKey) publicKey, comment);
        } else {
            return convertGenericToOpenSSH(publicKey, comment);
        }
    }
    
    /**
     * Convert RSA public key to OpenSSH format
     */
    private String convertRSAToOpenSSH(RSAPublicKey publicKey, String comment) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        
        // Write algorithm name
        writeString(buffer, "ssh-rsa");
        
        // Write public exponent
        writeBigInteger(buffer, publicKey.getPublicExponent());
        
        // Write modulus
        writeBigInteger(buffer, publicKey.getModulus());
        
        String base64 = Base64.getEncoder().encodeToString(buffer.toByteArray());
        return "ssh-rsa " + base64 + (comment != null ? " " + comment : "");
    }
    
    /**
     * Convert generic public key to OpenSSH format
     */
    private String convertGenericToOpenSSH(PublicKey publicKey, String comment) throws Exception {
        byte[] encoded = publicKey.getEncoded();
        String base64 = Base64.getEncoder().encodeToString(encoded);
        String algorithm = publicKey.getAlgorithm().toLowerCase();
        return "ssh-" + algorithm + " " + base64 + (comment != null ? " " + comment : "");
    }
    
    /**
     * Generate SSH key fingerprint
     */
    private String generateFingerprint(PublicKey publicKey) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(publicKey.getEncoded());
        String base64 = Base64.getEncoder().encodeToString(digest);
        
        // Remove padding and format
        String fingerprint = base64.replaceAll("=+$", "");
        return "SHA256:" + fingerprint;
    }
    
    /**
     * Write string to buffer in SSH format
     */
    private void writeString(ByteArrayOutputStream buffer, String str) throws Exception {
        byte[] bytes = str.getBytes("UTF-8");
        writeInt(buffer, bytes.length);
        buffer.write(bytes);
    }
    
    /**
     * Write BigInteger to buffer in SSH format
     */
    private void writeBigInteger(ByteArrayOutputStream buffer, BigInteger bigInt) throws Exception {
        byte[] bytes = bigInt.toByteArray();
        writeInt(buffer, bytes.length);
        buffer.write(bytes);
    }
    
    /**
     * Write integer to buffer in SSH format (big-endian)
     */
    private void writeInt(ByteArrayOutputStream buffer, int value) {
        buffer.write((value >>> 24) & 0xFF);
        buffer.write((value >>> 16) & 0xFF);
        buffer.write((value >>> 8) & 0xFF);
        buffer.write(value & 0xFF);
    }
    
    /**
     * Validate SSH key format
     */
    public boolean validatePrivateKey(String privateKeyPEM) {
        try {
            if (privateKeyPEM == null || privateKeyPEM.trim().isEmpty()) {
                return false;
            }
            
            // Remove PEM headers and decode
            String keyContent = privateKeyPEM
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
            
            byte[] keyBytes = Base64.getDecoder().decode(keyContent);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            
            // Try different key factories
            try {
                KeyFactory.getInstance("RSA").generatePrivate(keySpec);
                return true;
            } catch (Exception e) {
                // Try DSA
                KeyFactory.getInstance("DSA").generatePrivate(keySpec);
                return true;
            }
            
        } catch (Exception e) {
            log.debug("Private key validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Validate SSH public key format
     */
    public boolean validatePublicKey(String publicKeyOpenSSH) {
        try {
            if (publicKeyOpenSSH == null || publicKeyOpenSSH.trim().isEmpty()) {
                return false;
            }
            
            String[] parts = publicKeyOpenSSH.trim().split("\\s+");
            if (parts.length < 2) {
                return false;
            }
            
            String algorithm = parts[0];
            String base64Key = parts[1];
            
            // Check algorithm
            if (!algorithm.startsWith("ssh-")) {
                return false;
            }
            
            // Decode and validate base64
            Base64.getDecoder().decode(base64Key);
            
            return true;
            
        } catch (Exception e) {
            log.debug("Public key validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Extract key algorithm from public key
     */
    public String getKeyAlgorithm(String publicKeyOpenSSH) {
        try {
            String[] parts = publicKeyOpenSSH.trim().split("\\s+");
            if (parts.length >= 1) {
                return parts[0].replace("ssh-", "").toUpperCase();
            }
        } catch (Exception e) {
            log.debug("Failed to extract key algorithm: {}", e.getMessage());
        }
        return "UNKNOWN";
    }
    
    /**
     * Extract comment from public key
     */
    public String getKeyComment(String publicKeyOpenSSH) {
        try {
            String[] parts = publicKeyOpenSSH.trim().split("\\s+", 3);
            if (parts.length >= 3) {
                return parts[2];
            }
        } catch (Exception e) {
            log.debug("Failed to extract key comment: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * SSH key pair holder
     */
    public static class SshKeyPair {
        private final String algorithm;
        private final int keySize;
        private final String privateKey;
        private final String publicKey;
        private final String fingerprint;
        private final String comment;
        
        public SshKeyPair(String algorithm, int keySize, String privateKey, 
                         String publicKey, String fingerprint, String comment) {
            this.algorithm = algorithm;
            this.keySize = keySize;
            this.privateKey = privateKey;
            this.publicKey = publicKey;
            this.fingerprint = fingerprint;
            this.comment = comment;
        }
        
        public String getAlgorithm() { return algorithm; }
        public int getKeySize() { return keySize; }
        public String getPrivateKey() { return privateKey; }
        public String getPublicKey() { return publicKey; }
        public String getFingerprint() { return fingerprint; }
        public String getComment() { return comment; }
        
        @Override
        public String toString() {
            return "SshKeyPair{algorithm='" + algorithm + "', keySize=" + keySize + 
                   ", fingerprint='" + fingerprint + "', comment='" + comment + "'}";
        }
    }
}