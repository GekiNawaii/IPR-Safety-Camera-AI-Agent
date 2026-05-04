"""
SafetyLogicModule – rule engine that turns raw perception data into violations.

Input: the dict produced by PerceptionModule.detect():
    {
        "persons"  : [{ "bbox": [x1,y1,x2,y2], "conf": 0.9, "keypoints": [...] }],
        "ppe_items": [{ "type": "helmet", "bbox": [...], "conf": 0.85 }]
    }

Output (violations list):
    [
        {
            "type"       : "MISSING_PPE",       # or "FALL_DETECTED"
            "person_bbox": [x1, y1, x2, y2],
            "details"    : ["helmet", "vest"]   # missing items  (PPE only)
        },
        ...
    ]
"""

from __future__ import annotations
from typing import Optional

# ── YOLOv8-pose keypoint indices ───────────────────────────────────────────────
KP_NOSE       =  0
KP_LEFT_EYE   =  1
KP_RIGHT_EYE  =  2
KP_LEFT_EAR   =  3
KP_RIGHT_EAR  =  4
KP_LEFT_SHLDR =  5
KP_RIGHT_SHLDR=  6
KP_LEFT_ELBOW =  7
KP_RIGHT_ELBOW=  8
KP_LEFT_WRIST =  9
KP_RIGHT_WRIST= 10
KP_LEFT_HIP   = 11
KP_RIGHT_HIP  = 12
KP_LEFT_KNEE  = 13
KP_RIGHT_KNEE = 14
KP_LEFT_ANKLE = 15
KP_RIGHT_ANKLE= 16

# Minimum keypoint confidence to consider a point valid
KP_MIN_CONF = 0.30

# Required PPE classes (must match keys in perception.PPE_CLASS_MAP)
REQUIRED_PPE: list[str] = ["helmet", "vest"]


