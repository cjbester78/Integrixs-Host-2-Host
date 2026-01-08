package com.integrixs.core.repository;

import com.integrixs.shared.model.DataRetentionConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for DataRetentionConfig entity using native SQL with Spring JDBC
 * Provides CRUD operations for data retention configuration management
 */
@Repository
public class DataRetentionConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public DataRetentionConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * RowMapper for DataRetentionConfig entity
     */
    private static class DataRetentionConfigRowMapper implements RowMapper<DataRetentionConfig> {
        @Override
        public DataRetentionConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
            DataRetentionConfig config = new DataRetentionConfig();
            config.setId(UUID.fromString(rs.getString("id")));
            config.setDataType(DataRetentionConfig.DataType.valueOf(rs.getString("data_type")));
            config.setName(rs.getString("name"));
            config.setDescription(rs.getString("description"));
            config.setRetentionDays(rs.getInt("retention_days"));
            
            // Handle nullable fields
            Object archiveDays = rs.getObject("archive_days");
            if (archiveDays != null) {
                config.setArchiveDays((Integer) archiveDays);
            }
            
            config.setScheduleCron(rs.getString("schedule_cron"));
            config.setEnabled(rs.getBoolean("enabled"));
            
            // Handle timestamps
            if (rs.getTimestamp("created_at") != null) {
                config.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            }
            if (rs.getTimestamp("updated_at") != null) {
                config.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
            }
            if (rs.getTimestamp("last_execution") != null) {
                config.setLastExecution(rs.getTimestamp("last_execution").toLocalDateTime());
            }
            
            config.setCreatedBy(rs.getString("created_by"));
            config.setUpdatedBy(rs.getString("updated_by"));
            config.setLastExecutionStatus(rs.getString("last_execution_status"));
            
            Object lastItemsProcessed = rs.getObject("last_items_processed");
            if (lastItemsProcessed != null) {
                config.setLastItemsProcessed((Integer) lastItemsProcessed);
            }
            
            return config;
        }
    }

    /**
     * Find all retention configurations ordered by data type and name
     */
    public List<DataRetentionConfig> findAll() {
        String sql = """
            SELECT id, data_type, name, description, retention_days, archive_days, 
                   schedule_cron, enabled, created_at, updated_at, created_by, updated_by,
                   last_execution, last_execution_status, last_items_processed
            FROM data_retention_configs 
            ORDER BY data_type, name
        """;
        return jdbcTemplate.query(sql, new DataRetentionConfigRowMapper());
    }

    /**
     * Find all enabled retention configurations
     */
    public List<DataRetentionConfig> findByEnabledTrue() {
        String sql = """
            SELECT id, data_type, name, description, retention_days, archive_days, 
                   schedule_cron, enabled, created_at, updated_at, created_by, updated_by,
                   last_execution, last_execution_status, last_items_processed
            FROM data_retention_configs 
            WHERE enabled = true
            ORDER BY data_type, name
        """;
        return jdbcTemplate.query(sql, new DataRetentionConfigRowMapper());
    }

    /**
     * Find configuration by ID
     */
    public Optional<DataRetentionConfig> findById(UUID id) {
        String sql = """
            SELECT id, data_type, name, description, retention_days, archive_days, 
                   schedule_cron, enabled, created_at, updated_at, created_by, updated_by,
                   last_execution, last_execution_status, last_items_processed
            FROM data_retention_configs 
            WHERE id = ?
        """;
        
        List<DataRetentionConfig> results = jdbcTemplate.query(sql, new DataRetentionConfigRowMapper(), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Find configuration by name
     */
    public Optional<DataRetentionConfig> findByName(String name) {
        String sql = """
            SELECT id, data_type, name, description, retention_days, archive_days, 
                   schedule_cron, enabled, created_at, updated_at, created_by, updated_by,
                   last_execution, last_execution_status, last_items_processed
            FROM data_retention_configs 
            WHERE name = ?
        """;
        
        List<DataRetentionConfig> results = jdbcTemplate.query(sql, new DataRetentionConfigRowMapper(), name);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Find configurations by data type and enabled status
     */
    public List<DataRetentionConfig> findByDataTypeAndEnabledTrue(DataRetentionConfig.DataType dataType) {
        String sql = """
            SELECT id, data_type, name, description, retention_days, archive_days, 
                   schedule_cron, enabled, created_at, updated_at, created_by, updated_by,
                   last_execution, last_execution_status, last_items_processed
            FROM data_retention_configs 
            WHERE data_type = ? AND enabled = true
            ORDER BY name
        """;
        return jdbcTemplate.query(sql, new DataRetentionConfigRowMapper(), dataType.name());
    }

    /**
     * Find the schedule configuration (should be unique)
     */
    public Optional<DataRetentionConfig> findScheduleConfig() {
        String sql = """
            SELECT id, data_type, name, description, retention_days, archive_days, 
                   schedule_cron, enabled, created_at, updated_at, created_by, updated_by,
                   last_execution, last_execution_status, last_items_processed
            FROM data_retention_configs 
            WHERE data_type = 'SCHEDULE' AND enabled = true
            LIMIT 1
        """;
        
        List<DataRetentionConfig> results = jdbcTemplate.query(sql, new DataRetentionConfigRowMapper());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Check if a configuration with the given name exists
     */
    public boolean existsByName(String name) {
        String sql = "SELECT COUNT(*) FROM data_retention_configs WHERE name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, name);
        return count != null && count > 0;
    }

    /**
     * Save a new retention configuration
     */
    public DataRetentionConfig save(DataRetentionConfig config) {
        if (config.getId() == null) {
            return insert(config);
        } else {
            return update(config);
        }
    }

    /**
     * Insert new configuration
     */
    private DataRetentionConfig insert(DataRetentionConfig config) {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        
        String sql = """
            INSERT INTO data_retention_configs (
                id, data_type, name, description, retention_days, archive_days, 
                schedule_cron, enabled, created_at, updated_at, created_by, updated_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        jdbcTemplate.update(sql,
            id,
            config.getDataType().name(),
            config.getName(),
            config.getDescription(),
            config.getRetentionDays(),
            config.getArchiveDays(),
            config.getScheduleCron(),
            config.getEnabled(),
            now,
            now,
            config.getCreatedBy(),
            config.getUpdatedBy()
        );
        
        config.setId(id);
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        return config;
    }

    /**
     * Update existing configuration
     */
    private DataRetentionConfig update(DataRetentionConfig config) {
        LocalDateTime now = LocalDateTime.now();
        
        String sql = """
            UPDATE data_retention_configs SET
                data_type = ?, name = ?, description = ?, retention_days = ?, 
                archive_days = ?, schedule_cron = ?, enabled = ?, updated_at = ?, updated_by = ?
            WHERE id = ?
        """;
        
        jdbcTemplate.update(sql,
            config.getDataType().name(),
            config.getName(),
            config.getDescription(),
            config.getRetentionDays(),
            config.getArchiveDays(),
            config.getScheduleCron(),
            config.getEnabled(),
            now,
            config.getUpdatedBy(),
            config.getId()
        );
        
        config.setUpdatedAt(now);
        return config;
    }

    /**
     * Delete configuration by ID
     */
    public void deleteById(UUID id) {
        String sql = "DELETE FROM data_retention_configs WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    /**
     * Delete configuration entity
     */
    public void delete(DataRetentionConfig config) {
        if (config.getId() != null) {
            deleteById(config.getId());
        }
    }

    /**
     * Update last execution details
     */
    public void updateLastExecution(UUID id, String status, Integer itemsProcessed) {
        String sql = """
            UPDATE data_retention_configs 
            SET last_execution = CURRENT_TIMESTAMP, 
                last_execution_status = ?, 
                last_items_processed = ? 
            WHERE id = ?
        """;
        jdbcTemplate.update(sql, status, itemsProcessed, id);
    }

    /**
     * Get schedule cron expression from SCHEDULE configuration
     */
    public String getScheduleCron() {
        return findScheduleConfig()
            .map(DataRetentionConfig::getScheduleCron)
            .orElse("0 0 2 * * ?"); // Default: Daily at 2:00 AM
    }
}