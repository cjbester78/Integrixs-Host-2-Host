package com.integrixs.adapters.file;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Simple configuration wrapper for File adapter settings
 * Based on reference SAP PI File adapter configuration structure
 */
public class FileAdapterConfig {
    
    private final Map<String, Object> configuration;
    
    public FileAdapterConfig(Map<String, Object> configuration) {
        this.configuration = configuration;
    }
    
    // ==== SENDER (Source) Configuration ====
    
    /**
     * Source Directory - for sender adapters only
     */
    public String getSourceDirectory() {
        return getString("sourceDirectory");
    }
    
    /**
     * File Name Mask/Pattern for filtering files
     */
    public String getFilePattern() {
        return getString("filePattern", "*");
    }
    
    /**
     * Exclusion Mask for files to exclude
     */
    public String getExclusionMask() {
        return getString("exclusionMask");
    }
    
    // ==== RECEIVER (Target) Configuration ====
    
    /**
     * Target Directory - for receiver adapters only
     */
    public String getTargetDirectory() {
        return getString("targetDirectory");
    }
    
    /**
     * Create Target Directory if it doesn't exist
     */
    public boolean isCreateTargetDirectory() {
        return getBoolean("createTargetDirectory", false);
    }
    
    /**
     * File Name Scheme for receiver files
     */
    public String getFileNameScheme() {
        return getString("fileNameScheme", "%o_output.dat");
    }
    
    // ==== Common Processing Parameters ====
    
    /**
     * Quality of Service - Exactly Once, Best Effort, etc.
     */
    public String getQualityOfService() {
        return getString("qualityOfService", "Exactly Once");
    }
    
    /**
     * Retry Interval in seconds
     */
    public int getRetryInterval() {
        return getInt("retryInterval", 60);
    }
    
    /**
     * Processing Mode - Test, Archive, Delete, etc.
     */
    public String getProcessingMode() {
        return getString("processingMode", "Test");
    }
    
    /**
     * Empty File Handling options
     */
    public String getEmptyFileHandling() {
        return getString("emptyFileHandling", "Do Not Create Message");
    }
    
    /**
     * Archive Faulty Source Files
     */
    public boolean isArchiveFaultySourceFiles() {
        return getBoolean("archiveFaultySourceFiles", false);
    }
    
    /**
     * Directory for Archiving Files with Errors
     */
    public String getArchiveErrorDirectory() {
        return getString("archiveErrorDirectory");
    }
    
    /**
     * Add Time Stamp to archived files
     */
    public boolean isAddTimeStamp() {
        return getBoolean("addTimeStamp", false);
    }
    
    /**
     * Process Read-Only Files
     */
    public boolean isProcessReadOnlyFiles() {
        return getBoolean("processReadOnlyFiles", false);
    }
    
    /**
     * Processing Sequence - By Name, By Date
     */
    public String getProcessingSequence() {
        return getString("processingSequence", "By Name");
    }
    
    /**
     * File Type - Binary or Text
     */
    public String getFileType() {
        return getString("fileType", "Binary");
    }
    
    // ==== Advanced Parameters ====
    
    /**
     * Set Adapter-Specific Message Attributes
     */
    public boolean isSetAdapterSpecificMessageAttributes() {
        return getBoolean("setAdapterSpecificMessageAttributes", false);
    }
    
    /**
     * File Name attribute
     */
    public boolean isFileNameAttribute() {
        return getBoolean("fileNameAttribute", false);
    }
    
    /**
     * Directory attribute
     */
    public boolean isDirectoryAttribute() {
        return getBoolean("directoryAttribute", false);
    }
    
    /**
     * File Type attribute
     */
    public boolean isFileTypeAttribute() {
        return getBoolean("fileTypeAttribute", false);
    }
    
    /**
     * Source File Size attribute
     */
    public boolean isSourceFileSizeAttribute() {
        return getBoolean("sourceFileSizeAttribute", false);
    }
    
    /**
     * Source File Time Stamp attribute
     */
    public boolean isSourceFileTimeStampAttribute() {
        return getBoolean("sourceFileTimeStampAttribute", false);
    }
    
    /**
     * Adapter Status
     */
    public String getAdapterStatus() {
        return getString("adapterStatus", "Active");
    }
    
    /**
     * Advanced parameters flag
     */
    public boolean isAdvancedMode() {
        return getBoolean("advancedMode", false);
    }
    
    /**
     * Milliseconds to Wait Before Modification Check
     */
    public int getMsecsToWaitBeforeModificationCheck() {
        return getInt("msecsToWaitBeforeModificationCheck", 0);
    }
    
    /**
     * Maximum File Size in Bytes
     */
    public long getMaximumFileSize() {
        return getLong("maximumFileSize", 0L); // 0 = no limit
    }
    
    // ==== RECEIVER Processing Parameters ====
    
