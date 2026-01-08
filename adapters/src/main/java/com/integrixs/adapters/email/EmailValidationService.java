package com.integrixs.adapters.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Email validation service with comprehensive validation frameworks.
 * Provides validation for email addresses, configurations, and composition requests.
 * Part of Phase 3 email adapter refactoring following OOP principles.
 */
@Service
public class EmailValidationService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailValidationService.class);
    
    // Email regex pattern (basic RFC 5322 compliance)
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"
    );
    
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_LOCAL_PART_LENGTH = 64;
    private static final int MAX_DOMAIN_PART_LENGTH = 253;
    
    private final Map<String, EmailValidator> validators = new HashMap<>();
    
    public EmailValidationService() {
        // Register built-in validators
        registerValidator("address", new EmailAddressValidator());
        registerValidator("configuration", new EmailConfigurationValidator());
        registerValidator("composition", new EmailCompositionValidator());
        registerValidator("comprehensive", new ComprehensiveEmailValidator());
    }
    
    /**
     * Validate email address with comprehensive checks.
     */
    public EmailValidationResult validateEmailAddress(String emailAddress) {
        return validateEmailAddress(emailAddress, "comprehensive");
    }
    
    /**
     * Validate email address with specified validator type.
     */
    public EmailValidationResult validateEmailAddress(String emailAddress, String validatorType) {
        EmailValidator validator = getEmailValidator(validatorType);
        return validator.validateEmailAddress(emailAddress);
    }
    
    /**
     * Validate multiple email addresses.
     */
    public EmailValidationResult validateEmailAddresses(List<String> emailAddresses) {
        if (emailAddresses == null || emailAddresses.isEmpty()) {
            return EmailValidationResult.failure("Email address list cannot be empty");
        }
        
        LocalDateTime startTime = LocalDateTime.now();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Object> validationMetadata = new HashMap<>();
        
        int validCount = 0;
        int invalidCount = 0;
        List<String> duplicates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        
        for (int i = 0; i < emailAddresses.size(); i++) {
            String email = emailAddresses.get(i);
            
            if (email == null || email.trim().isEmpty()) {
                errors.add("Email address at position " + i + " is null or empty");
                invalidCount++;
                continue;
            }
            
            String normalizedEmail = email.trim().toLowerCase();
            if (seen.contains(normalizedEmail)) {
                duplicates.add(email);
                warnings.add("Duplicate email address: " + email);
            } else {
                seen.add(normalizedEmail);
            }
            
            EmailValidationResult result = validateEmailAddress(email);
            if (result.isValid()) {
                validCount++;
            } else {
                invalidCount++;
                errors.add("Email address '" + email + "': " + String.join(", ", result.getErrors()));
            }
        }
        
        validationMetadata.put("totalCount", emailAddresses.size());
        validationMetadata.put("validCount", validCount);
        validationMetadata.put("invalidCount", invalidCount);
        validationMetadata.put("duplicateCount", duplicates.size());
        validationMetadata.put("duplicateAddresses", duplicates);
        
        long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
        
        boolean isValid = errors.isEmpty();
        return new EmailValidationResult(isValid, errors, warnings, validationMetadata, duration);
    }
    
    /**
     * Validate email configuration object.
     */
    public EmailValidationResult validateEmailConfiguration(Map<String, Object> configuration) {
        EmailValidator validator = getEmailValidator("configuration");
        return validator.validateEmailConfiguration(configuration);
    }
    
    /**
     * Validate email composition request.
     */
    public EmailValidationResult validateEmailComposition(EmailCompositionRequest request) {
        EmailValidator validator = getEmailValidator("composition");
        return validator.validateEmailComposition(request);
    }
    
    /**
     * Register email validator.
     */
    public void registerValidator(String type, EmailValidator validator) {
        if (type != null && validator != null) {
            validators.put(type.toLowerCase(), validator);
            logger.debug("Registered email validator: {}", type);
        }
    }
    
    /**
     * Get email validator by type.
     */
    private EmailValidator getEmailValidator(String validatorType) {
        String type = validatorType != null ? validatorType.toLowerCase() : "comprehensive";
        EmailValidator validator = validators.get(type);
        
        if (validator == null) {
            logger.warn("Unknown email validator: {}, using comprehensive validator", validatorType);
            validator = validators.get("comprehensive");
        }
        
        return validator;
    }
    
    /**
     * Get available validator types.
     */
    public Set<String> getAvailableValidatorTypes() {
        return new HashSet<>(validators.keySet());
    }
    
    /**
     * Quick email address format check.
     */
    public boolean isValidEmailFormat(String emailAddress) {
        if (emailAddress == null || emailAddress.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = emailAddress.trim();
        return EMAIL_PATTERN.matcher(trimmed).matches() && trimmed.length() <= MAX_EMAIL_LENGTH;
    }
}

/**
 * Interface for email validation strategies.
 */
