package com.safetyCamera;

import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Modal camera-selection dialog.
 *
 * Probes camera indices 0–9 to find available cameras, presents them
 * in a styled combo-box, and stores the user's choice.
 */
public class CameraSelectDialog extends JDialog {

    private int selectedCameraIndex = -1;

    public CameraSelectDialog(Frame owner) {
        super(owner, "Select Camera Input", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(480, 320);
        setLocationRelativeTo(owner);
        setResizable(false);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(new Color(0x1A1A2E));
        setContentPane(root);

        // ── Header ────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0x0F3460));
        header.setBorder(new EmptyBorder(12, 20, 12, 20));

        JLabel title = new JLabel("\uD83C\uDFA5  Camera Selection");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(new Color(0x53C0F0));
        header.add(title, BorderLayout.WEST);
        root.add(header, BorderLayout.NORTH);

        // ── Centre ────────────────────────────────────────────────
        JPanel centre = new JPanel(new GridBagLayout());
        centre.setBackground(new Color(0x1A1A2E));
        centre.setBorder(new EmptyBorder(20, 30, 10, 30));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill   = GridBagConstraints.HORIZONTAL;

        JLabel scanLabel  = new JLabel("Scanning for available cameras…");
        scanLabel.setForeground(new Color(0xAAAAAA));
        scanLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        centre.add(scanLabel, gbc);

        JLabel selectLabel = new JLabel("Select Camera:");
        selectLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        selectLabel.setForeground(new Color(0xE0E0E0));
        gbc.gridy = 1; gbc.gridwidth = 1;
        centre.add(selectLabel, gbc);

        // Probe cameras on a background thread so the dialog isn't frozen
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        JComboBox<String> cameraCombo = new JComboBox<>(model);
        cameraCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cameraCombo.setBackground(new Color(0x0F3460));
        cameraCombo.setForeground(Color.WHITE);
        gbc.gridx = 1;
        centre.add(cameraCombo, gbc);

        JLabel infoLabel = new JLabel("Tip: Use a camera with 720p or higher for best results.");
        infoLabel.setForeground(new Color(0x888888));
        infoLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        centre.add(infoLabel, gbc);

        root.add(centre, BorderLayout.CENTER);

        // ── Footer ────────────────────────────────────────────────
        JButton startBtn  = new JButton("  Start Surveillance  ");
        JButton cancelBtn = new JButton("Cancel");

        styleButton(startBtn,  new Color(0x4CAF50), Color.WHITE);
        styleButton(cancelBtn, new Color(0x333355), new Color(0xAAAAAA));

        startBtn.setEnabled(false); // enabled once cameras are found

        startBtn.addActionListener(e -> {
            String sel = (String) cameraCombo.getSelectedItem();
            if (sel != null && sel.contains(" ")) {
                try {
                    // Format is "Camera [index] ..."
                    String[] parts = sel.split(" ");
                    if (parts.length >= 2) {
                        selectedCameraIndex = Integer.parseInt(parts[1]);
                    }
                } catch (NumberFormatException nfe) {
                    System.err.println("Failed to parse camera index from: " + sel);
                }
            }
            dispose();
        });

        cancelBtn.addActionListener(e -> {
            selectedCameraIndex = -1;
            dispose();
        });

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 12));
        footer.setBackground(new Color(0x1A1A2E));
        footer.add(cancelBtn);
        footer.add(startBtn);
        root.add(footer, BorderLayout.SOUTH);

        // ── Background camera probe ───────────────────────────────
        SwingWorker<List<Integer>, Void> prober = new SwingWorker<>() {
            @Override
            protected List<Integer> doInBackground() {
                List<Integer> found = new ArrayList<>();
                for (int i = 0; i <= 9; i++) {
                    VideoCapture vc = new VideoCapture();
                    try {
                        if (vc.open(i)) {
                            // Extra check: try to read a frame
                            org.opencv.core.Mat test = new org.opencv.core.Mat();
                            if (vc.read(test) && !test.empty()) {
                                found.add(i);
                            }
                            test.release();
                        }
                    } finally {
                        vc.release();
                    }
                }
                return found;
            }

            @Override
            protected void done() {
                try {
                    List<Integer> cameras = get();
                    if (cameras.isEmpty()) {
                        scanLabel.setText("No cameras detected. Check connections and retry.");
                        scanLabel.setForeground(new Color(0xE94560));
                    } else {
                        scanLabel.setText(cameras.size() + " camera(s) found.");
                        scanLabel.setForeground(new Color(0x4CAF50));
                        for (int idx : cameras) {
                            String label = "Camera " + idx +
                                (idx == 0 ? " – Built-in / Default" : " – USB / External");
                            model.addElement(label);
                        }
                        startBtn.setEnabled(true);
                    }
                } catch (Exception ex) {
                    scanLabel.setText("Error scanning cameras: " + ex.getMessage());
                    scanLabel.setForeground(new Color(0xE94560));
                }
            }
        };
        prober.execute();
    }

    private void styleButton(JButton btn, Color bg, Color fg) {
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(bg.brighter(), 1),
            new EmptyBorder(6, 16, 6, 16)));
    }

    /** Returns the chosen camera index, or -1 if the dialog was cancelled. */
    public int getSelectedCameraIndex() {
        return selectedCameraIndex;
    }
}
