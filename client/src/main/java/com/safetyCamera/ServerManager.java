package com.safetyCamera;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages the Python backend server lifecycle.
 * Launches start_server.bat, monitors it, restarts on crash,
 * and cleanly kills it when the client exits.
 *
 * Server location is resolved in this order:
 *   1. server.path file written by the NSIS installer (absolute path)
 *   2. ../server/ relative to the client directory (development layout)
 */
public class ServerManager {
    private static Process serverProcess;
    private static volatile boolean running = true;

    /**
     * Resolve the server directory.
     * First checks for a "server.path" file in the working directory
     * (written by the installer), then falls back to ../server/.
     */
    private static File resolveServerDir() {
        try {
            // Check for installer-written config
            Path configFile = Paths.get("server.path");
            if (Files.exists(configFile)) {
                String serverPath = Files.readString(configFile).trim();
                if (!serverPath.isEmpty()) {
                    File dir = new File(serverPath);
                    if (dir.isDirectory()) {
                        return dir;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ServerManager] Could not read server.path: " + e.getMessage());
        }

        // Fallback: relative path for development layout
        try {
            return new File("../server").getCanonicalFile();
        } catch (Exception e) {
            return new File("../server").getAbsoluteFile();
        }
    }

    public static void start() {
        Thread watchdog = new Thread(() -> {
            File serverDir = resolveServerDir();
            File serverBat = new File(serverDir, "start_server.bat");

            if (!serverBat.exists()) {
                System.err.println("[ServerManager] Server batch file not found at " + serverBat.getAbsolutePath());
                System.err.println("[ServerManager] Server will not be started. Use the Server shortcut to start it manually.");
                return;
            }

            while (running) {
                try {
                    System.out.println("[ServerManager] Launching backend server from " + serverDir.getAbsolutePath());
                    ProcessBuilder pb = new ProcessBuilder(serverBat.getAbsolutePath());
                    pb.directory(serverDir);
                    pb.redirectErrorStream(true);

                    // Log server output to a file to avoid spamming the console
                    File logFile = new File("server_backend.log");
                    pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));

                    serverProcess = pb.start();
                    System.out.println("[ServerManager] Backend server started (PID " + serverProcess.pid() + ")");

                    // Block until the server process exits
                    serverProcess.waitFor();

                    if (running) {
                        System.err.println("[ServerManager] Backend server exited unexpectedly. Restarting in 3 seconds...");
                        Thread.sleep(3000);
                    }
                } catch (Exception e) {
                    System.err.println("[ServerManager] Error in watchdog: " + e.getMessage());
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                }
            }
        });

        watchdog.setName("ServerWatchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        // Ensure we cleanly kill the server when the client app exits
        Runtime.getRuntime().addShutdownHook(new Thread(ServerManager::stop));
    }

    public static void stop() {
        running = false;
        if (serverProcess != null) {
            System.out.println("[ServerManager] Shutting down backend server...");
            // Kill cmd.exe and its child python.exe processes
            serverProcess.descendants().forEach(ProcessHandle::destroyForcibly);
            serverProcess.destroyForcibly();
            serverProcess = null;
        }
    }
}
