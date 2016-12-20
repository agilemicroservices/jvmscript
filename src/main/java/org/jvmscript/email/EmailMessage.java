package org.jvmscript.email;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class EmailMessage {

    private static final Logger logger = LoggerFactory.getLogger(EmailMessage.class);

    Message message;
    Multipart smtpMultipart;

    public void saveAttachmentFiles(String directory) throws Exception {

        Multipart multipart = null;
        logger.info("Content Type = {}", message.getContent().getClass().toString());

        if (message.getContent().getClass() == MimeMultipart.class) {
            multipart = (Multipart) message.getContent();
        }

        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);

            if (bodyPart.getFileName() != null) {

                InputStream inputStream = bodyPart.getInputStream();
                Path target = new File(directory + bodyPart.getFileName()).toPath();
                Files.copy(inputStream, target);

                logger.info("Attachment Number {} Filename = {} Content Type = {}",
                        i,
                        bodyPart.getFileName(),
                        bodyPart.getContentType());
            }
        }
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
}
