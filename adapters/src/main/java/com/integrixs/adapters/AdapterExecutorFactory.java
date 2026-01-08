package com.integrixs.adapters;

import com.integrixs.core.adapter.AbstractAdapterExecutor;
import com.integrixs.core.service.AdapterConfigurationService;
import com.integrixs.core.service.FileOperationsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PROPERLY DESIGNED Adapter Factory following OOP principles:
 * 
 * 1. DEPENDENCY INVERSION: Depends only on abstractions (AbstractAdapterExecutor)
 * 2. OPEN/CLOSED: Open for extension (new adapters) without modification
 * 3. SINGLE RESPONSIBILITY: Only responsible for adapter creation
 * 4. INVERSION OF CONTROL: Uses Spring IoC container for discovery
 */
@Component
public class AdapterExecutorFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(AdapterExecutorFactory.class);
    
    private final ApplicationContext applicationContext;
    private final AdapterConfigurationService configService;
    private final FileOperationsService fileService;
    private final Map<String, AbstractAdapterExecutor> adapterCache = new ConcurrentHashMap<>();
    
    @Autowired
    public AdapterExecutorFactory(
            ApplicationContext applicationContext,
            AdapterConfigurationService configService, 
            FileOperationsService fileService) {
        this.applicationContext = applicationContext;
        this.configService = configService;
        this.fileService = fileService;
    }
    
    /**
     * Creates adapter executor using DEPENDENCY INJECTION.
     * No hardcoded class dependencies - follows DEPENDENCY INVERSION principle.
     * 
     * @param type Adapter type (FILE, SFTP, EMAIL)
     * @param direction Adapter direction (SENDER, RECEIVER)
     * @return Configured adapter executor
     */
    public Optional<AbstractAdapterExecutor> createAdapter(String type, String direction) {
        if (type == null || direction == null) {
            return Optional.empty();
        }
        
        String adapterKey = buildAdapterKey(type, direction);
        
        // Check cache first
        AbstractAdapterExecutor cachedAdapter = adapterCache.get(adapterKey);
        if (cachedAdapter != null) {
            logger.debug("Returning cached adapter for {}", adapterKey);
            return Optional.of(cachedAdapter);
        }
        
        // Use Spring IoC to find adapter - FOLLOWS OPEN/CLOSED PRINCIPLE
        Optional<AbstractAdapterExecutor> adapter = findAdapterByTypeAndDirection(type, direction);
        
        if (adapter.isPresent()) {
            adapterCache.put(adapterKey, adapter.get());
            logger.debug("Created and cached adapter for {}", adapterKey);
            return adapter;
        }
        
        logger.warn("No adapter found for type: {} direction: {}", type, direction);
        return Optional.empty();
    }
    
    /**
     * Uses Spring's IoC container to discover adapters automatically.
     * FOLLOWS OPEN/CLOSED PRINCIPLE - no code changes needed for new adapters.
     */
    private Optional<AbstractAdapterExecutor> findAdapterByTypeAndDirection(String type, String direction) {
        // Get all adapter beans from Spring context
        Map<String, AbstractAdapterExecutor> adapters = applicationContext.getBeansOfType(AbstractAdapterExecutor.class);
        
        return adapters.values().stream()
                .filter(adapter -> type.equalsIgnoreCase(adapter.getSupportedType()))
                .filter(adapter -> direction.equalsIgnoreCase(adapter.getSupportedDirection()))
                .findFirst();
    }
    
    /**
     * Validates adapter configuration following SINGLE RESPONSIBILITY.
     */
    public boolean isValidAdapter(String type, String direction) {
        return createAdapter(type, direction).isPresent();
    }
    
    /**
     * Gets supported adapter types dynamically from registered beans.
     * FOLLOWS OPEN/CLOSED - automatically includes new adapters.
     */
    public Map<String, String> getSupportedAdapters() {
        Map<String, AbstractAdapterExecutor> adapters = applicationContext.getBeansOfType(AbstractAdapterExecutor.class);
        Map<String, String> supported = new ConcurrentHashMap<>();
        
        adapters.values().forEach(adapter -> {
            String key = buildAdapterKey(adapter.getSupportedType(), adapter.getSupportedDirection());
            String description = adapter.getClass().getSimpleName();
            supported.put(key, description);
        });
        
        return supported;
    }
    
    /**
     * Clears adapter cache - useful for testing and configuration changes.
     */
    public void clearCache() {
        adapterCache.clear();
        logger.info("Adapter cache cleared");
    }
    
    private String buildAdapterKey(String type, String direction) {
        return (type + "_" + direction).toUpperCase();
    }
}