package com.integrixs.core.factory;

import com.integrixs.core.adapter.AdapterExecutor;
import com.integrixs.core.adapter.AdapterExecutorFactory;
import com.integrixs.shared.model.Adapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Legacy factory class for backward compatibility.
 * Delegates to the new DI-based AdapterExecutorFactory for proper OOP design.
 * 
 * @deprecated Use AdapterExecutorFactory directly for better testability and DI support
 */
@Deprecated
@Component
public class AdapterFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(AdapterFactory.class);
    
    private static AdapterExecutorFactory executorFactory;
    
    @Autowired
    public void setExecutorFactory(AdapterExecutorFactory factory) {
        AdapterFactory.executorFactory = factory;
    }
    
    /**
     * Create the appropriate adapter executor based on adapter type and direction.
     * 
     * @param adapterType The type of adapter (FILE, SFTP, EMAIL)
     * @param direction The direction (SENDER, RECEIVER)
     * @return The appropriate adapter executor
     * @throws UnsupportedOperationException if adapter type/direction combination is not supported
     */
    public static AdapterExecutor createExecutor(String adapterType, String direction) {
        if (executorFactory == null) {
            throw new IllegalStateException("AdapterExecutorFactory not initialized - check Spring configuration");
        }
        
        Optional<AdapterExecutor> executor = executorFactory.createExecutor(adapterType, direction);
        
        if (executor.isPresent()) {
            return executor.get();
        } else {
            throw new UnsupportedOperationException(
                String.format("Unsupported adapter type/direction combination: %s %s", adapterType, direction));
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
     * Check if an adapter type and direction combination is supported.
     * 
     * @param adapterType The adapter type
     * @param direction The adapter direction
     * @return true if combination is supported
     */
    public static boolean isSupported(String adapterType, String direction) {
        if (executorFactory == null) {
            logger.warn("AdapterExecutorFactory not initialized - returning false for isSupported check");
            return false;
        }
        
        return executorFactory.isSupported(adapterType, direction);
    }
    
    /**
     * Get all supported adapter types.
     * 
     * @return Array of supported adapter types
     */
    public static String[] getSupportedTypes() {
        if (executorFactory == null) {
            logger.warn("AdapterExecutorFactory not initialized - returning empty array");
            return new String[0];
        }
        
        return executorFactory.getSupportedTypes();
    }
    
    /**
     * Get supported directions for an adapter type.
     * 
     * @param adapterType The adapter type
     * @return Array of supported directions for the type
     */
    public static String[] getSupportedDirections(String adapterType) {
        if (executorFactory == null) {
            logger.warn("AdapterExecutorFactory not initialized - returning empty array");
            return new String[0];
        }
        
        return executorFactory.getSupportedDirections(adapterType);
    }
}