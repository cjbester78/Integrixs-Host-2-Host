package com.integrixs.core.adapter.sftp;

import java.util.Map;

/**
 * Command interface for SFTP operations following the Command pattern.
 * Encapsulates SFTP operations as objects for better abstraction and testability.
 * Follows OOP principles with clear separation of concerns and immutable results.
 */
public interface SftpOperationCommand {
    
    /**
     * Execute the SFTP operation.
     * 
     * @param connection the SFTP connection to use
     * @param parameters operation-specific parameters
     * @return immutable operation result
     */
    SftpOperationResult execute(SftpConnection connection, Map<String, Object> parameters);
    
    /**
     * Get the command name for identification.
     * 
     * @return command name
     */
    String getCommandName();
    
    /**
     * Get the command description.
     * 
     * @return command description
     */
    String getCommandDescription();
    
    /**
     * Validate operation parameters before execution.
     * 
     * @param parameters operation parameters to validate
     * @return validation result
     */
    default SftpOperationResult validateParameters(Map<String, Object> parameters) {
        if (parameters == null) {
            return SftpOperationResult.failure("Parameters cannot be null", 
                                             new IllegalArgumentException("Parameters required"));
        }
        return SftpOperationResult.success(null, "Parameters validation passed");
    }
    
    /**
     * Check if this command supports the given operation type.
     * 
     * @param operationType the operation type to check
     * @return true if operation is supported
     */
    boolean supportsOperation(SftpOperationType operationType);
    
    /**
     * SFTP operation types
     */
    enum SftpOperationType {
        LIST_FILES("List files in directory", false),
        DOWNLOAD_FILE("Download file from remote server", false),
        UPLOAD_FILE("Upload file to remote server", true),
        DELETE_FILE("Delete file on remote server", true),
        RENAME_FILE("Rename/move file on remote server", true),
        CREATE_DIRECTORY("Create directory on remote server", true),
        DELETE_DIRECTORY("Delete directory on remote server", true),
        CHECK_FILE_EXISTS("Check if file exists on remote server", false),
        GET_FILE_INFO("Get file metadata from remote server", false),
        BATCH_DOWNLOAD("Download multiple files", false),
        BATCH_UPLOAD("Upload multiple files", true);
        
        private final String description;
        private final boolean modifiesRemoteState;
        
        SftpOperationType(String description, boolean modifiesRemoteState) {
            this.description = description;
            this.modifiesRemoteState = modifiesRemoteState;
        }
        
        public String getDescription() { return description; }
        public boolean isModifyingOperation() { return modifiesRemoteState; }
    }
}