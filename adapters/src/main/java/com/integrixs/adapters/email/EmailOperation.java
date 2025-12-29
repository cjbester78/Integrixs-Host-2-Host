package com.integrixs.adapters.email;

/**
 * Types of Email operations
 */
public enum EmailOperation {
    SEND("Send email with attachments"),
    RECEIVE("Receive emails and download attachments"),
    SEND_NOTIFICATION("Send notification email without attachments"),
    TEST_CONNECTION("Test email server connection");
    
    private final String description;
    
    EmailOperation(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}