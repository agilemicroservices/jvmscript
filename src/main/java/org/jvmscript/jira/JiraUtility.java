package org.jvmscript.jira;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.*;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jvmscript.property.PropertyUtility.propertyGet;
import static org.jvmscript.property.PropertyUtility.propertyOpenFileClassPath;


public final class JiraUtility {
    private static final Logger logger = LogManager.getLogger(JiraUtility.class);
    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static HttpHost jiraHost;

    private static String apiToken;
    private static String username;
    private static String url;


    private JiraUtility() {
        // static class
    }


    public static void initialize(String host, int port) {
        jiraHost = new HttpHost(host, port, AuthScope.ANY_REALM);
    }

    public static void openJiraConnection() throws Exception {
        openJiraConnection("jira.properties");
    }

    public static void openJiraConnection(String propertyFilename) throws Exception {
        propertyOpenFileClassPath(propertyFilename);

        var apiToken = propertyGet("jira.apiToken");
        var username = propertyGet("jira.username");
        var url = propertyGet("jira.url");

        openJiraConnection(apiToken, username, url);
    }

    public static void openJiraConnection(String apiToken, String username, String url) throws Exception {
        JiraUtility.apiToken = apiToken;
        JiraUtility.username = username;
        JiraUtility.url = url;
    }

    private static String toString(InputStream is) throws IOException {
        Reader reader = new InputStreamReader(is);
        StringBuilder builder = new StringBuilder();
        int read;
        char[] chars = new char[1024];
        while ((read = reader.read(chars)) != -1) {
            builder.append(chars, 0, read);
        }
        return builder.toString();
    }

    private static void logUnexpectedResponse(HttpResponse response) throws IOException {
        StatusLine statusLine = response.getStatusLine();
        System.err.printf("Unexpected status code %d, reason %s\nContent:\n%s\n",
                statusLine.getStatusCode(),
                statusLine.getReasonPhrase(),
                response.getEntity() == null ? "" : toString(response.getEntity().getContent()));

        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        System.err.println("StackTrace:");
        for (int i = 2; i < stackTrace.length; i++) {
            StackTraceElement element = stackTrace[i];
            System.err.printf("%s.%s:%d\n",
                    element.getClassName(),
                    element.getMethodName(),
                    element.getLineNumber());
        }
    }

    private static void checkStatusCode(int expectedStatusCode, HttpResponse response) throws IOException {
        int actualStatusCode = response.getStatusLine().getStatusCode();
        if (actualStatusCode != expectedStatusCode) {
            logUnexpectedResponse(response);
            throw new IllegalStateException("Expected status code " + expectedStatusCode + " but received " +
                    actualStatusCode);
        }
    }

    private static HttpClientContext createContext(String username, String password) {
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(jiraHost.getHostName(), jiraHost.getPort(), jiraHost.getSchemeName()),
                new UsernamePasswordCredentials(username, password));
        AuthCache cache = new BasicAuthCache();
        cache.put(jiraHost, new BasicScheme());


        HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);
        context.setAuthCache(cache);

        return context;
    }


    public static String createIssue(String projectKey, String summary, String description, String username, String password)
            throws IOException {
        HttpUriRequest request = RequestBuilder
                .post("/rest/api/latest/issue")
                .setHeader("Content-Type", "application/json")
                .setEntity(new StringEntity(
                        "{\"fields\":{\"project\":{\"key\":\"" +
                                projectKey +
                                "\"}, \"issuetype\":{\"name\":\"Access\"}, \"summary\":\"" +
                                summary +
                                "\", \"description\":\"" +
                                description +
                                "\"}}",
                        StandardCharsets.UTF_8))
                .build();

        HttpClient client = HttpClientBuilder.create().build();
        HttpClientContext context = createContext(username, password);
        HttpResponse response = client.execute(jiraHost, request, context);

        checkStatusCode(201, response);

        String responseStr = toString(response.getEntity().getContent());
        Matcher matcher = Pattern.compile("\"key\":\"([^\"]*)\"").matcher(responseStr);
        if (!matcher.find()) {
            logUnexpectedResponse(response);
            throw new IllegalStateException("No \"key\" property found in JSON repsonse");
        }
        return matcher.group(1);
    }
    public static String jiraCreateIssue(Map<String, Object> inputFieldMap) throws IOException {

        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, Object> fieldsMap = new HashMap<>();

        if (inputFieldMap != null) {
            inputFieldMap.forEach((customFieldName, customFieldValue) -> {
                //customFieldName field id from Jira customfield_xxxxx
                //customFieldValue value for the is User_Group
                if (customFieldValue != null) {
                    var customFieldMap = new HashMap<String, Object>();
                    switch (customFieldName) {
                        case "project":
                            customFieldMap.put("key", customFieldValue);
                            fieldsMap.put(customFieldName, customFieldMap);
                            break;
                        case "assignee":
                        case "reporter":
                            customFieldMap.put("accountId", customFieldValue);
                            fieldsMap.put(customFieldName, customFieldMap);
                            break;
                        case "description":
                        case "summary":
                        case "duedate":
                            fieldsMap.put(customFieldName, customFieldValue);
                            break;
                        default:
                            customFieldMap.put("name", customFieldValue);
                            fieldsMap.put(customFieldName, customFieldMap);
                    }
                }
            });
        }

        Map<String, Object> issueMap = new HashMap<>();
        issueMap.put("fields", fieldsMap);

        String jsonBody = objectMapper.writeValueAsString(issueMap);

        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost request = new HttpPost(url);
        String authHeader = Base64.getEncoder().encodeToString((username + ":" + apiToken).getBytes());
        request.setHeader("Authorization", "Basic " + authHeader);
        request.setHeader("Content-Type", "application/json");
        request.setEntity(new StringEntity(jsonBody));

        HttpResponse response = httpClient.execute(request);
        HttpEntity entity = response.getEntity();
        String responseString = toString(response.getEntity().getContent());

        Matcher matcher = Pattern.compile("\"key\":\"([^\"]*)\"").matcher(responseString);
        if (!matcher.find()) {
            logUnexpectedResponse(response);
            throw new IllegalStateException("No \"key\" property found in JSON repsonse");
        }
        return matcher.group(1);
    }

    public static String jiraCustomFields(String url) throws Exception{
        CloseableHttpClient httpClient = HttpClients.createDefault();
        var request = new HttpGet(url);
//        String authHeader = Base64.getEncoder().encodeToString((username + ":" + apiToken).getBytes());
//        request.setHeader("Authorization", "Basic " + authHeader);
        request.setHeader("Content-Type", "application/json");

        HttpResponse response = httpClient.execute(request);
        HttpEntity entity = response.getEntity();
//        String responseString = EntityUtils.toString(entity, "UTF-8");

//        System.out.println("Response: " + responseString);
        String responseString = toString(response.getEntity().getContent());
        return responseString;
    }

    public static void closeIssue(String issueIdOrKey, String username, String password) throws IOException {
        HttpUriRequest request = RequestBuilder
                .post("/rest/api/latest/issue/" + issueIdOrKey + "/transitions")
                .setHeader("Content-Type", "application/json")
                .setEntity(new StringEntity(
                        "{\"fields\":{\"resolution\":{\"name\":\"Fixed\"}},\"transition\":{\"id\":\"2\"}}",
                        StandardCharsets.UTF_8)
                )
                .build();

        HttpClient client = HttpClientBuilder.create().build();
        HttpClientContext context = createContext(username, password);
        HttpResponse response = client.execute(jiraHost, request, context);
        checkStatusCode(204, response);
    }


    public static void uploadAttachment(String issueKey, InputStream inputStream, String fileName, String username,
                                         String password) throws IOException {
        HttpUriRequest request = RequestBuilder.post()
                .setUri("/rest/api/latest/issue/" + issueKey + "/attachments")
                // .setHeader("Content-Type", "multipart/form-data")
                .setHeader("X-Atlassian-Token", "nocheck")
                .setEntity(
                        MultipartEntityBuilder.create()
                                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                                .addBinaryBody("file", inputStream, ContentType.MULTIPART_FORM_DATA, fileName)
                                .build())
                .build();

        HttpClient client = HttpClientBuilder.create().build();
        HttpClientContext context = createContext(username, password);
        HttpResponse response = client.execute(jiraHost, request, context);
        checkStatusCode(200, response);
    }

    public static String[] listAttachments(String issueIdOrKey, String username, String password) throws IOException {

        // retrieve attachment meta data for ticket to discover attachments

        HttpUriRequest request = RequestBuilder.get("/rest/api/latest/issue/" + issueIdOrKey).build();
        HttpResponse response;
        try {
            HttpClient client = HttpClientBuilder.create().build();
            HttpClientContext context = createContext(username, password);
            response = client.execute(jiraHost, request, context);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
            // TODO handle
        }


        if (response.getStatusLine().getStatusCode() == 404)
            return EMPTY_STRING_ARRAY;
        checkStatusCode(200, response);

        // return list of attachment urls

        String str = toString(response.getEntity().getContent());
        Matcher matcher = Pattern.compile("\"content\":\"([^\"]*)\"").matcher(str);
        ArrayList<String> urls = new ArrayList<String>();
        while (matcher.find()) {
            urls.add(matcher.group(1));
        }

        return urls.toArray(EMPTY_STRING_ARRAY);
    }

    public static InputStream downloadAttachment(String url, String username, String password) throws IOException {
        HttpUriRequest request = RequestBuilder.get(url).build();
        HttpClient client = HttpClientBuilder.create().build();
        HttpClientContext context = createContext(username, password);
        HttpResponse response = client.execute(jiraHost, request, context);
        return response.getEntity().getContent();
    }

    public static void downloadAttachmentTo(String url, String fileName, String username, String password)
            throws IOException {
        InputStream in = downloadAttachment(url, username, password);
        FileOutputStream os = new FileOutputStream(fileName);
        byte[] bytes = new byte[1024];
        int read;
        while ((read = in.read(bytes)) != -1) {
            os.write(bytes, 0, read);
        }
        os.close();
    }


    public static String[] findResolvedIssues(String projectName, String username, String password)
            throws IOException {
        HttpUriRequest request = RequestBuilder
                .get("/rest/api/latest/search?fields=id&jql=status=resolved+and+project=" + projectName)
                .build();
        HttpResponse response;
        try {
            HttpClient client = HttpClientBuilder.create().build();
            HttpClientContext context = createContext(username, password);
            response = client.execute(jiraHost, request, context);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        checkStatusCode(200, response);

        List<String> values = new ArrayList<String>();
        String str = toString(response.getEntity().getContent());
        Matcher matcher = Pattern.compile("\"id\":\"([^\"]*)\"").matcher(str);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }

        return values.toArray(EMPTY_STRING_ARRAY);
    }
}
