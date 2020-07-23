package org.jvmscript.file;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.util.Zip4jConstants;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.jvmscript.datetime.DateTimeUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.stream.Stream;

import static org.jvmscript.datetime.DateTimeUtility.*;

public class FileUtility {
    private static final Logger logger = LoggerFactory.getLogger(FileUtility.class);

    public static String[] ls(String pathName) throws IOException {
        return dir(pathName);
    }

    public static String[] dir(String pathName) throws IOException{
        return dir(pathName, false);
    }

    public static String[] dir(String pathName, boolean recursive) throws IOException{

        File directory = new File(getFilePath(pathName));
        IOFileFilter filter = new WildcardFileFilter(getFileName(pathName), IOCase.INSENSITIVE);
        Collection<File> files;

        if (recursive) {
            files = FileUtils.listFiles(directory, filter, DirectoryFileFilter.DIRECTORY);
        }
        else {
            files = FileUtils.listFiles(directory, filter, null);
        }

        String[] fileNames = new String[files.size()];

        int fileCount = 0;
        for (File file : files) {
            fileNames[fileCount++] = file.getAbsolutePath();
        }

        return fileNames;
    }

    public static String onlyOneFileInDirectoryList(String fileSpec, String errorMessage) throws Exception {
        String[] fileList = dir(fileSpec);
        return onlyOneFileInDirectoryList(fileList, errorMessage);
    }

    public static String onlyOneFileInDirectoryList(String[] fileList, String errorMessage) throws Exception {
        if (fileList.length != 1) {
            throw new RuntimeException("Only One File For " + errorMessage + ". There are " + fileList.length + " files");
        }
        return fileList[0];
    }

    public static String getFilePath(String fullFilename) throws IOException {
        String path = FilenameUtils.getFullPath(fullFilename);
        logger.debug("Full filename = {}, path = {}", fullFilename, path);
        return path;
    }

    public static String getFileDateTime(String fullFilename) throws IOException {
        Long lastModified = new File(fullFilename).lastModified();
        return DateFormatUtils.format(lastModified, "yyyy-MM-dd kk:mm:ss.sss z");
    }

    public static Long getFileSize(String fullFilename) {
        return FileUtils.sizeOf(new File(fullFilename));
    }

    public static Long fileGetLineCount(String fullFilename) throws Exception {
        return fileGetLineCount(fullFilename, 0);
    }

    public static Long fileGetLineCount(String fullFilename, Integer headerLines)  throws Exception {

        Path path = Paths.get(fullFilename);
        Long lineCount = 0L;
        try (Stream<String> stream = Files.lines(path)) {
            lineCount = stream.count();
            stream.close();
        }
        if (lineCount >= headerLines) lineCount = lineCount - headerLines;
        logger.info("file {} with header count {} has {} lines after subtracting header lines", fullFilename, headerLines, lineCount);
        return lineCount;
    }

    public static String getFileName(String fullFilename) throws IOException {
        String filename = FilenameUtils.getName(fullFilename);
        logger.debug("Full filename = {}, filename = {}", fullFilename, filename);
        return filename;
    }

    public static String[] cp(String[] sourceFileNames, String destFilename) throws IOException {
        return copyFile(sourceFileNames, destFilename);
    }

    public static String[] cp(String sourceFilename, String destFilename) throws IOException {
        return copyFile(sourceFilename, destFilename);
    }


    public static String[] copyFile(String[] sourceFileNames, String destFilename) throws IOException {

        String[] fileList = new String[0];

        for (String sourceFilename : sourceFileNames) {
            String[] subFileList = copyFile(sourceFilename, destFilename);
            fileList = ArrayUtils.addAll(fileList, subFileList);
        }
        return fileList;
    }

