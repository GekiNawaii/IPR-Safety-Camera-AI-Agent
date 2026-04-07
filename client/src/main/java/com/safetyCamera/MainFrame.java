package com.safetyCamera;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.util.concurrent.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Primary application window.
 *
 * Layout:
 *   ┌─────────────────────────────────────────┐
 *   │  [Logo + Title]          [Modes ▾] [●]  │  ← Top toolbar
 *   ├─────────────────────────────────────────┤
 *   │                                         │
 *   │           Live camera feed              │  ← CameraPanel
 *   │          (with detection overlays)      │
 *   │                                         │
 *   ├─────────────────────────────────────────┤
 *   │  🟢 RESTRICTED AREA | Detections: 0    │  ← Status bar
 *   └─────────────────────────────────────────┘
 */
public class MainFrame extends JFrame {

    // ── Constants ─────────────────────────────────────────────────
    private static final int TARGET_FPS = 15;
    private static final long FRAME_INTERVAL_MS = 1000L / TARGET_FPS;

    // ── Components ────────────────────────────────────────────────
    private final CameraPanel    cameraPanel;
    private final JLabel         statusLabel;
    private final JLabel         detectionCountLabel;
    private final JLabel         fpsLabel;
    private final ModeSelectorPopup modePopup;

    // ── Log UI ────────────────────────────────────────────────────
    private final JTextArea      logArea;
    private final JScrollPane    logScrollPane;
    private boolean              isLogVisible = false;

