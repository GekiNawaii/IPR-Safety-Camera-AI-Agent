import os
import glob
from typing import Optional

def get_dataset_classes(dataset_dir: str) -> tuple[int, list[str], str]:
    """Scans YOLO label files to find the maximum class ID and detects the task type."""
    labels_dir = os.path.join(dataset_dir, 'labels', 'train')
    
    max_class_id = -1
    task = "detect"
    if os.path.exists(labels_dir):
        # Scan all label files to find the absolute max class ID
        import typing
        label_files: list[str] = list(glob.glob(os.path.join(labels_dir, '*.txt')))
        for fpath in label_files:
            try:
                with open(fpath, 'r', encoding='utf-8') as f:
                    for line in f:
                        parts = line.strip().split()
                        if parts and parts[0].isdigit():
                            class_id = int(parts[0])
                            if class_id > max_class_id:
                                max_class_id = class_id
                            # Detect task: if more than 5 columns, likely pose (keypoints)
                            if len(parts) > 6:
                                task = "pose"
            except Exception:
                continue
                
    # If no labels found or empty, default to 1 class
    nc = max(1, max_class_id + 1)
    # Generate generic names like ['class_0', 'class_1', ...]
    names = [f"class_{i}" for i in range(nc)]
    return nc, names, task

def generate_yaml(dataset_dir: str) -> Optional[str]:
    """Generates a dataset.yaml file for a pre-formatted YOLO dataset."""
    dataset_dir = os.path.abspath(dataset_dir)
    
    # Check if this is a standard YOLO format
    if not os.path.exists(os.path.join(dataset_dir, 'images', 'train')):
        print(f"Error: dataset at {dataset_dir} does not contain 'images/train'.")
        return None
        
    yaml_path = os.path.join(dataset_dir, 'dataset.yaml')
    
    # Use native paths so YOLO's os.sep string replacement for 'images' -> 'labels' works correctly
    train_path = os.path.join(dataset_dir, 'images', 'train')
    val_path = os.path.join(dataset_dir, 'images', 'val') if os.path.exists(os.path.join(dataset_dir, 'images', 'val')) else train_path
    
    nc, names, task = get_dataset_classes(dataset_dir)
    print(f"Detected {nc} classes and '{task}' task in dataset labels.")
    
    with open(yaml_path, 'w', encoding='utf-8') as f:
        f.write(f"train: {train_path}\n")
        f.write(f"val: {val_path}\n")
        f.write(f"nc: {nc}\n")
        
        # If pose, add kpt_shape
        if task == "pose":
            f.write("kpt_shape: [17, 3]\n")

        # Format names correctly for YAML: ['hat', 'person']
        formatted_names = "[" + ", ".join(f"'{n}'" for n in names) + "]"
        f.write(f"names: {formatted_names}\n")

    print(f"Generated YAML configuration at: {yaml_path}")
    return yaml_path

def prepare_ppe_dataset(source_dir: str) -> tuple[Optional[str], str]:
    """Prepares the PPE dataset. Returns (yaml_path, task)."""
    print(f"Preparing PPE Dataset mapping from {source_dir}...")
    nc, names, task = get_dataset_classes(source_dir)
    yaml_path = generate_yaml(source_dir)
    return yaml_path, task

def prepare_fall_dataset(source_dir: str) -> tuple[Optional[str], str]:
    """Prepares the Fall dataset. Returns (yaml_path, task)."""
    print(f"Preparing Fall Dataset mapping from {source_dir}...")
    nc, names, task = get_dataset_classes(source_dir)
    yaml_path = generate_yaml(source_dir)
    return yaml_path, task
