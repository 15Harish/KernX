package com.linuxconsole.ssh;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Equivalent of lib/ssh/sshService.ts from the original Next.js project.
 * The ONLY class that knows how a command actually runs.
 * Switches between mock mode and real SSH (via JSch) based on MOCK_MODE.
 *
 * To go live with real SSH: set MOCK_MODE = false. Nothing else in the
 * UI layer needs to change (same contract as the original design).
 */
public class SshService {

    /** Mirrors SSH_MOCK_MODE from the Next.js .env.local */
    public static boolean MOCK_MODE = true;

    /** Mirrors SSH_CONNECT_TIMEOUT_MS */
    public static int CONNECT_TIMEOUT_MS = 12000;

    /** Mirrors SSH_COMMAND_TIMEOUT_MS */
    public static int COMMAND_TIMEOUT_MS = 30000;

    public CommandResult executeCommand(ConnectionConfig config, String command) {
        if (MOCK_MODE) {
            return runMockCommand(command);
        }
        return runSshCommand(config, command);
    }

    // ---------------------------------------------------------------
    // Mock mode — no server needed, useful for building/testing the UI
    // ---------------------------------------------------------------
    private CommandResult runMockCommand(String command) {
        String cmd = command.trim();
        String output;

        if (cmd.equals("df -h")) {
            output = "Filesystem      Size  Used Avail Use% Mounted on\n"
                   + "/dev/sda1        50G   18G   30G  38% /\n"
                   + "tmpfs           3.9G     0  3.9G   0% /dev/shm";
        } else if (cmd.equals("free -m")) {
            output = "              total        used        free      shared  buff/cache   available\n"
                   + "Mem:           7982        1904        3210         102        2867        5722\n"
                   + "Swap:          2048           0        2048";
        } else if (cmd.equals("uptime")) {
            output = " 14:32:01 up 12 days,  3:41,  2 users,  load average: 0.15, 0.22, 0.19";
        } else if (cmd.equals("whoami")) {
            output = "mockuser";
        } else if (cmd.equals("uname -a")) {
            output = "Linux mock-host 5.15.0-generic #1 SMP x86_64 GNU/Linux";
        } else if (cmd.equals("ps aux")) {
            output = "USER   PID  %CPU %MEM  COMMAND\n"
                   + "root     1   0.0  0.1  /sbin/init\n"
                   + "mock  1042   0.3  1.2  sshd: mockuser@pts/0";
        } else {
            output = "(mock) executed: " + cmd;
        }

        return CommandResult.ok(output, 0);
    }

    // ---------------------------------------------------------------
    // Real SSH mode via JSch
    // ---------------------------------------------------------------
    private CommandResult runSshCommand(ConnectionConfig config, String command) {
        Session session = null;
        ChannelExec channel = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
            session.setPassword(config.getPassword());

            // NOTE: accepts any host key, same trade-off as the original project's
            // hostVerifier — fine for learning/dev, not for production.
            session.setConfig("StrictHostKeyChecking", "no");

            session.connect(CONNECT_TIMEOUT_MS);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
            ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
            channel.setOutputStream(outBuffer);
            channel.setErrStream(errBuffer);

            InputStream in = channel.getInputStream();
            channel.connect(CONNECT_TIMEOUT_MS);

            long startTime = System.currentTimeMillis();
            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) break;
                    outBuffer.write(tmp, 0, i);
                }
                if (channel.isClosed()) {
                    if (in.available() > 0) continue;
                    break;
                }
                if (System.currentTimeMillis() - startTime > COMMAND_TIMEOUT_MS) {
                    return CommandResult.failure("Timed out while waiting for command output");
                }
                Thread.sleep(50);
            }

            int exitCode = channel.getExitStatus();
            String output = outBuffer.toString();
            String errOutput = errBuffer.toString();

            if (!errOutput.isBlank()) {
                output = output.isBlank() ? errOutput : output + "\n" + errOutput;
            }

            return CommandResult.ok(output, exitCode);

        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.toString();
            return CommandResult.failure(message);
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }
}
