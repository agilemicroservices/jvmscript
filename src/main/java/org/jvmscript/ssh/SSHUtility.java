package org.jvmscript.ssh;

import net.schmizz.sshj.SSHClient;

import static org.jvmscript.property.PropertyUtility.propertyGet;
import static org.jvmscript.property.PropertyUtility.propertyOpenFileClassPath;

public class SSHUtility {
    private static SSHClient sshClient = null;

    public static void SSHOpenConnection() throws Exception {
        SSHOpenConnection("application.properties");
    }

    public static void SSHOpenConnection(String propertyFilename) throws Exception {
        propertyOpenFileClassPath(propertyFilename);

        var sshServer = propertyGet("ssh.server");
        var sshUser = propertyGet("ssh.user");
        var sshPassword = propertyGet("ssh.password");
        //todo implement port
//        String sshPortString = propertyGet("ssh.port");

        SSHOpenConnection(sshServer, sshUser, sshPassword, 22);
    }

    public static void SSHOpenConnection(String server, String user, String password, int port) throws Exception {
        sshClient = new SSHClient();

        sshClient.loadKnownHosts();
        sshClient.connect(server);
        sshClient.authPassword(user, password);
    }

    public static void SSHCloseConnection() throws Exception{
        if (sshClient.isConnected()) {
            sshClient.disconnect();
        }
    }

    public static void scpUload(String localFilename, String remoteFilename) throws Exception {
        sshClient.newSCPFileTransfer().upload(localFilename, remoteFilename);
    }

    public static void scpDownload(String localFilename, String remoteFilename) throws Exception {
        sshClient.newSCPFileTransfer().download(remoteFilename, localFilename);
    }

}
