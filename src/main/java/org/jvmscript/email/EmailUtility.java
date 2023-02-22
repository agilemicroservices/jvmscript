package org.jvmscript.email;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import static java.nio.file.Files.readAllBytes;

public class EmailUtility {

    private static final Logger logger = LogManager.getLogger(EmailUtility.class);

    private static Folder imapFolder;
    private static Store imapStore;
    private static Session session;
    private static Transport smtpTransport;
    private static String token;

    public static void openImapConnection(String server, String user, String password) throws Exception {
        Properties props = System.getProperties();

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
        Properties properties = new Properties();
        InputStream inputStream = EmailUtility.class.getResourceAsStream("/" + propertyFilename);
        properties.load(inputStream);

        String imapServer = properties.getProperty("imap.server");
        String imapUser = properties.getProperty("imap.user");
        String office365TenantId = properties.getProperty("office365.tenantId");
        String office365ClientId = properties.getProperty("office365.clientId");
        String office365ClientSecret = properties.getProperty("office365.clientSecret");

        var token = getAuthToken(office365TenantId, office365ClientId, office365ClientSecret);

        openImapConnection(imapServer, imapUser, token);

    }

    public static void openImapConnection() throws Exception {
        openImapConnection("application.properties");
    }

    public static void openImapConnection(String propertyFilename) throws Exception {
        Properties properties = new Properties();
        InputStream inputStream = EmailUtility.class.getResourceAsStream("/" + propertyFilename);
        properties.load(inputStream);

        String imapServer = properties.getProperty("imap.server");
        String imapUser = properties.getProperty("imap.user");
        String imapPassword = properties.getProperty("imap.password");

        openImapConnection(imapServer, imapUser, imapPassword);
    }

    public static void openSmtpConnection() throws Exception {
        openSmtpConnection("application.properties");
    }

    public static void openSmtpConnection(String propertyFilename) throws Exception {
        Properties properties = new Properties();
        InputStream inputStream = EmailUtility.class.getResourceAsStream("/" + propertyFilename);
        properties.load(inputStream);

        String smtpServer = properties.getProperty("smtp.server");
        String smtpUser = properties.getProperty("smtp.user");
        String smtpPassword = properties.getProperty("smtp.password");
        String smtpPort = properties.getProperty("smtp.port", "465");
        String auth = properties.getProperty("smtp.auth", "true");
        String tls = properties.getProperty("smtp.tls", "true");

        openSmtpConnection(smtpServer, smtpPort, smtpUser, smtpPassword, auth, tls);
    }

    public static void openSmtpConnection(String server, String smtpPort, String user, String password) throws Exception {
        Properties props = System.getProperties();
        props.setProperty("mail.smtp.port",smtpPort);
        props.setProperty("mail.smtp.auth", "true");
        props.setProperty("mail.smtp.starttls.enable", "true");
        props.setProperty("mail.smtp.host",server);
        props.setProperty("mail.smtp.from",user);

        session = Session.getDefaultInstance(props, null);

        smtpTransport = session.getTransport();
        smtpTransport.connect(user, password);

        openSmtpConnection(server, smtpPort, user, password, "true", "true");
    }

    public static void openSmtpConnection(String server, String smtpPort, String user, String password, String auth, String tls) throws Exception {
        Properties props = System.getProperties();
        props.setProperty("mail.smtp.port",smtpPort);
        props.setProperty("mail.smtp.auth", auth);
        props.setProperty("mail.smtp.starttls.enable", tls);
        props.setProperty("mail.smtp.host", server);
        props.setProperty("mail.smtp.from", user);

        session = Session.getDefaultInstance(props, null);

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

    public static void main(String[] args) throws Exception{

//
        var app = new EmailUtility();
//        var token = app.getAuthToken("3df9da5c-da1d-4d7f-8f3c-5e6b3bd2b1ae", "fa483d17-3402-42f9-a732-5332cc80520d", "Zjl8Q~rCKLiTZFp6NKUD6mekH1I-TpyLbVtzpcmO");
////        System.out.println(token);
//
//        EmailUtility.openImapConnection("outlook.office365.com", "Bluesheetrecorder@vfmarkets.com", token);

        EmailUtility.openOffice365ImapConnection("imap.properties");
        EmailUtility.openImapFolder("Inbox");
        var message = EmailUtility.getFirstEmailMessageInFolder();
        var subject = message.getSubject();
        var from = message.getSenderAddress();
        var contentType = message.message.getContentType();

        var txt = message.getText();

        EmailUtility.closeImapConnection();
    }

    public static String getAuthToken(String tenantId,String clientId,String clientSecret) throws ClientProtocolException, IOException {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost loginPost = new HttpPost("https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token");
        String scopes = "https://outlook.office365.com/.default";
        String encodedBody = "client_id=" + clientId +
                             "&scope=" + scopes +
                             "&client_secret=" + clientSecret +
                             "&grant_type=client_credentials";

        loginPost.setEntity(new StringEntity(encodedBody, ContentType.APPLICATION_FORM_URLENCODED));
        loginPost.addHeader(new BasicHeader("cache-control", "no-cache"));
        CloseableHttpResponse loginResponse = client.execute(loginPost);

        InputStream inputStream = loginResponse.getEntity().getContent();
        byte[] response = inputStream.readAllBytes();

        ObjectMapper objectMapper = new ObjectMapper();
        JavaType type = objectMapper.constructType(
                objectMapper.getTypeFactory().constructParametricType(Map.class, String.class, String.class));
        Map<String, String> parsed = new ObjectMapper().readValue(response, type);

        return parsed.get("access_token");
    }
}
