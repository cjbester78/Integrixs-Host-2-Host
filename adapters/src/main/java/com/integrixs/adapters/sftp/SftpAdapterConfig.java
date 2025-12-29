package com.integrixs.adapters.sftp;

import java.util.Map;

/**
 * Configuration wrapper for SFTP adapter settings
 */
public class SftpAdapterConfig {
    
    private final Map<String, Object> configuration;
    
    public SftpAdapterConfig(Map<String, Object> configuration) {
        this.configuration = configuration;
    }
    
    // Connection settings
    public String getHost() {
        return getString("host");
    }
    
    public int getPort() {
        return getInt("port", 22);
    }
    
    public String getUsername() {
        return getString("username");
    }
    
    public String getPassword() {
        return getString("password");
    }
    
    // SSH Key authentication
    public String getSshKeyName() {
        return getString("sshKeyName");
    }
    
    public String getPrivateKeyPath() {
        return getString("privateKeyPath");
    }
    
    public String getPrivateKeyPassphrase() {
        return getString("privateKeyPassphrase");
    }
    
    public String getPublicKeyPath() {
        return getString("publicKeyPath");
    }
    
    // Directory settings
    public String getLocalDirectory() {
        return getString("localDirectory");
    }
    
    public String getRemoteDirectory() {
        return getString("remoteDirectory");
    }
    
    public String getArchiveDirectory() {
        return getString("archiveDirectory");
    }
    
    public String getErrorDirectory() {
        return getString("errorDirectory");
    }
    
    // File processing settings
    public String getFilePattern() {
        return getString("filePattern", "*");
    }
    
    public String getPostProcessing() {
        return getString("postProcessing", "NONE");
    }
    
    public boolean isRecursiveScanning() {
        return getBoolean("recursiveScanning", false);
    }
    
    // Connection settings
    public int getSessionTimeout() {
        return getInt("sessionTimeout", 60000); // 60 seconds
    }
    
    public int getChannelTimeout() {
        return getInt("channelTimeout", 30000); // 30 seconds
    }
    
    public int getConnectionPoolSize() {
        return getInt("connectionPoolSize", 5);
    }
    
    public boolean isStrictHostKeyChecking() {
        return getBoolean("strictHostKeyChecking", false);
    }
    
    // Transfer settings
    public int getMaxRetryAttempts() {
        return getInt("maxRetryAttempts", 3);
    }
    
    public long getRetryDelayMs() {
        return getLong("retryDelayMs", 5000L);
    }
    
    public long getMaxFileSize() {
        return getLong("maxFileSize", 100 * 1024 * 1024L); // 100MB
    }
    
    public int getTransferBufferSize() {
        return getInt("transferBufferSize", 32768); // 32KB
    }
    
    // Validation settings
    public boolean isValidationEnabled() {
        return getBoolean("validationEnabled", true);
    }
    
    public boolean isChecksumValidation() {
        return getBoolean("checksumValidation", false);
    }
    
    // Backup and cleanup
    public boolean isBackupEnabled() {
        return getBoolean("backupEnabled", false);
    }
    
    public String getBackupDirectory() {
        return getString("backupDirectory");
    }
    
    public boolean isCleanupEnabled() {
        return getBoolean("cleanupEnabled", false);
    }
    
    public int getCleanupDays() {
        return getInt("cleanupDays", 30);
    }

    // New configuration fields based on frontend requirements

    // Authentication Type
    public String getAuthenticationType() {
        return getString("authenticationType", "USERNAME_PASSWORD");
    }

    public String getSshKeyId() {
        return getString("sshKeyId");
    }

    // Connection Mode
    public String getConnectionMode() {
        return getString("connectionMode", "Permanently");
    }

    // Buffer Size (different from transferBufferSize)
    public int getBufferSize() {
        return getInt("bufferSize", 32768);
    }

    // Transfer Mode
    public String getTransferMode() {
        return getString("transferMode", "BINARY");
    }

    // Checksum settings
    public String getChecksumAlgorithm() {
        return getString("checksumAlgorithm", "NONE");
    }

    public boolean isChecksumVerification() {
        return getBoolean("checksumVerification", false);
    }

    public boolean isAtomicWrite() {
        return getBoolean("atomicWrite", false);
    }

    public boolean isOverwriteRemote() {
        return getBoolean("overwriteRemote", false);
    }

    // SENDER-specific settings
    public String getFilename() {
        return getString("filename");
    }

    public String getExcludePattern() {
        return getString("excludePattern");
    }

    public String getEmptyFileHandling() {
        return getString("emptyFileHandling", "Do Not Create Message");
    }

    public boolean isUseTemporaryFileName() {
        return getBoolean("useTemporaryFileName", false);
    }

    public String getTemporaryFileSuffix() {
        return getString("temporaryFileSuffix", ".tmp");
    }

    // RECEIVER-specific settings
    public String getRemoteFilePermissions() {
        return getString("remoteFilePermissions", "644");
    }

    public boolean isCreateRemoteDirectory() {
        return getBoolean("createRemoteDirectory", false);
    }

    public String getOutputFilenameMode() {
        return getString("outputFilenameMode", "UseOriginal");
    }

    public String getCustomFilenamePattern() {
        return getString("customFilenamePattern");
    }

    // Post-processing settings
    public String getPostProcessAction() {
        return getString("postProcessAction", "ARCHIVE");
    }

    public boolean isArchiveWithTimestamp() {
        return getBoolean("archiveWithTimestamp", false);
    }

    public String getCompressionType() {
        return getString("compressionType", "NONE");
    }

    public String getProcessedDirectory() {
        return getString("processedDirectory");
    }

    public String getProcessedFileSuffix() {
        return getString("processedFileSuffix", ".processed");
    }

    public String getReprocessingDirectory() {
        return getString("reprocessingDirectory");
    }

    public long getReprocessingDelay() {
        return getLong("reprocessingDelay", 3600000L); // 1 hour
    }

    public String getDeleteBackupDirectory() {
        return getString("deleteBackupDirectory");
    }

    public boolean isConfirmDelete() {
        return getBoolean("confirmDelete", false);
    }

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
        return getString("timeZone", "UTC+00:00");
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
    
    @Override
    public String toString() {
        return "SftpAdapterConfig{" +
                "host='" + getHost() + '\'' +
                ", port=" + getPort() +
                ", username='" + getUsername() + '\'' +
                ", authenticationType='" + getAuthenticationType() + '\'' +
                ", remoteDirectory='" + getRemoteDirectory() + '\'' +
                ", localDirectory='" + getLocalDirectory() + '\'' +
                ", filePattern='" + getFilePattern() + '\'' +
                ", transferMode='" + getTransferMode() + '\'' +
                ", postProcessAction='" + getPostProcessAction() + '\'' +
                '}';
    }
}