package org.jvmscript.dataframe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dflib.DataFrame;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

import static org.jvmscript.cli.CliUtility.*;

public class CsvLoader {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        cliUtilityInitialize();
        cliAddOption("yaml", true, true);
        cliAddOption("csv", true, true);
        cliAddOption("table", true, true);
        cliAddOption("db-url", true, true);
        cliAddOption("db-user", true, true);
        cliAddOption("db-password", true, true);
        cliAddOption("batch-size", true, false);
        cliParse(args);

        String yamlPath = cliGetOptionValue("yaml");
        String csvPath = cliGetOptionValue("csv");
        String fullTableName = cliGetOptionValue("table");
        String dbUrl = cliGetOptionValue("db-url");
        String dbUser = cliGetOptionValue("db-user");
        String dbPassword = cliGetOptionValue("db-password");
        int batchSize = Integer.parseInt(cliGetOptionValue("batch-size", "1000"));

        LoadResult result = YamlSchemaDataFrameLoader.loadCsvWithYamlSchema(csvPath, yamlPath);
        DataFrame df = result.validData;

        System.out.println("Loaded " + df.height() + " valid rows, " + result.getTotalBadRows() + " bad rows");

        String sql = "INSERT INTO " + fullTableName + " (json_data) VALUES (?::jsonb)";

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int row = 0; row < df.height(); row++) {
                    ObjectNode json = mapper.createObjectNode();
                    for (int col = 0; col < df.width(); col++) {
                        String colName = df.getColumnsIndex().get(col);
                        Object value = df.getColumn(colName).get(row);
                        if (value == null) {
                            json.putNull(colName);
                        } else if (value instanceof Number) {
                            json.put(colName, ((Number) value).doubleValue());
                        } else if (value instanceof Boolean) {
                            json.put(colName, (Boolean) value);
                        } else {
                            json.put(colName, value.toString());
                        }
                    }

                    ps.setString(1, mapper.writeValueAsString(json));
                    ps.addBatch();

                    if ((row + 1) % batchSize == 0) {
                        ps.executeBatch();
                        System.out.println("Inserted " + (row + 1) + " rows...");
                    }
                }
                ps.executeBatch();
            }

            conn.commit();
            System.out.println("Inserted " + df.height() + " rows into " + fullTableName);
        }
    }
}