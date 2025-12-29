package com.integrixs.adapters.email;

/**
 * Exception thrown by Email adapter operations
 */
public class EmailAdapterException extends RuntimeException {
    
    private final String operationType;
    private final String emailServer;
    
    public EmailAdapterException(String message) {
        super(message);
        this.operationType = null;
        this.emailServer = null;
    }
    
    public EmailAdapterException(String message, Throwable cause) {
        super(message, cause);
        this.operationType = null;
        this.emailServer = null;
    }
    
    public EmailAdapterException(String message, String operationType, String emailServer) {
        super(message);
        this.operationType = operationType;
        this.emailServer = emailServer;
    }
    
    public EmailAdapterException(String message, Throwable cause, String operationType, String emailServer) {
        super(message, cause);
        this.operationType = operationType;
        this.emailServer = emailServer;
    }
    
    public String getOperationType() {
        return operationType;
    }
    
    public String getEmailServer() {
        return emailServer;
    }
    
    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(super.getMessage());
        
        if (operationType != null) {
            sb.append(" [Operation: ").append(operationType).append("]");
        }
        
        if (emailServer != null) {
            sb.append(" [Server: ").append(emailServer).append("]");
        }
        
        return sb.toString();
    }
}