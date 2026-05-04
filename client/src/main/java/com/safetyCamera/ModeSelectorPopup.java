package com.safetyCamera;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Top-right dropdown that lets the user switch between AI detection modes.
 *
 * UI uses Radio-button style menu items so exactly ONE mode is active at a time:
 *   ⬜  OFF              – no AI inference
 *   🛡  Safety Gear      – run PPE model (helmet / vest)
 *   ⚠  Falling Detection – run pose model (keypoints)
 *   ─────────────────────────────────────── (separator)
 *   🔴  Restricted Area  – DISABLED / coming soon
 */
public class ModeSelectorPopup extends JPopupMenu implements PropertyChangeListener {

    private final ButtonGroup group = new ButtonGroup();

    private final JRadioButtonMenuItem offItem;
    private final JRadioButtonMenuItem safetyGearItem;
    private final JRadioButtonMenuItem fallingItem;
    private final JMenuItem            restrictedItem;   // always disabled

    private boolean updating = false;

    public ModeSelectorPopup() {
        setBackground(new Color(0x16213E));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x0F3460), 1),
            new EmptyBorder(4, 0, 4, 0)));

<<<<<<< HEAD
        JLabel header = new JLabel("Detection Mode");
=======
        // ── Header ────────────────────────────────────────────────────────────
        JLabel header = new JLabel("  🛡  Detection Mode");
>>>>>>> 8991c7bd1b1c18c1cfefa76689e325281ccdef2e
        header.setFont(new Font("Segoe UI", Font.BOLD, 13));
        header.setForeground(new Color(0x53C0F0));
        header.setBorder(new EmptyBorder(6, 12, 6, 12));
        add(header);
        addSeparator();

<<<<<<< HEAD
        offItem = buildRadio("OFF (No Detection)", new Color(0x888888),
=======
        // ── OFF ───────────────────────────────────────────────────────────────
        offItem = buildRadio("⬜  OFF  (No Detection)", new Color(0x888888),
>>>>>>> 8991c7bd1b1c18c1cfefa76689e325281ccdef2e
                             "Camera streams but AI model is paused.");
        offItem.addActionListener(e -> handleSelect(ModeManager.Mode.OFF));
        group.add(offItem);
        add(offItem);

        addSeparator();

<<<<<<< HEAD
        safetyGearItem = buildRadio("Safety Gear Recognition", new Color(0xFF9800),
=======
        // ── Safety Gear ───────────────────────────────────────────────────────
        safetyGearItem = buildRadio("🛡  Safety Gear Recognition", new Color(0xFF9800),
>>>>>>> 8991c7bd1b1c18c1cfefa76689e325281ccdef2e
                                    "Detects missing helmets and safety vests.");
        safetyGearItem.addActionListener(e -> handleSelect(ModeManager.Mode.SAFETY_GEAR));
        group.add(safetyGearItem);
        add(safetyGearItem);

<<<<<<< HEAD
        fallingItem = buildRadio("Falling Detection", new Color(0xF44336),
=======
        // ── Falling Detection ─────────────────────────────────────────────────
        fallingItem = buildRadio("⚠  Falling Detection", new Color(0xF44336),
>>>>>>> 8991c7bd1b1c18c1cfefa76689e325281ccdef2e
                                 "Detects when a worker falls or collapses.");
        fallingItem.addActionListener(e -> handleSelect(ModeManager.Mode.FALLING_DETECTION));
        group.add(fallingItem);
        add(fallingItem);

<<<<<<< HEAD
        restrictedItem = buildRadio("Restricted Area Detection", new Color(0x4CAF50),
                                    "Detects any human presence in the restricted zone.");
        restrictedItem.addActionListener(e -> handleSelect(ModeManager.Mode.RESTRICTED_AREA));
        group.add(restrictedItem);
=======
        addSeparator();

        // ── Restricted Area (disabled placeholder) ────────────────────────────
        restrictedItem = new JMenuItem("🔴  Restricted Area  (coming soon)");
        restrictedItem.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        restrictedItem.setForeground(new Color(0x555566));
        restrictedItem.setBackground(new Color(0x16213E));
        restrictedItem.setOpaque(true);
        restrictedItem.setBorder(new EmptyBorder(6, 14, 6, 14));
        restrictedItem.setEnabled(false);
>>>>>>> 8991c7bd1b1c18c1cfefa76689e325281ccdef2e
        add(restrictedItem);

        // Sync UI with initial manager state
        syncFromManager();
        ModeManager.getInstance().addPropertyChangeListener(this);
    }

    // ── Event handling ────────────────────────────────────────────────────────

    private void handleSelect(ModeManager.Mode mode) {
        if (updating) return;
        ModeManager.getInstance().setActiveMode(mode);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (!"activeMode".equals(evt.getPropertyName())) return;
        SwingUtilities.invokeLater(this::syncFromManager);
    }

    private void syncFromManager() {
        updating = true;
        try {
            ModeManager.Mode active = ModeManager.getInstance().getActiveMode();
            offItem.setSelected(active == ModeManager.Mode.OFF);
            safetyGearItem.setSelected(active == ModeManager.Mode.SAFETY_GEAR);
            fallingItem.setSelected(active == ModeManager.Mode.FALLING_DETECTION);
            restrictedItem.setSelected(active == ModeManager.Mode.RESTRICTED_AREA);
        } finally {
            updating = false;
        }
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    private JRadioButtonMenuItem buildRadio(String text, Color fg, String tooltip) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(text);
        item.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        item.setForeground(fg);
        item.setBackground(new Color(0x16213E));
        item.setOpaque(true);
        item.setBorder(new EmptyBorder(6, 14, 6, 14));
        item.setToolTipText(tooltip);
        return item;
    }
}
