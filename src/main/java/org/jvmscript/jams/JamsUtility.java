package org.jvmscript.jams;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Properties;

import static org.jvmscript.http.HttpUtility.*;

public class JamsUtility {

    private static final Logger logger = LogManager.getLogger(JamsUtility.class);

    public static String access_token;
    public static Long tokenExpiryTime = 0L;
    private static ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    public static String server;

    public static void jamsLogin() throws IOException {
        jamsLogin("application.properties");

    }

    public static void jamsLogin(String propertyFile) throws IOException {
        Properties properties = new Properties();
        InputStream inputStream = JamsUtility.class.getResourceAsStream("/" + propertyFile);
        properties.load(inputStream);

        String url = properties.getProperty("jams.url");
        String user = properties.getProperty("jams.user");
        String password = properties.getProperty("jams.password");


        jamsLogin(url, user, password);
    }

    public static void jamsLogin(String serverInput, String userame, String password) throws IOException {
        if (access_token != null && !isTokenExpired()) {
            logger.info("Using existing token");
            return;
        }

        server = serverInput;
        String url = server + "/JAMS/api/authentication/login";

        Login login = new Login();
        login.username = userame;
        login.password = password;

        HashMap<String, Object> headerMap = new HashMap<String, Object>();
        headerMap.put("Content-Type", "application/json");

        logger.info("JamsUtility.jamsLogin server {} username {}", serverInput, userame);

        String loginResponseString = httpPost(url,
                mapper.writeValueAsString(login),
                headerMap );

        LoginResponse loginResponse = mapper.readValue(loginResponseString, LoginResponse.class);
        access_token = loginResponse.access_token;
        tokenExpiryTime = System.currentTimeMillis() + (Integer.parseInt(loginResponse.expires_in) * 1000L);
    }

    private static boolean isTokenExpired() {
        return System.currentTimeMillis() >= tokenExpiryTime;
    }

    private static Variable getVariable(String variableName) throws Exception {

        String url = server + "/jams/api/variable?name=" + variableName;

        HashMap<String, Object> headerMap = new HashMap<String, Object>();
        headerMap.put("Content-Type", "application/json");
        headerMap.put("Authorization", "Bearer " + access_token);

        String jamsVariableGetString = httpGet(url, headerMap);
        Variable jamsVariable = mapper.readValue(jamsVariableGetString, Variable.class);

        return jamsVariable;
    }

    public static String jamsGetVariable(String variableName) throws Exception {
        return getVariable(variableName).value;
    }

    public static void jamsSetVariable(String variableName, String variableValue) throws Exception {
        Variable jamsVariable = getVariable(variableName);
        jamsVariable.value = variableValue;

        HashMap<String, Object> headerMap = new HashMap<String, Object>();
        headerMap.put("Content-Type", "application/json");
        headerMap.put("Authorization", "Bearer " + access_token);

        logger.info("JamsUtility.jamsSetVariable {} to {}", variableName, variableValue);

        String url = server + "/JAMS/api/variable/setvalue?name=" + variableName;
        httpPost(url,
                mapper.writeValueAsString(variableValue),
                headerMap);
    }

    public static void jamsClose() {
        httpDispose();
    }
}


