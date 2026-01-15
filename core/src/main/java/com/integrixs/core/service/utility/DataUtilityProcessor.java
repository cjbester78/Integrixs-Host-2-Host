package com.integrixs.core.service.utility;

import com.integrixs.shared.model.FlowExecutionStep;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Utility processor for data processing operations
 * Handles CSV and XML transform, parse, validate operations following Single Responsibility Principle
 */
@Service
public class DataUtilityProcessor extends AbstractUtilityProcessor {
    
    private static final String UTILITY_TYPE = "DATA";
    private static final String DEFAULT_CSV_DELIMITER = ",";
    private static final String DEFAULT_CSV_ENCODING = "UTF-8";
    
    @Override
    public String getUtilityType() {
        return UTILITY_TYPE;
    }
    
    @Override
    public Map<String, Object> executeUtility(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        logExecutionStart(step, "Data utility");
        long startTime = System.currentTimeMillis();
        
        try {
            String operation = getConfigValue(configuration, "operation", "transform");
            String dataType = getConfigValue(configuration, "dataType", "csv");
            
            String operationKey = dataType.toLowerCase() + "_" + operation.toLowerCase();
            
            switch (operationKey) {
                case "csv_transform":
                    return executeCsvTransform(step, context, configuration);
                case "csv_parse":
                    return executeCsvParse(step, context, configuration);
                case "csv_validate":
                    return executeCsvValidate(step, context, configuration);
                case "csv_merge":
                    return executeCsvMerge(step, context, configuration);
                case "xml_transform":
                    return executeXmlTransform(step, context, configuration);
                case "xml_parse":
                    return executeXmlParse(step, context, configuration);
                case "xml_validate":
                    return executeXmlValidate(step, context, configuration);
                case "xml_xpath":
                    return executeXmlXPath(step, context, configuration);
                default:
                    throw new IllegalArgumentException("Unsupported data operation: " + dataType + "_" + operation);
            }
            
        } catch (Exception e) {
            logExecutionError(step, "Data utility", e);
            return createErrorResult("Data utility execution failed: " + e.getMessage(), e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logExecutionComplete(step, "Data utility", duration);
        }
    }
    
    /**
     * Execute CSV transform operation
     */
    private Map<String, Object> executeCsvTransform(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        try {
            validateConfiguration(configuration, "sourceFile", "targetFile");
            
            String sourceFile = (String) configuration.get("sourceFile");
            String targetFile = (String) configuration.get("targetFile");
            String delimiter = getConfigValue(configuration, "delimiter", DEFAULT_CSV_DELIMITER);
            String encoding = getConfigValue(configuration, "encoding", DEFAULT_CSV_ENCODING);
            boolean hasHeader = getConfigValue(configuration, "hasHeader", true);
            @SuppressWarnings("unchecked")
            Map<String, String> columnMappings = getConfigValue(configuration, "columnMappings", new HashMap<>());
            @SuppressWarnings("unchecked")
            List<String> requiredColumns = getConfigValue(configuration, "requiredColumns", new ArrayList<>());
            @SuppressWarnings("unchecked")
            Map<String, String> defaultValues = getConfigValue(configuration, "defaultValues", new HashMap<>());
            
            validateFilePath(sourceFile);
            validateFilePath(targetFile);
            
            Path sourcePath = Paths.get(sourceFile);
            Path targetPath = Paths.get(targetFile);
            
            if (!Files.exists(sourcePath)) {
                return createErrorResult("Source file does not exist: " + sourceFile, null);
            }
            
            ensureDirectoryExists(targetPath.getParent().toString());
            
            // Transform CSV
            List<List<String>> transformedData = new ArrayList<>();
            List<String> headerRow = null;
            int recordsProcessed = 0;
            int recordsTransformed = 0;
            List<String> errors = new ArrayList<>();
            
            try (BufferedReader reader = Files.newBufferedReader(sourcePath, 
                    java.nio.charset.Charset.forName(encoding))) {
                
                String line;
                boolean isFirstLine = true;
                
                while ((line = reader.readLine()) != null) {
                    try {
                        List<String> columns = parseCsvLine(line, delimiter);
                        recordsProcessed++;
                        
                        if (isFirstLine && hasHeader) {
                            headerRow = new ArrayList<>(columns);
                            
                            // Apply column mappings to header
                            for (int i = 0; i < headerRow.size(); i++) {
                                String originalName = headerRow.get(i);
                                if (columnMappings.containsKey(originalName)) {
                                    headerRow.set(i, columnMappings.get(originalName));
                                }
                            }
                            
                            transformedData.add(headerRow);
                            isFirstLine = false;
                            continue;
                        }
                        
                        isFirstLine = false;
                        
                        // Transform data row
                        List<String> transformedRow = transformCsvRow(columns, headerRow, 
                            columnMappings, requiredColumns, defaultValues);
                        
                        if (transformedRow != null) {
                            transformedData.add(transformedRow);
                            recordsTransformed++;
                        }
                        
                    } catch (Exception e) {
                        String error = "Failed to transform row " + recordsProcessed + ": " + e.getMessage();
                        errors.add(error);
                        logger.error("CSV transform error: {}", error, e);
                    }
                }
            }
            
            // Write transformed CSV
            try (BufferedWriter writer = Files.newBufferedWriter(targetPath, 
                    java.nio.charset.Charset.forName(encoding))) {
                
                for (List<String> row : transformedData) {
                    writer.write(String.join(delimiter, row));
                    writer.newLine();
                }
            }
            
            // Update execution context
            updateExecutionContext(context, "csvTransformResult", targetFile);
            updateExecutionContext(context, "csvTransformStats", Map.of(
                "recordsProcessed", recordsProcessed,
                "recordsTransformed", recordsTransformed
            ));
            
            // Create result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("sourceFile", sourceFile);
            resultData.put("targetFile", targetFile);
            resultData.put("recordsProcessed", recordsProcessed);
            resultData.put("recordsTransformed", recordsTransformed);
            resultData.put("headerRow", headerRow);
            resultData.put("delimiter", delimiter);
            resultData.put("encoding", encoding);
            resultData.put("hasHeader", hasHeader);
            
            if (!errors.isEmpty()) {
                resultData.put("errors", errors);
            }
            
            String message = String.format("Successfully transformed %d of %d CSV records", 
                recordsTransformed, recordsProcessed);
            
            return createSuccessResult(message, resultData);
            
        } catch (Exception e) {
            logger.error("CSV transform failed", e);
            return createErrorResult("CSV transform failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute CSV parse operation
     */
    private Map<String, Object> executeCsvParse(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        try {
            validateConfiguration(configuration, "sourceFile");
            
            String sourceFile = (String) configuration.get("sourceFile");
            String delimiter = getConfigValue(configuration, "delimiter", DEFAULT_CSV_DELIMITER);
            String encoding = getConfigValue(configuration, "encoding", DEFAULT_CSV_ENCODING);
            boolean hasHeader = getConfigValue(configuration, "hasHeader", true);
            Integer maxRecords = getConfigValue(configuration, "maxRecords", null);
            
            validateFilePath(sourceFile);
            
            Path sourcePath = Paths.get(sourceFile);
            
            if (!Files.exists(sourcePath)) {
                return createErrorResult("Source file does not exist: " + sourceFile, null);
            }
            
            // Parse CSV
            List<Map<String, Object>> records = new ArrayList<>();
            List<String> headers = null;
            int recordsParsed = 0;
            List<String> errors = new ArrayList<>();
            
            try (BufferedReader reader = Files.newBufferedReader(sourcePath, 
                    java.nio.charset.Charset.forName(encoding))) {
                
                String line;
                boolean isFirstLine = true;
                
                while ((line = reader.readLine()) != null && 
                       (maxRecords == null || recordsParsed < maxRecords)) {
                    try {
                        List<String> columns = parseCsvLine(line, delimiter);
                        
                        if (isFirstLine && hasHeader) {
                            headers = new ArrayList<>(columns);
                            isFirstLine = false;
                            continue;
                        }
                        
                        isFirstLine = false;
                        
                        // Create record map
                        Map<String, Object> record = new HashMap<>();
                        
                        if (headers != null) {
                            for (int i = 0; i < Math.min(columns.size(), headers.size()); i++) {
                                record.put(headers.get(i), columns.get(i));
                            }
                        } else {
                            // Use column indices as keys
                            for (int i = 0; i < columns.size(); i++) {
                                record.put("column_" + i, columns.get(i));
                            }
                        }
                        
                        record.put("_rowNumber", recordsParsed + 1);
                        records.add(record);
                        recordsParsed++;
                        
                    } catch (Exception e) {
                        String error = "Failed to parse row " + (recordsParsed + 1) + ": " + e.getMessage();
                        errors.add(error);
                        logger.error("CSV parse error: {}", error, e);
                    }
                }
            }
            
            // Update execution context
            updateExecutionContext(context, "csvParseResult", records);
            updateExecutionContext(context, "csvHeaders", headers);
            
            // Create result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("sourceFile", sourceFile);
            resultData.put("records", records);
            resultData.put("recordsParsed", recordsParsed);
            resultData.put("headers", headers);
            resultData.put("delimiter", delimiter);
            resultData.put("encoding", encoding);
            resultData.put("hasHeader", hasHeader);
            
            if (!errors.isEmpty()) {
                resultData.put("errors", errors);
            }
            
            String message = String.format("Successfully parsed %d CSV records", recordsParsed);
            
            return createSuccessResult(message, resultData);
            
        } catch (Exception e) {
            logger.error("CSV parse failed", e);
            return createErrorResult("CSV parse failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute CSV validate operation
     */
    private Map<String, Object> executeCsvValidate(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        try {
            validateConfiguration(configuration, "sourceFile");
            
            String sourceFile = (String) configuration.get("sourceFile");
            String delimiter = getConfigValue(configuration, "delimiter", DEFAULT_CSV_DELIMITER);
            String encoding = getConfigValue(configuration, "encoding", DEFAULT_CSV_ENCODING);
            boolean hasHeader = getConfigValue(configuration, "hasHeader", true);
            @SuppressWarnings("unchecked")
            List<String> requiredColumns = getConfigValue(configuration, "requiredColumns", new ArrayList<>());
            Integer expectedColumnCount = getConfigValue(configuration, "expectedColumnCount", null);
            
            validateFilePath(sourceFile);
            
            Path sourcePath = Paths.get(sourceFile);
            
            if (!Files.exists(sourcePath)) {
                return createErrorResult("Source file does not exist: " + sourceFile, null);
            }
            
            // Validate CSV
            List<String> headers = null;
            int recordsValidated = 0;
            int validRecords = 0;
            int invalidRecords = 0;
            List<String> validationErrors = new ArrayList<>();
            List<Map<String, Object>> recordValidations = new ArrayList<>();
            
            try (BufferedReader reader = Files.newBufferedReader(sourcePath, 
                    java.nio.charset.Charset.forName(encoding))) {
                
                String line;
                boolean isFirstLine = true;
                
                while ((line = reader.readLine()) != null) {
                    try {
                        List<String> columns = parseCsvLine(line, delimiter);
                        recordsValidated++;
                        
                        if (isFirstLine && hasHeader) {
                            headers = new ArrayList<>(columns);
                            
                            // Validate header
                            for (String requiredColumn : requiredColumns) {
                                if (!headers.contains(requiredColumn)) {
                                    validationErrors.add("Missing required column: " + requiredColumn);
                                }
                            }
                            
                            isFirstLine = false;
                            continue;
                        }
                        
                        isFirstLine = false;
                        
                        // Validate record
                        Map<String, Object> recordValidation = validateCsvRecord(
                            columns, headers, expectedColumnCount, recordsValidated);
                        
                        recordValidations.add(recordValidation);
                        
                        boolean isValid = (Boolean) recordValidation.get("valid");
                        if (isValid) {
                            validRecords++;
                        } else {
                            invalidRecords++;
                            @SuppressWarnings("unchecked")
                            List<String> errors = (List<String>) recordValidation.get("errors");
                            validationErrors.addAll(errors);
                        }
                        
                    } catch (Exception e) {
                        String error = "Failed to validate row " + recordsValidated + ": " + e.getMessage();
                        validationErrors.add(error);
                        invalidRecords++;
                        logger.error("CSV validation error: {}", error, e);
                    }
                }
            }
            
            boolean isValid = invalidRecords == 0 && validationErrors.isEmpty();
            
            // Update execution context
            updateExecutionContext(context, "csvValidationResult", Map.of(
                "isValid", isValid,
                "validRecords", validRecords,
                "invalidRecords", invalidRecords
            ));
            
            // Create result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("sourceFile", sourceFile);
            resultData.put("isValid", isValid);
            resultData.put("recordsValidated", recordsValidated);
            resultData.put("validRecords", validRecords);
            resultData.put("invalidRecords", invalidRecords);
            resultData.put("headers", headers);
            resultData.put("recordValidations", recordValidations);
            resultData.put("delimiter", delimiter);
            resultData.put("encoding", encoding);
            resultData.put("hasHeader", hasHeader);
            
            if (!validationErrors.isEmpty()) {
                resultData.put("errors", validationErrors);
            }
            
            String message = String.format("CSV validation %s - %d valid, %d invalid records", 
                isValid ? "passed" : "failed", validRecords, invalidRecords);
            
            return isValid ? createSuccessResult(message, resultData) : 
                createResult(false, message, resultData);
            
        } catch (Exception e) {
            logger.error("CSV validation failed", e);
            return createErrorResult("CSV validation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute CSV merge operation
     */
    private Map<String, Object> executeCsvMerge(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        try {
            validateConfiguration(configuration, "sourceFiles", "targetFile");
            
            @SuppressWarnings("unchecked")
            List<String> sourceFiles = (List<String>) configuration.get("sourceFiles");
            String targetFile = (String) configuration.get("targetFile");
            String delimiter = getConfigValue(configuration, "delimiter", DEFAULT_CSV_DELIMITER);
            String encoding = getConfigValue(configuration, "encoding", DEFAULT_CSV_ENCODING);
            boolean hasHeader = getConfigValue(configuration, "hasHeader", true);
            boolean mergeHeaders = getConfigValue(configuration, "mergeHeaders", true);
            
            validateFilePath(targetFile);
            
            Path targetPath = Paths.get(targetFile);
            ensureDirectoryExists(targetPath.getParent().toString());
            
            // Merge CSV files
            Set<String> allHeaders = new LinkedHashSet<>();
            List<List<String>> allRecords = new ArrayList<>();
            int totalRecords = 0;
            int filesProcessed = 0;
            List<String> errors = new ArrayList<>();
            
            // First pass: collect all headers if merging
            if (hasHeader && mergeHeaders) {
                for (String sourceFileStr : sourceFiles) {
                    try {
                        validateFilePath(sourceFileStr);
                        Path sourcePath = Paths.get(sourceFileStr);
                        
                        if (!Files.exists(sourcePath)) {
                            errors.add("Source file does not exist: " + sourceFileStr);
                            continue;
                        }
                        
                        try (BufferedReader reader = Files.newBufferedReader(sourcePath, 
                                java.nio.charset.Charset.forName(encoding))) {
                            String headerLine = reader.readLine();
                            if (headerLine != null) {
                                List<String> headers = parseCsvLine(headerLine, delimiter);
                                allHeaders.addAll(headers);
                            }
                        }
                        
                    } catch (Exception e) {
                        String error = "Failed to read headers from " + sourceFileStr + ": " + e.getMessage();
                        errors.add(error);
                        logger.error("CSV merge error: {}", error, e);
                    }
                }
            }
            
            // Second pass: merge data
            for (String sourceFileStr : sourceFiles) {
                try {
                    validateFilePath(sourceFileStr);
                    Path sourcePath = Paths.get(sourceFileStr);
                    
                    if (!Files.exists(sourcePath)) {
                        continue; // Already logged error above
                    }
                    
                    List<String> fileHeaders = null;
                    
                    try (BufferedReader reader = Files.newBufferedReader(sourcePath, 
                            java.nio.charset.Charset.forName(encoding))) {
                        
                        String line;
                        boolean isFirstLine = true;
                        
                        while ((line = reader.readLine()) != null) {
                            List<String> columns = parseCsvLine(line, delimiter);
                            
                            if (isFirstLine && hasHeader) {
                                fileHeaders = new ArrayList<>(columns);
                                isFirstLine = false;
                                
                                // Add header to merged file only once for first file
                                if (filesProcessed == 0 && mergeHeaders) {
                                    allRecords.add(new ArrayList<>(allHeaders));
                                }
                                continue;
                            }
                            
                            isFirstLine = false;
                            
                            // Align columns with merged headers
                            List<String> alignedRecord = alignCsvRecord(columns, fileHeaders, allHeaders);
                            allRecords.add(alignedRecord);
                            totalRecords++;
                        }
                    }
                    
                    filesProcessed++;
                    
                } catch (Exception e) {
                    String error = "Failed to merge file " + sourceFileStr + ": " + e.getMessage();
                    errors.add(error);
                    logger.error("CSV merge error: {}", error, e);
                }
            }
            
            // Write merged CSV
            try (BufferedWriter writer = Files.newBufferedWriter(targetPath, 
                    java.nio.charset.Charset.forName(encoding))) {
                
                for (List<String> row : allRecords) {
                    writer.write(String.join(delimiter, row));
                    writer.newLine();
                }
            }
            
            // Update execution context
            updateExecutionContext(context, "csvMergeResult", targetFile);
            updateExecutionContext(context, "csvMergeStats", Map.of(
                "filesProcessed", filesProcessed,
                "totalRecords", totalRecords
            ));
            
            // Create result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("sourceFiles", sourceFiles);
            resultData.put("targetFile", targetFile);
            resultData.put("filesProcessed", filesProcessed);
            resultData.put("totalRecords", totalRecords);
            resultData.put("mergedHeaders", new ArrayList<>(allHeaders));
            resultData.put("delimiter", delimiter);
            resultData.put("encoding", encoding);
            resultData.put("hasHeader", hasHeader);
            resultData.put("mergeHeaders", mergeHeaders);
            
            if (!errors.isEmpty()) {
                resultData.put("errors", errors);
            }
            
            String message = String.format("Successfully merged %d files with %d total records", 
                filesProcessed, totalRecords);
            
            return createSuccessResult(message, resultData);
            
        } catch (Exception e) {
            logger.error("CSV merge failed", e);
            return createErrorResult("CSV merge failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute XML transform operation
     */
    private Map<String, Object> executeXmlTransform(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        try {
            validateConfiguration(configuration, "sourceFile", "targetFile");
            
            String sourceFile = (String) configuration.get("sourceFile");
            String targetFile = (String) configuration.get("targetFile");
            String encoding = getConfigValue(configuration, "encoding", "UTF-8");
            boolean formatOutput = getConfigValue(configuration, "formatOutput", true);
            @SuppressWarnings("unchecked")
            Map<String, String> elementMappings = getConfigValue(configuration, "elementMappings", new HashMap<>());
            @SuppressWarnings("unchecked")
            Map<String, String> attributeMappings = getConfigValue(configuration, "attributeMappings", new HashMap<>());
            
            validateFilePath(sourceFile);
            validateFilePath(targetFile);
            
            Path sourcePath = Paths.get(sourceFile);
            Path targetPath = Paths.get(targetFile);
            
            if (!Files.exists(sourcePath)) {
                return createErrorResult("Source file does not exist: " + sourceFile, null);
            }
            
            ensureDirectoryExists(targetPath.getParent().toString());
            
            // Parse and transform XML
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(sourcePath.toFile());
            
            int elementsTransformed = transformXmlElements(document, elementMappings, attributeMappings);
            
            // Write transformed XML
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, encoding);
            transformer.setOutputProperty(OutputKeys.INDENT, formatOutput ? "yes" : "no");
            
            DOMSource source = new DOMSource(document);
            StreamResult result = new StreamResult(targetPath.toFile());
            transformer.transform(source, result);
            
            // Update execution context
            updateExecutionContext(context, "xmlTransformResult", targetFile);
            updateExecutionContext(context, "xmlTransformStats", Map.of(
                "elementsTransformed", elementsTransformed
            ));
            
            // Create result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("sourceFile", sourceFile);
            resultData.put("targetFile", targetFile);
            resultData.put("elementsTransformed", elementsTransformed);
            resultData.put("encoding", encoding);
            resultData.put("formatOutput", formatOutput);
            
            String message = String.format("Successfully transformed XML with %d elements modified", 
                elementsTransformed);
            
            return createSuccessResult(message, resultData);
            
        } catch (Exception e) {
            logger.error("XML transform failed", e);
            return createErrorResult("XML transform failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute XML parse operation
     */
    private Map<String, Object> executeXmlParse(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        try {
            validateConfiguration(configuration, "sourceFile");
            
            String sourceFile = (String) configuration.get("sourceFile");
            String rootElement = getConfigValue(configuration, "rootElement", null);
            boolean includeAttributes = getConfigValue(configuration, "includeAttributes", true);
            Integer maxDepth = getConfigValue(configuration, "maxDepth", null);
            
            validateFilePath(sourceFile);
            
            Path sourcePath = Paths.get(sourceFile);
            
            if (!Files.exists(sourcePath)) {
                return createErrorResult("Source file does not exist: " + sourceFile, null);
            }
            
            // Parse XML
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(sourcePath.toFile());
            
            Map<String, Object> parsedData = new HashMap<>();
            Element root = document.getDocumentElement();
            
            if (rootElement == null || rootElement.equals(root.getNodeName())) {
                parsedData = parseXmlElement(root, includeAttributes, maxDepth, 0);
            } else {
                NodeList nodes = document.getElementsByTagName(rootElement);
                if (nodes.getLength() > 0) {
                    parsedData = parseXmlElement((Element) nodes.item(0), includeAttributes, maxDepth, 0);
                }
            }
            
            // Update execution context
            updateExecutionContext(context, "xmlParseResult", parsedData);
            
            // Create result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("sourceFile", sourceFile);
            resultData.put("parsedData", parsedData);
            resultData.put("rootElement", rootElement != null ? rootElement : root.getNodeName());
            resultData.put("includeAttributes", includeAttributes);
            resultData.put("maxDepth", maxDepth);
            
            String message = "Successfully parsed XML document";
            
            return createSuccessResult(message, resultData);
            
        } catch (Exception e) {
            logger.error("XML parse failed", e);
            return createErrorResult("XML parse failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute XML validate operation
     */
    private Map<String, Object> executeXmlValidate(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        try {
            validateConfiguration(configuration, "sourceFile");
            
            String sourceFile = (String) configuration.get("sourceFile");
            String schemaFile = getConfigValue(configuration, "schemaFile", null);
            
            validateFilePath(sourceFile);
            if (schemaFile != null) {
                validateFilePath(schemaFile);
            }
            
            Path sourcePath = Paths.get(sourceFile);
            
            if (!Files.exists(sourcePath)) {
                return createErrorResult("Source file does not exist: " + sourceFile, null);
            }
            
            // Validate XML
            boolean isValid = true;
            List<String> validationErrors = new ArrayList<>();
            
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                
                // Enable validation if schema provided
                if (schemaFile != null) {
                    Path schemaPath = Paths.get(schemaFile);
                    if (Files.exists(schemaPath)) {
                        factory.setValidating(true);
                    } else {
                        validationErrors.add("Schema file does not exist: " + schemaFile);
                        isValid = false;
                    }
                }
                
                DocumentBuilder builder = factory.newDocumentBuilder();
                builder.setErrorHandler(new org.xml.sax.ErrorHandler() {
                    @Override
                    public void warning(org.xml.sax.SAXParseException exception) {
                        validationErrors.add("Warning: " + exception.getMessage());
                    }
                    
                    @Override
                    public void error(org.xml.sax.SAXParseException exception) {
                        validationErrors.add("Error: " + exception.getMessage());
                    }
                    
                    @Override
                    public void fatalError(org.xml.sax.SAXParseException exception) throws SAXException {
                        validationErrors.add("Fatal Error: " + exception.getMessage());
                        throw exception;
                    }
                });
                
                Document document = builder.parse(sourcePath.toFile());
                
                // Additional structural validation
                if (document.getDocumentElement() == null) {
                    validationErrors.add("Document has no root element");
                    isValid = false;
                }
                
            } catch (ParserConfigurationException | SAXException | IOException e) {
                validationErrors.add("Parsing error: " + e.getMessage());
                isValid = false;
            }
            
            isValid = isValid && validationErrors.isEmpty();
            
            // Update execution context
            updateExecutionContext(context, "xmlValidationResult", Map.of(
                "isValid", isValid,
                "errorCount", validationErrors.size()
            ));
            
            // Create result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("sourceFile", sourceFile);
            resultData.put("schemaFile", schemaFile);
            resultData.put("isValid", isValid);
            resultData.put("errorCount", validationErrors.size());
            
            if (!validationErrors.isEmpty()) {
                resultData.put("errors", validationErrors);
            }
            
            String message = String.format("XML validation %s (%d errors)", 
                isValid ? "passed" : "failed", validationErrors.size());
            
            return isValid ? createSuccessResult(message, resultData) : 
                createResult(false, message, resultData);
            
        } catch (Exception e) {
            logger.error("XML validation failed", e);
            return createErrorResult("XML validation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute XML XPath operation
     */
    private Map<String, Object> executeXmlXPath(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        try {
            validateConfiguration(configuration, "sourceFile", "xpathExpression");
            
            String sourceFile = (String) configuration.get("sourceFile");
            String xpathExpression = (String) configuration.get("xpathExpression");
            String resultType = getConfigValue(configuration, "resultType", "NODESET");
            
            validateFilePath(sourceFile);
            
            Path sourcePath = Paths.get(sourceFile);
            
            if (!Files.exists(sourcePath)) {
                return createErrorResult("Source file does not exist: " + sourceFile, null);
            }
            
            // Execute XPath
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(sourcePath.toFile());
            
            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xpath = xpathFactory.newXPath();
            
            Object result;
            List<String> results = new ArrayList<>();
            int resultCount = 0;
            
            switch (resultType.toUpperCase()) {
                case "NODESET":
                    NodeList nodes = (NodeList) xpath.evaluate(xpathExpression, document, XPathConstants.NODESET);
                    resultCount = nodes.getLength();
                    for (int i = 0; i < nodes.getLength(); i++) {
                        Node node = nodes.item(i);
                        results.add(node.getTextContent());
                    }
                    result = results;
                    break;
                    
                case "STRING":
                    String stringResult = (String) xpath.evaluate(xpathExpression, document, XPathConstants.STRING);
                    results.add(stringResult);
                    resultCount = stringResult.isEmpty() ? 0 : 1;
                    result = stringResult;
                    break;
                    
                case "NUMBER":
                    Double numberResult = (Double) xpath.evaluate(xpathExpression, document, XPathConstants.NUMBER);
                    results.add(numberResult.toString());
                    resultCount = 1;
                    result = numberResult;
                    break;
                    
                case "BOOLEAN":
                    Boolean booleanResult = (Boolean) xpath.evaluate(xpathExpression, document, XPathConstants.BOOLEAN);
                    results.add(booleanResult.toString());
                    resultCount = 1;
                    result = booleanResult;
                    break;
                    
                default:
                    throw new IllegalArgumentException("Unsupported result type: " + resultType);
            }
            
            // Update execution context
            updateExecutionContext(context, "xpathResult", result);
            updateExecutionContext(context, "xpathResultCount", resultCount);
            
            // Create result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("sourceFile", sourceFile);
            resultData.put("xpathExpression", xpathExpression);
            resultData.put("resultType", resultType);
            resultData.put("result", result);
            resultData.put("results", results);
            resultData.put("resultCount", resultCount);
            
            String message = String.format("XPath expression returned %d results", resultCount);
            
            return createSuccessResult(message, resultData);
            
        } catch (Exception e) {
            logger.error("XML XPath failed", e);
            return createErrorResult("XML XPath failed: " + e.getMessage(), e);
        }
    }
    
    // Helper methods
    
    private List<String> parseCsvLine(String line, String delimiter) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == delimiter.charAt(0) && !inQuotes) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        
        result.add(current.toString().trim());
        return result;
    }
    
    private List<String> transformCsvRow(List<String> columns, List<String> headers, 
            Map<String, String> columnMappings, List<String> requiredColumns, 
            Map<String, String> defaultValues) {
        
        if (headers == null) {
            return columns; // No transformation possible without headers
        }
        
        List<String> transformedRow = new ArrayList<>();
        
        for (String header : headers) {
            String value = "";
            
            // Find value in original columns
            int columnIndex = findOriginalColumnIndex(header, headers, columnMappings);
            if (columnIndex >= 0 && columnIndex < columns.size()) {
                value = columns.get(columnIndex);
            }
            
            // Apply default value if empty and default provided
            if ((value == null || value.isEmpty()) && defaultValues.containsKey(header)) {
                value = defaultValues.get(header);
            }
            
            transformedRow.add(value);
        }
        
        return transformedRow;
    }
    
    private int findOriginalColumnIndex(String mappedHeader, List<String> headers, 
            Map<String, String> columnMappings) {
        
        // First check direct match
        int index = headers.indexOf(mappedHeader);
        if (index >= 0) {
            return index;
        }
        
        // Check reverse mapping
        for (Map.Entry<String, String> entry : columnMappings.entrySet()) {
            if (entry.getValue().equals(mappedHeader)) {
                return headers.indexOf(entry.getKey());
            }
        }
        
        return -1;
    }
    
    private Map<String, Object> validateCsvRecord(List<String> columns, List<String> headers, 
            Integer expectedColumnCount, int rowNumber) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("rowNumber", rowNumber);
        result.put("columnCount", columns.size());
        
        boolean valid = true;
        List<String> errors = new ArrayList<>();
        
        // Validate column count
        if (expectedColumnCount != null && columns.size() != expectedColumnCount) {
            valid = false;
            errors.add(String.format("Column count mismatch: expected %d, actual %d", 
                expectedColumnCount, columns.size()));
        }
        
        // Validate against headers if available
        if (headers != null && columns.size() != headers.size()) {
            valid = false;
            errors.add(String.format("Column count doesn't match headers: expected %d, actual %d", 
                headers.size(), columns.size()));
        }
        
        result.put("valid", valid);
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }
        
        return result;
    }
    
    private List<String> alignCsvRecord(List<String> columns, List<String> fileHeaders, 
            Set<String> allHeaders) {
        
        List<String> aligned = new ArrayList<>();
        
        for (String header : allHeaders) {
            String value = "";
            
            if (fileHeaders != null) {
                int index = fileHeaders.indexOf(header);
                if (index >= 0 && index < columns.size()) {
                    value = columns.get(index);
                }
            }
            
            aligned.add(value);
        }
        
        return aligned;
    }
    
    private int transformXmlElements(Document document, Map<String, String> elementMappings, 
            Map<String, String> attributeMappings) {
        
        int transformCount = 0;
        
        // Transform elements
        for (Map.Entry<String, String> entry : elementMappings.entrySet()) {
            NodeList elements = document.getElementsByTagName(entry.getKey());
            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);
                document.renameNode(element, element.getNamespaceURI(), entry.getValue());
                transformCount++;
            }
        }
        
        // Transform attributes
        for (Map.Entry<String, String> entry : attributeMappings.entrySet()) {
            NodeList allElements = document.getElementsByTagName("*");
            for (int i = 0; i < allElements.getLength(); i++) {
                Element element = (Element) allElements.item(i);
                if (element.hasAttribute(entry.getKey())) {
                    String value = element.getAttribute(entry.getKey());
                    element.removeAttribute(entry.getKey());
                    element.setAttribute(entry.getValue(), value);
                    transformCount++;
                }
            }
        }
        
        return transformCount;
    }
    
    private Map<String, Object> parseXmlElement(Element element, boolean includeAttributes, 
            Integer maxDepth, int currentDepth) {
        
        if (maxDepth != null && currentDepth >= maxDepth) {
            return Collections.singletonMap("_truncated", true);
        }
        
        Map<String, Object> result = new HashMap<>();
        
        // Add attributes
        if (includeAttributes && element.hasAttributes()) {
            Map<String, String> attributes = new HashMap<>();
            for (int i = 0; i < element.getAttributes().getLength(); i++) {
                Node attr = element.getAttributes().item(i);
                attributes.put(attr.getNodeName(), attr.getNodeValue());
            }
            result.put("_attributes", attributes);
        }
        
        // Process child nodes
        NodeList children = element.getChildNodes();
        Map<String, Object> childElements = new HashMap<>();
        StringBuilder textContent = new StringBuilder();
        
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String tagName = childElement.getTagName();
                
                Map<String, Object> childData = parseXmlElement(childElement, includeAttributes, 
                    maxDepth, currentDepth + 1);
                
                // Handle multiple elements with same name
                if (childElements.containsKey(tagName)) {
                    Object existing = childElements.get(tagName);
                    if (existing instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> list = (List<Object>) existing;
                        list.add(childData);
                    } else {
                        List<Object> list = new ArrayList<>();
                        list.add(existing);
                        list.add(childData);
                        childElements.put(tagName, list);
                    }
                } else {
                    childElements.put(tagName, childData);
                }
                
            } else if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getNodeValue().trim();
                if (!text.isEmpty()) {
                    textContent.append(text);
                }
            }
        }
        
        // Add text content if exists
        if (textContent.length() > 0) {
            result.put("_text", textContent.toString());
        }
        
        // Add child elements
        result.putAll(childElements);
        
        return result;
    }
}