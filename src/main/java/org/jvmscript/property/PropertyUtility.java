package org.jvmscript.property;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.*;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PropertyUtility {

    private static final Logger logger = LoggerFactory.getLogger(PropertyUtility.class);

    //matches ${ENV:VARIABLE_NAME} placeholders in property file values
    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{ENV:([A-Za-z0-9_]+)}");

    private static Properties properties;

    public static final String DEFAULT_PROPERTY_FILE = "application.properties";

    public static void propertyOpenFileClassPath(String propertyFile) throws IOException {
        properties = new Properties();
        InputStream inputStream = PropertyUtility.class.getResourceAsStream("/" + propertyFile);
        if (inputStream == null) {
            //the implicit default may be absent when config comes entirely from the
            //environment; an explicitly named file signals intent, so it must exist
            if (DEFAULT_PROPERTY_FILE.equals(propertyFile)) {
                logger.info("{} not found on classpath, relying on environment variables and system properties", DEFAULT_PROPERTY_FILE);
                return;
            }
            throw new FileNotFoundException("Property file not found in classpath: " + propertyFile);
        }
        properties.load(inputStream);
        inputStream.close();
    }

    public static void propertyReadFile(String propertyFile) throws Exception {
        properties = new Properties();
        var fileInputStream = new FileInputStream(propertyFile);
        properties.load(fileInputStream);
        fileInputStream.close();
    }

    public static void propertyWriteFile(String propertyFile, String comments) throws Exception {
        var fileOutputStream = new FileOutputStream(propertyFile);
        properties.store(fileOutputStream, comments);
        fileOutputStream.close();
    }

    /**
     * Resolves a property value, secrets-friendly. Resolution order:
     * <ol>
     *   <li>Environment variable, mapped by convention: {@code sftp.password} -> {@code SFTP_PASSWORD}</li>
     *   <li>Java system property: {@code -Dsftp.password=...}</li>
     *   <li>Property file value loaded by {@code propertyOpenFileClassPath}/{@code propertyReadFile}.
     *       File values may reference environment variables as {@code ${ENV:VARIABLE_NAME}}.</li>
     * </ol>
     * Returns null if the property is not defined anywhere.
     */
    public static String propertyGet(String propertyName) {
        return resolveProperty(propertyName, System.getenv());
    }

    //package-private for unit testing with a controlled environment map
    static String resolveProperty(String propertyName, Map<String, String> environment) {
        String environmentName = toEnvironmentName(propertyName);
        String environmentValue = environment.get(environmentName);
        if (environmentValue != null) {
            logger.debug("propertyGet {} resolved from environment variable {}", propertyName, environmentName);
            return environmentValue;
        }

        String systemPropertyValue = System.getProperty(propertyName);
        if (systemPropertyValue != null) {
            logger.debug("propertyGet {} resolved from java system property", propertyName);
            return systemPropertyValue;
        }

        String fileValue = properties != null ? properties.getProperty(propertyName) : null;
        if (fileValue != null) {
            return resolveEnvPlaceholders(propertyName, fileValue, environment);
        }
        return null;
    }

    //sftp.password -> SFTP_PASSWORD
    static String toEnvironmentName(String propertyName) {
        return propertyName.toUpperCase().replaceAll("[^A-Z0-9]", "_");
    }

    static String resolveEnvPlaceholders(String propertyName, String value, Map<String, String> environment) {
        Matcher matcher = ENV_PLACEHOLDER.matcher(value);
        if (!matcher.find()) {
            return value;
        }

        StringBuilder result = new StringBuilder();
        do {
            String environmentName = matcher.group(1);
            String environmentValue = environment.get(environmentName);
            if (environmentValue == null) {
                throw new IllegalStateException("Property " + propertyName +
                        " references environment variable " + environmentName + " which is not set");
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(environmentValue));
        } while (matcher.find());
        matcher.appendTail(result);

        logger.debug("propertyGet {} resolved environment placeholder(s) in property file value", propertyName);
        return result.toString();
    }

    public static void propertyPut(String propertyName, Object value) {properties.put(propertyName, value);}
    public static void propertyClear() {properties.clear();;}

    public static void propertyInitialize() {properties = new Properties();}

    /** @deprecated typo — use {@link #propertyInitialize()} */
    @Deprecated
    public static void propertyInitialzie() {propertyInitialize();}

}
