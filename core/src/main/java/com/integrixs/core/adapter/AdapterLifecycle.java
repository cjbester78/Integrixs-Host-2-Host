package com.integrixs.core.adapter;

/**
 * Interface defining adapter lifecycle management operations.
 * Provides proper initialization, startup, shutdown, and cleanup hooks.
 * Follows OOP principles with clear state management and resource handling.
 */
public interface AdapterLifecycle {
    
    /**
     * Initialize the adapter with its configuration.
     * Called once during adapter setup to prepare resources.
     * 
     * @throws AdapterInitializationException if initialization fails
     */
    void initialize() throws AdapterInitializationException;
    
    /**
     * Start the adapter for active operation.
     * Called when the adapter should begin processing.
     * 
     * @throws AdapterStartupException if startup fails
     */
    void start() throws AdapterStartupException;
    
    /**
     * Stop the adapter gracefully.
     * Called when the adapter should cease processing.
     * 
     * @throws AdapterShutdownException if shutdown fails
     */
    void stop() throws AdapterShutdownException;
    
    /**
     * Clean up adapter resources.
     * Called during adapter disposal to release resources.
     */
    void cleanup();
    
    /**
     * Check if the adapter is currently initialized.
     * 
     * @return true if adapter is initialized
     */
    boolean isInitialized();
    
    /**
     * Check if the adapter is currently running.
     * 
     * @return true if adapter is actively running
     */
    boolean isRunning();
    
    /**
     * Get the current lifecycle state of the adapter.
     * 
     * @return current adapter lifecycle state
     */
    AdapterLifecycleState getLifecycleState();
    
    /**
     * Adapter lifecycle states
     */
    enum AdapterLifecycleState {
        UNINITIALIZED("Uninitialized", "Adapter has not been initialized"),
        INITIALIZED("Initialized", "Adapter is initialized but not running"),
        STARTING("Starting", "Adapter is in the process of starting"),
        RUNNING("Running", "Adapter is actively running"),
        STOPPING("Stopping", "Adapter is in the process of stopping"),
        STOPPED("Stopped", "Adapter has been stopped"),
        ERROR("Error", "Adapter is in an error state"),
        DISPOSED("Disposed", "Adapter has been disposed and cleaned up");
        
        private final String displayName;
        private final String description;
        
        AdapterLifecycleState(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
}