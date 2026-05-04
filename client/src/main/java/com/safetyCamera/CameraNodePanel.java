package com.safetyCamera;

import org.opencv.core.Mat;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a single camera stream layout running in its own thread,
 * with its own detection engine and mode configuration.
 * 
 * Uses SharedCameraService to retrieve frames to avoid hardware locks.
 */
public class CameraNodePanel extends JPanel {

    private final String cameraName;
    private final String cameraSource;
    private final Runnable onClose;
    private ModeManager.Mode activeMode = ModeManager.Mode.SAFETY_GEAR;

    private final CameraPanel cameraPanel;
    private final ModeSelectorPopup modePopup;
    private final JLabel statusLabel;
    private final JLabel fpsLabel;

    private final DetectionEngine engine;
    
    // Dedicated processor thread to avoid holding up the SharedCameraService broadcast
    private final ExecutorService processorPool = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    private final AtomicLong lastFrameTime = new AtomicLong(System.currentTimeMillis());
    private volatile double currentFps = 0.0;
    
    private final JButton modesBtn;
    private final SharedCameraService.FrameListener frameListener;

    public CameraNodePanel(String cameraName, String cameraSource, Runnable onClose) {
        this.cameraName = cameraName;
        this.cameraSource = cameraSource;
        this.onClose = onClose;
        this.engine = new DetectionEngine(cameraName);

        setLayout(new BorderLayout());
        setBackground(new Color(0x0D0D1A));
        setBorder(BorderFactory.createLineBorder(new Color(0x3A3A5C), 1));

        // Top toolbar
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(0x16213E));
        topBar.setBorder(new EmptyBorder(4, 8, 4, 8));

        JLabel titleLabel = new JLabel(" " + cameraName);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        topBar.add(titleLabel, BorderLayout.WEST);

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightControls.setOpaque(false);

        modePopup = new ModeSelectorPopup(activeMode, mode -> {
            this.activeMode = mode;
            updateStatusBar();
        });

        modesBtn = new JButton("Mode \u25BE");
        modesBtn.setBackground(new Color(0x0F3460));
        modesBtn.setForeground(new Color(0x53C0F0));
        modesBtn.setFocusPainted(false);
        modesBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        modesBtn.setBorder(new EmptyBorder(2, 6, 2, 6));
        modesBtn.addActionListener(e -> {
            modePopup.syncState(activeMode);
            modePopup.show(modesBtn, 0, modesBtn.getHeight() + 2);
        });

        JButton closeBtn = new JButton("\u2716");
        closeBtn.setBackground(new Color(0x4A1122));
        closeBtn.setForeground(new Color(0xEEEEEE));
        closeBtn.setFocusPainted(false);
        closeBtn.setBorder(new EmptyBorder(2, 6, 2, 6));
        closeBtn.addActionListener(e -> shutdown());
        
        rightControls.add(modesBtn);
        rightControls.add(Box.createHorizontalStrut(6));
        rightControls.add(closeBtn);
        topBar.add(rightControls, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);

        // Core camera
        cameraPanel = new CameraPanel();
        add(cameraPanel, BorderLayout.CENTER);

        // Bottom status
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(new Color(0x0A0A1A));
        statusBar.setBorder(new EmptyBorder(4, 8, 4, 8));

        statusLabel = new JLabel();
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));

        fpsLabel = new JLabel("0.0 FPS");
        fpsLabel.setFont(new Font("Segoe UI Mono", Font.PLAIN, 10));
        fpsLabel.setForeground(new Color(0x666688));

        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(fpsLabel, BorderLayout.EAST);
        add(statusBar, BorderLayout.SOUTH);

        updateStatusBar();

        // Attach listener to shared stream
        frameListener = new SharedCameraService.FrameListener() {
            @Override
            public void onFrame(Mat frame) {
                // Drop frames if backend is too slow to avoid memory exhaustion
                if (isProcessing.compareAndSet(false, true)) {
                    processorPool.submit(() -> {
                        try {
                            engine.process(frame, activeMode);
                            cameraPanel.updateFrame(frame);
                            updateFps();
                        } catch (Throwable t) {
                            t.printStackTrace();
                        } finally {
                            frame.release(); // release clone
                            isProcessing.set(false);
                        }
                    });
                } else {
                    frame.release(); // safely drop
                }
            }

            @Override
            public void onError(String message) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("ERROR: " + message);
                    statusLabel.setForeground(Color.RED);
                });
            }
        };

        SharedCameraService.subscribe(cameraSource, frameListener);
    }
    
    private void updateFps() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastFrameTime.getAndSet(now);
        if (elapsed > 0) {
            currentFps = 0.9 * currentFps + 0.1 * (1000.0 / elapsed);
        }
        SwingUtilities.invokeLater(() -> fpsLabel.setText(String.format("%.1f FPS", currentFps)));
    }

    private void updateStatusBar() {
        String text;
        Color col;
        switch (activeMode) {
            case SAFETY_GEAR       -> { text = "Safety Gear";  col = new Color(0xFF9800); }
            case FALLING_DETECTION -> { text = "Falling Det.";   col = new Color(0xF44336); }
            case RESTRICTED_AREA   -> { text = "Restricted";   col = new Color(0x4CAF50); }
            default                -> { text = "OFF";            col = new Color(0x888888); }
        }
        statusLabel.setForeground(col);
        statusLabel.setText(text);
        modesBtn.setText(text);
    }

    public void shutdown() {
        SharedCameraService.unsubscribe(cameraSource, frameListener);
        processorPool.shutdownNow();
        if (onClose != null) {
            SwingUtilities.invokeLater(onClose);
        }
    }
}
