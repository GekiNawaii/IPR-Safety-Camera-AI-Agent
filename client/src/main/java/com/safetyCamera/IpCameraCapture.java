package com.safetyCamera;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reads frames from an IP Webcam app via HTTP (bypasses OpenCV VideoCapture).
 *
 * Supports two modes:
 *   - Shot mode:  polls GET /shot.jpg repeatedly → reliable, low latency
 *   - MJPEG mode: reads MJPEG boundary stream (not used as primary fallback)
 *
 * Usage:
 *   IpCameraCapture cam = new IpCameraCapture("http://192.168.x.x:8080");
 *   if (cam.isConnected()) { Mat frame = cam.readFrame(); }
 *   cam.release();
 */
public class IpCameraCapture {

    private final String baseUrl;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private boolean connected = false;
    private int timeoutMs = 3000;

    public IpCameraCapture(String url) {
        // Normalize: strip /video or /shot.jpg suffix, keep just the base
        String base = url.trim();
        if (base.endsWith("/video"))     base = base.substring(0, base.length() - 6);
        if (base.endsWith("/shot.jpg"))  base = base.substring(0, base.length() - 9);
        if (base.endsWith("/"))          base = base.substring(0, base.length() - 1);
        this.baseUrl = base;

        // Test connectivity
        Mat test = readFrame();
        this.connected = (test != null && !test.empty());
        if (test != null) test.release();
    }

    /** @return true if the initial connection test succeeded. */
    public boolean isConnected() { return connected; }

    /**
     * Fetch one JPEG frame from /shot.jpg and decode to Mat.
     * Returns null on failure.
     */
    public Mat readFrame() {
        if (!running.get()) return null;
        try {
            URL url = new URL(baseUrl + "/shot.jpg");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("User-Agent", "IPR-SafetyCamera/1.0");
            conn.setRequestProperty("Accept", "image/jpeg");
            conn.connect();

            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return null;
            }

            // Read response bytes
            try (InputStream is = conn.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(65536);
                byte[] chunk = new byte[8192];
                int n;
                while ((n = is.read(chunk)) != -1) buffer.write(chunk, 0, n);
                byte[] jpegBytes = buffer.toByteArray();

                // Decode JPEG → Mat
                MatOfByte mob = new MatOfByte(jpegBytes);
                Mat frame = Imgcodecs.imdecode(mob, Imgcodecs.IMREAD_COLOR);
                mob.release();
                conn.disconnect();
                return (frame != null && !frame.empty()) ? frame : null;
            }
        } catch (Exception e) {
            System.err.println("[IpCameraCapture] Error fetching frame: " + e.getMessage());
            return null;
        }
    }

    /** Stop further frame reads. */
    public void release() {
        running.set(false);
    }
}
