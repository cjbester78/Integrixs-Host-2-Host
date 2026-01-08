package com.integrixs.backend.exception;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.backend.dto.ErrorDetails;
import com.integrixs.backend.dto.ExecutionValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Centralized exception handler for interface controller operations.
 * Provides consistent error responses and proper HTTP status codes.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
@ControllerAdvice(assignableTypes = {com.integrixs.backend.controller.InterfaceController.class})
public class InterfaceControllerExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(InterfaceControllerExceptionHandler.class);
    
    /**
     * Handle interface not found exceptions.
     */
    @ExceptionHandler(InterfaceNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleInterfaceNotFound(
            InterfaceNotFoundException ex, WebRequest request) {
        
        String correlationId = generateCorrelationId();
        logger.warn("Interface not found - correlation: {}, interfaceId: {}, message: {}", 
                   correlationId, ex.getInterfaceId(), ex.getMessage());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .correlationId(correlationId)
            .errorCode("INTERFACE_NOT_FOUND")
            .errorMessage(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .path(getRequestPath(request))
            .addDetail("interfaceId", ex.getInterfaceId() != null ? ex.getInterfaceId().toString() : "unknown")
            .build();
        
        ApiResponse<ErrorDetails> response = ApiResponse.error(
            "Interface not found", 
            errorDetails
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    /**
     * Handle invalid interface state exceptions.
     */
    @ExceptionHandler(InvalidInterfaceStateException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleInvalidInterfaceState(
            InvalidInterfaceStateException ex, WebRequest request) {
        
        String correlationId = generateCorrelationId();
        logger.warn("Invalid interface state - correlation: {}, interfaceId: {}, currentState: {}, operation: {}", 
                   correlationId, ex.getInterfaceId(), ex.getCurrentState(), ex.getAttemptedOperation());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .correlationId(correlationId)
            .errorCode("INVALID_INTERFACE_STATE")
            .errorMessage(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .path(getRequestPath(request))
            .addDetail("interfaceId", ex.getInterfaceId().toString())
            .addDetail("currentState", ex.getCurrentState())
            .addDetail("attemptedOperation", ex.getAttemptedOperation())
            .build();
        
        ApiResponse<ErrorDetails> response = ApiResponse.error(
            "Invalid interface state for requested operation", 
            errorDetails
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    
    /**
     * Handle interface configuration validation exceptions.
     */
    @ExceptionHandler(InterfaceConfigurationException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleInterfaceConfiguration(
            InterfaceConfigurationException ex, WebRequest request) {
        
        String correlationId = generateCorrelationId();
        logger.warn("Interface configuration invalid - correlation: {}, interfaceId: {}, configField: {}, message: {}", 
                   correlationId, ex.getInterfaceId(), ex.getConfigurationField(), ex.getMessage());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .correlationId(correlationId)
            .errorCode("INVALID_CONFIGURATION")
            .errorMessage(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .path(getRequestPath(request))
            .addDetail("interfaceId", ex.getInterfaceId() != null ? ex.getInterfaceId().toString() : "unknown")
            .addDetail("configurationField", ex.getConfigurationField())
            .addDetail("configurationErrors", ex.getValidationErrors())
            .build();
        
        ApiResponse<ErrorDetails> response = ApiResponse.error(
            "Interface configuration is invalid: " + ex.getMessage(), 
            errorDetails
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Handle interface validation exceptions.
     */
    @ExceptionHandler(InterfaceValidationException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleInterfaceValidation(
            InterfaceValidationException ex, WebRequest request) {
        
        String correlationId = generateCorrelationId();
        ExecutionValidationResult validationResult = ex.getValidationResult();
        
        logger.warn("Interface validation failed - correlation: {}, errors: {}, warnings: {}", 
                   correlationId, validationResult.getErrorCount(), validationResult.getWarningCount());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .correlationId(correlationId)
            .errorCode("VALIDATION_FAILED")
            .errorMessage("Interface request validation failed")
            .timestamp(LocalDateTime.now())
            .path(getRequestPath(request))
            .addDetail("validationErrors", validationResult.getErrors())
            .addDetail("validationWarnings", validationResult.getWarnings())
            .build();
        
        ApiResponse<ErrorDetails> response = ApiResponse.error(
            "Request validation failed: " + validationResult.getErrorsAsString(), 
            errorDetails
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Handle interface connection test exceptions.
     */
    @ExceptionHandler(InterfaceTestConnectionException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleInterfaceTestConnection(
            InterfaceTestConnectionException ex, WebRequest request) {
        
        String correlationId = generateCorrelationId();
        logger.warn("Interface connection test failed - correlation: {}, interfaceId: {}, testType: {}, reason: {}", 
                   correlationId, ex.getInterfaceId(), ex.getTestType(), ex.getReason());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .correlationId(correlationId)
            .errorCode("CONNECTION_TEST_FAILED")
            .errorMessage(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .path(getRequestPath(request))
            .addDetail("interfaceId", ex.getInterfaceId().toString())
            .addDetail("testType", ex.getTestType())
            .addDetail("reason", ex.getReason())
            .addDetail("testDurationMs", ex.getTestDurationMs())
            .build();
        
        ApiResponse<ErrorDetails> response = ApiResponse.error(
            "Interface connection test failed", 
            errorDetails
        );
        
        return ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).body(response);
    }
    
    /**
     * Handle interface operation timeout exceptions.
     */
    @ExceptionHandler(InterfaceOperationTimeoutException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleInterfaceOperationTimeout(
            InterfaceOperationTimeoutException ex, WebRequest request) {
        
        String correlationId = generateCorrelationId();
        logger.error("Interface operation timeout - correlation: {}, interfaceId: {}, operation: {}, timeout: {}ms", 
                    correlationId, ex.getInterfaceId(), ex.getOperation(), ex.getTimeoutMs());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .correlationId(correlationId)
            .errorCode("OPERATION_TIMEOUT")
            .errorMessage(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .path(getRequestPath(request))
            .addDetail("interfaceId", ex.getInterfaceId().toString())
            .addDetail("operation", ex.getOperation())
            .addDetail("timeoutMs", ex.getTimeoutMs())
            .build();
        
        ApiResponse<ErrorDetails> response = ApiResponse.error(
            "Interface operation timed out", 
            errorDetails
        );
        
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(response);
    }
    
    /**
     * Handle interface service unavailable exceptions.
     */
    @ExceptionHandler(InterfaceServiceUnavailableException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleInterfaceServiceUnavailable(
            InterfaceServiceUnavailableException ex, WebRequest request) {
        
        String correlationId = generateCorrelationId();
        logger.error("Interface service unavailable - correlation: {}, interfaceId: {}, service: {}, reason: {}", 
                    correlationId, ex.getInterfaceId(), ex.getServiceName(), ex.getReason());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .correlationId(correlationId)
            .errorCode("SERVICE_UNAVAILABLE")
            .errorMessage(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .path(getRequestPath(request))
            .addDetail("interfaceId", ex.getInterfaceId() != null ? ex.getInterfaceId().toString() : "unknown")
            .addDetail("serviceName", ex.getServiceName())
            .addDetail("reason", ex.getReason())
            .build();
        
        ApiResponse<ErrorDetails> response = ApiResponse.error(
            "Interface service temporarily unavailable", 
            errorDetails
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
    
    /**
     * Handle duplicate interface exceptions.
     */
    @ExceptionHandler(DuplicateInterfaceException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleDuplicateInterface(
            DuplicateInterfaceException ex, WebRequest request) {
        
        String correlationId = generateCorrelationId();
        logger.warn("Duplicate interface creation attempt - correlation: {}, interfaceName: {}, duplicateField: {}", 
                   correlationId, ex.getInterfaceName(), ex.getDuplicateField());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .correlationId(correlationId)
            .errorCode("DUPLICATE_INTERFACE")
            .errorMessage(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .path(getRequestPath(request))
            .addDetail("interfaceName", ex.getInterfaceName())
            .addDetail("duplicateField", ex.getDuplicateField())
            .addDetail("conflictingValue", ex.getConflictingValue())
            .build();
        
        ApiResponse<ErrorDetails> response = ApiResponse.error(
            "Interface already exists with the same " + ex.getDuplicateField(), 
            errorDetails
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    
    /**
     * Handle access denied exceptions for interface operations.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {
        
        String correlationId = generateCorrelationId();
        logger.warn("Access denied for interface operation - correlation: {}, message: {}", 
                   correlationId, ex.getMessage());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .correlationId(correlationId)
            .errorCode("ACCESS_DENIED")
            .errorMessage("Access denied")
            .timestamp(LocalDateTime.now())
            .path(getRequestPath(request))
            .build();
        
        ApiResponse<ErrorDetails> response = ApiResponse.error(
            "Access denied for this interface operation", 
            errorDetails
        );
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }
    
    /**
     * Handle illegal argument exceptions (parameter validation).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        
        String correlationId = generateCorrelationId();
        logger.warn("Invalid interface request parameters - correlation: {}, message: {}", 
                   correlationId, ex.getMessage());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .correlationId(correlationId)
            .errorCode("INVALID_PARAMETER")
            .errorMessage(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .path(getRequestPath(request))
            .build();
        
        ApiResponse<ErrorDetails> response = ApiResponse.error(
            "Invalid request parameters: " + ex.getMessage(), 
            errorDetails
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Handle unexpected runtime exceptions.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
        
        String correlationId = generateCorrelationId();
        logger.error("Unexpected runtime exception in interface controller - correlation: {}, message: {}", 
                    correlationId, ex.getMessage(), ex);
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .correlationId(correlationId)
            .errorCode("INTERNAL_ERROR")
            .errorMessage("An unexpected error occurred")
            .timestamp(LocalDateTime.now())
            .path(getRequestPath(request))
            .build();
        
        ApiResponse<ErrorDetails> response = ApiResponse.error(
            "An unexpected error occurred while processing your interface request", 
            errorDetails
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * Handle all other exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleGenericException(
            Exception ex, WebRequest request) {
        
        String correlationId = generateCorrelationId();
        logger.error("Unhandled exception in interface controller - correlation: {}, type: {}, message: {}", 
                    correlationId, ex.getClass().getSimpleName(), ex.getMessage(), ex);
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .correlationId(correlationId)
            .errorCode("UNKNOWN_ERROR")
            .errorMessage("A system error occurred")
            .timestamp(LocalDateTime.now())
            .path(getRequestPath(request))
            .addDetail("exceptionType", ex.getClass().getSimpleName())
            .build();
        
        ApiResponse<ErrorDetails> response = ApiResponse.error(
            "A system error occurred while processing your interface request", 
            errorDetails
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * Generate correlation ID for error tracking.
     */
    private String generateCorrelationId() {
        return "INT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    /**
     * Extract request path from WebRequest.
     */
    private String getRequestPath(WebRequest request) {
        try {
            return request.getDescription(false);
        } catch (Exception e) {
            return "unknown";
        }
    }
}