package org.jvmscript.dataframe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import org.dflib.DataFrame;
import org.dflib.csv.Csv;
import org.dflib.json.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility for loading CSV data into DFLib DataFrames using JSON Schema for type inference and validation.
 *
 * Features:
 * - Full Draft 2020-12 schema support via NetworkNT json-schema-validator
 * - Automatic filtering of invalid rows during loading
 * - Separate output files for valid and invalid data
 * - Handles nested "items" schema for array-type schemas
 * - Supports nullable types (type: ["string", "null"])
 * - Custom date/datetime format patterns
 * - Enum validation
 * - Required field validation
 * - Comprehensive error reporting with JSON Pointer paths
 */
public class JsonSchemaDataFrameLoader {

    private static final ObjectMapper mapper = new ObjectMapper();

     /**
     * Schema metadata for a single column
     */
    private static class ColumnSchema {
        String name;
        Class<?> type;
        boolean required;
        boolean nullable;
        List<String> enumValues;
        String pattern;
        String format;
        Number minimum;
        Number maximum;

        ColumnSchema(String name) {
            this.name = name;
            this.required = false;
            this.nullable = false;
        }

        @Override
        public String toString() {
            return String.format("Column{name='%s', type=%s, required=%s, nullable=%s}",
                    name, type != null ? type.getSimpleName() : "String", required, nullable);
        }
    }

    /**
     * Map JSON Schema types to Java/DFLib types
     */
    private static Class<?> mapJsonSchemaType(JsonNode typeNode, JsonNode formatNode, JsonNode patternNode) {
        if (typeNode == null) {
            return String.class;
        }

        if (typeNode.isArray()) {
            for (JsonNode type : typeNode) {
                String typeStr = type.asText();
                if (!"null".equals(typeStr)) {
                    return mapSingleType(typeStr, formatNode, patternNode);
                }
            }
            return String.class;
        }

        String type = typeNode.asText();
        return mapSingleType(type, formatNode, patternNode);
    }

    private static Class<?> mapSingleType(String type, JsonNode formatNode, JsonNode patternNode) {
        switch (type) {
            case "integer":
                // Check for int64 format -> Long
                if (formatNode != null && "int64".equals(formatNode.asText())) {
                    return Long.class;
                }
                return Integer.class;
            case "number":
                return Double.class;
            case "boolean":
                return Boolean.class;
            case "string":
                if (formatNode != null) {
                    String format = formatNode.asText();
                    switch (format) {
                        case "date":
                            return LocalDate.class;
                        case "date-time":
                            return LocalDateTime.class;
                        default:
                            return String.class;
                    }
                }
                if (patternNode != null) {
                    String pattern = patternNode.asText();
                    if (pattern.contains("[0-9]{2}-[0-9]{2}-[0-9]{4}") ||
                            pattern.contains("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                        return LocalDateTime.class;
                    }
                }
                return String.class;
            case "array":
            case "object":
            default:
                return String.class;
        }
    }

    /**
     * Parse a CSV line handling quoted fields
     */
    private static String[] parseCSVLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());