    public static void concatFiles(String destinationFilename, String ...soureFiles) throws Exception {

        File outputFile = new File(destinationFilename);
        FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
        FileChannel outputFileChannel = fileOutputStream.getChannel();

        long filePosition = 0;

        for(String sourceFilename : soureFiles) {
            File sourceFile = new File(sourceFilename);
            FileInputStream fileInputStream = new FileInputStream(sourceFile);
            FileChannel inputFileChannel = fileInputStream.getChannel();

            outputFileChannel.transferFrom( inputFileChannel,
                    filePosition,
                    inputFileChannel.size());

            filePosition += inputFileChannel.size();

            inputFileChannel.close();
            fileInputStream.close();
        }

        outputFileChannel.close();
        fileOutputStream.close();
    }

    public static String[] copyFile(String sourceFilename, String destFilename) throws IOException{

        File destinationFile = new File(destFilename);
        File sourceFile = new File(sourceFilename);

        if (fileNameContainsWildcard(sourceFilename)) {
            if (!destinationFile.isDirectory()) {
                logger.error("copyFile with wildcard source {} Destination {} is not a directory", sourceFilename, destFilename);
                throw new IOException("Copy Wildcard Source Destination is not a directory");
            }
            else {  //wildcard source and destination is directory
                logger.info("copyFile Wildcard Source {} copied to directory {}", sourceFilename, destFilename);

                WildcardFileFilter wildcardFileFilter = new WildcardFileFilter(getFileName(sourceFilename));
                Collection<File> sourceFiles = FileUtils.listFiles(new File(getFilePath(sourceFilename)), wildcardFileFilter, null);
                return copyFilesToDirectory(sourceFiles, destinationFile);
            }
        }
        else if (sourceFile.isDirectory()){  //not wildcard source
            if (!destinationFile.isDirectory()) {
                logger.error("copyFile with directory source {} Destination {} is not a directory", sourceFilename, destFilename);
                throw new IOException("copyFile Directory Source Destination is not a directory");
            }
            else {
                logger.info("copyFile Directory Source {} copied to directory {}", sourceFilename, destFilename);
                Collection<File> sourceFiles = FileUtils.listFiles(new File(getFilePath(sourceFilename)), null, false);
                return copyFilesToDirectory(sourceFiles, destinationFile);
            }
        }
        else {
            String[] copiedFile = new String[1];

            if (destinationFile.isDirectory()) {
                FileUtils.copyFileToDirectory(sourceFile, destinationFile);
                logger.info("File {} Copied to Directory {}", sourceFilename, destFilename);
                copiedFile[0] = destFilename + getFileName(sourceFilename);
            }
            else {
                logger.info("File {} Copied to File {}", sourceFilename, destFilename);
                FileUtils.copyFile(sourceFile, destinationFile);
                copiedFile[0] = destFilename;
            }
            return copiedFile;
        }
    }

    private static boolean fileNameContainsWildcard(String filename) {
        return (filename.contains("*") || filename.contains("?"));
    }

    private static String[] copyFilesToDirectory(Collection<File> sourceFiles, File destinationFile) throws IOException {

        String[] fileList = new String[sourceFiles.size()];
        int fileCount = 0;
        for (File sf : sourceFiles) {
            FileUtils.copyFileToDirectory(sf, destinationFile);
            fileList[fileCount++] = sf.getAbsolutePath();
            logger.info("File {} Copied to {}", sf.getAbsolutePath(), destinationFile.getAbsolutePath());
        }

        return fileList;
    }

    public static String[] deleteFile(String[] sourceFileNames) throws IOException {

        String[] fileList = new String[0];

        for (String sourceFilename : sourceFileNames) {
            String[] subFileList = deleteFile(sourceFilename);
            fileList = ArrayUtils.addAll(fileList, subFileList);
        }
        return fileList;
    }

