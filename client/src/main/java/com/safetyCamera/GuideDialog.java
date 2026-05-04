package com.safetyCamera;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;

/**
 * Modal startup guide dialog.
 *
 * The "Close" button is DISABLED until the user scrolls to the very bottom
 * of the guide text, ensuring they acknowledge the instructions before use.
 */
public class GuideDialog extends JDialog {

    // ---------------------------------------------------------------
    // Guide content – modify this HTML string to update the guide.
    // ---------------------------------------------------------------
    private static final String GUIDE_HTML = """
        <html>
        <body style='font-family:Segoe UI,Arial,sans-serif; color:#E0E0E0;
                     background:#16213E; padding:14px; font-size:13px;'>

        <h1 style='color:#E94560; margin-bottom:4px;'>
            &#x1F6D1; IPR Safety Camera – User Guide
        </h1>
        <p style='color:#AAAAAA; margin-top:0;'>Construction Site AI Surveillance System &nbsp;|&nbsp; v1.0</p>
        <hr style='border-color:#0F3460;'/>

        <h2 style='color:#53C0F0;'>Overview</h2>
        <p>This application monitors construction sites using a connected camera.
        It can detect humans entering restricted areas, identify missing safety
        gear, and flag potential falling incidents — all processed
        <strong>locally on this device</strong> with no internet connection required.</p>

        <h2 style='color:#53C0F0;'>Getting Started</h2>
        <ol>
          <li>Read this guide fully, then press <strong>Close</strong> to proceed.</li>
          <li>Select the camera you wish to use from the drop-down list.</li>
          <li>The main surveillance window will open with
              <strong>Restricted Area Detection</strong> active by default.</li>
        </ol>

        <h2 style='color:#53C0F0;'>Detection Modes</h2>

        <h3 style='color:#4CAF50;'>&#x1F7E2; Restricted Area Detection  <em>(Exclusive)</em></h3>
        <p>Detects any human presence within the camera's field of view and
        draws a <strong style='color:#4CAF50;'>green bounding box</strong> around each person.
        Every detection is timestamped and saved to <code>detections.log</code>.</p>
        <ul>
          <li>This mode <strong>cannot run together</strong> with the other two modes.</li>
          <li>Enabling it will automatically disable Safety Gear and Falling Detection.</li>
          <li>Enabling any other mode will disable Restricted Area Detection.</li>
        </ul>

        <h3 style='color:#FF9800;'>&#x1F7E0; Safety Gear Recognition</h3>
        <p>Analyses detected workers to determine whether they are wearing the
        required personal protective equipment (PPE) such as hard hats and
        high-visibility vests.  Any worker missing required gear is flagged and
        logged with a snapshot.</p>
        <p><em>Note: Full implementation coming in a future update.</em></p>

        <h3 style='color:#F44336;'>&#x1F534; Falling Detection</h3>
        <p>Monitors body posture and motion patterns to detect sudden falls or
        collapses, automatically raising an on-screen alert and logging the
        incident for review.</p>
        <p><em>Note: Full implementation coming in a future update.</em></p>

        <h2 style='color:#53C0F0;'>Mode Selector</h2>
        <p>Click the <strong>"Modes ▾"</strong> button in the top-right corner of
        the surveillance window to open the mode selector panel. Tick or untick
        each mode checkbox to enable or disable it.</p>

        <h2 style='color:#53C0F0;'>Detection Log</h2>
        <p>All detection events are automatically written to
        <code>detections.log</code> (CSV format) in the application's working
        directory. Each entry includes:</p>
        <ul>
          <li>Timestamp (date and time)</li>
          <li>Active mode</li>
          <li>Event type (human_detected, motion_detected, etc.)</li>
          <li>Additional details (bounding box coordinates, count, etc.)</li>
        </ul>

        <h2 style='color:#53C0F0;'>Performance Tips</h2>
        <ul>
          <li>Use a camera with at least 720p resolution for best detection accuracy.</li>
          <li>Ensure the camera has a clear, unobstructed view of the monitored area.</li>
          <li>Good lighting significantly improves detection reliability.</li>
          <li>Position the camera at a 45–60° downward angle for optimal pedestrian
              detection with the HOG algorithm.</li>
        </ul>

        <h2 style='color:#53C0F0;'>Limitations</h2>
        <ul>
          <li>Detection uses the OpenCV HOG + SVM algorithm (offline, CPU-based).
              Accuracy may be reduced in low-light conditions, heavy occlusion,
              or extreme camera angles.</li>
          <li>Safety Gear Recognition and Falling Detection are currently
              stub implementations.</li>
        </ul>

        <h2 style='color:#53C0F0;'>Privacy &amp; Data</h2>
        <p>No video data is transmitted over the network. All processing and
        logging occur on this local device only.</p>

        <br/>
        <p style='color:#888; font-size:11px; text-align:center;'>
            &mdash; IPR Safety Camera &nbsp;|&nbsp; Scroll to the very bottom to enable the Close button &mdash;
        </p>
        </body>
        </html>
        """;

