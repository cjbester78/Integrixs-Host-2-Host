package com.integrixs.core.adapter;

/**
 * Exception thrown when adapter initialization fails.
 * Provides specific error context for initialization problems.
 */
public class AdapterInitializationException extends Exception {
    
    private final String adapterId;
    private final String adapterType;
    
    public AdapterInitializationException(String message, String adapterId, String adapterType) {
        super(message);
        this.adapterId = adapterId;
        this.adapterType = adapterType;
    }
    
    public AdapterInitializationException(String message, Throwable cause, String adapterId, String adapterType) {
        super(message, cause);
        this.adapterId = adapterId;
        this.adapterType = adapterType;
    }
    
    public String getAdapterId() { return adapterId; }
    public String getAdapterType() { return adapterType; }
}