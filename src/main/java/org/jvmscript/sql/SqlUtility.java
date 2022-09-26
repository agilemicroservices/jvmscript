package org.jvmscript.sql;

import com.univocity.parsers.csv.CsvRoutines;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import com.univocity.parsers.tsv.TsvWriterSettings;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.sql2o.Connection;
import org.sql2o.Query;
import org.sql2o.Sql2o;
import org.sql2o.data.Column;
import org.sql2o.data.Row;
import org.sql2o.data.Table;

import java.io.*;
import java.sql.ResultSet;
import java.util.*;


// sql syntax for parameters
// where field1 = :p1 AND field2 = :p2
public class SqlUtility {

    private static Sql2o sql2o;
    private static Connection connection;
    public static char delimiter = '|';
    public static boolean header = true;
    public static boolean quoteAllFields = false;

    public static String nullValue = "";

    public static void openSqlConnection() throws IOException {
        openSqlConnection("application.properties");
    }

    public static void openSqlConnection(String propertyFile) throws IOException {
        Properties properties = new Properties();
        InputStream inputStream = SqlUtility.class.getResourceAsStream("/" + propertyFile);
        properties.load(inputStream);

        String dbUrl = properties.getProperty("db.url");
        String user = properties.getProperty("db.user");
        String password = properties.getProperty("db.password");

        openSqlConnection(dbUrl,user,password);
    }

    public static void openSqlConnection(String dbUrl, String user, String password) {
        sql2o = new Sql2o(dbUrl, user, password);
        connection = sql2o.open();
    }

    public static void sqlSetAutoCommit(boolean flag) throws Exception {
        connection.getJdbcConnection().setAutoCommit(flag);
    }

    public static void addCustomColumnMapping(HashMap<String,String> columnMap) {
        sql2o.setDefaultColumnMappings(columnMap);
    }

    public static void closeSqlConnection() {
        connection.close();
    }

    public static void exportSqlFileQueryToFile(String sqlQueryFilename, String outputFilename, Object... params) throws Exception {
        String sqlQuery = FileUtils.readFileToString(new File(sqlQueryFilename));
        exportSqlQueryToFile(outputFilename, sqlQuery, params);
    }

    public static void exportSqlFileQueryToExcel(String sqlQueryFilename, String outputFilename, Object... params) throws Exception {
        String sqlQuery = FileUtils.readFileToString(new File(sqlQueryFilename));
        exportSqlQueryToExcel(outputFilename, sqlQuery, params);
    }

    public static ArrayList<ArrayList<Object>> genericSqlFileQuery(String sqlQueryFilename, Object... params) throws Exception {
        String sqlQuery = FileUtils.readFileToString(new File(sqlQueryFilename));
        return genericSqlQuery(sqlQuery, params);
    }

    public static ArrayList<ArrayList<Object>> genericSqlQuery(String sqlString, Object... params) {
        Query query = connection.createQueryWithParams(sqlString, params);
        Table table = query.executeAndFetchTable();

        ArrayList<ArrayList<Object>> results = new ArrayList<>();
        ArrayList<Object> resultRow = new ArrayList<>();

        for(Column column : table.columns()) {
            resultRow.add(column.getName());
        }

        results.add(resultRow);

        for (Row row : table.rows()) {
            resultRow = new ArrayList<>();

            Collection<Object> objectArray = row.asMap().values();
            for (Object object : objectArray) {
                resultRow.add(object);
            }
            results.add(resultRow);
        }

        return results;
    }

    public static void exportSqlQueryToFile(String filename, String sqlString, Object... params) throws Exception {

        ArrayList<ArrayList<Object>> exportList = genericSqlQuery(sqlString, params);

        FileWriter fileWriter = new FileWriter(filename);
        CsvWriterSettings settings = new CsvWriterSettings();
        settings.getFormat().setDelimiter(delimiter);
        settings.setHeaderWritingEnabled(header);
        settings.setQuoteAllFields(quoteAllFields);
        settings.setNullValue(nullValue);

        CsvWriter csvWriter = new CsvWriter(fileWriter, settings);

        if (header) {
            csvWriter.writeRow(exportList.get(0));
        }

        for (int rowCount = 1; rowCount < exportList.size(); rowCount++) {
            csvWriter.writeRow(exportList.get(rowCount));
        }

        csvWriter.close();
        fileWriter.close();
    }

    public static void sqlExportResultSetToFile(String filename, ResultSet resultSet) {
        CsvWriterSettings settings = new CsvWriterSettings();
        settings.getFormat().setDelimiter(delimiter);
        settings.setHeaderWritingEnabled(header);
        settings.setQuoteAllFields(quoteAllFields);

        var file = new File(filename);
        CsvRoutines csvRoutines = new CsvRoutines(settings);
        csvRoutines.write(resultSet, file);
    }

    public static void exportSqlQueryToExcel(String filename, String sqlString, Object... params) throws Exception {

        ArrayList<ArrayList<Object>> exportList = genericSqlQuery(sqlString, params);

        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet spreadsheet = workbook.createSheet("Default");
        XSSFRow spreadhSheetRow;

        int rowId = 0;
        int cellId = 0;

        if (header) {
            spreadhSheetRow = spreadsheet.createRow(rowId++);

            for(Object object : exportList.get(0)) {
                Cell cell = spreadhSheetRow.createCell(cellId++);
                cell.setCellValue(object.toString());
            }
        }

        for (int rowCount = 1; rowCount < exportList.size(); rowCount++) {
            cellId = 0;
            spreadhSheetRow = spreadsheet.createRow(rowCount);

            for(Object object : exportList.get(rowCount)) {
                Cell cell = spreadhSheetRow.createCell(cellId++);
                if (object != null) {
                    cell.setCellValue(object.toString());
                }
            }
        }

        FileOutputStream fileOutputStream = new FileOutputStream(new File(filename));
        workbook.write(fileOutputStream);
        fileOutputStream.close();
    }

    public static <T> List<T> executeSqlFileToList(String sqlQueryFilename, Class<T> clazz, Object... params) throws IOException{
        String sqlQuery = FileUtils.readFileToString(new File(sqlQueryFilename));
        return executeSqlToList(sqlQuery, clazz, params);
    }

    public static <T> List<T> executeSqlToList(String sql, Class<T> clazz, Object... params) {
        Query query =  connection.createQueryWithParams(sql, params);
        return query.executeAndFetch(clazz);
    }

    public static <T> T sqlFindOne(String sql, Class<T> clazz, Object... params) {
        Query query =  connection.createQueryWithParams(sql, params);
        return query.executeAndFetchFirst(clazz);
    }

    public static <T> T sqlExecuteScalar(String sql, Class<T> clazz, Object... params) {
        Query query =  connection.createQueryWithParams(sql, params);
        return query.executeScalar(clazz);
    }

    public static void executeSql(String sqlString, Object... params) throws Exception {
        Query query = connection.createQueryWithParams(sqlString, params);
        query.executeUpdate();
    }

    public static void executeSqlFile(String sqlFilename, Object... params) throws Exception {
        String sqlQuery = FileUtils.readFileToString(new File(sqlFilename));
        executeSql(sqlQuery, params);
    }
}