package org.jvmscript.file;

import static org.jvmscript.cli.CliUtility.*;
import static org.jvmscript.cli.CliUtility.cliGetOptionValue;
import static org.jvmscript.file.FileUtility.cleanDirectory;
import static org.jvmscript.log.LogUtility.initLogger;

public class CleanDirectory {
    public static void main(String[] args) throws Exception {
        initLogger("CleanDirectory");

        cliUtilityInitialize();
        cliAddOption("inputDirectory", true, true);
        cliParse(args);

        String inputDirectory = cliGetOptionValue("inputDirectory");
        cleanDirectory(inputDirectory);
    }
}
