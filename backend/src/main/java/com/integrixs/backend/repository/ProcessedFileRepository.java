package com.integrixs.backend.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.integrixs.adapters.file.FileProcessingResult;
import com.integrixs.adapters.file.FileProcessingStatus;
import com.integrixs.shared.service.SystemAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class ProcessedFileRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(ProcessedFileRepository.class);
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private SystemAuditService auditService;
    
    private final RowMapper<FileProcessingResult> rowMapper = new FileProcessingResultRowMapper();
    
    public void save(FileProcessingResult result, UUID adapterInterfaceId, UUID executionId) {
        String sql = """
            INSERT INTO processed_files (
                id, adapter_interface_id, execution_id, file_path, file_name, file_size,
                status, error_message, start_time, end_time, content_hash, metadata
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            """;
        
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String metadataJson = result.getMetadata() != null && !result.getMetadata().isEmpty() 
                ? objectMapper.writeValueAsString(result.getMetadata())
                : "{}";
                
            jdbcTemplate.update(sql,
                result.getId() != null ? result.getId() : UUID.randomUUID(),
                adapterInterfaceId,
                executionId,
                result.getFilePath() != null ? result.getFilePath().toString() : null,
                result.getFileName(),
                result.getFileSize(),
                result.getStatus() != null ? result.getStatus().name() : FileProcessingStatus.PENDING.name(),
                result.getErrorMessage(),
                result.getStartTime(),
                result.getEndTime(),
                result.getContentHash(),
                metadataJson
            );
            
            logger.debug("Saved processed file result: {}", result.getFileName());
            
            // Log audit trail for file processing
            UUID fileId = result.getId() != null ? result.getId() : UUID.randomUUID();
            auditService.logDatabaseOperation("INSERT", "processed_files", fileId, 
                result.getFileName(), true, null);
            
        } catch (Exception e) {
            logger.error("Failed to save processed file result: {}", result.getFileName(), e);
            
            // Log failed audit trail
            auditService.logDatabaseOperation("INSERT", "processed_files", null, 
                result.getFileName(), false, e.getMessage());
            
            throw new RuntimeException("Failed to save processed file result", e);
        }
    }
    
    public List<FileProcessingResult> findByAdapterInterfaceId(UUID adapterInterfaceId, int limit) {
        String sql = """
            SELECT * FROM processed_files 
            WHERE adapter_interface_id = ? 
            ORDER BY start_time DESC 
            LIMIT ?
            """;
        
        return jdbcTemplate.query(sql, rowMapper, adapterInterfaceId, limit);
    }
    
    public List<FileProcessingResult> findRecent(int limit) {
        String sql = """
            SELECT * FROM processed_files 
            ORDER BY start_time DESC 
            LIMIT ?
            """;
        
        return jdbcTemplate.query(sql, rowMapper, limit);
    }
    
    public List<FileProcessingResult> findByExecutionId(UUID executionId) {
        String sql = """
            SELECT * FROM processed_files 
            WHERE execution_id = ? 
            ORDER BY start_time
            """;
        
        return jdbcTemplate.query(sql, rowMapper, executionId);
    }
    
    private static class FileProcessingResultRowMapper implements RowMapper<FileProcessingResult> {
        @Override
        public FileProcessingResult mapRow(ResultSet rs, int rowNum) throws SQLException {
            FileProcessingResult result = new FileProcessingResult();
            
            result.setId((UUID) rs.getObject("id"));
            result.setAdapterInterfaceId((UUID) rs.getObject("adapter_interface_id"));
            
            String filePath = rs.getString("file_path");
            if (filePath != null) {
                result.setFilePath(java.nio.file.Paths.get(filePath));
            }
            
            result.setFileSize(rs.getLong("file_size"));
            
            String statusStr = rs.getString("status");
            if (statusStr != null) {
                try {
                    result.setStatus(FileProcessingStatus.valueOf(statusStr));
                } catch (IllegalArgumentException e) {
                    result.setStatus(FileProcessingStatus.PENDING);
                }
            }
            
            result.setErrorMessage(rs.getString("error_message"));
            result.setStartTime(rs.getObject("start_time", LocalDateTime.class));
            result.setEndTime(rs.getObject("end_time", LocalDateTime.class));
            result.setContentHash(rs.getString("content_hash"));
            
            // Parse metadata JSON
            String metadataJson = rs.getString("metadata");
            if (metadataJson != null && !metadataJson.isEmpty()) {
                try {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> metadata = new ObjectMapper()
                        .readValue(metadataJson, java.util.Map.class);
                    result.setMetadata(metadata);
                } catch (Exception e) {
                    logger.warn("Failed to parse metadata JSON for processed file: {}", result.getId());
                }
            }
            
            return result;
        }
    }
}