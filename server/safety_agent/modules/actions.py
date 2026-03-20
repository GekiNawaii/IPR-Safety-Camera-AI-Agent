import cv2

class ActionModule:
    def __init__(self):
        self.log_file = "safety_events.log"

    def handle(self, image, detections, violations):
        """Logs violations and draws debug visuals on the image."""
        for v in violations:
            self._log_event(v)
            
        return self._draw_visuals(image, detections, violations)

    def _log_event(self, violation):
        import datetime
        timestamp = datetime.datetime.now().isoformat()
        log_entry = f"[{timestamp}] {violation['type']} detected at {violation['person_bbox']}\n"
        with open(self.log_file, "a") as f:
            f.write(log_entry)

    def _draw_visuals(self, image, detections, violations):
        canvas = image.copy()
        
        # Draw detections
        for d in detections:
            x1, y1, x2, y2 = map(int, d['bbox'])
            color = (0, 255, 0) # Green for ok
            label = f"{d['class']} {d['confidence']:.2f}"
            cv2.rectangle(canvas, (x1, y1), (x2, y2), color, 2)
            cv2.putText(canvas, label, (x1, y1-10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 2)
            
        # Draw violations in Red
        for v in violations:
            x1, y1, x2, y2 = map(int, v['person_bbox'])
            cv2.rectangle(canvas, (x1, y1), (x2, y2), (0, 0, 255), 3) # Red border
            cv2.putText(canvas, v['type'], (x1, y2+20), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 255), 2)
            
        return canvas
