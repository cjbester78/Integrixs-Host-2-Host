# File Adapter Configuration and Logic

## Overview
File adapters in integration systems are split into two distinct components: **Sender Adapters** (read files) and **Receiver Adapters** (write files). Each has specific configurations and responsibilities.

---

## Sender Adapter Configuration

### Purpose
The Sender Adapter reads files from a source directory and passes the content to the integration flow.

### Required Configuration
| Parameter | Description | Required | Example |
|-----------|-------------|----------|---------|
| `sourceDirectory` | Directory to monitor for files | **Yes** | `/data/incoming/payments` |
| `filePattern` | File name pattern to match | **Yes** | `Payment_*.xml` |
| `processingMode` | Post-processing behavior | **Yes** | `Test`, `Archive`, `Delete` |

### Optional Configuration
| Parameter | Description | Default | Example |
|-----------|-------------|---------|---------|
| `archiveDirectory` | Where to move processed files | None | `/data/archive/payments` |
| `errorDirectory` | Where to move failed files | None | `/data/error/payments` |
| `fileAge` | Minimum file age before pickup | `0` | `30` (seconds) |
| `maxFileSize` | Maximum file size to process | No limit | `10485760` (10MB) |
| `addTimestamp` | Add timestamp to archived files | `false` | `true/false` |
| `zipProcessing` | Extract ZIP files | `true` | `true/false` |
| `retryAttempts` | Retry failed reads | `3` | `0-10` |

### Sender Logic Flow
```
1. Monitor sourceDirectory for files matching filePattern
2. Check file age and size constraints
3. Read file content into memory/stream
4. Pass file data to integration flow
5. Apply processingMode:
   - Test: Leave file in source (for testing)
   - Archive: Move file to archiveDirectory
   - Delete: Remove file from source
6. Handle errors by moving to errorDirectory
```

---

## Receiver Adapter Configuration

### Purpose
The Receiver Adapter writes data from the integration flow to target files.

### Required Configuration
| Parameter | Description | Required | Example |
|-----------|-------------|----------|---------|
| `targetDirectory` | Directory to write files to | **Yes** | `/data/outgoing/responses` |
| `fileName` | Output file name pattern | **Yes** | `Response_{timestamp}.xml` |

### Optional Configuration
| Parameter | Description | Default | Example |
|-----------|-------------|---------|---------|
| `fileEncoding` | Character encoding for output | `UTF-8` | `ISO-8859-1` |
| `createDirectories` | Create target path if missing | `true` | `true/false` |
| `overwriteExisting` | Overwrite existing files | `false` | `true/false` |
| `tempFileExtension` | Extension for temp files | `.tmp` | `.writing` |
| `addTimestamp` | Add timestamp to filename | `false` | `true/false` |
| `compressOutput` | Compress output files | `false` | `true/false` |
| `validateOutput` | Validate written content | `false` | `true/false` |

### Receiver Logic Flow
```
1. Receive data from integration flow
2. Generate target filename using pattern
3. Create temporary file with .tmp extension
4. Write data to temporary file
5. Validate written content (if enabled)
6. Rename temporary file to final name (atomic operation)
7. Log successful write operation
```

---

## Adapter Processing Logic

### Sender Adapter Execution Logic

#### File Discovery Phase
```java
1. Scan sourceDirectory for files
2. Filter by filePattern (wildcards/regex)
3. Check file age (must be older than fileAge setting)
4. Check file size (must be under maxFileSize)
5. Verify file is not locked/in use
6. Sort files by discovery order or timestamp
```

#### File Reading Phase
```java
1. Open file for reading
2. Detect file encoding automatically
3. Handle special file types:
   - ZIP: Extract all files
   - Large files: Stream reading
   - Binary: Base64 encoding
4. Create file metadata (size, timestamp, path)
5. Pass content + metadata to flow
```

#### Post-Processing Phase
```java
switch (processingMode.toUpperCase()) {
    case "TEST":
        // Leave file in sourceDirectory unchanged
        logger.info("Test mode: File remains in source for reprocessing");
        break;
        
    case "ARCHIVE":
        // Move file to archiveDirectory
        if (archiveDirectory != null) {
            moveToArchive(file, archiveDirectory, addTimestamp);
        } else {
            logger.warn("Archive mode but no archiveDirectory configured");
        }
        break;
        
    case "DELETE":
        // Delete file from sourceDirectory
        deleteFile(file);
        logger.info("File deleted after successful processing");
        break;
        
    default:
        logger.warn("Unknown processing mode: " + processingMode);
        // Default to Test behavior (leave file)
}
```

