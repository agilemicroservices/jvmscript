package org.jvmscript.ftp;

import org.apache.commons.net.ftp.*;
import org.jvmscript.file.FileUtility;
import org.slf4j.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class FtpUtility {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(FtpUtility.class);

    private static FTPClient ftpClient = new FTPClient();
    private static FTPClientConfig ftpClientConfig = new FTPClientConfig();

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

        var localFile = FileUtility.getFileName(remoteFile);
        ftpGet(remoteFile, localFile);
    }

    public static void ftpGet(String remoteFile, String localFile) throws Exception {
        logger.info("FTPUtility.get remoteFile = {} localFile = {}", remoteFile, localFile);

        var outputStream = new FileOutputStream(localFile);
        ftpClient.retrieveFile(remoteFile, outputStream);
        outputStream.close();
    }

    public static String ftpLPwd() {
        return "";
    }

    public static void ftpOpenConnection() throws Exception {
        ftpOpenConnection("application.properties");
    }

    public static void ftpOpenConnection(String propertyFilename) throws Exception {
        Properties properties = new Properties();
        InputStream inputStream = FtpUtility.class.getResourceAsStream("/" + propertyFilename);
        properties.load(inputStream);

        String ftpServer = properties.getProperty("ftp.server");
        String ftpUser = properties.getProperty("ftp.user");
        String ftpPassword = properties.getProperty("ftp.password");
        String ftpPortString = properties.getProperty("ftp.port");

        int ftpPort = 21;

        if (ftpPortString != null) {
            ftpPort = Integer.valueOf(ftpPortString);
        }

        ftpOpenConnection(ftpServer, ftpUser, ftpPassword, ftpPort);
    }

    public static void ftpOpenConnection(String server, String user, String password, int port) throws Exception {

        try {
            ftpClient.connect(server);
            ftpClient.login(user, password);

            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            ftpClient.enterLocalPassiveMode();
        }
        catch (Exception e) {
            logger.error("FtpUtility.Cannot connect to FTP server {} port {} user {}", server, port, user);
            if (!ftpClient.isConnected()) {
                throw new IOException("Cannot connect to FTP Server" + server);
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
        return ftpClient.listNames(fileSpec);
    }

    public static String[] ftpDir(String fileSpec) throws Exception {
        return ftpLs(fileSpec);
    }

    public static void ftpRm(String filename) throws Exception {
        logger.info("FTPUtility.rm, removed filename = {}", filename);
    }

    public static void main(String[] args) throws Exception {
        ftpOpenConnection("cloud.onetick.com", "cloud-ops+corclearing@onetick.com", "cloudopscc");
        var fileSpec = "DAILY_BAR_WITH_FACTORS_US_LISTED_1_20181001_060000.gz";
        var filename = "/dev/" + fileSpec;
        ftpGet(fileSpec, filename);
        ftpCloseConnection();
        FileUtility.unGzipFile(filename);
    }
}

