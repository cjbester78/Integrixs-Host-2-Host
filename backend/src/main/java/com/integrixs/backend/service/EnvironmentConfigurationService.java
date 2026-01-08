package com.integrixs.backend.service;

import com.integrixs.shared.enums.EnvironmentType;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Environment Configuration Service Interface
 * Provides abstraction for environment-specific configuration management
 */
public interface EnvironmentConfigurationService {
    
    /**
     * Get current environment configuration
     */
    EnvironmentConfiguration getCurrentEnvironment();
    
    /**
     * Update environment configuration
     */
    EnvironmentUpdateResult updateEnvironment(EnvironmentUpdateRequest request);
    
    /**
     * Validate environment configuration change
     */
    EnvironmentValidationResult validateEnvironmentChange(EnvironmentType newType, String userId);
    
    /**
     * Check if operation is allowed in current environment
     */
    OperationPermissionResult checkOperationPermission(EnvironmentOperation operation);
    
    /**
     * Get environment-specific configuration values
     */
    <T> Optional<T> getEnvironmentSpecificValue(String key, Class<T> type);
    
    /**
     * Reload environment configuration from source
     */
    void reloadEnvironmentConfiguration();
    
    /**
     * Get environment restrictions for current environment
     */
    EnvironmentRestrictions getEnvironmentRestrictions();
    
    /**
     * Immutable environment configuration
     */
    record EnvironmentConfiguration(
        EnvironmentType type,
        String displayName,
        String description,
        boolean enforceRestrictions,
        String restrictionMessage,
        Map<String, Object> environmentProperties,
        LocalDateTime lastUpdated,
        String updatedBy
    ) {
        
        public static EnvironmentConfiguration create(EnvironmentType type, boolean enforceRestrictions, 
                                                    String restrictionMessage, String updatedBy) {
            return new EnvironmentConfiguration(
                type, 
                type.getDisplayName(),
                type.getDescription(),
                enforceRestrictions,
                restrictionMessage,
                Map.of(),
                LocalDateTime.now(),
                updatedBy
            );
        }
        
        public String getFormattedRestrictionMessage() {
            return String.format(restrictionMessage, displayName);
        }
        
        public boolean isProductionEnvironment() {
            return type == EnvironmentType.PRODUCTION;
        }
        
        public boolean isDevelopmentEnvironment() {
            return type == EnvironmentType.DEVELOPMENT;
        }
        
        public boolean isQualityAssuranceEnvironment() {
            return type == EnvironmentType.QUALITY_ASSURANCE;
        }
    }
    
