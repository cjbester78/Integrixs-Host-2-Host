package com.integrixs.adapters.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Email template service implementing strategy pattern for email template processing.
 * Separates template processing concerns from email composition and sending.
 * Part of Phase 3 email adapter refactoring following OOP principles.
 */
@Service
public class EmailTemplateService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailTemplateService.class);
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    
    private final Map<String, EmailTemplateStrategy> templateStrategies = new HashMap<>();
    
    public EmailTemplateService() {
        // Register built-in template strategies
        registerStrategy("standard", new StandardTemplateStrategy());
        registerStrategy("notification", new NotificationTemplateStrategy());
        registerStrategy("processing", new ProcessingTemplateStrategy());
    }
    
    /**
     * Process email template using specified strategy.
     */
    public EmailTemplateResult processTemplate(EmailTemplateRequest request) {
        if (request == null) {
            return EmailTemplateResult.failure("Template request cannot be null");
        }
        
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            // Get strategy for template processing
            EmailTemplateStrategy strategy = getTemplateStrategy(request.getStrategyType());
            
            // Process template with strategy
            EmailTemplateResult result = strategy.processTemplate(request);
            
            long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            logger.debug("Template processing completed in {} ms using strategy: {}", 
                        duration, request.getStrategyType());
            
            return result;
            
        } catch (Exception e) {
            long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            logger.error("Template processing failed: {}", e.getMessage(), e);
            return EmailTemplateResult.failure("Template processing failed: " + e.getMessage(), e, duration);
        }
    }
    
    /**
     * Process simple template with default strategy.
     */
    public EmailTemplateResult processTemplate(String template, Map<String, Object> variables) {
        EmailTemplateRequest request = EmailTemplateRequest.builder()
            .template(template)
            .variables(variables)
            .strategyType("standard")
            .build();
        
        return processTemplate(request);
    }
    
    /**
     * Validate template syntax.
     */
    public EmailTemplateValidationResult validateTemplate(String template) {
        if (template == null || template.trim().isEmpty()) {
            return new EmailTemplateValidationResult(true, Collections.emptyList(), 
                                                   Collections.singletonList("Empty template"));
        }
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> variables = new HashSet<>();
        
        try {
            Matcher matcher = VARIABLE_PATTERN.matcher(template);
            while (matcher.find()) {
                String variableName = matcher.group(1).trim();
                
                if (variableName.isEmpty()) {
                    errors.add("Empty variable name found: ${" + matcher.group(1) + "}");
                } else if (variables.contains(variableName)) {
                    warnings.add("Duplicate variable found: " + variableName);
                } else {
                    variables.add(variableName);
                }
            }
            
            if (variables.isEmpty()) {
                warnings.add("No template variables found");
            }
            
        } catch (Exception e) {
            errors.add("Template validation failed: " + e.getMessage());
        }
        
        return new EmailTemplateValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Extract variables from template.
     */
    public Set<String> extractVariables(String template) {
        Set<String> variables = new HashSet<>();
        
        if (template == null) {
            return variables;
        }
        
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            String variableName = matcher.group(1).trim();
            if (!variableName.isEmpty()) {
                variables.add(variableName);
            }
        }
        
        return variables;
    }
    
    /**
     * Register template processing strategy.
     */
    public void registerStrategy(String type, EmailTemplateStrategy strategy) {
        if (type != null && strategy != null) {
            templateStrategies.put(type.toLowerCase(), strategy);
            logger.debug("Registered email template strategy: {}", type);
        }
    }
    
    /**
     * Get template strategy by type.
     */
    private EmailTemplateStrategy getTemplateStrategy(String strategyType) {
        String type = strategyType != null ? strategyType.toLowerCase() : "standard";
        EmailTemplateStrategy strategy = templateStrategies.get(type);
        
        if (strategy == null) {
            logger.warn("Unknown template strategy: {}, using standard strategy", strategyType);
            strategy = templateStrategies.get("standard");
        }
        
        return strategy;
    }
    
    /**
     * Get available strategy types.
     */
    public Set<String> getAvailableStrategyTypes() {
        return new HashSet<>(templateStrategies.keySet());
    }
}

/**
 * Strategy interface for email template processing.
 */
interface EmailTemplateStrategy {
    EmailTemplateResult processTemplate(EmailTemplateRequest request);
    String getStrategyName();
    String getStrategyDescription();
}

/**
 * Standard template processing strategy.
 */
class StandardTemplateStrategy implements EmailTemplateStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(StandardTemplateStrategy.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @Override
    public EmailTemplateResult processTemplate(EmailTemplateRequest request) {
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            String template = request.getTemplate();
            if (template == null || template.trim().isEmpty()) {
                return EmailTemplateResult.success(template != null ? template : "", 0);
            }
            
            String result = template;
            List<String> warnings = new ArrayList<>();
            
            // Process user-defined variables
            Map<String, Object> variables = request.getVariables();
            if (variables != null) {
                for (Map.Entry<String, Object> entry : variables.entrySet()) {
                    String placeholder = "${" + entry.getKey() + "}";
                    String value = entry.getValue() != null ? entry.getValue().toString() : "";
                    result = result.replace(placeholder, value);
                }
            }
            
            // Process system variables
            result = processSystemVariables(result, request);
            
            long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            return EmailTemplateResult.success(result, duration, warnings, 0);
            
        } catch (Exception e) {
            long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            logger.error("Standard template processing failed: {}", e.getMessage(), e);
            return EmailTemplateResult.failure("Standard template processing failed: " + e.getMessage(), e, duration);
        }
    }
    
    private String processSystemVariables(String template, EmailTemplateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        
        String result = template
            .replace("${timestamp}", now.format(DATETIME_FORMATTER))
            .replace("${date}", now.format(DATE_FORMATTER))
            .replace("${time}", now.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
            .replace("${year}", String.valueOf(now.getYear()))
            .replace("${month}", String.format("%02d", now.getMonthValue()))
            .replace("${day}", String.format("%02d", now.getDayOfMonth()));
        
        // Add context-specific variables
        if (request.getAdapterName() != null) {
            result = result.replace("${adapter}", request.getAdapterName());
        }
        
        // Add hostname
        try {
            String hostname = java.net.InetAddress.getLocalHost().getHostName();
            result = result.replace("${hostname}", hostname);
        } catch (Exception e) {
            result = result.replace("${hostname}", "Unknown");
        }
        
        return result;
    }
    
    @Override
    public String getStrategyName() {
        return "Standard";
    }
    
    @Override
    public String getStrategyDescription() {
        return "Standard template processing with variable substitution and system variables";
    }
}