class SafetyLogicModule:

    def analyze(self, data: dict, mode: str = "ALL") -> list[dict]:
        """
        Analyse the perception payload and return a list of violation dicts.
        'mode' controls which checks are executed: 'SAFETY_GEAR', 'FALLING_DETECTION', or 'ALL'.
        """
        persons   = data.get("persons",   [])
        ppe_items = data.get("ppe_items", [])
        violations: list[dict] = []

        for person in persons:
            pbbox = person["bbox"]
            is_violated = False

            # ── 1. PPE check ──────────────────────────────────────────────────
            if mode in ("ALL", "SAFETY_GEAR"):
                missing = self._missing_ppe(pbbox, ppe_items)
                if missing:
                    is_violated = True
                    violations.append({
                        "type":        "MISSING_PPE",
                        "person_bbox": pbbox,
                        "details":     missing,
                        "safe":        False,
                    })

            # ── 2. Fall detection ─────────────────────────────────────────────
            if not is_violated and mode in ("ALL", "FALLING_DETECTION"):
                if self._is_falling(person):
                    is_violated = True
                    violations.append({
                        "type":        "FALL_DETECTED",
                        "person_bbox": pbbox,
                        "details":     ["Person may have fallen"],
                        "safe":        False,
                    })

            # ── 2.5 Restricted Area ───────────────────────────────────────────
            if not is_violated and mode == "RESTRICTED_AREA":
                is_violated = True
                violations.append({
                    "type":        "RESTRICTED_ACCESS",
                    "person_bbox": pbbox,
                    "details":     ["Unauthorized person detected in zone"],
                    "safe":        False,
                })

            # ── 3. Safe marker ────────────────────────────────────────────────
            # Emit a SAFE box if the person clears all the chosen mode checks
            if not is_violated:
                violations.append({
                    "type":        "SAFE",
                    "person_bbox": pbbox,
                    "details":     [],
                    "safe":        True,
                })

        return violations

    # ─── PPE ──────────────────────────────────────────────────────────────────

    def _missing_ppe(self, pbbox: list, ppe_items: list[dict]) -> list[str]:
        """
        Return list of required PPE types that are NOT found near the person.
        Uses a generous overlap heuristic: the PPE item's centre must lie inside
        the person bounding box (expanded slightly).
        """
        x1, y1, x2, y2 = pbbox
        pw = x2 - x1
        ph = y2 - y1

        # Expand person box by 10 % on each side to be generous
        px1 = x1 - pw * 0.10
        py1 = y1 - ph * 0.10
        px2 = x2 + pw * 0.10
        py2 = y2 + ph * 0.10

        found: set[str] = set()
        for item in ppe_items:
            ix1, iy1, ix2, iy2 = item["bbox"]
            # Centre of the PPE item
            cx = (ix1 + ix2) / 2
            cy = (iy1 + iy2) / 2
            if px1 <= cx <= px2 and py1 <= cy <= py2:
                found.add(item["type"])

        missing = [p for p in REQUIRED_PPE if p not in found]
        return missing

    # ─── Fall detection ───────────────────────────────────────────────────────

    def _is_falling(self, person: dict) -> bool:
        """
        Multi-heuristic fall detector.

        Evidence is weighted; we require at least 2 independent signals before
        flagging to avoid false positives (e.g. people crouching or doing work).

        Heuristics (each contributes 1 point):
          H1 – BBox aspect ratio: width / height > 1.3  (horizontal silhouette)
          H2 – Nose is below hip keypoints
          H3 – Head Y-position is lower than knee Y-position
          H4 – Hip Y-position is below ankle Y-position  (person inverted)
          H5 – Spine angle (shoulder→hip vector) is nearly horizontal (< 30 °)
        """
        pbbox = person["bbox"]
        w = pbbox[2] - pbbox[0]
        h = pbbox[3] - pbbox[1]
        if h == 0:
            return False

        evidence = 0

        # H1 – Aspect ratio
        if w / h > 1.3:
            evidence += 1

        # Keypoint-based checks only if keypoints are available
        kp = person.get("keypoints")
        if kp and len(kp) >= 17:
            def pt(idx: int):
                """Return (x, y) or None if confidence is too low."""
                kx, ky, kc = kp[idx]
                return (kx, ky) if kc >= KP_MIN_CONF else None

            nose        = pt(KP_NOSE)
            l_hip       = pt(KP_LEFT_HIP)
            r_hip       = pt(KP_RIGHT_HIP)
            l_shoulder  = pt(KP_LEFT_SHLDR)
            r_shoulder  = pt(KP_RIGHT_SHLDR)
            l_knee      = pt(KP_LEFT_KNEE)
            r_knee      = pt(KP_RIGHT_KNEE)
            l_ankle     = pt(KP_LEFT_ANKLE)
            r_ankle     = pt(KP_RIGHT_ANKLE)

            hip_y = _avg_y(l_hip, r_hip)
            ankle_y = _avg_y(l_ankle, r_ankle)
            knee_y = _avg_y(l_knee, r_knee)
            shldr_y = _avg_y(l_shoulder, r_shoulder)

            # H2 – Nose below hips (in image coords, Y increases downward)
            if nose and hip_y is not None:
                if nose[1] > hip_y:
                    evidence += 1

            # H3 – Head below knees
            if nose and knee_y is not None:
                if nose[1] > knee_y:
                    evidence += 1

            # H4 – Hip below ankles (person upside-down / thrown)
            if hip_y is not None and ankle_y is not None:
                if hip_y > ankle_y:
                    evidence += 1

            # H5 – Spine nearly horizontal
            if shldr_y is not None and hip_y is not None:
                from math import atan2, degrees, pi
                shldr_mid_x = _avg_x(l_shoulder, r_shoulder)
                hip_mid_x   = _avg_x(l_hip, r_hip)
                if shldr_mid_x is not None and hip_mid_x is not None:
                    dx = hip_mid_x - shldr_mid_x
                    dy = hip_y - shldr_y
                    angle = abs(degrees(atan2(dy, dx)))
                    # Angle ~0° or ~180° → horizontal spine → falling
                    if angle < 30 or angle > 150:
                        evidence += 1

        # Require at least 2 independent signals (or just aspect ratio for now
        # if keypoints are unavailable / occluded)
        is_fall = evidence >= 2
        if is_fall:
            print(f"[Logic] FALL_DETECTED – evidence score: {evidence}/5  "
                  f"bbox_ratio={w/h:.2f}")
        return is_fall


# ── helpers ────────────────────────────────────────────────────────────────────

def _avg_y(*pts) -> Optional[float]:
    valid = [p[1] for p in pts if p is not None]
    return sum(valid) / len(valid) if valid else None


def _avg_x(*pts) -> Optional[float]:
    valid = [p[0] for p in pts if p is not None]
    return sum(valid) / len(valid) if valid else None
