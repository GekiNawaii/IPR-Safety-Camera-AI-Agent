package com.safetyCamera;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ServerManager {
    private static Process serverProcess;
    private static volatile boolean running = true;

    /** Port used by the AI backend server. */
    static final int SERVER_PORT = 8000;

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
        running = true;
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

    // ── Manual restart support ─────────────────────────────────────

    /**
     * Find PIDs of processes listening on the server port (8000).
     * Uses {@code netstat} which is available to regular users.
     *
     * @return list of PIDs bound to port 8000, possibly empty
     */
    public static List<Long> findExistingServerPids() {
        List<Long> pids = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "cmd.exe", "/c", "netstat -ano | findstr :" + SERVER_PORT
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    // Match LISTENING state lines for the exact port
                    if (line.contains("LISTENING") && line.contains(":" + SERVER_PORT)) {
                        // netstat output: proto  local_addr  foreign_addr  state  PID
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 5) {
                            try {
                                long pid = Long.parseLong(parts[parts.length - 1]);
                                if (pid > 0 && !pids.contains(pid)) {
                                    pids.add(pid);
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
            proc.waitFor();
        } catch (Exception e) {
            System.err.println("[ServerManager] Error finding existing server PIDs: " + e.getMessage());
        }
        return pids;
    }

    /**
     * Terminate processes by their PIDs using {@code taskkill}.
     * Does not require admin privileges for processes owned by the same user.
     *
     * @param pids list of PIDs to kill
     * @return true if all kills were attempted (best-effort)
     */
    public static boolean terminateProcesses(List<Long> pids) {
        boolean allOk = true;
        for (long pid : pids) {
            try {
                System.out.println("[ServerManager] Terminating process PID " + pid);
                // Use taskkill /F /T to force-kill the process tree
                ProcessBuilder pb = new ProcessBuilder(
                    "taskkill", "/F", "/T", "/PID", String.valueOf(pid)
                );
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                // Drain output
                try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String l;
                    while ((l = r.readLine()) != null) {
                        System.out.println("[ServerManager] taskkill: " + l);
                    }
                }
                proc.waitFor();
            } catch (Exception e) {
                System.err.println("[ServerManager] Failed to kill PID " + pid + ": " + e.getMessage());
                allOk = false;
            }
        }

        // Also stop our own managed process if running
        if (serverProcess != null) {
            serverProcess.descendants().forEach(ProcessHandle::destroyForcibly);
            serverProcess.destroyForcibly();
            serverProcess = null;
        }
        running = false;
        return allOk;
    }

    /**
     * Check if our managed server process is currently alive.
     */
    public static boolean isManagedServerRunning() {
        return serverProcess != null && serverProcess.isAlive();
    }

    // ── Console visibility (Windows) ──────────────────────────────

    /**
     * Hide the console window of the current Java process.
     * Uses PowerShell to find and hide the console window by PID.
     * Does NOT require administrator privileges.
     */
    public static void hideConsoleWindow() {
        try {
            long pid = ProcessHandle.current().pid();
            // PowerShell script that finds the console window by title match and hides it
            String psScript = String.format(
                "Add-Type @\"\n" +
                "using System;\n" +
                "using System.Runtime.InteropServices;\n" +
                "public class Win32 {\n" +
                "    [DllImport(\"kernel32.dll\")]\n" +
                "    public static extern IntPtr GetConsoleWindow();\n" +
                "    [DllImport(\"user32.dll\")]\n" +
                "    public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);\n" +
                "}\n" +
                "\"@\n" +
                "$hwnd = [Win32]::GetConsoleWindow()\n" +
                "if ($hwnd -ne [IntPtr]::Zero) {\n" +
                "    [Win32]::ShowWindow($hwnd, 0)\n" +
                "}\n"
            );

            ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", psScript
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            // Don't wait – fire and forget
            proc.waitFor();
        } catch (Exception e) {
            System.err.println("[ServerManager] Could not hide console window: " + e.getMessage());
        }
    }
}
