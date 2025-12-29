package com.integrixs.adapters.email;

import java.util.List;
import java.util.Map;

/**
 * Configuration wrapper for Email adapter settings
 */
public class EmailAdapterConfig {
    
    private final Map<String, Object> configuration;
    
    public EmailAdapterConfig(Map<String, Object> configuration) {
        this.configuration = configuration;
    }
    
    // SMTP Settings
    public String getSmtpHost() {
        return getString("smtpHost");
    }
    
    public int getSmtpPort() {
        return getInt("smtpPort", 587);
    }
    
    public String getSmtpUsername() {
        return getString("smtpUsername");
    }
    
    public String getSmtpPassword() {
        return getString("smtpPassword");
    }
    
    public boolean isSmtpAuth() {
        return getBoolean("smtpAuth", true);
    }
    
    public boolean isStartTlsEnabled() {
        return getBoolean("startTlsEnabled", true);
    }
    
    public boolean isSslEnabled() {
        return getBoolean("sslEnabled", false);
    }
    
    // IMAP Settings (for sender email processing)
    public String getImapHost() {
        return getString("imapHost");
    }
    
    public int getImapPort() {
        return getInt("imapPort", 993);
    }
    
    public String getImapUsername() {
        return getString("imapUsername");
    }
    
    public String getImapPassword() {
        return getString("imapPassword");
    }
    
    public String getImapFolder() {
        return getString("imapFolder", "INBOX");
    }
    
    public boolean isImapSslEnabled() {
        return getBoolean("imapSslEnabled", true);
    }
    
    // Email Content Settings
    public String getFromAddress() {
        return getString("fromAddress");
    }
    
    public String getFromName() {
        return getString("fromName");
    }
    
    public List<String> getToAddresses() {
        return getStringList("toAddresses");
    }
    
    public List<String> getCcAddresses() {
        return getStringList("ccAddresses");
    }
    
    public List<String> getBccAddresses() {
        return getStringList("bccAddresses");
    }
    
    public String getSubjectTemplate() {
        // Try both field names for backward compatibility, with default fallback
        String subject = getString("subjectTemplate");
        if (subject == null) {
            subject = getString("subject");
        }
        return subject != null ? subject : "File Transfer Notification";
    }
    
    public String getBodyTemplate() {
        // Try both field names for backward compatibility, with default fallback  
        String body = getString("bodyTemplate");
        if (body == null) {
            body = getString("body");
        }
        return body != null ? body : "Please find attached files for processing.";
    }
    
    public String getContentType() {
        return getString("contentType", "text/plain");
    }
    
    public boolean isHtmlContent() {
        return "text/html".equalsIgnoreCase(getContentType());
    }
    
    // Attachment Settings
    public String getAttachmentDirectory() {
        return getString("attachmentDirectory");
    }
    
    public String getAttachmentPattern() {
        return getString("attachmentPattern", "*");
    }
    
    public boolean isIncludeAttachments() {
        return getBoolean("includeAttachments", true);
    }
    
    public long getMaxAttachmentSize() {
        return getLong("maxAttachmentSize", 25 * 1024 * 1024L); // 25MB
    }
    
    public int getMaxAttachmentCount() {
        return getInt("maxAttachmentCount", 10);
    }
    
    // Processing Settings
    public String getProcessedDirectory() {
        return getString("processedDirectory");
    }
    
    public String getErrorDirectory() {
        return getString("errorDirectory");
    }
    
    public String getPostProcessing() {
        return getString("postProcessing", "NONE");
    }
    
    public boolean isDeleteAfterSend() {
        return getBoolean("deleteAfterSend", false);
    }
    
    public boolean isArchiveAfterSend() {
        return getBoolean("archiveAfterSend", true);
    }
    
    // Connection Settings
    public int getConnectionTimeout() {
        return getInt("connectionTimeout", 60000); // 60 seconds
    }
    
    public int getReadTimeout() {
        return getInt("readTimeout", 60000); // 60 seconds
    }
    
    public int getMaxRetryAttempts() {
        return getInt("maxRetryAttempts", 3);
    }
    
    public long getRetryDelayMs() {
        return getLong("retryDelayMs", 5000L);
    }
    
    // Scheduling Settings
    public int getBatchSize() {
        return getInt("batchSize", 50);
    }
    
    public boolean isScheduledProcessing() {
        return getBoolean("scheduledProcessing", false);
    }
    
    // Validation Settings
    public boolean isValidationEnabled() {
        return getBoolean("validationEnabled", true);
    }
    
    public boolean isValidateEmailAddresses() {
        return getBoolean("validateEmailAddresses", true);
    }
    
    public boolean isValidateAttachments() {
        return getBoolean("validateAttachments", true);
    }
    
    // Security Settings
    public boolean isRequireAuthentication() {
        return getBoolean("requireAuthentication", true);
    }
    
    public boolean isVirusScanEnabled() {
        return getBoolean("virusScanEnabled", false);
    }
    
    // Template Settings
    public Map<String, String> getTemplateVariables() {
        @SuppressWarnings("unchecked")
        Map<String, String> variables = (Map<String, String>) configuration.get("templateVariables");
        return variables != null ? variables : Map.of();
    }
    
    // Helper methods for type-safe configuration access
    public String getString(String key) {
        Object value = configuration.get(key);
        return value != null ? value.toString() : null;
    }
    
    public String getString(String key, String defaultValue) {
        String value = getString(key);
        return value != null ? value : defaultValue;
    }
    
    public int getInt(String key, int defaultValue) {
        Object value = configuration.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    public long getLong(String key, long defaultValue) {
        Object value = configuration.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        } else if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = configuration.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            String stringValue = ((String) value).trim().toLowerCase();
            // Handle various boolean representations
            return "true".equals(stringValue) || 
                   "on".equals(stringValue) || 
                   "1".equals(stringValue) || 
                   "yes".equals(stringValue);
        }
        return defaultValue;
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getStringList(String key) {
        Object value = configuration.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return List.of();
    }
    
    public Map<String, Object> getRawConfiguration() {
        return configuration;
    }
    
    @Override
    public String toString() {
        return "EmailAdapterConfig{" +
                "smtpHost='" + getSmtpHost() + '\'' +
                ", smtpPort=" + getSmtpPort() +
                ", fromAddress='" + getFromAddress() + '\'' +
                ", toAddresses=" + getToAddresses() +
                ", attachmentDirectory='" + getAttachmentDirectory() + '\'' +
                ", contentType='" + getContentType() + '\'' +
                '}';
    }
}