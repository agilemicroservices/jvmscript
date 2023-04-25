package org.jvmscript.datetime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jvmscript.cli.CliUtility;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;

public class DateTimeUtility {

    private static final Logger logger = LogManager.getLogger(DateTimeUtility.class);

    public static LocalTime toLocalTimeFromString(String timeString, String formatter) {
        var dateTimeFormatter = new DateTimeFormatterBuilder().parseCaseInsensitive()
                .appendPattern(formatter)
                .toFormatter(Locale.ENGLISH);
        LocalTime localTime = LocalTime.parse(timeString, dateTimeFormatter);
        return localTime;
    }

    public static String toStringFromLocalTime(LocalTime localTime, String formatter) {
        var dateTimeFormatter = new DateTimeFormatterBuilder().parseCaseInsensitive()
                .appendPattern(formatter)
                .toFormatter(Locale.ENGLISH);
        String localTimeString = localTime.format(dateTimeFormatter);
        return localTimeString;
    }

    public static LocalDate toLocalDateFromString(String dateString, String formatter) {
        LocalDate localDate = null;

        try {
//            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(formatter);
            var dateTimeFormatter = new DateTimeFormatterBuilder().parseCaseInsensitive()
                                                                  .appendPattern(formatter)
                                                                  .toFormatter(Locale.ENGLISH);
            localDate = LocalDate.parse(dateString, dateTimeFormatter);
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return localDate;
    }

    public static String toStringFromLocalDate(LocalDate localDate, String formatter) {
        var dateTimeFormatter = new DateTimeFormatterBuilder().parseCaseInsensitive()
                .appendPattern(formatter)
                .toFormatter(Locale.ENGLISH);
        String localDateString = localDate.format(dateTimeFormatter);
        return localDateString;
    }

    public static String toStringFromLocalDateTime(LocalDateTime localDate, String formatter) {
        var dateTimeFormatter = new DateTimeFormatterBuilder().parseCaseInsensitive()
                .appendPattern(formatter)
                .toFormatter(Locale.ENGLISH);
        String localDateTimeString = dateTimeFormatter.format(localDate);
        return localDateTimeString;
    }

    public static String getDateString() {
        LocalDate now = LocalDate.now();
        return now.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    public static String getDateString(String format) {
        LocalDate now = LocalDate.now();
        var dateTimeFormatter = new DateTimeFormatterBuilder().parseCaseInsensitive()
                .appendPattern(format)
                .toFormatter(Locale.ENGLISH);
        return now.format(dateTimeFormatter);
    }

    public static String getDateTimeString() {
        LocalDateTime now = LocalDateTime.now();
        return now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss.SSS"));
    }

    public static String getDateTimeString(String format) {
        LocalDateTime now = LocalDateTime.now();
        return now.format(DateTimeFormatter.ofPattern(format));
    }

    public static String convertDateStringFormat(String inputDateString, String outputFormat) {
        return convertDateStringFormat(inputDateString, "yyyy-MM-dd", outputFormat);
    }

    public static String convertDateStringFormat(String inputDateString, String inputFormat, String outputFormat) {
        var inputDate = DateTimeUtility.toLocalDateFromString(inputDateString, inputFormat);
        var outputDateString = DateTimeUtility.toStringFromLocalDate(inputDate, outputFormat);
        return outputDateString;
    }

//    public static void main(String[] args) {
//        var today = convertDateStringFormat("23-Jun-23", "dd-MMM-yy", "yyyy-MM-dd");
//        System.out.println(today);
//    }
}
