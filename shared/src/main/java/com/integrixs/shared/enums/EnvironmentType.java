package com.integrixs.shared.enums;

/**
 * Environment types for the H2H system
 * Controls feature availability and operational restrictions
 */
public enum EnvironmentType {
    
    DEVELOPMENT("Development", "All functionality enabled for development", "text-blue-500", "bg-blue-100"),
    QUALITY_ASSURANCE("Quality Assurance", "Limited to adapter configuration, import/export, and deployment", "text-yellow-500", "bg-yellow-100"), 
    PRODUCTION("Production", "Limited to adapter configuration, import/export, and deployment", "text-red-500", "bg-red-100");
    
    private final String displayName;
    private final String description;
    private final String textColorClass;
    private final String backgroundColorClass;
    
    EnvironmentType(String displayName, String description, String textColorClass, String backgroundColorClass) {
        this.displayName = displayName;
        this.description = description;
        this.textColorClass = textColorClass;
        this.backgroundColorClass = backgroundColorClass;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getTextColorClass() {
        return textColorClass;
    }
    
    public String getBackgroundColorClass() {
        return backgroundColorClass;
    }
    
    /**
     * Check if flow creation is allowed in this environment
     */
    public boolean canCreateFlows() {
        return this == DEVELOPMENT;
    }
    
    /**
     * Check if adapter creation is allowed in this environment
     */
    public boolean canCreateAdapters() {
        return this == DEVELOPMENT;
    }
    
    /**
     * Check if adapter configuration can be modified in this environment
     */
    public boolean canModifyAdapterConfig() {
        return true; // All environments allow adapter config modification
    }
    
    /**
     * Check if flow import/export is allowed in this environment
     */
    public boolean canImportExportFlows() {
        return true; // All environments allow import/export
    }
    
    /**
     * Check if flow deployment is allowed in this environment
     */
    public boolean canDeployFlows() {
        return true; // All environments allow deployment
    }
    
    /**
     * Get environment type from string value
     */
    public static EnvironmentType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DEVELOPMENT; // Default
        }
        
        try {
            return EnvironmentType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return DEVELOPMENT; // Fallback to development
        }
    }
}