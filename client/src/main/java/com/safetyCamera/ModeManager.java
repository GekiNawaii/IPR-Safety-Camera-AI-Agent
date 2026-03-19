package com.safetyCamera;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Singleton-ish manager for the three detection modes.
 *
 * Rules enforced here:
 *  - RESTRICTED_AREA is exclusive: enabling it disables the other two.
 *  - Enabling SAFETY_GEAR or FALLING_DETECTION disables RESTRICTED_AREA.
 *
 * Fires {@code PropertyChangeEvent("modes", oldSet, newSet)} whenever state changes.
 */
public class ModeManager {

    // ── Mode enum ─────────────────────────────────────────────────
    public enum Mode {
        RESTRICTED_AREA("Restricted Area Detection"),
        SAFETY_GEAR("Safety Gear Recognition"),
        FALLING_DETECTION("Falling Detection");

        private final String displayName;
        Mode(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    // ── Singleton ─────────────────────────────────────────────────
    private static final ModeManager INSTANCE = new ModeManager();
    public  static ModeManager getInstance() { return INSTANCE; }

    // ── State ─────────────────────────────────────────────────────
    private final EnumSet<Mode> activeModes = EnumSet.noneOf(Mode.class);
    private final PropertyChangeSupport pcs  = new PropertyChangeSupport(this);

    private ModeManager() {
        // Default: Restricted Area Detection ON
        activeModes.add(Mode.RESTRICTED_AREA);
    }

    // ── Accessors ─────────────────────────────────────────────────

    public synchronized boolean isActive(Mode m) {
        return activeModes.contains(m);
    }

    /** Immutable snapshot of the current active mode set. */
    public synchronized Set<Mode> getActiveModes() {
        return Collections.unmodifiableSet(activeModes.clone());
    }

    // ── Mutators ──────────────────────────────────────────────────

    /**
     * Enable the given mode, applying mutual-exclusion rules.
     */
    public synchronized void enable(Mode mode) {
        Set<Mode> old = activeModes.clone();

        if (mode == Mode.RESTRICTED_AREA) {
            // Exclusive – clear the other two
            activeModes.clear();
            activeModes.add(Mode.RESTRICTED_AREA);
        } else {
            // Any non-exclusive mode → disable Restricted Area
            activeModes.remove(Mode.RESTRICTED_AREA);
            activeModes.add(mode);
        }

        fireIfChanged(old);
    }

    /**
     * Disable the given mode.
     */
    public synchronized void disable(Mode mode) {
        Set<Mode> old = activeModes.clone();
        activeModes.remove(mode);
        fireIfChanged(old);
    }

    /**
     * Set a mode's active state explicitly (convenience for checkbox binding).
     */
    public void setActive(Mode mode, boolean active) {
        if (active) enable(mode); else disable(mode);
    }

    // ── Property change support ───────────────────────────────────

    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    private void fireIfChanged(Set<Mode> oldSet) {
        Set<Mode> newSet = Collections.unmodifiableSet(activeModes.clone());
        if (!oldSet.equals(activeModes)) {
            pcs.firePropertyChange("modes", oldSet, newSet);
        }
    }
}
