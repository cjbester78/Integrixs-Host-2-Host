package com.integrixs.backend.service;

import com.integrixs.backend.dto.response.TokenResponse;
import com.integrixs.backend.model.User;
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
import java.util.Optional;

/**
 * Implementation of JWT Token Management Service
 * Provides comprehensive token operations with proper OOP design
 */
@Service
public class JwtTokenManagementServiceImpl implements JwtTokenManagementService {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenManagementServiceImpl.class);
    
    private final SecretKey jwtSecret;
    private final SystemConfigurationService configService;
    private final TokenConfigurationManager tokenConfig;

    public JwtTokenManagementServiceImpl(SystemConfigurationService configService) {
        this.configService = configService;
        this.tokenConfig = new TokenConfigurationManager(configService);
        
        // Initialize JWT secret from configuration
        String secret = configService.getValue(
            "security.jwt.secret", 
            "H2HFileTransferSecretKeyForJWTTokenGeneration2024!"
        );
        
        this.jwtSecret = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        
        logger.info("JWT Token Management Service initialized with database configuration");
        logger.debug("JWT secret length: {} characters", secret.length());
    }

    @Override
    public TokenResponse generateTokenResponse(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null for token generation");
        }
        
        String accessToken = generateAccessToken(user);
        String refreshToken = generateRefreshToken(user);
        
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(tokenConfig.getAccessTokenExpirationSeconds())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusSeconds(tokenConfig.getAccessTokenExpirationSeconds()))
                .build();
    }

    @Override
    public TokenResponse generateTokenResponse(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalArgumentException("Authentication cannot be null for token generation");
        }
        
        User user = (User) authentication.getPrincipal();
        return generateTokenResponse(user);
    }

    @Override
    public TokenValidationResult validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return TokenValidationResult.invalid("Token is null or empty");
        }
        
        try {
            Claims claims = parseTokenClaims(token);
            
            String userId = claims.getSubject();
            String username = claims.get("username", String.class);
            String role = claims.get("role", String.class);
            
            if (userId == null || username == null) {
                return TokenValidationResult.invalid("Token missing required claims");
            }
            
            return TokenValidationResult.valid(userId, username, role);
            
        } catch (SecurityException ex) {
            logger.error("Invalid JWT signature: {}", ex.getMessage());
            return TokenValidationResult.invalid("Invalid token signature");
        } catch (MalformedJwtException ex) {
            logger.error("Invalid JWT token: {}", ex.getMessage());
            return TokenValidationResult.invalid("Malformed token");
        } catch (ExpiredJwtException ex) {
            logger.warn("Expired JWT token: {}", ex.getMessage());
            return TokenValidationResult.invalid("Token expired");
        } catch (UnsupportedJwtException ex) {
            logger.error("Unsupported JWT token: {}", ex.getMessage());
            return TokenValidationResult.invalid("Unsupported token");
        } catch (IllegalArgumentException ex) {
            logger.error("JWT claims string is empty: {}", ex.getMessage());
            return TokenValidationResult.invalid("Empty token claims");
        } catch (Exception ex) {
            logger.error("Unexpected error validating token: {}", ex.getMessage());
            return TokenValidationResult.invalid("Token validation failed");
        }
    }

    @Override
    public Optional<TokenUserInfo> extractUserInfo(String token) {
        try {
            Claims claims = parseTokenClaims(token);
            
            String userId = claims.getSubject();
            String username = claims.get("username", String.class);
            String email = claims.get("email", String.class);
            String fullName = claims.get("fullName", String.class);
            String role = claims.get("role", String.class);
            Boolean enabled = claims.get("enabled", Boolean.class);
            String tokenTypeStr = claims.get("tokenType", String.class);
            
            TokenType tokenType = TokenType.ACCESS;
            if ("REFRESH".equals(tokenTypeStr)) {
                tokenType = TokenType.REFRESH;
            }
            
            TokenUserInfo userInfo = TokenUserInfo.from(
                userId, username, email, fullName, role,
                enabled != null ? enabled : false, tokenType
            );
            
            return Optional.of(userInfo);
            
        } catch (Exception e) {
            logger.debug("Could not extract user info from token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<TokenResponse> refreshAccessToken(String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return Optional.empty();
        }
        
        try {
            // Validate refresh token
            TokenValidationResult validation = validateToken(refreshToken);
            if (!validation.isValid()) {
                logger.debug("Refresh token validation failed: {}", validation.getFailureReason());
                return Optional.empty();
            }
            
            // Check if it's actually a refresh token
            if (!isTokenOfType(refreshToken, TokenType.REFRESH)) {
                logger.warn("Token is not a refresh token");
                return Optional.empty();
            }
            
            // Extract user info and generate new tokens
            Optional<TokenUserInfo> userInfo = extractUserInfo(refreshToken);
            if (userInfo.isEmpty()) {
                return Optional.empty();
            }
            
            // Create a minimal User object for token generation
            // In a real implementation, you might want to fetch the full user from database
            User user = createUserFromTokenInfo(userInfo.get());
            return Optional.of(generateTokenResponse(user));
            
        } catch (Exception e) {
            logger.error("Error refreshing access token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public TokenExpirationInfo getTokenExpirationInfo(String token) {
        try {
            Claims claims = parseTokenClaims(token);
            LocalDateTime expiresAt = claims.getExpiration().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDateTime();
            
            return TokenExpirationInfo.create(expiresAt, tokenConfig.getNearExpiryThresholdMinutes());
            
        } catch (Exception e) {
            // Return expired info for invalid tokens
            LocalDateTime now = LocalDateTime.now();
            return TokenExpirationInfo.create(now.minusMinutes(1), tokenConfig.getNearExpiryThresholdMinutes());
        }
    }

    @Override
    public boolean isTokenNearExpiry(String token, int minutesThreshold) {
        TokenExpirationInfo expInfo = getTokenExpirationInfo(token);
        return expInfo.nearExpiry() || expInfo.minutesUntilExpiry() <= minutesThreshold;
    }

    @Override
    public boolean isTokenOfType(String token, TokenType tokenType) {
        Optional<TokenUserInfo> userInfo = extractUserInfo(token);
        return userInfo.map(info -> info.tokenType() == tokenType).orElse(false);
    }

    @Override
    public long getTimeUntilExpiration(String token) {
        TokenExpirationInfo expInfo = getTokenExpirationInfo(token);
        return expInfo.getRemainingMillis();
    }

    // Private helper methods

    private String generateAccessToken(User user) {
        Date expiryDate = new Date(System.currentTimeMillis() + tokenConfig.getAccessTokenExpirationMillis());
        
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

    private String generateRefreshToken(User user) {
        Date expiryDate = new Date(System.currentTimeMillis() + tokenConfig.getRefreshTokenExpirationMillis());
        
        return Jwts.builder()
                .setSubject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("tokenType", "REFRESH")
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .signWith(jwtSecret, SignatureAlgorithm.HS512)
                .compact();
    }

    private Claims parseTokenClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(jwtSecret)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private User createUserFromTokenInfo(TokenUserInfo info) {
        // This is a simplified approach - in production you might want to fetch from DB
        User user = new User();
        user.setId(java.util.UUID.fromString(info.userId()));
        user.setUsername(info.username());
        user.setEmail(info.email());
        user.setFullName(info.fullName());
        user.setEnabled(info.enabled());
        // Note: Role setting would need to be handled based on your User model
        return user;
    }

    /**
     * Token configuration manager for centralized configuration handling
     */
    private static class TokenConfigurationManager {
        private final SystemConfigurationService configService;
        
        public TokenConfigurationManager(SystemConfigurationService configService) {
            this.configService = configService;
        }
        
        public long getAccessTokenExpirationMillis() {
            Integer hours = configService.getIntegerValue("security.jwt.access_token_expiry_hours", 24);
            return hours * 60L * 60L * 1000L;
        }
        
        public long getRefreshTokenExpirationMillis() {
            Integer days = configService.getIntegerValue("security.jwt.refresh_token_expiry_days", 7);
            return days * 24L * 60L * 60L * 1000L;
        }
        
        public long getAccessTokenExpirationSeconds() {
            return getAccessTokenExpirationMillis() / 1000L;
        }
        
        public int getNearExpiryThresholdMinutes() {
            return configService.getIntegerValue("security.jwt.near_expiry_threshold_minutes", 30);
        }
    }
}