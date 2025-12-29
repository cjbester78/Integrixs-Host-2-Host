package com.integrixs.backend.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Result of file adapter configuration test
 */
public class FileAdapterTestResult {
    private UUID adapterInterfaceId;
    private LocalDateTime testTime;
    private boolean valid;
    private String message;
    private List<String> errors;
    private List<String> successes;
    private int discoveredFileCount;
    
    public FileAdapterTestResult() {
        this.errors = new ArrayList<>();
        this.successes = new ArrayList<>();
    }
    
    public void addError(String error) { 
        this.errors.add(error); 
    }
    
    public void addSuccess(String success) { 
        this.successes.add(success); 
    }
    
    // Getters and setters
    public UUID getAdapterInterfaceId() { return adapterInterfaceId; }
    public void setAdapterInterfaceId(UUID adapterInterfaceId) { this.adapterInterfaceId = adapterInterfaceId; }
    
    public LocalDateTime getTestTime() { return testTime; }
    public void setTestTime(LocalDateTime testTime) { this.testTime = testTime; }
    
    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }
    
    public List<String> getSuccesses() { return successes; }
    public void setSuccesses(List<String> successes) { this.successes = successes; }
    
    public int getDiscoveredFileCount() { return discoveredFileCount; }
    public void setDiscoveredFileCount(int discoveredFileCount) { this.discoveredFileCount = discoveredFileCount; }
    
    @Override
    public String toString() {
        return String.format("FileAdapterTestResult{valid=%s, files=%d, errors=%d, successes=%d}",
                valid, discoveredFileCount, errors.size(), successes.size());
    }
}