package com.integrixs.core.adapter;

/**
 * Exception thrown when adapter shutdown fails.
 * Provides specific error context for shutdown problems.
 */
public class AdapterShutdownException extends Exception {
    
    private final String adapterId;
    private final String adapterType;
    
    public AdapterShutdownException(String message, String adapterId, String adapterType) {
        super(message);
        this.adapterId = adapterId;
        this.adapterType = adapterType;
    }
    
    public AdapterShutdownException(String message, Throwable cause, String adapterId, String adapterType) {
        super(message, cause);
        this.adapterId = adapterId;
        this.adapterType = adapterType;
    }
    
    public String getAdapterId() { return adapterId; }
    public String getAdapterType() { return adapterType; }
}