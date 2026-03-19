package com.safetyCamera;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.HOGDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline detection engine.
 *
 * <b>Human detection:</b> OpenCV HOGDescriptor + default SVM pedestrian model.
 *   Runs entirely on CPU, no network or AI API required.
 *
 * <b>Motion detection:</b> Frame-differencing (absolute pixel diff + threshold).
 *
 * Detection results are drawn directly onto the supplied Mat frame.
 */
public class DetectionEngine {

    // ── HOG people detector ───────────────────────────────────────
    private final HOGDescriptor hog;

    // ── Motion detection state ─────────────────────────────────────
    private Mat previousFrame = null;

    // ── Colours (BGR for OpenCV) ───────────────────────────────────
    private static final Scalar COLOR_GREEN  = new Scalar( 50, 220,  50);   // person box
    private static final Scalar COLOR_ORANGE = new Scalar( 50, 165, 255);   // safety gear stub
    private static final Scalar COLOR_RED    = new Scalar( 50,  50, 255);   // fall stub
    private static final Scalar COLOR_MOTION = new Scalar(200, 200,  50);   // motion indicator

    // ── HOG tuning parameters ─────────────────────────────────────
    /** Hit-threshold for the SVM. Lower = more detections, more false positives. */
    private static final double HIT_THRESHOLD    = 0.0;
    /** Sliding-window stride. Smaller = more thorough but slower. */
    private static final Size   WIN_STRIDE       = new Size(8, 8);
    /** Padding around each window. */
    private static final Size   PADDING          = new Size(4, 4);
    /** Scale factor between pyramid levels. */
    private static final double SCALE            = 1.05;
    /** Minimum group count to suppress duplicates. */
    private static final double FINAL_THRESHOLD  = 2.0;

    // ── Motion detection parameters ───────────────────────────────
    /** Pixel-difference threshold (0-255) to count as "changed". */
    private static final int    MOTION_THRESHOLD  = 25;
    /** Minimum changed-pixel fraction (0-1) to trigger motion event. */
    private static final double MOTION_MIN_RATIO  = 0.005; // 0.5 %

    private static final int LOG_COOLDOWN_FRAMES = 30; // ~2 s at 15 fps
    private int humanLogCooldown  = 0;
    private int motionLogCooldown = 0;

    public DetectionEngine() {
        hog = new HOGDescriptor();
        hog.setSVMDetector(HOGDescriptor.getDefaultPeopleDetector());
    }

    // ── Public API ────────────────────────────────────────────────

    /**
     * Process one video frame according to currently active modes.
     * Modifies {@code frame} in-place (draws overlays).
     *
     * @param frame the camera frame (BGR, original size)
     */
    public synchronized void process(Mat frame) {
        ModeManager mm = ModeManager.getInstance();

        if (mm.isActive(ModeManager.Mode.RESTRICTED_AREA)) {
            processRestrictedArea(frame);
        }
        if (mm.isActive(ModeManager.Mode.SAFETY_GEAR)) {
            processSafetyGearStub(frame);
        }
        if (mm.isActive(ModeManager.Mode.FALLING_DETECTION)) {
            processFallingStub(frame);
        }

        // Always run motion detection in the background for logging
        detectMotion(frame);
    }

    // ── Restricted Area Detection ─────────────────────────────────

    private void processRestrictedArea(Mat frame) {
        List<Rect> people = detectPeople(frame);

        // Draw green bounding boxes
        for (Rect r : people) {
            // Slightly expand box for visual comfort
            Rect expanded = new Rect(
                Math.max(0, r.x - 4), Math.max(0, r.y - 4),
                Math.min(r.width  + 8, frame.width()  - r.x),
                Math.min(r.height + 8, frame.height() - r.y));
            Imgproc.rectangle(frame, expanded.tl(), expanded.br(), COLOR_GREEN, 2);
            Imgproc.putText(frame, "PERSON", new Point(expanded.x, expanded.y - 6),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.55, COLOR_GREEN, 2);
        }

        // Log if people found, with cooldown to avoid flooding the log
        if (!people.isEmpty()) {
            if (humanLogCooldown <= 0) {
                DetectionLogger.getInstance().log(
                    ModeManager.Mode.RESTRICTED_AREA.getDisplayName(),
                    "human_detected",
                    "count=" + people.size() + " " + rectsToString(people));
                humanLogCooldown = LOG_COOLDOWN_FRAMES;
            }
        }
        if (humanLogCooldown > 0) humanLogCooldown--;

        // Mode label overlay
        drawModeLabel(frame, "RESTRICTED AREA DETECTION", COLOR_GREEN, 0);
    }

    // ── Safety Gear Recognition (stub) ────────────────────────────

