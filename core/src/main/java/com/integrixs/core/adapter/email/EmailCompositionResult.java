package com.integrixs.core.adapter.email;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Immutable result object for email composition operations.
 * Contains composition result, timing, and any warning/error messages.
 * Part of Phase 3 email adapter refactoring following OOP principles.
 */
public final class EmailCompositionResult {
    
    private final boolean successful;
    private final EmailComposition composition;
    private final String errorMessage;
    private final Throwable exception;
    private final long durationMs;
    private final List<String> warnings;
    private final LocalDateTime completedAt;
    
    private EmailCompositionResult(boolean successful, EmailComposition composition, 
                                  String errorMessage, Throwable exception, long durationMs, 
                                  List<String> warnings) {
        this.successful = successful;
        this.composition = composition;
        this.errorMessage = errorMessage;
        this.exception = exception;
        this.durationMs = durationMs;
        this.warnings = warnings != null ? Collections.unmodifiableList(warnings) : Collections.emptyList();
        this.completedAt = LocalDateTime.now();
    }
    
    // Getters
    public boolean isSuccessful() { return successful; }
    public EmailComposition getComposition() { return composition; }
    public String getErrorMessage() { return errorMessage; }
    public Throwable getException() { return exception; }
    public long getDurationMs() { return durationMs; }
    public List<String> getWarnings() { return warnings; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    
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
     * Get attachment count from composition if available.
     */
    public int getAttachmentCount() {
        return composition != null ? composition.getAttachmentCount() : 0;
    }
    
    /**
     * Create successful composition result.
     */
    public static EmailCompositionResult success(EmailComposition composition, long durationMs, 
                                               List<String> warnings) {
        return new EmailCompositionResult(true, composition, null, null, durationMs, warnings);
    }
    
    /**
     * Create successful composition result without warnings.
     */
    public static EmailCompositionResult success(EmailComposition composition, long durationMs) {
        return success(composition, durationMs, Collections.emptyList());
    }
    
    /**
     * Create failed composition result with error message.
     */
    public static EmailCompositionResult failure(String errorMessage) {
        return new EmailCompositionResult(false, null, errorMessage, null, 0L, Collections.emptyList());
    }
    
    /**
     * Create failed composition result with error message and exception.
     */
    public static EmailCompositionResult failure(String errorMessage, Throwable exception, long durationMs) {
        return new EmailCompositionResult(false, null, errorMessage, exception, durationMs, Collections.emptyList());
    }
    
    /**
     * Create failed composition result with just error message and duration.
     */
    public static EmailCompositionResult failure(String errorMessage, long durationMs) {
        return new EmailCompositionResult(false, null, errorMessage, null, durationMs, Collections.emptyList());
    }
    
    @Override
    public String toString() {
        if (successful) {
            return String.format("EmailCompositionResult{successful=true, attachmentCount=%d, " +
                               "durationMs=%d, warnings=%d, completedAt=%s}", 
                               getAttachmentCount(), durationMs, warnings.size(), completedAt);
        } else {
            return String.format("EmailCompositionResult{successful=false, errorMessage='%s', " +
                               "durationMs=%d, hasException=%b, completedAt=%s}", 
                               errorMessage, durationMs, hasException(), completedAt);
        }
    }
}