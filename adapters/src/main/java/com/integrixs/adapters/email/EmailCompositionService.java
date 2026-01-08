package com.integrixs.adapters.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Email composition service that separates email composition from sending logic.
 * Follows OOP principles with immutable composition results and proper encapsulation.
 * Part of Phase 3 email adapter refactoring to implement proper separation of concerns.
 */
@Service
public class EmailCompositionService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailCompositionService.class);
    
    /**
     * Compose email with attachments from flow context.
     * Creates immutable EmailComposition result with all email components properly structured.
     */
    public EmailCompositionResult composeEmailWithAttachments(EmailCompositionRequest request) {
        if (request == null) {
            return EmailCompositionResult.failure("Email composition request cannot be null");
        }
        
        LocalDateTime startTime = LocalDateTime.now();
        String correlationId = UUID.randomUUID().toString();
        
        logger.debug("Composing email - correlation: {}, subject: {}, recipients: {}, attachments: {}", 
                    correlationId, request.getSubject(), request.getRecipients().size(), 
                    request.getAttachments() != null ? request.getAttachments().size() : 0);
        
        try {
            // Validate composition request
            EmailCompositionValidationResult validation = validateCompositionRequest(request);
            if (!validation.isValid()) {
                return EmailCompositionResult.failure("Composition validation failed: " + 
                                                    String.join(", ", validation.getErrors()));
            }
            
            // Process attachments from flow context
            List<EmailAttachment> processedAttachments = processAttachmentsFromFlowContext(
                request.getAttachments(), correlationId);
            
            // Create immutable email composition
            EmailComposition composition = EmailComposition.builder()
                .correlationId(correlationId)
                .fromAddress(request.getFromAddress())
                .fromName(request.getFromName())
                .toAddresses(new ArrayList<>(request.getRecipients()))
                .ccAddresses(request.getCcAddresses() != null ? new ArrayList<>(request.getCcAddresses()) : null)
                .bccAddresses(request.getBccAddresses() != null ? new ArrayList<>(request.getBccAddresses()) : null)
                .subject(request.getSubject())
                .bodyContent(request.getBodyContent())
                .isHtmlContent(request.isHtmlContent())
                .attachments(processedAttachments)
                .composedAt(LocalDateTime.now())
                .build();
            
            long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            logger.info("Email composition completed - correlation: {} ({} ms, {} attachments)", 
                       correlationId, duration, processedAttachments.size());
            
            return EmailCompositionResult.success(composition, duration, 
                                                 processedAttachments.size() > 0 ? 
                                                 Collections.singletonList("Email composed with " + processedAttachments.size() + " attachments") : 
                                                 Collections.emptyList());
            
        } catch (Exception e) {
            long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            logger.error("Email composition failed - correlation: {}: {}", correlationId, e.getMessage(), e);
            return EmailCompositionResult.failure("Email composition failed: " + e.getMessage(), e, duration);
        }
    }
    
    /**
     * Compose notification email without attachments.
     */
    public EmailCompositionResult composeNotificationEmail(String fromAddress, String fromName, 
                                                          List<String> recipients, String subject, 
                                                          String bodyContent, boolean isHtml) {
        EmailCompositionRequest request = EmailCompositionRequest.builder()
            .fromAddress(fromAddress)
            .fromName(fromName)
            .recipients(recipients)
            .subject(subject)
            .bodyContent(bodyContent)
            .isHtmlContent(isHtml)
            .attachments(null)
            .build();
        
        return composeEmailWithAttachments(request);
    }
    
    /**
     * Validate email composition request.
     */
    private EmailCompositionValidationResult validateCompositionRequest(EmailCompositionRequest request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate from address
        if (request.getFromAddress() == null || request.getFromAddress().trim().isEmpty()) {
            errors.add("From address is required");
        } else if (!isValidEmailAddress(request.getFromAddress())) {
            errors.add("From address is not valid: " + request.getFromAddress());
        }
        
        // Validate recipients
        if (request.getRecipients() == null || request.getRecipients().isEmpty()) {
            errors.add("At least one recipient is required");
        } else {
            for (String recipient : request.getRecipients()) {
                if (recipient == null || recipient.trim().isEmpty()) {
                    warnings.add("Empty recipient address found");
                } else if (!isValidEmailAddress(recipient)) {
                    errors.add("Invalid recipient address: " + recipient);
                }
            }
        }
        
        // Validate CC addresses
        if (request.getCcAddresses() != null) {
            for (String ccAddress : request.getCcAddresses()) {
                if (ccAddress != null && !ccAddress.trim().isEmpty() && !isValidEmailAddress(ccAddress)) {
                    errors.add("Invalid CC address: " + ccAddress);
                }
            }
        }
        
        // Validate BCC addresses
        if (request.getBccAddresses() != null) {
            for (String bccAddress : request.getBccAddresses()) {
                if (bccAddress != null && !bccAddress.trim().isEmpty() && !isValidEmailAddress(bccAddress)) {
                    errors.add("Invalid BCC address: " + bccAddress);
                }
            }
        }
        
        // Validate subject (warning only)
        if (request.getSubject() == null || request.getSubject().trim().isEmpty()) {
            warnings.add("Email subject is empty");
        }
        
        // Validate body content (warning only)
        if (request.getBodyContent() == null || request.getBodyContent().trim().isEmpty()) {
            warnings.add("Email body content is empty");
        }
        
        return new EmailCompositionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Process attachments from flow context into immutable EmailAttachment objects.
     */
    private List<EmailAttachment> processAttachmentsFromFlowContext(List<Map<String, Object>> attachments, 
                                                                   String correlationId) {
        if (attachments == null || attachments.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<EmailAttachment> processedAttachments = new ArrayList<>();
        
        for (int i = 0; i < attachments.size(); i++) {
            Map<String, Object> attachment = attachments.get(i);
            
            try {
                String fileName = extractFileName(attachment, i);
                byte[] content = extractAttachmentContent(attachment);
                String contentType = determineContentType(fileName, attachment);
                
                if (fileName != null && content != null && content.length > 0) {
                    EmailAttachment emailAttachment = EmailAttachment.builder()
                        .fileName(fileName)
                        .content(Arrays.copyOf(content, content.length)) // Defensive copy
                        .contentType(contentType)
                        .size(content.length)
                        .attachmentId(correlationId + "_" + i)
                        .build();
                    
                    processedAttachments.add(emailAttachment);
                    logger.debug("Processed attachment: {} ({} bytes)", fileName, content.length);
                } else {
                    logger.warn("Skipping attachment {} due to missing fileName or content", i);
                }
                
            } catch (Exception e) {
                logger.warn("Failed to process attachment {}: {}", i, e.getMessage());
            }
        }
        
        logger.debug("Processed {} attachments from flow context", processedAttachments.size());
        return processedAttachments;
    }
    
    /**
     * Extract file name from attachment map with fallback generation.
     */
    private String extractFileName(Map<String, Object> attachment, int index) {
        // Try fileName first
        Object fileNameObj = attachment.get("fileName");
        if (fileNameObj instanceof String && !((String) fileNameObj).trim().isEmpty()) {
            return ((String) fileNameObj).trim();
        }
        
        // Try name as fallback
        Object nameObj = attachment.get("name");
        if (nameObj instanceof String && !((String) nameObj).trim().isEmpty()) {
            return ((String) nameObj).trim();
        }
        
        // Generate default name
        return "attachment_" + index + ".dat";
    }
    
    /**
     * Extract attachment content from various possible keys.
     */
    private byte[] extractAttachmentContent(Map<String, Object> attachment) {
        // Try content first
        Object contentObj = attachment.get("content");
        if (contentObj instanceof byte[]) {
            return (byte[]) contentObj;
        }
        
        // Try fileContent as fallback
        contentObj = attachment.get("fileContent");
        if (contentObj instanceof byte[]) {
            return (byte[]) contentObj;
        }
        
        // Try String content
        if (contentObj instanceof String) {
            return ((String) contentObj).getBytes();
        }
        
        return null;
    }
    
    /**
     * Determine content type for attachment.
     */
    private String determineContentType(String fileName, Map<String, Object> attachment) {
        // Check if explicitly provided
        Object contentTypeObj = attachment.get("contentType");
        if (contentTypeObj instanceof String && !((String) contentTypeObj).trim().isEmpty()) {
            return (String) contentTypeObj;
        }
        
        // Determine from file extension
        if (fileName != null) {
            String lowerFileName = fileName.toLowerCase();
            if (lowerFileName.endsWith(".pdf")) {
                return "application/pdf";
            } else if (lowerFileName.endsWith(".txt")) {
                return "text/plain";
            } else if (lowerFileName.endsWith(".csv")) {
                return "text/csv";
            } else if (lowerFileName.endsWith(".xml")) {
                return "application/xml";
            } else if (lowerFileName.endsWith(".json")) {
                return "application/json";
            } else if (lowerFileName.endsWith(".zip")) {
                return "application/zip";
            }
        }
        
        // Default to octet-stream
        return "application/octet-stream";
    }
    
    /**
     * Basic email address validation.
     */
    private boolean isValidEmailAddress(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        // Basic email validation - contains @ and dot after @
        String trimmed = email.trim();
        int atIndex = trimmed.indexOf('@');
        
        if (atIndex <= 0 || atIndex == trimmed.length() - 1) {
            return false;
        }
        
        String domain = trimmed.substring(atIndex + 1);
        return domain.contains(".") && !domain.startsWith(".") && !domain.endsWith(".");
    }
    
    /**
     * Immutable validation result for email composition requests.
     */
    public static class EmailCompositionValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        
        public EmailCompositionValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = Collections.unmodifiableList(new ArrayList<>(errors != null ? errors : Collections.emptyList()));
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings != null ? warnings : Collections.emptyList()));
        }
        
        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
    }
}