#### Error Handling Logic
```java
try {
    processFile(file);
} catch (Exception e) {
    if (errorDirectory != null) {
        moveToError(file, errorDirectory);
        logger.error("File moved to error directory: " + e.getMessage());
    } else {
        // Leave file in source for manual intervention
        markFileAsErrored(file);
        logger.error("File processing failed: " + e.getMessage());
    }
    
    if (retryAttempts > 0) {
        scheduleRetry(file, retryAttempts);
    }
}
```

### Receiver Adapter Execution Logic

#### File Writing Phase
```java
1. Validate targetDirectory exists/is writable
2. Generate unique filename:
   - Apply fileName pattern
   - Add timestamp if configured
   - Ensure uniqueness (append counter if needed)
3. Create temporary file: finalName + tempFileExtension
4. Write data to temporary file
5. Flush and sync to disk
6. Atomic rename: temp → final name
```

#### Content Processing Logic
```java
1. Receive payload from integration flow
2. Apply content transformations:
   - Character encoding conversion
   - Data formatting/serialization
   - Compression (if enabled)
3. Validate content structure (if enabled)
4. Calculate content metadata (size, checksum)
5. Write with error recovery mechanisms
```

#### Delivery Confirmation Logic
```java
try {
    writeFile(content, targetPath);
    
    // Verify file was written correctly
    if (validateOutput) {
        validateWrittenContent(targetPath, originalContent);
    }
    
    // Log success metrics
    logSuccessfulDelivery(fileName, fileSize, processingTime);
    
    return SUCCESS;
    
} catch (IOException e) {
    // Clean up partial files
    deleteTemporaryFiles();
    
    // Retry logic
    if (retryAttempts > 0) {
        scheduleRetry(content, targetPath, retryAttempts - 1);
    } else {
        return FAILURE;
    }
}
```

---

## Integration Flow Interaction

### Data Flow Pattern
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Sender Adapter │───▶│ Integration     │───▶│ Receiver Adapter│
│  (File Reader)  │    │ Flow Processing │    │ (File Writer)   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ sourceDirectory │    │   Transform     │    │ targetDirectory │
│ processingMode  │    │   Validate      │    │ fileName Pattern│
│ filePattern     │    │   Route         │    │ fileEncoding    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### Metadata Passing
The Sender Adapter provides metadata that flows through the integration:

```json
{
  "fileContent": "base64EncodedContent",
  "fileName": "Payment_20240108_001.xml",
  "fileSize": 2048,
  "filePath": "/data/incoming/payments/Payment_20240108_001.xml",
  "archiveFilePath": "/data/archive/payments/20240108_103000_Payment_20240108_001.xml",
  "fileTimestamp": "2024-01-08T10:30:00Z",
  "fileEncoding": "UTF-8",
  "processingMode": "Archive",
  "sourceDirectory": "/data/incoming/payments",
  "archiveDirectory": "/data/archive/payments"
}
```

**Key Paths Explained**:
- `filePath`: Original source file location (where file was read from)
- `archiveFilePath`: Final archive destination (where file will be moved to)
- `sourceDirectory`: Base source directory being monitored
- `archiveDirectory`: Base archive directory for processed files

### Error Propagation
- **Sender Errors**: Stop flow execution, handle via processingMode
- **Flow Errors**: Stop execution, sender should not post-process
- **Receiver Errors**: Flow fails, sender should not post-process

### Success Confirmation
- **Flow Success**: Sender applies processingMode (Archive/Delete/Test)
- **Flow Failure**: Sender leaves file in source or moves to error directory
- **Partial Success**: Handle based on error tolerance configuration

---

## Processing Mode Behavior Summary

| Mode | Source File Action | Use Case | Reprocessing | Error Handling |
|------|-------------------|----------|--------------|----------------|
| `Test` | **Remains in source** | Development, Testing | ✅ Available | Remains in source |
| `Archive` | **Moved to archive** | Production with audit | ❌ Not available | Moved to error dir |
| `Delete` | **Deleted** | Production, space-constrained | ❌ Lost forever | Moved to error dir |

**Critical Rule**: `processingMode = "Test"` means **NO post-processing that removes files from source**. This is the fundamental behavior for testing scenarios.