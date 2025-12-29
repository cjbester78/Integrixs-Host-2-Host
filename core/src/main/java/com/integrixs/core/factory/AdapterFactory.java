package com.integrixs.core.factory;

import com.integrixs.core.adapter.AdapterExecutor;
import com.integrixs.core.adapter.email.EmailReceiverAdapter;
import com.integrixs.core.adapter.file.FileReceiverAdapter;
import com.integrixs.core.adapter.file.FileSenderAdapter;
import com.integrixs.core.adapter.sftp.SftpReceiverAdapter;
import com.integrixs.core.adapter.sftp.SftpSenderAdapter;
import com.integrixs.shared.model.Adapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class for creating appropriate adapter executors.
 * Implements Factory Method pattern to instantiate the correct
 * adapter executor based on type and direction.
 * 
 * This eliminates the need for switch statements and large 
 * conditional logic in the service layer.
 */
public class AdapterFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(AdapterFactory.class);
    
    /**
     * Create the appropriate adapter executor based on adapter type and direction.
     * 
     * @param adapterType The type of adapter (FILE, SFTP, EMAIL)
     * @param direction The direction (SENDER, RECEIVER)
     * @return The appropriate adapter executor
     * @throws UnsupportedOperationException if adapter type/direction combination is not supported
     */
    public static AdapterExecutor createExecutor(String adapterType, String direction) {
        if (adapterType == null || direction == null) {
            throw new IllegalArgumentException("Adapter type and direction cannot be null");
        }
        
        String type = adapterType.toUpperCase().trim();
        String dir = direction.toUpperCase().trim();
        
        logger.debug("Creating adapter executor for type: {}, direction: {}", type, dir);
        
        switch (type) {
            case "SFTP":
                return createSftpExecutor(dir);
                
            case "FILE":
                return createFileExecutor(dir);
                
            case "EMAIL":
                return createEmailExecutor(dir);
                
            default:
                throw new UnsupportedOperationException(
                    String.format("Unsupported adapter type: %s", adapterType));
        }
    }
    
    /**
     * Create adapter executor from Adapter object.
     * 
     * @param adapter The adapter configuration
     * @return The appropriate adapter executor
     */
    public static AdapterExecutor createExecutor(Adapter adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("Adapter cannot be null");
        }
        
        String direction = adapter.isSender() ? "SENDER" : "RECEIVER";
        return createExecutor(adapter.getAdapterType(), direction);
    }
    
    /**
     * Create SFTP adapter executor based on direction.
     */
    private static AdapterExecutor createSftpExecutor(String direction) {
        switch (direction) {
            case "SENDER":
                logger.debug("Creating SFTP Sender adapter executor");
                return new SftpSenderAdapter();
                
            case "RECEIVER":
                logger.debug("Creating SFTP Receiver adapter executor");
                return new SftpReceiverAdapter();
                
            default:
                throw new UnsupportedOperationException(
                    String.format("Unsupported SFTP adapter direction: %s", direction));
        }
    }
    
    /**
     * Create File adapter executor based on direction.
     */
    private static AdapterExecutor createFileExecutor(String direction) {
        switch (direction) {
            case "SENDER":
                logger.debug("Creating File Sender adapter executor");
                return new FileSenderAdapter();
                
            case "RECEIVER":
                logger.debug("Creating File Receiver adapter executor");
                return new FileReceiverAdapter();
                
            default:
                throw new UnsupportedOperationException(
                    String.format("Unsupported File adapter direction: %s", direction));
        }
    }
    
    /**
     * Create Email adapter executor based on direction.
     * Note: EMAIL adapters only support RECEIVER direction (sending emails with attachments).
     */
    private static AdapterExecutor createEmailExecutor(String direction) {
        switch (direction) {
            case "RECEIVER":
                logger.debug("Creating Email Receiver adapter executor");
                return new EmailReceiverAdapter();
                
            case "SENDER":
                throw new UnsupportedOperationException(
                    "Email sender adapters are not supported in this application (EMAIL is receiver-only)");
                
            default:
                throw new UnsupportedOperationException(
                    String.format("Unsupported Email adapter direction: %s", direction));
        }
    }
    
    /**
     * Check if an adapter type and direction combination is supported.
     * 
     * @param adapterType The adapter type
     * @param direction The adapter direction
     * @return true if combination is supported
     */
    public static boolean isSupported(String adapterType, String direction) {
        try {
            createExecutor(adapterType, direction);
            return true;
        } catch (UnsupportedOperationException e) {
            return false;
        } catch (Exception e) {
            logger.warn("Error checking adapter support for {}:{} - {}", 
                       adapterType, direction, e.getMessage());
            return false;
        }
    }
    
    /**
     * Get all supported adapter types.
     * 
     * @return Array of supported adapter types
     */
    public static String[] getSupportedTypes() {
        return new String[]{"SFTP", "FILE", "EMAIL"};
    }
    
    /**
     * Get supported directions for an adapter type.
     * 
     * @param adapterType The adapter type
     * @return Array of supported directions for the type
     */
    public static String[] getSupportedDirections(String adapterType) {
        if (adapterType == null) {
            return new String[0];
        }
        
        switch (adapterType.toUpperCase()) {
            case "SFTP":
                return new String[]{"SENDER", "RECEIVER"};
            case "FILE":
                return new String[]{"SENDER", "RECEIVER"};
            case "EMAIL":
                return new String[]{"RECEIVER"}; // EMAIL only supports RECEIVER direction
            default:
                return new String[0];
        }
    }
}