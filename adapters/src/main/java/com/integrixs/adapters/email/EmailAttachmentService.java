package com.integrixs.adapters.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Email attachment handling service with proper OOP patterns.
 * Manages attachment processing, validation, and memory optimization.
 * Part of Phase 3 email adapter refactoring following OOP principles.
 */
@Service
public class EmailAttachmentService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailAttachmentService.class);
    
    private static final long MAX_ATTACHMENT_SIZE = 25 * 1024 * 1024; // 25MB
    private static final long MAX_TOTAL_ATTACHMENTS_SIZE = 100 * 1024 * 1024; // 100MB
    private static final int MAX_ATTACHMENT_COUNT = 50;
    
    private final Map<String, AttachmentProcessor> processors = new ConcurrentHashMap<>();
    
    public EmailAttachmentService() {
        // Register built-in attachment processors
        registerProcessor("memory", new MemoryAttachmentProcessor());
        registerProcessor("file", new FileAttachmentProcessor());
        registerProcessor("flow", new FlowContextAttachmentProcessor());
    }
    
    /**
     * Process attachments from various sources with validation and optimization.
     */
    public EmailAttachmentProcessingResult processAttachments(EmailAttachmentRequest request) {
        if (request == null) {
            return EmailAttachmentProcessingResult.failure("Attachment request cannot be null");
        }
        
        LocalDateTime startTime = LocalDateTime.now();
        String correlationId = UUID.randomUUID().toString();
        
        logger.debug("Processing attachments - correlation: {}, processor: {}, sourceCount: {}", 
                    correlationId, request.getProcessorType(), 
                    request.getAttachmentSources() != null ? request.getAttachmentSources().size() : 0);
        
        try {
            // Get appropriate processor
            AttachmentProcessor processor = getAttachmentProcessor(request.getProcessorType());
            
            // Validate attachment request
            EmailAttachmentValidationResult validation = validateAttachmentRequest(request);
            if (!validation.isValid()) {
                return EmailAttachmentProcessingResult.failure("Attachment validation failed: " + 
                                                             String.join(", ", validation.getErrors()));
            }
            
            // Process attachments
            List<EmailAttachment> processedAttachments = new ArrayList<>();
            List<String> warnings = new ArrayList<>(validation.getWarnings());
            long totalSize = 0;
            
            for (int i = 0; i < request.getAttachmentSources().size(); i++) {
                Object source = request.getAttachmentSources().get(i);
                
                try {
                    EmailAttachment attachment = processor.processAttachment(source, correlationId + "_" + i, request);
                    
                    if (attachment != null && attachment.hasContent()) {
                        // Validate individual attachment
                        EmailAttachmentValidationResult attachmentValidation = validateIndividualAttachment(attachment);
                        
                        if (attachmentValidation.isValid()) {
                            processedAttachments.add(attachment);
                            totalSize += attachment.getSize();
                            logger.debug("Processed attachment: {} ({} bytes)", 
                                        attachment.getFileName(), attachment.getSize());
                        } else {
                            warnings.addAll(attachmentValidation.getErrors());
                            warnings.add("Skipped invalid attachment: " + (attachment.getFileName() != null ? 
                                        attachment.getFileName() : "attachment_" + i));
                        }
                    } else {
                        warnings.add("Skipped empty attachment at index " + i);
                    }
                    
                } catch (Exception e) {
                    logger.warn("Failed to process attachment {}: {}", i, e.getMessage());
                    warnings.add("Failed to process attachment " + i + ": " + e.getMessage());
                }
            }
            
            // Final validation of total size
            if (totalSize > MAX_TOTAL_ATTACHMENTS_SIZE) {
                return EmailAttachmentProcessingResult.failure("Total attachment size (" + 
                                                             formatFileSize(totalSize) + 
                                                             ") exceeds maximum allowed (" + 
                                                             formatFileSize(MAX_TOTAL_ATTACHMENTS_SIZE) + ")");
            }
            
            long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            logger.info("Attachment processing completed - correlation: {} ({} attachments, {} total size, {} ms)", 
                       correlationId, processedAttachments.size(), formatFileSize(totalSize), duration);
            
            return EmailAttachmentProcessingResult.success(processedAttachments, totalSize, duration, warnings);
            
        } catch (Exception e) {
            long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            logger.error("Attachment processing failed - correlation: {}: {}", correlationId, e.getMessage(), e);
            return EmailAttachmentProcessingResult.failure("Attachment processing failed: " + e.getMessage(), e, duration);
        }
    }
    
    /**
     * Process single attachment from file path.
     */
    public EmailAttachmentProcessingResult processFileAttachment(String filePath) {
        List<Object> sources = Collections.singletonList(filePath);
        EmailAttachmentRequest request = EmailAttachmentRequest.builder()
            .attachmentSources(sources)
            .processorType("file")
            .build();
        
        return processAttachments(request);
    }
    
    /**
     * Process attachments from flow context data.
     */
    public EmailAttachmentProcessingResult processFlowAttachments(List<Map<String, Object>> flowData) {
        List<Object> sources = new ArrayList<>(flowData);
        EmailAttachmentRequest request = EmailAttachmentRequest.builder()
            .attachmentSources(sources)
            .processorType("flow")
            .build();
        
        return processAttachments(request);
    }
    
    /**
     * Validate attachment request.
     */
    private EmailAttachmentValidationResult validateAttachmentRequest(EmailAttachmentRequest request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (request.getAttachmentSources() == null || request.getAttachmentSources().isEmpty()) {
            errors.add("No attachment sources provided");
            return new EmailAttachmentValidationResult(false, errors, warnings);
        }
        
        if (request.getAttachmentSources().size() > MAX_ATTACHMENT_COUNT) {
            errors.add("Too many attachments: " + request.getAttachmentSources().size() + 
                      " exceeds maximum " + MAX_ATTACHMENT_COUNT);
        }
        
        if (request.getProcessorType() == null || request.getProcessorType().trim().isEmpty()) {
            warnings.add("No processor type specified, using default 'memory' processor");
        }
        
        return new EmailAttachmentValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Validate individual attachment.
     */
    private EmailAttachmentValidationResult validateIndividualAttachment(EmailAttachment attachment) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (attachment.getFileName() == null || attachment.getFileName().trim().isEmpty()) {
            errors.add("Attachment filename is required");
        }
        
        if (!attachment.hasContent()) {
            errors.add("Attachment content is empty");
        }
        
        if (attachment.getSize() > MAX_ATTACHMENT_SIZE) {
            errors.add("Attachment size (" + formatFileSize(attachment.getSize()) + 
                      ") exceeds maximum allowed (" + formatFileSize(MAX_ATTACHMENT_SIZE) + ")");
        }
        
        if (attachment.getContentType() == null || attachment.getContentType().trim().isEmpty()) {
            warnings.add("Content type not specified for attachment: " + attachment.getFileName());
        }
        
        return new EmailAttachmentValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Register attachment processor.
     */
    public void registerProcessor(String type, AttachmentProcessor processor) {
        if (type != null && processor != null) {
            processors.put(type.toLowerCase(), processor);
            logger.debug("Registered email attachment processor: {}", type);
        }
    }
    
    /**
     * Get attachment processor by type.
     */
    private AttachmentProcessor getAttachmentProcessor(String processorType) {
        String type = processorType != null ? processorType.toLowerCase() : "memory";
        AttachmentProcessor processor = processors.get(type);
        
        if (processor == null) {
            logger.warn("Unknown attachment processor: {}, using memory processor", processorType);
            processor = processors.get("memory");
        }
        
        return processor;
    }
    
    /**
     * Format file size in human-readable format.
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
     * Get available processor types.
     */
    public Set<String> getAvailableProcessorTypes() {
        return new HashSet<>(processors.keySet());
    }
}

