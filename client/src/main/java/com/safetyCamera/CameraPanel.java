package com.safetyCamera;

import org.opencv.core.Mat;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

/**
 * JPanel that renders live camera frames.
 *
 * Receives processed {@link Mat} objects from the camera thread,
 * converts them to {@link BufferedImage} and paints them scaled-to-fit.
 */
public class CameraPanel extends JPanel {

    private volatile BufferedImage currentFrame;
    private volatile int           detectionCount = 0;

    public CameraPanel() {
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(960, 540));
    }

    /**
     * Called from the camera capture thread to deliver the next processed frame.
     * Does NOT need to be called from the EDT.
     */
    public void updateFrame(Mat mat) {
        if (mat == null || mat.empty()) return;
        BufferedImage img = matToBufferedImage(mat);
        currentFrame = img;
        // Trigger repaint on the EDT
        SwingUtilities.invokeLater(this::repaint);
    }

    /** Update the running detection event count shown in the overlay. */
    public void setDetectionCount(int count) {
        this.detectionCount = count;
        SwingUtilities.invokeLater(this::repaint);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        BufferedImage img = currentFrame;
        if (img == null) {
            // Placeholder while camera initialises
            g2.setColor(new Color(0x16213E));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(new Color(0x53C0F0));
            g2.setFont(new Font("Segoe UI", Font.BOLD, 18));
            String msg = "Initialising camera…";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg,
                (getWidth()  - fm.stringWidth(msg)) / 2,
                (getHeight() / 2));
            return;
        }

        // Scale image to fill panel while preserving aspect ratio
        int panelW = getWidth();
        int panelH = getHeight();
        double scaleX = (double) panelW / img.getWidth();
        double scaleY = (double) panelH / img.getHeight();
        double scale  = Math.min(scaleX, scaleY);

        int drawW = (int) (img.getWidth()  * scale);
        int drawH = (int) (img.getHeight() * scale);
        int drawX = (panelW - drawW) / 2;
        int drawY = (panelH - drawH) / 2;

        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, panelW, panelH);
        g2.drawImage(img, drawX, drawY, drawW, drawH, null);
    }

    // ── OpenCV Mat → BufferedImage conversion ─────────────────────

    private static BufferedImage matToBufferedImage(Mat mat) {
        int type;
        if (mat.channels() == 1) {
            type = BufferedImage.TYPE_BYTE_GRAY;
        } else if (mat.channels() == 3) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        } else {
            type = BufferedImage.TYPE_4BYTE_ABGR;
        }

        BufferedImage image = new BufferedImage(mat.width(), mat.height(), type);
        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        mat.get(0, 0, data);
        return image;
    }
}
