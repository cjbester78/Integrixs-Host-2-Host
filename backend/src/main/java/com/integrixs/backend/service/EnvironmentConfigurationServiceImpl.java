package com.integrixs.backend.service;

import com.integrixs.shared.enums.EnvironmentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of Environment Configuration Service
 * Provides comprehensive environment-specific configuration management with proper OOP patterns
 */
@Service
public class EnvironmentConfigurationServiceImpl implements EnvironmentConfigurationService {

    private static final Logger logger = LoggerFactory.getLogger(EnvironmentConfigurationServiceImpl.class);
    
    private final SystemConfigurationService systemConfigurationService;
    private final ApplicationEventPublisher eventPublisher;
    private final EnvironmentValidator environmentValidator;
    private final EnvironmentConfigurationCache configurationCache;
    
    // Configuration keys
    private static final String ENV_TYPE_KEY = "system.environment.type";
    private static final String ENV_ENFORCE_KEY = "system.environment.enforce_restrictions";
    private static final String ENV_MESSAGE_KEY = "system.environment.restriction_message";
    
    public EnvironmentConfigurationServiceImpl(SystemConfigurationService systemConfigurationService,
                                             ApplicationEventPublisher eventPublisher) {
        this.systemConfigurationService = systemConfigurationService;
        this.eventPublisher = eventPublisher;
        this.environmentValidator = new EnvironmentValidator();
        this.configurationCache = new EnvironmentConfigurationCache();
        
        // Initialize configuration
        loadAndCacheConfiguration();
    }

    @Override
    public EnvironmentConfiguration getCurrentEnvironment() {
        return configurationCache.getCurrentConfiguration();
    }

    @Override
    public EnvironmentUpdateResult updateEnvironment(EnvironmentUpdateRequest request) {
        try {
            EnvironmentConfiguration current = getCurrentEnvironment();
            
            // Validate the update request
            EnvironmentValidationResult validation = validateUpdate(request, current);
            if (validation.valid() == false) {
                return EnvironmentUpdateResult.failure(validation.validationMessage(), current);
            }
            
            // Apply updates
            EnvironmentConfiguration updated = applyUpdates(request, current);
            
            // Persist changes
            persistConfiguration(updated);
            
            // Update cache
            configurationCache.updateConfiguration(updated);
            
            // Publish environment change event
            publishEnvironmentChangeEvent(current, updated, request.userId());
            
            logger.info("Environment configuration updated successfully by user: {} - Type: {} -> {}", 
                request.userId(), current.type(), updated.type());
            
            return EnvironmentUpdateResult.success(current, updated);
            
        } catch (Exception e) {
            logger.error("Failed to update environment configuration", e);
            return EnvironmentUpdateResult.failure("Internal error: " + e.getMessage(), getCurrentEnvironment());
        }
    }

    @Override
    public EnvironmentValidationResult validateEnvironmentChange(EnvironmentType newType, String userId) {
        EnvironmentConfiguration current = getCurrentEnvironment();
        
        // Check if change is significant
        if (current.type() == newType) {
            return EnvironmentValidationResult.createValid();
        }
        
        return environmentValidator.validateChange(current.type(), newType, userId);
    }

    @Override
    public OperationPermissionResult checkOperationPermission(EnvironmentOperation operation) {
        EnvironmentConfiguration config = getCurrentEnvironment();
        EnvironmentRestrictions restrictions = getEnvironmentRestrictions();
        
        boolean permitted = restrictions.isOperationAllowed(operation);
        
        if (permitted) {
            return OperationPermissionResult.permitted(operation, config.type());
        } else {
            String reason = config.getFormattedRestrictionMessage();
            return OperationPermissionResult.denied(operation, config.type(), reason);
        }
    }

