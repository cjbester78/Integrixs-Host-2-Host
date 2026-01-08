package com.integrixs.backend.service;

import com.integrixs.backend.dto.ExecutionValidationResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Comprehensive DTO validation service providing validation framework for all request/response DTOs.
 * Implements strategy pattern with reusable validation rules and error collection.
 * Part of Phase 5.3 DTO enhancement following OOP principles.
 */
@Service
public class DtoValidationService {
    
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"
    );
    
    private static final Set<String> VALID_SORT_DIRECTIONS = Set.of("ASC", "DESC", "asc", "desc");
    
    /**
     * Validate DTO using builder pattern for collecting validation results.
     */
    public <T> DtoValidationResult validateDto(T dto, String context) {
        if (dto == null) {
            return DtoValidationResult.failure("DTO object cannot be null", context);
        }
        
        DtoValidationBuilder builder = DtoValidationResult.builder()
            .context(context)
            .validatedObject(dto.getClass().getSimpleName());
            
        return builder.build();
    }
    
    /**
     * Validate string field with multiple validation rules.
     */
    public ValidationFieldResult validateStringField(String value, String fieldName, 
                                                   ValidationRules rules) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Null/Required validation
        if (value == null) {
            if (rules.isRequired()) {
                errors.add(fieldName + " is required and cannot be null");
            }
            return new ValidationFieldResult(fieldName, errors, warnings);
        }
        
        // Empty/Blank validation
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            if (rules.isRequired()) {
                errors.add(fieldName + " cannot be empty or blank");
            }
            return new ValidationFieldResult(fieldName, errors, warnings);
        }
        
        // Length validation
        if (rules.getMinLength() > 0 && trimmed.length() < rules.getMinLength()) {
            errors.add(fieldName + " must be at least " + rules.getMinLength() + " characters long");
        }
        
        if (rules.getMaxLength() > 0 && trimmed.length() > rules.getMaxLength()) {
            errors.add(fieldName + " must not exceed " + rules.getMaxLength() + " characters");
        }
        
        // Pattern validation
        if (rules.getPattern() != null && !rules.getPattern().matcher(trimmed).matches()) {
            errors.add(fieldName + " has invalid format");
        }
        
        // Custom predicate validation
        if (rules.getCustomValidator() != null && !rules.getCustomValidator().test(trimmed)) {
            errors.add(fieldName + " " + rules.getCustomErrorMessage());
        }
        
        return new ValidationFieldResult(fieldName, errors, warnings);
    }
    
    /**
     * Validate UUID field.
     */
    public ValidationFieldResult validateUuidField(String value, String fieldName, boolean required) {
        ValidationRules rules = ValidationRules.builder()
            .required(required)
            .pattern(UUID_PATTERN)
            .build();
            
        return validateStringField(value, fieldName, rules);
    }
    
    /**
     * Validate email field.
     */
    public ValidationFieldResult validateEmailField(String value, String fieldName, boolean required) {
        ValidationRules rules = ValidationRules.builder()
            .required(required)
            .pattern(EMAIL_PATTERN)
            .minLength(3)
            .maxLength(320)
            .build();
            
        return validateStringField(value, fieldName, rules);
    }
    
    /**
     * Validate numeric field with range validation.
     */
    public ValidationFieldResult validateNumericField(Number value, String fieldName, 
                                                    NumericValidationRules rules) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (value == null) {
            if (rules.isRequired()) {
                errors.add(fieldName + " is required and cannot be null");
            }
            return new ValidationFieldResult(fieldName, errors, warnings);
        }
        
        double doubleValue = value.doubleValue();
        
        if (rules.getMinValue() != null && doubleValue < rules.getMinValue()) {
            errors.add(fieldName + " must be at least " + rules.getMinValue());
        }
        
        if (rules.getMaxValue() != null && doubleValue > rules.getMaxValue()) {
            errors.add(fieldName + " must not exceed " + rules.getMaxValue());
        }
        
        if (rules.isPositiveOnly() && doubleValue <= 0) {
            errors.add(fieldName + " must be positive");
        }
        
        return new ValidationFieldResult(fieldName, errors, warnings);
    }
    
    /**
     * Validate pagination parameters.
     */
    public DtoValidationResult validatePaginationParameters(Integer page, Integer size, 
                                                           String sortBy, String sortDirection) {
        DtoValidationBuilder builder = DtoValidationResult.builder()
            .context("Pagination Parameters");
        
        // Validate page
        if (page != null) {
            ValidationFieldResult pageResult = validateNumericField(page, "page", 
                NumericValidationRules.builder()
                    .required(false)
                    .minValue(0.0)
                    .maxValue(10000.0)
                    .build());
            builder.addFieldResult(pageResult);
        }
        
        // Validate size
        if (size != null) {
            ValidationFieldResult sizeResult = validateNumericField(size, "size",
                NumericValidationRules.builder()
                    .required(false)
                    .minValue(1.0)
                    .maxValue(1000.0)
                    .build());
            builder.addFieldResult(sizeResult);
        }
        
        // Validate sortBy
        if (sortBy != null) {
            ValidationFieldResult sortByResult = validateStringField(sortBy, "sortBy",
                ValidationRules.builder()
                    .required(false)
                    .minLength(1)
                    .maxLength(50)
                    .customValidator(sort -> isValidSortField(sort))
                    .customErrorMessage("is not a valid sort field")
                    .build());
            builder.addFieldResult(sortByResult);
        }
        
        // Validate sortDirection
        if (sortDirection != null) {
            ValidationFieldResult sortDirResult = validateStringField(sortDirection, "sortDirection",
                ValidationRules.builder()
                    .required(false)
                    .customValidator(VALID_SORT_DIRECTIONS::contains)
                    .customErrorMessage("must be one of: ASC, DESC")
                    .build());
            builder.addFieldResult(sortDirResult);
        }
        
        return builder.build();
    }
    
    /**
     * Validate date range parameters.
     */
    public DtoValidationResult validateDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        DtoValidationBuilder builder = DtoValidationResult.builder()
            .context("Date Range Parameters");
        
        if (startDate != null && endDate != null) {
            if (startDate.isAfter(endDate)) {
                builder.addError("Start date must be before end date");
            }
            
            LocalDateTime now = LocalDateTime.now();
            if (startDate.isAfter(now)) {
                builder.addWarning("Start date is in the future");
            }
            
            if (endDate.isAfter(now.plusDays(1))) {
                builder.addWarning("End date is more than 1 day in the future");
            }
        }
        
        return builder.build();
    }
    
    /**
     * Check if sort field is valid (extensible for different entities).
     */
    private boolean isValidSortField(String sortField) {
        Set<String> commonSortFields = Set.of(
            "id", "name", "createdAt", "updatedAt", "status", 
            "startedAt", "completedAt", "timestamp", "priority",
            "type", "category", "active", "enabled"
        );
        return commonSortFields.contains(sortField);
    }
    
    // Immutable result classes
    
    /**
     * Immutable validation result for DTO validation operations.
     */
    public static final class DtoValidationResult {
        private final boolean valid;
        private final String context;
        private final String validatedObject;
        private final List<String> errors;
        private final List<String> warnings;
        private final LocalDateTime validatedAt;
        private final List<ValidationFieldResult> fieldResults;
        
        private DtoValidationResult(DtoValidationBuilder builder) {
            this.valid = builder.errors.isEmpty();
            this.context = builder.context;
            this.validatedObject = builder.validatedObject;
            this.errors = Collections.unmodifiableList(builder.errors);
            this.warnings = Collections.unmodifiableList(builder.warnings);
            this.validatedAt = LocalDateTime.now();
            this.fieldResults = Collections.unmodifiableList(builder.fieldResults);
        }
        
        // Getters
        public boolean isValid() { return valid; }
        public String getContext() { return context; }
        public String getValidatedObject() { return validatedObject; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public LocalDateTime getValidatedAt() { return validatedAt; }
        public List<ValidationFieldResult> getFieldResults() { return fieldResults; }
        
        public boolean hasErrors() { return !errors.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
        public int getErrorCount() { return errors.size(); }
        public int getWarningCount() { return warnings.size(); }
        
        public String getFirstError() {
            return errors.isEmpty() ? null : errors.get(0);
        }
        
        public String getErrorsAsString() {
            return String.join(", ", errors);
        }
        
        public static DtoValidationBuilder builder() {
            return new DtoValidationBuilder();
        }
        
        public static DtoValidationResult success(String context) {
            return builder().context(context).build();
        }
        
        public static DtoValidationResult failure(String error, String context) {
            return builder().context(context).addError(error).build();
        }
        
        @Override
        public String toString() {
            return String.format("DtoValidationResult{valid=%b, context='%s', errorCount=%d, warningCount=%d}", 
                               valid, context, getErrorCount(), getWarningCount());
        }
    }
    
    /**
     * Builder for DtoValidationResult.
     */
    public static class DtoValidationBuilder {
        private String context = "DTO Validation";
        private String validatedObject = "Unknown";
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private List<ValidationFieldResult> fieldResults = new ArrayList<>();
        
        public DtoValidationBuilder context(String context) {
            this.context = context;
            return this;
        }
        
        public DtoValidationBuilder validatedObject(String validatedObject) {
            this.validatedObject = validatedObject;
            return this;
        }
        
        public DtoValidationBuilder addError(String error) {
            this.errors.add(error);
            return this;
        }
        
        public DtoValidationBuilder addWarning(String warning) {
            this.warnings.add(warning);
            return this;
        }
        
        public DtoValidationBuilder addFieldResult(ValidationFieldResult fieldResult) {
            this.fieldResults.add(fieldResult);
            this.errors.addAll(fieldResult.getErrors());
            this.warnings.addAll(fieldResult.getWarnings());
            return this;
        }
        
        public DtoValidationResult build() {
            return new DtoValidationResult(this);
        }
    }
    
    /**
     * Immutable field validation result.
     */
    public static final class ValidationFieldResult {
        private final String fieldName;
        private final List<String> errors;
        private final List<String> warnings;
        
        public ValidationFieldResult(String fieldName, List<String> errors, List<String> warnings) {
            this.fieldName = fieldName;
            this.errors = Collections.unmodifiableList(errors != null ? errors : Collections.emptyList());
            this.warnings = Collections.unmodifiableList(warnings != null ? warnings : Collections.emptyList());
        }
        
        public String getFieldName() { return fieldName; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public boolean hasErrors() { return !errors.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
    }
    
    /**
     * Validation rules for string fields.
     */
    public static final class ValidationRules {
        private final boolean required;
        private final int minLength;
        private final int maxLength;
        private final Pattern pattern;
        private final Predicate<String> customValidator;
        private final String customErrorMessage;
        
        private ValidationRules(Builder builder) {
            this.required = builder.required;
            this.minLength = builder.minLength;
            this.maxLength = builder.maxLength;
            this.pattern = builder.pattern;
            this.customValidator = builder.customValidator;
            this.customErrorMessage = builder.customErrorMessage;
        }
        
        public boolean isRequired() { return required; }
        public int getMinLength() { return minLength; }
        public int getMaxLength() { return maxLength; }
        public Pattern getPattern() { return pattern; }
        public Predicate<String> getCustomValidator() { return customValidator; }
        public String getCustomErrorMessage() { return customErrorMessage; }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private boolean required = false;
            private int minLength = 0;
            private int maxLength = 0;
            private Pattern pattern;
            private Predicate<String> customValidator;
            private String customErrorMessage = "is invalid";
            
            public Builder required(boolean required) {
                this.required = required;
                return this;
            }
            
            public Builder minLength(int minLength) {
                this.minLength = minLength;
                return this;
            }
            
            public Builder maxLength(int maxLength) {
                this.maxLength = maxLength;
                return this;
            }
            
            public Builder pattern(Pattern pattern) {
                this.pattern = pattern;
                return this;
            }
            
            public Builder customValidator(Predicate<String> validator) {
                this.customValidator = validator;
                return this;
            }
            
            public Builder customErrorMessage(String message) {
                this.customErrorMessage = message;
                return this;
            }
            
            public ValidationRules build() {
                return new ValidationRules(this);
            }
        }
    }
    
    /**
     * Validation rules for numeric fields.
     */
    public static final class NumericValidationRules {
        private final boolean required;
        private final Double minValue;
        private final Double maxValue;
        private final boolean positiveOnly;
        
        private NumericValidationRules(Builder builder) {
            this.required = builder.required;
            this.minValue = builder.minValue;
            this.maxValue = builder.maxValue;
            this.positiveOnly = builder.positiveOnly;
        }
        
        public boolean isRequired() { return required; }
        public Double getMinValue() { return minValue; }
        public Double getMaxValue() { return maxValue; }
        public boolean isPositiveOnly() { return positiveOnly; }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private boolean required = false;
            private Double minValue;
            private Double maxValue;
            private boolean positiveOnly = false;
            
            public Builder required(boolean required) {
                this.required = required;
                return this;
            }
            
            public Builder minValue(Double minValue) {
                this.minValue = minValue;
                return this;
            }
            
            public Builder maxValue(Double maxValue) {
                this.maxValue = maxValue;
                return this;
            }
            
            public Builder positiveOnly(boolean positiveOnly) {
                this.positiveOnly = positiveOnly;
                return this;
            }
            
            public NumericValidationRules build() {
                return new NumericValidationRules(this);
            }
        }
    }
}