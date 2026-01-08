package com.integrixs.adapters.email;

import com.integrixs.shared.model.Adapter;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.util.ByteArrayDataSource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Email adapter for sending and receiving emails with SMTP/IMAP support
 */
public class EmailAdapter {
    
    private static final Logger log = LoggerFactory.getLogger(EmailAdapter.class);
    
    private final Adapter adapter;
    private final EmailAdapterConfig config;
    private final Map<String, Session> sessionCache = new ConcurrentHashMap<>();
    private ExecutorService executorService;
    
    public EmailAdapter(Adapter adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("Adapter cannot be null");
        }
        this.adapter = adapter;
        this.config = new EmailAdapterConfig(adapter.getConfiguration());
        log.info("EmailAdapter initialized for interface: {}", adapter.getName());
    }
    
    @PostConstruct
    public void initialize() {
        try {
            // Clear any existing sessions to ensure fresh configuration
            sessionCache.clear();
            validateConfiguration();
            initializeExecutorService();
            log.info("EmailAdapter initialization completed for: {}", adapter.getName());
        } catch (Exception e) {
            log.error("Failed to initialize EmailAdapter: {}", e.getMessage(), e);
            throw new EmailAdapterException("Initialization failed: " + e.getMessage(), e, "INIT", null);
        }
    }
    
    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up EmailAdapter for: {}", adapter.getName());
        
        // Shutdown executor service
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Clear session cache
        sessionCache.clear();
        
        log.info("EmailAdapter cleanup completed");
    }
    
    /**
     * Send email with files from flow context
     */
    public EmailOperationResult sendEmailFromFlow(String subject, String body, List<String> toAddresses, 
                                                List<Map<String, Object>> files) {
        String correlationId = UUID.randomUUID().toString();
        log.info("Sending email from flow context - correlation: {}, subject: {}, recipients: {}, files: {}", 
                correlationId, subject, toAddresses.size(), files.size());
        
        EmailOperationResult result = new EmailOperationResult(
            correlationId, EmailOperation.SEND, EmailOperationStatus.IN_PROGRESS
        );
        
        try {
            // Process files from flow context into memory attachments
            List<Map<String, Object>> memoryAttachments = new ArrayList<>();
            
            for (Map<String, Object> file : files) {
                String fileName = (String) file.get("fileName");
                Object contentObj = file.get("content");
                // Also check for fileContent key (used by ZIP extraction)
                if (contentObj == null) {
                    contentObj = file.get("fileContent");
                }
                
                log.debug("Processing file from flow context - fileName: {}, contentType: {}, fileKeys: {}", 
                    fileName, contentObj != null ? contentObj.getClass().getSimpleName() : "null", file.keySet());
                
                byte[] content = null;
                if (contentObj instanceof byte[]) {
                    content = (byte[]) contentObj;
                } else if (contentObj instanceof String) {
                    content = ((String) contentObj).getBytes();
                } else {
                    log.warn("Unexpected content type for file {}: {}", fileName, 
                        contentObj != null ? contentObj.getClass().getName() : "null");
                    continue;
                }
                
                if (fileName != null && content != null && content.length > 0) {
                    Map<String, Object> attachment = new HashMap<>();
                    attachment.put("fileName", fileName);
                    attachment.put("content", content);
                    memoryAttachments.add(attachment);
                    log.debug("Prepared memory attachment: {} ({} bytes)", fileName, content.length);
                } else {
                    log.warn("Skipping file due to null/empty content - fileName: {}, contentLength: {}", 
                        fileName, content != null ? content.length : 0);
                }
            }
            
            // Send email with memory attachments
            EmailOperationResult sendResult = sendEmailWithMemoryAttachments(subject, body, toAddresses, memoryAttachments);
            result.setStatus(sendResult.getStatus());
            result.setError(sendResult.getError());
            // Copy messages from sendResult
            for (String message : sendResult.getMessages()) {
                result.addMessage(message);
            }
            
            log.info("Email sent from flow context - correlation: {}", correlationId);
            
        } catch (Exception e) {
            log.error("Failed to send email from flow context - correlation: {}: {}", correlationId, e.getMessage(), e);
            result.setStatus(EmailOperationStatus.FAILED);
            result.setError(e.getMessage());
            result.addMessage("Send from flow failed: " + e.getMessage());
        }
        
        result.setCompletedAt(LocalDateTime.now());
        return result;
    }

    
    /**
     * Send email with memory-based attachments
     */
    public EmailOperationResult sendEmailWithMemoryAttachments(String subject, String body, List<String> toAddresses, 
                                                              List<Map<String, Object>> attachments) {
        String correlationId = UUID.randomUUID().toString();
        log.info("Sending email with memory attachments - correlation: {}, subject: {}, recipients: {}, attachments: {}", 
                correlationId, subject, toAddresses.size(), attachments != null ? attachments.size() : 0);
        
        EmailOperationResult result = new EmailOperationResult(
            correlationId, EmailOperation.SEND, EmailOperationStatus.IN_PROGRESS
        );
        
        try {
            Session session = getSmtpSession();
            MimeMessage message = createMessageWithMemoryAttachments(session, subject, body, toAddresses, attachments);
            
            Transport.send(message);
            
            result.setStatus(EmailOperationStatus.SUCCESS);
            result.addMessage("Email sent successfully to " + toAddresses.size() + " recipients");
            
            log.info("Email sent successfully with memory attachments - correlation: {}", correlationId);
            
        } catch (Exception e) {
            log.error("Failed to send email with memory attachments - correlation: {}: {}", correlationId, e.getMessage(), e);
            result.setStatus(EmailOperationStatus.FAILED);
            result.setError(e.getMessage());
            result.addMessage("Send failed: " + e.getMessage());
        }
        
        result.setCompletedAt(LocalDateTime.now());
        return result;
    }
    
    /**
     * Send notification email (without attachments)
     */
    public EmailOperationResult sendNotification(String subject, String body, List<String> toAddresses) {
        return sendEmailWithMemoryAttachments(subject, body, toAddresses, null);
    }
    
    /**
     * Receive emails from IMAP server
     */
    public EmailOperationResult receiveEmails() {
        String correlationId = UUID.randomUUID().toString();
        log.info("Receiving emails - correlation: {}", correlationId);
        
        EmailOperationResult result = new EmailOperationResult(
            correlationId, EmailOperation.RECEIVE, EmailOperationStatus.IN_PROGRESS
        );
        
        try {
            Session session = getImapSession();
            Store store = session.getStore("imaps");
            store.connect(config.getImapHost(), config.getImapUsername(), config.getImapPassword());
            
            Folder inbox = store.getFolder(config.getImapFolder());
            inbox.open(Folder.READ_WRITE);
            
            Message[] messages = inbox.getMessages();
            int processedCount = 0;
            
            for (Message message : messages) {
                if (message.isSet(Flags.Flag.SEEN)) {
                    continue; // Skip already processed messages
                }
                
                processIncomingMessage(message, correlationId);
                message.setFlag(Flags.Flag.SEEN, true);
                processedCount++;
                
                if (processedCount >= config.getBatchSize()) {
                    break;
                }
            }
            
            inbox.close(false);
            store.close();
            
            result.setStatus(EmailOperationStatus.SUCCESS);
            result.addMessage("Processed " + processedCount + " new emails");
            
            log.info("Email receive completed - correlation: {}, processed: {}", correlationId, processedCount);
            
        } catch (Exception e) {
            log.error("Failed to receive emails - correlation: {}: {}", correlationId, e.getMessage(), e);
            result.setStatus(EmailOperationStatus.FAILED);
            result.setError(e.getMessage());
            result.addMessage("Receive failed: " + e.getMessage());
        }
        
        result.setCompletedAt(LocalDateTime.now());
        return result;
    }
    
    /**
     * Test email server connection
     */
    public EmailOperationResult testConnection() {
        String correlationId = UUID.randomUUID().toString();
        log.info("Testing email connection - correlation: {}", correlationId);
        
        EmailOperationResult result = new EmailOperationResult(
            correlationId, EmailOperation.TEST_CONNECTION, EmailOperationStatus.IN_PROGRESS
        );
        
        try {
            // Test SMTP connection
            Session smtpSession = getSmtpSession();
            String protocol = config.isSslEnabled() ? "smtps" : "smtp";
            Transport transport = smtpSession.getTransport(protocol);
            transport.connect(config.getSmtpHost(), config.getSmtpPort(), 
                            config.getSmtpUsername(), config.getSmtpPassword());
            transport.close();
            
            result.addMessage("SMTP connection successful");
            
            // Test IMAP connection if configured
            if (config.getImapHost() != null) {
                Session imapSession = getImapSession();
                Store store = imapSession.getStore("imaps");
                store.connect(config.getImapHost(), config.getImapUsername(), config.getImapPassword());
                store.close();
                
                result.addMessage("IMAP connection successful");
            }
            
            result.setStatus(EmailOperationStatus.SUCCESS);
            log.info("Email connection test successful - correlation: {}", correlationId);
            
        } catch (Exception e) {
            log.error("Email connection test failed - correlation: {}: {}", correlationId, e.getMessage(), e);
            result.setStatus(EmailOperationStatus.FAILED);
            result.setError(e.getMessage());
            result.addMessage("Connection test failed: " + e.getMessage());
        }
        
        result.setCompletedAt(LocalDateTime.now());
        return result;
    }
    
    /**
     * Get SMTP session with caching
     */
    private Session getSmtpSession() {
        // Include SSL configuration in cache key to ensure proper cache invalidation
        String cacheKey = "smtp_" + config.getSmtpHost() + "_" + config.getSmtpPort() + 
                         "_ssl:" + config.isSslEnabled() + "_starttls:" + config.isStartTlsEnabled();
        
        return sessionCache.computeIfAbsent(cacheKey, k -> {
            Properties props = new Properties();
            props.put("mail.smtp.host", config.getSmtpHost());
            props.put("mail.smtp.port", String.valueOf(config.getSmtpPort()));
            props.put("mail.smtp.auth", String.valueOf(config.isSmtpAuth()));
            props.put("mail.smtp.connectiontimeout", String.valueOf(config.getConnectionTimeout()));
            props.put("mail.smtp.timeout", String.valueOf(config.getReadTimeout()));
            
            // Configure SSL vs STARTTLS properly - they are mutually exclusive
            if (config.isSslEnabled()) {
                // SSL mode (typically port 465)
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.starttls.enable", "false");
                props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
                log.debug("SMTP session configured for SSL on port {}", config.getSmtpPort());
            } else if (config.isStartTlsEnabled()) {
                // STARTTLS mode (typically port 587)
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.ssl.enable", "false");
                props.put("mail.smtp.starttls.required", "true");
                log.debug("SMTP session configured for STARTTLS on port {}", config.getSmtpPort());
            } else {
                // Plain text (not recommended)
                props.put("mail.smtp.ssl.enable", "false");
                props.put("mail.smtp.starttls.enable", "false");
                log.warn("SMTP session configured without encryption on port {}", config.getSmtpPort());
            }
            
            if (config.isSmtpAuth()) {
                return Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(config.getSmtpUsername(), config.getSmtpPassword());
                    }
                });
            } else {
                return Session.getInstance(props);
            }
        });
    }
    
    /**
     * Get IMAP session with caching
     */
    private Session getImapSession() {
        String cacheKey = "imap_" + config.getImapHost() + "_" + config.getImapPort();
        
        return sessionCache.computeIfAbsent(cacheKey, k -> {
            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            props.put("mail.imaps.host", config.getImapHost());
            props.put("mail.imaps.port", String.valueOf(config.getImapPort()));
            props.put("mail.imaps.ssl.enable", String.valueOf(config.isImapSslEnabled()));
            props.put("mail.imaps.connectiontimeout", String.valueOf(config.getConnectionTimeout()));
            props.put("mail.imaps.timeout", String.valueOf(config.getReadTimeout()));
            
            return Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(config.getImapUsername(), config.getImapPassword());
                }
            });
        });
    }
    
    /**
     * Create MIME message with memory attachments
     */
    private MimeMessage createMessageWithMemoryAttachments(Session session, String subject, String body, 
                                                         List<String> toAddresses, List<Map<String, Object>> attachments) throws Exception {
        MimeMessage message = new MimeMessage(session);
        
        // Set from address
        String fromAddress = config.getFromAddress();
        String fromName = config.getFromName();
        if (fromName != null && !fromName.trim().isEmpty()) {
            message.setFrom(new InternetAddress(fromAddress, fromName));
        } else {
            message.setFrom(new InternetAddress(fromAddress));
        }
        
        // Set recipients
        for (String toAddress : toAddresses) {
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(toAddress));
        }
        
        // Add CC recipients
        List<String> ccAddresses = config.getCcAddresses();
        if (ccAddresses != null) {
            for (String ccAddress : ccAddresses) {
                message.addRecipient(Message.RecipientType.CC, new InternetAddress(ccAddress));
            }
        }
        
        // Add BCC recipients
        List<String> bccAddresses = config.getBccAddresses();
        if (bccAddresses != null) {
            for (String bccAddress : bccAddresses) {
                message.addRecipient(Message.RecipientType.BCC, new InternetAddress(bccAddress));
            }
        }
        
        // Set subject with template variable replacement
        String processedSubject = replaceTemplateVariables(subject);
        message.setSubject(processedSubject);
        
        // Create message content
        if (attachments == null || attachments.isEmpty()) {
            // Simple message without attachments
            String processedBody = replaceTemplateVariables(body);
            if (config.isHtmlContent()) {
                message.setContent(processedBody, "text/html; charset=utf-8");
            } else {
                message.setText(processedBody);
            }
        } else {
            // Multipart message with attachments
            Multipart multipart = new MimeMultipart();
            
            // Add body part
            MimeBodyPart textPart = new MimeBodyPart();
            String processedBody = replaceTemplateVariables(body);
            if (config.isHtmlContent()) {
                textPart.setContent(processedBody, "text/html; charset=utf-8");
            } else {
                textPart.setText(processedBody);
            }
            multipart.addBodyPart(textPart);
            
            // Add memory attachment parts
            for (Map<String, Object> attachment : attachments) {
                String fileName = (String) attachment.get("fileName");
                byte[] content = (byte[]) attachment.get("content");
                if (fileName != null && content != null && content.length > 0) {
                    addMemoryAttachment(multipart, fileName, content);
                }
            }
            
            message.setContent(multipart);
        }
        
        message.setSentDate(new Date());
        return message;
    }
    
    
    /**
     * Add memory-based attachment to multipart message
     */
    private void addMemoryAttachment(Multipart multipart, String fileName, byte[] content) throws Exception {
        log.info("Adding memory-based email attachment: {} ({} bytes)", fileName, content.length);
        
        MimeBodyPart attachmentPart = new MimeBodyPart();
        
        // Create data source from byte array
        DataSource dataSource = new ByteArrayDataSource(content, "application/octet-stream");
        attachmentPart.setDataHandler(new DataHandler(dataSource));
        attachmentPart.setFileName(fileName);
        
        multipart.addBodyPart(attachmentPart);
        log.info("Successfully added memory attachment: {}", fileName);
    }
    
    
    /**
     * Process incoming email message
     */
    private void processIncomingMessage(Message message, String correlationId) throws Exception {
        log.debug("Processing incoming message - correlation: {}, subject: {}", 
                 correlationId, message.getSubject());
        
        // Extract attachments if any
        if (message.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) message.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition())) {
                    saveAttachment(bodyPart, correlationId);
                }
            }
        }
    }
    
    /**
     * Save email attachment to disk
     */
    private void saveAttachment(BodyPart bodyPart, String correlationId) throws Exception {
        String fileName = bodyPart.getFileName();
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = "attachment_" + System.currentTimeMillis();
        }
        
        String attachmentDir = config.getAttachmentDirectory();
        if (attachmentDir == null) {
            log.warn("Attachment directory not configured, skipping attachment: {}", fileName);
            return;
        }
        
        Path attachmentPath = Paths.get(attachmentDir, correlationId + "_" + fileName);
        Files.createDirectories(attachmentPath.getParent());
        
        try {
            // Use input stream to save file content
            try (java.io.InputStream inputStream = bodyPart.getInputStream();
                 java.io.FileOutputStream outputStream = new java.io.FileOutputStream(attachmentPath.toFile())) {
                inputStream.transferTo(outputStream);
            }
            log.info("Saved attachment: {}", attachmentPath);
        } catch (IOException e) {
            log.error("Failed to save attachment {}: {}", fileName, e.getMessage(), e);
            throw new EmailAdapterException("Failed to save attachment: " + fileName, e, "RECEIVE", config.getImapHost());
        }
    }
    
    
    /**
     * Replace template variables in text
     */
    private String replaceTemplateVariables(String text) {
        if (text == null) {
            return null;
        }
        
        String result = text;
        Map<String, String> variables = config.getTemplateVariables();
        
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            result = result.replace(placeholder, entry.getValue());
        }
        
        // Add system variables
        result = result.replace("${timestamp}", LocalDateTime.now().toString());
        result = result.replace("${date}", LocalDateTime.now().toLocalDate().toString());
        result = result.replace("${adapter}", adapter.getName());
        
        return result;
    }
    
    /**
     * Validate adapter configuration for receiver email operations
     */
    private void validateConfiguration() {
        List<String> errors = new ArrayList<>();
        
        if (config.getSmtpHost() == null || config.getSmtpHost().trim().isEmpty()) {
            errors.add("SMTP host is required");
        }
        
        if (config.getFromAddress() == null || config.getFromAddress().trim().isEmpty()) {
            errors.add("From address is required");
        }
        
        if (config.isSmtpAuth()) {
            if (config.getSmtpUsername() == null || config.getSmtpUsername().trim().isEmpty()) {
                errors.add("SMTP username is required when authentication is enabled");
            }
            if (config.getSmtpPassword() == null || config.getSmtpPassword().trim().isEmpty()) {
                errors.add("SMTP password is required when authentication is enabled");
            }
        }
        
        // Note: Attachment directory is optional - files can come from flow context
        // Note: IMAP configuration is not validated - sender email not supported in this application
        
        if (!errors.isEmpty()) {
            throw new EmailAdapterException("Configuration validation failed: " + String.join(", ", errors));
        }
    }
    
    /**
     * Initialize executor service for async operations
     */
    private void initializeExecutorService() {
        int threadPoolSize = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        log.debug("Email adapter executor service initialized with {} threads", threadPoolSize);
    }
    
    
    
    // Getters
    public Adapter getAdapter() {
        return adapter;
    }
    
    public EmailAdapterConfig getConfig() {
        return config;
    }
}