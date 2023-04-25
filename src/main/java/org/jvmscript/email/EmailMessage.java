package org.jvmscript.email;

import jakarta.mail.*;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class EmailMessage {

    private static final Logger logger = LogManager.getLogger(EmailMessage.class);

    Message message;
    Multipart smtpMultipart;

    public String[] saveAttachmentFiles(String directory) throws Exception {

        var filenameList = new ArrayList<String>();
        Multipart multipart = null;
        logger.info("Content Type = {}", message.getContent().getClass().toString());

        if (message.getContent().getClass() == MimeMultipart.class) {
            multipart = (Multipart) message.getContent();
        }

        for (int i = 0; i < multipart.getCount(); i++) {
            var bodyPart = (MimeBodyPart) multipart.getBodyPart(i);

            var fname = bodyPart.getFileName();
            var body = bodyPart.getContent();

            if (fname != null) {

                InputStream inputStream = bodyPart.getInputStream();
                String filename = directory + bodyPart.getFileName();
                filenameList.add(filename);
                Path target = new File(filename).toPath();
                Files.copy(inputStream, target);

                logger.info("Attachment Number {} Filename = {} Content Type = {} path = {}",
                        i,
                        bodyPart.getFileName(),
                        bodyPart.getContentType(),
                        filename);
            }
        }
        return filenameList.toArray(new String[0]);
    }

    public String getSubject() throws Exception {
        return message.getSubject();
    }

    public void setSubject(String subject) throws MessagingException {
        message.setSubject(subject);
    }

    public void addToRecipient(String toRecipient) throws MessagingException {
        String[] emailAddressList = toRecipient.split(";");
        for (String emailAddress : emailAddressList) {
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(emailAddress));
        }
    }

    public void setFrom(String fromAddress) throws MessagingException {
        message.setFrom(new InternetAddress(fromAddress));
    }

    public String getSenderAddress() throws MessagingException {
        return message.getFrom()[0].toString();
    }

    public void setBody(String body) throws MessagingException, IOException {
        if (message.getClass() == MimeMessage.class) {
            MimeMessage mimeMessage = (MimeMessage) message;

            if (smtpMultipart == null) {
                smtpMultipart = new MimeMultipart();
            }

            BodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setText(body);
            smtpMultipart.addBodyPart(messageBodyPart);

            mimeMessage.setContent(smtpMultipart );
        }
        else {
            message.setContent(body, "text/html");
        }

    }

    public void addAttachment(String fullPathFileName) throws MessagingException {
        if (message.getClass() == MimeMessage.class) {
            MimeMessage mimeMessage = (MimeMessage) message;

            if (smtpMultipart == null) {
                smtpMultipart = new MimeMultipart();
            }

            BodyPart messageBodyPart = new MimeBodyPart();

            DataSource source = new FileDataSource(fullPathFileName);
            messageBodyPart.setDataHandler(new DataHandler(source));

            String filename = FilenameUtils.getName(fullPathFileName);
            messageBodyPart.setFileName(filename);
            smtpMultipart.addBodyPart(messageBodyPart);

            mimeMessage.setContent(smtpMultipart );
        }
        else {
            throw new MessagingException("Cannot Add Attachement to Non MimeMessage");
        }
    }

    private boolean textIsHtml = false;
    public String getText() throws MessagingException, IOException {
        return getText(message);
    }
    private String getText(Part p) throws MessagingException, IOException {

        if (p.isMimeType("text/*")) {
            String s = (String)p.getContent();
            textIsHtml = p.isMimeType("text/html");
            return s;
        }

        if (p.isMimeType("multipart/alternative")) {
            // prefer html text over plain text
            Multipart mp = (Multipart)p.getContent();
            String text = null;
            for (int i = 0; i < mp.getCount(); i++) {
                Part bp = mp.getBodyPart(i);
                if (bp.isMimeType("text/plain")) {
                    if (text == null)
                        text = getText(bp);
                    continue;
                } else if (bp.isMimeType("text/html")) {
                    String s = getText(bp);
                    if (s != null)
                        return s;
                } else {
                    return getText(bp);
                }
            }
            return text;
        } else if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart)p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                String s = getText(mp.getBodyPart(i));
                if (s != null)
                    return s;
            }
        }

        return null;
    }
    public void writeToEml(String filename) throws Exception{
        var file = new FileOutputStream(new File(filename));
        message.writeTo(file);
    }
}
