package com.integrixs.backend.exception;

import java.util.List;
import java.util.UUID;

/**
 * Exception thrown when interface configuration is invalid.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public class InterfaceConfigurationException extends RuntimeException {
    
    private final UUID interfaceId;
    private final String configurationField;
    private final List<String> validationErrors;
    
    public InterfaceConfigurationException(UUID interfaceId, String configurationField, List<String> validationErrors) {
        super(String.format("Invalid configuration for interface %s, field '%s': %s", 
                           interfaceId, configurationField, String.join(", ", validationErrors)));
        this.interfaceId = interfaceId;
        this.configurationField = configurationField;
        this.validationErrors = validationErrors;
    }
    
    public InterfaceConfigurationException(UUID interfaceId, String configurationField, String error) {
        super(String.format("Invalid configuration for interface %s, field '%s': %s", 
                           interfaceId, configurationField, error));
        this.interfaceId = interfaceId;
        this.configurationField = configurationField;
        this.validationErrors = List.of(error);
    }
    
    public InterfaceConfigurationException(String configurationField, String error) {
        super(String.format("Invalid configuration field '%s': %s", configurationField, error));
        this.interfaceId = null;
        this.configurationField = configurationField;
        this.validationErrors = List.of(error);
    }
    
    public UUID getInterfaceId() {
        return interfaceId;
    }
    
    public String getConfigurationField() {
        return configurationField;
    }
    
    public List<String> getValidationErrors() {
        return validationErrors;
    }
}