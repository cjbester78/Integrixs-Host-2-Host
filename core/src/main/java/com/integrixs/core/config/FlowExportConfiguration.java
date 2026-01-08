package com.integrixs.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration for flow export/import operations.
 * Follows OOP principles:
 * - Single Responsibility: Only handles export configuration
 * - Dependency Injection: Injectable and configurable via application.yml
 * - Encapsulation: Configuration validation within the class
 * - Testability: Easy to mock for unit tests
 */
@Component
public class FlowExportConfiguration {
    
    @Value("${h2h.flow.export.format:H2H_FLOW_V1}")
    private String format;
    
    @Value("${h2h.flow.export.encryption-enabled:true}")
    private boolean encryptionEnabled;
    
    @Value("${h2h.flow.export.include-adapters:true}")
    private boolean includeAdapters;
    
    @Value("${h2h.flow.export.include-metrics:false}")
    private boolean includeMetrics;
    
    /**
     * Get the current export format version
     */
    public String getFormat() {
        return format;
    }
    
    /**
     * Set the export format version
     */
    public void setFormat(String format) {
        if (format == null || format.trim().isEmpty()) {
            throw new IllegalArgumentException("Export format cannot be null or empty");
        }
        this.format = format;
    }
    
    /**
     * Check if encryption is enabled for exports
     */
    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }
    
    /**
     * Enable or disable encryption for exports
     */
    public void setEncryptionEnabled(boolean encryptionEnabled) {
        this.encryptionEnabled = encryptionEnabled;
    }
    
    /**
     * Check if adapters should be included in exports
     */
    public boolean isIncludeAdapters() {
        return includeAdapters;
    }
    
    /**
     * Set whether to include adapters in exports
     */
    public void setIncludeAdapters(boolean includeAdapters) {
        this.includeAdapters = includeAdapters;
    }
    
    /**
     * Check if metrics should be included in exports
     */
    public boolean isIncludeMetrics() {
        return includeMetrics;
    }
    
    /**
     * Set whether to include metrics in exports
     */
    public void setIncludeMetrics(boolean includeMetrics) {
        this.includeMetrics = includeMetrics;
    }
    
    /**
     * Validate the current configuration
     */
    public void validate() {
        if (format == null || format.trim().isEmpty()) {
            throw new IllegalStateException("Export format must be configured");
        }
        
        if (!format.startsWith("H2H_FLOW_V")) {
            throw new IllegalStateException("Export format must follow H2H_FLOW_V* pattern");
        }
    }
    
    /**
     * Check if a format version is supported
     */
    public boolean isValidFormat(String formatToCheck) {
        return format.equals(formatToCheck);
    }
}