    /**
     * Environment update request
     */
    record EnvironmentUpdateRequest(
        Optional<EnvironmentType> newType,
        Optional<Boolean> newEnforceRestrictions,
        Optional<String> newRestrictionMessage,
        String userId,
        String reason,
        Map<String, Object> metadata
    ) {
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private EnvironmentType type;
            private Boolean enforceRestrictions;
            private String restrictionMessage;
            private String userId;
            private String reason;
            private Map<String, Object> metadata = Map.of();
            
            public Builder type(EnvironmentType type) {
                this.type = type;
                return this;
            }
            
            public Builder enforceRestrictions(Boolean enforceRestrictions) {
                this.enforceRestrictions = enforceRestrictions;
                return this;
            }
            
            public Builder restrictionMessage(String restrictionMessage) {
                this.restrictionMessage = restrictionMessage;
                return this;
            }
            
            public Builder userId(String userId) {
                this.userId = userId;
                return this;
            }
            
            public Builder reason(String reason) {
                this.reason = reason;
                return this;
            }
            
            public Builder metadata(Map<String, Object> metadata) {
                this.metadata = metadata;
                return this;
            }
            
            public EnvironmentUpdateRequest build() {
                if (userId == null || userId.trim().isEmpty()) {
                    throw new IllegalArgumentException("User ID is required for environment updates");
                }
                
                return new EnvironmentUpdateRequest(
                    Optional.ofNullable(type),
                    Optional.ofNullable(enforceRestrictions),
                    Optional.ofNullable(restrictionMessage),
                    userId,
                    reason,
                    metadata
                );
            }
        }
    }
    
    /**
     * Environment update result
     */
    record EnvironmentUpdateResult(
        boolean successful,
        String message,
        EnvironmentConfiguration previousConfiguration,
        EnvironmentConfiguration newConfiguration,
        LocalDateTime updatedAt
    ) {
        
        public static EnvironmentUpdateResult success(EnvironmentConfiguration previous, 
                                                    EnvironmentConfiguration updated) {
            return new EnvironmentUpdateResult(true, "Environment updated successfully", 
                previous, updated, LocalDateTime.now());
        }
        
        public static EnvironmentUpdateResult failure(String message, EnvironmentConfiguration current) {
            return new EnvironmentUpdateResult(false, message, current, current, LocalDateTime.now());
        }
    }
    
    /**
     * Environment validation result
     */
    record EnvironmentValidationResult(
        boolean valid,
        String validationMessage,
        SecurityImpact securityImpact,
        OperationalImpact operationalImpact,
        LocalDateTime validatedAt
    ) {
        
        public static EnvironmentValidationResult createValid() {
            return new EnvironmentValidationResult(true, "Validation passed", 
                SecurityImpact.LOW, OperationalImpact.LOW, LocalDateTime.now());
        }
        
        public static EnvironmentValidationResult invalid(String message, SecurityImpact securityImpact, 
                                                         OperationalImpact operationalImpact) {
            return new EnvironmentValidationResult(false, message, securityImpact, 
                operationalImpact, LocalDateTime.now());
        }
        
        public boolean hasHighSecurityImpact() {
            return securityImpact == SecurityImpact.HIGH || securityImpact == SecurityImpact.CRITICAL;
        }
        
        public boolean hasHighOperationalImpact() {
            return operationalImpact == OperationalImpact.HIGH || operationalImpact == OperationalImpact.CRITICAL;
        }
    }
    
    /**
     * Operation permission result
     */
    record OperationPermissionResult(
        boolean permitted,
        String reason,
        EnvironmentOperation operation,
        EnvironmentType environment,
        LocalDateTime checkedAt
    ) {
        
        public static OperationPermissionResult permitted(EnvironmentOperation operation, EnvironmentType environment) {
            return new OperationPermissionResult(true, null, operation, environment, LocalDateTime.now());
        }
        
        public static OperationPermissionResult denied(EnvironmentOperation operation, 
                                                      EnvironmentType environment, String reason) {
            return new OperationPermissionResult(false, reason, operation, environment, LocalDateTime.now());
        }
    }
    
    /**
     * Environment restrictions
     */
    record EnvironmentRestrictions(
        boolean canCreateFlows,
        boolean canCreateAdapters,
        boolean canModifyAdapterConfig,
        boolean canImportExportFlows,
        boolean canDeployFlows,
        boolean canDeleteData,
        boolean canModifySystemConfig,
        Map<String, Boolean> customRestrictions
    ) {
        
        public static EnvironmentRestrictions fromEnvironmentType(EnvironmentType type, boolean enforceRestrictions) {
            if (!enforceRestrictions) {
                return new EnvironmentRestrictions(true, true, true, true, true, true, true, Map.of());
            }
            
            return switch (type) {
                case DEVELOPMENT -> new EnvironmentRestrictions(true, true, true, true, true, true, true, Map.of());
                case QUALITY_ASSURANCE -> new EnvironmentRestrictions(true, true, false, true, true, false, false, Map.of());
                case PRODUCTION -> new EnvironmentRestrictions(false, false, false, false, true, false, false, Map.of());
            };
        }
        
        public boolean isOperationAllowed(EnvironmentOperation operation) {
            return switch (operation) {
                case CREATE_FLOW -> canCreateFlows;
                case CREATE_ADAPTER -> canCreateAdapters;
                case MODIFY_ADAPTER_CONFIG -> canModifyAdapterConfig;
                case IMPORT_EXPORT_FLOWS -> canImportExportFlows;
                case DEPLOY_FLOWS -> canDeployFlows;
                case DELETE_DATA -> canDeleteData;
                case MODIFY_SYSTEM_CONFIG -> canModifySystemConfig;
            };
        }
    }
    
    // Enumerations
    
    enum EnvironmentOperation {
        CREATE_FLOW,
        CREATE_ADAPTER,
        MODIFY_ADAPTER_CONFIG,
        IMPORT_EXPORT_FLOWS,
        DEPLOY_FLOWS,
        DELETE_DATA,
        MODIFY_SYSTEM_CONFIG
    }
    
    enum SecurityImpact {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    enum OperationalImpact {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}