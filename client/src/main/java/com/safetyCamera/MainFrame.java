package com.safetyCamera;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * Primary application window.
 *
 * Modified to support Multi-Camera Multi-threading:
 * The main area is a dynamic GridLayout that instantiates and holds 
 * multiple CameraNodePanel instances. 
 */
public class MainFrame extends JFrame {

    // ── Components ────────────────────────────────────────────────
    private final JPanel         gridPanel;
    private final JTextArea      logArea;
    private final JScrollPane    logScrollPane;
    private boolean              isLogVisible = false;
    private JButton              serverBtn;

    // ── Constructor ───────────────────────────────────────────────

    public MainFrame(String initialCameraName, String initialCameraSource) {
        super("IPR Safety Camera – Multi-Camera Monitor");
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

        // ── Camera Grid ───────────────────────────────────────────
        gridPanel = new JPanel();
        gridPanel.setBackground(new Color(0x0D0D1A));
        gridPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        root.add(gridPanel, BorderLayout.CENTER);

        // ── Status bar (simplified) ───────────────────────────────
        JPanel statusBar = new JPanel(new BorderLayout(12, 0));
        statusBar.setBackground(new Color(0x0A0A1A));
        statusBar.setBorder(new EmptyBorder(6, 16, 6, 16));

        JLabel infoLabel = new JLabel("All logs are saved to detections.log");
        infoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        infoLabel.setForeground(new Color(0xAAAAAA));

        statusBar.add(infoLabel,  BorderLayout.WEST);
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
        
        DetectionLogger.getInstance().addListener((timestamp, camera, mode, eventType, details) -> {
            SwingUtilities.invokeLater(() -> {
                logArea.append(String.format("[%s] %s\n  Camera: %s | Mode: %s\n  %s\n\n", timestamp, eventType, camera, mode, details));
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        });

        // ── Start initial camera ──────────────────────────────────
        if (initialCameraSource != null && initialCameraName != null) {
            addCameraNode(initialCameraName, initialCameraSource);
        }

        // Hide the console window now that the GUI is up
        ServerManager.hideConsoleWindow();
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

        JLabel appSub = new JLabel("Multi-Camera Surveillance Dashboard");
        appSub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        appSub.setForeground(new Color(0x888888));

        titleBlock.add(appName);
        titleBlock.add(appSub);
        left.add(logo);
        left.add(titleBlock);

        bar.add(left, BorderLayout.WEST);

        // Right buttons
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        // Add Camera Button
        JButton addCamBtn = new JButton("Add Camera");
        addCamBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        addCamBtn.setBackground(new Color(0x0F3460));
        addCamBtn.setForeground(new Color(0x53C0F0));
        addCamBtn.setFocusPainted(false);
        addCamBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x0F3460), 1),
            new EmptyBorder(6, 16, 6, 16)));
        addCamBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addCamBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { addCamBtn.setBackground(new Color(0x1A4A80)); }
            @Override public void mouseExited(MouseEvent e) { addCamBtn.setBackground(new Color(0x0F3460)); }
        });

        addCamBtn.addActionListener(e -> {
            CameraSelectDialog dialog = new CameraSelectDialog(this);
            dialog.setVisible(true);
            String source = dialog.getCameraSource();
            String name = dialog.getCameraName();
            if (source != null && name != null) {
                addCameraNode(name, source);
            }
        });

        // Logs toggle button
        JButton toggleLogBtn = new JButton("Logs");
        toggleLogBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        toggleLogBtn.setBackground(new Color(0x2C2C4A));
        toggleLogBtn.setForeground(new Color(0xAAAAAA));
        toggleLogBtn.setFocusPainted(false);
        toggleLogBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x3A3A5C), 1),
            new EmptyBorder(6, 14, 6, 14)));
        toggleLogBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        toggleLogBtn.addActionListener(e -> {
            isLogVisible = !isLogVisible;
            logScrollPane.setVisible(isLogVisible);
            revalidate();
            repaint();
        });

        right.add(toggleLogBtn);
        right.add(Box.createHorizontalStrut(6));

        // AI Server manual control button
        serverBtn = new JButton("AI Server");
        serverBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        serverBtn.setBackground(new Color(0x1B5E20));
        serverBtn.setForeground(new Color(0xA5D6A7));
        serverBtn.setFocusPainted(false);
        serverBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x2E7D32), 1),
            new EmptyBorder(6, 14, 6, 14)));
        serverBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        serverBtn.setToolTipText("Manually start/restart the AI backend server");
        serverBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (serverBtn.isEnabled()) serverBtn.setBackground(new Color(0x2E7D32));
            }
            @Override public void mouseExited(MouseEvent e) {
                if (serverBtn.isEnabled()) serverBtn.setBackground(new Color(0x1B5E20));
            }
        });
        serverBtn.addActionListener(e -> handleServerRestart());

        right.add(serverBtn);
        right.add(Box.createHorizontalStrut(6));
        right.add(addCamBtn);

        bar.add(right, BorderLayout.EAST);

        return bar;
    }

    private void addCameraNode(String name, String source) {
        CameraNodePanel[] panelRef = new CameraNodePanel[1];
        panelRef[0] = new CameraNodePanel(name, source, () -> {
            gridPanel.remove(panelRef[0]);
            updateGridLayout();
        });
        gridPanel.add(panelRef[0]);
        updateGridLayout();
    }

    private void updateGridLayout() {
        int count = gridPanel.getComponentCount();
        if (count == 0) {
            gridPanel.setLayout(new BorderLayout());
            gridPanel.removeAll(); // Clear if somehow empty but layout still there
            gridPanel.revalidate();
            gridPanel.repaint();
            return;
        }

        int cols = (int) Math.ceil(Math.sqrt(count));
        int rows = (int) Math.ceil((double) count / cols);
        gridPanel.setLayout(new GridLayout(rows, cols, 8, 8));
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    // ── AI Server restart logic ────────────────────────────────────

    /**
     * Handle the AI Server button press.
     * Runs the entire check → terminate → relaunch flow on a background thread
     * so the EDT stays responsive. Button text and state update at each step.
     */
    private void handleServerRestart() {
        serverBtn.setEnabled(false);
        serverBtn.setBackground(new Color(0x424242));
        serverBtn.setForeground(new Color(0x9E9E9E));
        serverBtn.setText("Checking…");

        new Thread(() -> {
            try {
                // Step 1: Check for existing server instances on port 8000
                List<Long> pids = ServerManager.findExistingServerPids();
                boolean hadExisting = !pids.isEmpty() || ServerManager.isManagedServerRunning();

                if (hadExisting) {
                    // Step 2a: Terminate existing instances
                    SwingUtilities.invokeLater(() -> serverBtn.setText("Terminating…"));
                    ServerManager.terminateProcesses(pids);
                    // Give the OS a moment to release the port
                    Thread.sleep(1500);

                    // Step 2b: Relaunch
                    SwingUtilities.invokeLater(() -> serverBtn.setText("Relaunching…"));
                } else {
                    // No existing instance – just launch
                    SwingUtilities.invokeLater(() -> serverBtn.setText("Launching…"));
                }

                // Step 3: Start the server
                ServerManager.start();

                // Wait a bit for the server to bind the port
                Thread.sleep(3000);

                // Step 4: Verify
                List<Long> newPids = ServerManager.findExistingServerPids();
                boolean success = !newPids.isEmpty() || ServerManager.isManagedServerRunning();

                SwingUtilities.invokeLater(() -> {
                    if (success) {
                        serverBtn.setText("Server Running");
                        serverBtn.setBackground(new Color(0x1B5E20));
                        serverBtn.setForeground(new Color(0xA5D6A7));
                    } else {
                        serverBtn.setText("Launch Failed");
                        serverBtn.setBackground(new Color(0xBF360C));
                        serverBtn.setForeground(new Color(0xFFCCBC));
                    }
                    serverBtn.setEnabled(true);

                    // Reset button text after 4 seconds
                    Timer resetTimer = new Timer(4000, ev -> {
                        serverBtn.setText("AI Server");
                        serverBtn.setBackground(new Color(0x1B5E20));
                        serverBtn.setForeground(new Color(0xA5D6A7));
                    });
                    resetTimer.setRepeats(false);
                    resetTimer.start();
                });

            } catch (Exception ex) {
                System.err.println("[MainFrame] Server restart error: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    serverBtn.setText("Error");
                    serverBtn.setBackground(new Color(0xBF360C));
                    serverBtn.setForeground(new Color(0xFFCCBC));
                    serverBtn.setEnabled(true);

                    Timer resetTimer = new Timer(4000, ev -> {
                        serverBtn.setText("AI Server");
                        serverBtn.setBackground(new Color(0x1B5E20));
                        serverBtn.setForeground(new Color(0xA5D6A7));
                    });
                    resetTimer.setRepeats(false);
                    resetTimer.start();
                });
            }
        }, "ServerRestartThread").start();
    }

    // ── Shutdown ──────────────────────────────────────────────────

    private void shutdown() {
        for (Component c : gridPanel.getComponents()) {
            if (c instanceof CameraNodePanel) {
                ((CameraNodePanel) c).shutdown();
            }
        }
        Runtime.getRuntime().halt(0);
    }
}
