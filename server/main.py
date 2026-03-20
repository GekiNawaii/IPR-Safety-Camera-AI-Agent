import cv2
import argparse
import sys
import os

# Add current directory to path so we can import safety_agent
sys.path.append(os.getcwd())
from safety_agent.agent import SafetyAgent

def main():
    parser = argparse.ArgumentParser(description="AI Safety Agent - Occupational Monitoring")
    parser.add_argument("--source", type=str, required=True, help="Path to image, video file, or camera index")
    parser.add_argument("--model", type=str, default="yolov8n-pose.pt", help="Path to YOLO model weights")
    parser.add_argument("--show", action="store_true", help="Display the output window")
    parser.add_argument("--save", type=str, help="Path to save the processed output")
    
    args = parser.parse_args()
    
    agent = SafetyAgent(model_path=args.model)
    
    # Check if source is image or video
    is_image = args.source.lower().endswith(('.png', '.jpg', '.jpeg'))
    
    if is_image:
        img = cv2.imread(args.source)
        if img is None:
            print(f"Error: Could not read image {args.source}")
            return
            
        result_img = agent.process_image(img)
        
        if args.show:
            cv2.imshow("Safety Agent Analysis", result_img)
            cv2.waitKey(0)
            
        if args.save:
            cv2.imwrite(args.save, result_img)
            print(f"Saved result to {args.save}")
            
    else:
        # Handle Video or Camera
        src = int(args.source) if args.source.isdigit() else args.source
        cap = cv2.VideoCapture(src)
        
        # Setup video writer if save path provided
        writer = None
        if args.save:
            fourcc = cv2.VideoWriter_fourcc(*'mp4v')
            fps = cap.get(cv2.CAP_PROP_FPS)
            w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
            h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
            writer = cv2.VideoWriter(args.save, fourcc, fps, (w, h))

        while cap.isOpened():
            ret, frame = cap.read()
            if not ret: break
            
            result_frame = agent.process_image(frame)
            
            if args.show:
                cv2.imshow("Safety Agent Live Monitoring", result_frame)
                if cv2.waitKey(1) & 0xFF == ord('q'):
                    break
            
            if writer:
                writer.write(result_frame)
                
        cap.release()
        if writer: writer.release()
        cv2.destroyAllWindows()

if __name__ == "__main__":
    main()
