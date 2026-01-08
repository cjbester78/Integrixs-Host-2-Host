package com.integrixs.backend.exception;

/**
 * Exception thrown when attempting to create a duplicate interface.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public class DuplicateInterfaceException extends RuntimeException {
    
    private final String interfaceName;
    private final String duplicateField;
    private final String conflictingValue;
    
    public DuplicateInterfaceException(String interfaceName, String duplicateField, String conflictingValue) {
        super(String.format("Interface '%s' already exists with %s: %s", 
                           interfaceName, duplicateField, conflictingValue));
        this.interfaceName = interfaceName;
        this.duplicateField = duplicateField;
        this.conflictingValue = conflictingValue;
    }
    
    public DuplicateInterfaceException(String duplicateField, String conflictingValue) {
        super(String.format("Interface already exists with %s: %s", duplicateField, conflictingValue));
        this.interfaceName = null;
        this.duplicateField = duplicateField;
        this.conflictingValue = conflictingValue;
    }
    
    public String getInterfaceName() {
        return interfaceName;
    }
    
    public String getDuplicateField() {
        return duplicateField;
    }
    
    public String getConflictingValue() {
        return conflictingValue;
    }
}