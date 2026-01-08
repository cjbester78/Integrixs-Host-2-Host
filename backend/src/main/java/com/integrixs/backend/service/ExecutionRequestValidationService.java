package com.integrixs.backend.service;

import com.integrixs.backend.dto.ExecutionValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Centralized request validation service for execution controller operations.
 * Implements validation strategy pattern with immutable validation results.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
@Service
public class ExecutionRequestValidationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ExecutionRequestValidationService.class);
    
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 1000;
    private static final int DEFAULT_PAGE_SIZE = 20;
    
    private final Map<String, ExecutionRequestValidator> validators = new HashMap<>();
    
    public ExecutionRequestValidationService() {
        // Register built-in validators
        registerValidator("history", new ExecutionHistoryValidator());
        registerValidator("details", new ExecutionDetailsValidator());
        registerValidator("steps", new ExecutionStepsValidator());
        registerValidator("retry", new ExecutionRetryValidator());
        registerValidator("cancel", new ExecutionCancelValidator());
        registerValidator("logs", new ExecutionLogsValidator());
        registerValidator("trace", new ExecutionTraceValidator());
        registerValidator("search", new ExecutionSearchValidator());
        
        logger.info("ExecutionRequestValidationService initialized with {} validators", validators.size());
    }
    
    /**
     * Validate execution history request parameters.
     */
    public ExecutionValidationResult validateHistoryRequest(Map<String, Object> parameters) {
        ExecutionRequestValidator validator = getValidator("history");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate execution details request parameters.
     */
    public ExecutionValidationResult validateDetailsRequest(String executionId) {
        Map<String, Object> parameters = Map.of("executionId", executionId);
        ExecutionRequestValidator validator = getValidator("details");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate execution steps request parameters.
     */
    public ExecutionValidationResult validateStepsRequest(String executionId) {
        Map<String, Object> parameters = Map.of("executionId", executionId);
        ExecutionRequestValidator validator = getValidator("steps");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate execution retry request parameters.
     */
    public ExecutionValidationResult validateRetryRequest(String executionId, String userId) {
        Map<String, Object> parameters = Map.of(
            "executionId", executionId,
            "userId", userId
        );
        ExecutionRequestValidator validator = getValidator("retry");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate execution cancel request parameters.
     */
    public ExecutionValidationResult validateCancelRequest(String executionId, String userId) {
        Map<String, Object> parameters = Map.of(
            "executionId", executionId,
            "userId", userId
        );
        ExecutionRequestValidator validator = getValidator("cancel");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate execution logs request parameters.
     */
    public ExecutionValidationResult validateLogsRequest(String executionId, String filter, String level, Integer limit) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("executionId", executionId);
        if (filter != null) parameters.put("filter", filter);
        if (level != null) parameters.put("level", level);
        if (limit != null) parameters.put("limit", limit);
        
        ExecutionRequestValidator validator = getValidator("logs");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate execution trace request parameters.
     */
    public ExecutionValidationResult validateTraceRequest(String executionId) {
        Map<String, Object> parameters = Map.of("executionId", executionId);
        ExecutionRequestValidator validator = getValidator("trace");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate execution search request parameters.
     */
    public ExecutionValidationResult validateSearchRequest(String messageId) {
        Map<String, Object> parameters = Map.of("messageId", messageId);
        ExecutionRequestValidator validator = getValidator("search");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Register request validator.
     */
    public void registerValidator(String type, ExecutionRequestValidator validator) {
        if (type != null && validator != null) {
            validators.put(type.toLowerCase(), validator);
            logger.debug("Registered execution request validator: {}", type);
        }
    }
    
    /**
     * Get request validator by type.
     */
    private ExecutionRequestValidator getValidator(String validatorType) {
        ExecutionRequestValidator validator = validators.get(validatorType.toLowerCase());
        
        if (validator == null) {
            logger.warn("Unknown execution request validator: {}, using details validator", validatorType);
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
 * Interface for execution request validation strategies.
 */
interface ExecutionRequestValidator {
    ExecutionValidationResult validateRequest(Map<String, Object> parameters);
    String getValidatorName();
    String getValidatorDescription();
}

/**
 * Execution history request validator.
 */
class ExecutionHistoryValidator implements ExecutionRequestValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(ExecutionHistoryValidator.class);
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate page parameter
        Object pageObj = parameters.get("page");
        if (pageObj != null) {
            try {
                int page = Integer.parseInt(pageObj.toString());
                if (page < 0) {
                    errors.add("Page number cannot be negative");
                }
            } catch (NumberFormatException e) {
                errors.add("Page must be a valid integer");
            }
        }
        
        // Validate size parameter
        Object sizeObj = parameters.get("size");
        if (sizeObj != null) {
            try {
                int size = Integer.parseInt(sizeObj.toString());
                if (size < 1) {
                    errors.add("Page size must be at least 1");
                } else if (size > 1000) {
                    errors.add("Page size cannot exceed 1000");
                } else if (size > 100) {
                    warnings.add("Large page size may impact performance");
                }
            } catch (NumberFormatException e) {
                errors.add("Size must be a valid integer");
            }
        }
        
        // Validate flowId parameter
        Object flowIdObj = parameters.get("flowId");
        if (flowIdObj != null && !flowIdObj.toString().trim().isEmpty()) {
            try {
                UUID.fromString(flowIdObj.toString());
            } catch (IllegalArgumentException e) {
                errors.add("FlowId must be a valid UUID");
            }
        }
        
        // Validate status parameter
        Object statusObj = parameters.get("status");
        if (statusObj != null && !statusObj.toString().trim().isEmpty()) {
            String status = statusObj.toString().toUpperCase();
            try {
                // Assuming ExecutionStatus enum exists
                validateExecutionStatus(status);
            } catch (IllegalArgumentException e) {
                errors.add("Invalid execution status: " + statusObj.toString());
            }
        }
        
        // Validate date parameters
        Object startDateObj = parameters.get("startDate");
        Object endDateObj = parameters.get("endDate");
        
        LocalDateTime startDate = null;
        LocalDateTime endDate = null;
        
        if (startDateObj instanceof LocalDateTime) {
            startDate = (LocalDateTime) startDateObj;
        }
        
        if (endDateObj instanceof LocalDateTime) {
            endDate = (LocalDateTime) endDateObj;
        }
        
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            errors.add("Start date cannot be after end date");
        }
        
        if (endDate != null && endDate.isAfter(LocalDateTime.now())) {
            warnings.add("End date is in the future");
        }
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    private void validateExecutionStatus(String status) {
        // List of valid execution statuses - this should match actual enum values
        Set<String> validStatuses = Set.of("PENDING", "RUNNING", "COMPLETED", "FAILED", "CANCELLED", "RETRY");
        if (!validStatuses.contains(status)) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }
    }
    
    @Override
    public String getValidatorName() {
        return "Execution History";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates execution history request parameters including pagination, filters, and dates";
    }
}

/**
 * Execution details request validator.
 */
class ExecutionDetailsValidator implements ExecutionRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        Object executionIdObj = parameters.get("executionId");
        if (executionIdObj == null || executionIdObj.toString().trim().isEmpty()) {
            errors.add("Execution ID is required");
        } else {
            try {
                UUID.fromString(executionIdObj.toString());
            } catch (IllegalArgumentException e) {
                errors.add("Execution ID must be a valid UUID");
            }
        }
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public String getValidatorName() {
        return "Execution Details";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates execution details request parameters";
    }
}

/**
 * Execution steps request validator.
 */
class ExecutionStepsValidator implements ExecutionRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        // Same validation as details validator
        ExecutionDetailsValidator detailsValidator = new ExecutionDetailsValidator();
        return detailsValidator.validateRequest(parameters);
    }
    
    @Override
    public String getValidatorName() {
        return "Execution Steps";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates execution steps request parameters";
    }
}

/**
 * Execution retry request validator.
 */
class ExecutionRetryValidator implements ExecutionRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate execution ID
        Object executionIdObj = parameters.get("executionId");
        if (executionIdObj == null || executionIdObj.toString().trim().isEmpty()) {
            errors.add("Execution ID is required");
        } else {
            try {
                UUID.fromString(executionIdObj.toString());
            } catch (IllegalArgumentException e) {
                errors.add("Execution ID must be a valid UUID");
            }
        }
        
        // Validate user ID
        Object userIdObj = parameters.get("userId");
        if (userIdObj == null || userIdObj.toString().trim().isEmpty()) {
            errors.add("User ID is required");
        } else {
            try {
                UUID.fromString(userIdObj.toString());
            } catch (IllegalArgumentException e) {
                errors.add("User ID must be a valid UUID");
            }
        }
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public String getValidatorName() {
        return "Execution Retry";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates execution retry request parameters including execution ID and user ID";
    }
}

/**
 * Execution cancel request validator.
 */
class ExecutionCancelValidator implements ExecutionRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        // Same validation as retry validator
        ExecutionRetryValidator retryValidator = new ExecutionRetryValidator();
        return retryValidator.validateRequest(parameters);
    }
    
    @Override
    public String getValidatorName() {
        return "Execution Cancel";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates execution cancel request parameters including execution ID and user ID";
    }
}

