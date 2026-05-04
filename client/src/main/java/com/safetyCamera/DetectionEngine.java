package com.safetyCamera;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * AI Detection Engine – v2 (Clean Violations API).
 *
 * New JSON contract from Python server:
 * {
 *   "violations": [
 *     { "type": "SAFE",          "person_bbox": [x1,y1,x2,y2], "safe": true,  "details": [] },
 *     { "type": "MISSING_PPE",   "person_bbox": [x1,y1,x2,y2], "safe": false, "details": ["helmet"] },
 *     { "type": "FALL_DETECTED", "person_bbox": [x1,y1,x2,y2], "safe": false, "details": ["Person may have fallen"] }
 *   ]
 * }
 *
 * Rendering rules:
 *   SAFE         → thick GREEN  box  (no label)
 *   MISSING_PPE  → thick RED    box  + label "! Missing: helmet, vest"
 *   FALL_DETECTED→ thick RED    box  + label "⚠ FALL"
 */
public class DetectionEngine {

    private final HttpClient httpClient;
    private final Gson       gson;
    private static final String SERVER_URL = "http://localhost:8000/detect";

    // BGR colours for OpenCV
    private static final Scalar GREEN  = new Scalar( 40, 210,  40);   // safe
    private static final Scalar RED    = new Scalar( 40,  40, 255);   // violation
    private static final Scalar CYAN   = new Scalar(240, 200,  40);   // restricted area
    private static final Scalar WHITE  = new Scalar(240, 240, 240);   // status bar
    private static final Scalar BLACK  = new Scalar(  0,   0,   0);

    // Throttle logging: only log every ONCE per ALERT_LOG_INTERVAL_MS
    private final java.util.Map<String, Long> lastLogTime = new java.util.HashMap<>();
    private static final long ALERT_LOG_INTERVAL_MS = 2000;

    // Restricted area capture directory and throttle
    private static final String CAPTURE_DIR = "captures";
    private static final long CAPTURE_INTERVAL_MS = 5000;
    private long lastCaptureTime = 0;
    private static final DateTimeFormatter CAPTURE_TS_FMT =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final String cameraName;

    public DetectionEngine(String cameraName) {
        this.cameraName = cameraName;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        this.gson = new Gson();
    }

    /**
     * Send {@code frame} to the AI server and draw detection overlays in-place.
     * Called from the camera capture thread.
     */
    public synchronized void process(Mat frame) {
        if (frame.empty()) return;

        // Skip AI entirely when mode is OFF
        ModeManager.Mode mode = ModeManager.getInstance().getActiveMode();
        if (mode == ModeManager.Mode.OFF) {
            drawStatusBar(frame, "AI Detection: OFF", WHITE);
            return;
        }

        try {
            // Encode frame as JPEG
            MatOfByte buf = new MatOfByte();
            Imgcodecs.imencode(".jpg", frame, buf, new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 80));
            byte[] imageBytes = buf.toArray();

            String boundary = "---Boundary" + System.currentTimeMillis();
            byte[] body     = buildMultipart(imageBytes, "frame.jpg", boundary, mode.name());

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(SERVER_URL))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

