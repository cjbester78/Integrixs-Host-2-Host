package com.integrixs.adapters.email;

import java.util.Arrays;

/**
 * Immutable email attachment representation with defensive copying.
 * Contains file name, content, content type and metadata for email attachments.
 * Part of Phase 3 email adapter refactoring following OOP principles.
 */
public final class EmailAttachment {
    
    private final String fileName;
    private final byte[] content;
    private final String contentType;
    private final long size;
    private final String attachmentId;
    
    private EmailAttachment(Builder builder) {
        this.fileName = builder.fileName;
        this.content = builder.content != null ? Arrays.copyOf(builder.content, builder.content.length) : null;
        this.contentType = builder.contentType;
        this.size = builder.size;
        this.attachmentId = builder.attachmentId;
    }
    
    // Getters with defensive copying for mutable content
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSize() { return size; }
    public String getAttachmentId() { return attachmentId; }
    
    /**
     * Get attachment content with defensive copying to maintain immutability.
     */
    public byte[] getContent() { 
        return content != null ? Arrays.copyOf(content, content.length) : null; 
    }
    
    /**
     * Check if attachment has content.
     */
    public boolean hasContent() {
        return content != null && content.length > 0;
    }
    
    /**
     * Get content length safely.
     */
    public int getContentLength() {
        return content != null ? content.length : 0;
    }
    
    /**
     * Check if attachment is text-based.
     */
    public boolean isTextContent() {
        return contentType != null && (
            contentType.startsWith("text/") ||
            contentType.equals("application/json") ||
            contentType.equals("application/xml")
        );
    }
    
    /**
     * Check if attachment is binary content.
     */
    public boolean isBinaryContent() {
        return !isTextContent();
    }
    
    /**
     * Get file extension from filename.
     */
    public String getFileExtension() {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
    
    /**
     * Create builder instance for constructing EmailAttachment.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for EmailAttachment following builder pattern.
     */
    public static class Builder {
        private String fileName;
        private byte[] content;
        private String contentType;
        private long size;
        private String attachmentId;
        
        private Builder() {}
        
        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }
        
        public Builder content(byte[] content) {
            this.content = content;
            return this;
        }
        
        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }
        
        public Builder size(long size) {
            this.size = size;
            return this;
        }
        
        public Builder attachmentId(String attachmentId) {
            this.attachmentId = attachmentId;
            return this;
        }
        
        public EmailAttachment build() {
            return new EmailAttachment(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("EmailAttachment{fileName='%s', contentType='%s', size=%d, id='%s'}", 
                           fileName, contentType, size, attachmentId);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        EmailAttachment that = (EmailAttachment) obj;
        return size == that.size &&
               Objects.equals(fileName, that.fileName) &&
               Objects.equals(contentType, that.contentType) &&
               Objects.equals(attachmentId, that.attachmentId) &&
               Arrays.equals(content, that.content);
    }
    
    @Override
    public int hashCode() {
        int result = Objects.hash(fileName, contentType, size, attachmentId);
        result = 31 * result + Arrays.hashCode(content);
        return result;
    }
}

class Objects {
    public static boolean equals(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }
    
    public static int hash(Object... values) {
        return Arrays.hashCode(values);
    }
}