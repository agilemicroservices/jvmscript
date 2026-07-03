package org.jvmscript.ssh;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.slf4j.LoggerFactory;

import java.io.File;

import static org.jvmscript.property.PropertyUtility.propertyGet;
import static org.jvmscript.property.PropertyUtility.propertyOpenFileClassPath;

public class SSHUtility {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SSHUtility.class);

    private static SSHClient sshClient = null;

    private static boolean verifyHostKey = true;
    private static String knownHostsFile = null;
    private static String hostKeyFingerprint = null;

    public static void SSHOpenConnection() throws Exception {
        SSHOpenConnection("application.properties");
    }

    public static void SSHOpenConnection(String propertyFilename) throws Exception {
        propertyOpenFileClassPath(propertyFilename);

        var sshServer = propertyGet("ssh.server");
        var sshUser = propertyGet("ssh.user");
        var sshPassword = propertyGet("ssh.password");
        var sshPortString = propertyGet("ssh.port");

        int sshPort = sshPortString != null ? Integer.parseInt(sshPortString) : 22;

        SSHOpenConnection(sshServer, sshUser, sshPassword, sshPort);
    }

    public static void SSHOpenConnection(String server, String user, String password, int port) throws Exception {
        sshClient = new SSHClient();

        if (hostKeyFingerprint != null) {
            sshClient.addHostKeyVerifier(hostKeyFingerprint);
        }
        else if (verifyHostKey) {
            if (knownHostsFile != null) {
                sshClient.loadKnownHosts(new File(knownHostsFile));
            }
            else {
                sshClient.loadKnownHosts();
            }
        }
        else {
            logger.warn("SSH host key verification is DISABLED, connection to {} is vulnerable to man-in-the-middle attacks", server);
            sshClient.addHostKeyVerifier(new PromiscuousVerifier());
        }

        try {
            sshClient.connect(server, port);
        }
        catch (TransportException e) {
            logger.error("SSH connection to {}:{} failed host key verification. Add the server key to ~/.ssh/known_hosts, " +
                         "pin it with SSHSetHostKeyFingerprint(), or call SSHDisableHostKeyVerification() to opt out. Error: {}",
                         server, port, e.getMessage());
            throw e;
        }

        sshClient.authPassword(user, password);
    }

    public static void SSHDisableHostKeyVerification() {
        logger.warn("SSHUtility.SSHDisableHostKeyVerification host key verification disabled for subsequent connections");
        verifyHostKey = false;
    }

    public static void SSHEnableHostKeyVerification() {
        logger.info("SSHUtility.SSHEnableHostKeyVerification host key verification enabled for subsequent connections");
        verifyHostKey = true;
    }

    public static void SSHSetKnownHostsFile(String inputKnownHostsFile) {
        logger.info("SSHUtility.SSHSetKnownHostsFile known hosts file = {}", inputKnownHostsFile);
        knownHostsFile = inputKnownHostsFile;
    }

    //accepts MD5 colon hex (e.g. "4b:69:6c:72..."), "SHA-1:base64" or "SHA-256:base64" fingerprints
    public static void SSHSetHostKeyFingerprint(String fingerprint) {
        logger.info("SSHUtility.SSHSetHostKeyFingerprint fingerprint = {}", fingerprint);
        hostKeyFingerprint = fingerprint;
    }

    public static void SSHCloseConnection() throws Exception{
        if (sshClient.isConnected()) {
            sshClient.disconnect();
        }
    }

    public static void scpUpload(String localFilename, String remoteFilename) throws Exception {
        sshClient.newSCPFileTransfer().upload(localFilename, remoteFilename);
    }

    /** @deprecated typo — use {@link #scpUpload(String, String)} */
    @Deprecated
    public static void scpUload(String localFilename, String remoteFilename) throws Exception {
        scpUpload(localFilename, remoteFilename);
    }

    public static void scpDownload(String localFilename, String remoteFilename) throws Exception {
        sshClient.newSCPFileTransfer().download(remoteFilename, localFilename);
    }

}