/**
 * Interface for attachment processing strategies.
 */
interface AttachmentProcessor {
    EmailAttachment processAttachment(Object source, String attachmentId, EmailAttachmentRequest request);
    String getProcessorName();
    String getProcessorDescription();
}

/**
 * Memory-based attachment processor.
 */
class MemoryAttachmentProcessor implements AttachmentProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(MemoryAttachmentProcessor.class);
    
    @Override
    public EmailAttachment processAttachment(Object source, String attachmentId, EmailAttachmentRequest request) {
        if (source instanceof byte[]) {
            byte[] content = (byte[]) source;
            String fileName = "attachment_" + attachmentId + ".dat";
            
            return EmailAttachment.builder()
                .fileName(fileName)
                .content(content)
                .contentType("application/octet-stream")
                .size(content.length)
                .attachmentId(attachmentId)
                .build();
                
        } else if (source instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sourceMap = (Map<String, Object>) source;
            
            String fileName = extractStringValue(sourceMap, "fileName", "attachment_" + attachmentId + ".dat");
            byte[] content = extractByteContent(sourceMap);
            String contentType = extractStringValue(sourceMap, "contentType", "application/octet-stream");
            
            if (content != null && content.length > 0) {
                return EmailAttachment.builder()
                    .fileName(fileName)
                    .content(content)
                    .contentType(contentType)
                    .size(content.length)
                    .attachmentId(attachmentId)
                    .build();
            }
        }
        
        logger.warn("Cannot process attachment source of type: {}", source != null ? source.getClass().getName() : "null");
        return null;
    }
    
    private String extractStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }
    
    private byte[] extractByteContent(Map<String, Object> map) {
        Object content = map.get("content");
        if (content instanceof byte[]) {
            return (byte[]) content;
        }
        
        content = map.get("fileContent");
        if (content instanceof byte[]) {
            return (byte[]) content;
        }
        
        if (content instanceof String) {
            return ((String) content).getBytes();
        }
        
        return null;
    }
    
    @Override
    public String getProcessorName() {
        return "Memory";
    }
    
    @Override
    public String getProcessorDescription() {
        return "Process attachments from memory-based sources (byte arrays, maps)";
    }
}

/**
 * File-based attachment processor.
 */
