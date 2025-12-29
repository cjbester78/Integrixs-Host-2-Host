package com.integrixs.adapters.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Email template processor for variable substitution and content generation
 */
public class EmailTemplateProcessor {
    
    private static final Logger log = LoggerFactory.getLogger(EmailTemplateProcessor.class);
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final EmailAdapterConfig config;
    private final String adapterName;
    
    public EmailTemplateProcessor(EmailAdapterConfig config, String adapterName) {
        this.config = config;
        this.adapterName = adapterName;
    }
    
    /**
     * Process template with variable substitution
     */
    public String processTemplate(String template, Map<String, Object> variables) {
        if (template == null || template.trim().isEmpty()) {
            return template;
        }
        
        String result = template;
        
        try {
            // First replace user-defined variables
            Map<String, String> templateVars = config.getTemplateVariables();
            if (templateVars != null && !templateVars.isEmpty()) {
                for (Map.Entry<String, String> entry : templateVars.entrySet()) {
                    String placeholder = "${" + entry.getKey() + "}";
                    result = result.replace(placeholder, entry.getValue());
                }
            }
            
            // Then replace operation-specific variables
            if (variables != null && !variables.isEmpty()) {
                for (Map.Entry<String, Object> entry : variables.entrySet()) {
                    String placeholder = "${" + entry.getKey() + "}";
                    String value = entry.getValue() != null ? entry.getValue().toString() : "";
                    result = result.replace(placeholder, value);
                }
            }
            
            // Finally replace system variables
            result = replaceSystemVariables(result);
            
            log.debug("Template processed successfully, variables replaced: {}", countVariables(template));
            
        } catch (Exception e) {
            log.error("Failed to process template: {}", e.getMessage(), e);
            // Return original template if processing fails
            result = template;
        }
        
        return result;
    }
    
    /**
     * Generate email subject with file processing context
     */
    public String generateSubject(String template, String fileName, String operation, String status) {
        Map<String, Object> variables = Map.of(
            "fileName", fileName != null ? fileName : "Unknown",
            "operation", operation != null ? operation : "Process",
            "status", status != null ? status : "Completed"
        );
        
        return processTemplate(template, variables);
    }
    
    /**
     * Generate email body with processing results
     */
    public String generateBody(String template, EmailProcessingContext context) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("fileName", context.getFileName());
        variables.put("fileSize", formatFileSize(context.getFileSize()));
        variables.put("operation", context.getOperation());
        variables.put("status", context.getStatus());
        variables.put("startTime", context.getStartTime().format(DATETIME_FORMATTER));
        variables.put("endTime", context.getEndTime() != null ? context.getEndTime().format(DATETIME_FORMATTER) : "In Progress");
        variables.put("duration", calculateDuration(context.getStartTime(), context.getEndTime()));
        variables.put("errorMessage", context.getErrorMessage() != null ? context.getErrorMessage() : "");
        variables.put("recordCount", context.getRecordCount());
        variables.put("successCount", context.getSuccessCount());
        variables.put("errorCount", context.getErrorCount());
        
        return processTemplate(template, variables);
    }
    
    /**
     * Generate notification email for system events
     */
    public String generateNotification(String template, String eventType, String message) {
        Map<String, Object> variables = Map.of(
            "eventType", eventType,
            "message", message,
            "severity", deriveSeverity(eventType)
        );
        
        return processTemplate(template, variables);
    }
    
    /**
     * Replace system variables with current values
     */
    private String replaceSystemVariables(String template) {
        LocalDateTime now = LocalDateTime.now();
        
        return template
            .replace("${timestamp}", now.format(DATETIME_FORMATTER))
            .replace("${date}", now.format(DATE_FORMATTER))
            .replace("${time}", now.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
            .replace("${adapter}", adapterName != null ? adapterName : "Unknown")
            .replace("${hostname}", getHostname())
            .replace("${year}", String.valueOf(now.getYear()))
            .replace("${month}", String.format("%02d", now.getMonthValue()))
            .replace("${day}", String.format("%02d", now.getDayOfMonth()));
    }
    
    /**
     * Count variables in template
     */
    private int countVariables(String template) {
        if (template == null) return 0;
        
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
    
    /**
     * Format file size in human readable format
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
    
    /**
     * Calculate duration between two timestamps
     */
    private String calculateDuration(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return "Unknown";
        }
        
        java.time.Duration duration = java.time.Duration.between(start, end);
        long seconds = duration.getSeconds();
        
        if (seconds < 60) {
            return seconds + " seconds";
        } else if (seconds < 3600) {
            return (seconds / 60) + " minutes " + (seconds % 60) + " seconds";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + " hours " + minutes + " minutes";
        }
    }
    
    /**
     * Derive severity from event type
     */
    private String deriveSeverity(String eventType) {
        if (eventType == null) return "INFO";
        
        String type = eventType.toUpperCase();
        if (type.contains("ERROR") || type.contains("FAIL")) {
            return "ERROR";
        } else if (type.contains("WARN")) {
            return "WARNING";
        } else if (type.contains("SUCCESS") || type.contains("COMPLETE")) {
            return "INFO";
        } else {
            return "INFO";
        }
    }
    
    /**
     * Get current hostname
     */
    private String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    /**
     * Validate template syntax
     */
    public boolean validateTemplate(String template) {
        if (template == null || template.trim().isEmpty()) {
            return true; // Empty templates are valid
        }
        
        try {
            Matcher matcher = VARIABLE_PATTERN.matcher(template);
            while (matcher.find()) {
                String variableName = matcher.group(1);
                if (variableName.trim().isEmpty()) {
                    log.warn("Empty variable name found in template");
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.error("Template validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Extract variables from template
     */
    public java.util.Set<String> extractVariables(String template) {
        java.util.Set<String> variables = new java.util.HashSet<>();
        
        if (template == null) {
            return variables;
        }
        
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        
        return variables;
    }
}