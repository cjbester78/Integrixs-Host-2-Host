package com.integrixs.backend.exception;

import com.integrixs.backend.dto.ExecutionValidationResult;

/**
 * Exception thrown when execution request validation fails.
 * Contains detailed validation results for proper error handling.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public class ExecutionValidationException extends RuntimeException {
    
    private final ExecutionValidationResult validationResult;
    
    public ExecutionValidationException(ExecutionValidationResult validationResult) {
        super("Execution request validation failed: " + validationResult.getErrorsAsString());
        this.validationResult = validationResult;
    }
    
    public ExecutionValidationException(ExecutionValidationResult validationResult, String message) {
        super(message);
        this.validationResult = validationResult;
    }
    
    public ExecutionValidationException(ExecutionValidationResult validationResult, String message, Throwable cause) {
        super(message, cause);
        this.validationResult = validationResult;
    }
    
    public ExecutionValidationResult getValidationResult() {
        return validationResult;
    }
    
    /**
     * Check if validation result has errors.
     */
    public boolean hasErrors() {
        return validationResult.hasErrors();
    }
    
    /**
     * Check if validation result has warnings.
     */
    public boolean hasWarnings() {
        return validationResult.hasWarnings();
    }
    
    /**
     * Get error count from validation result.
     */
    public int getErrorCount() {
        return validationResult.getErrorCount();
    }
    
    /**
     * Get warning count from validation result.
     */
    public int getWarningCount() {
        return validationResult.getWarningCount();
    }
}