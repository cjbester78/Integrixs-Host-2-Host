package com.integrixs.adapters.email;

import java.util.List;
import java.util.Map;

/**
 * Immutable request object for email attachment processing.
 * Contains attachment sources, processor configuration, and processing options.
 * Part of Phase 3 email adapter refactoring following OOP principles.
 */
public final class EmailAttachmentRequest {
    
    private final List<Object> attachmentSources;
    private final String processorType;
    private final Map<String, Object> processingOptions;
    private final boolean validateSize;
    private final boolean validateType;
    private final long maxIndividualSize;
    private final long maxTotalSize;
    
    private EmailAttachmentRequest(Builder builder) {
        this.attachmentSources = builder.attachmentSources;
        this.processorType = builder.processorType;
        this.processingOptions = builder.processingOptions;
        this.validateSize = builder.validateSize;
        this.validateType = builder.validateType;
        this.maxIndividualSize = builder.maxIndividualSize;
        this.maxTotalSize = builder.maxTotalSize;
    }
    
    // Getters
    public List<Object> getAttachmentSources() { return attachmentSources; }
    public String getProcessorType() { return processorType; }
    public Map<String, Object> getProcessingOptions() { return processingOptions; }
    public boolean isValidateSize() { return validateSize; }
    public boolean isValidateType() { return validateType; }
    public long getMaxIndividualSize() { return maxIndividualSize; }
    public long getMaxTotalSize() { return maxTotalSize; }
    
    /**
     * Get processing option value with type safety.
     */
    @SuppressWarnings("unchecked")
    public <T> T getProcessingOption(String key, Class<T> type, T defaultValue) {
        if (processingOptions == null || !processingOptions.containsKey(key)) {
            return defaultValue;
        }
        
        Object value = processingOptions.get(key);
        if (type.isInstance(value)) {
            return (T) value;
        }
        
        return defaultValue;
    }
    
    /**
     * Check if request has attachment sources.
     */
    public boolean hasAttachmentSources() {
        return attachmentSources != null && !attachmentSources.isEmpty();
    }
    
    /**
     * Get attachment source count.
     */
    public int getAttachmentSourceCount() {
        return attachmentSources != null ? attachmentSources.size() : 0;
    }
    
    /**
     * Check if request has processing options.
     */
    public boolean hasProcessingOptions() {
        return processingOptions != null && !processingOptions.isEmpty();
    }
    
    /**
     * Create builder instance for constructing EmailAttachmentRequest.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for EmailAttachmentRequest following builder pattern.
     */
    public static class Builder {
        private List<Object> attachmentSources;
        private String processorType = "memory";
        private Map<String, Object> processingOptions;
        private boolean validateSize = true;
        private boolean validateType = true;
        private long maxIndividualSize = 25 * 1024 * 1024; // 25MB
        private long maxTotalSize = 100 * 1024 * 1024; // 100MB
        
        private Builder() {}
        
        public Builder attachmentSources(List<Object> attachmentSources) {
            this.attachmentSources = attachmentSources;
            return this;
        }
        
        public Builder processorType(String processorType) {
            this.processorType = processorType;
            return this;
        }
        
        public Builder processingOptions(Map<String, Object> processingOptions) {
            this.processingOptions = processingOptions;
            return this;
        }
        
        public Builder validateSize(boolean validateSize) {
            this.validateSize = validateSize;
            return this;
        }
        
        public Builder validateType(boolean validateType) {
            this.validateType = validateType;
            return this;
        }
        
        public Builder maxIndividualSize(long maxIndividualSize) {
            this.maxIndividualSize = maxIndividualSize;
            return this;
        }
        
        public Builder maxTotalSize(long maxTotalSize) {
            this.maxTotalSize = maxTotalSize;
            return this;
        }
        
        public EmailAttachmentRequest build() {
            return new EmailAttachmentRequest(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("EmailAttachmentRequest{processorType='%s', sourceCount=%d, " +
                           "validateSize=%b, validateType=%b, maxIndividualSize=%d, maxTotalSize=%d}", 
                           processorType, getAttachmentSourceCount(), validateSize, validateType, 
                           maxIndividualSize, maxTotalSize);
    }
}