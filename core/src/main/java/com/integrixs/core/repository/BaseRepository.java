package com.integrixs.core.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Base repository interface defining common CRUD operations for all entities.
 * Part of Phase 5 repository layer standardization following OOP principles.
 * 
 * @param <T> The entity type
 */
public interface BaseRepository<T> {
    
    /**
     * Find an entity by its ID.
     * @param id the entity ID
     * @return Optional containing the entity if found, empty otherwise
     */
    Optional<T> findById(UUID id);
    
    /**
     * Find all entities.
     * @return List of all entities
     */
    List<T> findAll();
    
    /**
     * Save a new entity (INSERT operation).
     * Sets audit fields for creation.
     * @param entity the entity to save
     * @param createdBy the user creating the entity
     * @return the saved entity with generated ID and audit fields
     */
    T save(T entity, UUID createdBy);
    
    /**
     * Update an existing entity (UPDATE operation).
     * Sets audit fields for update.
     * @param entity the entity to update
     * @param updatedBy the user updating the entity
     * @return the updated entity with updated audit fields
     */
    T update(T entity, UUID updatedBy);
    
    /**
     * Save or update entity based on whether ID exists.
     * Delegates to save() or update() with proper audit handling.
     * @param entity the entity to save or update
     * @param userId the user performing the operation
     * @return the saved/updated entity
     */
    T saveOrUpdate(T entity, UUID userId);
    
    /**
     * Delete an entity by ID.
     * @param id the entity ID to delete
     * @return true if entity was deleted, false if not found
     */
    boolean deleteById(UUID id);
    
    /**
     * Check if entity exists by ID.
     * @param id the entity ID
     * @return true if entity exists, false otherwise
     */
    boolean existsById(UUID id);
    
    /**
     * Count all entities.
     * @return total number of entities
     */
    long count();
    
    /**
     * Find entities by active status (if entity supports active/inactive).
     * Default implementation throws UnsupportedOperationException.
     * Override in repositories for entities that have active field.
     * @param active the active status
     * @return list of entities matching the active status
     */
    default List<T> findByActive(boolean active) {
        throw new UnsupportedOperationException("Entity does not support active/inactive status");
    }
}