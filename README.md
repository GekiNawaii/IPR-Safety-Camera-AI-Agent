# AI Safety Agent - Server (Backend)

Hệ thống giám sát an toàn lao động dựa trên trí tuệ nhân tạo (YOLOv8). Dự án này bao gồm các tính năng nhận diện trang thiết bị bảo hộ (PPE) và phát hiện té ngã.

## Tính năng chính
- **Nhận diện PPE**: Tự động phát hiện công nhân, mũ bảo hiểm và các trang bị bảo hộ khác.
- **Phát hiện té ngã**: Sử dụng mô hình tư thế (Pose) và logic tọa độ để cảnh báo khi có người bị ngã.
- **Huấn luyện linh hoạt**: Tự động hóa việc chuẩn bị dữ liệu và huấn luyện nối tiếp (Resumable Training).

## Yêu cầu hệ thống
- **Python**: 3.11+
- **Thư viện**: Xem trong `backend/requirements.txt`

## Hướng dẫn cài đặt

1. **Clone repository**:
   ```bash
   git clone <URL_REPO>
   git checkout server
   ```

2. **Cài đặt thư viện**:
   ```bash
   pip install -r backend/requirements.txt
   ```

## Hướng dẫn sử dụng

### 1. Huấn luyện Agent (Training)
Nếu bạn có dataset mới, hãy nạp vào thư mục `datasets/` và chạy:
- **PPE**: `python backend/train_agent.py --prep-ppe "đường/dẫn/dataset"`
- **Té ngã**: `python backend/train_agent.py --prep-fall "đường/dẫn/dataset"`

### 2. Kiểm tra Agent (Inference)
Sử dụng script `test_model.py` để chạy nhận diện lên các ảnh mẫu. Script mặc định sẽ tìm trong thư mục `models/`:
- **PPE**: `python test_model.py --source "path/to/img" --model "models/ppe_best.pt"`
- **Té ngã**: `python test_model.py --source "path/to/img" --model "models/fall_best.pt"`

## Cấu trúc thư mục
- `backend/`: Chứa mã nguồn chính của AI Agent.
  - `safety_agent/`: Logic lõi xử lý Perception, Logic, Actions.
  - `train_agent.py`: Script huấn luyện.
  - `main.py`: Entry point của server.
- `models/`: **Quan trọng** - Chứa các file trọng số AI đã được huấn luyện (`ppe_best.pt`, `fall_best.pt`).
- `datasets/`: (Tùy chọn) Chứa dữ liệu huấn luyện.
- `runs/`: (Bị bỏ qua bởi git) Chứa kết quả huấn luyện và các file `.pt`.
