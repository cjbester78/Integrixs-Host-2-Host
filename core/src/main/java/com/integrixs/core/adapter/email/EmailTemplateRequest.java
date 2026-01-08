package com.integrixs.core.adapter.email;

import java.util.Map;

/**
 * Immutable request object for email template processing.
 * Contains template content, variables, and processing strategy configuration.
 * Part of Phase 3 email adapter refactoring following OOP principles.
 */
public final class EmailTemplateRequest {
    
    private final String template;
    private final Map<String, Object> variables;
    private final String strategyType;
    private final String adapterName;
    private final Map<String, Object> additionalContext;
    
    private EmailTemplateRequest(Builder builder) {
        this.template = builder.template;
        this.variables = builder.variables;
        this.strategyType = builder.strategyType;
        this.adapterName = builder.adapterName;
        this.additionalContext = builder.additionalContext;
    }
    
    // Getters
    public String getTemplate() { return template; }
    public Map<String, Object> getVariables() { return variables; }
    public String getStrategyType() { return strategyType; }
    public String getAdapterName() { return adapterName; }
    public Map<String, Object> getAdditionalContext() { return additionalContext; }
    
    /**
     * Get variable value by key with type safety.
     */
    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key, Class<T> type, T defaultValue) {
        if (variables == null || !variables.containsKey(key)) {
            return defaultValue;
        }
        
        Object value = variables.get(key);
        if (type.isInstance(value)) {
            return (T) value;
        }
        
        return defaultValue;
    }
    
    /**
     * Get variable value as string.
     */
    public String getVariableAsString(String key, String defaultValue) {
        Object value = variables != null ? variables.get(key) : null;
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * Check if template has content.
     */
    public boolean hasTemplate() {
        return template != null && !template.trim().isEmpty();
    }
    
    /**
     * Check if request has variables.
     */
    public boolean hasVariables() {
        return variables != null && !variables.isEmpty();
    }
    
    /**
     * Create builder instance for constructing EmailTemplateRequest.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for EmailTemplateRequest following builder pattern.
     */
    public static class Builder {
        private String template;
        private Map<String, Object> variables;
        private String strategyType = "standard";
        private String adapterName;
        private Map<String, Object> additionalContext;
        
        private Builder() {}
        
        public Builder template(String template) {
            this.template = template;
            return this;
        }
        
        public Builder variables(Map<String, Object> variables) {
            this.variables = variables;
            return this;
        }
        
        public Builder strategyType(String strategyType) {
            this.strategyType = strategyType;
            return this;
        }
        
        public Builder adapterName(String adapterName) {
            this.adapterName = adapterName;
            return this;
        }
        
        public Builder additionalContext(Map<String, Object> additionalContext) {
            this.additionalContext = additionalContext;
            return this;
        }
        
        public EmailTemplateRequest build() {
            return new EmailTemplateRequest(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("EmailTemplateRequest{strategyType='%s', adapterName='%s', " +
                           "hasTemplate=%b, variableCount=%d}", 
                           strategyType, adapterName, hasTemplate(), 
                           variables != null ? variables.size() : 0);
    }
}