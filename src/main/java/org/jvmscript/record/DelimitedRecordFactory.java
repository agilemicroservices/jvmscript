package org.jvmscript.record;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

public class DelimitedRecordFactory extends RecordFactory {

    private static final Logger logger = LogManager.getLogger(DelimitedRecordFactory.class);

    public Character delimiterChar = ',';
    public Character quoteChar = '"';
    public String lineSeparator = "\r\n";

    public boolean roundDecimals = false;

    protected static TreeMap<String, TreeMap> IdCachedFieldsByClassMap = new TreeMap<String, TreeMap>();
    protected static TreeMap<String, TreeMap> NameCachedFieldsByClassMap = new TreeMap<String, TreeMap>();

    public <T> ArrayList<T> getRecordListByPositionFromFile(String filename, Class<T> beanClass) throws Exception {
        TreeMap<Integer, BeanField> idDataFieldIdMap = getIdDataFieldMapByClass(beanClass);
        List<String[]> lines = parseFileToList(filename);

        ArrayList<T> beans = new ArrayList<T>();

        long startTime = System.currentTimeMillis();
        for (int lineCnt = headerRows; lineCnt < lines.size()-trailerRows; lineCnt++) {
            T bean = beanClass.newInstance();

            String[] currentLine = lines.get(lineCnt);
            int fieldsToProcess = Math.min(idDataFieldIdMap.size(), currentLine.length);

            for (var beanField : idDataFieldIdMap.values()) {
                if (beanField.dataField.id() < currentLine.length) {
                    try {
                        String beanFieldStringValue = currentLine[beanField.dataField.id()];
                        setBeanField(bean, beanField, beanFieldStringValue);
                    } catch (Exception e) {
                        logger.error("Field id {} is invalid\r\n" +
                                        "in file {}\r\n" +
                                        "current line is {}\r\n" +
                                        "number of data records in object {}  = {}\r\n" +
                                        "Number of records in line is {}",
                                beanField.dataField.id(),
                                filename,
                                lineCnt,
                                beanClass.getName(),
                                idDataFieldIdMap.size(),
                                currentLine.length);
                        throw e;
                    }
                }
            }
            beans.add(bean);
        }
        logger.debug("Object Creating Time = {}", System.currentTimeMillis() - startTime);
        return beans;
    }
    public <K, V> HashMap<K,V> getRecordMapByPositionFromFile(String filename, Class<V> beanClass, Function methodFunction) throws Exception {

        TreeMap<Integer, BeanField> idDataFieldIdMap = getIdDataFieldMapByClass(beanClass);
        List<String[]> lines = parseFileToList(filename);

        HashMap<K, V> beans = new HashMap<K, V>();

        long startTime = System.currentTimeMillis();
        for (int lineCnt = headerRows; lineCnt < lines.size()-trailerRows; lineCnt++) {
            V bean = beanClass.newInstance();

            String[] currentLine = lines.get(lineCnt);
            int fieldsToProcess = Math.min(idDataFieldIdMap.size(), currentLine.length);

            for (var beanField : idDataFieldIdMap.values()) {

                try {
                    String beanFieldStringValue = currentLine[beanField.dataField.id()];
                    setBeanField(bean, beanField, beanFieldStringValue);
                }
                catch (Exception e) {
                    logger.error("Field id {} is invalid\r\n"  +
                                    "in file {}\r\n" +
                                    "current line is {}\r\n" +
                                    "number of data records in object {}  = {}\r\n" +
                                    "Number of records in line is {}",
                            beanField.dataField.id(),
                            filename,
                            lineCnt,
                            beanClass.getName(),
                            idDataFieldIdMap.size(),
                            currentLine.length);
                    throw e;
                }

            }

            var key = (K) methodFunction.apply(bean);
            beans.put(key, bean);
        }
        logger.debug("Object Creating Time = {}", System.currentTimeMillis() - startTime);
        return beans;
    }

    public <T> ArrayList<T> getRecordListByFieldAndColumnNameFromFile(String filename, Class<T> beanClass, ArrayList<ArrayList<Integer>> psositionMapping) throws Exception {
        ArrayList<ArrayList<Integer>> positionMapping = new ArrayList<ArrayList<Integer>>();
        return getRecordListByPositionFromFile(filename, beanClass, positionMapping);
    }

