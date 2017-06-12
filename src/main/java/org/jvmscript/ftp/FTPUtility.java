package org.jvmscript.ftp;

import com.jcraft.jsch.*;
import com.jcraft.jsch.Logger;
import org.slf4j.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.Vector;

public class FTPUtility {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(FTPUtility.class);

    public static class JschLogger implements Logger {
        static java.util.Hashtable name=new java.util.Hashtable();
        static{
            name.put(new Integer(DEBUG), "DEBUG: ");
            name.put(new Integer(INFO), "INFO: ");
            name.put(new Integer(WARN), "WARN: ");
            name.put(new Integer(ERROR), "ERROR: ");
            name.put(new Integer(FATAL), "FATAL: ");
        }
        public boolean isEnabled(int level){
            return true;
        }
        public void log(int level, String message){
            System.err.print(name.get(new Integer(level)));
            System.err.println(message);
        }
    }

    private static Session session = null;
    private static String keyFile = null;
    private static ChannelSftp sftpChannel;

    public static void connectSFtp() throws JSchException {
        session.connect();
        Channel channel = session.openChannel("sftp");
        channel.connect();
        sftpChannel = (ChannelSftp) channel;
    }

    public static void closeSFtpConnection() {
        logger.info("FTPUtility.closeSFtpConnection sftpChannel.exit()");
        sftpChannel.exit();
        logger.info("FTPUtility.closeSFtpConnection session.disconnect()");
        session.disconnect();
    }

    public static String pwd() throws SftpException {
        String pwd = sftpChannel.pwd();
        logger.info("FTPUtility.pwd = {}", pwd);
        return pwd;
    }

    public static void cd(String remoteDirectory) throws SftpException {
        logger.info("FTPUtility.cd remoteDirectory = {}", remoteDirectory);
        sftpChannel.cd(remoteDirectory);
    }

    public static void lcd(String localDirectory) throws Exception {
        logger.info("FTPUtility.lcd localDirectory = {}", localDirectory);
        sftpChannel.lcd(localDirectory);
    }

    public static void put(String localFile) throws SftpException {
        logger.info("FTPUtility.put localFile = {}", localFile);
        sftpChannel.put(localFile);
    }

    public static void put(String localFile, String remoteFile) throws SftpException {
        logger.info("FTPUtility.put remoteFile = {} localFile = {}", remoteFile, localFile);
        sftpChannel.put(localFile, remoteFile);
    }

    public static void get(String remoteFile) throws Exception {
        logger.info("FTPUtility.get remoteFile = {}", remoteFile);
        sftpChannel.get(remoteFile);
    }

    public static void get(String remoteFile, String localFile) throws Exception {
        logger.info("FTPUtility.get remoteFile = {} localFile = {}", remoteFile, localFile);
        sftpChannel.get(remoteFile, localFile);
    }

    public static String lPwd() {
        return sftpChannel.lpwd();
    }

    public static void openSFtpConnection() throws Exception {
        openSFtpConnection("application.properties");
    }

    public static void openSFtpConnection(String propertyFilename) throws Exception {
        Properties properties = new Properties();
        InputStream inputStream = FTPUtility.class.getResourceAsStream("/" + propertyFilename);
        properties.load(inputStream);

        String ftpServer = properties.getProperty("ftp.server");
        String ftpUser = properties.getProperty("ftp.user");
        String ftpPassword = properties.getProperty("ftp.password");
        String ftpIdentityFile = properties.getProperty("ftp.identity");
        String ftpPortString = properties.getProperty("ftp.port");
        keyFile = ftpIdentityFile;

        Integer ftpPort = 22;

        if (ftpPortString != null) {
            ftpPort = new Integer(ftpPortString);
        }

        openSFtpConnection(ftpServer, ftpUser, ftpPassword, ftpPort);
    }

    public static void openSFtpConnection(String server, String user, String password, int port) throws Exception {

        JSch.setLogger(new JschLogger());

        JSch jsch = new JSch();
        if (keyFile != null) {
            jsch.addIdentity(keyFile);
        }
        logger.info("openSFtpConnection server = {} user = {} port = {}", server, user, port);
        session = jsch.getSession(user, server, port);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("compression.s2c", "zlib@openssh.com,zlib,none");
        session.setConfig("compression.c2s", "zlib@openssh.com,zlib,none");
        session.setConfig("compression_level", "9");


        if (keyFile == null) {
            session.setPassword(password);
        }

        connectSFtp();
    }

    public static void sFtpAddIdentity(String inputKeyFile) {
        logger.info("FTPUtility.sFtpAddIdentity key files = {}", inputKeyFile);
        keyFile = inputKeyFile;
    }

    public static void openSFtpConnection(String server, String user, String password) throws Exception {
        openSFtpConnection(server, user, password, 22);
    }

    public static String[] ls(String fileSpec) throws Exception {
        Vector<ChannelSftp.LsEntry> vector = sftpChannel.ls(fileSpec);
        ArrayList<String> filenames = new ArrayList<String>();
        for (ChannelSftp.LsEntry entry : vector) {
            filenames.add(entry.getFilename());
        }

        return filenames.toArray(new String[0]);
    }

    public static String[] dir(String fileSpec) throws Exception {
        return ls(fileSpec);
    }

    public static void rm(String filename) throws SftpException {
        logger.info("FTPUtility.r, filename = {}", filename);
        sftpChannel.rm(filename);
    }
}