interface EmailValidator {
    EmailValidationResult validateEmailAddress(String emailAddress);
    EmailValidationResult validateEmailConfiguration(Map<String, Object> configuration);
    EmailValidationResult validateEmailComposition(EmailCompositionRequest request);
    String getValidatorName();
    String getValidatorDescription();
}

/**
 * Basic email address validator.
 */
class EmailAddressValidator implements EmailValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailAddressValidator.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"
    );
    
    @Override
    public EmailValidationResult validateEmailAddress(String emailAddress) {
        LocalDateTime startTime = LocalDateTime.now();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (emailAddress == null || emailAddress.trim().isEmpty()) {
            return EmailValidationResult.failure("Email address cannot be null or empty");
        }
        
        String email = emailAddress.trim();
        
        // Basic format validation
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            errors.add("Invalid email format");
        }
        
        // Length validation
        if (email.length() > 254) {
            errors.add("Email address too long (max 254 characters)");
        }
        
        // Local and domain part validation
        int atIndex = email.indexOf('@');
        if (atIndex > 0 && atIndex < email.length() - 1) {
            String localPart = email.substring(0, atIndex);
            String domainPart = email.substring(atIndex + 1);
            
            if (localPart.length() > 64) {
                errors.add("Local part too long (max 64 characters)");
            }
            
            if (domainPart.length() > 253) {
                errors.add("Domain part too long (max 253 characters)");
            }
        }
        
        long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
        return new EmailValidationResult(errors.isEmpty(), errors, warnings, null, duration);
    }
    
    @Override
    public EmailValidationResult validateEmailConfiguration(Map<String, Object> configuration) {
        return EmailValidationResult.success("Basic address validator does not validate configurations");
    }
    
    @Override
    public EmailValidationResult validateEmailComposition(EmailCompositionRequest request) {
        return EmailValidationResult.success("Basic address validator does not validate compositions");
    }
    
    @Override
    public String getValidatorName() {
        return "Email Address";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Basic email address format and length validation";
    }
}

/**
 * Email configuration validator.
 */
class EmailConfigurationValidator implements EmailValidator {
    
    @Override
    public EmailValidationResult validateEmailAddress(String emailAddress) {
        EmailAddressValidator addressValidator = new EmailAddressValidator();
        return addressValidator.validateEmailAddress(emailAddress);
    }
    
    @Override
    public EmailValidationResult validateEmailConfiguration(Map<String, Object> configuration) {
        LocalDateTime startTime = LocalDateTime.now();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (configuration == null || configuration.isEmpty()) {
            return EmailValidationResult.failure("Email configuration cannot be null or empty");
        }
        
        // Validate SMTP configuration
        validateSmtpConfiguration(configuration, errors, warnings);
        
        // Validate from address
        validateFromConfiguration(configuration, errors, warnings);
        
        // Validate recipient configuration
        validateRecipientConfiguration(configuration, errors, warnings);
        
        long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
        return new EmailValidationResult(errors.isEmpty(), errors, warnings, null, duration);
    }
    
    private void validateSmtpConfiguration(Map<String, Object> config, List<String> errors, List<String> warnings) {
        Object smtpHost = config.get("smtpHost");
        if (smtpHost == null || smtpHost.toString().trim().isEmpty()) {
            errors.add("SMTP host is required");
        }
        
        Object smtpPort = config.get("smtpPort");
        if (smtpPort != null) {
            try {
                int port = Integer.parseInt(smtpPort.toString());
                if (port < 1 || port > 65535) {
                    errors.add("SMTP port must be between 1 and 65535");
                }
            } catch (NumberFormatException e) {
                errors.add("SMTP port must be a valid number");
            }
        }
        
        Object smtpAuth = config.get("smtpAuth");
        if (smtpAuth != null && Boolean.parseBoolean(smtpAuth.toString())) {
            Object username = config.get("smtpUsername");
            Object password = config.get("smtpPassword");
            
            if (username == null || username.toString().trim().isEmpty()) {
                errors.add("SMTP username is required when authentication is enabled");
            }
            
            if (password == null || password.toString().trim().isEmpty()) {
                errors.add("SMTP password is required when authentication is enabled");
            }
        }
    }
    
    private void validateFromConfiguration(Map<String, Object> config, List<String> errors, List<String> warnings) {
        Object fromAddress = config.get("fromAddress");
        if (fromAddress == null || fromAddress.toString().trim().isEmpty()) {
            errors.add("From address is required");
        } else {
            EmailValidationResult addressResult = validateEmailAddress(fromAddress.toString());
            if (!addressResult.isValid()) {
                errors.add("Invalid from address: " + String.join(", ", addressResult.getErrors()));
            }
        }
        
        Object fromName = config.get("fromName");
        if (fromName != null && fromName.toString().length() > 100) {
            warnings.add("From name is very long (over 100 characters)");
        }
    }
    
