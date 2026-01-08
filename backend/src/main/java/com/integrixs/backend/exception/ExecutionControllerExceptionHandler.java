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
 * Centralized exception handler for execution controller operations.
 * Provides consistent error responses and proper HTTP status codes.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
@ControllerAdvice(basePackages = "com.integrixs.backend.controller")
public class ExecutionControllerExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ExecutionControllerExceptionHandler.class);
    
    /**
     * Handle execution not found exceptions.
     */
    @ExceptionHandler(ExecutionNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleExecutionNotFound(
            ExecutionNotFoundException ex, WebRequest request) {
        
        String correlationId = generateCorrelationId();
        logger.warn("Execution not found - correlation: {}, executionId: {}, message: {}", 
                   correlationId, ex.getExecutionId(), ex.getMessage());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .correlationId(correlationId)
            .errorCode("EXECUTION_NOT_FOUND")
            .errorMessage(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .path(getRequestPath(request))
            .build();
        
        ApiResponse<ErrorDetails> response = ApiResponse.error(
            "Execution not found", 
            errorDetails
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    /**
     * Handle invalid execution state exceptions.
     */
    @ExceptionHandler(InvalidExecutionStateException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleInvalidExecutionState(
            InvalidExecutionStateException ex, WebRequest request) {
        
        String correlationId = generateCorrelationId();
        logger.warn("Invalid execution state - correlation: {}, executionId: {}, currentState: {}, operation: {}", 
                   correlationId, ex.getExecutionId(), ex.getCurrentState(), ex.getAttemptedOperation());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .correlationId(correlationId)
            .errorCode("INVALID_EXECUTION_STATE")
            .errorMessage(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .path(getRequestPath(request))
            .addDetail("executionId", ex.getExecutionId().toString())
            .addDetail("currentState", ex.getCurrentState())
            .addDetail("attemptedOperation", ex.getAttemptedOperation())
            .build();
        
        ApiResponse<ErrorDetails> response = ApiResponse.error(
            "Invalid execution state for requested operation", 
            errorDetails
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    
    /**
     * Handle execution validation exceptions.
     */
    @ExceptionHandler(ExecutionValidationException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleExecutionValidation(
            ExecutionValidationException ex, WebRequest request) {
        
        String correlationId = generateCorrelationId();
        ExecutionValidationResult validationResult = ex.getValidationResult();
        
        logger.warn("Execution validation failed - correlation: {}, errors: {}, warnings: {}", 
                   correlationId, validationResult.getErrorCount(), validationResult.getWarningCount());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .correlationId(correlationId)
            .errorCode("VALIDATION_FAILED")
            .errorMessage("Request validation failed")
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
     * Handle execution operation timeout exceptions.
     */
    @ExceptionHandler(ExecutionOperationTimeoutException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleExecutionOperationTimeout(
            ExecutionOperationTimeoutException ex, WebRequest request) {
        
        String correlationId = generateCorrelationId();
        logger.error("Execution operation timeout - correlation: {}, executionId: {}, operation: {}, timeout: {}ms", 
                    correlationId, ex.getExecutionId(), ex.getOperation(), ex.getTimeoutMs());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .correlationId(correlationId)
            .errorCode("OPERATION_TIMEOUT")
            .errorMessage(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .path(getRequestPath(request))
            .addDetail("executionId", ex.getExecutionId().toString())
            .addDetail("operation", ex.getOperation())
            .addDetail("timeoutMs", ex.getTimeoutMs())
            .build();
        
        ApiResponse<ErrorDetails> response = ApiResponse.error(
            "Operation timed out", 
            errorDetails
        );
        
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(response);
    }
    
    /**
     * Handle execution service unavailable exceptions.
     */
    @ExceptionHandler(ExecutionServiceUnavailableException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleExecutionServiceUnavailable(
            ExecutionServiceUnavailableException ex, WebRequest request) {
        
        String correlationId = generateCorrelationId();
        logger.error("Execution service unavailable - correlation: {}, service: {}, reason: {}", 
                    correlationId, ex.getServiceName(), ex.getReason());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .correlationId(correlationId)
            .errorCode("SERVICE_UNAVAILABLE")
            .errorMessage(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .path(getRequestPath(request))
            .addDetail("serviceName", ex.getServiceName())
            .addDetail("reason", ex.getReason())
            .build();
        
        ApiResponse<ErrorDetails> response = ApiResponse.error(
            "Execution service temporarily unavailable", 
            errorDetails
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
    
    /**
     * Handle access denied exceptions.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {
        
        String correlationId = generateCorrelationId();
        logger.warn("Access denied for execution operation - correlation: {}, message: {}", 
                   correlationId, ex.getMessage());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .correlationId(correlationId)
            .errorCode("ACCESS_DENIED")
            .errorMessage("Access denied")
            .timestamp(LocalDateTime.now())
            .path(getRequestPath(request))
            .build();
        
        ApiResponse<ErrorDetails> response = ApiResponse.error(
            "Access denied for this operation", 
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
        logger.warn("Invalid request parameters - correlation: {}, message: {}", 
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
        logger.error("Unexpected runtime exception in execution controller - correlation: {}, message: {}", 
                    correlationId, ex.getMessage(), ex);
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .correlationId(correlationId)
            .errorCode("INTERNAL_ERROR")
            .errorMessage("An unexpected error occurred")
            .timestamp(LocalDateTime.now())
            .path(getRequestPath(request))
            .build();
        
        ApiResponse<ErrorDetails> response = ApiResponse.error(
            "An unexpected error occurred while processing your request", 
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
        logger.error("Unhandled exception in execution controller - correlation: {}, type: {}, message: {}", 
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
            "A system error occurred while processing your request", 
            errorDetails
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * Generate correlation ID for error tracking.
     */
    private String generateCorrelationId() {
        return "ERR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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