/**
 * Notification template processing strategy.
 */
class NotificationTemplateStrategy implements EmailTemplateStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationTemplateStrategy.class);
    
    @Override
    public EmailTemplateResult processTemplate(EmailTemplateRequest request) {
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            // Use standard processing as base
            StandardTemplateStrategy standardStrategy = new StandardTemplateStrategy();
            EmailTemplateResult baseResult = standardStrategy.processTemplate(request);
            
            if (!baseResult.isSuccessful()) {
                return baseResult;
            }
            
            String result = baseResult.getProcessedTemplate();
            List<String> warnings = new ArrayList<>(baseResult.getWarnings());
            
            // Add notification-specific processing
            result = addNotificationFormatting(result, request);
            
            long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            return EmailTemplateResult.success(result, duration, warnings, 0);
            
        } catch (Exception e) {
            long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            logger.error("Notification template processing failed: {}", e.getMessage(), e);
            return EmailTemplateResult.failure("Notification template processing failed: " + e.getMessage(), e, duration);
        }
    }
    
    private String addNotificationFormatting(String template, EmailTemplateRequest request) {
        // Add severity-based formatting
        Object severityObj = request.getVariables() != null ? request.getVariables().get("severity") : null;
        if (severityObj != null) {
            String severity = severityObj.toString().toUpperCase();
            String prefix = getSeverityPrefix(severity);
            if (!template.startsWith(prefix)) {
                template = prefix + template;
            }
        }
        
        return template;
    }
    
    private String getSeverityPrefix(String severity) {
        switch (severity) {
            case "ERROR":
                return "🔴 ERROR: ";
            case "WARNING":
                return "🟡 WARNING: ";
            case "INFO":
                return "🔵 INFO: ";
            default:
                return "📄 NOTICE: ";
        }
    }
    
    @Override
    public String getStrategyName() {
        return "Notification";
    }
    
    @Override
    public String getStrategyDescription() {
        return "Notification template processing with severity formatting and alerts";
    }
}

/**
 * Processing result template strategy.
 */
class ProcessingTemplateStrategy implements EmailTemplateStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(ProcessingTemplateStrategy.class);
    
    @Override
    public EmailTemplateResult processTemplate(EmailTemplateRequest request) {
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            // Use standard processing as base
            StandardTemplateStrategy standardStrategy = new StandardTemplateStrategy();
            EmailTemplateResult baseResult = standardStrategy.processTemplate(request);
            
            if (!baseResult.isSuccessful()) {
                return baseResult;
            }
            
            String result = baseResult.getProcessedTemplate();
            List<String> warnings = new ArrayList<>(baseResult.getWarnings());
            
            // Add processing-specific formatting
            result = addProcessingFormatting(result, request);
            
            long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            return EmailTemplateResult.success(result, duration, warnings, 0);
            
        } catch (Exception e) {
            long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            logger.error("Processing template processing failed: {}", e.getMessage(), e);
            return EmailTemplateResult.failure("Processing template processing failed: " + e.getMessage(), e, duration);
        }
    }
    
    private String addProcessingFormatting(String template, EmailTemplateRequest request) {
        Map<String, Object> variables = request.getVariables();
        if (variables == null) {
            return template;
        }
        
        // Format file sizes
        template = formatFileSizes(template, variables);
        
        // Format durations
        template = formatDurations(template, variables);
        
        return template;
    }
    
    private String formatFileSizes(String template, Map<String, Object> variables) {
        Object fileSizeObj = variables.get("fileSize");
        if (fileSizeObj instanceof Number) {
            long sizeInBytes = ((Number) fileSizeObj).longValue();
            String formattedSize = formatFileSize(sizeInBytes);
            template = template.replace("${fileSize}", formattedSize);
            template = template.replace("${fileSizeFormatted}", formattedSize);
        }
        
        return template;
    }
    
    private String formatDurations(String template, Map<String, Object> variables) {
        Object durationObj = variables.get("duration");
        if (durationObj instanceof String) {
            // Already formatted
            return template;
        } else if (durationObj instanceof Number) {
            long durationMs = ((Number) durationObj).longValue();
            String formattedDuration = formatDuration(durationMs);
            template = template.replace("${duration}", formattedDuration);
            template = template.replace("${durationFormatted}", formattedDuration);
        }
        
        return template;
    }
    
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
    
    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        
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
    
    @Override
    public String getStrategyName() {
        return "Processing";
    }
    
    @Override
    public String getStrategyDescription() {
        return "Processing result template with file size and duration formatting";
    }
}