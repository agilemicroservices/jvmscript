package org.jvmscript.jira;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class JiraUtility {
    private static final Logger logger = LoggerFactory.getLogger(JiraUtility.class);
    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static HttpHost jiraHost;


    private JiraUtility() {
        // static class
    }


    public static void initialize(String host, int port) {
        jiraHost = new HttpHost(host, port, AuthScope.ANY_REALM);
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