            HttpResponse<String> resp =
                httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                drawViolations(frame, resp.body(), mode);
            } else {
                drawStatusBar(frame, "Server error " + resp.statusCode(), RED);
            }

        } catch (java.lang.InterruptedException ie) {
            Thread.currentThread().interrupt();   // restore flag, don't print stack
        } catch (Exception e) {
            System.err.println("[DetectionEngine] " + e.getMessage());
            drawStatusBar(frame, "AI SERVER OFFLINE", RED);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────

    private void drawViolations(Mat frame, String json, ModeManager.Mode mode) {
        JsonObject root = gson.fromJson(json, JsonObject.class);
        JsonArray  violations = root.getAsJsonArray("violations");
        if (violations == null) return;

        boolean isRestricted = (mode == ModeManager.Mode.RESTRICTED_AREA);
        int vCount = 0;

        for (JsonElement el : violations) {
            JsonObject v = el.getAsJsonObject();
            String type  = v.get("type").getAsString();
            boolean safe = v.get("safe").getAsBoolean();

            // Restricted Area: ANY person is a breach, override safe→violation
            if (isRestricted) {
                type = "RESTRICTED_AREA_BREACH";
                safe = false;
            }

            JsonArray bboxArr = v.getAsJsonArray("person_bbox");
            double x1 = bboxArr.get(0).getAsDouble();
            double y1 = bboxArr.get(1).getAsDouble();
            double x2 = bboxArr.get(2).getAsDouble();
            double y2 = bboxArr.get(3).getAsDouble();

            Scalar colour = safe ? GREEN : (isRestricted ? CYAN : RED);
            int    thick  = safe ? 2 : 3;

            // Draw bounding box
            Imgproc.rectangle(frame, new Point(x1, y1), new Point(x2, y2), colour, thick);

            // Draw label only for violations
            if (!safe) {
                String label = buildLabel(v, type);
                // Background rectangle for readability
                int fontFace  = Imgproc.FONT_HERSHEY_SIMPLEX;
                double fontSc = 0.55;
                int[]  base   = new int[1];
                Size   ts     = Imgproc.getTextSize(label, fontFace, fontSc, 2, base);
                double lx = x1;
                double ly = Math.max(y1 - 6, ts.height + 4);
                Imgproc.rectangle(frame,
                    new Point(lx, ly - ts.height - 4),
                    new Point(lx + ts.width + 4, ly + 2),
                    BLACK, -1);
                Imgproc.putText(frame, label,
                    new Point(lx + 2, ly - 2),
                    fontFace, fontSc, colour, 2);

                // Log (throttled so we don't flood the side panel)
                throttledLog(type, v);
                vCount++;
            }
        }

        // Capture frame snapshot for restricted area breaches (throttled)
        if (isRestricted && vCount > 0) {
            captureFrame(frame, vCount);
        }

        // Status bar at bottom
        if (isRestricted) {
            String status = violations.size() > 0
                ? vCount + " intruder(s) detected!"
                : "Zone clear – no persons";
            drawStatusBar(frame, "RESTRICTED AREA  |  " + status, vCount > 0 ? RED : GREEN);
        } else {
            String status = violations.size() > 0
                ? (vCount == 0 ? "All workers safe" : vCount + " violation(s) detected")
                : "No persons in frame";
            drawStatusBar(frame, "AI Active  |  " + status, vCount > 0 ? RED : GREEN);
        }
    }

    private String buildLabel(JsonObject v, String type) {
        switch (type) {
            case "MISSING_PPE": {
                JsonArray d = v.getAsJsonArray("details");
                StringBuilder sb = new StringBuilder("! Missing: ");
                for (JsonElement e : d) sb.append(e.getAsString()).append(", ");
                if (sb.length() > 2) sb.setLength(sb.length() - 2);
                return sb.toString();
            }
            case "FALL_DETECTED":          return "\u26a0 FALL DETECTED";
            case "RESTRICTED_AREA_BREACH": return "INTRUDER - RESTRICTED AREA";
            default:                        return type;
        }
    }

    /**
     * Save a timestamped capture of the current frame (with overlays already drawn)
     * to the captures/ directory.  Throttled to one capture per CAPTURE_INTERVAL_MS.
     */
    private void captureFrame(Mat frame, int intruderCount) {
        long now = System.currentTimeMillis();
        if (now - lastCaptureTime < CAPTURE_INTERVAL_MS) return;
        lastCaptureTime = now;

        try {
            File dir = new File(CAPTURE_DIR);
            if (!dir.exists()) dir.mkdirs();

            String ts   = LocalDateTime.now().format(CAPTURE_TS_FMT);
            String name = String.format("breach_%s_%dpersons.jpg", ts, intruderCount);
            File   out  = new File(dir, name);

            Imgcodecs.imwrite(out.getAbsolutePath(), frame);
            System.out.println("[RestrictedArea] Captured: " + out.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[RestrictedArea] Capture failed: " + e.getMessage());
        }
    }

    private void throttledLog(String type, JsonObject v) {
        long now = System.currentTimeMillis();
        long last = lastLogTime.getOrDefault(type, 0L);
        if (now - last < ALERT_LOG_INTERVAL_MS) return;
        lastLogTime.put(type, now);

        String details = v.has("details") ? v.getAsJsonArray("details").toString() : "";
        DetectionLogger.getInstance().log(cameraName, "AI_ALERT", type, details);
    }

    private void drawStatusBar(Mat frame, String text, Scalar colour) {
        int y = frame.rows() - 8;
        Imgproc.rectangle(frame, new Point(0, y - 20), new Point(frame.cols(), y + 4), BLACK, -1);
        Imgproc.putText(frame, text, new Point(8, y),
            Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, colour, 1);
    }

    // ── Multipart builder ─────────────────────────────────────────────────────

    private byte[] buildMultipart(byte[] file, String name, String boundary, String mode) {
        // Part 1: image file
        String header1 = "--" + boundary + "\r\n" +
                         "Content-Disposition: form-data; name=\"file\"; filename=\"" + name + "\"\r\n" +
                         "Content-Type: image/jpeg\r\n\r\n";
        // Part 2: mode text field
        String header2 = "\r\n--" + boundary + "\r\n" +
                         "Content-Disposition: form-data; name=\"mode\"\r\n\r\n";
        String footer  = "\r\n--" + boundary + "--\r\n";

        byte[] h1 = header1.getBytes(StandardCharsets.UTF_8);
        byte[] h2 = header2.getBytes(StandardCharsets.UTF_8);
        byte[] mb = mode.getBytes(StandardCharsets.UTF_8);
        byte[] fb = footer.getBytes(StandardCharsets.UTF_8);

        byte[] body = new byte[h1.length + file.length + h2.length + mb.length + fb.length];
        int pos = 0;
        System.arraycopy(h1,   0, body, pos, h1.length);   pos += h1.length;
        System.arraycopy(file, 0, body, pos, file.length);  pos += file.length;
        System.arraycopy(h2,   0, body, pos, h2.length);   pos += h2.length;
        System.arraycopy(mb,   0, body, pos, mb.length);   pos += mb.length;
        System.arraycopy(fb,   0, body, pos, fb.length);
        return body;
    }
}
