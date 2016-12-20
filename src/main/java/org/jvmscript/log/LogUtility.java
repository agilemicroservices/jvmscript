package org.jvmscript.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogUtility {

    private static Logger logger = LoggerFactory.getLogger("script.logger");

    public static void initLogger(String loggerName) {logger = LoggerFactory.getLogger(loggerName);}

    public static void debug(String logFormatString, Object... args) {
        logger.debug(logFormatString, args);
    }

    public static void info(String logFormatString, Object... args) {
        logger.info(logFormatString, args);
    }

    public static void warn(String logFormatString, Object... args) {
        logger.warn(logFormatString, args);
    }

    public static void error(String logFormatString, Object... args) {
        logger.error(logFormatString, args);
    }
}
