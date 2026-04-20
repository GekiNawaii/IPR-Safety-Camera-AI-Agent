package com.safetyCamera;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Dropdown that lets the user switch between AI detection modes.
 *
 * This popup is now instantiated per-camera and accepts a callback.
 */
public class ModeSelectorPopup extends JPopupMenu {

    private final ButtonGroup group = new ButtonGroup();

    private final JRadioButtonMenuItem offItem;
    private final JRadioButtonMenuItem safetyGearItem;
    private final JRadioButtonMenuItem fallingItem;
    private final JMenuItem            restrictedItem;

    private boolean updating = false;
    private final java.util.function.Consumer<ModeManager.Mode> onSelectCallback;

    public ModeSelectorPopup(ModeManager.Mode initialMode, java.util.function.Consumer<ModeManager.Mode> onSelectCallback) {
        this.onSelectCallback = onSelectCallback;

        setBackground(new Color(0x16213E));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x0F3460), 1),
            new EmptyBorder(4, 0, 4, 0)));

        JLabel header = new JLabel("  🛡  Detection Mode");
        header.setFont(new Font("Segoe UI", Font.BOLD, 13));
        header.setForeground(new Color(0x53C0F0));
        header.setBorder(new EmptyBorder(6, 12, 6, 12));
        add(header);
        addSeparator();

        offItem = buildRadio("⬜  OFF  (No Detection)", new Color(0x888888),
                             "Camera streams but AI model is paused.");
        offItem.addActionListener(e -> handleSelect(ModeManager.Mode.OFF));
        group.add(offItem);
        add(offItem);

        addSeparator();

        safetyGearItem = buildRadio("🛡  Safety Gear Recognition", new Color(0xFF9800),
                                    "Detects missing helmets and safety vests.");
        safetyGearItem.addActionListener(e -> handleSelect(ModeManager.Mode.SAFETY_GEAR));
        group.add(safetyGearItem);
        add(safetyGearItem);

        fallingItem = buildRadio("⚠  Falling Detection", new Color(0xF44336),
                                 "Detects when a worker falls or collapses.");
        fallingItem.addActionListener(e -> handleSelect(ModeManager.Mode.FALLING_DETECTION));
        group.add(fallingItem);
        add(fallingItem);

        addSeparator();

        restrictedItem = new JMenuItem("🔴  Restricted Area  (coming soon)");
        restrictedItem.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        restrictedItem.setForeground(new Color(0x555566));
        restrictedItem.setBackground(new Color(0x16213E));
        restrictedItem.setOpaque(true);
        restrictedItem.setBorder(new EmptyBorder(6, 14, 6, 14));
        restrictedItem.setEnabled(false);
        add(restrictedItem);

        syncState(initialMode);
    }

    private void handleSelect(ModeManager.Mode mode) {
        if (updating) return;
        if (onSelectCallback != null) {
            onSelectCallback.accept(mode);
        }
    }

    public void syncState(ModeManager.Mode active) {
        updating = true;
        try {
            offItem.setSelected(active == ModeManager.Mode.OFF);
            safetyGearItem.setSelected(active == ModeManager.Mode.SAFETY_GEAR);
            fallingItem.setSelected(active == ModeManager.Mode.FALLING_DETECTION);
        } finally {
            updating = false;
        }
    }

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
