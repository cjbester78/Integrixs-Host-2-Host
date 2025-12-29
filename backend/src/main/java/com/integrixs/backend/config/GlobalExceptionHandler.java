package com.integrixs.backend.config;

import com.integrixs.core.logging.EnhancedLogger;
import com.integrixs.core.sftp.SftpOperationException;
import com.integrixs.shared.dto.BankOperationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for REST API
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final EnhancedLogger logger = EnhancedLogger.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SftpOperationException.class)
    public ResponseEntity<BankOperationResponse> handleSftpOperationException(
            SftpOperationException ex, WebRequest request) {
        
        logger.error("SFTP operation failed: {}", ex.getMessage(), ex);
        
        BankOperationResponse response = BankOperationResponse.error("UNKNOWN", 
            "SFTP operation failed: " + ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        
        logger.error("Invalid argument: {}", ex.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Invalid argument: " + ex.getMessage());
        response.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex, WebRequest request) {
        
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Internal server error: " + ex.getMessage());
        response.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}