package org.jvmscript.dataframe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRecord;
import de.siegmar.fastcsv.reader.FieldMismatchStrategy;
import org.dflib.DataFrame;
import org.dflib.row.RowProxy;
import org.dflib.csv.Csv;
import org.dflib.json.Json;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Loads CSV data into a DFLib DataFrame using a JSON Schema for typing and validation.
 *
 * <p>Design: <b>validate-then-coerce</b>. The CSV is parsed (FastCSV) into raw string
 * records; each record is validated against the schema using the <i>raw</i> values, so
 * malformed input fails validation instead of being silently coerced to {@code 0}/{@code null};
 * only rows that pass are coerced into the typed DataFrame returned as {@code validData}.
 *
 * <p>Features:
 * <ul>
 *   <li>Full Draft 2020-12 schema support via NetworkNT (incl. {@code dependentRequired},
 *       {@code oneOf}, {@code if/then}).</li>
 *   <li>Parse errors (ragged rows, structural CSV failures) and validation errors are reported
 *       separately, each with the true physical line number.</li>
 *   <li>{@code additionalProperties: false} is enforced — unknown CSV columns are surfaced.</li>
 *   <li>{@code number} → {@code BigDecimal} (financial precision); optional, gated thousands-separator
 *       handling (a value must be <i>properly grouped</i> to be accepted).</li>
 *   <li>UTF-8 BOM handled natively by the parser.</li>
 *   <li>Optional date conversion: a column with {@code format: date}/{@code date-time} or a custom
 *       {@code x-date-format: <pattern>} is parsed for real, catching impossible dates (e.g. Feb 30)
 *       that a regex {@code pattern} cannot.</li>
 * </ul>
 */
public class JsonSchemaDataFrameLoader {

    private static final ObjectMapper mapper = new ObjectMapper();

    // A plain decimal, optionally signed, with optional fraction and exponent.
    private static final Pattern PLAIN_NUMBER =
            Pattern.compile("^[+-]?\\d+(\\.\\d+)?([eE][+-]?\\d+)?$");
    // A properly grouped decimal: 1,234 / 1,234,567.89  (rejects 1,2,3 and the decimal-comma 1,5).
    private static final Pattern GROUPED_NUMBER =
            Pattern.compile("^[+-]?\\d{1,3}(,\\d{3})+(\\.\\d+)?$");

    /** Schema metadata for a single column. */
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
        // Non-null when this column is parsed as a real date/datetime (catches impossible dates).
        DateTimeFormatter dateFormatter;
        boolean dateTime; // true => LocalDateTime, false => LocalDate (when dateFormatter != null)

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

    /** A parsed CSV record with its true physical starting line number. */
    private record ParsedRow(long lineNumber, List<String> values) {}

    // ---------------------------------------------------------------------
    // Type mapping
    // ---------------------------------------------------------------------

    private static Class<?> mapJsonSchemaType(JsonNode typeNode, JsonNode formatNode) {
        if (typeNode == null) {
            return String.class;
        }
        if (typeNode.isArray()) {
            for (JsonNode type : typeNode) {
                String typeStr = type.asText();
                if (!"null".equals(typeStr)) {
                    return mapSingleType(typeStr, formatNode);
                }
            }
            return String.class;
        }
        return mapSingleType(typeNode.asText(), formatNode);
    }

    private static Class<?> mapSingleType(String type, JsonNode formatNode) {
        switch (type) {
            case "integer":
                if (formatNode != null && "int64".equals(formatNode.asText())) {
                    return Long.class;
                }
                return Integer.class;
            case "number":
                return BigDecimal.class;
            case "boolean":
                return Boolean.class;
            case "string":
                if (formatNode != null) {
                    switch (formatNode.asText()) {
                        case "date":      return LocalDate.class;
                        case "date-time": return LocalDateTime.class;
                        default:          return String.class;
                    }
                }
                return String.class;
            case "array":
            case "object":
            default:
                return String.class;
        }
    }

    /** Extract per-column schema metadata. */
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

            colSchema.type = mapJsonSchemaType(typeNode, formatNode);
            if (formatNode != null) {
                colSchema.format = formatNode.asText();
            }
            if (patternNode != null) {
                colSchema.pattern = patternNode.asText();
            }

