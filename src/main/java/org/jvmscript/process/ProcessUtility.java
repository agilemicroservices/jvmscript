package org.jvmscript.process;


import org.apache.commons.exec.*;

import java.io.ByteArrayOutputStream;

public class ProcessUtility {

    public static CommandLine commandLine;
    public static int processExitValue = 0;
    public static String processOutput = "";

    public static void initializeProcess(String executable) {
        commandLine = new CommandLine(executable);
    }

    public static void addProcessArgument(String argument) {
        commandLine.addArgument(argument);
    }

    public static void executeProcess() throws Exception {
        executeProcess(60);
    }

    public static void executeProcess(int timeout) throws Exception {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);

        DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();
        ExecuteWatchdog watchdog = new ExecuteWatchdog(timeout * 1000);
        Executor executor = new DefaultExecutor();
        executor.setStreamHandler(streamHandler);
        executor.setWatchdog(watchdog);
        executor.execute(commandLine, resultHandler);
        resultHandler.waitFor();
        processExitValue = resultHandler.getExitValue();
        processOutput = outputStream.toString();
        if (processExitValue < 0) throw new Exception("Process Failed " + commandLine.getExecutable());
    }


    public static String getProcessOutput() {
        return processOutput;
    }

    public static int getProcessExitValue() {
        return processExitValue;
    }


    public static void main(String[] args) throws Exception{
        initializeProcess("cmd.exe");

        addProcessArgument("/C");
        addProcessArgument("copy");
        addProcessArgument("README.md");
        addProcessArgument("c:\\dev");
        executeProcess();
        System.out.println("return value = " + getProcessExitValue());
        System.out.println("process Output = " + getProcessOutput());
    }

}
