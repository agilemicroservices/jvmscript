package org.jvmscript.dataframe;

import java.util.Map;
import java.util.stream.Collectors;

public class BadRow {
    public int rowNumber;
    public String rawLine;
    public String reason;
    public Map<String, Object> rowData;

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
}