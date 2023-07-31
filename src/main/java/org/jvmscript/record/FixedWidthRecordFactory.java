package org.jvmscript.record;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class FixedWidthRecordFactory extends RecordFactory {

    private static final Logger logger = LogManager.getLogger(FixedWidthRecordFactory.class);

    class FixedWidthBeanField {
        Field field;
        FixedWidthField fixedWidthField;
    }

    private static TreeMap<String, TreeMap> CachedFieldsByClassMap = new TreeMap<String, TreeMap>();

    public <T> ArrayList<T> getBeanListFromFixedWidthFile(String filename, Class<T> beanClass) throws Exception {

        List<String> lines = FileUtils.readLines(new File(filename));
        ArrayList<T> beans = new ArrayList<T>();

        for (int lineCnt = headerRows; lineCnt < lines.size()-trailerRows; lineCnt++) {
            String line = lines.get(lineCnt);
            T bean = getBeanFromFixedWidthBuffer(line, beanClass);
            beans.add(bean);
        }

        return beans;
    }
    public <T> T getBeanFromFixedWidthBuffer(String buffer, Class<T> beanClass) throws Exception {

        T bean = beanClass.newInstance();
        TreeMap<Integer, FixedWidthBeanField> fixedWidthFieldClassMap = getFixedWidthDataFieldMapByClass(beanClass);

        for (FixedWidthBeanField fixedWidthBeanField : fixedWidthFieldClassMap.values()) {
            FixedWidthField annotation = fixedWidthBeanField.fixedWidthField;

            if (annotation != null) {

                int endPosition = annotation.start()+annotation.length();
                String fieldString = null;

                if (endPosition <= buffer.length()) {
                    fieldString = buffer.substring(annotation.start(), endPosition).trim();
                }

                if (!"".equals(fieldString) && fieldString != null) {
                    if (fixedWidthBeanField.field.getType() == String.class) {
                        fixedWidthBeanField.field.set(bean, fieldString);
                    } else if (fixedWidthBeanField.field.getType() == BigDecimal.class) {
                        try {
                            fixedWidthBeanField.field.set(bean, new BigDecimal(cleanNumberString(fieldString)));
                        }
                        catch (Exception e) {
                            logger.error("field string = {} annotation name  = {} annotation start = {}", fieldString, annotation.name(), annotation.start());
                            throw e;
                        }
                    } else if (fixedWidthBeanField.field.getType() == Integer.class) {
                        fixedWidthBeanField.field.set(bean, Integer.valueOf(cleanNumberString(fieldString)));
                    } else if (fixedWidthBeanField.field.getType() == LocalDate.class) {
                        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(annotation.dateFormat());
                        fixedWidthBeanField.field.set(bean, LocalDate.parse(fieldString, dtf));
                    } else if (fixedWidthBeanField.field.getType() == LocalDateTime.class) {
                        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(annotation.dateFormat());
                        fixedWidthBeanField.field.set(bean, LocalDateTime.parse(fieldString, dtf));
                    } else {
                        Object object = fixedWidthBeanField.field.getType().getDeclaredConstructor(String.class).newInstance(fieldString);
                        fixedWidthBeanField.field.set(bean, object);
                    }
                }
                else {
                    //empty
                    fixedWidthBeanField.field.set(bean, null);
                }
            }
        }
        return bean;
    }

    public String convertFixedWidthBeanToString(Object fixedWidthBean) throws Exception{
        TreeMap<Integer, FixedWidthBeanField> fixedWidthFieldClassMap = getFixedWidthDataFieldMapByClass(fixedWidthBean.getClass());
        var fixedWidthBeanString = buildStringBufferFromFixedWidthBeanFieldList(fixedWidthBean, fixedWidthFieldClassMap);
        return fixedWidthBeanString;
    }

    <T> TreeMap<Integer,FixedWidthBeanField> getFixedWidthDataFieldMapByClass(Class<T> beanClass) {

            Class clazz = beanClass;
            TreeMap<Integer, FixedWidthBeanField> fixedWidthFieldClassMap = CachedFieldsByClassMap.get(beanClass.getName());

            if (fixedWidthFieldClassMap == null) {
                fixedWidthFieldClassMap = new TreeMap<Integer, FixedWidthBeanField>();

                do {
                    for (Field field : clazz.getDeclaredFields()) {
                        FixedWidthField annotation = (FixedWidthField) field.getAnnotation(FixedWidthField.class);
                        if (annotation != null) {
                            FixedWidthBeanField beanField = new FixedWidthBeanField();
                            beanField.field = field;
                            beanField.fixedWidthField = annotation;
                            fixedWidthFieldClassMap.put(annotation.start(), beanField);
                        }
                    }
                    clazz = clazz.getSuperclass();

                } while (clazz != null);

                CachedFieldsByClassMap.put(beanClass.getName(), fixedWidthFieldClassMap);
            }
            return fixedWidthFieldClassMap;
    }
    public void writeFixedWidthBeanListToFile(String filename, ArrayList<? extends Object> fixedWidthBeanList) throws Exception {

        Path outputPath = Paths.get(filename);
        BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8);

        if (fixedWidthBeanList.size() == 0) throw new Exception("Cannot write empty list to file");
        checkAllBeansAreSameClass(fixedWidthBeanList);
        TreeMap<Integer, FixedWidthBeanField> fixedWidthFieldClassMap = getFixedWidthDataFieldMapByClass(fixedWidthBeanList.get(0).getClass());

        try {

            for (Object fixedWidthBean : fixedWidthBeanList) {
                String fixedWidthBeanString = buildStringBufferFromFixedWidthBeanFieldList(fixedWidthBean, fixedWidthFieldClassMap);
                writer.write(fixedWidthBeanString);
                writer.newLine();
            }
        } catch (Exception e) {
            logger.error("Error", e);
        } finally {
            writer.close();
        }
    }

    void checkAllBeansAreSameClass(ArrayList<?> beanList) throws Exception {
        Class previousClass = beanList.get(0).getClass();
        for (Object bean : beanList) {
            if (bean.getClass() != previousClass) throw new Exception("All beans must be same class for fixed width file");
            previousClass = bean.getClass();
        }
    }

    String buildStringBufferFromFixedWidthBeanFieldList(Object fixedWidthBean, TreeMap<Integer, FixedWidthBeanField> fixedWidthFieldClassMap) throws Exception {

        Integer bufferSize = getBufferSize(fixedWidthFieldClassMap);

        StringBuffer fixedWidthBeanStringBuffer = initializeStringBuffer(bufferSize, " ");
        for (FixedWidthBeanField fixedWidthBeanField : fixedWidthFieldClassMap.values()) {
            Object object = fixedWidthBeanField.field.get(fixedWidthBean);
            FixedWidthField annotation = fixedWidthBeanField.fixedWidthField;
            String fixedWidthString = "";

            if (object != null) {
                if (object.getClass() == BigDecimal.class) {
                    BigDecimal bigDecimalValue = (BigDecimal) object;
                    fixedWidthString = convertBigDecimalToFixedWidth(bigDecimalValue,
                            annotation.name(),
                            annotation.scale(),
                            annotation.length());
                }

                else if (object.getClass() == Long.class || object.getClass() == Integer.class) {

                    Long longValue;

                    if (object.getClass() == Integer.class) longValue = ((Integer) object).longValue();
                    else longValue = (Long) object;

                    fixedWidthString = convertLongToFixedWidth(longValue,
                            annotation.name(),
                            annotation.length());
                }

                else if (object.getClass() == LocalDate.class) {
                    LocalDate localDate = (LocalDate) object;
                    LocalDateTime localDateTime = LocalDateTime.of(localDate, LocalTime.MIDNIGHT);
                    fixedWidthString = convertLocalDateTimeToFixedWidth(localDateTime,
                            annotation.name(),
                            annotation.dateFormat(),
                            annotation.length());
                }
                else if (object.getClass() == LocalDateTime.class) {
                    LocalDateTime localDateTime = (LocalDateTime) object;
                    fixedWidthString = convertLocalDateTimeToFixedWidth(localDateTime,
                            annotation.name(),
                            annotation.dateFormat(),
                            annotation.length());
                }
                else {
                    fixedWidthString = convertObjectToFixedWidth(object, annotation.name(), annotation.length());
                }
            }
            fixedWidthBeanStringBuffer.replace( annotation.start()-1,
                    annotation.start() + annotation.length()-1,
                    fixedWidthString);
        }

        return fixedWidthBeanStringBuffer.toString();
    }

    Integer getBufferSize(TreeMap<Integer, FixedWidthBeanField> fixedWidthFieldClassMap) {
        FixedWidthBeanField lastField = fixedWidthFieldClassMap.lastEntry().getValue();
        return lastField.fixedWidthField.start() + lastField.fixedWidthField.length();
    }

    StringBuffer initializeStringBuffer(Integer bufferSize, String initCharacter) {
        StringBuffer stringBuffer = new StringBuffer(" ".repeat(bufferSize));
        return stringBuffer;
    }

    String convertObjectToFixedWidth(Object object, String name, int maxLength) throws Exception {
        String stringValue = object.toString();
        checkStringLength(stringValue, name, maxLength);
        return rightPadFixedString(stringValue, maxLength);
    }

    String convertLocalDateTimeToFixedWidth(LocalDateTime localDateTime, String name, String dateFormat, int maxLength) throws Exception {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(dateFormat);
        String dateString = localDateTime.format(dtf);
        checkStringLength(dateString, name, maxLength);
        return rightPadFixedString(dateString, maxLength);

    }

    public String convertLongToFixedWidth(Long longValue, String name, int length) throws Exception {
        String longString = longValue.toString();
        checkStringLength(longString, name, length);
        return leftPadFixedWidthNumber(longString, length);
    }

    String convertBigDecimalToFixedWidth(BigDecimal bigDecimal, String name, int impliedDecimal, int maxLength) throws Exception {
        bigDecimal = bigDecimal.setScale(impliedDecimal, BigDecimal.ROUND_HALF_UP);
        bigDecimal = bigDecimal.multiply(BigDecimal.TEN.pow(impliedDecimal)).setScale(0);
        String bigDecimalString = bigDecimal.toPlainString();
        checkStringLength(bigDecimalString, name, maxLength);
        return leftPadFixedWidthNumber(bigDecimalString, maxLength);
    }

    void checkStringLength(String stringToCheck, String fieldName, int maxFieldLength) throws Exception {
        if (stringToCheck.length() > maxFieldLength) {
            throw new Exception("Fixed Width Field " +
                    fieldName +
                    " greater than Length of field defination "
                    + maxFieldLength);
        }
    }

    String leftPadFixedWidthNumber(String fixedWidthNumberString, int maxStringLength) {
        if (fixedWidthNumberString.length() < maxStringLength) {
            fixedWidthNumberString = StringUtils.leftPad(fixedWidthNumberString, maxStringLength, '0');
        }
        return fixedWidthNumberString;
    }

    String rightPadFixedString(String fixedWidthString, int maxStringLength) {
        if (fixedWidthString.length() < maxStringLength) {
            fixedWidthString = StringUtils.rightPad(fixedWidthString, maxStringLength, ' ');
        }
        return fixedWidthString;
    }
}