    // ── Camera / detection thread ──────────────────────────────────
    private VideoCapture     capture;    // local camera or video file
    private IpCameraCapture  ipCapture;  // HTTP phone camera (bypasses OpenCV CV_IMAGES bug)
    private final DetectionEngine engine = new DetectionEngine();
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "camera-thread");
            t.setDaemon(true);
            return t;
        });
    private ScheduledFuture<?> captureTask;

    // ── Statistics ─────────────────────────────────────────────────
    private final AtomicInteger totalDetections = new AtomicInteger(0);
    private final AtomicLong    lastFrameTime   = new AtomicLong(System.currentTimeMillis());
    private volatile double     currentFps      = 0.0;

    // ── Constructor ───────────────────────────────────────────────

    public MainFrame(String cameraSource) {
        super("IPR Safety Camera – Surveillance Monitor");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(880, 600));
        setSize(1200, 740);
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { shutdown(); }
        });

        // Root panel
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(new Color(0x0D0D1A));
        setContentPane(root);

        // ── Top toolbar ───────────────────────────────────────────
        modePopup = new ModeSelectorPopup(); // Initialize before buildToolbar
        JPanel toolbar = buildToolbar();
        root.add(toolbar, BorderLayout.NORTH);

        // ── Camera panel ──────────────────────────────────────────
        cameraPanel = new CameraPanel();
        root.add(cameraPanel, BorderLayout.CENTER);

        // ── Status bar ────────────────────────────────────────────
        JPanel statusBar = new JPanel(new BorderLayout(12, 0));
        statusBar.setBackground(new Color(0x0A0A1A));
        statusBar.setBorder(new EmptyBorder(6, 16, 6, 16));

        statusLabel = new JLabel();
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));

        detectionCountLabel = new JLabel();
        detectionCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        detectionCountLabel.setForeground(new Color(0xAAAAAA));

        fpsLabel = new JLabel();
        fpsLabel.setFont(new Font("Segoe UI Mono", Font.PLAIN, 11));
        fpsLabel.setForeground(new Color(0x666688));

        JPanel leftStatus = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        leftStatus.setOpaque(false);
        leftStatus.add(statusLabel);
        leftStatus.add(detectionCountLabel);

        statusBar.add(leftStatus,  BorderLayout.WEST);
        statusBar.add(fpsLabel,    BorderLayout.EAST);
        root.add(statusBar, BorderLayout.SOUTH);

        // ── Log panel ─────────────────────────────────────────────
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(0x16213E));
        logArea.setForeground(new Color(0xE0E0E0));
        logArea.setBorder(new EmptyBorder(8, 8, 8, 8));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        
        logScrollPane = new JScrollPane(logArea);
        logScrollPane.setPreferredSize(new Dimension(320, 0));
        logScrollPane.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(0x0F3460)));
        logScrollPane.setVisible(false);
        root.add(logScrollPane, BorderLayout.EAST);
        
        DetectionLogger.getInstance().addListener((timestamp, mode, eventType, details) -> {
            SwingUtilities.invokeLater(() -> {
                logArea.append(String.format("[%s] %s\n  Mode: %s\n  %s\n\n", timestamp, eventType, mode, details));
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        });

        // ── Listen for mode changes → update status bar ───────────
        ModeManager.getInstance().addPropertyChangeListener(this::onModeChange);
        updateStatusBar();

        // ── Start camera / source ─────────────────────────────────
        boolean ready = false;
        if (cameraSource.startsWith("http")) {
            // HTTP URL: use Java HttpURLConnection fetcher (avoids OpenCV CV_IMAGES bug on Windows)
            ipCapture = new IpCameraCapture(cameraSource);
            ready     = ipCapture.isConnected();
        } else {
            // Local camera index or video file
            capture = new VideoCapture();
            try {
                ready = cameraSource.matches("\\d+")
                    ? capture.open(Integer.parseInt(cameraSource))
                    : capture.open(cameraSource);
            } catch (Exception e) {
                System.err.println("Error opening camera source: " + cameraSource);
            }
        }

        if (!ready) {
            JOptionPane.showMessageDialog(this,
                "Could not open: " + cameraSource + "\n\n" +
                "TIPS:\n" +
                "1. For IP Webcam: ensure phone and PC are on the same Wi-Fi.\n" +
                "2. Open http://<phone-ip>:8080 in browser to verify stream.\n" +
                "3. For camera/video: ensure device is not used by another app.",
                "Camera Error", JOptionPane.ERROR_MESSAGE);
        } else {
            if (capture != null) {
                capture.set(Videoio.CAP_PROP_FRAME_WIDTH,  1280);
                capture.set(Videoio.CAP_PROP_FRAME_HEIGHT,  720);
            }
            startCaptureLoop();
        }
    }


    // ── Toolbar builder ───────────────────────────────────────────

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout(0, 0));
        bar.setBackground(new Color(0x0F1629));
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x0F3460)),
            new EmptyBorder(8, 16, 8, 12)));

        // Left: logo + title
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        JLabel logo = new JLabel("\uD83D\uDED1");
        logo.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 0, 1));
        titleBlock.setOpaque(false);

        JLabel appName = new JLabel("IPR Safety Camera");
        appName.setFont(new Font("Segoe UI", Font.BOLD, 16));
        appName.setForeground(new Color(0xE94560));

        JLabel appSub = new JLabel("Construction Site Surveillance");
        appSub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        appSub.setForeground(new Color(0x888888));

        titleBlock.add(appName);
        titleBlock.add(appSub);
        left.add(logo);
        left.add(titleBlock);

        bar.add(left, BorderLayout.WEST);

        // Right: Modes button + live indicator
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        // Pulsing LIVE indicator
        JLabel liveLabel = new JLabel("● LIVE");
        liveLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        liveLabel.setForeground(new Color(0xE94560));

        // Pulse animation (toggle colour every 800 ms)
        Timer pulse = new Timer(800, null);
        pulse.addActionListener(e2 -> {
            Color current = liveLabel.getForeground();
            liveLabel.setForeground(
                current.equals(new Color(0xE94560))
                    ? new Color(0x441122)
                    : new Color(0xE94560));
        });
        pulse.start();

        // Modes dropdown button
        JButton modesBtn = new JButton("Modes ▾");
        modesBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        modesBtn.setBackground(new Color(0x0F3460));
        modesBtn.setForeground(new Color(0x53C0F0));
        modesBtn.setFocusPainted(false);
        modesBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x0F3460), 1),
            new EmptyBorder(6, 16, 6, 16)));
        modesBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Hover highlight
        modesBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                modesBtn.setBackground(new Color(0x1A4A80));
            }
            @Override public void mouseExited(MouseEvent e) {
                modesBtn.setBackground(new Color(0x0F3460));
            }
        });

        modesBtn.addActionListener(e -> {
            modePopup.show(modesBtn, 0, modesBtn.getHeight() + 2);
        });

        // Logs toggle button
        JButton toggleLogBtn = new JButton("📜 Logs");
        toggleLogBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        toggleLogBtn.setBackground(new Color(0x0F3460));
        toggleLogBtn.setForeground(new Color(0x53C0F0));
        toggleLogBtn.setFocusPainted(false);
        toggleLogBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x0F3460), 1),
            new EmptyBorder(6, 16, 6, 16)));
        toggleLogBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        toggleLogBtn.addActionListener(e -> {
            isLogVisible = !isLogVisible;
            logScrollPane.setVisible(isLogVisible);
            revalidate();
            repaint();
        });

        toggleLogBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { toggleLogBtn.setBackground(new Color(0x1A4A80)); }
            @Override public void mouseExited(MouseEvent e) { toggleLogBtn.setBackground(new Color(0x0F3460)); }
        });

        right.add(liveLabel);
        right.add(Box.createHorizontalStrut(6));

        // Back button – stop capture and return to source selection
        JButton backBtn = new JButton("← Back");
        backBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        backBtn.setBackground(new Color(0x2C2C4A));
        backBtn.setForeground(new Color(0xAAAAAA));
        backBtn.setFocusPainted(false);
        backBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x3A3A5C), 1),
            new EmptyBorder(6, 14, 6, 14)));
        backBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { backBtn.setBackground(new Color(0x3A3A5C)); }
            @Override public void mouseExited(MouseEvent e)  { backBtn.setBackground(new Color(0x2C2C4A)); }
        });
        backBtn.addActionListener(e -> goBack());

        right.add(backBtn);
        right.add(Box.createHorizontalStrut(6));
        right.add(toggleLogBtn);
        right.add(Box.createHorizontalStrut(6));
        right.add(modesBtn);
        bar.add(right, BorderLayout.EAST);

        return bar;
    }

    /** Stop current capture, close this window and re-open the source selection dialog. */
    private void goBack() {
        if (captureTask != null) captureTask.cancel(true);
        if (capture != null && capture.isOpened()) capture.release();
        if (ipCapture != null) ipCapture.release();
        dispose();
        SwingUtilities.invokeLater(() -> {
            CameraSelectDialog dialog = new CameraSelectDialog(null);
            dialog.setVisible(true);
            String source = dialog.getCameraSource();
            if (source != null) new MainFrame(source).setVisible(true);
        });
    }



    // ── Camera capture loop ───────────────────────────────────────

    private void startCaptureLoop() {
        captureTask = scheduler.scheduleAtFixedRate(
            this::captureAndProcess,
            0, FRAME_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void captureAndProcess() {
        Mat frame;

        if (ipCapture != null) {
            // HTTP-based IP camera
            frame = ipCapture.readFrame();
            if (frame == null || frame.empty()) return;
        } else {
            // Local camera or video file via OpenCV VideoCapture
            if (capture == null || !capture.isOpened()) return;
            frame = new Mat();
            if (!capture.read(frame) || frame.empty()) {
                frame.release();
                return;
            }
        }

        // Run detection engine (modifies frame in-place)
        engine.process(frame);

        // Push frame to display
        cameraPanel.updateFrame(frame);
        frame.release();

        // FPS calculation
        long now     = System.currentTimeMillis();
        long elapsed = now - lastFrameTime.getAndSet(now);
        if (elapsed > 0) {
            currentFps = 0.9 * currentFps + 0.1 * (1000.0 / elapsed);
        }

        // Update status bar (rate-limited to every 0.5 s to avoid EDT flooding)
        SwingUtilities.invokeLater(() -> {
            fpsLabel.setText(String.format("%.1f FPS", currentFps));
        });
    }

    // ── Mode change handler ───────────────────────────────────────

    private void onModeChange(PropertyChangeEvent evt) {
        SwingUtilities.invokeLater(this::updateStatusBar);
    }

    private void updateStatusBar() {
        ModeManager.Mode mode = ModeManager.getInstance().getActiveMode();
        String text;
        Color  col;
        switch (mode) {
            case SAFETY_GEAR       -> { text = "🛡  Safety Gear Recognition";  col = new Color(0xFF9800); }
            case FALLING_DETECTION -> { text = "⚠  Falling Detection";          col = new Color(0xF44336); }
            case RESTRICTED_AREA   -> { text = "🟢 Restricted Area Detection";  col = new Color(0x4CAF50); }
            default                -> { text = "⬜  AI Detection: OFF";          col = new Color(0x888888); }
        }
        statusLabel.setForeground(col);
        statusLabel.setText(text);
        detectionCountLabel.setText("  |  Log: detections.log");
    }

    // ── Shutdown ──────────────────────────────────────────────────

    private void shutdown() {
        // Force exit immediately. 
        // Calling capture.release() on OpenCV Windows MSMF can deadlock the EDT.
        // Furthermore, System.exit(0) can deadlock in openpnp's OpenCV native cleanup hook.
        // The OS will automatically release webcam handles, threads, and memory.
        Runtime.getRuntime().halt(0);
    }
}
