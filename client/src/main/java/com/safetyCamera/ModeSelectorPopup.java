package com.safetyCamera;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Set;

/**
 * Top-right dropdown that lets the user enable / disable detection modes
 * via checkboxes. Enforces the mutual-exclusion rule by listening to
 * {@link ModeManager} and keeping checkbox states in sync.
 */
public class ModeSelectorPopup extends JPopupMenu implements PropertyChangeListener {

    private final JCheckBoxMenuItem restrictedItem;
    private final JCheckBoxMenuItem safetyGearItem;
    private final JCheckBoxMenuItem fallingItem;

    /** Flag to suppress re-entrant events while we're updating checkboxes programmatically. */
    private boolean updating = false;

    public ModeSelectorPopup() {
        setBackground(new Color(0x16213E));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x0F3460), 1),
            new EmptyBorder(4, 0, 4, 0)));

        // ── Header label ──────────────────────────────────────────
        JLabel header = new JLabel("  \uD83D\uDEE1  Detection Modes");
        header.setFont(new Font("Segoe UI", Font.BOLD, 13));
        header.setForeground(new Color(0x53C0F0));
        header.setBorder(new EmptyBorder(6, 12, 6, 12));
        add(header);
        addSeparator();

        // ── Restricted Area (exclusive) ───────────────────────────
        restrictedItem = buildCheckItem(
            "\u2B24  Restricted Area Detection",
            new Color(0x4CAF50),
            "Detects humans in the monitored area. EXCLUSIVE – disables other modes.");
        restrictedItem.setSelected(true); // default ON
        restrictedItem.addActionListener(e -> handleCheck(
            ModeManager.Mode.RESTRICTED_AREA, restrictedItem.isSelected()));
        add(restrictedItem);

        addSeparator();

        // ── Safety Gear ────────────────────────────────────────────
        safetyGearItem = buildCheckItem(
            "\uD83D\uDEE2  Safety Gear Recognition",
            new Color(0xFF9800),
            "Flags workers missing required PPE.");
        safetyGearItem.addActionListener(e -> handleCheck(
            ModeManager.Mode.SAFETY_GEAR, safetyGearItem.isSelected()));
        add(safetyGearItem);

        // ── Falling Detection ──────────────────────────────────────
        fallingItem = buildCheckItem(
            "\u26A0  Falling Detection",
            new Color(0xF44336),
            "Alerts when a person falls or collapses.");
        fallingItem.addActionListener(e -> handleCheck(
            ModeManager.Mode.FALLING_DETECTION, fallingItem.isSelected()));
        add(fallingItem);

        addSeparator();

        // ── Info footer ───────────────────────────────────────────
        JLabel info = new JLabel("  Restricted Area is exclusive");
        info.setFont(new Font("Segoe UI", Font.ITALIC, 10));
        info.setForeground(new Color(0x666688));
        info.setBorder(new EmptyBorder(4, 12, 4, 12));
        add(info);

        // Listen to ModeManager so checkboxes stay in sync with
        // programmatic changes (e.g. mutual exclusion)
        ModeManager.getInstance().addPropertyChangeListener(this);
    }

    // ── User action handler ───────────────────────────────────────

    private void handleCheck(ModeManager.Mode mode, boolean selected) {
        if (updating) return;
        ModeManager.getInstance().setActive(mode, selected);
        // ModeManager fires propertyChange → syncFromManager() will update visuals
    }

    // ── PropertyChangeListener (from ModeManager) ─────────────────

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (!"modes".equals(evt.getPropertyName())) return;
        SwingUtilities.invokeLater(this::syncFromManager);
    }

    /** Refresh checkbox states and enabled-status from ModeManager truth. */
    private void syncFromManager() {
        updating = true;
        try {
            ModeManager mm = ModeManager.getInstance();
            Set<ModeManager.Mode> active = mm.getActiveModes();

            boolean restrictedOn = active.contains(ModeManager.Mode.RESTRICTED_AREA);

            restrictedItem.setSelected(restrictedOn);
            safetyGearItem.setSelected(active.contains(ModeManager.Mode.SAFETY_GEAR));
            fallingItem.setSelected(active.contains(ModeManager.Mode.FALLING_DETECTION));

            // Disable Safety Gear and Falling when Restricted Area is ON
            safetyGearItem.setEnabled(!restrictedOn);
            fallingItem.setEnabled(!restrictedOn);

            // Disable Restricted Area when others are ON
            boolean othersOn = active.contains(ModeManager.Mode.SAFETY_GEAR)
                            || active.contains(ModeManager.Mode.FALLING_DETECTION);
            restrictedItem.setEnabled(!othersOn);

        } finally {
            updating = false;
        }
    }

    // ── Builder helper ────────────────────────────────────────────

    private JCheckBoxMenuItem buildCheckItem(String text, Color fg, String tooltip) {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(text);
        item.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        item.setForeground(fg);
        item.setBackground(new Color(0x16213E));
        item.setOpaque(true);
        item.setBorder(new EmptyBorder(6, 14, 6, 14));
        item.setToolTipText(tooltip);
        return item;
    }
}