            // Date handling: a custom x-date-format wins; otherwise honor format: date/date-time.
            JsonNode dateFmtNode = propSchema.get("x-date-format");
            if (dateFmtNode != null && !dateFmtNode.asText().isEmpty()) {
                String fmt = dateFmtNode.asText();
                // STRICT resolution rejects impossible dates (e.g. Feb 30) that SMART silently adjusts.
                // STRICT needs year ('u') not year-of-era ('y'), so translate; optional sections like
                // [.SSS] still parse fine when absent, so timestamps without millis are not false-rejected.
                colSchema.dateFormatter = DateTimeFormatter.ofPattern(toStrictPattern(fmt))
                        .withResolverStyle(ResolverStyle.STRICT);
                colSchema.dateTime = fmt.matches(".*[HhmsSAnN].*");
                colSchema.type = colSchema.dateTime ? LocalDateTime.class : LocalDate.class;
                colSchema.format = fmt;
            } else if (colSchema.type == LocalDate.class) {
                colSchema.dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE;
            } else if (colSchema.type == LocalDateTime.class) {
                colSchema.dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
                colSchema.dateTime = true;
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

    private static JsonSchema getItemSchema(JsonNode schemaNode, JsonSchemaFactory factory,
                                            SchemaValidatorsConfig config) {
        JsonNode itemsNode = schemaNode.get("items");
        if (itemsNode != null) {
            return factory.getSchema(itemsNode, config);
        }
        return factory.getSchema(schemaNode, config);
    }

    // ---------------------------------------------------------------------
    // Public load API (signatures unchanged)
    // ---------------------------------------------------------------------

    public static LoadResult loadCsvWithJsonSchema(String csvPath, String schemaPath) throws IOException {
        return loadCsvWithJsonSchema(csvPath, schemaPath, new LoadOptions());
    }

    public static LoadResult loadCsvWithJsonSchema(String csvPath, String schemaPath, LoadOptions options)
            throws IOException {
        JsonNode schemaNode = mapper.readTree(Files.readString(Paths.get(schemaPath)));
        return loadCsvWithJsonSchema(csvPath, schemaNode, options);
    }

    /**
     * Load CSV against a schema node. Parses with FastCSV, validates each row against the raw
     * values, then coerces only the rows that pass into the typed {@code validData} DataFrame.
     */
    public static LoadResult loadCsvWithJsonSchema(String csvPath, JsonNode schemaNode, LoadOptions options)
            throws IOException {

        LoadResult result = new LoadResult();
        Map<String, ColumnSchema> columnSchemas = extractColumnSchemas(schemaNode);
        List<String> schemaCols = new ArrayList<>(columnSchemas.keySet());

        if (options.verbose) {
            System.out.println("Detected column schemas:");
            columnSchemas.values().forEach(cs -> System.out.println("  " + cs));
        }

        // Phase 1: parse to raw string records (with true line numbers); ragged rows -> parseErrors.
        List<ParsedRow> rows = new ArrayList<>();
        List<String> headers = parseCsv(csvPath, rows, result);
        if (headers == null) {
            result.validData = DataFrame.empty(schemaCols.toArray(new String[0]));
            return result;
        }
        Map<String, Integer> headerIdx = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            headerIdx.putIfAbsent(headers.get(i), i);
        }

        // Phase 2/3: validate each row against raw values; keep good rows for coercion.
        JsonSchemaFactory factory = createSchemaFactory(schemaNode);
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                .formatAssertionsEnabled(true)
                .build();
        JsonSchema itemSchema = getItemSchema(schemaNode, factory, config);

        List<Object[]> validRows = new ArrayList<>();
        for (ParsedRow row : rows) {
            if (!options.validateAndFilter) {
                validRows.add(coerceRow(headerIdx, row.values(), schemaCols, columnSchemas, options));
                continue;
            }

            ObjectNode rowJson = toRowJson(headers, row.values(), columnSchemas, options);
            List<BadRow.FieldError> fieldErrors = new ArrayList<>();

            for (ValidationMessage m : itemSchema.validate(rowJson)) {
                String col = lastToken(m.getInstanceLocation() == null ? null : m.getInstanceLocation().toString());
                String val = col == null ? null : valueOf(headerIdx, row.values(), col);
                String schemaLoc = m.getSchemaLocation() == null ? null : m.getSchemaLocation().toString();
                fieldErrors.add(new BadRow.FieldError(col, val, m.getMessage(), schemaLoc));
            }

            // Semantic date check: regex patterns accept impossible dates (Feb 30); a real parse rejects them.
            for (ColumnSchema cs : columnSchemas.values()) {
                if (cs.dateFormatter == null) {
                    continue;
                }
                String raw = valueOf(headerIdx, row.values(), cs.name);
                if (raw == null || raw.trim().isEmpty()) {
                    continue;
                }
                String t = raw.trim();
                boolean validDate;
                try {
                    // STRICT formatter rejects impossible dates; optional sections (e.g. [.SSS]) parse
                    // fine when absent, so a plain parse is both correct and tolerant of missing millis.
                    if (cs.dateTime) {
                        LocalDateTime.parse(t, cs.dateFormatter);
                    } else {
                        LocalDate.parse(t, cs.dateFormatter);
                    }
                    validDate = true;
                } catch (DateTimeException ex) {
                    validDate = false;
                }
                if (!validDate) {
                    fieldErrors.add(new BadRow.FieldError(cs.name, raw,
                            "Invalid date; expected format " + (cs.format != null ? cs.format : "ISO"), null));
                }
            }

            if (fieldErrors.isEmpty()) {
                validRows.add(coerceRow(headerIdx, row.values(), schemaCols, columnSchemas, options));
            } else {
                result.validationErrors.add(toBadRow(row, headers, fieldErrors));
            }
        }

        result.validData = buildDataFrame(schemaCols, validRows);
        options.badRows = result.getAllBadRows();

        if (options.verbose) {
            System.out.println("Loaded " + result.validData.height() + " valid rows; "
                    + result.parseErrors.size() + " parse errors, "
                    + result.validationErrors.size() + " validation errors.");
        }
        return result;
    }

