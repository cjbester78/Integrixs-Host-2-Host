package com.integrixs.adapters.email;

import java.util.List;
import java.util.Map;

/**
 * Immutable request object for email composition.
 * Contains all necessary information to compose an email with attachments.
 * Part of Phase 3 email adapter refactoring following OOP principles.
 */
public final class EmailCompositionRequest {
    
    private final String fromAddress;
    private final String fromName;
    private final List<String> recipients;
    private final List<String> ccAddresses;
    private final List<String> bccAddresses;
    private final String subject;
    private final String bodyContent;
    private final boolean isHtmlContent;
    private final List<Map<String, Object>> attachments;
    
    private EmailCompositionRequest(Builder builder) {
        this.fromAddress = builder.fromAddress;
        this.fromName = builder.fromName;
        this.recipients = builder.recipients;
        this.ccAddresses = builder.ccAddresses;
        this.bccAddresses = builder.bccAddresses;
        this.subject = builder.subject;
        this.bodyContent = builder.bodyContent;
        this.isHtmlContent = builder.isHtmlContent;
        this.attachments = builder.attachments;
    }
    
    // Getters
    public String getFromAddress() { return fromAddress; }
    public String getFromName() { return fromName; }
    public List<String> getRecipients() { return recipients; }
    public List<String> getCcAddresses() { return ccAddresses; }
    public List<String> getBccAddresses() { return bccAddresses; }
    public String getSubject() { return subject; }
    public String getBodyContent() { return bodyContent; }
    public boolean isHtmlContent() { return isHtmlContent; }
    public List<Map<String, Object>> getAttachments() { return attachments; }
    
    /**
     * Create builder instance for constructing EmailCompositionRequest.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for EmailCompositionRequest following builder pattern.
     */
    public static class Builder {
        private String fromAddress;
        private String fromName;
        private List<String> recipients;
        private List<String> ccAddresses;
        private List<String> bccAddresses;
        private String subject;
        private String bodyContent;
        private boolean isHtmlContent = false;
        private List<Map<String, Object>> attachments;
        
        private Builder() {}
        
        public Builder fromAddress(String fromAddress) {
            this.fromAddress = fromAddress;
            return this;
        }
        
        public Builder fromName(String fromName) {
            this.fromName = fromName;
            return this;
        }
        
        public Builder recipients(List<String> recipients) {
            this.recipients = recipients;
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
        
        public Builder attachments(List<Map<String, Object>> attachments) {
            this.attachments = attachments;
            return this;
        }
        
        public EmailCompositionRequest build() {
            return new EmailCompositionRequest(this);
        }
    }
}