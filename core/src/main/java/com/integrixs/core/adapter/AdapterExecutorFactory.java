package com.integrixs.core.adapter;

import com.integrixs.core.adapter.file.FileSenderAdapter;
import com.integrixs.core.adapter.file.FileReceiverAdapter;
import com.integrixs.core.adapter.sftp.SftpSenderAdapter;
import com.integrixs.core.adapter.sftp.SftpReceiverAdapter;
import com.integrixs.core.adapter.email.EmailReceiverAdapter;
import com.integrixs.core.service.AdapterConfigurationService;
import com.integrixs.core.service.FileOperationsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating adapter executors with proper dependency injection.
 * Follows OOP principles:
 * - Single Responsibility: Only creates adapter instances
 * - Dependency Injection: Uses Spring to inject dependencies
 * - Type Safety: Proper type checking and validation
 * - Caching: Reuses instances for performance
 */
@Component
public class AdapterExecutorFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(AdapterExecutorFactory.class);
    
    private final AdapterConfigurationService configService;
    private final FileOperationsService fileService;
    
    // Cache for adapter instances - adapters are stateless so safe to reuse
    private final Map<String, AdapterExecutor> adapterCache = new ConcurrentHashMap<>();
    
    @Autowired
    public AdapterExecutorFactory(AdapterConfigurationService configService, 
                                FileOperationsService fileService) {
        this.configService = configService;
        this.fileService = fileService;
    }
    
    /**
     * Create or retrieve an adapter executor for the given type and direction
     */
    public Optional<AdapterExecutor> createExecutor(String adapterType, String direction) {
        if (adapterType == null || direction == null) {
            logger.error("Adapter type and direction cannot be null");
            return Optional.empty();
        }
        
        String cacheKey = adapterType.toUpperCase() + "_" + direction.toUpperCase();
        
        // Check cache first
        AdapterExecutor cachedExecutor = adapterCache.get(cacheKey);
        if (cachedExecutor != null) {
            logger.debug("Retrieved cached adapter executor for {} {}", adapterType, direction);
            return Optional.of(cachedExecutor);
        }
        
        // Create new instance
        Optional<AdapterExecutor> executor = createNewExecutor(adapterType.toUpperCase(), direction.toUpperCase());
        
        if (executor.isPresent()) {
            // Cache the instance
            adapterCache.put(cacheKey, executor.get());
            logger.debug("Created and cached new adapter executor for {} {}", adapterType, direction);
        } else {
            logger.error("Failed to create adapter executor for type '{}' and direction '{}'", adapterType, direction);
        }
        
        return executor;
    }
    
    /**
     * Check if an adapter type and direction combination is supported
     */
    public boolean isSupported(String adapterType, String direction) {
        return createExecutor(adapterType, direction).isPresent();
    }
    
    /**
     * Get all supported adapter types
     */
    public String[] getSupportedTypes() {
        return new String[]{"FILE", "SFTP", "EMAIL"};
    }
    
    /**
     * Get supported directions for a given adapter type
     */
    public String[] getSupportedDirections(String adapterType) {
        return switch (adapterType.toUpperCase()) {
            case "FILE", "SFTP" -> new String[]{"SENDER", "RECEIVER"};
            case "EMAIL" -> new String[]{"RECEIVER"}; // Only email receiver is implemented
            default -> new String[]{};
        };
    }
    
    /**
     * Clear the adapter cache - useful for testing
     */
    public void clearCache() {
        adapterCache.clear();
        logger.debug("Adapter cache cleared");
    }
    
    // Private factory methods
    
    private Optional<AdapterExecutor> createNewExecutor(String adapterType, String direction) {
        try {
            return switch (adapterType) {
                case "FILE" -> createFileExecutor(direction);
                case "SFTP" -> createSftpExecutor(direction);
                case "EMAIL" -> createEmailExecutor(direction);
                default -> {
                    logger.warn("Unsupported adapter type: {}", adapterType);
                    yield Optional.empty();
                }
            };
        } catch (Exception e) {
            logger.error("Error creating adapter executor for {} {}: {}", adapterType, direction, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    private Optional<AdapterExecutor> createFileExecutor(String direction) {
        return switch (direction) {
            case "SENDER" -> {
                logger.debug("Creating File Sender adapter executor with DI");
                yield Optional.of(new FileSenderAdapter(configService, fileService));
            }
            case "RECEIVER" -> {
                logger.debug("Creating File Receiver adapter executor");
                // TODO: Update FileReceiverAdapter to use DI in future iterations
                yield Optional.of(new FileReceiverAdapter());
            }
            default -> {
                logger.warn("Unsupported File adapter direction: {}", direction);
                yield Optional.empty();
            }
        };
    }
    
    private Optional<AdapterExecutor> createSftpExecutor(String direction) {
        return switch (direction) {
            case "SENDER" -> {
                logger.debug("Creating SFTP Sender adapter executor");
                // TODO: Update SftpSenderAdapter to use DI in future iterations
                yield Optional.of(new SftpSenderAdapter());
            }
            case "RECEIVER" -> {
                logger.debug("Creating SFTP Receiver adapter executor");
                // TODO: Update SftpReceiverAdapter to use DI in future iterations
                yield Optional.of(new SftpReceiverAdapter());
            }
            default -> {
                logger.warn("Unsupported SFTP adapter direction: {}", direction);
                yield Optional.empty();
            }
        };
    }
    
    private Optional<AdapterExecutor> createEmailExecutor(String direction) {
        return switch (direction) {
            case "RECEIVER" -> {
                logger.debug("Creating Email Receiver adapter executor");
                // TODO: Update EmailReceiverAdapter to use DI in future iterations  
                yield Optional.of(new EmailReceiverAdapter());
            }
            case "SENDER" -> {
                logger.warn("Email Sender adapter not implemented yet");
                yield Optional.empty();
            }
            default -> {
                logger.warn("Unsupported Email adapter direction: {}", direction);
                yield Optional.empty();
            }
        };
    }
}