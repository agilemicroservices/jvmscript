package org.jvmscript.dataframe;

import org.dflib.DataFrame;

public class DataFrameUtility {

    public static LoadResult loadCsvWithJsonSchema(String inputCsv, String schemaPath) throws Exception{
        return JsonSchemaDataFrameLoader.loadCsvWithJsonSchema(inputCsv, schemaPath);
    }

    public static LoadResult loadCsvWithJsonSchemaDebug(String inputCsv, String schemaPath) throws Exception{
        return JsonSchemaDataFrameLoader.loadCsvWithJsonSchema(inputCsv, schemaPath, new JsonSchemaDataFrameLoader.LoadOptions().verbose(true));
    }

    public static LoadResult loadCsvWithYamlSchema(String inputCsv, String schemaPath) throws Exception{
        return YamlSchemaDataFrameLoader.loadCsvWithYamlSchema(inputCsv, schemaPath);
    }

    public static LoadResult loadCsvWithYamlSchemaDebug(String inputCsv, String schemaPath) throws Exception{
        return YamlSchemaDataFrameLoader.loadCsvWithYamlSchema(inputCsv, schemaPath, new JsonSchemaDataFrameLoader.LoadOptions().verbose(true));
    }

    /**
     * Re-validate an already-typed DataFrame against a YAML schema, row by row (no CSV parse).
     * validData = passing rows (typed values preserved); validationErrors = a BadRow per failing row.
     */
    public static LoadResult revalidateDataFrameWithYamlSchema(DataFrame df, String schemaPath) throws Exception {
        return YamlSchemaDataFrameLoader.revalidateDataFrameWithYamlSchema(df, schemaPath);
    }

    /**
     * Load a CSV by POSITION (header names ignored) into a DataFrame with columns labeled "1".."N",
     * ragged-tolerant. For fixed-position legacy formats whose headers are unreliable; a converter maps
     * positions to canonical and the canonical re-validation is the gate. See
     * {@link JsonSchemaDataFrameLoader#loadCsvPositional}.
     */
    public static DataFrame loadCsvPositional(String inputCsv) throws Exception {
        return JsonSchemaDataFrameLoader.loadCsvPositional(inputCsv);
    }

    public static void saveBadRows(LoadResult result, String outputPath) throws Exception {
        var badRows = result.getAllBadRows();
        JsonSchemaDataFrameLoader.saveBadRows(badRows, outputPath);
    }

    public static void main() throws Exception {
        String inputCsv = "/opt/data/trafix/testEodFile.csv";
        String badRowsCsv = "/opt/data/trafix/testEodFile.bad.csv";
        String schemaPath = "/opt/data/trafix/vision-execution.schema.yaml";

//        var result = loadCsvWithJsonSchema(inputCsv, schemaPath);
        var result = loadCsvWithYamlSchema(inputCsv, schemaPath);
        saveBadRows(result, badRowsCsv);
    }
}
