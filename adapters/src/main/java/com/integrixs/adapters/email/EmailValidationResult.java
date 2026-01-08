package com.integrixs.adapters.email;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable result object for email validation operations.
 * Contains validation status, errors, warnings, and metadata.
 * Part of Phase 3 email adapter refactoring following OOP principles.
 */
public final class EmailValidationResult {
    
    private final boolean valid;
    private final List<String> errors;
    private final List<String> warnings;
    private final Map<String, Object> validationMetadata;
    private final long durationMs;
    private final LocalDateTime validatedAt;
    
    public EmailValidationResult(boolean valid, List<String> errors, List<String> warnings, 
                               Map<String, Object> validationMetadata, long durationMs) {
        this.valid = valid;
        this.errors = Collections.unmodifiableList(errors != null ? errors : Collections.emptyList());
        this.warnings = Collections.unmodifiableList(warnings != null ? warnings : Collections.emptyList());
        this.validationMetadata = validationMetadata != null ? 
            Collections.unmodifiableMap(validationMetadata) : Collections.emptyMap();
        this.durationMs = durationMs;
        this.validatedAt = LocalDateTime.now();
    }
    
    // Getters
    public boolean isValid() { return valid; }
    public List<String> getErrors() { return errors; }
    public List<String> getWarnings() { return warnings; }
    public Map<String, Object> getValidationMetadata() { return validationMetadata; }
    public long getDurationMs() { return durationMs; }
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
     * Check if validation result has metadata.
     */
    public boolean hasValidationMetadata() {
        return !validationMetadata.isEmpty();
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
     * Get metadata value with type safety.
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadataValue(String key, Class<T> type, T defaultValue) {
        if (!validationMetadata.containsKey(key)) {
            return defaultValue;
        }
        
        Object value = validationMetadata.get(key);
        if (type.isInstance(value)) {
            return (T) value;
        }
        
        return defaultValue;
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
    public static EmailValidationResult success() {
        return new EmailValidationResult(true, Collections.emptyList(), Collections.emptyList(), null, 0L);
    }
    
    /**
     * Create successful validation result with warning.
     */
    public static EmailValidationResult success(String warning) {
        return new EmailValidationResult(true, Collections.emptyList(), 
                                       Collections.singletonList(warning), null, 0L);
    }
    
    /**
     * Create successful validation result with warnings.
     */
    public static EmailValidationResult success(List<String> warnings) {
        return new EmailValidationResult(true, Collections.emptyList(), warnings, null, 0L);
    }
    
    /**
     * Create successful validation result with metadata.
     */
    public static EmailValidationResult success(List<String> warnings, Map<String, Object> metadata, long durationMs) {
        return new EmailValidationResult(true, Collections.emptyList(), warnings, metadata, durationMs);
    }
    
    /**
     * Create failed validation result.
     */
    public static EmailValidationResult failure(String error) {
        return new EmailValidationResult(false, Collections.singletonList(error), 
                                       Collections.emptyList(), null, 0L);
    }
    
    /**
     * Create failed validation result with multiple errors.
     */
    public static EmailValidationResult failure(List<String> errors) {
        return new EmailValidationResult(false, errors, Collections.emptyList(), null, 0L);
    }
    
    /**
     * Create failed validation result with errors and warnings.
     */
    public static EmailValidationResult failure(List<String> errors, List<String> warnings) {
        return new EmailValidationResult(false, errors, warnings, null, 0L);
    }
    
    /**
     * Create failed validation result with errors, warnings, and metadata.
     */
    public static EmailValidationResult failure(List<String> errors, List<String> warnings, 
                                              Map<String, Object> metadata, long durationMs) {
        return new EmailValidationResult(false, errors, warnings, metadata, durationMs);
    }
    
    /**
     * Combine multiple validation results.
     */
    public static EmailValidationResult combine(List<EmailValidationResult> results) {
        if (results == null || results.isEmpty()) {
            return success();
        }
        
        boolean allValid = true;
        List<String> allErrors = new java.util.ArrayList<>();
        List<String> allWarnings = new java.util.ArrayList<>();
        long totalDuration = 0L;
        
        for (EmailValidationResult result : results) {
            if (!result.isValid()) {
                allValid = false;
            }
            allErrors.addAll(result.getErrors());
            allWarnings.addAll(result.getWarnings());
            totalDuration += result.getDurationMs();
        }
        
        return new EmailValidationResult(allValid, allErrors, allWarnings, null, totalDuration);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("EmailValidationResult{");
        sb.append("valid=").append(valid);
        sb.append(", errorCount=").append(getErrorCount());
        sb.append(", warningCount=").append(getWarningCount());
        sb.append(", durationMs=").append(durationMs);
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