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
    
    @Value("${h2h.flow.export.format:H2H_FLOW_V3}")
    private String format;
    
    @Value("${h2h.flow.export.encryption-enabled:true}")
    private boolean encryptionEnabled;
    
    @Value("${h2h.flow.export.include-adapters:true}")
    private boolean includeAdapters;
    
    @Value("${h2h.flow.export.include-metrics:false}")
    private boolean includeMetrics;
    
    @Value("${h2h.flow.export.include-package-info:true}")
    private boolean includePackageInfo;
    
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
     * Check if package information should be included in exports (V3+ feature)
     */
    public boolean isIncludePackageInfo() {
        return includePackageInfo;
    }
    
    /**
     * Set whether to include package information in exports (V3+ feature)
     */
    public void setIncludePackageInfo(boolean includePackageInfo) {
        this.includePackageInfo = includePackageInfo;
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
    
    /**
     * Check if the current format supports package information (V3+)
     */
    public boolean supportsPackageInfo() {
        return format != null && (format.equals("H2H_FLOW_V3") || isNewerThanV3(format));
    }
    
    /**
     * Check if format is package-aware (V3 or later)
     */
    public boolean isPackageAwareFormat(String formatToCheck) {
        return "H2H_FLOW_V3".equals(formatToCheck) || isNewerThanV3(formatToCheck);
    }
    
    /**
     * Check if format version is newer than V3
     */
    private boolean isNewerThanV3(String formatToCheck) {
        if (formatToCheck == null || !formatToCheck.startsWith("H2H_FLOW_V")) {
            return false;
        }
        
        try {
            String versionPart = formatToCheck.substring("H2H_FLOW_V".length());
            int version = Integer.parseInt(versionPart);
            return version > 3;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}