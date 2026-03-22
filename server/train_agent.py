from ultralytics import YOLO  # type: ignore
import argparse
import os
import sys


# Add current directory to path
sys.path.append(os.getcwd())
try:
    from safety_agent.utils.data_prep import prepare_ppe_dataset, prepare_fall_dataset  # type: ignore
except ImportError:
    print("Warning: Could not import data_prep utilities. Make sure you are running from the project root.")

def train_agent(model_path, data_yaml, epochs=50, imgsz=640, resume=False):
    """
    Retrains the AI Agent core model.
    """
    import time
    
    if not os.path.exists(data_yaml):
        print(f"Error: Dataset configuration file not found at {data_yaml}")
        return
        
    print(f"\n--- Retraining Agent Perception Layer ---")
    print(f"Base Model: {model_path}")
    print(f"Dataset Config: {data_yaml}")
    print(f"Resume Training: {resume}")
    
    # Load the base model
    model = YOLO(model_path)
    
    # Use a unique run name per training session to avoid YOLO re-reading an old broken args.yaml
    # Unless we are resuming, then we might want to stay in the same folder (but usually unique is safer)
    run_name = f"train_{int(time.time())}"
    
    # Start training - exist_ok=True prevents name collision errors
    model.train(data=data_yaml, epochs=epochs, imgsz=imgsz, name=run_name, exist_ok=True, resume=resume)
    
    print(f"\nTraining complete. Model saved in 'runs/detect/{run_name}/weights/best.pt'")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Train/Fine-tune AI Safety Agent")
    parser.add_argument("--data", type=str, help="Path to an existing data.yaml file")
    parser.add_argument("--model", type=str, default="yolov8n-pose.pt", help="Path to base model weights")
    parser.add_argument("--epochs", type=int, default=50, help="Number of training epochs")
    parser.add_argument("--resume", action="store_true", help="Resume training from a checkpoint (model path)")
    
    # Custom Dataset Adapters
    parser.add_argument("--prep-ppe", type=str, help="Path to Hard Hat/PPE Dataset root (converts Pascal VOC to YOLO)")
    parser.add_argument("--prep-fall", type=str, help="Path to Fall Dataset root (Images/Labels structure)")
    parser.add_argument("--ppe-classes", type=str, default="hat,person", help="Comma-separated class names for PPE (e.g. hat,person)")
    
    args = parser.parse_args()
    
    yaml_path = args.data
    
    # Handle PPE Dataset Prep
    if args.prep_ppe:
        yaml_path, task = prepare_ppe_dataset(args.prep_ppe)
        # Only auto-switch if the model is still the default base model
        if not args.resume and args.model in ["yolov8n.pt", "yolov8n-pose.pt"]:
            if task == "detect":
                setattr(args, 'model', "yolov8n.pt")
                print("Auto-switched base model to 'yolov8n.pt' for standard Object Detection.")
            else:
                setattr(args, 'model', "yolov8n-pose.pt")
                print("Auto-switched base model to 'yolov8n-pose.pt' for Pose Estimation.")
        
    # Handle Fall Dataset Prep
    elif args.prep_fall:
        yaml_path, task = prepare_fall_dataset(args.prep_fall)
        # Only auto-switch if the model is still the default base model
        if not args.resume and args.model in ["yolov8n.pt", "yolov8n-pose.pt"]:
            if task == "pose":
                setattr(args, 'model', "yolov8n-pose.pt")
                print("Auto-switched base model to 'yolov8n-pose.pt' for Pose Estimation.")
            else:
                setattr(args, 'model', "yolov8n.pt")
                print("Auto-switched base model to 'yolov8n.pt' for standard Object Detection (No keypoints found).")
        
    if yaml_path:
        train_agent(str(getattr(args, 'model', 'yolov8n.pt')), yaml_path, getattr(args, 'epochs', 50), resume=args.resume)
    else:
        print("Error: You must provide --data, --prep-ppe, or --prep-fall")
        parser.print_help()