    public <T> ArrayList<T> getRecordListByPositionFromFile(String filename, Class<T> beanClass, ArrayList<ArrayList<Integer>> positionMapping) throws Exception {
        TreeMap<Integer, BeanField> idDataFieldIdMap = getIdDataFieldMapByClass(beanClass);
        List<String[]> lines = parseFileToList(filename);

        ArrayList<T> beans = new ArrayList<T>();

        long startTime = System.currentTimeMillis();
        for (int lineCnt = headerRows; lineCnt < lines.size()-trailerRows; lineCnt++) {
            T bean = beanClass.newInstance();

            String[] currentLine = lines.get(lineCnt);
            int fieldsToProcess = Math.min(idDataFieldIdMap.size(), currentLine.length);

            for (int fieldCnt = 0; fieldCnt < positionMapping.size(); fieldCnt++) {

                try {
                    BeanField beanField = idDataFieldIdMap.get(positionMapping.get(fieldCnt).get(0));
                    String beanFieldStringValue = currentLine[positionMapping.get(fieldCnt).get(1)];
                    setBeanField(bean, beanField, beanFieldStringValue);
                }
                catch (Exception e) {
                    logger.error("Field id {} is invalid\r\n"  +
                                    "in file {}\r\n" +
                                    "current line is {}\r\n" +
                                    "number of data records in object {}  = {}\r\n" +
                                    "Number of records in line is {}",
                            fieldCnt,
                            filename,
                            lineCnt,
                            beanClass.getName(),
                            idDataFieldIdMap.size(),
                            currentLine.length);
                    throw e;
                }

            }
            beans.add(bean);
        }
        logger.debug("Object Creating Time = {}", System.currentTimeMillis() - startTime);
        return beans;
    }

    public <T> ArrayList<T> getRecordListByHeaderNameFromFile(String filename, Class<T> beanClass) throws Exception {
        TreeMap<String, BeanField> nameDataFieldIdMap = getNameDataFieldMapByClass(beanClass);
        List<String[]> lines = parseFileToList(filename);

        ArrayList<T> beans = new ArrayList<T>();

        long startTime = System.currentTimeMillis();
        String[] headerNameLine = lines.get(headerNameRow);
        for (int lineCnt = headerRows; lineCnt < lines.size()-trailerRows; lineCnt++) {
            T bean = beanClass.newInstance();

            String[] currentLine = lines.get(lineCnt);
            int fieldsToProcess = Math.min(nameDataFieldIdMap.size(), headerNameLine.length);

            Set<String> keySet = nameDataFieldIdMap.keySet();
            String[] headerKeys = nameDataFieldIdMap.keySet().toArray(new String[0]);

            HashMap<String, String> lineMap = new HashMap<>();
            for (int x = 1; x < headerNameLine.length; x++) {
                lineMap.put(headerNameLine[x], currentLine[x]);
            }

            int fieldCnt = 0;
            for (fieldCnt = 0; fieldCnt < fieldsToProcess; fieldCnt++) {

                BeanField beanField;
                String beanFieldStringValue;

                try {
                    beanField = nameDataFieldIdMap.get(headerKeys[fieldCnt]);
                    beanFieldStringValue = lineMap.get(headerKeys[fieldCnt]);
                    setBeanField(bean, beanField, beanFieldStringValue);
                }
                catch (Exception e) {
                    logger.error("Field name {} is invalid\r\n"  +
                                    "in file {}\r\n" +
                                    "current line is {}\r\n" +
                                    "number of data records in object {}  = {}\r\n" +
                                    "Number of records in line is {}",
                            headerNameLine[fieldCnt],
                            filename,
                            lineCnt,
                            beanClass.getName(),
                            nameDataFieldIdMap.size(),
                            currentLine.length);
                    throw e;
                }

            }
            beans.add(bean);
        }
        logger.debug("Object Creating Time = {}", System.currentTimeMillis() - startTime);
        return beans;
    }

