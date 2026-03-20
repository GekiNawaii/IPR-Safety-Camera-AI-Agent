import os
import glob
import argparse
import random
from ultralytics import YOLO

def test_model():
    parser = argparse.ArgumentParser(description="Test YOLO model on specific or random images")
    parser.add_argument("--source", type=str, help="Path to an image or a folder of images")
    parser.add_argument("--num", type=int, default=10, help="Number of random images to test (default: 10)")
    args = parser.parse_args()

    # 1. Tìm model (Ưu tiên thư mục models/ trước, sau đó mới đến runs/detect/train*)
    model_path = ""
    # Nếu người dùng không truyền --source nhưng có file ppe_best.pt, hãy dùng nó làm mặc định
    default_models = ["models/ppe_best.pt", "models/fall_best.pt"]
    
    train_dirs = glob.glob('runs/detect/train*')
    latest_train = max(train_dirs, key=os.path.getmtime) if train_dirs else None

    # Logic tìm model:
    potential_paths = [
        "models/ppe_best.pt", 
        "models/fall_best.pt"
    ]
    if latest_train:
        potential_paths.append(os.path.join(latest_train, 'weights', 'best.pt'))
        potential_paths.append(os.path.join(latest_train, 'weights', 'last.pt'))

    # Kiểm tra đường dẫn tồn tại
    for p in potential_paths:
        if os.path.exists(p):
            model_path = p
            break
            
    if not model_path:
        print("Error: Không tìm thấy file model trong 'models/' hoặc 'runs/detect/'. Vui lòng train trước.")
        return

    print(f"--- Model Loading ---")
    print(f"Path: {model_path}")
    model = YOLO(model_path)

    # 2. Xác định nguồn ảnh
    if args.source:
        source = args.source
        print(f"Testing on custom source: {source}")
    else:
        # Fallback to random images from dataset
        val_images = glob.glob('datasets/ppe_dataset/data/images/val/*.jpg')
        if not val_images:
            val_images = glob.glob('datasets/ppe_dataset/data/images/train/*.jpg')
        
        if not val_images:
            print("Error: Không tìm thấy ảnh nào trong dataset để test.")
            return
            
        # Lấy file ngẫu nhiên thay vì chỉ lấy [:10] để đa dạng
        source = random.sample(val_images, min(args.num, len(val_images)))
        print(f"Testing on {len(source)} random images from dataset...")

    # 3. Chạy nhận diện và lưu kết quả
    results = model.predict(source=source, save=True, project='runs/detect', name='test_results', exist_ok=True)

    print(f"\nDone! Kết quả đã được lưu tại: runs/detect/test_results")

if __name__ == "__main__":
    test_model()
