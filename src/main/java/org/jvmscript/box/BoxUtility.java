package org.jvmscript.box;

import com.box.sdk.*;
import org.apache.logging.log4j.LogManager;
import org.jvmscript.datetime.DateTimeUtility;
import org.jvmscript.file.FileUtility;
import org.jvmscript.sftp.SftpUtility;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

public class BoxUtility {

    private static final org.apache.logging.log4j.Logger logger = LogManager.getLogger(SftpUtility.class);

    public static BoxDeveloperEditionAPIConnection api;
    public static Metadata metadata;
    public static BoxFile.Info boxFileInfo;
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

    public static String boxUpLoadFile(String localFile, String boxFolderId) throws Exception{
        var file = new File(localFile);
        var fileInsputStream = new FileInputStream(file);
        var folder = new BoxFolder(api, boxFolderId);
        var name = file.getName();
        boxFileInfo = folder.uploadFile(fileInsputStream, name);
        fileInsputStream.close();

        var localSha1 = FileUtility.fileCalculateSHA1(localFile);
        if (!localSha1.equals(boxFileInfo.getSha1())) {
            throw new Exception("SHA1 does not match for file " + localFile);
        }

        logger.info("Uploaded file {} ID {} to Box Folder {}", localFile, boxFileInfo.getID(), boxFolderId);
        return boxFileInfo.getID();
    }

    public static String boxGetFileSha1() {
        return boxFileInfo.getSha1();
    }

    public static String boxCreateFolder(String folderName, String parentFolderId) {
        BoxFolder parentFolder = new BoxFolder(api, parentFolderId);
        BoxFolder.Info childFolderInfo = parentFolder.createFolder(folderName);
        return childFolderInfo.getID();
    }

    public static BoxFile.Info boxGetFileInfo(String fileId) {
        BoxFile boxFile = new BoxFile(api, fileId);
        boxFileInfo =  boxFile.getInfo();
        return boxFileInfo;
    }

    public static BoxFile.Info getBoxFileInfo() {
        return boxFileInfo;
    }
    public static String boxCreateMonthFolder(String parentFolderId) {
        var folderName = DateTimeUtility.getDateTimeString("yyyy-MM");
        return boxCreateMonthFolder(parentFolderId, folderName);
    }

    public static String boxCreateMonthFolder(String parentFolderId, String folderName) {

        var folderList = boxGetFolderItems(parentFolderId).stream().filter(item -> item.getType().equals("folder") && item.getName().equals(folderName)).toList();

        String folderId = null;

        if (folderList.size() == 0) {
            folderId = boxCreateFolder(folderName, parentFolderId);
            return folderId;
        } else {
            return folderList.get(0).getID();
        }
    }

    public static String boxCreateDateFolder(String parentFolderId) {
        var dateFolderName = DateTimeUtility.getDateTimeString("yyyy-MM-dd");
        return boxCreateDateFolder(parentFolderId, dateFolderName);
    }

    public static String boxCreateDateFolder(String parentFolderId, String dateFolderName) {
        var monthFolderId = boxCreateMonthFolder(parentFolderId, dateFolderName.substring(0,7));
        var folderList = boxGetFolderItems(monthFolderId).stream().filter(item -> item.getType().equals("folder") && item.getName().equals(dateFolderName)).toList();

        String folderId = null;

        if (folderList.size() == 0) {
            folderId = boxCreateFolder(dateFolderName, monthFolderId);
            return folderId;
        } else {
            return folderList.get(0).getID();
        }
    }

    public static List<BoxItem.Info> boxGetFolderItems(String folderId) {
        BoxFolder folder = new BoxFolder(api, folderId);
        List<BoxItem.Info> list = StreamSupport.stream(folder.spliterator(), false).toList();
        return list;
    }

    public static List<MetadataTemplate> boxGetTemplates() {
        var templates = MetadataTemplate.getEnterpriseMetadataTemplates("enterprise", api);
        var templateList = new ArrayList<MetadataTemplate>();
        for (MetadataTemplate templateInfo : templates) {
            templateList.add(templateInfo);
            System.out.println(templateInfo.getDisplayName());
        }
        return templateList;
    }


    public static MetadataTemplate boxGetTemplatesById(String templateId) {
        var template = MetadataTemplate.getMetadataTemplateByID(api, templateId);
        return template;
    }

    public static void boxAddMetaData(String field, String value) throws Exception{
        if (metadata == null) {
            metadata = new Metadata();
        }
        metadata.add(field, value);

    }

    public static void boxCreateMetaData(String fileId, String templateId) {
        var boxfile  = new BoxFile(api, fileId);
        boxfile.createMetadata(templateId,"enterprise", metadata);
        metadata = null;
    }


    public static void boxCreateFolderMetaData(String folderId, String templateId) {
        BoxFolder folder = new BoxFolder(api, folderId);
        var info = folder.getInfo();
        folder.setMetadata(templateId, "enterprise", metadata);
        metadata = null;
    }
    public static void boxClearMetaData() {
        metadata = null;
    }

    public static void main(String[] args) {
        try {
            //need box.json in the classpath
            boxOpenConnection();
            //root folder id is 0
            var template = boxGetTemplatesById("");
            var items = boxGetFolderItems("0");
//            boxUpLoadFile("text.txt", "0");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