    public <K, V> HashMap<K,V> getRecordMapByHeaderNameFromFile(String filename, Class<V> beanClass, Function methodFunction) throws Exception {
        TreeMap<String, BeanField> nameDataFieldIdMap = getNameDataFieldMapByClass(beanClass);
        List<String[]> lines = parseFileToList(filename);

        HashMap<K, V> beans = new HashMap<K, V>();

        long startTime = System.currentTimeMillis();
        String[] headerNameLine = lines.get(headerNameRow);
        for (int lineCnt = headerRows; lineCnt < lines.size()-trailerRows; lineCnt++) {
            V bean = beanClass.newInstance();

            String[] currentLine = lines.get(lineCnt);
            int fieldsToProcess = Math.min(nameDataFieldIdMap.size(), headerNameLine.length);

            Set<String> keySet = nameDataFieldIdMap.keySet();
            String[] headerKeys = nameDataFieldIdMap.keySet().toArray(new String[0]);

            HashMap<String, String> lineMap = new HashMap<>();
            for (int x = 1; x < headerNameLine.length; x++) {
                lineMap.put(headerNameLine[x], currentLine[x]);
            }

            int fieldCnt = 0;
            for (fieldCnt = 0; fieldCnt < fieldsToProcess; fieldCnt++) {

                BeanField beanField;
                String beanFieldStringValue;

                try {
                    beanField = nameDataFieldIdMap.get(headerKeys[fieldCnt]);
                    beanFieldStringValue = lineMap.get(headerKeys[fieldCnt]);
                    setBeanField(bean, beanField, beanFieldStringValue);
                }
                catch (Exception e) {
                    logger.error("Field name {} is invalid\r\n"  +
                                    "in file {}\r\n" +
                                    "current line is {}\r\n" +
                                    "number of data records in object {}  = {}\r\n" +
                                    "Number of records in line is {}",
                            headerNameLine[fieldCnt],
                            filename,
                            lineCnt,
                            beanClass.getName(),
                            nameDataFieldIdMap.size(),
                            currentLine.length);
                    throw e;
                }

            }
            var key = (K) methodFunction.apply(bean);
            beans.put(key, bean);
        }
        logger.debug("Object Creating Time = {}", System.currentTimeMillis() - startTime);
        return beans;
    }


    public void writeRecordListToDelimitedFile(String filename, List<? extends Object> beanList) throws IOException {

        Path outputPath = Paths.get(filename);
        BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8);

