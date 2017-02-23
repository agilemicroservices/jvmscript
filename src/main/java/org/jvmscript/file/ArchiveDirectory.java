package org.jvmscript.file;

import static org.jvmscript.cli.CliUtility.*;
import static org.jvmscript.log.LogUtility.initLogger;
import static org.jvmscript.file.FileUtility.*;

public class ArchiveDirectory {
    public static void main(String[] args) throws Exception {
        initLogger("ArchiveDirectory");

        cliUtilityInitialize();
        cliAddOption("inputDirectory", true, true);
        cliAddOption("outputDirectory", true, true);

        cliParse(args);

        String inputDirectory = cliGetOptionValue("inputDirectory");
        String outputDirectory = cliGetOptionValue("outputDirectory");

        archiveZipDirectoryWithDate(inputDirectory, outputDirectory);

    }
}
