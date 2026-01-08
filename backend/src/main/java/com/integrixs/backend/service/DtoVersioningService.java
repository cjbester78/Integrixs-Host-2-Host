package com.integrixs.backend.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * DTO versioning service providing backward compatibility and migration strategies for API evolution.
 * Implements strategy pattern for different versioning approaches and maintains compatibility matrix.
 * Part of Phase 5.3 DTO enhancement following OOP principles.
 */
@Service
public class DtoVersioningService {
    
    private final Map<VersionKey, VersionMigrator<?, ?>> migrators = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> supportedVersions = new ConcurrentHashMap<>();
    
    /**
     * Register a version migrator for converting between DTO versions.
     */
    public <F, T> void registerMigrator(String dtoType, String fromVersion, String toVersion, 
                                      Function<F, T> migrator) {
        VersionKey key = new VersionKey(dtoType, fromVersion, toVersion);
        migrators.put(key, new VersionMigrator<>(migrator, fromVersion, toVersion));
        
        // Track supported versions
        supportedVersions.computeIfAbsent(dtoType, k -> new HashSet<>()).add(fromVersion);
        supportedVersions.computeIfAbsent(dtoType, k -> new HashSet<>()).add(toVersion);
    }
    
    /**
     * Migrate DTO from one version to another.
     */
    @SuppressWarnings("unchecked")
    public <F, T> VersionMigrationResult<T> migrateDto(F sourceDto, String dtoType, 
                                                     String fromVersion, String toVersion) {
        if (fromVersion.equals(toVersion)) {
            return VersionMigrationResult.success((T) sourceDto, fromVersion, toVersion);
        }
        
        VersionKey key = new VersionKey(dtoType, fromVersion, toVersion);
        VersionMigrator<F, T> migrator = (VersionMigrator<F, T>) migrators.get(key);
        
        if (migrator == null) {
            // Try to find a migration path
            return findMigrationPath(sourceDto, dtoType, fromVersion, toVersion);
        }
        
        try {
            T migratedDto = migrator.migrate(sourceDto);
            return VersionMigrationResult.success(migratedDto, fromVersion, toVersion);
        } catch (Exception e) {
            return VersionMigrationResult.failure(fromVersion, toVersion, 
                "Migration failed: " + e.getMessage());
        }
    }
    
    /**
     * Find migration path through intermediate versions.
     */
    @SuppressWarnings("unchecked")
    private <F, T> VersionMigrationResult<T> findMigrationPath(F sourceDto, String dtoType, 
                                                             String fromVersion, String toVersion) {
        // Simple path finding - can be enhanced with graph algorithms
        Set<String> versions = supportedVersions.get(dtoType);
        if (versions == null) {
            return VersionMigrationResult.failure(fromVersion, toVersion, 
                "No versions registered for DTO type: " + dtoType);
        }
        
        // Try direct intermediate migration (v1 -> v2 -> v3)
        for (String intermediate : versions) {
            if (!intermediate.equals(fromVersion) && !intermediate.equals(toVersion)) {
                VersionKey key1 = new VersionKey(dtoType, fromVersion, intermediate);
                VersionKey key2 = new VersionKey(dtoType, intermediate, toVersion);
                
                if (migrators.containsKey(key1) && migrators.containsKey(key2)) {
                    try {
                        VersionMigrator<F, ?> migrator1 = (VersionMigrator<F, ?>) migrators.get(key1);
                        VersionMigrator<Object, T> migrator2 = (VersionMigrator<Object, T>) migrators.get(key2);
                        
                        Object intermediateDto = migrator1.migrate(sourceDto);
                        T finalDto = migrator2.migrate(intermediateDto);
                        
                        return VersionMigrationResult.success(finalDto, fromVersion, toVersion, 
                            Arrays.asList(fromVersion, intermediate, toVersion));
                    } catch (Exception e) {
                        // Continue searching for other paths
                    }
                }
            }
        }
        
        return VersionMigrationResult.failure(fromVersion, toVersion, 
            "No migration path found from " + fromVersion + " to " + toVersion);
    }
    