    public static String[] deleteFile(String sourceFilename) throws IOException{

        File sourceFile = new File(sourceFilename);

        if (fileNameContainsWildcard(sourceFilename)) {
            //wildcard source
            logger.info("Wildcard Source {} Deleted", sourceFilename);

            WildcardFileFilter wildcardFileFilter = new WildcardFileFilter(getFileName(sourceFilename));
            Collection<File> sourceFiles = FileUtils.listFiles(new File(getFilePath(sourceFilename)), wildcardFileFilter, null);

            String[] fileList = new String[sourceFiles.size()];
            int fileCount = 0;
            for (File sf : sourceFiles) {
                FileUtils.forceDelete(sf);
                fileList[fileCount++] = sf.getAbsolutePath();
                logger.info("File {} Deleted", sf.getAbsolutePath());
            }

            return fileList;
        }
        else if (sourceFile.isDirectory()){  //not wildcard source

            logger.error("deleteFile not allowed with directory source {}", sourceFilename);
            throw new IOException("deleteFile not allowed on Directory Source");
        }
        else {
            String[] deletedFile = new String[1];

            logger.info("File {} Deleted", sourceFilename);
            FileUtils.forceDelete(sourceFile);
            deletedFile[0] = sourceFilename;

            return deletedFile;
        }
    }

    public static String[] moveFile(String[] sourceFileNames, String destFilename) throws IOException {

        String[] fileList = new String[0];

        for (String sourceFilename : sourceFileNames) {
            String[] subFileList = moveFile(sourceFilename, destFilename);
            fileList = ArrayUtils.addAll(fileList, subFileList);
        }
        return fileList;
    }

    public static String[] moveFile(String sourceFilename, String destFilename) throws IOException{

        File destinationFile = new File(destFilename);
        File sourceFile = new File(sourceFilename);

        if (fileNameContainsWildcard(sourceFilename)) {
            if (!destinationFile.isDirectory()) {
                logger.error("moveFile with wildcard source {} Destination {} is not a directory", sourceFilename, destFilename);
                throw new IOException("moveFile Wildcard Source Destination is not a directory");
            }
            else {  //wildcard source and destination is directory
                logger.info("Wildcard Source {} moved to directory {}", sourceFilename, destFilename);

                WildcardFileFilter wildcardFileFilter = new WildcardFileFilter(getFileName(sourceFilename));
                Collection<File> sourceFiles = FileUtils.listFiles(new File(getFilePath(sourceFilename)), wildcardFileFilter, null);
                return moveFilesToDirectory(sourceFiles, destinationFile);
            }
        }
        else if (sourceFile.isDirectory()){  //not wildcard source
            if (!destinationFile.isDirectory()) {
                logger.error("moveFile with directory source {} Destination {} is not a directory", sourceFilename, destFilename);
                throw new IOException("moveFile Directory Source Destination is not a directory");
            }
            else {
                logger.info("Directory Source {} moved to directory {}", sourceFilename, destFilename);
                Collection<File> sourceFiles = FileUtils.listFiles(new File(getFilePath(sourceFilename)), null, false);
                return moveFilesToDirectory(sourceFiles, destinationFile);
            }
        }
        else {
            String[] movedFile = new String[1];

            if (destinationFile.isDirectory()) {
                FileUtils.moveFileToDirectory(sourceFile, destinationFile, true);
                logger.info("File {} Moved to Directory {}", sourceFilename, destFilename);
                movedFile[0] = destFilename + File.separator + getFileName(sourceFilename);
            }
            else {
                logger.info("File {} Moved to File {}", sourceFilename, destFilename);
                FileUtils.moveFile(sourceFile, destinationFile);
                movedFile[0] = destFilename;
            }
            return movedFile;
        }
    }

    private static String[] moveFilesToDirectory(Collection<File> sourceFiles, File destinationFile) throws IOException {

        String[] fileList = new String[sourceFiles.size()];
        int fileCount = 0;
        for (File sf : sourceFiles) {
            FileUtils.moveFileToDirectory(sf, destinationFile, true);
            fileList[fileCount++] = sf.getAbsolutePath();
            logger.info("File {} Moved to {}", sf.getAbsolutePath(), destinationFile.getAbsolutePath());
        }

        return fileList;
    }

    public static String[] archvieFile(String[] sourceFileNames) throws IOException {

        String[] fileList = new String[0];

        for (String sourceFilename : sourceFileNames) {
            String[] subFileList = archiveFile(sourceFilename);
            fileList = ArrayUtils.addAll(fileList, subFileList);
        }
        return fileList;
    }

