package org.jvmscript.box;

import com.box.sdk.*;
import org.apache.logging.log4j.LogManager;
import org.jvmscript.datetime.DateTimeUtility;
import org.jvmscript.file.FileUtility;
import org.jvmscript.sftp.SftpUtility;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.StreamSupport;

public class BoxUtility {

    private static final org.apache.logging.log4j.Logger logger = LogManager.getLogger(BoxUtility.class);

    public static BoxDeveloperEditionAPIConnection api;
    public static void boxOpenConnection() throws Exception{
        boxOpenConnection("box.json");
    }

    public static void boxOpenConnectionJava() throws Exception{
        boxOpenConnectionJavaDev("box.json");
    }

    public static void boxOpenConnection(String boxConfigFile) throws Exception{
        InputStream inputStream = BoxUtility.class.getResourceAsStream("/" + boxConfigFile);
        Reader reader = new InputStreamReader(inputStream);
        BoxConfig boxConfig = BoxConfig.readFrom(reader);
        IAccessTokenCache tokenCache = new InMemoryLRUAccessTokenCache(100);
        api = BoxDeveloperEditionAPIConnection.getAppEnterpriseConnection(boxConfig, tokenCache);
        reader.close();
    }

    public static void boxOpenConnectionJavaDev(String boxConfigFile) throws Exception{
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("/" + boxConfigFile);
        Reader reader = new InputStreamReader(inputStream);
        BoxConfig boxConfig = BoxConfig.readFrom(reader);
        IAccessTokenCache tokenCache = new InMemoryLRUAccessTokenCache(100);
        api = BoxDeveloperEditionAPIConnection.getAppEnterpriseConnection(boxConfig, tokenCache);
        reader.close();
    }

    public static void boxOpenConnectionJava(BoxConfig boxConfig) throws Exception{
        IAccessTokenCache tokenCache = new InMemoryLRUAccessTokenCache(100);
        api = BoxDeveloperEditionAPIConnection.getAppEnterpriseConnection(boxConfig, tokenCache);
    }

    public static String boxUpLoadFile(String localFile, String boxFolderId) throws Exception {
        File file = new File(localFile);

        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            BoxFolder folder = new BoxFolder(api, boxFolderId);
            String name = file.getName();
            BoxFile.Info boxFileInfo = folder.uploadFile(fileInputStream, name);

            String localSha1 = FileUtility.fileCalculateSHA1(localFile);
            if (!localSha1.equals(boxFileInfo.getSha1())) {
                throw new Exception("SHA1 does not match for file " + localFile);
            } else {
                logger.info("Uploaded file {} ID {} to Box Folder {}", localFile, boxFileInfo.getID(), boxFolderId);
                return boxFileInfo.getID();
            }
        } catch (IOException e) {
            throw new Exception("Error uploading file to Box: " + e.getMessage(), e);
        }
    }

    private static ThreadLocal<BoxFile.Info> threadLocalBoxFileInfo = new ThreadLocal<>();

    public static String boxGetFileSha1() {
        BoxFile.Info boxFileInfo = threadLocalBoxFileInfo.get();
        if (boxFileInfo != null) {
            return boxFileInfo.getSha1();
        } else {
            throw new IllegalStateException("boxFileInfo is not initialized");
        }
    }

    public static void setBoxFileInfo(BoxFile.Info fileInfo) {
        threadLocalBoxFileInfo.set(fileInfo);
    }

    public static void clearBoxFileInfo() {
        threadLocalBoxFileInfo.remove();
    }

    public static BoxFile.Info boxGetFileInfo(String fileId) {
        try {
            BoxFile boxFile = new BoxFile(api, fileId);
            BoxFile.Info boxFileInfo = boxFile.getInfo();
            threadLocalBoxFileInfo.set(boxFileInfo);  // Store in thread-local variable
            return boxFileInfo;
        } finally {
            clearBoxFileInfo();
        }
    }

    public static List<String> boxGetFileIdsInFolder(String folderId) {
        BoxFolder folder = new BoxFolder(api, folderId);
        List<String> fileIds = new ArrayList<>();
        for (BoxItem.Info itemInfo : folder) {
            if (itemInfo instanceof BoxFile.Info) {
                fileIds.add(itemInfo.getID());
            }
        }
        return fileIds;
    }

    public static void boxDownloadFileById(String fileId, String localFilePath) throws Exception {
        BoxFile file = new BoxFile(api, fileId);
        try (FileOutputStream outputStream = new FileOutputStream(localFilePath)) {
            file.download(outputStream);
            logger.info("File downloaded to {}", localFilePath);
        } catch (IOException e) {
            throw new Exception("Error downloading file from Box: " + e.getMessage(), e);
        }
    }

    public static void markFileAsProcessed(String fileId) {
        BoxFile file = new BoxFile(api, fileId);
        Metadata metadata = new Metadata();
        metadata.add("/status", "processed");

        try {
            file.createMetadata(metadata);
            logger.info("Marked file with ID {} as processed", fileId);
        } catch (BoxAPIException e) {
            logger.error("Failed to add metadata to file with ID {} - {}", fileId, e.getMessage());
        }
    }
    public static List<String> getUnprocessedFileIds(String folderId) {
        BoxFolder folder = new BoxFolder(api, folderId);
        List<String> unprocessedFileIds = new ArrayList<>();
        for (BoxItem.Info itemInfo : folder) {
            if (itemInfo instanceof BoxFile.Info) {
                BoxFile file = new BoxFile(api, itemInfo.getID());
                try {
                    Metadata metadata = file.getMetadata();
                    String status = metadata.getString("/status");
                    if (!"processed".equals(status)) {
                        unprocessedFileIds.add(itemInfo.getID());
                    }
                } catch (BoxAPIException e) {
                    if (e.getResponseCode() == 404) {
                        unprocessedFileIds.add(itemInfo.getID());
                    } else {
                        logger.error("Error fetching metadata for file with ID {} - {}", itemInfo.getID(), e.getMessage());
                    }
                }
            }
        }
        return unprocessedFileIds;
    }


    public static String boxCreateFolder(String folderName, String parentFolderId) {
        BoxFolder parentFolder = new BoxFolder(api, parentFolderId);
        BoxFolder.Info childFolderInfo = parentFolder.createFolder(folderName);
        return childFolderInfo.getID();
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

    private static ThreadLocal<Metadata> threadLocalMetadata = ThreadLocal.withInitial(Metadata::new);

    public static void boxAddMetaData(String field, String value) throws Exception {
        Metadata metadata = threadLocalMetadata.get();
        metadata.add(field, value);
    }

    public static void boxCreateMetaData(String fileId, String templateId) {
        try {
            Metadata metadata = threadLocalMetadata.get();
            BoxFile boxfile = new BoxFile(api, fileId);
            boxfile.createMetadata(templateId, "enterprise", metadata);
        } finally {
            threadLocalMetadata.remove();
        }
    }


    public static void boxCreateFolderMetaData(String folderId, String templateId) {
        try {
            Metadata metadata = threadLocalMetadata.get();
            BoxFolder folder = new BoxFolder(api, folderId);
            folder.setMetadata(templateId, "enterprise", metadata);
        } finally {
            threadLocalMetadata.remove();
        }
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
