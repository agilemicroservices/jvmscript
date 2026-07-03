package org.jvmscript.email;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

import static org.jvmscript.property.PropertyUtility.propertyGet;
import static org.jvmscript.property.PropertyUtility.propertyOpenFileClassPath;

public class EmailUtility {

    private static final Logger logger = LoggerFactory.getLogger(EmailUtility.class);

    private static Folder imapFolder;
    private static Store imapStore;
    private static Session session;
    private static Transport smtpTransport;

    //fresh Properties seeded with system properties as defaults: external -Dmail.*
    //flags still apply, but mail settings no longer mutate global JVM state
    private static Properties newMailProperties() {
        return new Properties(System.getProperties());
    }

    public static void openImapConnection(String server, String user, String password) throws Exception {
        Properties props = newMailProperties();

        props.put("mail.store.protocol", "imap");
        props.put("mail.imap.host", server);
        props.put("mail.imap.port", "993");
        props.put("mail.imap.ssl.enable", "true");
        props.put("mail.imap.starttls.enable", "true");
        props.put("mail.imap.auth", "true");
        props.put("mail.imap.auth.mechanisms", "XOAUTH2");
        props.put("mail.imap.user", user);
//        props.put("mail.debug", "true");
//        props.put("mail.debug.auth", "true");

        session = Session.getInstance(props);
//        session.setDebug(true);
//        imapStore = session.getStore("imaps");
        imapStore = session.getStore("imap");

        imapStore.connect(server, user, password);
    }

    public static void openOffice365ImapConnection(String propertyFilename) throws Exception {
        propertyOpenFileClassPath(propertyFilename);

        String imapServer = propertyGet("imap.server");
        String imapUser = propertyGet("imap.user");
        String office365TenantId = propertyGet("office365.tenantId");
        String office365ClientId = propertyGet("office365.clientId");
        String office365ClientSecret = propertyGet("office365.clientSecret");

        var token = getAuthToken(office365TenantId, office365ClientId, office365ClientSecret);

        openImapConnection(imapServer, imapUser, token);

    }

    public static void openImapConnection() throws Exception {
        openImapConnection("application.properties");
    }

    public static void openImapConnection(String propertyFilename) throws Exception {
        propertyOpenFileClassPath(propertyFilename);

        String imapServer = propertyGet("imap.server");
        String imapUser = propertyGet("imap.user");
        String imapPassword = propertyGet("imap.password");

        openImapConnection(imapServer, imapUser, imapPassword);
    }

    public static void openSmtpConnection() throws Exception {
        openSmtpConnection("application.properties");
    }

    public static void openSmtpConnection(String propertyFilename) throws Exception {
        propertyOpenFileClassPath(propertyFilename);

        String smtpServer = propertyGet("smtp.server");
        String smtpUser = propertyGet("smtp.user");
        String smtpPassword = propertyGet("smtp.password");
        String smtpPort = propertyGet("smtp.port") != null ? propertyGet("smtp.port") : "465";
        String auth = propertyGet("smtp.auth") != null ? propertyGet("smtp.auth") : "true";
        String tls = propertyGet("smtp.tls") != null ? propertyGet("smtp.tls") : "true";

        openSmtpConnection(smtpServer, smtpPort, smtpUser, smtpPassword, auth, tls);
    }

    public static void openSmtpConnection(String server, String smtpPort, String user, String password) throws Exception {
        openSmtpConnection(server, smtpPort, user, password, "true", "true");
    }

    public static void openSmtpConnection(String server, String smtpPort, String user, String password, String auth, String tls) throws Exception {
        Properties props = newMailProperties();
        props.setProperty("mail.smtp.port",smtpPort);
        props.setProperty("mail.smtp.auth", auth);
        props.setProperty("mail.smtp.starttls.enable", tls);
        props.setProperty("mail.smtp.host", server);
        props.setProperty("mail.smtp.from", user);

        session = Session.getInstance(props);

        smtpTransport = session.getTransport();
        smtpTransport.connect(user, password);
    }

    public static void closeSmtpConnection() throws MessagingException {
        smtpTransport.close();
    }

    public static void sendEmailMessage(EmailMessage emailMessage) throws MessagingException {
        smtpTransport.sendMessage(emailMessage.message, emailMessage.message.getAllRecipients());
    }

    public static EmailMessage createEmailMessage() {
        EmailMessage emailMessage = new EmailMessage();
        emailMessage.message = new MimeMessage(session);
        return emailMessage;
    }

    public static void openImapFolder(String imapFolderName) throws MessagingException {
        imapFolder = imapStore.getFolder(imapFolderName);
        imapFolder.open(Folder.READ_WRITE);
    }

    public static void closeImapConnection() throws MessagingException {
        imapFolder.close(true);
        imapStore.close();
    }

    public static void copyEmailMessageToImapFolder(EmailMessage emailMessage, String folderName) throws MessagingException {
        Message[] messages = new Message[1];
        messages[0] = emailMessage.message;
        Folder archiveFolder = imapStore.getFolder(folderName);
        if (!archiveFolder.exists()) { archiveFolder.create(Folder.HOLDS_MESSAGES);}
        imapFolder.copyMessages(messages, archiveFolder);
    }

    public static EmailMessage getFirstEmailMessageInFolder() throws MessagingException {
        EmailMessage emailMessage = null;
        if (imapFolder.getMessageCount() > 0) {
            emailMessage = new EmailMessage();
            emailMessage.message = imapFolder.getMessage(1);
        }
        return emailMessage;
    }

    public static void deleteMessage(EmailMessage emailMessage) throws Exception {
        emailMessage.message.setFlag(Flags.Flag.DELETED, true);
        imapFolder.expunge();
    }

    public static String getAuthToken(String tenantId, String clientId, String clientSecret) throws IOException, InterruptedException {
        String scopes = "https://outlook.office365.com/.default";
        String encodedBody = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                             "&scope=" + URLEncoder.encode(scopes, StandardCharsets.UTF_8) +
                             "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8) +
                             "&grant_type=client_credentials";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encodedBody))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Office365 token request for client " + clientId +
                    " failed with status " + response.statusCode() + ": " + response.body());
        }

        ObjectMapper objectMapper = new ObjectMapper();
        JavaType type = objectMapper.constructType(
                objectMapper.getTypeFactory().constructParametricType(Map.class, String.class, String.class));
        Map<String, String> parsed = objectMapper.readValue(response.body(), type);

        return parsed.get("access_token");
    }
}
