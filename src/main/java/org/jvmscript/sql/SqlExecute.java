package org.jvmscript.sql;

import static org.jvmscript.cli.CliUtility.*;
import static org.jvmscript.sql.SqlUtility.*;
import static org.jvmscript.log.LogUtility.*;

public class SqlExecute {
    public static void main(String[] args) throws Exception{

        initLogger("SqlExecute");

        cliUtilityInitialize();
        cliAddOption("propertyFile", true, true);
        cliAddOption("sqlFile", true, true);
        cliParse(args);

        String sqlFilename = cliGetOptionValue("sqlFile");
        openSqlConnection(cliGetOptionValue("propertyFile"));

        executeSqlFile(sqlFilename);
        info("Sql Execution for file {}", sqlFilename);
    }
}
