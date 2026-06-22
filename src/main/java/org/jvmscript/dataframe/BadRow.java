package org.jvmscript.dataframe;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BadRow {
    public int rowNumber;
    public String rawLine;
    public String reason;
    public Map<String, Object> rowData;

    /**
     * Structured, per-field detail of why the row was rejected. Null for parse-level
     * errors (e.g. column-count mismatch); populated for schema validation errors so
     * ops can see column + offending value + rule rather than one opaque string.
     */
    public List<FieldError> fieldErrors;

    public BadRow(int rowNumber, String rawLine, String reason) {
        this.rowNumber = rowNumber;
        this.rawLine = rawLine;
        this.reason = reason;
    }

    public BadRow(int rowNumber, Map<String, Object> rowData, String reason) {
        this.rowNumber = rowNumber;
        this.rowData = rowData;
        this.reason = reason;
        // Reconstruct raw line from data
        this.rawLine = rowData.values().stream()
                .map(v -> v == null ? "" : v.toString())
                .collect(Collectors.joining(","));
    }

    @Override
    public String toString() {
        return String.format("Row %d: %s", rowNumber, reason);
    }

    /**
     * A single field-level validation failure: which column, the raw value that failed,
     * the human-readable message, and the schema location of the violated rule.
     */
    public static class FieldError {
        public final String column;
        public final String value;
        public final String message;
        public final String schemaPath;

        public FieldError(String column, String value, String message, String schemaPath) {
            this.column = column;
            this.value = value;
            this.message = message;
            this.schemaPath = schemaPath;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (column != null) {
                sb.append(column);
                if (value != null) {
                    sb.append("='").append(value).append('\'');
                }
                sb.append(": ");
            }
            sb.append(message);
            return sb.toString();
        }
    }
}
