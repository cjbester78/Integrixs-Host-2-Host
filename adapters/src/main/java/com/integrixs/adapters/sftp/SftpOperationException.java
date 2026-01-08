package com.integrixs.adapters.sftp;

/**
 * Exception thrown during SFTP operations
 */
public class SftpOperationException extends Exception {
    
    public SftpOperationException(String message) {
        super(message);
    }
    
    public SftpOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}