    /**
     * Get version compatibility matrix for a DTO type.
     */
    public VersionCompatibilityMatrix getCompatibilityMatrix(String dtoType) {
        Set<String> versions = supportedVersions.get(dtoType);
        if (versions == null) {
            return VersionCompatibilityMatrix.empty(dtoType);
        }
        
        Map<String, Set<String>> compatibilityMap = new HashMap<>();
        
        for (String fromVersion : versions) {
            Set<String> compatibleVersions = new HashSet<>();
            
            for (String toVersion : versions) {
                if (fromVersion.equals(toVersion) || 
                    migrators.containsKey(new VersionKey(dtoType, fromVersion, toVersion))) {
                    compatibleVersions.add(toVersion);
                }
            }
            
            compatibilityMap.put(fromVersion, compatibleVersions);
        }
        
        return new VersionCompatibilityMatrix(dtoType, versions, compatibilityMap);
    }
    
    /**
     * Check if migration is supported between versions.
     */
    public boolean isMigrationSupported(String dtoType, String fromVersion, String toVersion) {
        if (fromVersion.equals(toVersion)) {
            return true;
        }
        
        VersionKey key = new VersionKey(dtoType, fromVersion, toVersion);
        return migrators.containsKey(key);
    }
    
    /**
     * Get all supported versions for a DTO type.
     */
    public Set<String> getSupportedVersions(String dtoType) {
        return Collections.unmodifiableSet(
            supportedVersions.getOrDefault(dtoType, Collections.emptySet()));
    }
    
    /**
     * Get latest version for a DTO type (assumes semantic versioning).
     */
    public String getLatestVersion(String dtoType) {
        Set<String> versions = supportedVersions.get(dtoType);
        if (versions == null || versions.isEmpty()) {
            return null;
        }
        
        return versions.stream()
                .max(this::compareVersions)
                .orElse(null);
    }
    