    /**
     * File Construction Mode - Add Time Stamp, Create, etc.
     */
    public String getFileConstructionMode() {
        return getString("fileConstructionMode", "Add Time Stamp");
    }
    
    /**
     * Write Mode - Directly, Create Temp File, etc.
     */
    public String getWriteMode() {
        return getString("writeMode", "Directly");
    }
    
    /**
     * Empty Message Handling for receiver
     */
    public String getEmptyMessageHandling() {
        return getString("emptyMessageHandling", "Write Empty File");
    }
    
    /**
     * Maximum Concurrency
     */
    public int getMaximumConcurrency() {
        return getInt("maximumConcurrency", 1);
    }
    
    // ==== Utility Methods ====
    
    /**
     * Get the appropriate directory based on adapter direction
     * For SENDER: returns source directory
     * For RECEIVER: returns target directory
     */
    public String getDirectory() {
        String sourceDir = getSourceDirectory();
        String targetDir = getTargetDirectory();
        return sourceDir != null ? sourceDir : targetDir;
    }
    
    // ==== Backward Compatibility Methods ====
    // These methods maintain compatibility with existing FileAdapter, ZipFileProcessor, and FileValidator classes
    
    public boolean isZipProcessingEnabled() {
        return getBoolean("zipProcessingEnabled", true);
    }
    
    public String getPostProcessing() {
        return getProcessingMode(); // Map to processing mode
    }
    
    public String getArchiveDirectory() {
        return getString("archiveDirectory"); // Read from archive directory configuration
    }
    
    public boolean isValidationEnabled() {
        return getBoolean("validationEnabled", true);
    }
    
    public long getMaxFileSize() {
        return getMaximumFileSize();
    }
    
    public long getFileAgeThresholdMs() {
        return getLong("fileAgeThresholdMs", 0L);
    }
    
    // ==== Scheduler Configuration ====
    
    // Scheduler configuration fields
    public String getScheduleType() {
        return getString("scheduleType", "Daily");
    }

    public String getScheduleMode() {
        return getString("scheduleMode", "OnTime");
    }

    public String getOnTimeValue() {
        return getString("onTimeValue", "20:27");
    }

    public String getEveryInterval() {
        return getString("everyInterval", "1 min");
    }

    public String getEveryStartTime() {
        return getString("everyStartTime", "00:00");
    }

    public String getEveryEndTime() {
        return getString("everyEndTime", "24:00");
    }

    public String getTimeZone() {
        return getString("timeZone", "UTC 0:00");
    }

    // Weekly days configuration
    public boolean isMondayEnabled() {
        return getBoolean("weeklyDays.monday", false);
    }

    public boolean isTuesdayEnabled() {
        return getBoolean("weeklyDays.tuesday", false);
    }

    public boolean isWednesdayEnabled() {
        return getBoolean("weeklyDays.wednesday", false);
    }

    public boolean isThursdayEnabled() {
        return getBoolean("weeklyDays.thursday", false);
    }

    public boolean isFridayEnabled() {
        return getBoolean("weeklyDays.friday", false);
    }

    public boolean isSaturdayEnabled() {
        return getBoolean("weeklyDays.saturday", false);
    }

    public boolean isSundayEnabled() {
        return getBoolean("weeklyDays.sunday", false);
    }

    // Monthly day configuration
    public String getMonthlyDay() {
        return getString("monthlyDay", "1");
    }
    
    // Helper methods for type-safe configuration access
    public String getString(String key) {
        Object value = configuration.get(key);
        return value != null ? value.toString() : null;
    }
    
    public String getString(String key, String defaultValue) {
        String value = getString(key);
        return value != null ? value : defaultValue;
    }
    
    public int getInt(String key, int defaultValue) {
        Object value = configuration.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    public long getLong(String key, long defaultValue) {
        Object value = configuration.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        } else if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = configuration.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
    
    public Map<String, Object> getRawConfiguration() {
        return configuration;
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getList(String key) {
        Object value = configuration.get(key);
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<String> stringList = new ArrayList<>();
            for (Object item : list) {
                stringList.add(item.toString());
            }
            return stringList;
        } else if (value instanceof String) {
            // Support comma-separated values
            String stringValue = (String) value;
            return java.util.Arrays.asList(stringValue.split(","));
        }
        return null;
    }
    
    @Override
    public String toString() {
        return "FileAdapterConfig{" +
                "sourceDirectory='" + getSourceDirectory() + '\'' +
                ", targetDirectory='" + getTargetDirectory() + '\'' +
                ", filePattern='" + getFilePattern() + '\'' +
                ", processingMode='" + getProcessingMode() + '\'' +
                ", qualityOfService='" + getQualityOfService() + '\'' +
                ", fileType='" + getFileType() + '\'' +
                ", archiveFaultySourceFiles=" + isArchiveFaultySourceFiles() +
                ", addTimeStamp=" + isAddTimeStamp() +
                '}';
    }
}