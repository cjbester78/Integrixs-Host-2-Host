package com.integrixs.core.adapter.email;

import com.integrixs.core.adapter.AbstractAdapterExecutor;
import com.integrixs.core.util.AdapterConfigUtil;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.FlowExecutionStep;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Email Receiver Adapter - Sends files from flow context as email attachments.
 * 
 * Single Responsibility: Only send emails with file attachments from context data.
 * Gets file content from context (provided by sender adapter).
 * 
 * Note: This application does not support email sender adapters (email collection).
 * EMAIL adapters are receiver-only, handling outbound email delivery with attachments.
 */
public class EmailReceiverAdapter extends AbstractAdapterExecutor {
    
    @Override
    public String getSupportedType() {
        return "EMAIL";
    }
    
    @Override
    public String getSupportedDirection() {
        return "RECEIVER";
    }
    
    @Override
    protected Map<String, Object> executeInternal(Adapter adapter, Map<String, Object> context, 
                                                 FlowExecutionStep step) {
        
        logger.info("Direction: RECEIVER (sending email with attachments)");
        logger.info("Executing Email adapter receiver operation for: {}", adapter.getName());
        
        // Get files from flow context (not from directory!)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> filesToProcess = (List<Map<String, Object>>) 
            context.getOrDefault("filesToProcess", new ArrayList<>());
        
        if (filesToProcess.isEmpty()) {
            logger.warn("No files found in flow context for email adapter: {}", adapter.getName());
            return createSuccessResult(0, 0, 0L, "No files available to send via email");
        }
        
        logger.info("Email adapter processing {} files from flow context", filesToProcess.size());
        
        // Get and validate configuration
        Map<String, Object> config = adapter.getConfiguration();
        validateEmailReceiverConfiguration(config);
        
        String smtpHost = AdapterConfigUtil.getStringConfig(config, "smtpHost", true, null);
        Integer smtpPort = AdapterConfigUtil.getIntegerConfig(config, "smtpPort", false, 587);
        String smtpUsername = AdapterConfigUtil.getStringConfig(config, "smtpUsername", false, null);
        String smtpPassword = AdapterConfigUtil.getStringConfig(config, "smtpPassword", false, null);
        String fromAddress = AdapterConfigUtil.getStringConfig(config, "fromAddress", true, null);
        String subject = AdapterConfigUtil.getStringConfig(config, "subject", false, "File Transfer Notification");
        String bodyTemplate = AdapterConfigUtil.getStringConfig(config, "bodyTemplate", false, "Please find attached files for processing.");
        
        // Handle recipients - could be stored as String or List
        List<String> recipients = parseRecipients(config);
        
        logger.info("Configuration - SMTP Host: {}, Recipients: {}, Attachments: {}", 
                   smtpHost, recipients.size(), filesToProcess.size());
        
        try {
            // Calculate total bytes for tracking
            long totalBytes = 0;
            for (Map<String, Object> fileInfo : filesToProcess) {
                byte[] fileContent = (byte[]) fileInfo.get("fileContent");
                String fileName = (String) fileInfo.get("fileName");
                
                if (fileContent != null && fileName != null) {
                    totalBytes += fileContent.length;
                    if (step != null) {
                        step.addFileProcessed(fileName, "email_attachment", fileContent.length);
                    }
                }
            }
            
            // Use reflection to call external email adapter (avoids circular dependency)
            int successCount = sendEmailWithReflection(adapter, subject, bodyTemplate, recipients, filesToProcess);
            
            // Create success result
            Map<String, Object> result = createSuccessResult(successCount, 0, totalBytes,
                String.format("Email receiver completed: %d emails sent with %d attachments each", 
                             successCount, filesToProcess.size()));
            
            result.put("smtpHost", smtpHost);
            result.put("recipients", recipients);
            result.put("emailsSent", successCount);
            result.put("emailsFailed", 0);
            result.put("attachmentsSent", filesToProcess.size());
            
            logger.info("✓ Emails sent: {} recipients", successCount);
            logger.info("✓ Total bytes sent: {}", totalBytes);
            logger.info("✓ Total attachments: {}", filesToProcess.size());
            
            return result;
            
        } catch (Exception e) {
            logger.error("Email receiver execution failed: {}", e.getMessage(), e);
            throw new RuntimeException("Email receiver execution failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void validateConfiguration(Adapter adapter) {
        // Call parent validation
        super.validateConfiguration(adapter);
        
        Map<String, Object> config = adapter.getConfiguration();
        validateEmailReceiverConfiguration(config);
        
        AdapterConfigUtil.logConfigSummary(config, getSupportedType(), getSupportedDirection());
    }
    
    /**
     * Validate Email receiver specific configuration.
     * Requires SMTP details and recipient information.
     */
    private void validateEmailReceiverConfiguration(Map<String, Object> config) {
        String adapterType = getSupportedType() + " " + getSupportedDirection();
        
        // Email receiver needs SMTP configuration
        AdapterConfigUtil.validateRequiredString(config, "smtpHost", adapterType);
        AdapterConfigUtil.validateRequiredString(config, "fromAddress", adapterType);
        
        // Validate recipients
        Object recipientsObj = config.get("recipients");
        if (recipientsObj == null) {
            throw new IllegalArgumentException(adapterType + " recipients configuration is required");
        }
        
        List<String> recipients = parseRecipients(config);
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException(adapterType + " must have at least one recipient email address");
        }
        
        // Validate SMTP port if present
        Integer smtpPort = AdapterConfigUtil.getIntegerConfig(config, "smtpPort", false, 587);
        if (smtpPort != null && (smtpPort < 1 || smtpPort > 65535)) {
            throw new IllegalArgumentException(adapterType + " smtpPort must be between 1 and 65535");
        }
        
        logger.debug("Email receiver configuration validation passed");
    }
    
    /**
     * Parse recipients configuration from either String or List format.
     */
    private List<String> parseRecipients(Map<String, Object> config) {
        Object recipientsObj = config.get("recipients");
        
        if (recipientsObj instanceof String) {
            // If stored as comma-separated string, split it
            String recipientStr = (String) recipientsObj;
            return Arrays.stream(recipientStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        } else if (recipientsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> recipientList = (List<String>) recipientsObj;
            return recipientList;
        } else {
            throw new IllegalArgumentException("Recipients configuration must be either a string or list of email addresses");
        }
    }
    
    /**
     * Send email using reflection to call external email adapter classes.
     * This avoids circular dependencies while maintaining the existing email functionality.
     */
    private int sendEmailWithReflection(Adapter adapter, String subject, String bodyTemplate, 
                                       List<String> recipients, List<Map<String, Object>> filesToProcess) throws Exception {
        
        try {
            // Use reflection to create email adapter instance (avoids circular dependency)
            Class<?> emailAdapterClass = Class.forName("com.integrixs.adapters.email.EmailAdapter");
            Object emailAdapter = emailAdapterClass.getConstructor(Adapter.class).newInstance(adapter);
            
            // Initialize adapter
            emailAdapterClass.getMethod("initialize").invoke(emailAdapter);
            
            // Get email configuration using reflection
            Object emailConfig = emailAdapterClass.getMethod("getConfig").invoke(emailAdapter);
            Class<?> configClass = Class.forName("com.integrixs.adapters.email.EmailAdapterConfig");
            
            // Get email details from configuration or use defaults
            String emailSubject = (String) configClass.getMethod("getSubjectTemplate").invoke(emailConfig);
            String emailBody = (String) configClass.getMethod("getBodyTemplate").invoke(emailConfig);
            @SuppressWarnings("unchecked")
            List<String> toAddresses = (List<String>) configClass.getMethod("getToAddresses").invoke(emailConfig);
            
            // Ensure subject and body are never null - provide defaults if needed
            String finalSubject = (emailSubject != null && !emailSubject.trim().isEmpty()) ? emailSubject : subject;
            String finalBody = (emailBody != null && !emailBody.trim().isEmpty()) ? emailBody : bodyTemplate;
            List<String> finalRecipients = (toAddresses != null && !toAddresses.isEmpty()) ? toAddresses : recipients;
            
            // Send email with files from flow context using reflection
            Object emailResult = emailAdapterClass.getMethod("sendEmailFromFlow", String.class, String.class, List.class, List.class)
                .invoke(emailAdapter, finalSubject, finalBody, finalRecipients, filesToProcess);
            
            // Get status from result
            Class<?> resultClass = Class.forName("com.integrixs.adapters.email.EmailOperationResult");
            Object status = resultClass.getMethod("getStatus").invoke(emailResult);
            
            Class<?> statusEnum = Class.forName("com.integrixs.adapters.email.EmailOperationStatus");
            Object successStatus = statusEnum.getField("SUCCESS").get(null);
            
            if (status.equals(successStatus)) {
                logger.info("Email sent successfully to {} recipients with {} attachments from flow context", 
                           finalRecipients.size(), filesToProcess.size());
                return finalRecipients.size();
            } else {
                String error = (String) resultClass.getMethod("getError").invoke(emailResult);
                String errorMessage = (error != null && !error.trim().isEmpty()) ? error : "Unknown email sending error";
                logger.error("Email sending failed: {}", errorMessage);
                throw new RuntimeException("Email sending failed: " + errorMessage);
            }
            
        } catch (ClassNotFoundException e) {
            logger.error("Email adapter classes not available: {}", e.getMessage());
            throw new RuntimeException("Email adapter not available: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Email adapter execution failed via reflection: {}", e.getMessage(), e);
            throw new RuntimeException("Email adapter execution failed: " + e.getMessage(), e);
        }
    }
}