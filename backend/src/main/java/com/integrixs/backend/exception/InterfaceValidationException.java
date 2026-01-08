package com.integrixs.backend.exception;

import com.integrixs.backend.dto.ExecutionValidationResult;

/**
 * Exception thrown when interface request validation fails.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public class InterfaceValidationException extends RuntimeException {
    
    private final ExecutionValidationResult validationResult;
    
    public InterfaceValidationException(ExecutionValidationResult validationResult) {
        super("Interface request validation failed: " + validationResult.getErrorsAsString());
        this.validationResult = validationResult;
    }
    
    public InterfaceValidationException(ExecutionValidationResult validationResult, String message) {
        super(message);
        this.validationResult = validationResult;
    }
    
    public ExecutionValidationResult getValidationResult() {
        return validationResult;
    }
}