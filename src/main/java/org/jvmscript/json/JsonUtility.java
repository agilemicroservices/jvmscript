package org.jvmscript.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.File;
import java.util.HashMap;

public class JsonUtility {
    static boolean initialized;
    static ObjectMapper objectMapper;
    static ObjectWriter objectWriter;
    static ObjectWriter objectPrettyWriter;

    public static boolean failOnUnknowProperties = false;

    public static void initialize() {
        objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, failOnUnknowProperties);
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectWriter = objectMapper.writer();
        objectPrettyWriter = objectMapper.writerWithDefaultPrettyPrinter();
        initialized = true;
    }

    public static void initialize(boolean failUnknown) {
        failOnUnknowProperties = failUnknown;
        initialize();
    }

    public static void jsonCreateMetaFile(Object object, String fileName) throws Exception {
        if (!initialized) initialize();
        objectWriter.writeValue(new File(fileName), object);
    }

    public static String jsonSerialize(Object object) throws Exception{
        if (!initialized) initialize();
        return objectWriter.writeValueAsString(object);
    }
    public static String jsonSerializePrettyPrint(Object object) throws Exception{
        if (!initialized) initialize();
        return objectPrettyWriter.writeValueAsString(object);
    }

    public static Object jsonDeserialize(String json, Class clazz) throws Exception {
        if (!initialized) initialize();
        return objectMapper.readValue(json, clazz);
    }

    public static <T> T  jsonDeserialize(String json, TypeReference <T> type) throws Exception {
        if (!initialized) initialize();
        return objectMapper.readValue(json, type);
    }

    public static HashMap<String, Object> jsonDeserializeToMap(String jsonText) throws Exception{
        if (!initialized) initialize();
        return objectMapper.readValue(jsonText, HashMap.class );
    }

}