        return result.toArray(new String[0]);
    }

    /**
     * Extract column schemas from JSON Schema
     */
    private static Map<String, ColumnSchema> extractColumnSchemas(JsonNode schemaNode) {
        Map<String, ColumnSchema> columnSchemas = new LinkedHashMap<>();

        JsonNode itemsNode = schemaNode.get("items");
        if (itemsNode != null) {
            schemaNode = itemsNode;
        }

        JsonNode propsNode = schemaNode.get("properties");
        if (propsNode == null) {
            throw new IllegalArgumentException("Schema must define 'properties' for columns");
        }

        Set<String> requiredFields = new HashSet<>();
        JsonNode requiredNode = schemaNode.get("required");
        if (requiredNode != null && requiredNode.isArray()) {
            requiredNode.forEach(node -> requiredFields.add(node.asText()));
        }

        Iterator<Map.Entry<String, JsonNode>> fields = propsNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String columnName = field.getKey();
            JsonNode propSchema = field.getValue();

            ColumnSchema colSchema = new ColumnSchema(columnName);
            colSchema.required = requiredFields.contains(columnName);

            JsonNode typeNode = propSchema.get("type");
            JsonNode formatNode = propSchema.get("format");
            JsonNode patternNode = propSchema.get("pattern");

            if (typeNode != null && typeNode.isArray()) {
                for (JsonNode type : typeNode) {
                    if ("null".equals(type.asText())) {
                        colSchema.nullable = true;
                        break;
                    }
                }
            }

            colSchema.type = mapJsonSchemaType(typeNode, formatNode, patternNode);

            if (formatNode != null) {
                colSchema.format = formatNode.asText();
            }
            if (patternNode != null) {
                colSchema.pattern = patternNode.asText();
            }

            JsonNode enumNode = propSchema.get("enum");
            if (enumNode != null && enumNode.isArray()) {
                colSchema.enumValues = new ArrayList<>();
                enumNode.forEach(node -> {
                    if (!node.isNull()) {
                        colSchema.enumValues.add(node.asText());
                    }
                });
            }

            JsonNode minNode = propSchema.get("minimum");
            if (minNode != null) {
                colSchema.minimum = minNode.numberValue();
            }

            JsonNode maxNode = propSchema.get("maximum");
            if (maxNode != null) {
                colSchema.maximum = maxNode.numberValue();
            }

            columnSchemas.put(columnName, colSchema);
        }

        return columnSchemas;
    }

    /**
     * Create a JsonSchemaFactory for the appropriate schema version
     */
    private static JsonSchemaFactory createSchemaFactory(JsonNode schemaNode) {
        JsonNode schemaVersion = schemaNode.get("$schema");
        SpecVersion.VersionFlag version = SpecVersion.VersionFlag.V202012;

        if (schemaVersion != null) {
            String schemaUri = schemaVersion.asText();
            if (schemaUri.contains("draft-07") || schemaUri.contains("draft/7")) {
                version = SpecVersion.VersionFlag.V7;
            } else if (schemaUri.contains("draft-06") || schemaUri.contains("draft/6")) {
                version = SpecVersion.VersionFlag.V6;
            } else if (schemaUri.contains("draft-04") || schemaUri.contains("draft/4")) {
                version = SpecVersion.VersionFlag.V4;
            } else if (schemaUri.contains("2019-09")) {
                version = SpecVersion.VersionFlag.V201909;
            } else if (schemaUri.contains("2020-12")) {
                version = SpecVersion.VersionFlag.V202012;
            }
        }

        return JsonSchemaFactory.getInstance(version);
    }

    /**
     * Get the item schema (handles both array wrapper and direct object schema)
     */
    private static JsonSchema getItemSchema(JsonNode schemaNode, JsonSchemaFactory factory, SchemaValidatorsConfig config) {
        JsonNode itemsNode = schemaNode.get("items");
        if (itemsNode != null) {
            return factory.getSchema(itemsNode, config);
        }
        return factory.getSchema(schemaNode, config);
    }

    /**
     * Load CSV with JSON Schema validation - filters out invalid rows
     * Returns only valid data as DataFrame
     */
    public static LoadResult loadCsvWithJsonSchema(String csvPath, String schemaPath) throws IOException {
        return loadCsvWithJsonSchema(csvPath, schemaPath, new LoadOptions());
    }

    /**
     * Load CSV with JSON Schema and custom options - returns LoadResult with valid data and all errors
     */
    public static LoadResult loadCsvWithJsonSchema(String csvPath, String schemaPath, LoadOptions options)
            throws IOException {
        // Parse schema from file
        JsonNode schemaNode = mapper.readTree(Files.readString(Paths.get(schemaPath)));
        return loadCsvWithJsonSchema(csvPath, schemaNode, options);
    }

    /**
     * Load CSV with JSON Schema node directly (for YAML or programmatic schemas)
     */
    public static LoadResult loadCsvWithJsonSchema(String csvPath, JsonNode schemaNode, LoadOptions options)
            throws IOException {

        LoadResult result = new LoadResult();

        // Extract column schemas
        Map<String, ColumnSchema> columnSchemas = extractColumnSchemas(schemaNode);

        if (options.verbose) {
            System.out.println("📋 Detected Column Schemas:");
            columnSchemas.values().forEach(cs -> System.out.println("  " + cs));
            System.out.println();
        }

        // Load CSV first (handle parse errors)
        var loader = Csv.loader();
        if (options.emptyStringAsNull) {
            loader.emptyStringIsNull();
        }

        // Configure column types from schema with safe converters
        // These create ObjectSeries; we'll compact to primitives after loading
        for (Map.Entry<String, ColumnSchema> entry : columnSchemas.entrySet()) {
            String colName = entry.getKey();
            ColumnSchema colSchema = entry.getValue();

            if (colSchema.type == Integer.class) {
                loader.col(colName, s -> {
                    if (s == null || s.trim().isEmpty()) return 0;
                    try {
                        return Integer.parseInt(s.trim());
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                });
            } else if (colSchema.type == Double.class) {
                loader.col(colName, s -> {
                    if (s == null || s.trim().isEmpty()) return 0.0;
                    try {
                        return Double.parseDouble(s.trim());
                    } catch (NumberFormatException e) {
                        return 0.0;
                    }
                });
            } else if (colSchema.type == Long.class) {
                loader.col(colName, s -> {
                    if (s == null || s.trim().isEmpty()) return 0L;
                    try {
                        return Long.parseLong(s.trim());
                    } catch (NumberFormatException e) {
                        return 0L;
                    }
                });
            } else if (colSchema.type == Boolean.class) {
                loader.col(colName, s ->
                        s != null && !s.trim().isEmpty() && Boolean.parseBoolean(s.trim()));
            }
            // String and other types use default parsing
        }

        DataFrame df;
        try {
            df = loader.load(csvPath);
        } catch (Exception e) {
            if (options.verbose) {
                System.out.println("⚠️  Standard CSV load failed, using robust line-by-line parser...");
            }

            List<String> lines = Files.readAllLines(Paths.get(csvPath));
            if (lines.isEmpty()) {
                throw new IOException("CSV file is empty");
            }

            String headerLine = lines.get(0);
            String[] headers = parseCSVLine(headerLine);
            int expectedColumns = headers.length;

            List<String[]> goodRows = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                String[] values = parseCSVLine(line);

                if (values.length == expectedColumns) {
                    goodRows.add(values);
                } else {
                    result.parseErrors.add(new BadRow(i + 1, line,
                            "Column count mismatch: expected " + expectedColumns + ", found " + values.length));
                }
            }

            String tempCsvPath = csvPath + ".tmp";
            try (var writer = Files.newBufferedWriter(Paths.get(tempCsvPath))) {
                writer.write(String.join(",", headers));
                writer.newLine();
                for (String[] row : goodRows) {
                    writer.write(String.join(",", row));
                    writer.newLine();
                }
            }

            // Re-use the typed loader for the cleaned temp file
            df = loader.load(tempCsvPath);
            Files.delete(Paths.get(tempCsvPath));
        }

        if (options.verbose && !result.parseErrors.isEmpty()) {
            System.out.println("⚠️  " + result.parseErrors.size() + " rows failed to parse (malformed CSV)");
        }

        // Compact numeric columns to primitive series for performance
        // Get the Series, compact it with a mapper, then merge it back into the DataFrame
        for (Map.Entry<String, ColumnSchema> entry : columnSchemas.entrySet()) {
            String colName = entry.getKey();
            ColumnSchema colSchema = entry.getValue();

            if (!df.getColumnsIndex().contains(colName)) {
                continue;
            }

            try {
                if (colSchema.type == Integer.class) {
                    var compacted = df.getColumn(colName).compactInt(
                            v -> v == null ? 0 : ((Number) v).intValue()
                    );
                    df = df.cols(colName).merge(compacted);
                } else if (colSchema.type == Double.class) {
                    var compacted = df.getColumn(colName).compactDouble(
                            v -> v == null ? 0.0 : ((Number) v).doubleValue()
                    );
                    df = df.cols(colName).merge(compacted);
                } else if (colSchema.type == Long.class) {
                    var compacted = df.getColumn(colName).compactLong(
                            v -> v == null ? 0L : ((Number) v).longValue()
                    );
                    df = df.cols(colName).merge(compacted);
                } else if (colSchema.type == Boolean.class) {
                    var compacted = df.getColumn(colName).compactBool(
                            v -> v != null && (Boolean) v
                    );
                    df = df.cols(colName).merge(compacted);
                }
            } catch (Exception e) {
                // If compaction fails, keep the column as-is
                if (options.verbose) {
                    System.out.println("⚠️  Could not compact column '" + colName + "': " + e.getMessage());
                }
            }
        }

        // Note: Column names are preserved as-is from CSV to match schema field names
        // The schema should use the exact CSV header names (with spaces if present)

        if (options.verbose) {
            System.out.println("📥 Loaded " + df.height() + " rows from CSV");
        }

        // Now validate each row and filter out invalid ones
        if (options.validateAndFilter) {
            df = validateAndFilterRows(df, schemaNode, columnSchemas, result, options);
        }

        result.validData = df;

        // Store references for backward compatibility
        options.badRows = result.getAllBadRows();

        if (options.verbose) {
            System.out.println("\n✅ Final result: " + df.height() + " valid rows");
            if (result.getTotalBadRows() > 0) {
                System.out.println("❌ Filtered out: " + result.getTotalBadRows() + " bad rows");
                System.out.println("   • Parse errors: " + result.parseErrors.size());
                System.out.println("   • Validation errors: " + result.validationErrors.size());
            }
            System.out.println();
        }

        return result;
    }

    /**
     * Validate each row against schema and filter out invalid rows
     */
    private static DataFrame validateAndFilterRows(DataFrame df, JsonNode schemaNode,
                                                   Map<String, ColumnSchema> columnSchemas,
                                                   LoadResult result, LoadOptions options) {
        if (options.verbose) {
            System.out.println("🔍 Validating rows against schema...");
        }

        // Create schema factory and config
        JsonSchemaFactory factory = createSchemaFactory(schemaNode);
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                .formatAssertionsEnabled(true)
                .build();

        // Get item schema for validating individual rows
        JsonSchema itemSchema = getItemSchema(schemaNode, factory, config);

        List<Integer> validRowIndices = new ArrayList<>();

        // Convert Index to List (Index doesn't have toList())
        List<String> columns = new ArrayList<>();
        for (int i = 0; i < df.width(); i++) {
            columns.add(df.getColumnsIndex().get(i));
        }

        for (int rowIdx = 0; rowIdx < df.height(); rowIdx++) {
            // Convert row to JSON object
            ObjectNode rowJson = mapper.createObjectNode();

            for (String colName : columns) {
                if (!columnSchemas.containsKey(colName)) {
                    continue; // Skip columns not in schema
                }

                Object value = df.getColumn(colName).get(rowIdx);
                ColumnSchema colSchema = columnSchemas.get(colName);

                if (value == null) {
                    rowJson.putNull(colName);
                } else if (colSchema.type == Integer.class) {
                    if (value instanceof Number) {
                        rowJson.put(colName, ((Number) value).intValue());
                    } else {
                        try {
                            rowJson.put(colName, Integer.parseInt(value.toString().trim()));
                        } catch (NumberFormatException e) {
                            rowJson.put(colName, value.toString());
                        }
                    }
                } else if (colSchema.type == Double.class) {
                    if (value instanceof Number) {
                        rowJson.put(colName, ((Number) value).doubleValue());
                    } else {
                        try {
                            rowJson.put(colName, Double.parseDouble(value.toString().trim()));
                        } catch (NumberFormatException e) {
                            rowJson.put(colName, value.toString());
                        }
                    }
                } else if (colSchema.type == Boolean.class) {
                    if (value instanceof Boolean) {
                        rowJson.put(colName, (Boolean) value);
                    } else {
                        rowJson.put(colName, Boolean.parseBoolean(value.toString()));
                    }
                } else {
                    rowJson.put(colName, value.toString());
                }
            }

            // Validate this row
            Set<ValidationMessage> errors = itemSchema.validate(rowJson);

            if (errors.isEmpty()) {
                validRowIndices.add(rowIdx);
            } else {
                // Collect row data for the bad row
                Map<String, Object> rowData = new LinkedHashMap<>();
                for (String colName : columns) {
                    rowData.put(colName, df.getColumn(colName).get(rowIdx));
                }

                String errorMessages = errors.stream()
                        .map(ValidationMessage::getMessage)
                        .collect(Collectors.joining("; "));

                result.validationErrors.add(new BadRow(rowIdx + 2, rowData, errorMessages)); // +2 for 1-based + header
            }
        }

        if (options.verbose) {
            System.out.println("   Valid rows: " + validRowIndices.size());
            System.out.println("   Invalid rows: " + result.validationErrors.size());
        }

        // Return filtered DataFrame with only valid rows
        if (validRowIndices.size() == df.height()) {
            return df; // All rows valid
        }

        int[] indices = validRowIndices.stream().mapToInt(Integer::intValue).toArray();
        return df.rows(indices).select();
    }

    /**
     * Validate DataFrame against JSON Schema (for already-loaded data)
     */
    public static ValidationResult validateDataFrame(DataFrame df, String schemaPath) throws IOException {
        return validateDataFrame(df, schemaPath, new ValidationOptions());
    }

    /**
     * Validate DataFrame with custom options
     */
    public static ValidationResult validateDataFrame(DataFrame df, String schemaPath, ValidationOptions options)
            throws IOException {

        ValidationResult result = new ValidationResult();

        JsonNode schemaNode = mapper.readTree(Files.readString(Paths.get(schemaPath)));
        Map<String, ColumnSchema> columnSchemas = extractColumnSchemas(schemaNode);

        // Check structural issues
        List<String> structuralErrors = new ArrayList<>();

        if (df.width() != columnSchemas.size()) {
            structuralErrors.add("Column count mismatch: CSV has " + df.width() +
                    " columns but schema expects " + columnSchemas.size());
        }

        for (String expectedCol : columnSchemas.keySet()) {
            if (!df.getColumnsIndex().contains(expectedCol)) {
                structuralErrors.add("Missing required column: " + expectedCol);
            }
        }

        for (String actualCol : df.getColumnsIndex()) {
            if (!columnSchemas.containsKey(actualCol)) {
                structuralErrors.add("Unexpected column not in schema: " + actualCol);
            }
        }

        if (!structuralErrors.isEmpty()) {
            result.valid = false;
            result.errors.addAll(structuralErrors);
            result.message = "❌ Structural validation failed with " + structuralErrors.size() + " error(s):";

            if (options.verbose) {
                System.out.println(result.message);
                result.errors.forEach(error -> System.out.println("  • " + error));
            }

            if (options.failFast) {
                throw new ValidationException("Structural validation failed", result.errors);
            }

            return result;
        }

        // Content validation
        JsonSchemaFactory factory = createSchemaFactory(schemaNode);
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                .formatAssertionsEnabled(true)
                .build();

        JsonSchema schema;
        try (InputStream schemaStream = Files.newInputStream(Paths.get(schemaPath))) {
            schema = factory.getSchema(schemaStream, config);
        }

        String jsonStr = Json.saver().saveToString(df);
        JsonNode jsonArray = mapper.readTree(jsonStr);

        Set<ValidationMessage> validationMessages = schema.validate(jsonArray);

        if (validationMessages.isEmpty()) {
            result.valid = true;
            result.message = "✅ Validation passed for all " + df.height() + " records.";
        } else {
            result.valid = false;
            result.errors = validationMessages.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.toList());
            result.message = "❌ Content validation failed with " + validationMessages.size() + " error(s):";
        }

        if (options.verbose) {
            System.out.println(result.message);
            if (!result.valid) {
                result.errors.stream()
                        .limit(20)
                        .forEach(error -> System.out.println("  • " + error));
                if (result.errors.size() > 20) {
                    System.out.println("  ... and " + (result.errors.size() - 20) + " more errors");
                }
            }
        }

        if (options.failFast && !result.valid) {
            throw new ValidationException("Schema validation failed", result.errors);
        }

        return result;
    }

    /**
     * Options for loading CSV
     */
    public static class LoadOptions {
        public boolean verbose = true;
        public boolean emptyStringAsNull = true;
        public boolean validateAndFilter = true;  // Filter out invalid rows by default
        public List<BadRow> badRows = new ArrayList<>();

        public LoadOptions() {
        }

        public LoadOptions verbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        public LoadOptions emptyStringAsNull(boolean emptyStringAsNull) {
            this.emptyStringAsNull = emptyStringAsNull;
            return this;
        }

        public LoadOptions validateAndFilter(boolean validateAndFilter) {
            this.validateAndFilter = validateAndFilter;
            return this;
        }

        public List<BadRow> getBadRows() {
            return badRows;
        }
    }

    /**
     * Options for validation
     */
    public static class ValidationOptions {
        public boolean verbose = true;
        public boolean failFast = false;

        public ValidationOptions() {
        }

        public ValidationOptions verbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        public ValidationOptions failFast(boolean failFast) {
            this.failFast = failFast;
            return this;
        }
    }

    /**
     * Validation result container
     */
    public static class ValidationResult {
        public boolean valid;
        public String message;
        public List<String> errors = new ArrayList<>();

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    /**
     * Custom validation exception
     */
    public static class ValidationException extends RuntimeException {
        private final List<String> errors;

        public ValidationException(String message, List<String> errors) {
            super(message);
            this.errors = errors;
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    /**
     * Print schema information
     */
    public static void printSchemaInfo(String schemaPath) throws IOException {
        JsonNode schemaNode = mapper.readTree(Files.readString(Paths.get(schemaPath)));
        Map<String, ColumnSchema> columnSchemas = extractColumnSchemas(schemaNode);

        System.out.println("JSON Schema Information");
        System.out.println("======================");

        JsonNode schemaVersion = schemaNode.get("$schema");
        if (schemaVersion != null) {
            System.out.println("Schema Version: " + schemaVersion.asText());
        }

        JsonNode title = schemaNode.get("title");
        if (title != null) {
            System.out.println("Title: " + title.asText());
        }

        JsonNode description = schemaNode.get("description");
        if (description != null) {
            System.out.println("Description: " + description.asText());
        }

        System.out.println("\nColumns (" + columnSchemas.size() + "):");
        System.out.println("----------------------------------------");

        for (ColumnSchema cs : columnSchemas.values()) {
            System.out.printf("%-30s %-15s %s%s%s%n",
                    cs.name,
                    cs.type != null ? cs.type.getSimpleName() : "String",
                    cs.required ? "[Required] " : "",
                    cs.nullable ? "[Nullable] " : "",
                    cs.enumValues != null ? "[Enum: " + cs.enumValues.size() + " values]" : ""
            );
        }

        System.out.println();
    }

    /**
     * Save bad rows to a CSV file with error details
     */
    public static void saveBadRows(List<BadRow> badRows, String outputPath) throws IOException {
        if (badRows.isEmpty()) {
            return;
        }

        try (var writer = Files.newBufferedWriter(Paths.get(outputPath))) {
            // Determine columns from first row with data
            BadRow firstWithData = badRows.stream()
                    .filter(br -> br.rowData != null && !br.rowData.isEmpty())
                    .findFirst()
                    .orElse(null);

            if (firstWithData != null) {
                // Write header with original columns + error info (properly escaped)
                List<String> headers = new ArrayList<>(firstWithData.rowData.keySet());
                headers.add("_error_row_number");
                headers.add("_error_reason");
                writer.write(headers.stream().map(JsonSchemaDataFrameLoader::escapeCSV).collect(Collectors.joining(",")));
                writer.newLine();

                // Write each bad row
                for (BadRow badRow : badRows) {
                    List<String> values = new ArrayList<>();
                    if (badRow.rowData != null) {
                        for (String col : firstWithData.rowData.keySet()) {
                            Object val = badRow.rowData.get(col);
                            values.add(escapeCSV(val == null ? "" : val.toString()));
                        }
                    } else {
                        // Fill with empty values if no row data
                        for (int i = 0; i < firstWithData.rowData.size(); i++) {
                            values.add("");
                        }
                    }
                    values.add(String.valueOf(badRow.rowNumber));
                    values.add(escapeCSV(badRow.reason));
                    writer.write(String.join(",", values));
                    writer.newLine();
                }
            } else {
                // Fallback: simple format for parse errors
                writer.write("row_number,reason,raw_line");
                writer.newLine();
                for (BadRow badRow : badRows) {
                    writer.write(badRow.rowNumber + "," +
                            escapeCSV(badRow.reason) + "," +
                            escapeCSV(badRow.rawLine));
                    writer.newLine();
                }
            }
        }
    }

    /**
     * Escape a value for CSV output
     */
    private static String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Save valid rows (DataFrame) to a CSV file
     */
    public static void saveGoodRows(DataFrame df, String outputPath) throws IOException {
        Csv.saver().save(df, outputPath);
    }

    /**
     * Convenience method: Load, validate, filter, and save in one call
     */
    public static LoadResult processAndSave(String inputCsvPath, String schemaPath,
                                            String validOutputPath, String invalidOutputPath) throws IOException {
        return processAndSave(inputCsvPath, schemaPath, validOutputPath, invalidOutputPath, new LoadOptions());
    }

    /**
     * Convenience method: Load, validate, filter, and save with options
     */
    public static LoadResult processAndSave(String inputCsvPath, String schemaPath,
                                            String validOutputPath, String invalidOutputPath,
                                            LoadOptions options) throws IOException {
        // Load and validate
        LoadResult result = loadCsvWithJsonSchema(inputCsvPath, schemaPath, options);

        // Save valid rows
        saveGoodRows(result.validData, validOutputPath);

        // Save invalid rows if any
        if (result.getTotalBadRows() > 0) {
            saveBadRows(result.getAllBadRows(), invalidOutputPath);
        }

        return result;
    }

    /**
     * Main method with example
     */
    public static void main(String[] args) {
        try {
            String csvPath = "trading_orders.csv";
            String schemaPath = "trading_orders_schema.json";

            System.out.println("=".repeat(60));
            System.out.println("JSON Schema DataFrame Loader (NetworkNT - with filtering)");
            System.out.println("=".repeat(60));
            System.out.println();

            printSchemaInfo(schemaPath);

            System.out.println("Loading and validating CSV...");
            System.out.println("-".repeat(60));

            // Process and save in one call
            LoadResult result = processAndSave(
                    csvPath,
                    schemaPath,
                    csvPath.replace(".csv", ".valid.csv"),
                    csvPath.replace(".csv", ".invalid.csv"),
                    new LoadOptions().verbose(true)
            );

            System.out.println("\n" + "=".repeat(60));
            System.out.println("Processing complete!");
            System.out.println("  Valid rows:   " + result.validData.height());
            System.out.println("  Invalid rows: " + result.getTotalBadRows());
            System.out.println("=".repeat(60));

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}