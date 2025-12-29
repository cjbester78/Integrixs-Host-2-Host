package com.integrixs.adapters.sftp;

/**
 * Exception thrown by SFTP adapter operations
 */
public class SftpAdapterException extends RuntimeException {
    
    private final String operationType;
    private final String remoteHost;
    
    public SftpAdapterException(String message) {
        super(message);
        this.operationType = null;
        this.remoteHost = null;
    }
    
    public SftpAdapterException(String message, Throwable cause) {
        super(message, cause);
        this.operationType = null;
        this.remoteHost = null;
    }
    
    public SftpAdapterException(String message, String operationType, String remoteHost) {
        super(message);
        this.operationType = operationType;
        this.remoteHost = remoteHost;
    }
    
    public SftpAdapterException(String message, Throwable cause, String operationType, String remoteHost) {
        super(message, cause);
        this.operationType = operationType;
        this.remoteHost = remoteHost;
    }
    
    public String getOperationType() {
        return operationType;
    }
    
    public String getRemoteHost() {
        return remoteHost;
    }
    
    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(super.getMessage());
        
        if (operationType != null) {
            sb.append(" [Operation: ").append(operationType).append("]");
        }
        
        if (remoteHost != null) {
            sb.append(" [Host: ").append(remoteHost).append("]");
        }
        
        return sb.toString();
    }
}