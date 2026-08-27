package com.linuxconsole.ssh;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class SshService {

    public static boolean MOCK_MODE = true;
    public static int CONNECT_TIMEOUT_MS = 12000;
    public static int COMMAND_TIMEOUT_MS = 30000;

    public CommandResult executeCommand(ConnectionConfig config, String command) {
        if (MOCK_MODE) {
            return runMockCommand(command);
        }
        return runSshCommand(config, command);
    }

    private CommandResult runMockCommand(String command) {
        String cmd = command.trim();
        String output;

        if (cmd.contains("disp+work")) {
            output = "disp+work=>sapparam(1c): No Profile used.\n"
                   + "disp+work=>sapparam: SAPSYSTEMNAME neither in Profile nor in Commandline\n\n"
                   + "----------------------\n"
                   + "disp+work information\n"
                   + "----------------------\n\n"
                   + "kernel release                720\n"
                   + "kernel make variant            720_REL\n"
                   + "compiled on                    NT 5.2 3790 S x86 MS VC++ 14.00 for NTAMD64\n"
                   + "compiled for                   64 BIT\n"
                   + "compilation mode               UNICODE\n"
                   + "compile time                   Dec 1 2011 23:12:20\n"
                   + "update level                   0\n"
                   + "patch number                   114\n"
                   + "source id                      0.114\n"
                   + "version number                  5.15.0-mock";
        } else if (cmd.equals("df -h")) {
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

    private CommandResult runSshCommand(ConnectionConfig config, String command) {
        Session session = null;
        ChannelExec channel = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
            session.setPassword(config.getPassword());

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

            // If the caller requested disp+work, also fetch a system/kernel version and append it
            String cmdTrim = command == null ? "" : command.trim();
            if (cmdTrim.contains("disp+work") && session != null && session.isConnected()) {
                ChannelExec channel2 = null;
                try {
                    channel2 = (ChannelExec) session.openChannel("exec");
                    channel2.setCommand("uname -r");

                    ByteArrayOutputStream out2 = new ByteArrayOutputStream();
                    ByteArrayOutputStream err2 = new ByteArrayOutputStream();
                    channel2.setOutputStream(out2);
                    channel2.setErrStream(err2);

                    InputStream in2 = channel2.getInputStream();
                    channel2.connect(CONNECT_TIMEOUT_MS);

                    long startTime2 = System.currentTimeMillis();
                    byte[] tmp2 = new byte[1024];
                    while (true) {
                        while (in2.available() > 0) {
                            int i = in2.read(tmp2, 0, 1024);
                            if (i < 0) break;
                            out2.write(tmp2, 0, i);
                        }
                        if (channel2.isClosed()) {
                            if (in2.available() > 0) continue;
                            break;
                        }
                        if (System.currentTimeMillis() - startTime2 > COMMAND_TIMEOUT_MS) {
                            break;
                        }
                        Thread.sleep(50);
                    }

                    String ver = out2.toString().trim();
                    String err2Str = err2.toString().trim();
                    if (!err2Str.isBlank() && ver.isBlank()) ver = err2Str;
                    if (!ver.isBlank()) {
                        output = output + "\nversion number                  " + ver;
                    }
                } catch (Exception ignored) {
                    // don't fail the whole command if the extra probe fails
                } finally {
                    if (channel2 != null && channel2.isConnected()) channel2.disconnect();
                }
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