package com.safetyCamera;

import com.formdev.flatlaf.FlatDarkLaf;
import nu.pattern.OpenCV;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

/**
 * Application entry point.
 * Bootstraps OpenCV native library, sets dark UI theme,
 * then runs the startup flow: Guide (first run only) → Camera Select → Main Window.
 */
public class Main {

    /** Marker file – its existence means the guide has already been shown. */
    private static final File GUIDE_MARKER = new File(".guide_shown");

    public static void main(String[] args) {
        // Load OpenCV native library (bundled inside openpnp JAR)
        try {
            OpenCV.loadLocally();
        } catch (Throwable e) {
            JOptionPane.showMessageDialog(null,
                "Failed to load OpenCV native library:\n" + e.getMessage(),
                "Startup Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(1);
        }

        // Apply FlatLaf dark theme before any Swing component is created
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (UnsupportedLookAndFeelException e) {
            System.err.println("FlatLaf not available, using default L&F");
        }

        // Customise global colours to match our industrial palette
        UIManager.put("Panel.background",            new java.awt.Color(0x1A1A2E));
        UIManager.put("OptionPane.background",       new java.awt.Color(0x1A1A2E));
        UIManager.put("ScrollPane.background",       new java.awt.Color(0x16213E));
        UIManager.put("TextArea.background",         new java.awt.Color(0x16213E));
        UIManager.put("TextArea.foreground",         new java.awt.Color(0xE0E0E0));
        UIManager.put("Button.background",           new java.awt.Color(0x0F3460));
        UIManager.put("Button.foreground",           new java.awt.Color(0xE0E0E0));
        UIManager.put("CheckBox.background",         new java.awt.Color(0x1A1A2E));
        UIManager.put("CheckBox.foreground",         new java.awt.Color(0xE0E0E0));
        UIManager.put("ComboBox.background",         new java.awt.Color(0x0F3460));
        UIManager.put("ComboBox.foreground",         new java.awt.Color(0xE0E0E0));
        UIManager.put("Label.foreground",            new java.awt.Color(0xE0E0E0));

        SwingUtilities.invokeLater(() -> {
            // Step 1: Show usage guide only on first run
            if (!GUIDE_MARKER.exists()) {
                GuideDialog guide = new GuideDialog(null);
                guide.setVisible(true);   // modal – blocks until closed
                // Create marker so the guide won't appear again
                try {
                    GUIDE_MARKER.createNewFile();
                } catch (IOException ex) {
                    System.err.println("Could not create guide marker: " + ex.getMessage());
                }
            }

            // Step 2: Camera selection
            CameraSelectDialog dialog = new CameraSelectDialog(null);
            dialog.setVisible(true);

            String source = dialog.getCameraSource();
            if (source == null) {
                Runtime.getRuntime().halt(0);
            }

            // Step 3: Start backend server & launch main surveillance window
            ServerManager.start();
            new MainFrame(source).setVisible(true);
        });
    }
}