/**
 * Execution logs request validator.
 */
class ExecutionLogsValidator implements ExecutionRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate execution ID
        Object executionIdObj = parameters.get("executionId");
        if (executionIdObj == null || executionIdObj.toString().trim().isEmpty()) {
            errors.add("Execution ID is required");
        } else {
            try {
                UUID.fromString(executionIdObj.toString());
            } catch (IllegalArgumentException e) {
                errors.add("Execution ID must be a valid UUID");
            }
        }
        
        // Validate level parameter
        Object levelObj = parameters.get("level");
        if (levelObj != null && !levelObj.toString().trim().isEmpty()) {
            String level = levelObj.toString().toUpperCase();
            Set<String> validLevels = Set.of("DEBUG", "INFO", "WARN", "ERROR", "FATAL");
            if (!validLevels.contains(level)) {
                errors.add("Invalid log level: " + levelObj.toString());
            }
        }
        
        // Validate limit parameter
        Object limitObj = parameters.get("limit");
        if (limitObj != null) {
            try {
                int limit = Integer.parseInt(limitObj.toString());
                if (limit < 1) {
                    errors.add("Limit must be at least 1");
                } else if (limit > 10000) {
                    errors.add("Limit cannot exceed 10000");
                } else if (limit > 1000) {
                    warnings.add("Large limit may impact performance");
                }
            } catch (NumberFormatException e) {
                errors.add("Limit must be a valid integer");
            }
        }
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public String getValidatorName() {
        return "Execution Logs";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates execution logs request parameters including filters and limits";
    }
}

/**
 * Execution trace request validator.
 */
class ExecutionTraceValidator implements ExecutionRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        // Same validation as details validator
        ExecutionDetailsValidator detailsValidator = new ExecutionDetailsValidator();
        return detailsValidator.validateRequest(parameters);
    }
    
    @Override
    public String getValidatorName() {
        return "Execution Trace";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates execution trace request parameters";
    }
}

/**
 * Execution search request validator.
 */
class ExecutionSearchValidator implements ExecutionRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        Object messageIdObj = parameters.get("messageId");
        if (messageIdObj == null || messageIdObj.toString().trim().isEmpty()) {
            errors.add("Message ID is required");
        } else {
            String messageId = messageIdObj.toString().trim();
            if (messageId.length() < 3) {
                errors.add("Message ID must be at least 3 characters");
            } else if (messageId.length() > 100) {
                errors.add("Message ID cannot exceed 100 characters");
            }
        }
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public String getValidatorName() {
        return "Execution Search";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates execution search request parameters including message ID format";
    }
}