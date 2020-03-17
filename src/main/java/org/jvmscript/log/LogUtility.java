package org.jvmscript.log;

import org.apache.logging.log4j.*;


public class LogUtility {

    public static void mdcPush(String key, String val) {ThreadContext.put(key, val);}

    public static void mdcClear() {ThreadContext.clearAll();}

    public static Logger logger = LogManager.getLogger("script.logger");

    public static void initLogger(String loggerName) {logger = LogManager.getLogger(loggerName);}

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