    /**
     * Compare version strings (simple implementation - can be enhanced for semantic versioning).
     */
    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        
        int maxLength = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < maxLength; i++) {
            int num1 = i < parts1.length ? parseInt(parts1[i], 0) : 0;
            int num2 = i < parts2.length ? parseInt(parts2[i], 0) : 0;
            
            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }
        return 0;
    }
    
    private int parseInt(String str, int defaultValue) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    // Helper classes and result objects
    
    /**
     * Immutable version migration result.
     */
    public static final class VersionMigrationResult<T> {
        private final boolean success;
        private final T migratedDto;
        private final String fromVersion;
        private final String toVersion;
        private final String errorMessage;
        private final List<String> migrationPath;
        private final LocalDateTime migratedAt;
        
        private VersionMigrationResult(boolean success, T migratedDto, String fromVersion, 
                                     String toVersion, String errorMessage, List<String> migrationPath) {
            this.success = success;
            this.migratedDto = migratedDto;
            this.fromVersion = fromVersion;
            this.toVersion = toVersion;
            this.errorMessage = errorMessage;
            this.migrationPath = Collections.unmodifiableList(
                migrationPath != null ? migrationPath : Collections.emptyList());
            this.migratedAt = LocalDateTime.now();
        }
        
        public boolean isSuccess() { return success; }
        public T getMigratedDto() { return migratedDto; }
        public String getFromVersion() { return fromVersion; }
        public String getToVersion() { return toVersion; }
        public String getErrorMessage() { return errorMessage; }
        public List<String> getMigrationPath() { return migrationPath; }
        public LocalDateTime getMigratedAt() { return migratedAt; }
        
        public boolean hasMultipleSteps() { return migrationPath.size() > 2; }
        public boolean isDirectMigration() { return migrationPath.size() <= 2; }
        
        public static <T> VersionMigrationResult<T> success(T migratedDto, String fromVersion, String toVersion) {
            return new VersionMigrationResult<>(true, migratedDto, fromVersion, toVersion, null, 
                Arrays.asList(fromVersion, toVersion));
        }
        
        public static <T> VersionMigrationResult<T> success(T migratedDto, String fromVersion, 
                                                          String toVersion, List<String> migrationPath) {
            return new VersionMigrationResult<>(true, migratedDto, fromVersion, toVersion, null, migrationPath);
        }
        
        public static <T> VersionMigrationResult<T> failure(String fromVersion, String toVersion, String errorMessage) {
            return new VersionMigrationResult<>(false, null, fromVersion, toVersion, errorMessage, null);
        }
        
        @Override
        public String toString() {
            return String.format("VersionMigrationResult{success=%b, fromVersion='%s', toVersion='%s', " +
                               "hasMultipleSteps=%b, errorMessage='%s'}", 
                               success, fromVersion, toVersion, hasMultipleSteps(), errorMessage);
        }
    }
    
    /**
     * Version compatibility matrix for a DTO type.
     */
    public static final class VersionCompatibilityMatrix {
        private final String dtoType;
        private final Set<String> supportedVersions;
        private final Map<String, Set<String>> compatibilityMap;
        
        public VersionCompatibilityMatrix(String dtoType, Set<String> supportedVersions, 
                                        Map<String, Set<String>> compatibilityMap) {
            this.dtoType = dtoType;
            this.supportedVersions = Collections.unmodifiableSet(new HashSet<>(supportedVersions));
            this.compatibilityMap = Collections.unmodifiableMap(new HashMap<>(compatibilityMap));
        }
        
        public String getDtoType() { return dtoType; }
        public Set<String> getSupportedVersions() { return supportedVersions; }
        public Map<String, Set<String>> getCompatibilityMap() { return compatibilityMap; }
        
        public Set<String> getCompatibleVersions(String version) {
            return compatibilityMap.getOrDefault(version, Collections.emptySet());
        }
        
        public boolean isCompatible(String fromVersion, String toVersion) {
            Set<String> compatible = compatibilityMap.get(fromVersion);
            return compatible != null && compatible.contains(toVersion);
        }
        
        public static VersionCompatibilityMatrix empty(String dtoType) {
            return new VersionCompatibilityMatrix(dtoType, Collections.emptySet(), Collections.emptyMap());
        }
        
        @Override
        public String toString() {
            return String.format("VersionCompatibilityMatrix{dtoType='%s', supportedVersions=%d, compatibilityEntries=%d}", 
                               dtoType, supportedVersions.size(), compatibilityMap.size());
        }
    }
    
    /**
     * Version migrator wrapper.
     */
    private static class VersionMigrator<F, T> {
        private final Function<F, T> migrator;
        private final String fromVersion;
        private final String toVersion;
        
        public VersionMigrator(Function<F, T> migrator, String fromVersion, String toVersion) {
            this.migrator = Objects.requireNonNull(migrator, "Migrator function cannot be null");
            this.fromVersion = Objects.requireNonNull(fromVersion, "From version cannot be null");
            this.toVersion = Objects.requireNonNull(toVersion, "To version cannot be null");
        }
        
        public T migrate(F source) {
            return migrator.apply(source);
        }
        
        public String getFromVersion() { return fromVersion; }
        public String getToVersion() { return toVersion; }
    }
    
    /**
     * Version key for migrator lookup.
     */
    private static final class VersionKey {
        private final String dtoType;
        private final String fromVersion;
        private final String toVersion;
        
        public VersionKey(String dtoType, String fromVersion, String toVersion) {
            this.dtoType = dtoType;
            this.fromVersion = fromVersion;
            this.toVersion = toVersion;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VersionKey that = (VersionKey) o;
            return Objects.equals(dtoType, that.dtoType) &&
                   Objects.equals(fromVersion, that.fromVersion) &&
                   Objects.equals(toVersion, that.toVersion);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(dtoType, fromVersion, toVersion);
        }
        
        @Override
        public String toString() {
            return String.format("%s:%s->%s", dtoType, fromVersion, toVersion);
        }
    }
    
    /**
     * DTO version strategy interface.
     */
    @FunctionalInterface
    public interface VersionStrategy {
        String determineVersion(String apiVersion, String clientVersion);
    }
    
    /**
     * Common versioning strategies.
     */
    public static class VersioningStrategies {
        
        public static final VersionStrategy PREFER_CLIENT = (api, client) -> 
            client != null ? client : api;
        
        public static final VersionStrategy PREFER_API = (api, client) -> 
            api != null ? api : client;
        
        public static final VersionStrategy LATEST_COMPATIBLE = (api, client) -> {
            // Implementation would require version compatibility checking
            // For now, prefer client version if available
            return client != null ? client : api;
        };
        
        public static VersionStrategy fallback(String fallbackVersion) {
            return (api, client) -> {
                if (client != null) return client;
                if (api != null) return api;
                return fallbackVersion;
            };
        }
    }
}