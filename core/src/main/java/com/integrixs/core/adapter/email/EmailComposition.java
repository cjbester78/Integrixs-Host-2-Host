package com.integrixs.core.adapter.email;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Immutable email composition result containing all composed email components.
 * Represents a fully composed email ready for sending by email delivery services.
 * Part of Phase 3 email adapter refactoring following OOP principles.
 */
public final class EmailComposition {
    
    private final String correlationId;
    private final String fromAddress;
    private final String fromName;
    private final List<String> toAddresses;
    private final List<String> ccAddresses;
    private final List<String> bccAddresses;
    private final String subject;
    private final String bodyContent;
    private final boolean isHtmlContent;
    private final List<EmailAttachment> attachments;
    private final LocalDateTime composedAt;
    
    private EmailComposition(Builder builder) {
        this.correlationId = builder.correlationId;
        this.fromAddress = builder.fromAddress;
        this.fromName = builder.fromName;
        this.toAddresses = builder.toAddresses != null ? 
            Collections.unmodifiableList(builder.toAddresses) : Collections.emptyList();
        this.ccAddresses = builder.ccAddresses != null ? 
            Collections.unmodifiableList(builder.ccAddresses) : null;
        this.bccAddresses = builder.bccAddresses != null ? 
            Collections.unmodifiableList(builder.bccAddresses) : null;
        this.subject = builder.subject;
        this.bodyContent = builder.bodyContent;
        this.isHtmlContent = builder.isHtmlContent;
        this.attachments = builder.attachments != null ? 
            Collections.unmodifiableList(builder.attachments) : Collections.emptyList();
        this.composedAt = builder.composedAt;
    }
    
    // Getters
    public String getCorrelationId() { return correlationId; }
    public String getFromAddress() { return fromAddress; }
    public String getFromName() { return fromName; }
    public List<String> getToAddresses() { return toAddresses; }
    public List<String> getCcAddresses() { return ccAddresses; }
    public List<String> getBccAddresses() { return bccAddresses; }
    public String getSubject() { return subject; }
    public String getBodyContent() { return bodyContent; }
    public boolean isHtmlContent() { return isHtmlContent; }
    public List<EmailAttachment> getAttachments() { return attachments; }
    public LocalDateTime getComposedAt() { return composedAt; }
    
    /**
     * Check if email has attachments.
     */
    public boolean hasAttachments() {
        return attachments != null && !attachments.isEmpty();
    }
    
    /**
     * Get total attachment size in bytes.
     */
    public long getTotalAttachmentSize() {
        return attachments.stream()
            .mapToLong(EmailAttachment::getSize)
            .sum();
    }
    
    /**
     * Get number of attachments.
     */
    public int getAttachmentCount() {
        return attachments.size();
    }
    
    /**
     * Check if email has CC recipients.
     */
    public boolean hasCcAddresses() {
        return ccAddresses != null && !ccAddresses.isEmpty();
    }
    
    /**
     * Check if email has BCC recipients.
     */
    public boolean hasBccAddresses() {
        return bccAddresses != null && !bccAddresses.isEmpty();
    }
    
    /**
     * Get total recipient count (TO + CC + BCC).
     */
    public int getTotalRecipientCount() {
        int count = toAddresses.size();
        if (ccAddresses != null) count += ccAddresses.size();
        if (bccAddresses != null) count += bccAddresses.size();
        return count;
    }
    
    /**
     * Create builder instance for constructing EmailComposition.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for EmailComposition following builder pattern.
     */
    public static class Builder {
        private String correlationId;
        private String fromAddress;
        private String fromName;
        private List<String> toAddresses;
        private List<String> ccAddresses;
        private List<String> bccAddresses;
        private String subject;
        private String bodyContent;
        private boolean isHtmlContent = false;
        private List<EmailAttachment> attachments;
        private LocalDateTime composedAt;
        
        private Builder() {}
        
        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }
        
        public Builder fromAddress(String fromAddress) {
            this.fromAddress = fromAddress;
            return this;
        }
        
        public Builder fromName(String fromName) {
            this.fromName = fromName;
            return this;
        }
        
        public Builder toAddresses(List<String> toAddresses) {
            this.toAddresses = toAddresses;
            return this;
        }
        
        public Builder ccAddresses(List<String> ccAddresses) {
            this.ccAddresses = ccAddresses;
            return this;
        }
        
        public Builder bccAddresses(List<String> bccAddresses) {
            this.bccAddresses = bccAddresses;
            return this;
        }
        
        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }
        
        public Builder bodyContent(String bodyContent) {
            this.bodyContent = bodyContent;
            return this;
        }
        
        public Builder isHtmlContent(boolean isHtmlContent) {
            this.isHtmlContent = isHtmlContent;
            return this;
        }
        
        public Builder attachments(List<EmailAttachment> attachments) {
            this.attachments = attachments;
            return this;
        }
        
        public Builder composedAt(LocalDateTime composedAt) {
            this.composedAt = composedAt;
            return this;
        }
        
        public EmailComposition build() {
            return new EmailComposition(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("EmailComposition{correlationId='%s', fromAddress='%s', toCount=%d, " +
                           "subject='%s', hasAttachments=%b, attachmentCount=%d, composedAt=%s}", 
                           correlationId, fromAddress, toAddresses.size(), subject, 
                           hasAttachments(), getAttachmentCount(), composedAt);
    }
}