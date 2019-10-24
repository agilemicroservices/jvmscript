package org.jvmscript.amazon.s3;

public class S3Utility {
    private static S3Util s3Util;

    public static void s3OpenConnection() throws Exception {
        s3OpenConnection("s3.properties");
    }

    public static void s3OpenConnection(String propertyFilename) throws Exception{
        s3Util = new S3Util();
        s3Util.s3OpenConnection(propertyFilename);
    }

    public static void s3OpenConnection(String accessKeyId, String secretAccessKey, String regionName) throws Exception {
        s3Util = new S3Util();
        s3Util.s3OpenConnection(accessKeyId, secretAccessKey, regionName);
    }

    public static void s3CloseConnection() {
        s3Util.s3CloseConnection();
    }

    public static void s3CreateBucket(String bucketName) {
        s3Util.s3CreateBucket(bucketName);
    }

    public static void s3PutFile(String bucket, String folder, String filePath) throws Exception{
        s3Util.s3PutFile(bucket, folder, filePath);
    }

    public static String[] s3ListBuckets() {
        return s3Util.s3ListBuckets();
    }

    public static boolean s3DoesBucketExist(String bucketName) {
        return s3Util.s3DoesBucketExist(bucketName);
    }

    public static void s3CreateFolder(String bucket, String folder) {
        s3Util.s3CreateFolder(bucket, folder);
    }

    public static void s3GetFile(String bucket, String folder, String filename, String localFilename) {
        s3Util.s3GetFile(bucket, folder, filename, localFilename);
    }

    public static void main(String[] args) throws Exception{
        S3Utility.s3OpenConnection();
        S3Utility.s3GetFile("risktest-bucket", "test/t1", "test.txt", "/develop/t.txt");
        S3Utility.s3CloseConnection();
    }

}
