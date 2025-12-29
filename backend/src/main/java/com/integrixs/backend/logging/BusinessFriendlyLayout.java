package com.integrixs.backend.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.LayoutBase;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Custom Logback layout that transforms technical log messages into business-friendly format
 */
public class BusinessFriendlyLayout extends LayoutBase<ILoggingEvent> {
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    
    @Override
    public String doLayout(ILoggingEvent event) {
        String originalMessage = event.getFormattedMessage();
        
        // Filter out technical messages that shouldn't appear in business logs
        if (shouldFilterMessage(originalMessage, event.getLoggerName())) {
            return ""; // Don't log this message
        }
        
        long eventTime = event.getTimeStamp();
        String timestamp = java.time.Instant.ofEpochMilli(eventTime)
            .atZone(java.time.ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss:SSS"));
        
        // Get context information
        Map<String, String> mdc = event.getMDCPropertyMap();
        String flowName = mdc != null ? mdc.get("flowName") : "";
        String executionId = mdc != null ? mdc.get("executionId") : "";
        
        // Transform technical messages to business-friendly messages
        String businessMessage = transformMessage(originalMessage, flowName, executionId);
        
        return timestamp + "     Information        " + businessMessage + "\n";
    }
    
    /**
     * Filter out technical messages that should not appear in business logs
     */
    private boolean shouldFilterMessage(String message, String loggerName) {
        String lowercaseMessage = message.toLowerCase();
        
        // Filter SQL statements
        if (lowercaseMessage.contains("select ") || lowercaseMessage.contains("insert ") || 
            lowercaseMessage.contains("update ") || lowercaseMessage.contains("delete ") ||
            lowercaseMessage.contains("executing sql") || lowercaseMessage.contains("executing query")) {
            return true;
        }
        
        // Filter Spring/Hibernate technical messages
        if (loggerName != null && (
            loggerName.startsWith("org.springframework.jdbc") ||
            loggerName.startsWith("org.hibernate") ||
            loggerName.startsWith("com.zaxxer.hikari") ||
            loggerName.startsWith("org.apache.") ||
            loggerName.contains(".dao.") ||
            loggerName.contains(".repository."))) {
            return true;
        }
        
        // Filter technical debug messages
        if (lowercaseMessage.contains("real adapter") || 
            lowercaseMessage.contains("correlation id:") ||
            lowercaseMessage.contains("debug:") ||
            lowercaseMessage.contains("trace:") ||
            lowercaseMessage.contains("uuid") ||
            lowercaseMessage.contains("transaction") ||
            lowercaseMessage.contains("connection pool") ||
            lowercaseMessage.contains("datasource")) {
            return true;
        }
        
        // Filter Spring Boot startup messages
        if (lowercaseMessage.contains("started application") ||
            lowercaseMessage.contains("spring boot") ||
            lowercaseMessage.contains("autoconfiguration") ||
            lowercaseMessage.contains("bean creation") ||
            lowercaseMessage.contains("component scan")) {
            return true;
        }
        
        return false;
    }
    
    private String transformMessage(String originalMessage, String flowName, String executionId) {
        String message = originalMessage.toLowerCase();
        String shortId = executionId != null ? executionId.substring(0, 8) : "unknown";
        
        // Core flow execution start sequence
        if (message.contains("starting async execution for flow")) {
            return "Starting async execution for flow " + (flowName != null ? flowName : "Unknown Flow");
        }
        
        if (message.contains("application attempting to send message asynchronously")) {
            return "Application attempting to send message asynchronously using connection FlowExecution-" + shortId;
        }
        
        if (message.contains("trying to put the message into the processing queue")) {
            return "Trying to put the message into the processing queue";
        }
        
        if (message.contains("starting async execution") && !message.contains("for flow")) {
            return "Starting async execution";
        }
        
        if (message.contains("the message was successfully retrieved from the processing queue")) {
            return "The message was successfully retrieved from the processing queue";
        }
        
        if (message.contains("execution status changed to running")) {
            return "Execution status changed to RUNNING";
        }
        
        if (message.contains("executing flow steps for")) {
            return "Executing flow steps for " + (flowName != null ? flowName : "flow");
        }
        
        // Sender adapter processing
        if (message.contains("processing sender adapter")) {
            return "Processing sender adapter " + extractAdapterNameFromMessage(originalMessage);
        }
        
        if (message.contains("retrieving file from source directory")) {
            String directory = extractQuotedContent(originalMessage);
            return "Retrieving File from Source directory \"" + directory + "\"";
        }
        
        if (message.contains("file collected:")) {
            String filename = extractAfterColon(originalMessage);
            return "File collected: " + filename;
        }
        
        if (message.contains("executing mapping")) {
            String mappingName = extractQuotedContent(originalMessage);
            return "Executing Mapping '" + mappingName + "'";
        }
        
        if (message.contains("request message entering the adapter processing")) {
            return "Request message entering the adapter processing with user system";
        }
        
        if (message.contains("executing file adapter sender operation")) {
            return "Executing File adapter sender operation for: " + extractAdapterNameFromMessage(originalMessage);
        }
        
        if (message.contains("adapter execution completed successfully")) {
            return "Adapter execution completed successfully";
        }
        
        if (message.contains("file") && message.contains("has successfully been archived")) {
            String[] parts = originalMessage.split(" has successfully been archived in directory ");
            String filename = parts.length > 0 ? parts[0].replace("File ", "").trim() : "file";
            String directory = parts.length > 1 ? parts[1].trim() : "archive";
            return "File " + filename + " has successfully been archived in directory " + directory;
        }
        
        if (message.contains("file") && message.contains("has successfully been deleted")) {
            String filename = originalMessage.replace("File ", "").replace(" has successfully been deleted", "").trim();
            return "File " + filename + " has successfully been deleted";
        }
        
        if (message.contains("file processing completed")) {
            return "File Processing completed";
        }
        
        if (message.contains("message status set to delivered")) {
            return "Message status set to Delivered";
        }
        
        // START processing
        if (message.contains("start processing using") && message.contains("files")) {
            String[] parts = originalMessage.split(" ");
            String fileCount = "1";
            for (int i = 0; i < parts.length - 1; i++) {
                if (parts[i].toLowerCase().contains("using") && parts[i + 1].matches("\\d+")) {
                    fileCount = parts[i + 1];
                    break;
                }
            }
            return "START processing using " + fileCount + " files from trigger data for receiver processing";
        }
        
        if (message.contains("complete: step completed successfully") && message.contains("completed")) {
            return "COMPLETE: Step completed successfully start-process completed";
        }
        
        // Parallel processing
        if (message.contains("parallel: splitting execution into parallel paths")) {
            return "PARALLEL: Splitting execution into parallel paths";
        }
        
        if (message.contains("parallel split node ready to distribute")) {
            String fileCount = extractNumberFromMessage(originalMessage, "distribute");
            String pathCount = extractNumberFromMessage(originalMessage, "across");
            return "Parallel split node ready to distribute " + fileCount + " files across " + pathCount + " execution paths";
        }
        
        if (message.contains("complete: step completed successfully") && message.contains("parallel")) {
            return "COMPLETE: Step completed successfully for Parallel split";
        }
        
        // Branch messaging
        if (message.contains("branch") && message.contains("message sent to")) {
            String branchNum = extractBranchNumber(originalMessage);
            String target = extractAfter(originalMessage, "sent to ");
            return "Branch " + branchNum + " message sent to " + target;
        }
        
        // Receiver adapter processing
        if (message.contains("file") && message.contains("has been received by the receiver adapter")) {
            String filename = extractBefore(originalMessage, " has been received");
            String adapterName = extractAfter(originalMessage, "receiver adapter ");
            return "File " + filename + " has been received by the receiver adapter " + adapterName;
        }
        
        if (message.contains("connecting to target directory")) {
            String directory = extractAfterColon(originalMessage);
            if (directory.isEmpty()) {
                directory = extractAfter(originalMessage, "directory ");
            }
            return "Connecting to target directory " + directory;
        }
        
        if (message.contains("message processing completed")) {
            return "Message Processing completed";
        }
        
        if (message.contains("file") && message.contains("has successfully been placed in the target directory")) {
            String[] parts = originalMessage.split(" has successfully been placed in the target directory ");
            String filename = parts.length > 0 ? parts[0].replace("File ", "").trim() : "file";
            String directory = parts.length > 1 ? parts[1].trim() : "target";
            return "Message delivered to " + directory + " with filename " + filename;
        }
        
        if (message.contains("branch") && message.contains("message status set to")) {
            String branchNum = extractBranchNumber(originalMessage);
            return "Branch " + branchNum + " Message status set to Delivered";
        }
        
        // Utility operations
        if (message.contains("branch") && message.contains("utility")) {
            String branchNum = extractBranchNumber(originalMessage);
            String utilityName = extractAfter(originalMessage, "utility ");
            return "Branch " + branchNum + " message sent to utility " + utilityName;
        }
        
        if (message.contains("unzipped")) {
            String branchNum = extractBranchNumber(originalMessage);
            String fileCount = extractNumberFromMessage(originalMessage, "files");
            return "Branch " + branchNum + " file Unzipped - " + fileCount + " files extracted";
        }
        
        // Final flow completion
        if (message.contains("flow execution completed successfully")) {
            return "Flow execution completed successfully";
        }
        
        if (message.contains("message successfully put into the queue")) {
            return "Message successfully put into the queue";
        }
        
        if (message.contains("the application sent the message asynchronously")) {
            return "The application sent the message asynchronously using connection FlowExecution-" + shortId + ". Returning to application";
        }
        
        // Default: return cleaned up version of original message
        return cleanupMessage(originalMessage);
    }
    
    // Helper methods for extracting information from messages
    
    private String extractAdapterNameFromMessage(String message) {
        // Extract adapter name after common phrases
        if (message.contains("POP Zip Files")) return "POP Zip Files";
        if (message.contains("FNB")) return "FNB Adapter";
        
        // Generic extraction
        String[] words = message.split(" ");
        for (int i = 0; i < words.length - 1; i++) {
            if (words[i].toLowerCase().contains("adapter")) {
                return words[i + 1].trim();
            }
        }
        return "Unknown Adapter";
    }
    
    private String extractQuotedContent(String message) {
        int startQuote = message.indexOf('"');
        int endQuote = message.lastIndexOf('"');
        if (startQuote >= 0 && endQuote > startQuote) {
            return message.substring(startQuote + 1, endQuote);
        }
        
        // Try single quotes
        startQuote = message.indexOf('\'');
        endQuote = message.lastIndexOf('\'');
        if (startQuote >= 0 && endQuote > startQuote) {
            return message.substring(startQuote + 1, endQuote);
        }
        
        return "";
    }
    
    private String extractAfterColon(String message) {
        int colonIndex = message.indexOf(':');
        if (colonIndex >= 0 && colonIndex < message.length() - 1) {
            return message.substring(colonIndex + 1).trim();
        }
        return "";
    }
    
    private String extractAfter(String message, String marker) {
        int index = message.toLowerCase().indexOf(marker.toLowerCase());
        if (index >= 0) {
            String result = message.substring(index + marker.length()).trim();
            // Remove any trailing punctuation or IDs
            result = result.replaceAll("\\s*\\([^)]*\\)$", ""); // Remove (ID: xxx)
            return result;
        }
        return "";
    }
    
    private String extractBefore(String message, String marker) {
        int index = message.toLowerCase().indexOf(marker.toLowerCase());
        if (index >= 0) {
            return message.substring(0, index).trim();
        }
        return message;
    }
    
    private String extractNumberFromMessage(String message, String afterWord) {
        String[] words = message.split(" ");
        for (int i = 0; i < words.length - 1; i++) {
            if (words[i].toLowerCase().contains(afterWord.toLowerCase()) && i + 1 < words.length) {
                String next = words[i + 1].replaceAll("[^0-9]", "");
                if (!next.isEmpty()) {
                    return next;
                }
            }
        }
        return "1";
    }
    
    private String extractBranchNumber(String message) {
        if (message.toLowerCase().contains("branch 1")) return "1";
        if (message.toLowerCase().contains("branch 2")) return "2";
        if (message.toLowerCase().contains("branch 3")) return "3";
        
        // Extract using regex
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("branch (\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return "1";
    }
    
    private String cleanupMessage(String message) {
        // Remove technical prefixes and clean up the message
        String cleaned = message
            .replaceAll("\\[.*?\\]", "") // Remove bracketed content
            .replaceAll("\\(ID: [a-f0-9\\-]+\\)", "") // Remove ID references
            .replaceAll("Success: true", "") // Remove success flags
            .trim();
        
        // Capitalize first letter
        if (cleaned.length() > 0) {
            cleaned = Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
        }
        
        return cleaned;
    }
}