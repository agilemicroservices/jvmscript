package org.jvmscript.cli;

import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CliUtility {

    private static final Logger logger = LogManager.getLogger(CliUtility.class);

    private static Options options;
    private static CommandLineParser commandLineParser;
    private static CommandLine commandLine;

    public static void cliUtilityInitialize() {
        options = new Options();
        commandLineParser = new DefaultParser();
    }

    public static void cliAddOption(String longOpt, boolean hasArg) {
        cliAddOption(longOpt, hasArg,  false);
    }

    public static void cliAddOption(String longOpt, boolean hasArg, boolean required) {
        Option option = new Option(null, longOpt, hasArg, longOpt);
        option.setRequired(required);
        options.addOption(option);
    }

    public static void cliParse(String args[]) throws ParseException {
        commandLine = commandLineParser.parse(options, args);
    }

    public static boolean cliHasOption(String optionValue) {
        return commandLine.hasOption(optionValue);
    }

    public static String cliGetOptionValue(String opt) {
        return commandLine.getOptionValue(opt);
    }

    public static String cliGetOptionValue(String opt, String defaultValue) {
        return commandLine.getOptionValue(opt, defaultValue);
    }

    public static String[] cliGetOptionValues(String opt) {
        return commandLine.getOptionValues(opt);
    }

    public static void  main(String[] args) throws Exception {
        cliUtilityInitialize();
        cliAddOption("trade_date", true,  true);
        cliParse(args);
        String tradeDate = cliGetOptionValue("trade_date");
        System.out.println(tradeDate);
    }
}
