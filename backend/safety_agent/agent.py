import cv2
from ultralytics import YOLO
from .modules.perception import PerceptionModule
from .modules.logic import SafetyLogicModule
from .modules.actions import ActionModule

class SafetyAgent:
    def __init__(self, model_path='yolov8n-pose.pt'):
        print(f"Initializing Safety Agent with model: {model_path}")
        self.perception = PerceptionModule(model_path)
        self.logic = SafetyLogicModule()
        self.actions = ActionModule()
        
    def process_image(self, image):
        """Processes a single image frame and returns safety analysis results."""
        # 1. Perception Step
        detections = self.perception.detect(image)
        
        # 2. Logic Step
        violations = self.logic.analyze(detections)
        
        # 3. Action Step
        results = self.actions.handle(image, detections, violations)
        
        return results

    def retrain(self, data_config_path):
        """Interface for retraining the agent's core perception model."""
        print(f"Starting retraining with config: {data_config_path}")
        # This will call the ultralytics training API
        self.perception.model.train(data=data_config_path, epochs=50)
