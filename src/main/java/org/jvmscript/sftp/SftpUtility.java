package org.jvmscript.sftp;

import net.schmizz.sshj.Config;
import net.schmizz.sshj.DefaultConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.StatefulSFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import org.apache.logging.log4j.LogManager;
import org.jvmscript.file.FileUtility;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.jvmscript.property.PropertyUtility.propertyGet;
import static org.jvmscript.property.PropertyUtility.propertyOpenFileClassPath;

public class SftpUtility {

    private static final org.apache.logging.log4j.Logger logger = LogManager.getLogger(SftpUtility.class);

    private static SSHClient sshClient = null;
    private static StatefulSFTPClient sftpClient = null;
    private static String keyFile = null;

    private static String hostPort;

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

        hostPort = ftpServer + ":" + ftpPort.toString();

        sFtpOpenConnection(ftpServer, ftpUser, ftpPassword, ftpPort);
    }

    public static void sFtpOpenConnection(String server, String user, String password, int port) throws Exception {

        Config config = new DefaultConfig();
        sshClient = new SSHClient();
        sshClient.addHostKeyVerifier(new PromiscuousVerifier());
//        sshClient.loadKnownHosts();
        sshClient.connect(server, port);

        if (keyFile != null) {
            var privateKeyFile = new File(keyFile);
            KeyProvider keys = sshClient.loadKeys(privateKeyFile.getPath());
            sshClient.authPublickey(user, keyFile);
        }
        else {
            sshClient.authPassword(user, password);
        }

        sftpClient = (StatefulSFTPClient) sshClient.newStatefulSFTPClient();
    }

    public static void sFtpCloseConnection() throws Exception{
        logger.info("SftpUtility.closeSFtpConnection sftpClient.close()");
        sftpClient.close();
        logger.info("SftpUtility.closeSFtpConnection sshClient.disconnect()");
        sshClient.disconnect();
    }

    public static void sFtpSetPreservedAttributes(boolean value) {
        sftpClient.getFileTransfer().setPreserveAttributes(value);
        //no implementation
        logger.info("sFtpSetPreservedAttributes to {}", value);
    }

    public static String sFtpPwd() throws IOException {
        String pwd = sftpClient.pwd();
        logger.info("SftpUtility.pwd = {}", pwd);
        return pwd;
    }

    public static void sFtpCd(String remoteDirectory) throws IOException {
        logger.info("SftpUtility.cd remoteDirectory = {}", remoteDirectory);
        sftpClient.cd(remoteDirectory);
    }

    public static void sFtpLcd(String localDirectory) throws Exception {
        //no implementation
        logger.info("********* no implmentation SftpUtility.lcd localDirectory = {}", localDirectory);
//        sftpClient.cd(localDirectory);
    }

    public static void sFtpPut(String localFile) throws IOException {
        sFtpPut(localFile, localFile);
    }

    public static void sFtpPut(String localFile, String remoteFile) throws IOException {
        logger.info("SftpUtility.put remoteFile = {} localFile = {}", remoteFile, localFile);
        sftpClient.put(localFile, remoteFile);
    }

    public static void sFtpGet(String remoteFile) throws Exception {
        sFtpGet(remoteFile, remoteFile);
    }

    public static void sFtpGet(String remoteFile, String localFile) throws Exception {
        logger.info("SftpUtility.get remoteFile = {} localFile = {}", remoteFile, localFile);
        try {
            sftpClient.get(remoteFile, localFile);
        }
        catch(Exception e) {
            logger.error("sFtp Server {} Error {}, Remote File {}, Local File {}", hostPort, e.getMessage(), remoteFile, localFile);
            throw e;
        }
    }

    public static String sFtpLPwd() {
        //no implementation
        logger.info("********* no implmentation SftpUtility.sFtpLPwd()");
        return "";
    }

    public static void sFtpAddIdentity(String inputKeyFile) {
        logger.info("SftpUtility.sFtpAddIdentity key files = {}", inputKeyFile);
        keyFile = inputKeyFile;
    }

    public static void ftpRm(String filename) throws IOException {
        logger.info("SftpUtility.ftpRm, filename = {}", filename);
        sftpClient.rm(filename);
    }

    public static String[] sFtpLs() throws IOException {
        return  sFtpLs("*");
    }

    public static String[] sFtpLs(String fileSpec) throws IOException {
        boolean wildcard = false;
        var baseFileName = FileUtility.getFileName(fileSpec);
        var path = FileUtility.getFilePath(fileSpec);

        if (baseFileName.contains("*" )) {
            wildcard = true;
            baseFileName = baseFileName.replace("*", ".*");
        }
        if ( ".".equals(baseFileName)) {
            wildcard = true;
            baseFileName = ".*";
        }

        List<RemoteResourceInfo> files;
        if ("".equals(path)) {
            files = sftpClient.ls();
        }
        else {
            files = sftpClient.ls(path);
        }

        ArrayList<String> filenames = new ArrayList<String>();
        for (RemoteResourceInfo remoteResourceInfo : files) {
            if (wildcard == true && remoteResourceInfo.getName().matches(baseFileName)) {
                filenames.add(remoteResourceInfo.getName());
            }
            else if (wildcard == false && "".equals(baseFileName) ) {
                filenames.add(remoteResourceInfo.getName());
            }
            else if ("".equals(baseFileName) == false && baseFileName.equals(remoteResourceInfo.getName())) {
                filenames.add(remoteResourceInfo.getName());
            }
        }

        return filenames.toArray(new String[0]);
    }

    public static void sFtpExec(String command) throws Exception{
        sftpClient.ls(command);
    }


}