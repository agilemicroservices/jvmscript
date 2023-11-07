package org.jvmscript.box;

import com.box.sdk.*;
import com.box.sdk.BoxDeveloperEditionAPIConnection;
import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.logging.log4j.LogManager;
import org.jvmscript.sftp.SftpUtility;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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

    public static void boxUpLoadFile(String localFile, String boxFolderId) throws Exception{
        var file = new File(localFile);
        var fileInsputStream = new FileInputStream(file);
        var folder = new BoxFolder(api, boxFolderId);
        var name = file.getName();
        var uploadFile = folder.uploadFile(fileInsputStream, name);
        fileInsputStream.close();
        logger.info("Uploaded file {} ID {} to Box Folder {}", localFile, uploadFile.getID(), boxFolderId);
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