        try {
            if (headerIdRows > 0) {
                outputHeaderIdRow(writer, beanList.get(0).getClass());
            }

            if (headerRows > 0) {
                outputHeaderRow(writer, beanList.get(0).getClass());
            }

            TreeMap<Integer, BeanField> idDataFieldIdMap = getIdDataFieldMapByClass(beanList.get(0).getClass());

            for (Object bean : beanList) {

                boolean first = true;
                for (BeanField beanField : idDataFieldIdMap.values()) {

                    if (beanField.dataField.output() == true) {

                        if (!first) writer.write(delimiterChar);
                        else first = false;

                        Object object = beanField.field.get(bean);

                        if (object != null) {
                            if (object.getClass() == BigDecimal.class) {
                                BigDecimal bigDecimal = (BigDecimal) object;
                                if (beanField.dataField.round() == true || roundDecimals == true) {
                                    bigDecimal = bigDecimal.setScale(beanField.dataField.scale(), RoundingMode.HALF_UP);
                                }

                                writer.write(bigDecimal.toPlainString());
                            } else if (object.getClass() == Double.class || object.getClass() == double.class) {
                                BigDecimal bigDecimal = new BigDecimal((Double) object);
                                if (beanField.dataField.round() == true || roundDecimals == true) {
                                    bigDecimal = bigDecimal.setScale(beanField.dataField.scale(), RoundingMode.HALF_UP);
                                }
                                writer.write(bigDecimal.toPlainString());
                            } else if (object.getClass() == LocalDate.class) {
                                LocalDate localDate = (LocalDate) object;
                                DateTimeFormatter dtf = DateTimeFormatter.ofPattern(beanField.dataField.dateFormat());
                                writer.write(localDate.format(dtf));
                            } else if (object.getClass() == LocalDateTime.class) {
                                LocalDateTime localDateTime = (LocalDateTime) object;
                                DateTimeFormatter dtf = DateTimeFormatter.ofPattern(beanField.dataField.dateFormat());
                                writer.write(localDateTime.format(dtf));
                            } else if (object.getClass() == Timestamp.class) {
                                Timestamp timestamp = (Timestamp) object;
                                DateTimeFormatter dtf = DateTimeFormatter.ofPattern(beanField.dataField.dateFormat());
                                writer.write(timestamp.toLocalDateTime().format(dtf));
                            } else if (object.getClass() == String.class) {
                                if (quoteChar != null && ((String) object).contains(String.valueOf(delimiterChar))) writer.write(quoteChar);
                                writer.write(object.toString());
                                if (quoteChar != null && object.toString().contains(String.valueOf(delimiterChar))) writer.write(quoteChar);
                            } else {
                                writer.write(object.toString());
                            }
                        }  //not null object
                    } //end annotation for output is true
                }
                writer.write(lineSeparator);
            }
        } catch (Exception e) {
            logger.error("Error", e);
        } finally {
            writer.close();
        }
    }

    protected void outputHeaderIdRow(BufferedWriter writer, Class beanClass) throws IOException {
        TreeMap<Integer, BeanField> idDataFieldIdMap = getIdDataFieldMapByClass(beanClass);

        boolean first = true;
        for (BeanField beanField : idDataFieldIdMap.values()) {
            if (beanField.dataField.output() == true) {
                if (!first) writer.write(delimiterChar);
                else first = false;

                writer.write(Integer.toString(beanField.dataField.id()));
            }
        }
        writer.newLine();
    }

    protected void outputHeaderRow(BufferedWriter writer, Class beanClass) throws IOException {
        TreeMap<Integer, BeanField> idDataFieldIdMap = getIdDataFieldMapByClass(beanClass);

        boolean first = true;
        for (BeanField beanField : idDataFieldIdMap.values()) {
            if (beanField.dataField.output() == true) {
                if (!first) writer.write(delimiterChar);
                else first = false;

                writer.write(beanField.dataField.name());
            }
        }
        writer.newLine();
    }

    public List<String[]> parseFileToList(String filename) throws Exception {
        CsvParserSettings parserSettings = new CsvParserSettings();
        parserSettings.setLineSeparatorDetectionEnabled(true);
        parserSettings.setDelimiterDetectionEnabled(true, ',', '|', '\t');
        parserSettings.setAutoConfigurationEnabled(true);
        parserSettings.setHeaderExtractionEnabled(false);
        parserSettings.setCommentProcessingEnabled(false);

        CsvParser parser = new CsvParser(parserSettings);

        long startTime = System.currentTimeMillis();
        List<String[]> lines = parser.parseAll(new File(filename));
        logger.debug("Parse Time = {}",  System.currentTimeMillis()-startTime);

        return lines;
    }

    protected <T> TreeMap<Integer, BeanField> getIdDataFieldMapByClass(Class<T> beanClass ) {

        Class clazz = beanClass;
        TreeMap<Integer, BeanField> idDataFieldIdMap = IdCachedFieldsByClassMap.get(beanClass.getName());

        if (idDataFieldIdMap == null) {
            idDataFieldIdMap = new TreeMap<Integer, BeanField>();

            do {
                for (Field field : clazz.getDeclaredFields()) {
                    DataField annotation = (DataField) field.getAnnotation(DataField.class);
                    if (annotation != null) {
                        BeanField beanField = new BeanField();
                        beanField.field = field;
                        beanField.dataField = annotation;
                        idDataFieldIdMap.put(annotation.id(), beanField);
                    }
                }
                clazz = clazz.getSuperclass();

            } while (clazz != null);

            IdCachedFieldsByClassMap.put(beanClass.getName(), idDataFieldIdMap);
        }
        return idDataFieldIdMap;
    }

    protected <T> TreeMap<String, BeanField> getNameDataFieldMapByClass(Class<T> beanClass ) {

        Class clazz = beanClass;
        TreeMap<String, BeanField> nameDataFieldIdMap = NameCachedFieldsByClassMap.get(beanClass.getName());

        if (nameDataFieldIdMap == null) {
            nameDataFieldIdMap = new TreeMap<String, BeanField>();

            do {
                for (Field field : clazz.getDeclaredFields()) {
                    DataField annotation = (DataField) field.getAnnotation(DataField.class);
                    if (annotation != null) {
                        BeanField beanField = new BeanField();
                        beanField.field = field;
                        beanField.dataField = annotation;
                        nameDataFieldIdMap.put(annotation.name(), beanField);
                    }
                }
                clazz = clazz.getSuperclass();

            } while (clazz != null);

            IdCachedFieldsByClassMap.put(beanClass.getName(), nameDataFieldIdMap);
        }
        return nameDataFieldIdMap;
    }

    public int getHeaderRows() {
    return headerRows;
}
    public void setHeaderRows(int headerRows) {
        this.headerRows = headerRows;
    }
    public int getTrailerRows() {
        return trailerRows;
    }
    public void setTrailerRows(int trailerRows) {
        this.trailerRows = trailerRows;
    }
    public char getDelimiterChar() {
        return delimiterChar;
    }
    public void setDelimiterChar(char delimiterChar) {
        this.delimiterChar = delimiterChar;
    }
    public char getQuoteChar() {
        return quoteChar;
    }
    public void setQuoteChar(char quoteChar) {
        this.quoteChar = quoteChar;
    }
    public String getLineSeparator() {
        return lineSeparator;
    }
    public void setLineSeparator(String lineSeparator) {
        this.lineSeparator = lineSeparator;
    }
}