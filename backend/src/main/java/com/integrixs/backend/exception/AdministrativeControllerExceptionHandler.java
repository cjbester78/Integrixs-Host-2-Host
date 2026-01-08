package com.integrixs.backend.exception;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.backend.dto.ErrorDetails;
import com.integrixs.backend.service.ResponseStandardizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Centralized exception handler for administrative controller operations.
 * Provides consistent error responses and logging for administrative exceptions.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
@ControllerAdvice(assignableTypes = {
    com.integrixs.backend.controller.LoggingController.class,
    com.integrixs.backend.controller.SystemController.class,
    com.integrixs.backend.controller.UserController.class,
    com.integrixs.backend.controller.SystemConfigurationController.class,
    com.integrixs.backend.controller.DataRetentionController.class
})
public class AdministrativeControllerExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(AdministrativeControllerExceptionHandler.class);
    private static final String CORRELATION_ID_KEY = "correlationId";
    
    @Autowired
    private ResponseStandardizationService responseService;
    
    /**
     * Handle administrative user not found exceptions.
     */
    @ExceptionHandler(AdminUserNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleAdminUserNotFound(
            AdminUserNotFoundException ex, WebRequest request) {
        
        String correlationId = getCorrelationId();
        logger.warn("[{}] Admin user not found: {}", correlationId, ex.getMessage());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .errorCode("ADMIN_USER_NOT_FOUND")
            .errorMessage(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .path(extractPath(request))
            .build();
        
        return responseService.notFound("User not found", errorDetails);
    }
    
    /**
     * Handle administrative configuration not found exceptions.
     */
    @ExceptionHandler(AdminConfigNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleAdminConfigNotFound(
            AdminConfigNotFoundException ex, WebRequest request) {
        
        String correlationId = getCorrelationId();
        logger.warn("[{}] Admin configuration not found: {}", correlationId, ex.getMessage());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .errorCode("ADMIN_CONFIG_NOT_FOUND")
            .errorMessage(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .path(extractPath(request))
            .build();
        
        return responseService.notFound("Configuration not found", errorDetails);
    }
    
    /**
     * Handle administrative validation exceptions.
     */
    @ExceptionHandler(AdminValidationException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleAdminValidation(
            AdminValidationException ex, WebRequest request) {
        
        String correlationId = getCorrelationId();
        logger.warn("[{}] Admin validation failed: {}", correlationId, ex.getMessage());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .errorCode("ADMIN_VALIDATION_FAILED")
            .errorMessage(ex.getMessage())
            .addDetail("validationErrors", ex.getValidationErrors())
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .path(extractPath(request))
            .build();
        
        return responseService.badRequest("Validation failed", errorDetails);
    }
    
    /**
     * Handle administrative operation exceptions.
     */
    @ExceptionHandler(AdminOperationException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleAdminOperation(
            AdminOperationException ex, WebRequest request) {
        
        String correlationId = getCorrelationId();
        logger.error("[{}] Admin operation failed: {}", correlationId, ex.getMessage(), ex);
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .errorCode("ADMIN_OPERATION_FAILED")
            .errorMessage(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .path(extractPath(request))
            .build();
        
        return responseService.internalServerError("Operation failed", errorDetails);
    }
    
    /**
     * Handle administrative unauthorized access exceptions.
     */
    @ExceptionHandler(AdminUnauthorizedException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleAdminUnauthorized(
            AdminUnauthorizedException ex, WebRequest request) {
        
        String correlationId = getCorrelationId();
        logger.warn("[{}] Admin unauthorized access: {}", correlationId, ex.getMessage());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .errorCode("ADMIN_UNAUTHORIZED")
            .errorMessage(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .path(extractPath(request))
            .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("Access denied", errorDetails));
    }
    
    /**
     * Handle administrative system exceptions.
     */
    @ExceptionHandler(AdminSystemException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleAdminSystem(
            AdminSystemException ex, WebRequest request) {
        
        String correlationId = getCorrelationId();
        logger.error("[{}] Admin system error: {}", correlationId, ex.getMessage(), ex);
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .errorCode("ADMIN_SYSTEM_ERROR")
            .errorMessage(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .path(extractPath(request))
            .build();
        
        return responseService.internalServerError("System error", errorDetails);
    }
    
    /**
     * Handle administrative data retention exceptions.
     */
    @ExceptionHandler(AdminDataRetentionException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleAdminDataRetention(
            AdminDataRetentionException ex, WebRequest request) {
        
        String correlationId = getCorrelationId();
        logger.error("[{}] Admin data retention error: {}", correlationId, ex.getMessage(), ex);
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .errorCode("ADMIN_DATA_RETENTION_ERROR")
            .errorMessage(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .path(extractPath(request))
            .build();
        
        return responseService.internalServerError("Data retention operation failed", errorDetails);
    }
    
    /**
     * Handle administrative environment exceptions.
     */
    @ExceptionHandler(AdminEnvironmentException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleAdminEnvironment(
            AdminEnvironmentException ex, WebRequest request) {
        
        String correlationId = getCorrelationId();
        logger.error("[{}] Admin environment error: {}", correlationId, ex.getMessage(), ex);
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .errorCode("ADMIN_ENVIRONMENT_ERROR")
            .errorMessage(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .path(extractPath(request))
            .build();
        
        return responseService.internalServerError("Environment operation failed", errorDetails);
    }
    
    /**
     * Handle general administrative exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleGeneral(
            Exception ex, WebRequest request) {
        
        String correlationId = getCorrelationId();
        logger.error("[{}] Unexpected admin error: {}", correlationId, ex.getMessage(), ex);
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .errorCode("ADMIN_UNEXPECTED_ERROR")
            .errorMessage("An unexpected error occurred during administrative operation")
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .path(extractPath(request))
            .build();
        
        return responseService.internalServerError("Unexpected error", errorDetails);
    }
    
    /**
     * Get correlation ID from MDC or generate new one.
     */
    private String getCorrelationId() {
        String correlationId = MDC.get(CORRELATION_ID_KEY);
        return correlationId != null ? correlationId : UUID.randomUUID().toString();
    }
    
    /**
     * Extract path from web request.
     */
    private String extractPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}

// Administrative Exception Classes

/**
 * Exception for administrative user not found scenarios.
 */
class AdminUserNotFoundException extends RuntimeException {
    public AdminUserNotFoundException(String message) {
        super(message);
    }
    
    public AdminUserNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Exception for administrative configuration not found scenarios.
 */
class AdminConfigNotFoundException extends RuntimeException {
    public AdminConfigNotFoundException(String message) {
        super(message);
    }
    
    public AdminConfigNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Exception for administrative validation failures.
 */
class AdminValidationException extends RuntimeException {
    private final java.util.List<String> validationErrors;
    
    public AdminValidationException(String message, java.util.List<String> validationErrors) {
        super(message);
        this.validationErrors = validationErrors != null ? validationErrors : java.util.Collections.emptyList();
    }
    
    public java.util.List<String> getValidationErrors() {
        return validationErrors;
    }
}

/**
 * Exception for administrative operation failures.
 */
class AdminOperationException extends RuntimeException {
    public AdminOperationException(String message) {
        super(message);
    }
    
    public AdminOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Exception for administrative unauthorized access.
 */
class AdminUnauthorizedException extends RuntimeException {
    public AdminUnauthorizedException(String message) {
        super(message);
    }
    
    public AdminUnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Exception for administrative system errors.
 */
class AdminSystemException extends RuntimeException {
    public AdminSystemException(String message) {
        super(message);
    }
    
    public AdminSystemException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Exception for administrative data retention errors.
 */
class AdminDataRetentionException extends RuntimeException {
    public AdminDataRetentionException(String message) {
        super(message);
    }
    
    public AdminDataRetentionException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Exception for administrative environment errors.
 */
class AdminEnvironmentException extends RuntimeException {
    public AdminEnvironmentException(String message) {
        super(message);
    }
    
    public AdminEnvironmentException(String message, Throwable cause) {
        super(message, cause);
    }
}