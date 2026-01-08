package com.integrixs.backend.dto;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Immutable result object for execution request validation operations.
 * Contains validation status, errors, warnings, and validation metadata.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public final class ExecutionValidationResult {
    
    private final boolean valid;
    private final List<String> errors;
    private final List<String> warnings;
    private final LocalDateTime validatedAt;
    
    public ExecutionValidationResult(boolean valid, List<String> errors, List<String> warnings) {
        this.valid = valid;
        this.errors = Collections.unmodifiableList(errors != null ? errors : Collections.emptyList());
        this.warnings = Collections.unmodifiableList(warnings != null ? warnings : Collections.emptyList());
        this.validatedAt = LocalDateTime.now();
    }
    
    // Getters
    public boolean isValid() { return valid; }
    public List<String> getErrors() { return errors; }
    public List<String> getWarnings() { return warnings; }
    public LocalDateTime getValidatedAt() { return validatedAt; }
    
    /**
     * Check if validation result has errors.
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    /**
     * Check if validation result has warnings.
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    /**
     * Get error count.
     */
    public int getErrorCount() {
        return errors.size();
    }
    
    /**
     * Get warning count.
     */
    public int getWarningCount() {
        return warnings.size();
    }
    
    /**
     * Get first error message if available.
     */
    public String getFirstError() {
        return errors.isEmpty() ? null : errors.get(0);
    }
    
    /**
     * Get first warning message if available.
     */
    public String getFirstWarning() {
        return warnings.isEmpty() ? null : warnings.get(0);
    }
    
    /**
     * Get all error messages as single string.
     */
    public String getErrorsAsString() {
        return String.join(", ", errors);
    }
    
    /**
     * Get all warning messages as single string.
     */
    public String getWarningsAsString() {
        return String.join(", ", warnings);
    }
    
    /**
     * Create successful validation result.
     */
    public static ExecutionValidationResult success() {
        return new ExecutionValidationResult(true, Collections.emptyList(), Collections.emptyList());
    }
    
    /**
     * Create successful validation result with warning.
     */
    public static ExecutionValidationResult success(String warning) {
        return new ExecutionValidationResult(true, Collections.emptyList(), 
                                           Collections.singletonList(warning));
    }
    
    /**
     * Create successful validation result with warnings.
     */
    public static ExecutionValidationResult success(List<String> warnings) {
        return new ExecutionValidationResult(true, Collections.emptyList(), warnings);
    }
    
    /**
     * Create failed validation result.
     */
    public static ExecutionValidationResult failure(String error) {
        return new ExecutionValidationResult(false, Collections.singletonList(error), 
                                           Collections.emptyList());
    }
    
    /**
     * Create failed validation result with multiple errors.
     */
    public static ExecutionValidationResult failure(List<String> errors) {
        return new ExecutionValidationResult(false, errors, Collections.emptyList());
    }
    
    /**
     * Create failed validation result with errors and warnings.
     */
    public static ExecutionValidationResult failure(List<String> errors, List<String> warnings) {
        return new ExecutionValidationResult(false, errors, warnings);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ExecutionValidationResult{");
        sb.append("valid=").append(valid);
        sb.append(", errorCount=").append(getErrorCount());
        sb.append(", warningCount=").append(getWarningCount());
        sb.append(", validatedAt=").append(validatedAt);
        
        if (hasErrors()) {
            sb.append(", firstError='").append(getFirstError()).append("'");
        }
        
        if (hasWarnings()) {
            sb.append(", firstWarning='").append(getFirstWarning()).append("'");
        }
        
        sb.append("}");
        return sb.toString();
    }
}