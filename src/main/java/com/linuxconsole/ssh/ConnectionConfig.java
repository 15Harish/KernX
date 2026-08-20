package com.linuxconsole.ssh;

/**
 * Holds connection details entered by the user each session.
 * Never logged, never written to disk — matches the original
 * Next.js project's rule that credentials only live in the request.
 */
public class ConnectionConfig {
    private final String host;
    private final int port;
    private final String username;
    private final String password;

    public ConnectionConfig(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
}