    // ---------------------------------------------------------------

    private final JButton closeButton;

    public GuideDialog(Frame owner) {
        super(owner, "Welcome – Please Read the User Guide", true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(720, 560);
        setLocationRelativeTo(owner);
        setResizable(true);

        // ── Content pane ──────────────────────────────────────────
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(new Color(0x1A1A2E));
        setContentPane(root);

        // Header banner
        JPanel header = buildHeader();
        root.add(header, BorderLayout.NORTH);

        // Scrollable guide text
        JEditorPane editor = new JEditorPane("text/html", GUIDE_HTML);
        editor.setEditable(false);
        editor.setBackground(new Color(0x16213E));
        editor.setCaretPosition(0);   // start at top

        JScrollPane scrollPane = new JScrollPane(editor);
        scrollPane.setBackground(new Color(0x16213E));
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scrollPane, BorderLayout.CENTER);

        // Footer with Close button
        closeButton = new JButton("Close");
        closeButton.setEnabled(false);
        closeButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        closeButton.setBackground(new Color(0x0F3460));
        closeButton.setForeground(Color.WHITE);
        closeButton.setFocusPainted(false);
        closeButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x53C0F0), 1),
            new EmptyBorder(6, 28, 6, 28)));
        closeButton.addActionListener(e -> dispose());

        JLabel hint = new JLabel("Scroll to the bottom to unlock Close");
        hint.setForeground(new Color(0x888888));
        hint.setFont(new Font("Segoe UI", Font.ITALIC, 11));

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 12));
        footer.setBackground(new Color(0x1A1A2E));
        footer.add(hint);
        footer.add(closeButton);
        root.add(footer, BorderLayout.SOUTH);

        // ── Scroll-to-bottom detection ─────────────────────────────
        JScrollBar vBar = scrollPane.getVerticalScrollBar();
        AdjustmentListener scrollListener = (AdjustmentEvent e2) -> {
            JScrollBar bar = (JScrollBar) e2.getAdjustable();
            int extent  = bar.getModel().getExtent();
            int maximum = bar.getMaximum();
            int value   = bar.getValue();
            boolean atBottom = (value + extent) >= (maximum - 5); // 5px tolerance
            if (atBottom) {
                closeButton.setEnabled(true);
                hint.setText("You may now close this guide.");
                hint.setForeground(new Color(0x4CAF50));
            }
        };
        vBar.addAdjustmentListener(scrollListener);

        // Edge case: if content is short enough to fit without scrolling,
        // unlock immediately after the dialog is shown.
        SwingUtilities.invokeLater(() -> {
            int max = vBar.getMaximum();
            int ext = vBar.getModel().getExtent();
            if (max <= ext) {
                closeButton.setEnabled(true);
                hint.setText("You may now close this guide.");
                hint.setForeground(new Color(0x4CAF50));
            }
        });
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(0x0F3460));
        panel.setBorder(new EmptyBorder(14, 20, 14, 20));

        JLabel title = new JLabel("\uD83D\uDED1  IPR Safety Camera");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(new Color(0xE94560));

        JLabel sub = new JLabel("Construction Site Surveillance System");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(new Color(0xAAAAAA));

        JPanel text = new JPanel(new GridLayout(2, 1, 0, 2));
        text.setOpaque(false);
        text.add(title);
        text.add(sub);
        panel.add(text, BorderLayout.WEST);
        return panel;
    }
}
