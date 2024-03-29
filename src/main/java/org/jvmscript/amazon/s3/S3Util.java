package org.jvmscript.amazon.s3;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jvmscript.file.FileUtility;
import org.jvmscript.property.PropertyUtility;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.List;

public class S3Util {

    private static final Logger logger = LogManager.getLogger(S3Util.class);

    S3Client s3Client;

    void s3OpenConnection() throws Exception{
        s3OpenConnection("s3.properties");
    }

    void s3OpenConnection(String propertyFilename) throws Exception{
        PropertyUtility.propertyOpenFileClassPath(propertyFilename);
        String accessKeyId = PropertyUtility.propertyGet("accessKeyId");
        String secretAccessKey = PropertyUtility.propertyGet("secretAccessKey");
        String regionName = PropertyUtility.propertyGet("region");
        s3OpenConnection(accessKeyId, secretAccessKey, regionName);
    }

    void s3OpenConnection(String accessKeyId, String secretAccessKey, String regionName ) throws Exception{
        Region region = Region.of(regionName);
        AwsCredentials awsCredentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);

        s3Client = S3Client.builder()
                .region(region)
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                .build();
    }

    void s3CloseConnection() {
        s3Client.close();
    }

    public String[] s3ListBuckets() {
        ListBucketsRequest listBucketsRequest = ListBucketsRequest.builder().build();
        List<Bucket> bucketList = s3Client.listBuckets().buckets();

        int bucketListSize = bucketList.size();
        String[] s3BucketListArray = new String[bucketListSize];

        for (int index = 0; index < bucketListSize; index++) {
            s3BucketListArray[index] = bucketList.get(index).name();
        }

        return s3BucketListArray;
    }

    public void s3CreateBucket(String bucketName) {
        CreateBucketRequest createBucketRequest = CreateBucketRequest
                .builder()
                .bucket(bucketName)
                .createBucketConfiguration(CreateBucketConfiguration.builder()
                                                                    .build())
                .build();

        s3Client.createBucket(createBucketRequest);
    }

    public void s3CreateFolder(String bucket, String folder) {


        String key = folder.endsWith("/") ? folder : folder + "/";
        logger.info("s3CreateFolder bucket = {}, key = {}", bucket, key);

        // create empty content
        InputStream emptyContent = new ByteArrayInputStream(new byte[0]);


        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        RequestBody requestBody = RequestBody.empty();
        PutObjectResponse response = s3Client.putObject(putObjectRequest, requestBody);

    }

    void s3DeleteFolder(String bucket, String folder) {
        //not implemented
    }

    void s3DeleteFile(String bucket, String folder, String filename) {
        //not implemented
    }

    public void s3PutFile(String bucket, String folder, String filePath) throws Exception{

        String filename =  FileUtility.getFileName(filePath);
        String key = folder.endsWith("/") ? folder + filename : folder + "/" + filename;

        logger.info("s3PutFile bucket = {}, key = {}, filepath = {} filename = {}", bucket, key, filePath, filename);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        PutObjectResponse response = s3Client.putObject(putObjectRequest, Paths.get(filePath));
    }

    void s3GetFile(String bucket, String folder, String filename, String localFilename) {

        String key = folder.endsWith("/") ? folder + filename : folder + "/" + filename;
        logger.info("s3GetFile bucket = {}, key = {}, filename = {} local filename = {}", bucket, key, filename, localFilename);

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        s3Client.getObject(getObjectRequest, Paths.get(localFilename));
    }

    public boolean s3DoesBucketExist(String bucketName) {
        GetBucketLocationRequest getBucketLocationRequest = GetBucketLocationRequest.builder().bucket(bucketName).build();

        boolean bucketExists = true;
        try {
            GetBucketLocationResponse bucketLocationResponse = s3Client.getBucketLocation(getBucketLocationRequest);
        }
        catch (Exception e) {
            bucketExists = false;
        }

        return bucketExists;
    }

    public static void main(String[] args) throws Exception{}
}
