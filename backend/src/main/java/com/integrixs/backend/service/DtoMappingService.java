package com.integrixs.backend.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Comprehensive DTO mapping service providing entity-to-DTO conversions with proper separation of concerns.
 * Implements strategy pattern for different mapping approaches and maintains immutability.
 * Part of Phase 5.3 DTO enhancement following OOP principles.
 */
@Service
public class DtoMappingService {
    
    /**
     * Generic mapping method with transformation function.
     */
    public <T, R> R mapEntity(T entity, Function<T, R> mapper) {
        if (entity == null) {
            return null;
        }
        return mapper.apply(entity);
    }
    
    /**
     * Map list of entities to list of DTOs.
     */
    public <T, R> List<R> mapEntities(List<T> entities, Function<T, R> mapper) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream()
                .filter(Objects::nonNull)
                .map(mapper)
                .collect(Collectors.toList());
    }
    
    /**
     * Map entities with pagination metadata.
     */
    public <T, R> PaginatedMappingResult<R> mapEntitiesWithPagination(
            List<T> entities, 
            Function<T, R> mapper,
            PaginationMetadata pagination) {
        
        List<R> mappedEntities = mapEntities(entities, mapper);
        
        return PaginatedMappingResult.<R>builder()
            .content(mappedEntities)
            .pagination(pagination)
            .totalElements(pagination.getTotalElements())
            .build();
    }
    
    /**
     * Create mapping context for complex mappings with additional data.
     */
    public <T, R, C> List<R> mapEntitiesWithContext(List<T> entities, 
                                                   Function<MappingContextWrapper<T, C>, R> mapper, 
                                                   C context) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        
        return entities.stream()
                .filter(Objects::nonNull)
                .map(entity -> new MappingContextWrapper<>(entity, context))
                .map(mapper)
                .collect(Collectors.toList());
    }
    
    /**
     * Map optional entity to optional DTO.
     */
    public <T, R> Optional<R> mapOptional(Optional<T> entityOptional, Function<T, R> mapper) {
        return entityOptional.map(mapper);
    }
    
    /**
     * Map entity with validation.
     */
    public <T, R> MappingResult<R> mapWithValidation(T entity, 
                                                   Function<T, R> mapper, 
                                                   Function<R, List<String>> validator) {
        if (entity == null) {
            return MappingResult.failure("Source entity cannot be null");
        }
        
        try {
            R mappedDto = mapper.apply(entity);
            if (mappedDto == null) {
                return MappingResult.failure("Mapping function returned null");
            }
            
            List<String> validationErrors = validator.apply(mappedDto);
            if (validationErrors == null || validationErrors.isEmpty()) {
                return MappingResult.success(mappedDto);
            } else {
                return MappingResult.failure("Validation failed", validationErrors);
            }
            
        } catch (Exception e) {
            return MappingResult.failure("Mapping failed: " + e.getMessage());
        }
    }
    
    /**
     * Bidirectional mapping for request/response scenarios.
     */
    public <E, D> BidirectionalMapper<E, D> createBidirectionalMapper(
            Function<E, D> entityToDto,
            Function<D, E> dtoToEntity) {
        return new BidirectionalMapper<>(entityToDto, dtoToEntity);
    }
    
    // Helper classes and result objects
    
    /**
     * Immutable mapping result with success/failure handling.
     */
    public static final class MappingResult<T> {
        private final boolean success;
        private final T data;
        private final String errorMessage;
        private final List<String> validationErrors;
        private final LocalDateTime mappedAt;
        
        private MappingResult(boolean success, T data, String errorMessage, List<String> validationErrors) {
            this.success = success;
            this.data = data;
            this.errorMessage = errorMessage;
            this.validationErrors = Collections.unmodifiableList(
                validationErrors != null ? validationErrors : Collections.emptyList());
            this.mappedAt = LocalDateTime.now();
        }
        
        public boolean isSuccess() { return success; }
        public T getData() { return data; }
        public String getErrorMessage() { return errorMessage; }
        public List<String> getValidationErrors() { return validationErrors; }
        public LocalDateTime getMappedAt() { return mappedAt; }
        
        public boolean hasValidationErrors() { return !validationErrors.isEmpty(); }
        public String getFirstValidationError() { 
            return validationErrors.isEmpty() ? null : validationErrors.get(0);
        }
        
        public static <T> MappingResult<T> success(T data) {
            return new MappingResult<>(true, data, null, null);
        }
        
        public static <T> MappingResult<T> failure(String errorMessage) {
            return new MappingResult<>(false, null, errorMessage, null);
        }
        
        public static <T> MappingResult<T> failure(String errorMessage, List<String> validationErrors) {
            return new MappingResult<>(false, null, errorMessage, validationErrors);
        }
        
        @Override
        public String toString() {
            return String.format("MappingResult{success=%b, hasData=%b, errorMessage='%s', validationErrorCount=%d}", 
                               success, data != null, errorMessage, validationErrors.size());
        }
    }
    
    /**
     * Immutable paginated mapping result.
     */
    public static final class PaginatedMappingResult<T> {
        private final List<T> content;
        private final PaginationMetadata pagination;
        private final long totalElements;
        private final LocalDateTime mappedAt;
        
        private PaginatedMappingResult(Builder<T> builder) {
            this.content = Collections.unmodifiableList(
                builder.content != null ? builder.content : Collections.emptyList());
            this.pagination = builder.pagination;
            this.totalElements = builder.totalElements;
            this.mappedAt = LocalDateTime.now();
        }
        
        public List<T> getContent() { return content; }
        public PaginationMetadata getPagination() { return pagination; }
        public long getTotalElements() { return totalElements; }
        public LocalDateTime getMappedAt() { return mappedAt; }
        
        public int getContentSize() { return content.size(); }
        public boolean hasContent() { return !content.isEmpty(); }
        public boolean isFirstPage() { 
            return pagination != null && pagination.getPage() == 0; 
        }
        public boolean isLastPage() { 
            return pagination != null && 
                   (pagination.getPage() + 1) * pagination.getSize() >= totalElements; 
        }
        
        public static <T> Builder<T> builder() {
            return new Builder<>();
        }
        
        public static class Builder<T> {
            private List<T> content;
            private PaginationMetadata pagination;
            private long totalElements;
            
            public Builder<T> content(List<T> content) {
                this.content = content;
                return this;
            }
            
            public Builder<T> pagination(PaginationMetadata pagination) {
                this.pagination = pagination;
                return this;
            }
            
            public Builder<T> totalElements(long totalElements) {
                this.totalElements = totalElements;
                return this;
            }
            
            public PaginatedMappingResult<T> build() {
                return new PaginatedMappingResult<>(this);
            }
        }
        
        @Override
        public String toString() {
            return String.format("PaginatedMappingResult{contentSize=%d, totalElements=%d, page=%d, isLastPage=%b}", 
                               getContentSize(), totalElements, 
                               pagination != null ? pagination.getPage() : -1, 
                               isLastPage());
        }
    }
    
    /**
     * Immutable pagination metadata.
     */
    public static final class PaginationMetadata {
        private final int page;
        private final int size;
        private final long totalElements;
        private final int totalPages;
        private final String sortBy;
        private final String sortDirection;
        
        public PaginationMetadata(int page, int size, long totalElements, String sortBy, String sortDirection) {
            this.page = Math.max(0, page);
            this.size = Math.max(1, size);
            this.totalElements = Math.max(0, totalElements);
            this.totalPages = (int) Math.ceil((double) totalElements / this.size);
            this.sortBy = sortBy;
            this.sortDirection = sortDirection;
        }
        
        public int getPage() { return page; }
        public int getSize() { return size; }
        public long getTotalElements() { return totalElements; }
        public int getTotalPages() { return totalPages; }
        public String getSortBy() { return sortBy; }
        public String getSortDirection() { return sortDirection; }
        
        public int getOffset() { return page * size; }
        public boolean hasNext() { return page < totalPages - 1; }
        public boolean hasPrevious() { return page > 0; }
        
        @Override
        public String toString() {
            return String.format("PaginationMetadata{page=%d, size=%d, totalElements=%d, totalPages=%d, sortBy='%s'}", 
                               page, size, totalElements, totalPages, sortBy);
        }
    }
    
    /**
     * Wrapper for entity with additional mapping context.
     */
    public static final class MappingContextWrapper<T, C> {
        private final T entity;
        private final C context;
        
        public MappingContextWrapper(T entity, C context) {
            this.entity = entity;
            this.context = context;
        }
        
        public T getEntity() { return entity; }
        public C getContext() { return context; }
    }
    
    /**
     * Bidirectional mapper for entity-DTO conversions.
     */
    public static final class BidirectionalMapper<E, D> {
        private final Function<E, D> entityToDto;
        private final Function<D, E> dtoToEntity;
        
        public BidirectionalMapper(Function<E, D> entityToDto, Function<D, E> dtoToEntity) {
            this.entityToDto = Objects.requireNonNull(entityToDto, "Entity-to-DTO mapper cannot be null");
            this.dtoToEntity = Objects.requireNonNull(dtoToEntity, "DTO-to-entity mapper cannot be null");
        }
        
        public D toDto(E entity) {
            return entityToDto.apply(entity);
        }
        
        public E toEntity(D dto) {
            return dtoToEntity.apply(dto);
        }
        
        public List<D> toDtos(List<E> entities) {
            return entities.stream()
                    .filter(Objects::nonNull)
                    .map(entityToDto)
                    .collect(Collectors.toList());
        }
        
        public List<E> toEntities(List<D> dtos) {
            return dtos.stream()
                    .filter(Objects::nonNull)
                    .map(dtoToEntity)
                    .collect(Collectors.toList());
        }
        
        public Optional<D> toDto(Optional<E> entityOptional) {
            return entityOptional.map(entityToDto);
        }
        
        public Optional<E> toEntity(Optional<D> dtoOptional) {
            return dtoOptional.map(dtoToEntity);
        }
    }
    
    /**
     * Mapping strategy interface for different mapping approaches.
     */
    @FunctionalInterface
    public interface MappingStrategy<T, R> {
        R map(T source);
        
        default List<R> mapList(List<T> sources) {
            return sources.stream()
                    .filter(Objects::nonNull)
                    .map(this::map)
                    .collect(Collectors.toList());
        }
    }
    
    /**
     * Create mapping strategy with validation.
     */
    public <T, R> MappingStrategy<T, R> createValidatingStrategy(
            Function<T, R> mapper, 
            Function<R, Boolean> validator,
            String errorMessage) {
        
        return source -> {
            R result = mapper.apply(source);
            if (result != null && !validator.apply(result)) {
                throw new IllegalArgumentException(errorMessage);
            }
            return result;
        };
    }
}