package com.integrixs.core.adapter.email;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Immutable result object for email attachment processing operations.
 * Contains processed attachments, processing metadata, and any warnings or errors.
 * Part of Phase 3 email adapter refactoring following OOP principles.
 */
public final class EmailAttachmentProcessingResult {
    
    private final boolean successful;
    private final List<EmailAttachment> processedAttachments;
    private final long totalAttachmentSize;
    private final String errorMessage;
    private final Throwable exception;
    private final long durationMs;
    private final List<String> warnings;
    private final LocalDateTime completedAt;
    
    private EmailAttachmentProcessingResult(boolean successful, List<EmailAttachment> processedAttachments,
                                          long totalAttachmentSize, String errorMessage, Throwable exception,
                                          long durationMs, List<String> warnings) {
        this.successful = successful;
        this.processedAttachments = processedAttachments != null ? 
            Collections.unmodifiableList(processedAttachments) : Collections.emptyList();
        this.totalAttachmentSize = totalAttachmentSize;
        this.errorMessage = errorMessage;
        this.exception = exception;
        this.durationMs = durationMs;
        this.warnings = warnings != null ? Collections.unmodifiableList(warnings) : Collections.emptyList();
        this.completedAt = LocalDateTime.now();
    }
    
    // Getters
    public boolean isSuccessful() { return successful; }
    public List<EmailAttachment> getProcessedAttachments() { return processedAttachments; }
    public long getTotalAttachmentSize() { return totalAttachmentSize; }
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
     * Check if result has processed attachments.
     */
    public boolean hasProcessedAttachments() {
        return !processedAttachments.isEmpty();
    }
    
    /**
     * Get number of processed attachments.
     */
    public int getAttachmentCount() {
        return processedAttachments.size();
    }
    
    /**
     * Get formatted total attachment size.
     */
    public String getFormattedTotalSize() {
        return formatFileSize(totalAttachmentSize);
    }
    
    /**
     * Get average attachment size.
     */
    public long getAverageAttachmentSize() {
        return processedAttachments.isEmpty() ? 0 : totalAttachmentSize / processedAttachments.size();
    }
    
    /**
     * Get largest attachment size.
     */
    public long getLargestAttachmentSize() {
        return processedAttachments.stream()
            .mapToLong(EmailAttachment::getSize)
            .max()
            .orElse(0L);
    }
    
    /**
     * Get smallest attachment size.
     */
    public long getSmallestAttachmentSize() {
        return processedAttachments.stream()
            .mapToLong(EmailAttachment::getSize)
            .min()
            .orElse(0L);
    }
    
    /**
     * Create successful attachment processing result.
     */
    public static EmailAttachmentProcessingResult success(List<EmailAttachment> processedAttachments,
                                                        long totalAttachmentSize, long durationMs,
                                                        List<String> warnings) {
        return new EmailAttachmentProcessingResult(true, processedAttachments, totalAttachmentSize,
                                                 null, null, durationMs, warnings);
    }
    
    /**
     * Create successful attachment processing result without warnings.
     */
    public static EmailAttachmentProcessingResult success(List<EmailAttachment> processedAttachments,
                                                        long totalAttachmentSize, long durationMs) {
        return success(processedAttachments, totalAttachmentSize, durationMs, Collections.emptyList());
    }
    
    /**
     * Create failed attachment processing result.
     */
    public static EmailAttachmentProcessingResult failure(String errorMessage) {
        return new EmailAttachmentProcessingResult(false, Collections.emptyList(), 0L,
                                                 errorMessage, null, 0L, Collections.emptyList());
    }
    
    /**
     * Create failed attachment processing result with exception.
     */
    public static EmailAttachmentProcessingResult failure(String errorMessage, Throwable exception, long durationMs) {
        return new EmailAttachmentProcessingResult(false, Collections.emptyList(), 0L,
                                                 errorMessage, exception, durationMs, Collections.emptyList());
    }
    
    /**
     * Create failed attachment processing result with duration.
     */
    public static EmailAttachmentProcessingResult failure(String errorMessage, long durationMs) {
        return new EmailAttachmentProcessingResult(false, Collections.emptyList(), 0L,
                                                 errorMessage, null, durationMs, Collections.emptyList());
    }
    
    /**
     * Format file size in human-readable format.
     */
    private String formatFileSize(long sizeInBytes) {
        if (sizeInBytes < 1024) {
            return sizeInBytes + " B";
        } else if (sizeInBytes < 1024 * 1024) {
            return String.format("%.1f KB", sizeInBytes / 1024.0);
        } else if (sizeInBytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", sizeInBytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", sizeInBytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    @Override
    public String toString() {
        if (successful) {
            return String.format("EmailAttachmentProcessingResult{successful=true, attachmentCount=%d, " +
                               "totalSize=%s, durationMs=%d, warnings=%d, completedAt=%s}", 
                               getAttachmentCount(), getFormattedTotalSize(), durationMs, 
                               warnings.size(), completedAt);
        } else {
            return String.format("EmailAttachmentProcessingResult{successful=false, errorMessage='%s', " +
                               "durationMs=%d, hasException=%b, completedAt=%s}", 
                               errorMessage, durationMs, hasException(), completedAt);
        }
    }
}

/**
 * Immutable validation result for email attachments.
 */
class EmailAttachmentValidationResult {
    private final boolean valid;
    private final List<String> errors;
    private final List<String> warnings;
    
    public EmailAttachmentValidationResult(boolean valid, List<String> errors, List<String> warnings) {
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