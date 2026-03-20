from ultralytics import YOLO

class PerceptionModule:
    def __init__(self, model_path):
        # Load YOLO model (defaulting to pose to support fall detection)
        self.model = YOLO(model_path)
        
    def detect(self, image):
        """Runs inference on the image and returns structured detection objects."""
        results = self.model(image, verbose=False)[0]
        
        detections = []
        for box in results.boxes:
            # Structuring data for easier logic processing
            cls_id = int(box.cls[0])
            name = results.names[cls_id]
            conf = float(box.conf[0])
            xyxy = box.xyxy[0].tolist()
            
            # Keypoints for pose (if available)
            keypoints = None
            if results.keypoints is not None:
                # Get keypoints for this specific box index
                idx = box.id if box.id is not None else 0 # Simple mapping if no tracker
                # In YOLOv8, box and keypoints are usually ordered the same in the results object
                keypoints = results.keypoints.data[len(detections)].tolist()

            detections.append({
                'class': name,
                'confidence': conf,
                'bbox': xyxy,
                'keypoints': keypoints
            })
            
        return detections
