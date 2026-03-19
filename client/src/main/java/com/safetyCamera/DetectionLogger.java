package com.safetyCamera;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Thread-safe CSV detection event logger.
 *
 * Output file: {@code detections.log} in the JVM's working directory.
 * Format:  timestamp, mode, event_type, details
 */
public class DetectionLogger {

    private static final DetectionLogger INSTANCE = new DetectionLogger();
    public  static DetectionLogger getInstance() { return INSTANCE; }

    private static final String LOG_FILE = "detections.log";
    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PrintWriter writer;

    private DetectionLogger() {
        PrintWriter pw = null;
        try {
            boolean fileExists = Files.exists(Paths.get(LOG_FILE));
            pw = new PrintWriter(new BufferedWriter(
                    new FileWriter(LOG_FILE, true)));  // append mode
            if (!fileExists) {
                pw.println("timestamp,mode,event_type,details");
                pw.flush();
            }
        } catch (IOException e) {
            System.err.println("[DetectionLogger] Cannot open log file: " + e.getMessage());
        }
        writer = pw;
    }

    /**
     * Write a single log entry.
     *
     * @param mode      the active mode label
     * @param eventType short event identifier (e.g. "human_detected")
     * @param details   optional details (escape commas if needed)
     */
    public synchronized void log(String mode, String eventType, String details) {
        if (writer == null) return;
        String ts = LocalDateTime.now().format(FMT);
        writer.printf("%s,%s,%s,%s%n", ts, escape(mode), escape(eventType), escape(details));
        writer.flush();
    }

    /** Convenience overload with no details. */
    public void log(String mode, String eventType) {
        log(mode, eventType, "");
    }

    private static String escape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