    private void processSafetyGearStub(Mat frame) {
        drawModeLabel(frame, "SAFETY GEAR RECOGNITION  [Coming Soon]", COLOR_ORANGE,
            mm.isActive(ModeManager.Mode.SAFETY_GEAR) ? 1 : 0);
        drawStubOverlay(frame, "Safety Gear Recognition – Full detection coming soon",
            COLOR_ORANGE);
    }

    // ── Falling Detection (stub) ──────────────────────────────────

    private void processFallingStub(Mat frame) {
        drawModeLabel(frame, "FALLING DETECTION  [Coming Soon]", COLOR_RED,
            mm.isActive(ModeManager.Mode.FALLING_DETECTION) ? 2 : 0);
        drawStubOverlay(frame, "Falling Detection – Full detection coming soon",
            COLOR_RED);
    }

    // ── Motion detection (always-on, logging only) ────────────────

    private void detectMotion(Mat frame) {
        Mat grey = new Mat();
        Imgproc.cvtColor(frame, grey, Imgproc.COLOR_BGR2GRAY);
        Imgproc.GaussianBlur(grey, grey, new Size(21, 21), 0);

        if (previousFrame == null) {
            previousFrame = grey;
            return;
        }

        Mat diff = new Mat();
        Core.absdiff(previousFrame, grey, diff);
        Imgproc.threshold(diff, diff, MOTION_THRESHOLD, 255, Imgproc.THRESH_BINARY);

        int changedPixels = Core.countNonZero(diff);
        double totalPixels = frame.width() * (double) frame.height();
        double ratio = changedPixels / totalPixels;

        if (ratio > MOTION_MIN_RATIO) {
            // Draw subtle motion indicator in corner
            Imgproc.circle(frame, new Point(frame.width() - 20, 20), 8, COLOR_MOTION, -1);
            Imgproc.putText(frame, "MOTION", new Point(frame.width() - 80, 25),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.45, COLOR_MOTION, 1);

            if (motionLogCooldown <= 0) {
                DetectionLogger.getInstance().log(
                    "SYSTEM", "motion_detected",
                    String.format("changed_ratio=%.3f", ratio));
                motionLogCooldown = LOG_COOLDOWN_FRAMES;
            }
        }
        if (motionLogCooldown > 0) motionLogCooldown--;

        diff.release();
        previousFrame.release();
        previousFrame = grey;
    }

    // ── HOG pedestrian detection ──────────────────────────────────

    /**
     * Run the HOG people detector on a (possibly downscaled) copy of the frame.
     * Working on a smaller image speeds up detection with acceptable accuracy.
     */
    private List<Rect> detectPeople(Mat frame) {
        // Downscale to speed up HOG
        double scale  = Math.min(1.0, 640.0 / Math.max(frame.width(), frame.height()));
        Mat    resized = new Mat();
        if (scale < 1.0) {
            Imgproc.resize(frame, resized, new Size(frame.width() * scale, frame.height() * scale));
        } else {
            resized = frame;
        }

        MatOfRect locations = new MatOfRect();
        MatOfDouble weights  = new MatOfDouble();

        try {
            hog.detectMultiScale(
                resized, locations, weights,
                HIT_THRESHOLD, WIN_STRIDE, PADDING,
                SCALE, FINAL_THRESHOLD, false);
        } catch (Exception e) {
            System.err.println("[DetectionEngine] HOG error: " + e.getMessage());
        }

        // Scale rectangles back to original frame coordinates
        List<Rect> result = new ArrayList<>();
        for (Rect r : locations.toArray()) {
            result.add(new Rect(
                (int) (r.x / scale),
                (int) (r.y / scale),
                (int) (r.width  / scale),
                (int) (r.height / scale)));
        }

        if (scale < 1.0) resized.release();
        locations.release();
        weights.release();
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────

    private final ModeManager mm = ModeManager.getInstance();

    private void drawModeLabel(Mat frame, String text, Scalar color, int lineIndex) {
        int y = frame.height() - 16 - lineIndex * 22;
        Imgproc.rectangle(frame,
            new Point(0, y - 16),
            new Point(text.length() * 9 + 10, y + 4),
            new Scalar(0, 0, 0), -1);
        Imgproc.putText(frame, text, new Point(5, y),
            Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, color, 1);
    }

    private void drawStubOverlay(Mat frame, String msg, Scalar color) {
        Point centre = new Point((double) frame.width() / 2, (double) frame.height() / 2);
        Imgproc.putText(frame, msg,
            new Point(centre.x - msg.length() * 4, centre.y),
            Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, color, 1);
    }

    private String rectsToString(List<Rect> rects) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rects.size(); i++) {
            Rect r = rects.get(i);
            if (i > 0) sb.append('|');
            sb.append(String.format("(%d,%d,%d,%d)", r.x, r.y, r.width, r.height));
        }
        return sb.toString();
    }
}
