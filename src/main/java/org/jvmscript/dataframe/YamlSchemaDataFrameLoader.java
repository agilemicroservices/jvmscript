package org.jvmscript.dataframe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.dflib.DataFrame;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class YamlSchemaDataFrameLoader {

    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /**
     * Load CSV using a YAML schema file
     */
    public static LoadResult loadCsvWithYamlSchema(
            String csvPath, String yamlSchemaPath) throws IOException {
            var options = new JsonSchemaDataFrameLoader.LoadOptions();
            options.verbose(false);
        return loadCsvWithYamlSchema(csvPath, yamlSchemaPath, options);
    }

    /**
     * Load CSV using a YAML schema file with options
     */
    public static LoadResult loadCsvWithYamlSchema(
            String csvPath, String yamlSchemaPath,
            JsonSchemaDataFrameLoader.LoadOptions options) throws IOException {

        // Convert YAML to JSON schema node
        JsonNode schemaNode = loadYamlAsJsonNode(yamlSchemaPath);

        // Use the JSON loader with the converted schema
        return JsonSchemaDataFrameLoader.loadCsvWithJsonSchema(csvPath, schemaNode, options);
    }

    /**
     * Re-validate an already-typed DataFrame against a YAML schema, row by row (no CSV parse).
     * See {@link JsonSchemaDataFrameLoader#revalidateDataFrame}.
     */
    public static LoadResult revalidateDataFrameWithYamlSchema(
            DataFrame df, String yamlSchemaPath) throws IOException {
        return JsonSchemaDataFrameLoader.revalidateDataFrame(df, loadYamlAsJsonNode(yamlSchemaPath));
    }

    public static LoadResult revalidateDataFrameWithYamlSchema(
            DataFrame df, String yamlSchemaPath,
            JsonSchemaDataFrameLoader.LoadOptions options) throws IOException {
        return JsonSchemaDataFrameLoader.revalidateDataFrame(df, loadYamlAsJsonNode(yamlSchemaPath), options);
    }

    /**
     * Load CSV using a YAML schema from InputStream
     */
    public static LoadResult loadCsvWithYamlSchema(
            String csvPath, InputStream yamlSchemaStream) throws IOException {
        return loadCsvWithYamlSchema(csvPath, yamlSchemaStream, new JsonSchemaDataFrameLoader.LoadOptions());
    }

    /**
     * Load CSV using a YAML schema from InputStream with options
     */
    public static LoadResult loadCsvWithYamlSchema(
            String csvPath, InputStream yamlSchemaStream,
            JsonSchemaDataFrameLoader.LoadOptions options) throws IOException {

        JsonNode schemaNode = yamlMapper.readTree(yamlSchemaStream);
        return JsonSchemaDataFrameLoader.loadCsvWithJsonSchema(csvPath, schemaNode, options);
    }

    /**
     * Auto-detect schema format by file extension and load
     * Supports: .yaml, .yml, .json
     */
    public static LoadResult loadCsv(
            String csvPath, String schemaPath) throws IOException {
        return loadCsv(csvPath, schemaPath, new JsonSchemaDataFrameLoader.LoadOptions());
    }

    /**
     * Auto-detect schema format by file extension and load with options
     */
    public static LoadResult loadCsv(
            String csvPath, String schemaPath,
            JsonSchemaDataFrameLoader.LoadOptions options) throws IOException {

        String lowerPath = schemaPath.toLowerCase();

        if (lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml")) {
            return loadCsvWithYamlSchema(csvPath, schemaPath, options);
        } else if (lowerPath.endsWith(".json")) {
            return JsonSchemaDataFrameLoader.loadCsvWithJsonSchema(csvPath, schemaPath, options);
        } else {
            // Try to detect by content
            String content = Files.readString(Paths.get(schemaPath)).trim();
            if (content.startsWith("{")) {
                return JsonSchemaDataFrameLoader.loadCsvWithJsonSchema(csvPath, schemaPath, options);
            } else {
                return loadCsvWithYamlSchema(csvPath, schemaPath, options);
            }
        }
    }

    /**
     * Load a YAML file and convert to JsonNode
     */
    public static JsonNode loadYamlAsJsonNode(String yamlPath) throws IOException {
        return yamlMapper.readTree(Files.readString(Paths.get(yamlPath)));
    }

    /**
     * Load a YAML InputStream and convert to JsonNode
     */
    public static JsonNode loadYamlAsJsonNode(InputStream yamlStream) throws IOException {
        return yamlMapper.readTree(yamlStream);
    }

    /**
     * Convert YAML string to JsonNode
     */
    public static JsonNode parseYaml(String yamlContent) throws IOException {
        return yamlMapper.readTree(yamlContent);
    }

    /**
     * Convert YAML schema to JSON string (for debugging or compatibility)
     */
    public static String yamlToJson(String yamlPath) throws IOException {
        JsonNode node = loadYamlAsJsonNode(yamlPath);
        return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }

    /**
     * Validate a YAML schema file (check syntax and structure)
     */
    public static JsonSchemaDataFrameLoader.ValidationResult validateYamlSchema(
            String yamlSchemaPath) throws IOException {

        JsonSchemaDataFrameLoader.ValidationResult result = new JsonSchemaDataFrameLoader.ValidationResult();

        try {
            JsonNode schemaNode = loadYamlAsJsonNode(yamlSchemaPath);

            // Basic structure checks
            if (!schemaNode.has("type") && !schemaNode.has("properties") && !schemaNode.has("items")) {
                result.valid = false;
                result.message = "Schema must have 'type', 'properties', or 'items'";
                result.errors.add(result.message);
                return result;
            }

            // Try to create a JsonSchema to validate the schema itself
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            factory.getSchema(schemaNode);

            result.valid = true;
            result.message = "Valid YAML schema";

        } catch (Exception e) {
            result.valid = false;
            result.message = "Invalid YAML schema: " + e.getMessage();
            result.errors.add(e.getMessage());
        }

        return result;
    }

    /**
     * Print schema information from YAML file
     */
    public static void printSchemaInfo(String yamlSchemaPath) throws IOException {
        JsonNode schemaNode = loadYamlAsJsonNode(yamlSchemaPath);

        System.out.println("YAML Schema Information");
        System.out.println("=======================");

        if (schemaNode.has("$schema")) {
            System.out.println("Schema Version: " + schemaNode.get("$schema").asText());
        }

        if (schemaNode.has("title")) {
            System.out.println("Title: " + schemaNode.get("title").asText());
        }

        if (schemaNode.has("description")) {
            System.out.println("Description: " + schemaNode.get("description").asText());
        }

        // Get properties node (may be nested under items for array schemas)
        JsonNode propsNode = schemaNode.has("items")
                ? schemaNode.get("items").get("properties")
                : schemaNode.get("properties");

        if (propsNode != null) {
            System.out.println("\nFields (" + propsNode.size() + "):");
            System.out.println("-".repeat(60));

            propsNode.fields().forEachRemaining(field -> {
                String name = field.getKey();
                JsonNode prop = field.getValue();

                String type = prop.has("type") ? prop.get("type").toString() : "any";
                boolean hasEnum = prop.has("enum");
                String desc = prop.has("description")
                        ? prop.get("description").asText().split("\n")[0]  // First line only
                        : "";

                // Truncate description
                if (desc.length() > 40) {
                    desc = desc.substring(0, 37) + "...";
                }

                System.out.printf("  %-25s %-15s %s%n",
                        name,
                        type.replace("\"", ""),
                        hasEnum ? "[enum] " + desc : desc);
            });
        }

        // Show conditional rules if present
        JsonNode allOf = schemaNode.has("items")
                ? schemaNode.get("items").get("allOf")
                : schemaNode.get("allOf");

        if (allOf != null && allOf.isArray()) {
            System.out.println("\nConditional Rules (" + allOf.size() + "):");
            System.out.println("-".repeat(60));

            for (JsonNode rule : allOf) {
                if (rule.has("if") && rule.has("then")) {
                    JsonNode ifNode = rule.get("if");
                    JsonNode thenNode = rule.get("then");

                    String condition = "";
                    if (ifNode.has("properties")) {
                        JsonNode props = ifNode.get("properties");
                        if (props.fields().hasNext()) {
                            var entry = props.fields().next();
                            String fieldName = entry.getKey();
                            JsonNode fieldConstraint = entry.getValue();
                            if (fieldConstraint.has("const")) {
                                condition = fieldName + " = " + fieldConstraint.get("const").asText();
                            }
                        }
                    }

                    String required = "";
                    if (thenNode.has("required") && thenNode.get("required").isArray()) {
                        StringBuilder sb = new StringBuilder();
                        for (JsonNode req : thenNode.get("required")) {
                            if (sb.length() > 0) sb.append(", ");
                            sb.append(req.asText());
                        }
                        required = sb.toString();
                    }

                    if (!condition.isEmpty() && !required.isEmpty()) {
                        System.out.printf("  IF %s THEN require: %s%n", condition, required);
                    }
                }
            }
        }

        System.out.println();
    }

    /**
     * Process and save with auto-detection of schema format
     */
    public static LoadResult processAndSave(
            String inputCsvPath, String schemaPath,
            String validOutputPath, String invalidOutputPath) throws IOException {
        return processAndSave(inputCsvPath, schemaPath, validOutputPath, invalidOutputPath,
                new JsonSchemaDataFrameLoader.LoadOptions());
    }

    /**
     * Process and save with options
     */
    public static LoadResult processAndSave(
            String inputCsvPath, String schemaPath,
            String validOutputPath, String invalidOutputPath,
            JsonSchemaDataFrameLoader.LoadOptions options) throws IOException {

        // Load and validate
        LoadResult result = loadCsv(inputCsvPath, schemaPath, options);

        // Save valid rows
        JsonSchemaDataFrameLoader.saveGoodRows(result.validData, validOutputPath);

        // Save invalid rows if any
        if (result.getTotalBadRows() > 0) {
            JsonSchemaDataFrameLoader.saveBadRows(result.getAllBadRows(), invalidOutputPath);
        }

        return result;
    }

    /**
     * Main method with example
     */
    public static void main(String[] args) {
        try {
            if (args.length < 2) {
                System.out.println("Usage: YamlSchemaDataFrameLoader <csv-file> <schema-file>");
                System.out.println("  Schema can be .yaml, .yml, or .json");
                return;
            }

            String csvPath = args[0];
            String schemaPath = args[1];

            System.out.println("=".repeat(60));
            System.out.println("YAML/JSON Schema DataFrame Loader");
            System.out.println("=".repeat(60));
            System.out.println();

            // Print schema info
            if (schemaPath.toLowerCase().endsWith(".yaml") || schemaPath.toLowerCase().endsWith(".yml")) {
                printSchemaInfo(schemaPath);
            } else {
                JsonSchemaDataFrameLoader.printSchemaInfo(schemaPath);
            }

            System.out.println("Loading and validating CSV...");
            System.out.println("-".repeat(60));

            // Process with auto-detection
            LoadResult result = processAndSave(
                    csvPath,
                    schemaPath,
                    csvPath.replace(".csv", ".valid.csv"),
                    csvPath.replace(".csv", ".invalid.csv"),
                    new JsonSchemaDataFrameLoader.LoadOptions().verbose(true)
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