    // ---------------------------------------------------------------------
    // Positional CSV load (header NAMES ignored) for fixed-position legacy formats
    // ---------------------------------------------------------------------

    /**
     * Load a CSV by POSITION — the header row is consumed but its NAMES are ignored. Returns a DataFrame
     * whose columns are the file's columns in order, labeled {@code "1".."N"} (N = the header field
     * count). Ragged rows are tolerated: shorter rows are null-padded, longer rows truncated to N (so a
     * stray trailing comma is dropped). Blank cells become null; no schema/typing/validation is applied.
     *
     * <p>For legacy fixed-position formats whose headers are unreliable (varying casing/names across
     * files) but whose column ORDER is fixed — a converter maps positions to canonical fields and the
     * canonical re-validation is the enforcement (so the messy legacy header never reaches downstream).
     */
    public static DataFrame loadCsvPositional(String csvPath) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        int n = -1;
        try (CsvReader<CsvRecord> reader = CsvReader.builder()
                .detectBomHeader(true)
                .skipEmptyLines(true)
                .extraFieldStrategy(FieldMismatchStrategy.IGNORE)
                .missingFieldStrategy(FieldMismatchStrategy.IGNORE)
                .ofCsvRecord(Paths.get(csvPath))) {
            boolean header = true;
            for (CsvRecord rec : reader) {
                if (header) {
                    n = rec.getFields().size();
                    header = false;
                    continue;
                }
                rows.add(rec.getFields());
            }
        }
        if (n < 0) {
            return DataFrame.empty();
        }
        String[] labels = new String[n];
        for (int i = 0; i < n; i++) {
            labels[i] = String.valueOf(i + 1); // 1-based position label
        }
        if (rows.isEmpty()) {
            return DataFrame.empty(labels);
        }
        Object[] flat = new Object[rows.size() * n];
        int k = 0;
        for (List<String> r : rows) {
            for (int i = 0; i < n; i++) {
                String v = i < r.size() ? r.get(i) : null;
                flat[k++] = (v == null || v.isEmpty()) ? null : v; // blank -> null; truncate/pad to N
            }
        }
        return DataFrame.foldByRow(labels).of(flat);
    }

    // ---------------------------------------------------------------------
    // Phase 1: parsing
    // ---------------------------------------------------------------------

    /**
     * Parse the CSV into raw records using FastCSV. Returns the header field names, or {@code null}
     * if the file has no records. Rows whose field count differs from the header are routed to
     * {@code result.parseErrors} (with the true line number) and skipped. A structural parse failure
     * (e.g. an unterminated quote) is recorded as a parse error and stops parsing; records read
     * before the failure are retained.
     */
    private static List<String> parseCsv(String csvPath, List<ParsedRow> out, LoadResult result)
            throws IOException {
        List<String> headers = null;
        try (CsvReader<CsvRecord> reader = CsvReader.builder()
                .detectBomHeader(true)
                .skipEmptyLines(true)
                // Don't abort on a ragged row; hand it to us so we detect & report it with its line number.
                .extraFieldStrategy(FieldMismatchStrategy.IGNORE)
                .missingFieldStrategy(FieldMismatchStrategy.IGNORE)
                .ofCsvRecord(Paths.get(csvPath))) {
            for (CsvRecord rec : reader) {
                List<String> fields = rec.getFields();
                if (headers == null) {
                    headers = new ArrayList<>(fields);
                    continue;
                }
                if (fields.size() != headers.size()) {
                    result.parseErrors.add(new BadRow((int) rec.getStartingLineNumber(),
                            String.join(",", fields),
                            "Column count mismatch: expected " + headers.size()
                                    + ", found " + fields.size()));
                    continue;
                }
                out.add(new ParsedRow(rec.getStartingLineNumber(), fields));
            }
        } catch (RuntimeException e) {
            // FastCSV throws an unchecked parse exception on structural failures (e.g. unterminated
            // quote). Record it and keep whatever parsed cleanly before the failure point.
            result.parseErrors.add(new BadRow(-1, "", "CSV parse failed: " + e.getMessage()));
        }
        return headers;
    }

    // ---------------------------------------------------------------------
    // Phase 2: raw-faithful row JSON for validation
    // ---------------------------------------------------------------------

    private static ObjectNode toRowJson(List<String> headers, List<String> values,
                                        Map<String, ColumnSchema> cols, LoadOptions options) {
        ObjectNode o = mapper.createObjectNode();
        for (int i = 0; i < headers.size(); i++) {
            String name = headers.get(i);
            String raw = i < values.size() ? values.get(i) : null;
            ColumnSchema cs = cols.get(name);

            if (cs == null) {
                // Unknown column included verbatim so additionalProperties:false can fire.
                o.put(name, raw);
                continue;
            }

            String t = raw == null ? null : raw.trim();
            if (t == null || t.isEmpty()) {
                // Blank -> omit the key. Every CSV column is physically present, so emitting an explicit
                // null would make 'required'/'dependentRequired' (which test key presence) pass spuriously.
                // Omitting makes those keywords behave correctly; coerceRow still stores null in the frame.
                continue;
            }

            Class<?> type = cs.type;
            if (cs.dateFormatter != null) {
                o.put(name, raw); // validated by pattern (shape) + semantic parse (validity)
            } else if (type == Integer.class) {
                try { o.put(name, Integer.parseInt(t)); }
                catch (NumberFormatException e) { o.put(name, raw); } // raw -> type/enum fails -> row flagged
            } else if (type == Long.class) {
                try { o.put(name, Long.parseLong(t)); }
                catch (NumberFormatException e) { o.put(name, raw); }
            } else if (type == BigDecimal.class) {
                BigDecimal d = parseDecimal(t, options);
                if (d != null) { o.put(name, d); } else { o.put(name, raw); }
            } else if (type == Boolean.class) {
                if (t.equalsIgnoreCase("true") || t.equalsIgnoreCase("false")) {
                    o.put(name, Boolean.parseBoolean(t));
                } else {
                    o.put(name, raw); // not a clean boolean -> let the schema reject it
                }
            } else {
                o.put(name, raw);
            }
        }
        return o;
    }

    // ---------------------------------------------------------------------
    // Phase 4: coerce validated rows into typed values
    // ---------------------------------------------------------------------

    private static Object[] coerceRow(Map<String, Integer> headerIdx, List<String> values,
                                      List<String> schemaCols, Map<String, ColumnSchema> cols,
                                      LoadOptions options) {
        Object[] out = new Object[schemaCols.size()];
        for (int j = 0; j < schemaCols.size(); j++) {
            ColumnSchema cs = cols.get(schemaCols.get(j));
            String raw = valueOf(headerIdx, values, cs.name);
            String t = raw == null ? null : raw.trim();
            if (t == null || t.isEmpty()) {
                out[j] = null;
                continue;
            }
            try {
                Class<?> type = cs.type;
                if (cs.dateFormatter != null) {
                    out[j] = cs.dateTime
                            ? LocalDateTime.parse(t, cs.dateFormatter)
                            : LocalDate.parse(t, cs.dateFormatter);
                } else if (type == Integer.class) {
                    out[j] = Integer.valueOf(t);
                } else if (type == Long.class) {
                    out[j] = Long.valueOf(t);
                } else if (type == BigDecimal.class) {
                    BigDecimal d = parseDecimal(t, options);
                    out[j] = d != null ? d : raw;
                } else if (type == Boolean.class) {
                    out[j] = Boolean.valueOf(t);
                } else {
                    out[j] = raw;
                }
            } catch (Exception ex) {
                // Defensive: validated rows should always coerce; keep the raw value if not.
                out[j] = raw;
            }
        }
        return out;
    }

    /** Parse a decimal, accepting properly grouped thousands separators only when enabled. */
    private static BigDecimal parseDecimal(String s, LoadOptions options) {
        if (PLAIN_NUMBER.matcher(s).matches()) {
            return new BigDecimal(s);
        }
        if (options.allowThousandsSeparators && GROUPED_NUMBER.matcher(s).matches()) {
            return new BigDecimal(s.replace(",", ""));
        }
        return null;
    }

    private static DataFrame buildDataFrame(List<String> cols, List<Object[]> rows) {
        String[] labels = cols.toArray(new String[0]);
        if (rows.isEmpty()) {
            return DataFrame.empty(labels);
        }
        Object[] flat = new Object[rows.size() * labels.length];
        int k = 0;
        for (Object[] r : rows) {
            for (Object v : r) {
                flat[k++] = v;
            }
        }
        return DataFrame.foldByRow(labels).of(flat);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static String valueOf(Map<String, Integer> headerIdx, List<String> values, String name) {
        Integer i = headerIdx.get(name);
        return (i != null && i < values.size()) ? values.get(i) : null;
    }

    /**
     * Translate year-of-era ('y') to year ('u') outside quoted literals so a pattern works under
     * ResolverStyle.STRICT (which requires an era when 'y' is used). Patterns that already use 'u'
     * or include an explicit era are unaffected in practice.
     */
    private static String toStrictPattern(String pattern) {
        StringBuilder sb = new StringBuilder(pattern.length());
        boolean inQuote = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\'') {
                inQuote = !inQuote;
                sb.append(c);
            } else if (!inQuote && c == 'y') {
                sb.append('u');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Extract the failing field name from a JSON instance-location like {@code $.lastPx} or {@code /lastPx}. */
    private static String lastToken(String instanceLocation) {
        if (instanceLocation == null) {
            return null;
        }
        String s = instanceLocation;
        int cut = Math.max(s.lastIndexOf('.'), s.lastIndexOf('/'));
        String token = cut >= 0 ? s.substring(cut + 1) : s;
        int bracket = token.indexOf('[');
        if (bracket >= 0) {
            token = token.substring(0, bracket);
        }
        token = token.trim();
        return (token.isEmpty() || token.equals("$")) ? null : token;
    }

    private static BadRow toBadRow(ParsedRow row, List<String> headers, List<BadRow.FieldError> fieldErrors) {
        Map<String, Object> rowData = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            rowData.put(headers.get(i), i < row.values().size() ? row.values().get(i) : null);
        }
        String reason = fieldErrors.stream().map(BadRow.FieldError::toString).collect(Collectors.joining("; "));
        BadRow br = new BadRow((int) row.lineNumber(), rowData, reason);
        br.rawLine = String.join(",", row.values());
        br.fieldErrors = fieldErrors;
        return br;
    }

    // ---------------------------------------------------------------------
    // Validate an already-loaded DataFrame (separate entry point, unchanged behavior)
    // ---------------------------------------------------------------------

    public static ValidationResult validateDataFrame(DataFrame df, String schemaPath) throws IOException {
        return validateDataFrame(df, schemaPath, new ValidationOptions());
    }

    public static ValidationResult validateDataFrame(DataFrame df, String schemaPath, ValidationOptions options)
            throws IOException {

        ValidationResult result = new ValidationResult();

        JsonNode schemaNode = mapper.readTree(Files.readString(Paths.get(schemaPath)));
        Map<String, ColumnSchema> columnSchemas = extractColumnSchemas(schemaNode);

        List<String> structuralErrors = new ArrayList<>();
        if (df.width() != columnSchemas.size()) {
            structuralErrors.add("Column count mismatch: CSV has " + df.width()
                    + " columns but schema expects " + columnSchemas.size());
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
            result.message = "Structural validation failed with " + structuralErrors.size() + " error(s)";
            if (options.verbose) {
                System.out.println(result.message);
                result.errors.forEach(error -> System.out.println("  - " + error));
            }
            if (options.failFast) {
                throw new ValidationException("Structural validation failed", result.errors);
            }
            return result;
        }

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
            result.message = "Validation passed for all " + df.height() + " records.";
        } else {
            result.valid = false;
            result.errors = validationMessages.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.toList());
            result.message = "Content validation failed with " + validationMessages.size() + " error(s)";
        }

        if (options.verbose) {
            System.out.println(result.message);
            if (!result.valid) {
                result.errors.stream().limit(20).forEach(error -> System.out.println("  - " + error));
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

    // ---------------------------------------------------------------------
    // Re-validate an already-typed DataFrame, PER ROW, against a schema
    // ---------------------------------------------------------------------

    /**
     * Re-validate an already-typed {@link DataFrame} against a schema, <b>row by row</b>, returning a
     * {@link LoadResult} with the same shape as {@link #loadCsvWithJsonSchema}: {@code validData} holds
     * the rows that pass (typed values preserved) and {@code validationErrors} holds a {@link BadRow}
     * per failing row (with field-level detail). {@code parseErrors} is always empty — there is no CSV
     * parse here; the input is an in-memory frame (e.g. a converted/derived frame being re-checked
     * against the canonical schema so any row that is canonical-invalid is quarantined regardless of how
     * the source-format schema was written).
     *
     * <p>Row JSON is built from the frame's <i>typed</i> cell values (not raw text): {@code null} is
     * omitted (so {@code required}/{@code nullable} behave exactly as on load); {@link LocalDate}/
     * {@link LocalDateTime} are formatted back to the schema's date pattern so a {@code string}+pattern
     * date field validates; numbers/integers/booleans map to their JSON node types. Typed temporals are
     * inherently valid dates (a {@link LocalDate} cannot be Feb 30), so the semantic date check only
     * needs to fire for a date column that still holds a raw string.
     */
    public static LoadResult revalidateDataFrame(DataFrame df, JsonNode schemaNode) {
        return revalidateDataFrame(df, schemaNode, new LoadOptions().verbose(false));
    }

    public static LoadResult revalidateDataFrame(DataFrame df, JsonNode schemaNode, LoadOptions options) {
        LoadResult result = new LoadResult();
        Map<String, ColumnSchema> columnSchemas = extractColumnSchemas(schemaNode);
        // validData preserves the INPUT frame's columns (which may be a SUBSET of the schema's — an
        // optional schema column can be absent from the frame entirely), not the schema's, so passing
        // rows keep their exact shape/types and we never read a column the frame doesn't have.
        List<String> dfCols = new ArrayList<>();
        for (String c : df.getColumnsIndex()) {
            dfCols.add(c);
        }

        JsonSchemaFactory factory = createSchemaFactory(schemaNode);
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                .formatAssertionsEnabled(true)
                .build();
        JsonSchema itemSchema = getItemSchema(schemaNode, factory, config);

        List<Object[]> validRows = new ArrayList<>();
        int rowNumber = 0; // 1-based logical index within the frame (no physical CSV line after conversion)
        for (RowProxy row : df) {
            rowNumber++;
            ObjectNode rowJson = rowToJson(df, row, columnSchemas);
            List<BadRow.FieldError> fieldErrors = new ArrayList<>();

            for (ValidationMessage m : itemSchema.validate(rowJson)) {
                String col = lastToken(m.getInstanceLocation() == null ? null : m.getInstanceLocation().toString());
                String val = col == null ? null : stringValue(row.get(col));
                String schemaLoc = m.getSchemaLocation() == null ? null : m.getSchemaLocation().toString();
                fieldErrors.add(new BadRow.FieldError(col, val, m.getMessage(), schemaLoc));
            }

            // Semantic date check: only a date column still holding a raw String could be an impossible
            // date; a typed LocalDate/LocalDateTime is inherently valid and is skipped.
            for (ColumnSchema cs : columnSchemas.values()) {
                if (cs.dateFormatter == null) {
                    continue;
                }
                Object v = row.get(cs.name);
                if (!(v instanceof CharSequence)) {
                    continue;
                }
                String t = v.toString().trim();
                if (t.isEmpty()) {
                    continue;
                }
                try {
                    if (cs.dateTime) {
                        LocalDateTime.parse(t, cs.dateFormatter);
                    } else {
                        LocalDate.parse(t, cs.dateFormatter);
                    }
                } catch (DateTimeException ex) {
                    fieldErrors.add(new BadRow.FieldError(cs.name, t,
                            "Invalid date; expected format " + (cs.format != null ? cs.format : "ISO"), null));
                }
            }

            if (fieldErrors.isEmpty()) {
                Object[] vals = new Object[dfCols.size()];
                for (int j = 0; j < dfCols.size(); j++) {
                    vals[j] = row.get(dfCols.get(j));
                }
                validRows.add(vals);
            } else {
                result.validationErrors.add(dfBadRow(rowNumber, df, row, fieldErrors));
            }
        }

        result.validData = buildDataFrame(dfCols, validRows);
        options.badRows = result.getAllBadRows();

        if (options.verbose) {
            System.out.println("Re-validated " + df.height() + " rows; "
                    + result.validData.height() + " valid, "
                    + result.validationErrors.size() + " invalid.");
        }
        return result;
    }

    /** Build a JSON object for one DataFrame row from its TYPED cell values (mirrors toRowJson, typed-side). */
    private static ObjectNode rowToJson(DataFrame df, RowProxy row, Map<String, ColumnSchema> cols) {
        ObjectNode o = mapper.createObjectNode();
        for (String name : df.getColumnsIndex()) {
            Object v = row.get(name);
            if (v == null) {
                continue; // omit -> required/nullable behave correctly (same as the blank-omit on load)
            }
            ColumnSchema cs = cols.get(name);
            if (cs == null) {
                o.put(name, v.toString()); // unknown column -> additionalProperties:false fires
                continue;
            }
            if (v instanceof LocalDate d) {
                o.put(name, cs.dateFormatter != null ? d.format(cs.dateFormatter) : d.toString());
            } else if (v instanceof LocalDateTime dt) {
                o.put(name, cs.dateFormatter != null ? dt.format(cs.dateFormatter) : dt.toString());
            } else if (v instanceof BigDecimal d) {
                o.put(name, d);
            } else if (v instanceof Integer i) {
                o.put(name, i.intValue());
            } else if (v instanceof Long l) {
                o.put(name, l.longValue());
            } else if (v instanceof Boolean b) {
                o.put(name, b.booleanValue());
            } else if (v instanceof Number n) {
                o.put(name, new BigDecimal(n.toString()));
            } else {
                o.put(name, v.toString());
            }
        }
        return o;
    }

    /** Render a typed cell value for human-readable error reporting. */
    private static String stringValue(Object v) {
        return v == null ? null : v.toString();
    }

    /** Build a BadRow for a failing DataFrame row (logical index + full row data for the quarantine file). */
    private static BadRow dfBadRow(int rowNumber, DataFrame df, RowProxy row, List<BadRow.FieldError> fieldErrors) {
        Map<String, Object> rowData = new LinkedHashMap<>();
        for (String col : df.getColumnsIndex()) {
            rowData.put(col, row.get(col));
        }
        String reason = fieldErrors.stream().map(BadRow.FieldError::toString).collect(Collectors.joining("; "));
        BadRow br = new BadRow(rowNumber, rowData, reason); // (int, Map, String) ctor rebuilds rawLine from values
        br.fieldErrors = fieldErrors;
        return br;
    }

    // ---------------------------------------------------------------------
    // Options / results
    // ---------------------------------------------------------------------

    public static class LoadOptions {
        public boolean verbose = true;
        public boolean emptyStringAsNull = true;
        public boolean validateAndFilter = true;
        /** Accept properly grouped thousands separators in numeric fields (e.g. "1,234.56"). */
        public boolean allowThousandsSeparators = true;
        public List<BadRow> badRows = new ArrayList<>();

        public LoadOptions() {}

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

        public LoadOptions allowThousandsSeparators(boolean allow) {
            this.allowThousandsSeparators = allow;
            return this;
        }

        public List<BadRow> getBadRows() {
            return badRows;
        }
    }

    public static class ValidationOptions {
        public boolean verbose = true;
        public boolean failFast = false;

        public ValidationOptions() {}

        public ValidationOptions verbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        public ValidationOptions failFast(boolean failFast) {
            this.failFast = failFast;
            return this;
        }
    }

    public static class ValidationResult {
        public boolean valid;
        public String message;
        public List<String> errors = new ArrayList<>();

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        public List<String> getErrors() { return errors; }
    }

    public static class ValidationException extends RuntimeException {
        private final List<String> errors;

        public ValidationException(String message, List<String> errors) {
            super(message);
            this.errors = errors;
        }

        public List<String> getErrors() { return errors; }
    }

    // ---------------------------------------------------------------------
    // Schema info / output
    // ---------------------------------------------------------------------

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
                    cs.enumValues != null ? "[Enum: " + cs.enumValues.size() + " values]" : "");
        }
        System.out.println();
    }

    /** Save bad rows to a CSV file with error details. */
    public static void saveBadRows(List<BadRow> badRows, String outputPath) throws IOException {
        if (badRows.isEmpty()) {
            return;
        }
        try (var writer = Files.newBufferedWriter(Paths.get(outputPath))) {
            BadRow firstWithData = badRows.stream()
                    .filter(br -> br.rowData != null && !br.rowData.isEmpty())
                    .findFirst()
                    .orElse(null);

            if (firstWithData != null) {
                List<String> headers = new ArrayList<>(firstWithData.rowData.keySet());
                headers.add("_error_row_number");
                headers.add("_error_reason");
                writer.write(headers.stream().map(JsonSchemaDataFrameLoader::escapeCSV)
                        .collect(Collectors.joining(",")));
                writer.newLine();

                for (BadRow badRow : badRows) {
                    List<String> values = new ArrayList<>();
                    if (badRow.rowData != null) {
                        for (String col : firstWithData.rowData.keySet()) {
                            Object val = badRow.rowData.get(col);
                            values.add(escapeCSV(val == null ? "" : val.toString()));
                        }
                    } else {
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
                writer.write("row_number,reason,raw_line");
                writer.newLine();
                for (BadRow badRow : badRows) {
                    writer.write(badRow.rowNumber + ","
                            + escapeCSV(badRow.reason) + ","
                            + escapeCSV(badRow.rawLine));
                    writer.newLine();
                }
            }
        }
    }

    private static String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public static void saveGoodRows(DataFrame df, String outputPath) throws IOException {
        Csv.saver().save(df, outputPath);
    }

    public static LoadResult processAndSave(String inputCsvPath, String schemaPath,
                                            String validOutputPath, String invalidOutputPath) throws IOException {
        return processAndSave(inputCsvPath, schemaPath, validOutputPath, invalidOutputPath, new LoadOptions());
    }

    public static LoadResult processAndSave(String inputCsvPath, String schemaPath,
                                            String validOutputPath, String invalidOutputPath,
                                            LoadOptions options) throws IOException {
        LoadResult result = loadCsvWithJsonSchema(inputCsvPath, schemaPath, options);
        saveGoodRows(result.validData, validOutputPath);
        if (result.getTotalBadRows() > 0) {
            saveBadRows(result.getAllBadRows(), invalidOutputPath);
        }
        return result;
    }
}
