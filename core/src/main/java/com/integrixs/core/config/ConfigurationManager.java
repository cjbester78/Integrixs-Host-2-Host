package com.integrixs.core.config;

import com.integrixs.shared.constants.H2HConstants;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Manages application configuration loading and path resolution
 */
@Component
public class ConfigurationManager {
    
    private final String basePath;
    private final String environment;
    
    public ConfigurationManager() {
        this.basePath = determineBasePath();
        this.environment = determineEnvironment();
    }
    
    public ConfigurationManager(String customBasePath) {
        this.basePath = customBasePath;
        this.environment = determineEnvironment();
    }
    
    public ConfigurationManager(String customBasePath, String environment) {
        this.basePath = customBasePath;
        this.environment = environment;
    }
    
    private String determineBasePath() {
        String currentDir = System.getProperty("user.dir");
        
        // If running from App directory, go up one level
        if (currentDir.endsWith("App")) {
            Path parent = Paths.get(currentDir).getParent();
            if (parent != null) {
                return parent.toString();
            }
        }
        
        // Check if current directory has config subdirectory
        Path configPath = Paths.get(currentDir, H2HConstants.DEFAULT_CONFIG_DIR);
        if (Files.exists(configPath)) {
            return currentDir;
        }
        
        // Try parent directory
        Path parent = Paths.get(currentDir).getParent();
        if (parent != null) {
            Path parentConfig = parent.resolve(H2HConstants.DEFAULT_CONFIG_DIR);
            if (Files.exists(parentConfig)) {
                return parent.toString();
            }
        }
        
        return currentDir;
    }
    
    private String determineEnvironment() {
        // Check system property first
        String env = System.getProperty(H2HConstants.PROP_APP_ENVIRONMENT);
        if (env != null && !env.isEmpty()) {
            return env.toUpperCase();
        }
        
        // Check environment variable
        env = System.getenv(H2HConstants.ENV_APP_ENVIRONMENT);
        if (env != null && !env.isEmpty()) {
            return env.toUpperCase();
        }
        
        // Check Spring profiles
        String activeProfiles = System.getProperty("spring.profiles.active", "");
        if (activeProfiles.contains("dev")) {
            return "DEV";
        }
        if (activeProfiles.contains("qa") || activeProfiles.contains("qas")) {
            return "QAS";
        }
        
        return H2HConstants.DEFAULT_ENVIRONMENT;
    }
    
    public Properties loadConfiguration(String configFileName) throws ConfigurationException {
        Properties config = new Properties();
        String configPath = getConfigPath(configFileName);
        
        if (!Files.exists(Paths.get(configPath))) {
            throw new ConfigurationException("Configuration file not found: " + configPath);
        }
        
        try (InputStream input = new FileInputStream(configPath)) {
            config.load(input);
            validateConfiguration(config, configFileName);
            return config;
        } catch (IOException e) {
            throw new ConfigurationException("Failed to load configuration: " + configFileName, e);
        }
    }
    
    private void validateConfiguration(Properties config, String configFileName) throws ConfigurationException {
        if (H2HConstants.APP_CONFIG_FILE.equals(configFileName)) {
            validateAppConfig(config, configFileName);
        } else {
            validateSftpConfig(config, configFileName);
        }
    }
    
    private void validateAppConfig(Properties config, String configFileName) throws ConfigurationException {
        String apps = config.getProperty("apps");
        if (apps == null || apps.trim().isEmpty()) {
            throw new ConfigurationException("Missing required property 'apps' in " + configFileName);
        }
    }
    
    private void validateSftpConfig(Properties config, String configFileName) throws ConfigurationException {
        String[] requiredProps = {"host", "port", "username"};
        
        for (String prop : requiredProps) {
            if (config.getProperty(prop) == null || config.getProperty(prop).trim().isEmpty()) {
                throw new ConfigurationException("Missing required property '" + prop + "' in " + configFileName);
            }
        }
        
        // Validate port number
        try {
            Integer.parseInt(config.getProperty("port"));
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Invalid port number in " + configFileName, e);
        }
        
        // Validate SSH key exists
        String pkAlias = config.getProperty("pk_alias");
        if (pkAlias != null && !pkAlias.isEmpty()) {
            String sshKeyPath = resolvePath(pkAlias);
            if (!Files.exists(Paths.get(sshKeyPath))) {
                throw new ConfigurationException("SSH key file not found: " + sshKeyPath);
            }
        }
    }
    
    public String getConfigPath(String configFileName) {
        return Paths.get(basePath, H2HConstants.DEFAULT_CONFIG_DIR, environment, configFileName).toString();
    }
    
    public String getLogsPath(String logFileName) {
        Path logsDir = Paths.get(basePath, H2HConstants.DEFAULT_LOGS_DIR, environment);
        
        try {
            Files.createDirectories(logsDir);
        } catch (IOException e) {
            return logFileName;
        }
        
        return logsDir.resolve(logFileName).toString();
    }
    
    public String getSshKeyPath(String keyFileName) {
        return Paths.get(basePath, H2HConstants.DEFAULT_SSH_DIR, keyFileName).toString();
    }
    
    public String resolvePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        
        // Absolute paths returned as-is
        if (Paths.get(path).isAbsolute()) {
            return path;
        }
        
        // Handle special directory paths
        if (path.contains("\\ssh\\") || path.contains("/ssh/")) {
            String fileName = Paths.get(path).getFileName().toString();
            return getSshKeyPath(fileName);
        }
        
        if (path.contains("\\logs\\") || path.contains("/logs/")) {
            String fileName = Paths.get(path).getFileName().toString();
            return getLogsPath(fileName);
        }
        
        // Relative to base path
        return Paths.get(basePath, path).toString();
    }
    
    public void createDirectoryIfNotExists(String dirPath) throws IOException {
        Path path = Paths.get(resolvePath(dirPath));
        Files.createDirectories(path);
    }
    
    public String getBasePath() {
        return basePath;
    }
    
    public String getEnvironment() {
        return environment;
    }
    
    public Properties loadEnvironmentConfig(String baseConfigName) throws ConfigurationException {
        String envConfigName = baseConfigName.replace(".properties", "." + environment.toLowerCase() + ".properties");
        
        try {
            return loadConfiguration(envConfigName);
        } catch (ConfigurationException e) {
            return loadConfiguration(baseConfigName);
        }
    }
}