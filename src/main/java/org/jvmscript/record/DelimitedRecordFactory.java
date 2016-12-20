package org.jvmscript.record;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class DelimitedRecordFactory extends RecordFactory {

    public static Logger logger = LoggerFactory.getLogger(DelimitedRecordFactory.class);

    public Character delimiterChar = '|';
    public Character quoteChar = '\t';
    public String lineSeparator = "\r\n";

    protected static TreeMap<String, TreeMap> CachedFieldsByClassMap = new TreeMap<String, TreeMap>();
//    todo add support for header column lookup instead of ordinal position.
//    private static TreeMap<String, TreeMap> BeanFieldClassNameMap = new TreeMap<String, TreeMap>();

    public <T> ArrayList<T> getRecordListByPositionFromFile(String filename, Class<T> beanClass) throws Exception {
        TreeMap<Integer, BeanField> idDataFieldIdMap = getIdDataFieldMapByClass(beanClass);
        List<String[]> lines = parseFileToList(filename);

        ArrayList<T> beans = new ArrayList<T>();

        long startTime = System.currentTimeMillis();
        for (int lineCnt = headerRows; lineCnt < lines.size()-trailerRows; lineCnt++) {
            T bean = beanClass.newInstance();

            String[] currentLine = lines.get(lineCnt);
            int fieldsToProcess = Math.min(idDataFieldIdMap.size(), currentLine.length);

            for (int fieldCnt = 0; fieldCnt < fieldsToProcess; fieldCnt++) {

                try {
                    BeanField beanField = idDataFieldIdMap.get(fieldCnt);
                    String beanFieldStringValue = currentLine[fieldCnt];
                    setBeanField(bean, beanField, beanFieldStringValue);
                }
                catch (Exception e) {
                    logger.error("fieldIdMap.size() = {} line.length = {} lineCnt = {}", idDataFieldIdMap.size(), currentLine.length, lineCnt);
                    throw e;
                }

            }
            beans.add(bean);
        }
        logger.info("Object Creating Time = {}", System.currentTimeMillis() - startTime);
        return beans;
    }

    public void writeRecordListToDelimitedFile(String filename, ArrayList<? extends Object> beanList) throws IOException {

        Path outputPath = Paths.get(filename);
        BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8);

        try {
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
                                writer.write(bigDecimal.toPlainString());
                            } else if (object.getClass() == LocalDate.class) {
                                LocalDate localDate = (LocalDate) object;
                                DateTimeFormatter dtf = DateTimeFormatter.ofPattern(beanField.dataField.dateFormat());
                                writer.write(localDate.format(dtf));
                            } else if (object.getClass() == LocalDateTime.class) {
                                LocalDateTime localDateTime = (LocalDateTime) object;
                                DateTimeFormatter dtf = DateTimeFormatter.ofPattern(beanField.dataField.dateFormat());
                                writer.write(localDateTime.format(dtf));
                            } else {
                                writer.write(object.toString());
                            }
                        }  //not null object
                    } //end annotation for output is true
                }
                writer.newLine();
            }
        } catch (Exception e) {
            logger.error("Error = {}", e);
        } finally {
            writer.close();
        }
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

    private List<String[]> parseFileToList(String filename) throws Exception {
        CsvParserSettings parserSettings = new CsvParserSettings();
        parserSettings.getFormat().setDelimiter(delimiterChar);
        parserSettings.getFormat().setLineSeparator(lineSeparator);
        parserSettings.getFormat().setQuote(quoteChar);
        CsvParser parser = new CsvParser(parserSettings);

        long startTime = System.currentTimeMillis();
        List<String[]> lines = parser.parseAll(new FileReader(filename));
        logger.info("Parse Time = {}",  System.currentTimeMillis()-startTime);

        return lines;
    }

    protected <T> TreeMap<Integer, BeanField> getIdDataFieldMapByClass(Class<T> beanClass ) {

        Class clazz = beanClass;
        TreeMap<Integer, BeanField> idDataFieldIdMap = CachedFieldsByClassMap.get(beanClass.getName());

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

            CachedFieldsByClassMap.put(beanClass.getName(), idDataFieldIdMap);
        }
        return idDataFieldIdMap;

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