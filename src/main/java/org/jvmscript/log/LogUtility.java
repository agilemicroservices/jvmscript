package org.jvmscript.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogUtility {

    public static Logger Logger = LoggerFactory.getLogger("script.logger");

    public static void initLogger(String loggerName) {Logger = LoggerFactory.getLogger(loggerName);}

    public static void debug(String logFormatString, Object... args) {
        Logger.debug(logFormatString, args);
    }

    public static void info(String logFormatString, Object... args) {
        Logger.info(logFormatString, args);
    }

    public static void warn(String logFormatString, Object... args) {
        Logger.warn(logFormatString, args);
    }

    public static void error(String logFormatString, Object... args) {
        Logger.error(logFormatString, args);
    }
}
