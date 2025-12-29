package com.integrixs.adapters.sftp;

/**
 * Types of SFTP operations
 */
public enum SftpOperation {
    UPLOAD("Upload file to remote server"),
    DOWNLOAD("Download file from remote server"),
    LIST("List files in remote directory"),
    DELETE("Delete file on remote server"),
    MOVE("Move/rename file on remote server"),
    MKDIR("Create directory on remote server"),
    TEST_CONNECTION("Test SFTP connection");
    
    private final String description;
    
    SftpOperation(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}