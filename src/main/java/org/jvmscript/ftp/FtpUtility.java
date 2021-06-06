package org.jvmscript.ftp;

import org.apache.commons.net.ftp.*;
import org.jvmscript.file.FileUtility;
import org.slf4j.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.jvmscript.property.PropertyUtility.*;

public class FtpUtility {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(FtpUtility.class);

    private static FTPClient ftpClient = new FTPClient();
    //private static FTPClientConfig ftpClientConfig = new FTPClientConfig();

    public static void ftpCloseConnection() throws Exception {
        if (ftpClient.isConnected()) {
            ftpClient.logout();
            ftpClient.disconnect();
        }
    }

    public static String ftpPwd() throws Exception {
        String pwd = ftpClient.printWorkingDirectory();
        logger.info("FTPUtility.pwd = {}", pwd);
        return pwd;
    }

    public static void ftpCd(String remoteDirectory) throws Exception {
        logger.info("FTPUtility.cd remoteDirectory = {}", remoteDirectory);
        ftpClient.changeWorkingDirectory(remoteDirectory);
    }

    public static void ftpLcd(String localDirectory) throws Exception {
        logger.info("FTPUtility.lcd localDirectory = {}", localDirectory);

    }

    public static void ftpPut(String localFile) throws Exception {
        logger.info("FTPUtility.put localFile = {}", localFile);

    }

    public static void ftpPut(String localFile, String remoteFile) throws Exception {
        logger.info("FTPUtility.put remoteFile = {} localFile = {}", remoteFile, localFile);

    }

    public static void ftpGet(String remoteFile) throws Exception {
        logger.info("FTPUtility.get remoteFile = {}", remoteFile);

        String localFile = FileUtility.getFileName(remoteFile);
        ftpGet(remoteFile, localFile);
    }

    public static void ftpGet(String remoteFile, String localFile) throws Exception {
        logger.info("FTPUtility.get remoteFile = {} localFile = {}", remoteFile, localFile);

        FileOutputStream outputStream = new FileOutputStream(localFile);
        boolean result = ftpClient.retrieveFile(remoteFile, outputStream);
        outputStream.close();

        if (result == false) {
            throw new RuntimeException("Could not download " + remoteFile);
        }

    }

    public static String ftpLPwd() {
        return "";
    }

    public static void ftpOpenConnection() throws Exception {
        ftpOpenConnection("application.properties");
    }

    public static void ftpOpenConnection(String propertyFilename) throws Exception {
        propertyOpenFileClassPath(propertyFilename);

        String ftpServer = propertyGet("ftp.server");
        String ftpUser = propertyGet("ftp.user");
        String ftpPassword = propertyGet("ftp.password");
        String ftpPortString = propertyGet("ftp.port");

        int ftpPort = 21;

        if (ftpPortString != null) {
            ftpPort = Integer.valueOf(ftpPortString);
        }

        ftpOpenConnection(ftpServer, ftpUser, ftpPassword, ftpPort);
    }

    public static void ftpOpenConnection(String server, String user, String password, int port) throws Exception {

        try {
            logger.info("FtpUtility.ftpOpenConnection server {} user {} port {}", server, user, port);
            ftpClient.connect(server);
            ftpClient.login(user, password);

            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            ftpClient.enterLocalPassiveMode();
        }
        catch (Exception e) {
            logger.error("FtpUtility.Cannot connect to FTP server {} port {} user {}", server, port, user);
            if (!ftpClient.isConnected()) {
                throw new IOException("Cannot connect to FTP Server " + server);
            }
        }
    }

    public static void ftpMkDir(String directoryName) throws Exception{
        ftpClient.makeDirectory(directoryName);
    }

    public static void ftpOpenConnection(String server, String user, String password) throws Exception {
        ftpOpenConnection(server, user, password, 21);
    }

    public static String[] ftpLs(String fileSpec) throws Exception {
        String[] files = ftpClient.listNames(fileSpec);
        return files;
    }

    public static String[] ftpDir(String fileSpec) throws Exception {
        return ftpLs(fileSpec);
    }

    public static void ftpRm(String filename) throws Exception {
        logger.info("FTPUtility.rm, removed filename = {}", filename);
    }

    public static void main(String[] args) throws Exception {
    }
}
