package org.jvmscript.sftp;

import com.jcraft.jsch.*;
import com.jcraft.jsch.Logger;
import org.slf4j.*;

import static org.jvmscript.property.PropertyUtility.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Vector;

public class SftpUtility {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SftpUtility.class);

    public static class JschLogger implements Logger {
        static java.util.Hashtable name=new java.util.Hashtable();
        static{
            name.put(DEBUG, "DEBUG: ");
            name.put(INFO, "INFO: ");
            name.put(WARN, "WARN: ");
            name.put(ERROR, "ERROR: ");
            name.put(FATAL, "FATAL: ");
        }
        public boolean isEnabled(int level){
            return true;
        }
        public void log(int level, String message){
            System.err.print(name.get(Integer.valueOf(level)));
            System.err.println(message);
        }
    }

    private static Session session = null;
    private static String keyFile = null;
    private static ChannelSftp sftpChannel;

    public static void sFtpConnect() throws JSchException {
        session.connect();
        Channel channel = session.openChannel("sftp");
        channel.connect();
        sftpChannel = (ChannelSftp) channel;
    }

    public static void sFtpCloseConnection() {
        logger.info("SftpUtility.closeSFtpConnection sftpChannel.exit()");
        sftpChannel.exit();
        logger.info("SftpUtility.closeSFtpConnection session.disconnect()");
        session.disconnect();
    }

    public static String sFtpPwd() throws SftpException {
        String pwd = sftpChannel.pwd();
        logger.info("SftpUtility.pwd = {}", pwd);
        return pwd;
    }

    public static void sFtpCd(String remoteDirectory) throws SftpException {
        logger.info("SftpUtility.cd remoteDirectory = {}", remoteDirectory);
        sftpChannel.cd(remoteDirectory);
    }

    public static void sFtpLcd(String localDirectory) throws Exception {
        logger.info("SftpUtility.lcd localDirectory = {}", localDirectory);
        sftpChannel.lcd(localDirectory);
    }

    public static void sFtpPut(String localFile) throws SftpException {
        logger.info("SftpUtility.put localFile = {}", localFile);
        sftpChannel.put(localFile);
    }

    public static void sFtpPut(String localFile, String remoteFile) throws SftpException {
        logger.info("SftpUtility.put remoteFile = {} localFile = {}", remoteFile, localFile);
        sftpChannel.put(localFile, remoteFile);
    }

    public static void sFtpGet(String remoteFile) throws Exception {
        logger.info("SftpUtility.get remoteFile = {}", remoteFile);
        sftpChannel.get(remoteFile);
    }

    public static void sFtpGet(String remoteFile, String localFile) throws Exception {
        logger.info("SftpUtility.get remoteFile = {} localFile = {}", remoteFile, localFile);
        sftpChannel.get(remoteFile, localFile);
    }

    public static String sFtpLPwd() {
        return sftpChannel.lpwd();
    }

    public static void sFtpOpenConnection() throws Exception {
        sFtpOpenConnection("application.properties");
    }

    public static void sFtpOpenConnection(String propertyFilename) throws Exception {
        propertyOpenFileClassPath(propertyFilename);

        String ftpServer = propertyGet("sftp.server");
        String ftpUser = propertyGet("sftp.user");
        String ftpPassword = propertyGet("sftp.password");
        String ftpIdentityFile = propertyGet("sftp.identity");
        String ftpPortString = propertyGet("sftp.port");
        keyFile = ftpIdentityFile;

        Integer ftpPort = 22;

        if (ftpPortString != null) {
            ftpPort = Integer.valueOf(ftpPortString);
        }

        sFtpOpenConnection(ftpServer, ftpUser, ftpPassword, ftpPort);
    }

    public static void sFtpOpenConnection(String server, String user, String password, int port) throws Exception {

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

        sFtpConnect();
    }

    public static void sFtpAddIdentity(String inputKeyFile) {
        logger.info("SftpUtility.sFtpAddIdentity key files = {}", inputKeyFile);
        keyFile = inputKeyFile;
    }

    public static void openSFtpConnection(String server, String user, String password) throws Exception {
        sFtpOpenConnection(server, user, password, 22);
    }

    public static String[] ftpLs(String fileSpec) throws Exception {
        Vector<ChannelSftp.LsEntry> vector = sftpChannel.ls(fileSpec);
        ArrayList<String> filenames = new ArrayList<String>();
        for (ChannelSftp.LsEntry entry : vector) {
            filenames.add(entry.getFilename());
        }

        return filenames.toArray(new String[0]);
    }

    public static String[] ftpDir(String fileSpec) throws Exception {
        return ftpLs(fileSpec);
    }

    public static void ftpRm(String filename) throws SftpException {
        logger.info("SftpUtility.r, filename = {}", filename);
        sftpChannel.rm(filename);
    }
}