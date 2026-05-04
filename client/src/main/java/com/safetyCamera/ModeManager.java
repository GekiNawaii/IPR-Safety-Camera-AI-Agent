package com.safetyCamera;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Singleton that tracks which AI detection model is currently active.
 *
 * Replaces the old multi-checkbox system with a single exclusive selection:
 *   SAFETY_GEAR      → run ppe_best.pt  (helmet / vest check)
 *   FALLING_DETECTION→ run yolov8n-pose.pt keypoint analysis
 *   OFF              → no AI inference (camera still streams)
 *
 * RESTRICTED_AREA is kept in the enum for backward-compat but is not selectable.
 *
 * Fires PropertyChangeEvent("activeMode", oldMode, newMode) on every change.
 */
public class ModeManager {

    // ── Mode enum ──────────────────────────────────────────────────────────────
    public enum Mode {
        OFF              ("OFF (No Detection)",      0x666688),
        SAFETY_GEAR      ("Safety Gear Recognition", 0xFF9800),
        FALLING_DETECTION("Falling Detection",       0xF44336),
        RESTRICTED_AREA  ("Restricted Area (WIP)",   0x555566);  // disabled / placeholder

        private final String displayName;
        private final int    colour;       // for UI badges

        Mode(String d, int c) { this.displayName = d; this.colour = c; }
        public String getDisplayName() { return displayName; }
        public int    getColour()      { return colour; }
    }

    // ── Singleton ──────────────────────────────────────────────────────────────
    private static final ModeManager INSTANCE = new ModeManager();
    public  static ModeManager getInstance() { return INSTANCE; }

    // ── State ──────────────────────────────────────────────────────────────────
    private volatile Mode activeMode = Mode.SAFETY_GEAR;   // default
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    private ModeManager() {}

    // ── Accessors ──────────────────────────────────────────────────────────────

    public synchronized Mode getActiveMode() { return activeMode; }

    /** Back-compat: returns true only when the given mode is the active one. */
    public synchronized boolean isActive(Mode m) { return activeMode == m; }

    // ── Mutators ───────────────────────────────────────────────────────────────

    public synchronized void setActiveMode(Mode mode) {
        Mode old = activeMode;
        if (old == mode) return;
        activeMode = mode;
        pcs.firePropertyChange("activeMode", old, mode);
    }

    // ── Legacy convenience (used by MainFrame status bar) ──────────────────────

    /** @deprecated Use getActiveMode() instead. */
    @Deprecated
    public java.util.Set<Mode> getActiveModes() {
        return activeMode == Mode.OFF
            ? java.util.Collections.emptySet()
            : java.util.Collections.singleton(activeMode);
    }

    // ── Property change support ────────────────────────────────────────────────

    public void addPropertyChangeListener(PropertyChangeListener l)    { pcs.addPropertyChangeListener(l); }
    public void removePropertyChangeListener(PropertyChangeListener l) { pcs.removePropertyChangeListener(l); }
}
