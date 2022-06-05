package org.jvmscript.datetime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jvmscript.cli.CliUtility;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class DateTimeUtility {

    private static final Logger logger = LogManager.getLogger(DateTimeUtility.class);

    public static LocalTime toLocalTimeFromString(String timeString, String formatter) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(formatter);
        LocalTime localTime = LocalTime.parse(timeString, dateTimeFormatter);
        return localTime;
    }

    public static String toStringFromLocalTime(LocalTime localTime, String formatter) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(formatter);
        String localTimeString = localTime.format(dateTimeFormatter);
        return localTimeString;
    }

    public static LocalDate toLocalDateFromString(String dateString, String formatter) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(formatter);
        LocalDate localDate = LocalDate.parse(dateString, dateTimeFormatter);
        return localDate;
    }

    public static String toStringFromLocalDate(LocalDate localDate, String formatter) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(formatter);
        String localDateString = localDate.format(dateTimeFormatter);
        return localDateString;
    }

    public static String getDateString() {
        LocalDate now = LocalDate.now();
        return now.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    public static String getDateString(String format) {
        LocalDate now = LocalDate.now();
        return now.format(DateTimeFormatter.ofPattern(format));
    }

    public static String getDateTimeString() {
        LocalDateTime now = LocalDateTime.now();
        return now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }

    public static String getDateTimeString(String format) {
        LocalDateTime now = LocalDateTime.now();
        return now.format(DateTimeFormatter.ofPattern(format));
    }

    public static void main(String[] args) {
        System.out.println(toStringFromLocalDate(LocalDate.now(), "MM-dd-uu"));
    }
}
