package com.integrixs.core.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.integrixs.core.service.AuditService;
import com.integrixs.shared.util.AuditUtils;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Abstract base repository implementation providing common CRUD operations and utilities.
 * Part of Phase 5 repository layer standardization following OOP principles.
 * 
 * @param <T> The entity type
 */
public abstract class AbstractRepository<T> implements BaseRepository<T> {
    
    protected final JdbcTemplate jdbcTemplate;
    protected final ObjectMapper objectMapper;
    protected final AuditService auditService;
    
    public AbstractRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }
    
    /**
     * Get the table name for this repository.
     * Must be implemented by concrete repositories.
     */
    protected abstract String getTableName();
    
    /**
     * Get the row mapper for this entity type.
     * Must be implemented by concrete repositories.
     */
    protected abstract RowMapper<T> getRowMapper();
    
    /**
     * Get entity class for reflection operations.
     * Must be implemented by concrete repositories.
     */
    protected abstract Class<T> getEntityClass();
    
    /**
     * Get the entity name for audit logging.
     * Default implementation uses class simple name.
     */
    protected String getEntityName() {
        return getEntityClass().getSimpleName();
    }
    
    // Standard CRUD Operations
    
    @Override
    public Optional<T> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        
        try {
            String sql = "SELECT * FROM " + getTableName() + " WHERE id = ?";
            T entity = jdbcTemplate.queryForObject(sql, getRowMapper(), id);
            return Optional.ofNullable(entity);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    @Override
    public List<T> findAll() {
        String sql = "SELECT * FROM " + getTableName() + " ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, getRowMapper());
    }
    
    @Override
    public boolean existsById(UUID id) {
        if (id == null) {
            return false;
        }
        
        String sql = "SELECT COUNT(1) FROM " + getTableName() + " WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }
    
    @Override
    public long count() {
        String sql = "SELECT COUNT(1) FROM " + getTableName();
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0L;
    }
    
    @Override
    public boolean deleteById(UUID id) {
        if (id == null) {
            return false;
        }
        
        String sql = "DELETE FROM " + getTableName() + " WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, id);
        
        if (rowsAffected > 0) {
            // Log audit trail for deletion
            auditService.logSuccess("DELETE", getTableName(), id, getEntityName());
            return true;
        }
        return false;
    }
    
    @Override
    public T saveOrUpdate(T entity, UUID userId) {
        UUID entityId = getEntityId(entity);
        if (entityId == null || !existsById(entityId)) {
            return save(entity, userId);
        } else {
            return update(entity, userId);
        }
    }
    
    // Utility Methods
    
    /**
     * Set audit fields for INSERT operations.
     * Uses reflection to set createdAt and createdBy fields.
     */
    protected void setCreationAuditFields(T entity, UUID createdBy) {
        try {
            setField(entity, "createdAt", LocalDateTime.now());
            setField(entity, "createdBy", createdBy);
            // For INSERT operations, updatedAt and updatedBy should remain NULL
        } catch (Exception e) {
            // Entity might not have audit fields - that's okay
        }
    }
    
    /**
     * Set audit fields for UPDATE operations.
     * Uses reflection to set updatedAt and updatedBy fields.
     */
    protected void setUpdateAuditFields(T entity, UUID updatedBy) {
        try {
            setField(entity, "updatedAt", LocalDateTime.now());
            setField(entity, "updatedBy", updatedBy);
            // createdAt and createdBy should remain unchanged
        } catch (Exception e) {
            // Entity might not have audit fields - that's okay
        }
    }
    
    /**
     * Get entity ID using reflection.
     */
    protected UUID getEntityId(T entity) {
        try {
            Method getIdMethod = entity.getClass().getMethod("getId");
            return (UUID) getIdMethod.invoke(entity);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Set entity ID using reflection.
     */
    protected void setEntityId(T entity, UUID id) {
        try {
            setField(entity, "id", id);
        } catch (Exception e) {
            // Could not set ID - that's okay, might be handled elsewhere
        }
    }
    
    /**
     * Set field value using reflection.
     */
    private void setField(T entity, String fieldName, Object value) throws Exception {
        Field field = findField(entity.getClass(), fieldName);
        if (field != null) {
            field.setAccessible(true);
            field.set(entity, value);
        }
    }
    
    /**
     * Find field in class hierarchy.
     */
    private Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
    
    // JSON Utility Methods
    
    /**
     * Convert Map to JSON string.
     */
    protected String mapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert map to JSON", e);
        }
    }
    
    /**
     * Convert JSON string to Map.
     */
    protected Map<String, Object> jsonToMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert JSON to map: " + json, e);
        }
    }
    
    // ResultSet Utility Methods
    
    /**
     * Safely get UUID from ResultSet.
     */
    protected UUID getUuid(ResultSet rs, String columnName) throws SQLException {
        Object value = rs.getObject(columnName);
        if (value == null) {
            return null;
        }
        return UUID.fromString(value.toString());
    }
    
    /**
     * Safely get LocalDateTime from ResultSet.
     */
    protected LocalDateTime getLocalDateTime(ResultSet rs, String columnName) throws SQLException {
        var timestamp = rs.getTimestamp(columnName);
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }
    
    /**
     * Safely get Map from JSON column in ResultSet.
     */
    protected Map<String, Object> getJsonMap(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return jsonToMap(json);
    }
}