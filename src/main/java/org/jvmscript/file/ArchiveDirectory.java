package org.jvmscript.file;

import static org.jvmscript.cli.CliUtility.*;
import static org.jvmscript.log.LogUtility.initLogger;
import static org.jvmscript.file.FileUtility.*;

public class ArchiveDirectory {

    public static void main(String[] args) throws Exception {
        initLogger("ArchiveDirectory");

        cliUtilityInitialize();
        cliAddOption("inputDirectory", true, true);
        cliAddOption("archiveDirectory", true, true);

        cliParse(args);

        String inputDirectory = cliGetOptionValue("inputDirectory");
        String archiveDirectory = cliGetOptionValue("archiveDirectory");

        archiveZipDirectoryWithDate(inputDirectory, archiveDirectory);
    }
}
