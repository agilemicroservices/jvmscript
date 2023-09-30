package org.jvmscript.box;

import com.box.sdk.*;
import org.apache.logging.log4j.LogManager;
import org.jvmscript.sftp.SftpUtility;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.Reader;
import java.io.File;

public class BoxUtility {

    private static final org.apache.logging.log4j.Logger logger = LogManager.getLogger(SftpUtility.class);

    public static BoxDeveloperEditionAPIConnection api;
    public static void boxOpenConnection() throws Exception{
        boxOpenConnection("box.json");
    }

    public static void boxOpenConnection(String boxConfigFile) throws Exception{
        InputStream inputStream = BoxUtility.class.getResourceAsStream("/" + boxConfigFile);
        Reader reader = new InputStreamReader(inputStream);
        BoxConfig boxConfig = BoxConfig.readFrom(reader);
        IAccessTokenCache tokenCache = new InMemoryLRUAccessTokenCache(100);
        api = BoxDeveloperEditionAPIConnection.getAppEnterpriseConnection(boxConfig, tokenCache);
        reader.close();
    }

    public static void boxCloseConnection() {
//        api.
    }

    public static void boxUpLoadFile(String localFile, String boxFolderId) throws Exception{
        var file = new File(localFile);
        var fileInsputStream = new FileInputStream(file);
        var folder = new BoxFolder(api, boxFolderId);
        var name = file.getName();
        var uploadFile = folder.uploadFile(fileInsputStream, name);
        fileInsputStream.close();
        logger.info("Uploaded file {} ID {} to Box Folder {}", localFile, uploadFile.getID(), boxFolderId);
    }

    public static void getFolderItems(String folderId) {
        BoxFolder folder = new BoxFolder(api, folderId);
        for (BoxItem.Info itemInfo : folder) {
            if (itemInfo instanceof BoxFile.Info) {
                BoxFile.Info fileInfo = (BoxFile.Info) itemInfo;
                logger.info("File Name: {}", fileInfo.getName());
                // Do something with the file.
            } else if (itemInfo instanceof BoxFolder.Info) {
                BoxFolder.Info folderInfo = (BoxFolder.Info) itemInfo;
                logger.info("Folder Name: {}", folderInfo.getName());
                // Do something with the folder.
            }
        }
    }

    public static void main(String[] args) {
        try {
            logger.info("start open connection");
            boxOpenConnection();
            logger.info("end open connection");

            logger.info("start upload file");
            boxUpLoadFile("/opt/data/scm_20230814.txt", "181303634565");
            logger.info("end upload file");

//            getFolderItems("0");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}