    @Override
    public <T> Optional<T> getEnvironmentSpecificValue(String key, Class<T> type) {
        try {
            EnvironmentConfiguration config = getCurrentEnvironment();
            String envKey = String.format("%s.%s", config.type().name().toLowerCase(), key);
            
            if (type == String.class) {
                String value = systemConfigurationService.getValue(envKey, null);
                return Optional.ofNullable(type.cast(value));
            } else if (type == Boolean.class) {
                Boolean value = systemConfigurationService.getBooleanValue(envKey, null);
                return Optional.ofNullable(type.cast(value));
            } else if (type == Integer.class) {
                Integer value = systemConfigurationService.getIntegerValue(envKey, null);
                return Optional.ofNullable(type.cast(value));
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            logger.debug("Could not get environment-specific value for key: {}", key, e);
            return Optional.empty();
        }
    }

    @Override
    public void reloadEnvironmentConfiguration() {
        logger.info("Reloading environment configuration");
        loadAndCacheConfiguration();
    }

    @Override
    public EnvironmentRestrictions getEnvironmentRestrictions() {
        EnvironmentConfiguration config = getCurrentEnvironment();
        return EnvironmentRestrictions.fromEnvironmentType(config.type(), config.enforceRestrictions());
    }

    // Private helper methods

    private void loadAndCacheConfiguration() {
        try {
            EnvironmentType type = loadEnvironmentType();
            boolean enforceRestrictions = loadEnforceRestrictions();
            String restrictionMessage = loadRestrictionMessage();
            
            EnvironmentConfiguration config = EnvironmentConfiguration.create(
                type, enforceRestrictions, restrictionMessage, "SYSTEM"
            );
            
            configurationCache.updateConfiguration(config);
            
            logger.debug("Environment configuration loaded: {} (restrictions: {})", type, enforceRestrictions);
            
        } catch (Exception e) {
            logger.error("Failed to load environment configuration, using defaults", e);
            
            // Fallback to default configuration
            EnvironmentConfiguration defaultConfig = EnvironmentConfiguration.create(
                EnvironmentType.DEVELOPMENT, true, 
                "This action is not allowed in %s environment", "SYSTEM"
            );
            configurationCache.updateConfiguration(defaultConfig);
        }
    }

    private EnvironmentType loadEnvironmentType() {
        String envTypeValue = systemConfigurationService.getValue(ENV_TYPE_KEY, "DEVELOPMENT");
        try {
            return EnvironmentType.valueOf(envTypeValue.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid environment type '{}', defaulting to DEVELOPMENT", envTypeValue);
            return EnvironmentType.DEVELOPMENT;
        }
    }

    private boolean loadEnforceRestrictions() {
        return systemConfigurationService.getBooleanValue(ENV_ENFORCE_KEY, true);
    }

    private String loadRestrictionMessage() {
        return systemConfigurationService.getValue(ENV_MESSAGE_KEY, 
            "This action is not allowed in %s environment");
    }

    private EnvironmentValidationResult validateUpdate(EnvironmentUpdateRequest request, 
                                                      EnvironmentConfiguration current) {
        
        // Validate environment type change if specified
        if (request.newType().isPresent()) {
            EnvironmentType newType = request.newType().get();
            EnvironmentValidationResult typeValidation = validateEnvironmentChange(newType, request.userId());
            if (typeValidation.valid() == false) {
                return typeValidation;
            }
        }
        
        // Additional validation logic can be added here
        
        return EnvironmentValidationResult.createValid();
    }

    private EnvironmentConfiguration applyUpdates(EnvironmentUpdateRequest request, 
                                                EnvironmentConfiguration current) {
        
        EnvironmentType newType = request.newType().orElse(current.type());
        boolean newEnforceRestrictions = request.newEnforceRestrictions().orElse(current.enforceRestrictions());
        String newMessage = request.newRestrictionMessage().orElse(current.restrictionMessage());
        
        return new EnvironmentConfiguration(
            newType,
            newType.getDisplayName(),
            newType.getDescription(),
            newEnforceRestrictions,
            newMessage,
            current.environmentProperties(),
            LocalDateTime.now(),
            request.userId()
        );
    }

    private void persistConfiguration(EnvironmentConfiguration config) {
        if (config.type() != null) {
            systemConfigurationService.updateConfigValue(ENV_TYPE_KEY, config.type().name());
        }
        
        systemConfigurationService.updateConfigValue(ENV_ENFORCE_KEY, String.valueOf(config.enforceRestrictions()));
        
        if (config.restrictionMessage() != null) {
            systemConfigurationService.updateConfigValue(ENV_MESSAGE_KEY, config.restrictionMessage());
        }
    }

    private void publishEnvironmentChangeEvent(EnvironmentConfiguration previous, 
                                              EnvironmentConfiguration updated, 
                                              String userId) {
        // In a real implementation, publish application event
        logger.info("Environment change event: {} -> {} by user: {}", 
            previous.type(), updated.type(), userId);
    }

    // Inner classes

    private static class EnvironmentValidator {
        
        public EnvironmentValidationResult validateChange(EnvironmentType from, EnvironmentType to, String userId) {
            
            // Production to non-production requires high security clearance
            if (from == EnvironmentType.PRODUCTION && to != EnvironmentType.PRODUCTION) {
                return EnvironmentValidationResult.invalid(
                    "Changing from production environment requires additional authorization",
                    SecurityImpact.CRITICAL,
                    OperationalImpact.HIGH
                );
            }
            
            // Non-production to production requires validation
            if (from != EnvironmentType.PRODUCTION && to == EnvironmentType.PRODUCTION) {
                return EnvironmentValidationResult.invalid(
                    "Changing to production environment requires additional validation",
                    SecurityImpact.HIGH,
                    OperationalImpact.CRITICAL
                );
            }
            
            return EnvironmentValidationResult.createValid();
        }
    }

    private static class EnvironmentConfigurationCache {
        private volatile EnvironmentConfiguration currentConfiguration;
        private final Map<String, Object> cache = new ConcurrentHashMap<>();
        
        public EnvironmentConfiguration getCurrentConfiguration() {
            return currentConfiguration;
        }
        
        public void updateConfiguration(EnvironmentConfiguration config) {
            this.currentConfiguration = config;
            cache.clear(); // Clear cache when configuration changes
        }
        
        public <T> void cacheValue(String key, T value) {
            cache.put(key, value);
        }
        
        @SuppressWarnings("unchecked")
        public <T> Optional<T> getCachedValue(String key, Class<T> type) {
            Object value = cache.get(key);
            if (value != null && type.isInstance(value)) {
                return Optional.of((T) value);
            }
            return Optional.empty();
        }
    }
}