package com.integrixs.backend.service;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.backend.dto.ErrorDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for standardizing API responses across controllers.
 * Provides consistent response formats, pagination, and metadata handling.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
@Service
public class ResponseStandardizationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ResponseStandardizationService.class);
    
    /**
     * Create successful response with data.
     */
    public <T> ResponseEntity<ApiResponse<T>> success(T data) {
        return success(data, "Operation completed successfully");
    }
    
    /**
     * Create successful response with data and custom message.
     */
    public <T> ResponseEntity<ApiResponse<T>> success(T data, String message) {
        ApiResponse<T> response = ApiResponse.success(message, data);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Create successful response with data, message, and metadata.
     */
    public <T> ResponseEntity<ApiResponse<T>> success(T data, String message, Map<String, Object> metadata) {
        StandardizedResponse<T> response = StandardizedResponse.<T>builder()
            .success(true)
            .message(message)
            .data(data)
            .metadata(metadata)
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity.ok(ApiResponse.success(message, data));
    }
    
    /**
     * Create paginated success response.
     */
    public <T> ResponseEntity<ApiResponse<PaginatedResponse<T>>> successPaginated(
            List<T> items, int page, int size, long totalElements, String message) {
        
        PaginatedResponse<T> paginatedData = PaginatedResponse.<T>builder()
            .content(items)
            .page(page)
            .size(size)
            .totalElements(totalElements)
            .totalPages(calculateTotalPages(totalElements, size))
            .hasNext(hasNextPage(page, totalElements, size))
            .hasPrevious(page > 0)
            .build();
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("pagination", Map.of(
            "page", page,
            "size", size,
            "totalElements", totalElements,
            "totalPages", paginatedData.getTotalPages()
        ));
        
        logger.debug("Created paginated response: page={}, size={}, totalElements={}, totalPages={}", 
                    page, size, totalElements, paginatedData.getTotalPages());
        
        return success(paginatedData, message, metadata);
    }
    
    /**
     * Create created response (201) with data.
     */
    public <T> ResponseEntity<ApiResponse<T>> created(T data, String message) {
        ApiResponse<T> response = ApiResponse.success(message, data);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Create accepted response (202) for async operations.
     */
    public <T> ResponseEntity<ApiResponse<T>> accepted(T data, String message) {
        ApiResponse<T> response = ApiResponse.success(message, data);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
    
    /**
     * Create no content response (204).
     */
    public ResponseEntity<ApiResponse<Void>> noContent(String message) {
        ApiResponse<Void> response = ApiResponse.success(message, null);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(response);
    }
    
    /**
     * Create bad request response (400).
     */
    public ResponseEntity<ApiResponse<Void>> badRequest(String message) {
        ApiResponse<Void> response = ApiResponse.error(message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Create bad request response (400) with error details.
     */
    public ResponseEntity<ApiResponse<ErrorDetails>> badRequest(String message, ErrorDetails errorDetails) {
        ApiResponse<ErrorDetails> response = ApiResponse.error(message, errorDetails);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Create unauthorized response (401).
     */
    public ResponseEntity<ApiResponse<ErrorDetails>> unauthorized(String message) {
        ErrorDetails errorDetails = ErrorDetails.simple(
            generateCorrelationId(), "UNAUTHORIZED", message
        );
        ApiResponse<ErrorDetails> response = ApiResponse.error(message, errorDetails);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
    
    /**
     * Create forbidden response (403).
     */
    public ResponseEntity<ApiResponse<ErrorDetails>> forbidden(String message) {
        ErrorDetails errorDetails = ErrorDetails.simple(
            generateCorrelationId(), "FORBIDDEN", message
        );
        ApiResponse<ErrorDetails> response = ApiResponse.error(message, errorDetails);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }
    
    /**
     * Create not found response (404).
     */
    public ResponseEntity<ApiResponse<Void>> notFound(String message) {
        ApiResponse<Void> response = ApiResponse.error(message);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    /**
     * Create not found response (404) with error details.
     */
    public ResponseEntity<ApiResponse<ErrorDetails>> notFound(String message, ErrorDetails errorDetails) {
        ApiResponse<ErrorDetails> response = ApiResponse.error(message, errorDetails);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    /**
     * Create conflict response (409).
     */
    public ResponseEntity<ApiResponse<ErrorDetails>> conflict(String message, ErrorDetails errorDetails) {
        ApiResponse<ErrorDetails> response = ApiResponse.error(message, errorDetails);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    
    /**
     * Create internal server error response (500).
     */
    public ResponseEntity<ApiResponse<Void>> internalServerError(String message) {
        ApiResponse<Void> response = ApiResponse.error(message);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * Create internal server error response (500) with error details.
     */
    public ResponseEntity<ApiResponse<ErrorDetails>> internalServerError(String message, ErrorDetails errorDetails) {
        ApiResponse<ErrorDetails> response = ApiResponse.error(message, errorDetails);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * Create service unavailable response (503).
     */
    public ResponseEntity<ApiResponse<ErrorDetails>> serviceUnavailable(String message, ErrorDetails errorDetails) {
        ApiResponse<ErrorDetails> response = ApiResponse.error(message, errorDetails);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
    
    /**
     * Create custom status response.
     */
    public <T> ResponseEntity<ApiResponse<T>> customStatus(HttpStatus status, T data, String message) {
        ApiResponse<T> response = status.is2xxSuccessful() ? 
            ApiResponse.success(message, data) : 
            ApiResponse.error(message, data);
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * Create response with timing information.
     */
    public <T> ResponseEntity<ApiResponse<T>> successWithTiming(T data, String message, long durationMs) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("processingTime", durationMs + "ms");
        metadata.put("timestamp", LocalDateTime.now());
        
        logger.debug("Response generated in {} ms: {}", durationMs, message);
        return success(data, message, metadata);
    }
    
    /**
     * Create response with correlation tracking.
     */
    public <T> ResponseEntity<ApiResponse<T>> successWithCorrelation(T data, String message, String correlationId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("correlationId", correlationId);
        metadata.put("timestamp", LocalDateTime.now());
        
        return success(data, message, metadata);
    }
    
    /**
     * Calculate total pages for pagination.
     */
    private int calculateTotalPages(long totalElements, int size) {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
    
    /**
     * Check if there is a next page.
     */
    private boolean hasNextPage(int currentPage, long totalElements, int size) {
        return (long) (currentPage + 1) * size < totalElements;
    }
    
    /**
     * Generate correlation ID for tracking.
     */
    private String generateCorrelationId() {
        return "RESP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}

/**
 * Immutable standardized response wrapper with metadata and timing.
 */
class StandardizedResponse<T> {
    private final boolean success;
    private final String message;
    private final T data;
    private final Map<String, Object> metadata;
    private final LocalDateTime timestamp;
    private final String correlationId;
    
    private StandardizedResponse(Builder<T> builder) {
        this.success = builder.success;
        this.message = builder.message;
        this.data = builder.data;
        this.metadata = builder.metadata != null ? 
            Collections.unmodifiableMap(new HashMap<>(builder.metadata)) : Collections.emptyMap();
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
        this.correlationId = builder.correlationId != null ? builder.correlationId : UUID.randomUUID().toString();
    }
    
    // Getters
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public Map<String, Object> getMetadata() { return metadata; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getCorrelationId() { return correlationId; }
    
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }
    
    public static class Builder<T> {
        private boolean success;
        private String message;
        private T data;
        private Map<String, Object> metadata;
        private LocalDateTime timestamp;
        private String correlationId;
        
        public Builder<T> success(boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder<T> message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder<T> data(T data) {
            this.data = data;
            return this;
        }
        
        public Builder<T> metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public Builder<T> timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder<T> correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }
        
        public StandardizedResponse<T> build() {
            return new StandardizedResponse<>(this);
        }
    }
}

/**
 * Immutable paginated response wrapper.
 */
class PaginatedResponse<T> {
    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean hasNext;
    private final boolean hasPrevious;
    
    private PaginatedResponse(Builder<T> builder) {
        this.content = builder.content != null ? 
            Collections.unmodifiableList(new ArrayList<>(builder.content)) : Collections.emptyList();
        this.page = builder.page;
        this.size = builder.size;
        this.totalElements = builder.totalElements;
        this.totalPages = builder.totalPages;
        this.hasNext = builder.hasNext;
        this.hasPrevious = builder.hasPrevious;
    }
    
    // Getters
    public List<T> getContent() { return content; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
    public boolean isHasNext() { return hasNext; }
    public boolean isHasPrevious() { return hasPrevious; }
    public boolean isEmpty() { return content.isEmpty(); }
    public int getContentSize() { return content.size(); }
    
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }
    
    public static class Builder<T> {
        private List<T> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean hasNext;
        private boolean hasPrevious;
        
        public Builder<T> content(List<T> content) {
            this.content = content;
            return this;
        }
        
        public Builder<T> page(int page) {
            this.page = page;
            return this;
        }
        
        public Builder<T> size(int size) {
            this.size = size;
            return this;
        }
        
        public Builder<T> totalElements(long totalElements) {
            this.totalElements = totalElements;
            return this;
        }
        
        public Builder<T> totalPages(int totalPages) {
            this.totalPages = totalPages;
            return this;
        }
        
        public Builder<T> hasNext(boolean hasNext) {
            this.hasNext = hasNext;
            return this;
        }
        
        public Builder<T> hasPrevious(boolean hasPrevious) {
            this.hasPrevious = hasPrevious;
            return this;
        }
        
        public PaginatedResponse<T> build() {
            return new PaginatedResponse<>(this);
        }
    }
}