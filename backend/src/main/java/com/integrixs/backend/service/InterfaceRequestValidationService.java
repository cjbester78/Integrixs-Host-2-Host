package com.integrixs.backend.service;

import com.integrixs.backend.dto.ExecutionValidationResult;
import com.integrixs.shared.model.Adapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Centralized request validation service for interface controller operations.
 * Implements validation strategy pattern with immutable validation results.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
@Service
public class InterfaceRequestValidationService {
    
    private static final Logger logger = LoggerFactory.getLogger(InterfaceRequestValidationService.class);
    
    private static final Set<String> VALID_ADAPTER_TYPES = Set.of("FILE", "SFTP", "EMAIL");
    private static final Set<String> VALID_DIRECTIONS = Set.of("INBOUND", "OUTBOUND", "BIDIRECTIONAL");
    
    private final Map<String, InterfaceRequestValidator> validators = new HashMap<>();
    
    public InterfaceRequestValidationService() {
        // Register built-in validators
        registerValidator("list", new InterfaceListValidator());
        registerValidator("details", new InterfaceDetailsValidator());
        registerValidator("create", new InterfaceCreateValidator());
        registerValidator("update", new InterfaceUpdateValidator());
        registerValidator("delete", new InterfaceDeleteValidator());
        registerValidator("test", new InterfaceTestValidator());
        registerValidator("execute", new InterfaceExecuteValidator());
        registerValidator("lifecycle", new InterfaceLifecycleValidator());
    }
    
