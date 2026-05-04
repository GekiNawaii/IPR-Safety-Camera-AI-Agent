"""
test_model.py  –  Offline test harness for the Safety AI pipeline.

Renders the SAME visual output as the Java live camera feed:
  • GREEN box     = worker fully safe
  • RED box       = missing PPE or fall detected
  • Label on box  = "! Missing: helmet, vest" / "⚠ FALL DETECTED"

Output images are saved to:   runs/detect/test_results/

Usage examples
--------------
# Test a single image:
python test_model.py --source "datasets/ppe_dataset/data/images/val/image1.jpg"

# Test an entire folder:
python test_model.py --source "datasets/ppe_dataset/data/images/val"

# Test without --source  → picks random val images automatically:
python test_model.py --num 5
"""

import os, sys, glob, random, argparse, math
import cv2
import numpy as np

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from safety_agent.modules.perception import PerceptionModule
from safety_agent.modules.logic import SafetyLogicModule

# ─── Colours (BGR) ────────────────────────────────────────────────────────────
GREEN = (40, 210, 40)
RED   = (40, 40, 255)
BLACK = (0, 0, 0)
WHITE = (240, 240, 240)


def draw_results(img: np.ndarray, violations: list) -> np.ndarray:
    """Render the violations list onto a copy of img (same logic as Java)."""
    out = img.copy()
    vio_count = 0

    for v in violations:
        safe  = v.get("safe", True)
        bbox  = v["person_bbox"]
        vtype = v["type"]

        x1, y1, x2, y2 = [int(c) for c in bbox]
        colour = GREEN if safe else RED
        thick  = 2 if safe else 3

        cv2.rectangle(out, (x1, y1), (x2, y2), colour, thick)

        if not safe:
            vio_count += 1
            if vtype == "MISSING_PPE":
                missing = v.get("details", [])
                label = "! Missing: " + ", ".join(missing)
            elif vtype == "FALL_DETECTED":
                label = "\u26a0 FALL DETECTED"
            else:
                label = vtype

            # Draw label background
            font   = cv2.FONT_HERSHEY_SIMPLEX
            scale  = 0.55
            thick1 = 2
            (tw, th), _ = cv2.getTextSize(label, font, scale, thick1)
            lx  = x1
            ly  = max(y1 - 6, th + 6)
            cv2.rectangle(out, (lx, ly - th - 4), (lx + tw + 4, ly + 2), BLACK, -1)
            cv2.putText(out, label, (lx + 2, ly - 2), font, scale, colour, thick1)

    # Status bar
    h, w = out.shape[:2]
    status = (f"{vio_count} violation(s) detected" if vio_count > 0
              else "All workers safe" if violations else "No persons detected")
    cv2.rectangle(out, (0, h - 26), (w, h), BLACK, -1)
    cv2.putText(out, "SafetyCam AI  |  " + status,
                (8, h - 8), cv2.FONT_HERSHEY_SIMPLEX, 0.5,
                RED if vio_count > 0 else GREEN, 1)
    return out


def run_test(source, num: int = 10, mode: str = "ALL"):
    # ── Find models ───────────────────────────────────────────────────────────
    ppe_model_path = "models/ppe_best.pt"
    if not os.path.exists(ppe_model_path):
        ppe_model_path = "yolov8n.pt"
        print(f"[WARN] ppe_best.pt not found, using yolov8n.pt as fallback.")

    print(f"Loading models (this may take a moment on first run)...")
    perception = PerceptionModule(ppe_model_path)
    logic      = SafetyLogicModule()

    # ── Resolve source ────────────────────────────────────────────────────────
    if source:
        if os.path.isdir(source):
            files = (glob.glob(os.path.join(source, "*.jpg")) +
                     glob.glob(os.path.join(source, "*.png")) +
                     glob.glob(os.path.join(source, "*.jpeg")))
            if not files:
                print(f"No images found in folder: {source}")
                return
            sources = random.sample(files, min(num, len(files)))
        else:
            sources = [source]
    else:
        val = glob.glob("datasets/ppe_dataset/data/images/val/*.jpg")
        trn = glob.glob("datasets/ppe_dataset/data/images/train/*.jpg")
        all_imgs = val + trn
        if not all_imgs:
            print("No images found in datasets/ppe_dataset. Use --source to specify a path.")
            return
        sources = random.sample(all_imgs, min(num, len(all_imgs)))
        print(f"Auto-selected {len(sources)} images from PPE dataset…")

    # ── Out dir ───────────────────────────────────────────────────────────────
    out_dir = os.path.join("runs", "detect", "test_results")
    os.makedirs(out_dir, exist_ok=True)

    # ── Process ───────────────────────────────────────────────────────────────
    print(f"\nProcessing {len(sources)} image(s)…\n")
    total_violations = 0

    for idx, img_path in enumerate(sources):
        img = cv2.imread(img_path)
        if img is None:
            print(f"  [!] Could not read: {img_path}")
            continue

        data       = perception.detect(img)
        violations = logic.analyze(data, mode=mode)
        rendered   = draw_results(img, violations)

        # Summary for this image
        vios   = [v for v in violations if not v.get("safe", True)]
        status = ("SAFE" if not vios else
                  ("🔴 " + " | ".join(set(v["type"] for v in vios))))
        print(f"  [{idx+1:02d}] {os.path.basename(img_path):40s}  "
              f"persons={len(violations)}  {status}")
        for v in vios:
            print(f"         ↳ {v['type']}: {v.get('details', '')}")

        total_violations += len(vios)

        # Save
        base    = os.path.splitext(os.path.basename(img_path))[0]
        out_path = os.path.join(out_dir, f"{base}_result.jpg")
        cv2.imwrite(out_path, rendered)

    print(f"\nDone! {total_violations} violation(s) found across {len(sources)} image(s).")
    print(f"Results saved to: {out_dir}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Offline test: AI Safety Pipeline (PPE + Fall)")
    parser.add_argument("--source", type=str, default="",
                        help="Path to an image file or folder. Omit for auto random.")
    parser.add_argument("--num", type=int, default=10,
                        help="Number of random images to test when no --source (default: 10)")
    parser.add_argument("--mode", type=str, default="ALL",
                        choices=["ALL", "SAFETY_GEAR", "FALLING_DETECTION", "RESTRICTED_AREA"],
                        help="Detection mode to run")
    args = parser.parse_args()
    run_test(args.source, args.num, args.mode)
