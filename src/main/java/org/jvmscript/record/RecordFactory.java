package org.jvmscript.record;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RecordFactory {

    public class BeanField {
        public Field field;
        public DataField dataField;
    }

    public int headerRows = 1;
    public int headerIdRows = 0;
    public int trailerRows = 0;
    public int headerNameRow = 0;

    public static Logger logger = LoggerFactory.getLogger(RecordFactory.class);

    void setBeanField(Object bean, BeanField beanField, String value) throws Exception {

        try {
            if (value != null && !"".equals(value)) {

                value = value.trim();

                if (beanField.field.getType() == String.class) {
                    beanField.field.set(bean, value);
                } else if (beanField.field.getType() == Integer.class || beanField.field.getType() == int.class) {
                    value = cleanNumberString(value);
                    beanField.field.set(bean, Integer.valueOf(value));
                } else if (beanField.field.getType() == Double.class || beanField.field.getType() == double.class) {
                    value = cleanNumberString(value);
                    if (value == null && beanField.field.getType() == double.class) {
                        beanField.field.set(bean, 0.00);
                    }
                    else {
                        beanField.field.set(bean, Double.valueOf(value));
                    }
                } else if (beanField.field.getType() == BigDecimal.class) {
                    value = cleanNumberString(value);
                    beanField.field.set(bean, new BigDecimal(value));
                } else if (beanField.field.getType() == LocalDate.class) {
                    DateTimeFormatter dtf = DateTimeFormatter.ofPattern(beanField.dataField.dateFormat());
                    beanField.field.set(bean, LocalDate.parse(value, dtf));
                } else if (beanField.field.getType() == LocalDateTime.class) {
                    DateTimeFormatter dtf = DateTimeFormatter.ofPattern(beanField.dataField.dateFormat());
                    beanField.field.set(bean, LocalDateTime.parse(value, dtf));
                } else {
                    Object object = beanField.field.getType().getDeclaredConstructor(String.class).newInstance(value);
                    beanField.field.set(bean, object);
                }
            } else if (beanField.field != null) {
                if (beanField.field.getType() == double.class || beanField.field.getType() == float.class) {
                    beanField.field.set(bean, 0.00);
                }
                else if (beanField.field.getType() == int.class) {
                    beanField.field.set(bean, 0);
                }
                else {
                    beanField.field.set(bean, null);
                }
            }
            else {
                throw new Exception("Invalid Field ID " + beanField.dataField.id() + " for class " + bean.getClass().getName());
            }
        }
        catch (Exception e) {
            logger.error("**Field id {} invalid for value <{}> type <{}>", beanField.dataField.id(), value, beanField.field.getType());
            throw e;
        }
    }

    public String cleanNumberString(String numberString) {

        numberString = StringUtils.replace(numberString, ",", "");
        numberString = StringUtils.replace(numberString, "$", "");
        numberString = StringUtils.replace(numberString, " ", "");

        if (StringUtils.endsWith(numberString, "-")) {
            numberString = StringUtils.replace(numberString, "-", "");
            numberString = "-" + numberString;
        }
        else if (!numberString.startsWith("-") && numberString.contains("-") &&  !numberString.contains("E-")){
            numberString = StringUtils.replace(numberString, "-", "");
        }

        return numberString;
    }

    public static void main(String[] args) {
        var factory = new RecordFactory();

       System.out.println("clean string = " + factory.cleanNumberString("3.0E-4"));
       System.out.println("clean string = " + factory.cleanNumberString("1234-5678"));
        System.out.println("clean string = " + factory.cleanNumberString("-9999"));
    }
}