    public static String[] archiveFile(String sourceFilename) throws IOException {

        return archiveFile(sourceFilename, "archive");
    }

    public static String[] archiveFile(String sourceFilename, String archiveDirectory) throws IOException{
        String archiveSubFolder = archiveDirectory + File.separator + getDateTimeString("yyyy-MM/yyyy-MM-dd");
        String destRootPath = getFilePath(archiveDirectory);
        //String destFilename = destRootPath + archiveSubFolder;
        logger.info("Archive File(s) {} to {}", sourceFilename, archiveSubFolder);

        makeDirectory(archiveSubFolder);
        String[] archiveFiles = moveFile(sourceFilename, archiveSubFolder);

        return archiveFiles;
    }



    public static String makeDirectory(String directoryName) throws IOException {
        File directory = new File(directoryName);

        if (directory.isDirectory()) {
            logger.debug("makeDirectory directory {} already exists", directoryName);
        }
        else if (!directory.exists()){
            FileUtils.forceMkdir(directory);
            logger.info("makeDirectory directory {} created", directoryName);
        }
        else {
            logger.error("makeDirectory directory {} already exisits is a file", directoryName);
            throw new IOException("makeDirectory directory exists and is not a directory");
        }

        return directoryName;
    }

    public static String[] archiveAndTimeStampFile(String[] sourceFileNames) throws IOException{

        String[] fileList = new String[0];

        for (String sourceFilename : sourceFileNames) {
            String[] subFileList = archiveAndTimeStampFile(sourceFilename);
            fileList = ArrayUtils.addAll(fileList, subFileList);
        }
        return fileList;
    }

    public static String[] archiveAndTimeStampFile(String sourceFileName) throws IOException {
        String timeStampFileName = timeStampFile(sourceFileName);
        String[] archivedFileName = archiveFile(timeStampFileName);
        logger.info("archiveAndTimeStampFile() Original - <{}> New <{}>", sourceFileName, archivedFileName);
        return archivedFileName;
    }

    public static String[] archiveAndDateStampFile(String[] sourceFileNames) throws IOException{
        String[] fileList = new String[0];

        for (String sourceFilename : sourceFileNames) {
            String[] subFileList = archiveAndDateStampFile(sourceFilename);
            fileList = ArrayUtils.addAll(fileList, subFileList);
        }
        return fileList;
    }

    public static String[] archiveAndDateStampFile(String sourceFileName) throws IOException {
        String timeStampFileName = dateStampFile(sourceFileName);
        String[] archivedFileName = archiveFile(timeStampFileName);
        logger.info("archiveAndDateStampFile() Original - <{}> New <{}>", sourceFileName, archivedFileName);
        return archivedFileName;
    }

    public static void deleteDirectory(String directoryName) throws IOException {
        cleanDirectory(directoryName);
        File directory = new File(directoryName);
        FileUtils.deleteDirectory(directory);
        directory.delete();
        logger.debug("deleteDirectory {} deleted", directoryName);
    }

    public static void cleanDirectory(String directoryName) throws IOException {
        File directory = new File(directoryName);

        if (!directory.isDirectory()) {
            throw new IOException(directoryName + " is not a directory");
        }

        Iterator<File> fileIterator = FileUtils.iterateFiles(directory, null, true);
        while (fileIterator.hasNext()) {
            File file = fileIterator.next();

            if (!file.isDirectory()) {
                FileUtils.forceDelete(file);
            }
        }
    }

    public static String dateStampFile(String sourceFilename) throws IOException {
        String dateStampedFileName = getFilePath(sourceFilename) +
                getFileBaseName(sourceFilename) + "." +
                DateTimeUtility.getDateString() + "." +
                getFileExtension(sourceFilename);
        logger.info("Source File {} Time Stamped to {}", sourceFilename, dateStampedFileName);
        renameFile(sourceFilename, dateStampedFileName);
        return dateStampedFileName;
    }



