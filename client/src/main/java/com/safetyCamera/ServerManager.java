package com.safetyCamera;

import java.io.File;

public class ServerManager {
    private static Process serverProcess;
    private static volatile boolean running = true;

    public static void start() {
        Thread watchdog = new Thread(() -> {
            while (running) {
                try {
                    File serverBat = new File("../server/start_server.bat").getCanonicalFile();
                    if (!serverBat.exists()) {
                        System.err.println("[ServerManager] Critical: Server batch file not found at " + serverBat.getAbsolutePath());
                        return; // Cannot start
                    }

                    System.out.println("[ServerManager] Launching backend server...");
                    ProcessBuilder pb = new ProcessBuilder(serverBat.getAbsolutePath());
                    pb.directory(serverBat.getParentFile()); // Run in server directory
                    pb.redirectErrorStream(true);
                    
                    // Log server output to a debug file to avoid spamming the console
                    File logFile = new File("server_backend.log");
                    pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));

                    serverProcess = pb.start();
                    System.out.println("[ServerManager] Backend server started successfully.");

                    // Block until the server process exits
                    serverProcess.waitFor();

                    if (running) {
                        System.err.println("[ServerManager] Warning: Backend server crashed or exited. Restarting in 3 seconds...");
                        Thread.sleep(3000);
                    }
                } catch (Exception e) {
                    System.err.println("[ServerManager] Error in watchdog thread: " + e.getMessage());
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
            // Kill cmd.exe and its child python.exe processes forcibly
            serverProcess.descendants().forEach(ProcessHandle::destroyForcibly);
            serverProcess.destroyForcibly();
            serverProcess = null;
        }
    }
}
