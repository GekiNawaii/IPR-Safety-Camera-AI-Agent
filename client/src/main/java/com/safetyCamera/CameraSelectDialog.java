package com.safetyCamera;

import org.opencv.videoio.VideoCapture;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Source selection dialog with 3 input modes (dropdown + CardLayout):
 *   1. Camera thiết bị  – auto-detected local hardware cameras
 *   2. IP Camera        – MJPEG/RTSP URL (phone app)
 *   3. Video từ máy     – browse a local video file; OpenCV reads it like a camera
 */
public class CameraSelectDialog extends JDialog {

    private String selectedSource = null;
    private String selectedName = null;

    private static final String MODE_LOCAL = "Camera thiết bị";
    private static final String MODE_IP    = "IP Camera (điện thoại)";
    private static final String MODE_VIDEO = "Video từ máy tính";

    public CameraSelectDialog(Frame owner) {
        super(owner, "Chọn Nguồn Đầu Vào", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(560, 360);
        setLocationRelativeTo(owner);
        setResizable(false);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(0x1A1A2E));
        setContentPane(root);

        // ── Header ────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0x0F3460));
        header.setBorder(new EmptyBorder(12, 20, 12, 20));
        JLabel title = new JLabel("Nguồn Camera / Video");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(new Color(0x53C0F0));
        header.add(title, BorderLayout.WEST);

        // ── Mode row ──────────────────────────────────────────────
        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 14));
        modeRow.setBackground(new Color(0x1A1A2E));
        JLabel modeLabel = new JLabel("Chế độ:");
        modeLabel.setForeground(new Color(0xE0E0E0));
        modeLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JComboBox<String> modeCombo = new JComboBox<>(new String[]{MODE_LOCAL, MODE_IP, MODE_VIDEO});
        modeCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        modeCombo.setBackground(new Color(0x0F3460));
        modeCombo.setForeground(Color.WHITE);
        modeCombo.setPreferredSize(new Dimension(270, 30));
        modeRow.add(modeLabel);
        modeRow.add(modeCombo);

        JPanel topArea = new JPanel(new BorderLayout());
        topArea.add(header, BorderLayout.NORTH);
        topArea.add(modeRow, BorderLayout.CENTER);
        root.add(topArea, BorderLayout.NORTH);

        // ── Card layout ───────────────────────────────────────────
        CardLayout cards = new CardLayout();
        JPanel cardPanel = new JPanel(cards);
        cardPanel.setBackground(new Color(0x1A1A2E));

        // ─ Card 1: Local Camera ───────────────────────────────────
        DefaultComboBoxModel<String> cameraModel = new DefaultComboBoxModel<>();
        JComboBox<String> cameraCombo = new JComboBox<>(cameraModel);
        cameraCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cameraCombo.setBackground(new Color(0x0F3460));
        cameraCombo.setForeground(Color.WHITE);

        JLabel scanLabel = new JLabel("Đang quét camera...");
        scanLabel.setForeground(new Color(0xAAAAAA));
        scanLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));

        JPanel localCard = new JPanel(new GridBagLayout());
        localCard.setBackground(new Color(0x1A1A2E));
        localCard.setBorder(new EmptyBorder(24, 30, 10, 30));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8, 8, 8, 8);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridx = 0; g.gridy = 0;
        localCard.add(lbl("Camera phát hiện được:"), g);
        g.gridx = 1; g.weightx = 1.0;
        localCard.add(cameraCombo, g);
        g.gridx = 0; g.gridy = 1; g.gridwidth = 2; g.weightx = 0;
        localCard.add(scanLabel, g);
        cardPanel.add(localCard, MODE_LOCAL);

        // ─ Card 2: IP Camera ──────────────────────────────────────
        JTextField urlField = new JTextField("http://192.168.25.203:8080/video");
        urlField.setFont(new Font("Segoe UI Mono", Font.PLAIN, 12));
        urlField.setBackground(new Color(0x0F3460));
        urlField.setForeground(Color.WHITE);
        urlField.setCaretColor(Color.WHITE);

        JPanel ipCard = new JPanel(new GridBagLayout());
        ipCard.setBackground(new Color(0x1A1A2E));
        ipCard.setBorder(new EmptyBorder(24, 30, 10, 30));
        GridBagConstraints g2 = new GridBagConstraints();
        g2.insets = new Insets(8, 8, 8, 8);
        g2.fill = GridBagConstraints.HORIZONTAL;
        g2.gridx = 0; g2.gridy = 0;
        ipCard.add(lbl("URL Camera:"), g2);
        g2.gridx = 1; g2.weightx = 1.0;
        ipCard.add(urlField, g2);
        g2.gridx = 0; g2.gridy = 1; g2.gridwidth = 2; g2.weightx = 0;
        JLabel tipIP = new JLabel("💡 Dùng app \"IP Webcam\" (Android) rồi copy link từ app vào đây.");
        tipIP.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        tipIP.setForeground(new Color(0x888888));
        ipCard.add(tipIP, g2);
        cardPanel.add(ipCard, MODE_IP);

        // ─ Card 3: Video File ─────────────────────────────────────
        JTextField filePathField = new JTextField("Chưa chọn file...");
        filePathField.setFont(new Font("Segoe UI Mono", Font.PLAIN, 12));
        filePathField.setBackground(new Color(0x0F3460));
        filePathField.setForeground(new Color(0x888888));
        filePathField.setCaretColor(Color.WHITE);
        filePathField.setEditable(false);

        JButton browseBtn = new JButton("📂 Browse...");
        browseBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        browseBtn.setBackground(new Color(0x1B4F72));
        browseBtn.setForeground(new Color(0x76D7C4));
        browseBtn.setFocusPainted(false);
        browseBtn.setBorder(new EmptyBorder(6, 14, 6, 14));
        browseBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        browseBtn.addActionListener(ev -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Chọn video để phân tích");
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Video (mp4, avi, mov, mkv)", "mp4", "avi", "mov", "mkv"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File f = fc.getSelectedFile();
                filePathField.setText(f.getAbsolutePath());
                filePathField.setForeground(Color.WHITE);
            }
        });

        JPanel videoCard = new JPanel(new GridBagLayout());
        videoCard.setBackground(new Color(0x1A1A2E));
        videoCard.setBorder(new EmptyBorder(24, 30, 10, 30));
        GridBagConstraints g3 = new GridBagConstraints();
        g3.insets = new Insets(8, 8, 8, 8);
        g3.fill = GridBagConstraints.HORIZONTAL;
        g3.gridx = 0; g3.gridy = 0;
        videoCard.add(lbl("File video:"), g3);
        g3.gridx = 1; g3.weightx = 1.0;
        videoCard.add(filePathField, g3);
        g3.gridx = 2; g3.weightx = 0;
        videoCard.add(browseBtn, g3);
        g3.gridx = 0; g3.gridy = 1; g3.gridwidth = 3;
        JLabel tipVid = new JLabel("➡ Video sẽ được phát lại qua AI Detection y như camera thật.");
        tipVid.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        tipVid.setForeground(new Color(0x888888));
        videoCard.add(tipVid, g3);
        cardPanel.add(videoCard, MODE_VIDEO);

        root.add(cardPanel, BorderLayout.CENTER);

        // Switch card when mode changes
        modeCombo.addActionListener(e -> cards.show(cardPanel, (String) modeCombo.getSelectedItem()));

        // ── Footer ────────────────────────────────────────────────
        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
        namePanel.setBackground(new Color(0x1A1A2E));
        JLabel nameLabel = lbl("Tên hiển thị:");
        JTextField nameField = new JTextField(15);
        nameField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        nameField.setBackground(new Color(0x0F3460));
        nameField.setForeground(Color.WHITE);
        nameField.setCaretColor(Color.WHITE);
        namePanel.add(nameLabel);
        namePanel.add(nameField);

        JButton connectBtn = new JButton("  Kết nối & Bắt đầu  ");
        JButton cancelBtn  = new JButton("Huỷ");
        styleBtn(connectBtn, new Color(0x4CAF50), Color.WHITE);
        styleBtn(cancelBtn,  new Color(0x333355), new Color(0xAAAAAA));

        connectBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "Vui lòng nhập tên hiển thị cho Camera!", "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String mode = (String) modeCombo.getSelectedItem();
            if (MODE_LOCAL.equals(mode)) {
                String sel = (String) cameraCombo.getSelectedItem();
                if (sel != null) selectedSource = sel.split(" ")[1]; // "Camera 0 ..." → "0"
            } else if (MODE_IP.equals(mode)) {
                selectedSource = urlField.getText().trim();
            } else { // MODE_VIDEO
                String path = filePathField.getText().trim();
                if (path.isEmpty() || path.startsWith("Chưa")) {
                    JOptionPane.showMessageDialog(this,
                        "Vui lòng chọn file video trước!", "Thiếu file", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                selectedSource = path;
            }
            selectedName = name;
            dispose();
        });
        cancelBtn.addActionListener(e -> { selectedSource = null; selectedName = null; dispose(); });

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 12));
        footer.setBackground(new Color(0x1A1A2E));
        footer.add(cancelBtn);
        footer.add(connectBtn);
        
        JPanel bottomArea = new JPanel(new BorderLayout());
        bottomArea.setBackground(new Color(0x1A1A2E));
        bottomArea.add(namePanel, BorderLayout.NORTH);
        bottomArea.add(footer, BorderLayout.SOUTH);
        
        root.add(bottomArea, BorderLayout.SOUTH);

        // Probe local cameras in background
        new SwingWorker<List<Integer>, Void>() {
            @Override protected List<Integer> doInBackground() {
                List<Integer> found = new ArrayList<>();
                for (int i = 0; i <= 3; i++) {
                    VideoCapture vc = new VideoCapture();
                    if (vc.open(i)) { found.add(i); vc.release(); }
                }
                return found;
            }
            @Override protected void done() {
                try {
                    List<Integer> cams = get();
                    for (int idx : cams)
                        cameraModel.addElement("Camera " + idx + (idx == 0 ? " (Built-in)" : ""));
                    scanLabel.setText(cams.size() + " camera sẵn sàng.");
                    scanLabel.setForeground(cams.isEmpty() ? new Color(0xE94560) : new Color(0x4CAF50));
                } catch (Exception ex) { scanLabel.setText("Lỗi quét camera."); }
            }
        }.execute();
    }

    private JLabel lbl(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 13));
        l.setForeground(new Color(0xE0E0E0));
        return l;
    }

    private void styleBtn(JButton btn, Color bg, Color fg) {
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(bg.brighter(), 1),
            new EmptyBorder(6, 16, 6, 16)));
    }

    /** Returns: camera index string ("0"), URL, or absolute video file path. Null if cancelled. */
    public String getCameraSource() {
        return selectedSource;
    }

    public String getCameraName() {
        return selectedName;
    }
}
