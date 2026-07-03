package org.jvmscript.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class LogUtility {

    /*
     * When log4j2 is the logging backend (the bundled default for standalone scripts)
     * and the script project supplies no log4j2 configuration of its own, select one of
     * the configs bundled in this jar. LOG_FORMAT=json (or -Dlog.format=json) picks the
     * JSON template layout with MDC fields for Loki/OTel-collector/Splunk ingestion;
     * anything else gets the human-readable console pattern.
     *
     * A log4j2.configurationFile system property or a log4j2.* config file in the script
     * project always wins - this only fills the gap where nothing is configured. Runs
     * before the logger field below so it takes effect ahead of log4j2 initialization.
     */
    static {
        configureDefaultLogging();
    }

    static void configureDefaultLogging() {
        if (System.getProperty("log4j2.configurationFile") != null) return;

        String[] userConfigs = {"/log4j2.xml", "/log4j2.properties", "/log4j2.yaml", "/log4j2.yml", "/log4j2.json"};
        for (String userConfig : userConfigs) {
            if (LogUtility.class.getResource(userConfig) != null) return;
        }

        String format = System.getenv("LOG_FORMAT");
        if (format == null) format = System.getProperty("log.format");

        String bundledConfig = "json".equalsIgnoreCase(format) ? "jvmscript-log4j2-json.xml" : "jvmscript-log4j2.xml";
        System.setProperty("log4j2.configurationFile", bundledConfig);
    }

    public static Logger logger = LoggerFactory.getLogger("script.logger");

    public static void mdcPush(String key, String val) {MDC.put(key, val);}

    public static void mdcClear() {MDC.clear();}

    public static void initLogger(String loggerName) {
        logger = LoggerFactory.getLogger(loggerName);
    }

    public static void initLogger(String loggerName, String mdcKey) {
        logger = LoggerFactory.getLogger(loggerName);
        mdcPush(mdcKey, loggerName);
    }

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
