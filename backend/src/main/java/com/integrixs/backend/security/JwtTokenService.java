package com.integrixs.backend.security;

import com.integrixs.backend.dto.response.TokenResponse;
import com.integrixs.backend.model.User;
import com.integrixs.backend.service.JwtTokenManagementService;
import com.integrixs.backend.service.SystemConfigurationService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * JWT Token Service for authentication and authorization
 * Handles token generation, validation, and extraction of user information
 * 
 * @deprecated Use JwtTokenManagementService for new implementations
 * This class is maintained for backward compatibility and delegates to the new service
 */
@Service
public class JwtTokenService {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenService.class);
    
    private final SecretKey jwtSecret;
    private final SystemConfigurationService configService;
    private final JwtTokenManagementService tokenManagementService;

    public JwtTokenService(SystemConfigurationService configService, 
                          JwtTokenManagementService tokenManagementService) {
        this.configService = configService;
        this.tokenManagementService = tokenManagementService;
        
        // Get JWT secret from database configuration
        String secret = configService.getValue(
            "security.jwt.secret", 
            "H2HFileTransferSecretKeyForJWTTokenGeneration2024!"
        );
        
        this.jwtSecret = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        
        logger.info("JWT Token Service initialized with database configuration");
        logger.debug("JWT secret length: {} characters", secret.length());
    }

    /**
     * Get JWT access token expiration time in milliseconds
     */
    private long getJwtExpirationMs() {
        Integer hours = configService.getIntegerValue("security.jwt.access_token_expiry_hours", 24);
        return hours * 60L * 60L * 1000L; // Convert hours to milliseconds
    }

    /**
     * Get JWT refresh token expiration time in milliseconds
     */
    private long getRefreshTokenExpirationMs() {
        Integer days = configService.getIntegerValue("security.jwt.refresh_token_expiry_days", 7);
        return days * 24L * 60L * 60L * 1000L; // Convert days to milliseconds
    }

    /**
     * Generate JWT token for authenticated user
     */
    public String generateToken(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return generateToken(user);
    }

    /**
     * Generate JWT token for user
     */
    public String generateToken(User user) {
        Date expiryDate = new Date(System.currentTimeMillis() + getJwtExpirationMs());
        
        return Jwts.builder()
                .setSubject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("fullName", user.getFullName())
                .claim("role", user.getRole().name())
                .claim("enabled", user.isEnabled())
                .claim("tokenType", "ACCESS")
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .signWith(jwtSecret, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Generate refresh token for user
     */
    public String generateRefreshToken(User user) {
        Date expiryDate = new Date(System.currentTimeMillis() + getRefreshTokenExpirationMs());
        
        return Jwts.builder()
                .setSubject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("tokenType", "REFRESH")
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .signWith(jwtSecret, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Get user ID from JWT token
     */
    public String getUserIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(jwtSecret)
                .build()
                .parseClaimsJws(token)
                .getBody();
        
        return claims.getSubject();
    }

    /**
     * Get username from JWT token
     */
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(jwtSecret)
                .build()
                .parseClaimsJws(token)
                .getBody();
        
        return claims.get("username", String.class);
    }

    /**
     * Get user role from JWT token
     */
    public String getRoleFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(jwtSecret)
                .build()
                .parseClaimsJws(token)
                .getBody();
        
        return claims.get("role", String.class);
    }

    /**
     * Get token type (ACCESS or REFRESH)
     */
    public String getTokenTypeFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(jwtSecret)
                .build()
                .parseClaimsJws(token)
                .getBody();
        
        return claims.get("tokenType", String.class);
    }

    /**
     * Get expiration date from JWT token
     */
    public LocalDateTime getExpirationFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(jwtSecret)
                .build()
                .parseClaimsJws(token)
                .getBody();
        
        return claims.getExpiration().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    /**
     * Validate JWT token
     * @deprecated Use JwtTokenManagementService.validateToken() for detailed validation results
     */
    @Deprecated
    public boolean validateToken(String token) {
        return tokenManagementService.validateToken(token).isValid();
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(jwtSecret)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true; // If we can't parse it, consider it expired
        }
    }

    /**
     * Check if token is a refresh token
     */
    public boolean isRefreshToken(String token) {
        try {
            String tokenType = getTokenTypeFromToken(token);
            return "REFRESH".equals(tokenType);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if token is an access token
     */
    public boolean isAccessToken(String token) {
        try {
            String tokenType = getTokenTypeFromToken(token);
            return "ACCESS".equals(tokenType);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get all claims from token (for debugging/logging)
     */
    public Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(jwtSecret)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    
    /**
     * Get JWT token expiration time in milliseconds
     */
    public long getExpirationTime() {
        return getJwtExpirationMs();
    }
    
    /**
     * Get refresh token expiration time in milliseconds
     */
    public long getRefreshExpirationTime() {
        return getRefreshTokenExpirationMs();
    }

    /**
     * Get time until token expires in milliseconds
     */
    public long getTimeUntilExpiration(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            Date expiration = claims.getExpiration();
            return expiration.getTime() - System.currentTimeMillis();
        } catch (Exception e) {
            return 0; // Token is invalid or expired
        }
    }

    /**
     * Create token response with access and refresh tokens
     * @deprecated Use JwtTokenManagementService.generateTokenResponse() instead
     */
    @Deprecated
    public TokenResponse createTokenResponse(User user) {
        com.integrixs.backend.dto.response.TokenResponse newResponse = tokenManagementService.generateTokenResponse(user);
        
        // Convert new response to legacy format for backward compatibility
        return new TokenResponse(
            newResponse.accessToken(),
            newResponse.refreshToken(),
            newResponse.tokenType(),
            newResponse.expiresIn(),
            newResponse.username(),
            newResponse.fullName(),
            newResponse.role()
        );
    }

    /**
     * @deprecated Moved to com.integrixs.backend.dto.response.TokenResponse
     * This inner class remains for backward compatibility only
     */
    @Deprecated
    public static class TokenResponse {
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        private long expiresIn; // seconds
        private String username;
        private String fullName;
        private String role;

        public TokenResponse(String accessToken, String refreshToken, String tokenType, 
                           long expiresIn, String username, String fullName, String role) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.tokenType = tokenType;
            this.expiresIn = expiresIn;
            this.username = username;
            this.fullName = fullName;
            this.role = role;
        }

        // Getters and setters
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

        public String getTokenType() { return tokenType; }
        public void setTokenType(String tokenType) { this.tokenType = tokenType; }

        public long getExpiresIn() { return expiresIn; }
        public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }
}