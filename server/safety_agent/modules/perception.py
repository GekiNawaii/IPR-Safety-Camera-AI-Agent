"""
PerceptionModule – Dual-model inference.

Two separate YOLO models are used deliberately:
  • ppe_model  – Fine-tuned detector (ppe_best.pt).  Knows 11 equipment classes
                  such as helmet, vest, gloves, boots, etc.  Used only for PPE.
  • pose_model – Stock YOLOv8 Pose (yolov8n-pose.pt).  Knows "person" + 17 body
                  keypoints.  Used for person localisation and fall detection.

Why not one model?  Training the fall-dataset on ppe_best.pt destroyed the
keypoints – the model forgot how to estimate pose.  Keeping them separate gives
clean keypoints from the stock pose model and accurate PPE boxes from the
specialised detector with zero interference.
"""

from ultralytics import YOLO
import os

# ── PPE class mapping ──────────────────────────────────────────────────────────
# Derived by cross-referencing label images supplied by the user.
# class_6  → person / worker   (large bounding box around the full body)
# class_0  → helmet            (box on top of head, blue label in test image)
# class_2  → safety vest       (large box around torso, white label)
# class_1  → gloves / hands    (small cyan boxes near wrists)
# class_3  → safety boots      (cyan boxes at feet)
# class_4  → hard-hat variant  (second helmet class in some annotations)
# class_5, 7-10 → other items, not currently checked
PPE_CLASS_MAP: dict[int, str] = {
    0:  "helmet",
    1:  "gloves",
    2:  "vest",
    3:  "boots",
    4:  "helmet",   # alternate helmet annotation style
    6:  "person",
}

# Which classes are REQUIRED for a worker to be compliant
REQUIRED_PPE: list[str] = ["helmet", "vest"]


class PerceptionModule:
    """Wraps two YOLO models and exposes a unified detect() API."""

    def __init__(self, ppe_model_path: str):
        # PPE / object detector
        if os.path.exists(ppe_model_path):
            print(f"[Perception] Loading PPE model: {ppe_model_path}")
            self.ppe_model = YOLO(ppe_model_path)
        else:
            print(f"[Perception] PPE model not found at {ppe_model_path}, falling back to yolov8n.pt")
            self.ppe_model = YOLO("yolov8n.pt")

        # Pose model – always the stock YOLOv8-n-pose for keypoints
        print("[Perception] Loading Pose model: yolov8n-pose.pt")
        self.pose_model = YOLO("yolov8n-pose.pt")

    # ──────────────────────────────────────────────────────────────────────────

    def detect(self, image) -> dict:
        """
        Run both models and return a unified payload:

        {
            "persons"  : [{ "bbox": [x1,y1,x2,y2], "keypoints": [[x,y,c], ...] }, ...],
            "ppe_items": [{ "type": "helmet", "bbox": [...], "conf": 0.85 }, ...]
        }

        logic.py consumes this structure to produce violations.
        """
        persons   = self._run_pose(image)
        ppe_items = self._run_ppe(image)
        return {"persons": persons, "ppe_items": ppe_items}

    # ──────────────────────────────────────────────────────────────────────────

    def _run_pose(self, image) -> list[dict]:
        """Detect persons + 17-keypoint skeletons."""
        results = self.pose_model(image, verbose=False)[0]
        persons = []
        for i, box in enumerate(results.boxes):
            cls_id = int(box.cls[0])
            # YOLOv8-pose class 0 = "person"
            if cls_id != 0:
                continue
            conf = float(box.conf[0])
            if conf < 0.35:
                continue
            xyxy = box.xyxy[0].tolist()

            # Keypoints: shape (num_people, 17, 3) – (x, y, confidence)
            keypoints = None
            if results.keypoints is not None and i < len(results.keypoints.data):
                keypoints = results.keypoints.data[i].tolist()

            persons.append({
                "bbox": xyxy,        # [x1, y1, x2, y2]
                "conf": conf,
                "keypoints": keypoints
            })
        return persons

    def _run_ppe(self, image) -> list[dict]:
        """Detect PPE items (helmet, vest, gloves, boots, …)."""
        results = self.ppe_model(image, verbose=False)[0]
        items = []
        for box in results.boxes:
            cls_id = int(box.cls[0])
            label  = PPE_CLASS_MAP.get(cls_id)
            if label is None or label == "person":
                continue           # skip unmapped or the person class itself
            conf = float(box.conf[0])
            if conf < 0.40:
                continue
            items.append({
                "type": label,
                "bbox": box.xyxy[0].tolist(),
                "conf": conf,
            })
        return items
