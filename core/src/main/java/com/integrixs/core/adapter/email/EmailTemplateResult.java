package com.integrixs.core.adapter.email;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Immutable result object for email template processing operations.
 * Contains processed template content, execution metadata, and any warnings.
 * Part of Phase 3 email adapter refactoring following OOP principles.
 */
public final class EmailTemplateResult {
    
    private final boolean successful;
    private final String processedTemplate;
    private final String errorMessage;
    private final Throwable exception;
    private final long durationMs;
    private final List<String> warnings;
    private final LocalDateTime completedAt;
    private final int variablesProcessed;
    
    private EmailTemplateResult(boolean successful, String processedTemplate, String errorMessage, 
                               Throwable exception, long durationMs, List<String> warnings, 
                               int variablesProcessed) {
        this.successful = successful;
        this.processedTemplate = processedTemplate;
        this.errorMessage = errorMessage;
        this.exception = exception;
        this.durationMs = durationMs;
        this.warnings = warnings != null ? Collections.unmodifiableList(warnings) : Collections.emptyList();
        this.completedAt = LocalDateTime.now();
        this.variablesProcessed = variablesProcessed;
    }
    
    // Getters
    public boolean isSuccessful() { return successful; }
    public String getProcessedTemplate() { return processedTemplate; }
    public String getErrorMessage() { return errorMessage; }
    public Throwable getException() { return exception; }
    public long getDurationMs() { return durationMs; }
    public List<String> getWarnings() { return warnings; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public int getVariablesProcessed() { return variablesProcessed; }
    
    /**
     * Check if result has warnings.
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    /**
     * Check if result has an exception.
     */
    public boolean hasException() {
        return exception != null;
    }
    
    /**
     * Check if template has content.
     */
    public boolean hasProcessedTemplate() {
        return processedTemplate != null && !processedTemplate.trim().isEmpty();
    }
    
    /**
     * Get template length safely.
     */
    public int getTemplateLength() {
        return processedTemplate != null ? processedTemplate.length() : 0;
    }
    
    /**
     * Create successful template processing result.
     */
    public static EmailTemplateResult success(String processedTemplate, long durationMs, 
                                            List<String> warnings, int variablesProcessed) {
        return new EmailTemplateResult(true, processedTemplate, null, null, durationMs, warnings, variablesProcessed);
    }
    
    /**
     * Create successful template processing result without warnings.
     */
    public static EmailTemplateResult success(String processedTemplate, long durationMs) {
        return success(processedTemplate, durationMs, Collections.emptyList(), 0);
    }
    
    /**
     * Create successful template processing result with variable count.
     */
    public static EmailTemplateResult success(String processedTemplate, long durationMs, int variablesProcessed) {
        return success(processedTemplate, durationMs, Collections.emptyList(), variablesProcessed);
    }
    
    /**
     * Create failed template processing result.
     */
    public static EmailTemplateResult failure(String errorMessage) {
        return new EmailTemplateResult(false, null, errorMessage, null, 0L, Collections.emptyList(), 0);
    }
    
    /**
     * Create failed template processing result with exception.
     */
    public static EmailTemplateResult failure(String errorMessage, Throwable exception, long durationMs) {
        return new EmailTemplateResult(false, null, errorMessage, exception, durationMs, Collections.emptyList(), 0);
    }
    
    /**
     * Create failed template processing result with just error message and duration.
     */
    public static EmailTemplateResult failure(String errorMessage, long durationMs) {
        return new EmailTemplateResult(false, null, errorMessage, null, durationMs, Collections.emptyList(), 0);
    }
    
    @Override
    public String toString() {
        if (successful) {
            return String.format("EmailTemplateResult{successful=true, templateLength=%d, " +
                               "variablesProcessed=%d, durationMs=%d, warnings=%d, completedAt=%s}", 
                               getTemplateLength(), variablesProcessed, durationMs, warnings.size(), completedAt);
        } else {
            return String.format("EmailTemplateResult{successful=false, errorMessage='%s', " +
                               "durationMs=%d, hasException=%b, completedAt=%s}", 
                               errorMessage, durationMs, hasException(), completedAt);
        }
    }
}

/**
 * Immutable validation result for email templates.
 */
class EmailTemplateValidationResult {
    private final boolean valid;
    private final List<String> errors;
    private final List<String> warnings;
    
    public EmailTemplateValidationResult(boolean valid, List<String> errors, List<String> warnings) {
        this.valid = valid;
        this.errors = Collections.unmodifiableList(errors != null ? errors : Collections.emptyList());
        this.warnings = Collections.unmodifiableList(warnings != null ? warnings : Collections.emptyList());
    }
    
    public boolean isValid() { return valid; }
    public List<String> getErrors() { return errors; }
    public List<String> getWarnings() { return warnings; }
    
    public boolean hasErrors() { return !errors.isEmpty(); }
    public boolean hasWarnings() { return !warnings.isEmpty(); }
}