    public static String timeStampFile(String sourceFilename) throws IOException {
        String timeStampedFileName = getTimeStampFilename(sourceFilename);

        logger.info("Source File {} Time Stamped to {}", sourceFilename, timeStampedFileName);
        renameFile(sourceFilename, timeStampedFileName);
        return timeStampedFileName;
    }

    private static String getTimeStampFilename(String sourceFilename) throws IOException {
        String timeStampedFileName = getFilePath(sourceFilename) +
                getFileBaseName(sourceFilename) + "." +
                getDateTimeString() + "." +
                getFileExtension(sourceFilename);

        return timeStampedFileName;
    }

    public static String getFileBaseName(String fullFilename) throws IOException {
        String baseName = FilenameUtils.getBaseName(fullFilename);
        logger.debug("Full filename = {}, base name = {}", fullFilename, baseName);
        return baseName;
    }

    public static String getFileExtension(String fullFilename) throws IOException {
        String extension = FilenameUtils.getExtension(fullFilename);
        logger.debug("Full filename = {}, extension = {}", fullFilename, extension);
        return extension;
    }

    private static String renameFile(String sourceFilename, String destFilename) throws IOException{
        File sourceFile = new File(sourceFilename);
        File destFile = new File(destFilename);

        String[] fileList = new String[1];

        if (sourceFile.isFile() && !destFile.exists()) {
            fileList = moveFile(sourceFilename, destFilename);
        }
        else {
            logger.error("renameFile Source File {} and Dest File {} must be files");
            throw new IOException("renameFile Source and Destination must be files");
        }

        return fileList[0];
    }

    public static void unZipFile(String zipFilename, String destinationFolder) throws Exception {
        ZipFile zipFile = new ZipFile(zipFilename);
        zipFile.extractAll(destinationFolder);
        logger.info("unZipFile filename {} to {} directory", zipFilename, destinationFolder);
    }

    public static void unGzipFile(String zipFilename) throws Exception {
        FileInputStream fileInputStream = new FileInputStream(zipFilename);
        BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
        FileOutputStream out = new FileOutputStream(zipFilename.replace(".gz", ""));
        GzipCompressorInputStream gzIn = new GzipCompressorInputStream(bufferedInputStream);
        final byte[] buffer = new byte[2048];
        int n = 0;
        while (-1 != (n = gzIn.read(buffer))) {
            out.write(buffer, 0, n);
        }
        out.close();
        gzIn.close();
        logger.info("unGzipFile filename {}", zipFilename);
    }

    public static String zipFile(String filename) throws Exception {
        String zipFilename = filename + ".zip";
        zipFile(filename, zipFilename);
        return zipFilename;
    }

