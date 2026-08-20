package com.linuxconsole.ssh;

/**
 * Equivalent of the Next.js project's CommandResult / ExecuteCommandInput contract
 * (lib/ssh/types.ts). This is the shape passed back from SshService to the UI layer.
 */
public class CommandResult {

    private final boolean success;
    private final String output;
    private final String errorMessage;
    private final int exitCode;

    private CommandResult(boolean success, String output, String errorMessage, int exitCode) {
        this.success = success;
        this.output = output;
        this.errorMessage = errorMessage;
        this.exitCode = exitCode;
    }

    public static CommandResult ok(String output, int exitCode) {
        return new CommandResult(true, output, null, exitCode);
    }

    public static CommandResult failure(String errorMessage) {
        return new CommandResult(false, "", errorMessage, -1);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOutput() {
        return output;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getExitCode() {
        return exitCode;
    }
}
