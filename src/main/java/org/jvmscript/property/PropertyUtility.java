package org.jvmscript.property;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PropertyUtility {

    private static Properties properties;

    public static void propertyOpenFileClassPath(String propertyFile) throws IOException {
        properties = new Properties();
        InputStream inputStream = PropertyUtility.class.getResourceAsStream("/" + propertyFile);
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

    public static String propertyGet(String propertyName) {
        return properties.getProperty(propertyName);
    }
    public static void propertyPut(String propertyName, Object value) {properties.put(propertyName, value);}
    public static void propertyClear() {properties.clear();;}
    public static void propertyInitialzie() {properties = new Properties();}

}