    /**
     * Validate interface list request parameters.
     */
    public ExecutionValidationResult validateListRequest(Map<String, Object> parameters) {
        InterfaceRequestValidator validator = getValidator("list");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate interface details request parameters.
     */
    public ExecutionValidationResult validateDetailsRequest(String interfaceId) {
        Map<String, Object> parameters = Map.of("interfaceId", interfaceId);
        InterfaceRequestValidator validator = getValidator("details");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate interface creation request.
     */
    public ExecutionValidationResult validateCreateRequest(Adapter adapterData) {
        Map<String, Object> parameters = Map.of("adapterData", adapterData);
        InterfaceRequestValidator validator = getValidator("create");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate interface update request.
     */
    public ExecutionValidationResult validateUpdateRequest(String interfaceId, Adapter adapterData) {
        Map<String, Object> parameters = Map.of(
            "interfaceId", interfaceId,
            "adapterData", adapterData
        );
        InterfaceRequestValidator validator = getValidator("update");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate interface deletion request.
     */
    public ExecutionValidationResult validateDeleteRequest(String interfaceId) {
        Map<String, Object> parameters = Map.of("interfaceId", interfaceId);
        InterfaceRequestValidator validator = getValidator("delete");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate interface test connection request.
     */
    public ExecutionValidationResult validateTestRequest(String interfaceId) {
        Map<String, Object> parameters = Map.of("interfaceId", interfaceId);
        InterfaceRequestValidator validator = getValidator("test");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate interface execution request.
     */
    public ExecutionValidationResult validateExecuteRequest(String interfaceId) {
        Map<String, Object> parameters = Map.of("interfaceId", interfaceId);
        InterfaceRequestValidator validator = getValidator("execute");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate interface lifecycle operations (start/stop/enable/disable).
     */
    public ExecutionValidationResult validateLifecycleRequest(String interfaceId, String operation) {
        Map<String, Object> parameters = Map.of(
            "interfaceId", interfaceId,
            "operation", operation
        );
        InterfaceRequestValidator validator = getValidator("lifecycle");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Register request validator.
     */
    public void registerValidator(String type, InterfaceRequestValidator validator) {
        if (type != null && validator != null) {
            validators.put(type.toLowerCase(), validator);
            logger.debug("Registered interface request validator: {}", type);
        }
    }
    
    /**
     * Get request validator by type.
     */
    private InterfaceRequestValidator getValidator(String validatorType) {
        InterfaceRequestValidator validator = validators.get(validatorType.toLowerCase());
        
        if (validator == null) {
            logger.warn("Unknown interface request validator: {}, using details validator", validatorType);
            validator = validators.get("details");
        }
        
        return validator;
    }
    
    /**
     * Get available validator types.
     */
    public Set<String> getAvailableValidatorTypes() {
        return new HashSet<>(validators.keySet());
    }
}

/**
 * Interface for interface request validation strategies.
 */
interface InterfaceRequestValidator {
    ExecutionValidationResult validateRequest(Map<String, Object> parameters);
    String getValidatorName();
    String getValidatorDescription();
}

/**
 * Interface list request validator.
 */
class InterfaceListValidator implements InterfaceRequestValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(InterfaceListValidator.class);
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate type parameter
        Object typeObj = parameters.get("type");
        if (typeObj != null && !typeObj.toString().trim().isEmpty()) {
            String type = typeObj.toString().toUpperCase();
            if (!Set.of("FILE", "SFTP", "EMAIL").contains(type)) {
                errors.add("Invalid adapter type: " + typeObj.toString());
            }
        }
        
        // Validate direction parameter
        Object directionObj = parameters.get("direction");
        if (directionObj != null && !directionObj.toString().trim().isEmpty()) {
            String direction = directionObj.toString().toUpperCase();
            if (!Set.of("INBOUND", "OUTBOUND", "BIDIRECTIONAL").contains(direction)) {
                errors.add("Invalid direction: " + directionObj.toString());
            }
        }
        
        // Validate enabled parameter
        Object enabledObj = parameters.get("enabled");
        if (enabledObj != null && !(enabledObj instanceof Boolean)) {
            try {
                Boolean.parseBoolean(enabledObj.toString());
            } catch (Exception e) {
                errors.add("Enabled parameter must be boolean");
            }
        }
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public String getValidatorName() {
        return "Interface List";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates interface list request parameters including type, direction, and enabled filters";
    }
}

/**
 * Interface details request validator.
 */
class InterfaceDetailsValidator implements InterfaceRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        Object interfaceIdObj = parameters.get("interfaceId");
        if (interfaceIdObj == null || interfaceIdObj.toString().trim().isEmpty()) {
            errors.add("Interface ID is required");
        } else {
            try {
                UUID.fromString(interfaceIdObj.toString());
            } catch (IllegalArgumentException e) {
                errors.add("Interface ID must be a valid UUID");
            }
        }
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public String getValidatorName() {
        return "Interface Details";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates interface details request parameters";
    }
}

/**
 * Interface creation request validator.
 */
class InterfaceCreateValidator implements InterfaceRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        Object adapterDataObj = parameters.get("adapterData");
        if (adapterDataObj == null) {
            errors.add("Adapter data is required");
        } else if (adapterDataObj instanceof Adapter) {
            Adapter adapter = (Adapter) adapterDataObj;
            
            // Validate name
            if (adapter.getName() == null || adapter.getName().trim().isEmpty()) {
                errors.add("Adapter name is required");
            } else if (adapter.getName().length() > 100) {
                errors.add("Adapter name cannot exceed 100 characters");
            }
            
            // Validate type
            if (adapter.getAdapterType() == null || adapter.getAdapterType().trim().isEmpty()) {
                errors.add("Adapter type is required");
            } else if (!Set.of("FILE", "SFTP", "EMAIL").contains(adapter.getAdapterType().toUpperCase())) {
                errors.add("Invalid adapter type: " + adapter.getAdapterType());
            }
            
            // Validate direction
            if (adapter.getDirection() == null || adapter.getDirection().trim().isEmpty()) {
                errors.add("Adapter direction is required");
            } else if (!Set.of("INBOUND", "OUTBOUND", "BIDIRECTIONAL").contains(adapter.getDirection().toUpperCase())) {
                errors.add("Invalid adapter direction: " + adapter.getDirection());
            }
            
            // Validate configuration
            if (adapter.getConfiguration() == null || adapter.getConfiguration().isEmpty()) {
                warnings.add("Adapter configuration is empty");
            }
        } else {
            errors.add("Invalid adapter data format");
        }
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public String getValidatorName() {
        return "Interface Create";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates interface creation request including adapter data validation";
    }
}

/**
 * Interface update request validator.
 */
class InterfaceUpdateValidator implements InterfaceRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate interface ID first
        InterfaceDetailsValidator detailsValidator = new InterfaceDetailsValidator();
        ExecutionValidationResult idValidation = detailsValidator.validateRequest(parameters);
        if (!idValidation.isValid()) {
            errors.addAll(idValidation.getErrors());
        }
        
        // Then validate adapter data
        InterfaceCreateValidator createValidator = new InterfaceCreateValidator();
        ExecutionValidationResult dataValidation = createValidator.validateRequest(parameters);
        if (!dataValidation.isValid()) {
            errors.addAll(dataValidation.getErrors());
        }
        warnings.addAll(dataValidation.getWarnings());
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public String getValidatorName() {
        return "Interface Update";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates interface update request including ID and adapter data validation";
    }
}

/**
 * Interface deletion request validator.
 */
class InterfaceDeleteValidator implements InterfaceRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        // Same validation as details validator
        InterfaceDetailsValidator detailsValidator = new InterfaceDetailsValidator();
        return detailsValidator.validateRequest(parameters);
    }
    
    @Override
    public String getValidatorName() {
        return "Interface Delete";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates interface deletion request parameters";
    }
}

/**
 * Interface test connection request validator.
 */
class InterfaceTestValidator implements InterfaceRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        // Same validation as details validator
        InterfaceDetailsValidator detailsValidator = new InterfaceDetailsValidator();
        return detailsValidator.validateRequest(parameters);
    }
    
    @Override
    public String getValidatorName() {
        return "Interface Test";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates interface test connection request parameters";
    }
}

/**
 * Interface execution request validator.
 */
class InterfaceExecuteValidator implements InterfaceRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        // Same validation as details validator
        InterfaceDetailsValidator detailsValidator = new InterfaceDetailsValidator();
        return detailsValidator.validateRequest(parameters);
    }
    
    @Override
    public String getValidatorName() {
        return "Interface Execute";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates interface execution request parameters";
    }
}

/**
 * Interface lifecycle operations validator.
 */
class InterfaceLifecycleValidator implements InterfaceRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate interface ID first
        InterfaceDetailsValidator detailsValidator = new InterfaceDetailsValidator();
        ExecutionValidationResult idValidation = detailsValidator.validateRequest(parameters);
        if (!idValidation.isValid()) {
            errors.addAll(idValidation.getErrors());
        }
        
        // Validate operation
        Object operationObj = parameters.get("operation");
        if (operationObj != null && !operationObj.toString().trim().isEmpty()) {
            String operation = operationObj.toString().toLowerCase();
            if (!Set.of("start", "stop", "enable", "disable").contains(operation)) {
                errors.add("Invalid lifecycle operation: " + operationObj.toString());
            }
        }
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    
    @Override
    public String getValidatorName() {
        return "Interface Lifecycle";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates interface lifecycle operations (start, stop, enable, disable)";
    }
}