class FileAttachmentProcessor implements AttachmentProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(FileAttachmentProcessor.class);
    
    @Override
    public EmailAttachment processAttachment(Object source, String attachmentId, EmailAttachmentRequest request) {
        String filePath = null;
        
        if (source instanceof String) {
            filePath = (String) source;
        } else if (source instanceof Path) {
            filePath = ((Path) source).toString();
        } else if (source instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sourceMap = (Map<String, Object>) source;
            filePath = (String) sourceMap.get("filePath");
        }
        
        if (filePath == null || filePath.trim().isEmpty()) {
            logger.warn("No file path provided for attachment: {}", attachmentId);
            return null;
        }
        
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                logger.warn("File does not exist: {}", filePath);
                return null;
            }
            
            byte[] content = Files.readAllBytes(path);
            String fileName = path.getFileName().toString();
            String contentType = determineContentType(fileName);
            
            return EmailAttachment.builder()
                .fileName(fileName)
                .content(content)
                .contentType(contentType)
                .size(content.length)
                .attachmentId(attachmentId)
                .build();
                
        } catch (IOException e) {
            logger.error("Failed to read file {}: {}", filePath, e.getMessage());
            return null;
        }
    }
    
    private String determineContentType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".pdf")) return "application/pdf";
        if (lowerName.endsWith(".txt")) return "text/plain";
        if (lowerName.endsWith(".csv")) return "text/csv";
        if (lowerName.endsWith(".xml")) return "application/xml";
        if (lowerName.endsWith(".json")) return "application/json";
        if (lowerName.endsWith(".zip")) return "application/zip";
        
        return "application/octet-stream";
    }
    
    @Override
    public String getProcessorName() {
        return "File";
    }
    
    @Override
    public String getProcessorDescription() {
        return "Process attachments from file system paths";
    }
}

/**
 * Flow context attachment processor.
 */
class FlowContextAttachmentProcessor implements AttachmentProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowContextAttachmentProcessor.class);
    
    @Override
    public EmailAttachment processAttachment(Object source, String attachmentId, EmailAttachmentRequest request) {
        if (!(source instanceof Map)) {
            logger.warn("Flow context attachment source must be a Map, got: {}", 
                       source != null ? source.getClass().getName() : "null");
            return null;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> flowData = (Map<String, Object>) source;
        
        // Extract file information from flow context
        String fileName = extractFileName(flowData);
        byte[] content = extractContent(flowData);
        String contentType = extractContentType(flowData, fileName);
        
        if (fileName == null || content == null || content.length == 0) {
            logger.warn("Invalid flow context attachment data - fileName: {}, contentLength: {}", 
                       fileName, content != null ? content.length : 0);
            return null;
        }
        
        return EmailAttachment.builder()
            .fileName(fileName)
            .content(content)
            .contentType(contentType)
            .size(content.length)
            .attachmentId(attachmentId)
            .build();
    }
    
    private String extractFileName(Map<String, Object> flowData) {
        // Try various possible keys for filename
        Object fileName = flowData.get("fileName");
        if (fileName instanceof String && !((String) fileName).trim().isEmpty()) {
            return (String) fileName;
        }
        
        fileName = flowData.get("name");
        if (fileName instanceof String && !((String) fileName).trim().isEmpty()) {
            return (String) fileName;
        }
        
        fileName = flowData.get("originalFileName");
        if (fileName instanceof String && !((String) fileName).trim().isEmpty()) {
            return (String) fileName;
        }
        
        return null;
    }
    
    private byte[] extractContent(Map<String, Object> flowData) {
        // Try various possible keys for content
        Object content = flowData.get("content");
        if (content instanceof byte[]) {
            return (byte[]) content;
        }
        
        content = flowData.get("fileContent");
        if (content instanceof byte[]) {
            return (byte[]) content;
        }
        
        content = flowData.get("data");
        if (content instanceof byte[]) {
            return (byte[]) content;
        }
        
        if (content instanceof String) {
            return ((String) content).getBytes();
        }
        
        return null;
    }
    
    private String extractContentType(Map<String, Object> flowData, String fileName) {
        Object contentType = flowData.get("contentType");
        if (contentType instanceof String && !((String) contentType).trim().isEmpty()) {
            return (String) contentType;
        }
        
        contentType = flowData.get("mimeType");
        if (contentType instanceof String && !((String) contentType).trim().isEmpty()) {
            return (String) contentType;
        }
        
        // Determine from filename
        if (fileName != null) {
            String lowerName = fileName.toLowerCase();
            if (lowerName.endsWith(".pdf")) return "application/pdf";
            if (lowerName.endsWith(".txt")) return "text/plain";
            if (lowerName.endsWith(".csv")) return "text/csv";
            if (lowerName.endsWith(".xml")) return "application/xml";
            if (lowerName.endsWith(".json")) return "application/json";
            if (lowerName.endsWith(".zip")) return "application/zip";
        }
        
        return "application/octet-stream";
    }
    
    @Override
    public String getProcessorName() {
        return "Flow Context";
    }
    
    @Override
    public String getProcessorDescription() {
        return "Process attachments from flow execution context data";
    }
}