    public static String zipFile(String filename, String zipFilename) throws Exception {
        ZipFile zipFile = new ZipFile(zipFilename);
        File inputFile = new File(filename);
        ZipParameters zipParameters = new ZipParameters();
        zipParameters.setCompressionMethod(Zip4jConstants.COMP_DEFLATE);
        zipParameters.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL);
        zipFile.addFile(inputFile, zipParameters);
        logger.info("zipFile filename {} to {} zipfile", filename, zipFilename);
        return zipFilename;
    }

    public static void zipFile(String[] filenameList, String zipFilename) throws Exception {
        ZipFile zipFile = new ZipFile(zipFilename);
        ZipParameters zipParameters = new ZipParameters();
        zipParameters.setCompressionMethod(Zip4jConstants.COMP_DEFLATE);
        zipParameters.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL);

        ArrayList<File> files = new ArrayList<>();
        for (String filename : filenameList) {
            files.add(new File(filename));
        }

        zipFile.addFiles(files, zipParameters);
        logger.info("zipFile {} files added to {} zipfile", filenameList.length, zipFilename);
    }

    public static String zipDirectory(String directoryName) throws Exception {
        return zipDirectory(directoryName, false);
    }

    public static String zipDirectory(String directoryName, boolean recursive) throws Exception {
        File directory = new File(directoryName);

        if (!directory.isDirectory()) {
            throw new IOException(directoryName + " is not a directory");
        }

        String endPath = directory.getName();

        String zipFilename = directory.getAbsolutePath() + File.separator + endPath + ".zip";
        ZipFile zipFile = new ZipFile(zipFilename);

        if (recursive) {
            zipFile.addFolder(directoryName, getDefaultZipParameters());
        }
        else {
            ArrayList<File> nonDirectoryFiles = new ArrayList<File>();
            File[] files = directory.listFiles();
            for (File inputFile : files) {
                if (!inputFile.isDirectory())
                    nonDirectoryFiles.add(inputFile);
            }
            zipFile.addFiles(nonDirectoryFiles, getDefaultZipParameters());
        }

        logger.info("zipDirectory directory {} added to {} zipfile", directoryName, zipFilename);

        return zipFilename;
    }

    private static ZipParameters getDefaultZipParameters() {
        ZipParameters zipParameters = new ZipParameters();
        zipParameters.setCompressionMethod(Zip4jConstants.COMP_DEFLATE);
        zipParameters.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL);
        zipParameters.setIncludeRootFolder(true);
        return zipParameters;
    }

    public static boolean fileExists(String filename) throws Exception {
        if (dir(filename).length > 0) {
            return true;
        }
        else {
            return false;
        }
    }

    public static boolean fileContentEquals(String filename1, String filename2) throws IOException {
        File file1 = new File(filename1);
        File file2 = new File(filename2);
        return FileUtils.contentEquals(file1, file2);
    }

    public static void copyDirectory(String sourceDirectory, String destDirectory) throws Exception {
        File sourceDir = new File(sourceDirectory);
        File destDir = new File(destDirectory);
        FileUtils.copyDirectory(sourceDir, destDir);  ;
    }

    public static void archiveDirectoryWithDate(String sourceDirectory, String destDirectory) throws Exception {
        String dateFormat = "yyyy-MM" + File.separator + "yyyy-MM-dd";
        archiveDirectoryWithDate(sourceDirectory, destDirectory, dateFormat);
    }

    public static void archiveDirectoryWithDate(String sourceDirectory, String destDirectory, String dateFormat) throws Exception {
        logger.info("archiveDirectoryWithDate directory {} archive to {}", sourceDirectory, destDirectory);
        String archiveDirectory = destDirectory +
                File.separator +
                DateTimeUtility.getDateString(dateFormat) +
                File.separator;

        Path destDirPath = Paths.get(archiveDirectory);
        Path basePath = new File(sourceDirectory).getParentFile().toPath();

        Iterator<File> fileIterator = FileUtils.iterateFiles(new File(sourceDirectory), null, true);
        while (fileIterator.hasNext()) {
            File file = fileIterator.next();

            if (!file.isDirectory()) {
                Path sourcePath = file.getParentFile().toPath();
                Path relativePath = basePath.relativize(sourcePath);
                File destDirFile = destDirPath.resolve(relativePath).toFile();
                FileUtils.moveToDirectory(file, destDirFile, true);
            }
        }
    }

    public static void archiveZipDirectoryWithDate(String sourceDirectoryName, String targetDirectory) throws Exception{
        String dateFormat = "yyyy-MM" + File.separator + "yyyy-MM-dd";
        archiveZipDirectoryWithDate(sourceDirectoryName, targetDirectory, dateFormat);

    }

    public static void archiveZipDirectoryWithDate(String sourceDirectoryName, String targetDirectory, String dateFormat) throws Exception{
        String zipFilename = zipDirectory(sourceDirectoryName, true);
        String timeStampedFilename = timeStampFile(zipFilename);
        archiveFile(timeStampedFilename, targetDirectory);
        cleanDirectory(sourceDirectoryName);
    }

    public static void main(String[] args) throws Exception {
        String[] files = archiveFile("/dev/source.txt", "/archive/dev");
        copyFile(files[0], "/dev/dest.txt");
        logger.info("files = {}", files.length);
    }
}
