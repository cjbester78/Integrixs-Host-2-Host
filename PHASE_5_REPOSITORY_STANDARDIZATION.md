# Phase 5.2: Repository Layer Standardization Guide

## Overview

This document shows how to modernize existing repositories to use the new standardized base classes created in Phase 5.2 of the Java OOP refactoring initiative.

## Base Classes Created

### 1. BaseRepository<T> Interface
- **Location**: `core/src/main/java/com/integrixs/core/repository/BaseRepository.java`
- **Purpose**: Defines common CRUD operations for all entities
- **Key Methods**:
  - `Optional<T> findById(UUID id)`
  - `List<T> findAll()`
  - `T save(T entity, UUID createdBy)` - INSERT with creation audit
  - `T update(T entity, UUID updatedBy)` - UPDATE with update audit
  - `T saveOrUpdate(T entity, UUID userId)` - Smart save/update
  - `boolean deleteById(UUID id)`
  - `boolean existsById(UUID id)`
  - `long count()`
  - `List<T> findByActive(boolean active)` - Optional override

### 2. AbstractRepository<T> Abstract Class
- **Location**: `core/src/main/java/com/integrixs/core/repository/AbstractRepository.java`
- **Purpose**: Provides common repository functionality and utilities
- **Key Features**:
  - Proper audit trail handling (INSERT vs UPDATE semantics)
  - JSON conversion utilities for JSONB columns
  - ResultSet utility methods (UUID, LocalDateTime, JSON mapping)
  - Reflection-based audit field management
  - Consistent error handling and logging

## Refactoring Pattern

### Step 1: Extend AbstractRepository
```java
@Repository
public class YourRepositoryImpl extends AbstractRepository<YourEntity> {
    
    public YourRepositoryImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, AuditService auditService) {
        super(jdbcTemplate, objectMapper, auditService);
    }
}
```

### Step 2: Implement Required Abstract Methods
```java
@Override
protected String getTableName() {
    return "your_table_name";
}

@Override
protected Class<YourEntity> getEntityClass() {
    return YourEntity.class;
}

@Override
protected RowMapper<YourEntity> getRowMapper() {
    return new YourEntityRowMapper();
}
```

### Step 3: Implement CRUD Operations
```java
@Override
public YourEntity save(YourEntity entity, UUID createdBy) {
    if (entity.getId() == null) {
        entity.setId(UUID.randomUUID());
    }
    
    // Use base class method for audit fields
    setCreationAuditFields(entity, createdBy);
    
    String sql = "INSERT INTO " + getTableName() + " (...) VALUES (...)";
    jdbcTemplate.update(sql, /* parameters */);
    
    // Use base class audit logging
    auditService.logSuccess("INSERT", getTableName(), entity.getId(), getEntityName());
    
    return entity;
}

@Override
public YourEntity update(YourEntity entity, UUID updatedBy) {
    // Use base class method for audit fields
    setUpdateAuditFields(entity, updatedBy);
    
    String sql = "UPDATE " + getTableName() + " SET ... WHERE id = ?";
    int rowsAffected = jdbcTemplate.update(sql, /* parameters */);
    
    if (rowsAffected > 0) {
        auditService.logSuccess("UPDATE", getTableName(), entity.getId(), getEntityName());
    }
    
    return entity;
}
```

### Step 4: Use Base Class Utilities in RowMapper
```java
private class YourEntityRowMapper implements RowMapper<YourEntity> {
    @Override
    public YourEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
        YourEntity entity = new YourEntity();
        
        // Use base class utility methods
        entity.setId(getUuid(rs, "id"));
        entity.setSomeJsonField(getJsonMap(rs, "json_column"));
        entity.setCreatedAt(getLocalDateTime(rs, "created_at"));
        entity.setUpdatedAt(getLocalDateTime(rs, "updated_at"));
        entity.setCreatedBy(getUuid(rs, "created_by"));
        entity.setUpdatedBy(getUuid(rs, "updated_by"));
        
        return entity;
    }
}
```

## Key Benefits

### 1. Audit Trail Compliance
- **INSERT operations**: Only set `created_at` and `created_by`
- **UPDATE operations**: Only set `updated_at` and `updated_by`
- Prevents audit trail violations that were system-wide issues

### 2. Consistent Error Handling
- Standardized exception handling across all repositories
- Consistent audit logging for success and failure cases
- Defensive programming for null values and edge cases

### 3. Code Reuse
- Common SQL utilities (UUID conversion, JSON mapping, timestamp handling)
- Shared audit field management using reflection
- Consistent database operation patterns

### 4. Maintainability
- Single place to update common repository behavior
- Easier to add new repository functionality
- Consistent patterns across all repositories

## Migration Strategy

### Phase 1: Create New Implementations
1. Create new repository implementations using base classes
2. Keep existing repositories unchanged initially
3. Test new implementations thoroughly

### Phase 2: Service Layer Updates
1. Update service classes to use new repository implementations
2. Gradually migrate service by service
3. Run comprehensive tests after each migration

### Phase 3: Remove Legacy Code
1. Remove old repository implementations
2. Clean up unused imports and dependencies
3. Update documentation and comments

## Example Usage

The base classes provide a complete foundation for repository standardization:

### AbstractRepository Capabilities:
- **Audit Trail Management**: Proper INSERT vs UPDATE semantics
- **JSON Utilities**: JSONB column conversion with ObjectMapper
- **ResultSet Helpers**: Safe UUID, LocalDateTime, and JSON mapping
- **Reflection Support**: Automated audit field setting via reflection
- **Error Handling**: Consistent audit logging for all operations

### BaseRepository Interface:
- **Standard CRUD**: findById, findAll, save, update, delete operations
- **Audit-Aware Operations**: save() for INSERT, update() for UPDATE
- **Smart Operations**: saveOrUpdate() automatically chooses INSERT vs UPDATE
- **Optional Extensions**: findByActive() for entities with active field

## Validation

### Compilation Test
```bash
mvn clean compile
```
- All repository base classes compile successfully
- No audit field visibility issues
- Proper dependency injection setup

### Integration Test
- Test CRUD operations with proper audit trails
- Verify JSON column handling
- Confirm business-specific finder methods work correctly

## Next Steps

1. **Apply pattern to remaining repositories** - Refactor existing repositories one by one
2. **Add validation annotations** - Enhance entities with proper validation
3. **Performance optimization** - Add caching and query optimization where needed
4. **Documentation** - Update repository documentation with new patterns

This standardization creates a unified, maintainable repository layer that follows proper OOP principles and ensures consistent audit trail handling across the entire application.