    private void validateRecipientConfiguration(Map<String, Object> config, List<String> errors, List<String> warnings) {
        Object recipients = config.get("recipients");
        if (recipients != null) {
            if (recipients instanceof String) {
                String[] recipientArray = recipients.toString().split(",");
                for (String recipient : recipientArray) {
                    EmailValidationResult result = validateEmailAddress(recipient.trim());
                    if (!result.isValid()) {
                        errors.add("Invalid recipient address '" + recipient.trim() + "': " + 
                                  String.join(", ", result.getErrors()));
                    }
                }
            } else if (recipients instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> recipientList = (List<String>) recipients;
                for (String recipient : recipientList) {
                    EmailValidationResult result = validateEmailAddress(recipient);
                    if (!result.isValid()) {
                        errors.add("Invalid recipient address '" + recipient + "': " + 
                                  String.join(", ", result.getErrors()));
                    }
                }
            }
        }
    }
    
    @Override
    public EmailValidationResult validateEmailComposition(EmailCompositionRequest request) {
        return EmailValidationResult.success("Configuration validator does not validate compositions");
    }
    
    @Override
    public String getValidatorName() {
        return "Email Configuration";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Email adapter configuration validation including SMTP settings and addresses";
    }
}

/**
 * Email composition validator.
 */
class EmailCompositionValidator implements EmailValidator {
    
    @Override
    public EmailValidationResult validateEmailAddress(String emailAddress) {
        EmailAddressValidator addressValidator = new EmailAddressValidator();
        return addressValidator.validateEmailAddress(emailAddress);
    }
    
    @Override
    public EmailValidationResult validateEmailConfiguration(Map<String, Object> configuration) {
        EmailConfigurationValidator configValidator = new EmailConfigurationValidator();
        return configValidator.validateEmailConfiguration(configuration);
    }
    
    @Override
    public EmailValidationResult validateEmailComposition(EmailCompositionRequest request) {
        LocalDateTime startTime = LocalDateTime.now();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (request == null) {
            return EmailValidationResult.failure("Email composition request cannot be null");
        }
        
        // Validate from address
        if (request.getFromAddress() == null || request.getFromAddress().trim().isEmpty()) {
            errors.add("From address is required");
        } else {
            EmailValidationResult fromResult = validateEmailAddress(request.getFromAddress());
            if (!fromResult.isValid()) {
                errors.add("Invalid from address: " + String.join(", ", fromResult.getErrors()));
            }
        }
        
        // Validate recipients
        if (request.getRecipients() == null || request.getRecipients().isEmpty()) {
            errors.add("At least one recipient is required");
        } else {
            for (String recipient : request.getRecipients()) {
                EmailValidationResult recipientResult = validateEmailAddress(recipient);
                if (!recipientResult.isValid()) {
                    errors.add("Invalid recipient address '" + recipient + "': " + 
                              String.join(", ", recipientResult.getErrors()));
                }
            }
        }
        
        // Validate CC addresses
        if (request.getCcAddresses() != null) {
            for (String cc : request.getCcAddresses()) {
                EmailValidationResult ccResult = validateEmailAddress(cc);
                if (!ccResult.isValid()) {
                    errors.add("Invalid CC address '" + cc + "': " + String.join(", ", ccResult.getErrors()));
                }
            }
        }
        
        // Validate BCC addresses
        if (request.getBccAddresses() != null) {
            for (String bcc : request.getBccAddresses()) {
                EmailValidationResult bccResult = validateEmailAddress(bcc);
                if (!bccResult.isValid()) {
                    errors.add("Invalid BCC address '" + bcc + "': " + String.join(", ", bccResult.getErrors()));
                }
            }
        }
        
        // Validate subject and body (warnings only)
        if (request.getSubject() == null || request.getSubject().trim().isEmpty()) {
            warnings.add("Email subject is empty");
        }
        
        if (request.getBodyContent() == null || request.getBodyContent().trim().isEmpty()) {
            warnings.add("Email body content is empty");
        }
        
        long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
        return new EmailValidationResult(errors.isEmpty(), errors, warnings, null, duration);
    }
    
    @Override
    public String getValidatorName() {
        return "Email Composition";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Email composition request validation including all addresses and content";
    }
}

/**
 * Comprehensive email validator combining all validation types.
 */
class ComprehensiveEmailValidator implements EmailValidator {
    
    private final EmailAddressValidator addressValidator = new EmailAddressValidator();
    private final EmailConfigurationValidator configurationValidator = new EmailConfigurationValidator();
    private final EmailCompositionValidator compositionValidator = new EmailCompositionValidator();
    
    @Override
    public EmailValidationResult validateEmailAddress(String emailAddress) {
        return addressValidator.validateEmailAddress(emailAddress);
    }
    
    @Override
    public EmailValidationResult validateEmailConfiguration(Map<String, Object> configuration) {
        return configurationValidator.validateEmailConfiguration(configuration);
    }
    
    @Override
    public EmailValidationResult validateEmailComposition(EmailCompositionRequest request) {
        return compositionValidator.validateEmailComposition(request);
    }
    
    @Override
    public String getValidatorName() {
        return "Comprehensive";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Comprehensive email validation including addresses, configurations, and compositions";
    }
}