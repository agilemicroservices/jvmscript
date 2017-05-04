package org.jvmscript.jams;

import static org.jvmscript.http.HttpUtility.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jvmscript.jams.Variable;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Properties;

public class JamsUtility {
    public static String access_token;
    private static ObjectMapper mapper = new ObjectMapper();
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
        server = serverInput;
        String url = server + "/jams/api/authentication/login";
        Login login = new Login();
        login.username = userame;
        login.password = password;

        HashMap<String, Object> headerMap = new HashMap<String, Object>();
        headerMap.put("Content-Type", "application/json");

        String loginResponseString = httpPost(url,
                                              mapper.writeValueAsString(login),
                                              headerMap );

        LoginResponse loginResponse = mapper.readValue(loginResponseString, LoginResponse.class);
        access_token = loginResponse.access_token;
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

        String url = server + "/jams/api/variable";
        httpPut(url,
                mapper.writeValueAsString(jamsVariable),
                headerMap);
    }

    public static void jamsClose() {
        httpDispose();
    }
}

