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

    public static void main(String[] args) throws Exception{
        S3Utility.s3OpenConnection();

        //s3Utility.s3CreateBucket("test-3");
        String[] bucketNameList = S3Utility.s3ListBuckets();

        for (int index = 0; index < bucketNameList.length; index++) {
            System.out.println(bucketNameList[index]);
        }

        S3Utility.s3PutFile("risktest-bucket", "test3", "/opt/test.txt");

        S3Utility.s3CloseConnection();
    }

}
