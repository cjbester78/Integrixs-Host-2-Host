package com.integrixs.core.service;

import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.core.service.TransactionLogService;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.TransactionLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for adapter lifecycle and state management
 * Handles adapter state transitions and lifecycle operations following Single Responsibility Principle
 */
@Service
public class AdapterLifecycleService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdapterLifecycleService.class);
    
    private final AdapterRepository adapterRepository;
    private final TransactionLogService transactionLogService;
    
    @Autowired
    public AdapterLifecycleService(AdapterRepository adapterRepository, TransactionLogService transactionLogService) {
        this.adapterRepository = adapterRepository;
        this.transactionLogService = transactionLogService;
    }
    
    /**
     * Start an adapter
     */
    public void startAdapter(UUID adapterId) {
        logger.info("Starting adapter: {}", adapterId);
        
        Optional<Adapter> adapterOpt = adapterRepository.findById(adapterId);
        if (!adapterOpt.isPresent()) {
            throw new IllegalArgumentException("Adapter not found: " + adapterId);
        }
        
        Adapter adapter = adapterOpt.get();
        
        // Validate adapter can be started
        if (!adapter.getActive()) {
            throw new IllegalStateException("Cannot start inactive adapter: " + adapter.getName());
        }
        
        if (adapter.getStatus() == Adapter.AdapterStatus.STARTED) {
            logger.warn("Adapter {} is already started", adapter.getName());
            return;
        }
        
        // Update status to STARTED
        adapterRepository.updateStatus(adapterId, Adapter.AdapterStatus.STARTED);
        logger.info("Successfully started adapter: {}", adapter.getName());
    }
    
    /**
     * Stop an adapter
     */
    public void stopAdapter(UUID adapterId) {
        logger.info("Stopping adapter: {}", adapterId);
        
        Optional<Adapter> adapterOpt = adapterRepository.findById(adapterId);
        if (!adapterOpt.isPresent()) {
            throw new IllegalArgumentException("Adapter not found: " + adapterId);
        }
        
        Adapter adapter = adapterOpt.get();
        
        if (adapter.getStatus() == Adapter.AdapterStatus.STOPPED) {
            logger.warn("Adapter {} is already stopped", adapter.getName());
            return;
        }
        
        // Update status to STOPPED
        adapterRepository.updateStatus(adapterId, Adapter.AdapterStatus.STOPPED);
        logger.info("Successfully stopped adapter: {}", adapter.getName());
    }
    
    /**
     * Restart an adapter (stop then start)
     */
    public void restartAdapter(UUID adapterId) {
        logger.info("Restarting adapter: {}", adapterId);
        
        Optional<Adapter> adapterOpt = adapterRepository.findById(adapterId);
        if (!adapterOpt.isPresent()) {
            throw new IllegalArgumentException("Adapter not found: " + adapterId);
        }
        
        Adapter adapter = adapterOpt.get();
        
        // Stop first if running
        if (adapter.getStatus() == Adapter.AdapterStatus.STARTED) {
            stopAdapter(adapterId);
        }
        
        // Then start
        startAdapter(adapterId);
        
        logger.info("Successfully restarted adapter: {}", adapter.getName());
    }
    
    /**
     * Set adapter active/inactive status
     */
    public void setAdapterActive(UUID adapterId, boolean active) {
        logger.info("Setting adapter {} active status to: {}", adapterId, active);
        
        Optional<Adapter> adapterOpt = adapterRepository.findById(adapterId);
        if (!adapterOpt.isPresent()) {
            throw new IllegalArgumentException("Adapter not found: " + adapterId);
        }
        
        Adapter adapter = adapterOpt.get();
        
        // If deactivating a running adapter, stop it first
        if (!active && adapter.getStatus() == Adapter.AdapterStatus.STARTED) {
            logger.info("Stopping adapter {} before deactivating", adapter.getName());
            stopAdapter(adapterId);
        }
        
        // Update active status
        adapterRepository.setActive(adapterId, active);
        
        logger.info("Successfully set adapter {} active status to: {}", adapter.getName(), active);
    }
    
    /**
     * Update adapter status with error handling
     */
    public void updateAdapterStatus(UUID adapterId, Adapter.AdapterStatus status) {
        logger.info("Updating adapter {} status to: {}", adapterId, status);
        
        Optional<Adapter> adapterOpt = adapterRepository.findById(adapterId);
        if (!adapterOpt.isPresent()) {
            throw new IllegalArgumentException("Adapter not found: " + adapterId);
        }
        
        Adapter adapter = adapterOpt.get();
        
        // Validate state transition is allowed
        validateStatusTransition(adapter.getStatus(), status);
        
        // Update status
        adapterRepository.updateStatus(adapterId, status);
        
        logger.info("Successfully updated adapter {} status to: {}", adapter.getName(), status);
    }
    
    /**
     * Set adapter to error state with message
     */
    public void setAdapterError(UUID adapterId, String errorMessage) {
        logger.error("Setting adapter {} to error state: {}", adapterId, errorMessage);
        
        Optional<Adapter> adapterOpt = adapterRepository.findById(adapterId);
        if (!adapterOpt.isPresent()) {
            throw new IllegalArgumentException("Adapter not found: " + adapterId);
        }
        
        Adapter adapter = adapterOpt.get();
        
        // Update status to STOPPED (since ERROR doesn't exist in current enum)
        adapterRepository.updateStatus(adapterId, Adapter.AdapterStatus.STOPPED);
        
        // Store error message in transaction log for proper audit trail
        TransactionLog errorLog = new TransactionLog();
        errorLog.setCategory("ADAPTER_ERROR");
        errorLog.setComponent("LIFECYCLE_ERROR");
        errorLog.setMessage("Adapter error: " + errorMessage);
        errorLog.setDetails("Adapter '" + adapter.getName() + "' (ID: " + adapterId + ") encountered an error and was stopped. Error: " + errorMessage);
        errorLog.setAdapterId(adapterId);
        errorLog.setTimestamp(LocalDateTime.now());
        errorLog.setCreatedAt(LocalDateTime.now());
        
        try {
            transactionLogService.log(errorLog);
            logger.info("Error logged to transaction log for adapter {}: {}", adapter.getName(), errorMessage);
        } catch (Exception logException) {
            logger.error("Failed to log adapter error to transaction log", logException);
        }
        
        // Also log to application logs
        logger.error("Adapter {} set to error state: {}", adapter.getName(), errorMessage);
    }
    
    /**
     * Get current adapter lifecycle state
     */
    public Adapter.AdapterStatus getAdapterStatus(UUID adapterId) {
        Optional<Adapter> adapterOpt = adapterRepository.findById(adapterId);
        if (!adapterOpt.isPresent()) {
            throw new IllegalArgumentException("Adapter not found: " + adapterId);
        }
        
        return adapterOpt.get().getStatus();
    }
    
    /**
     * Check if adapter is running
     */
    public boolean isAdapterRunning(UUID adapterId) {
        Adapter.AdapterStatus status = getAdapterStatus(adapterId);
        return status == Adapter.AdapterStatus.STARTED;
    }
    
    /**
     * Check if adapter is active and can be started
     */
    public boolean isAdapterStartable(UUID adapterId) {
        Optional<Adapter> adapterOpt = adapterRepository.findById(adapterId);
        if (!adapterOpt.isPresent()) {
            return false;
        }
        
        Adapter adapter = adapterOpt.get();
        return adapter.getActive() && 
               (adapter.getStatus() == Adapter.AdapterStatus.STOPPED || 
                adapter.getStatus() == null);
    }
    
    /**
     * Validate status transition is allowed
     */
    private void validateStatusTransition(Adapter.AdapterStatus currentStatus, Adapter.AdapterStatus newStatus) {
        // Basic validation - can enhance with more complex rules
        if (currentStatus == newStatus) {
            logger.debug("Status transition: {} -> {} (no change)", currentStatus, newStatus);
            return;
        }
        
        // Allow all status transitions since we only have STARTED/STOPPED
        // In the future, ERROR status could be added to the enum
        
        // Allow basic lifecycle transitions
        switch (newStatus) {
            case STARTED:
                if (currentStatus != Adapter.AdapterStatus.STOPPED && currentStatus != null) {
                    logger.warn("Starting adapter from non-stopped state: {} -> {}", currentStatus, newStatus);
                }
                break;
                
            case STOPPED:
                if (currentStatus != Adapter.AdapterStatus.STARTED) {
                    logger.warn("Stopping adapter from unexpected state: {} -> {}", currentStatus, newStatus);
                }
                break;
                
            default:
                logger.debug("Status transition: {} -> {}", currentStatus, newStatus);
                break;
        }
    }
}