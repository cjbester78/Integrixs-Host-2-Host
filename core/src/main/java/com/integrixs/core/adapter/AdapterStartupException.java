package com.integrixs.core.adapter;

/**
 * Exception thrown when adapter startup fails.
 * Provides specific error context for startup problems.
 */
public class AdapterStartupException extends Exception {
    
    private final String adapterId;
    private final String adapterType;
    
    public AdapterStartupException(String message, String adapterId, String adapterType) {
        super(message);
        this.adapterId = adapterId;
        this.adapterType = adapterType;
    }
    
    public AdapterStartupException(String message, Throwable cause, String adapterId, String adapterType) {
        super(message, cause);
        this.adapterId = adapterId;
        this.adapterType = adapterType;
    }
    
    public String getAdapterId() { return adapterId; }
    public String getAdapterType() { return adapterType; }
}