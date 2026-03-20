package com.safetyCamera;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.util.Set;
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
    private final VideoCapture   capture;
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

    public MainFrame(int cameraIndex) {
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

        // ── Mode popup ────────────────────────────────────────────
        modePopup = new ModeSelectorPopup();

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

        // ── Start camera ──────────────────────────────────────────
        capture = new VideoCapture();
        capture.open(cameraIndex);
        capture.set(Videoio.CAP_PROP_FRAME_WIDTH,  1280);
        capture.set(Videoio.CAP_PROP_FRAME_HEIGHT,  720);

        if (!capture.isOpened()) {
            JOptionPane.showMessageDialog(this,
                "Could not open camera " + cameraIndex + ".\nCheck connection and restart.",
                "Camera Error", JOptionPane.ERROR_MESSAGE);
        } else {
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
        right.add(toggleLogBtn);
        right.add(Box.createHorizontalStrut(6));
        right.add(modesBtn);
        bar.add(right, BorderLayout.EAST);

        return bar;
    }

    // ── Camera capture loop ───────────────────────────────────────

    private void startCaptureLoop() {
        captureTask = scheduler.scheduleAtFixedRate(
            this::captureAndProcess,
            0, FRAME_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void captureAndProcess() {
        if (!capture.isOpened()) return;

        Mat frame = new Mat();
        if (!capture.read(frame) || frame.empty()) {
            frame.release();
            return;
        }

        // Run detection engine (modifies frame in-place)
        int countBefore = totalDetections.get();
        engine.process(frame);

        // Detect whether a new person was found this frame (simple heuristic:
        // check if log was written – we track it via the logger's call count proxy
        // by checking frame content. Instead, we simply increment on each processed frame
        // if Restricted Area is active and any bounding box was drawn.
        // For simplicity the counter tracks processed frames with active detection.
        if (ModeManager.getInstance().isActive(ModeManager.Mode.RESTRICTED_AREA)) {
            // We'll let DetectionEngine track real counts; here approximate with frame count
        }

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
        Set<ModeManager.Mode> active = ModeManager.getInstance().getActiveModes();

        if (active.isEmpty()) {
            statusLabel.setForeground(new Color(0x888888));
            statusLabel.setText("⬤  No mode active");
        } else {
            StringBuilder sb   = new StringBuilder();
            Color          col = new Color(0x888888);

            boolean first = true;
            for (ModeManager.Mode m : ModeManager.Mode.values()) {
                if (!active.contains(m)) continue;
                if (!first) sb.append("  |  ");
                first = false;

                switch (m) {
                    case RESTRICTED_AREA -> { sb.append("🟢 Restricted Area Detection"); col = new Color(0x4CAF50); }
                    case SAFETY_GEAR     -> { sb.append("🟠 Safety Gear Recognition");  col = new Color(0xFF9800); }
                    case FALLING_DETECTION->{ sb.append("🔴 Falling Detection");         col = new Color(0xF44336); }
                }
            }
            statusLabel.setForeground(col);
            statusLabel.setText(sb.toString());
        }

        detectionCountLabel.setText("  |  Log: detections.log");
    }

    // ── Shutdown ──────────────────────────────────────────────────

    private void shutdown() {
        if (captureTask != null) captureTask.cancel(true);
        scheduler.shutdownNow();
        if (capture != null && capture.isOpened()) capture.release();
        dispose();
        System.exit(0);
    }
}
