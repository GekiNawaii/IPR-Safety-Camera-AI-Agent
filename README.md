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
   Di chuyển vào thư mục server và cài đặt:
   ```bash
   cd server
   pip install -r requirements.txt
   ```

## Hướng dẫn sử dụng (Quan trọng: Chạy từ thư mục `server/`)

Mọi lệnh dưới đây đều thực hiện sau khi thành viên đã `cd server`.

### 1. Huấn luyện Agent (Training)
- **PPE**: `python train_agent.py --prep-ppe "../datasets/ppe_dataset"`
- **Té ngã**: `python train_agent.py --prep-fall "../datasets/fall_dataset"`

### 2. Kiểm tra Agent (Inference)
Script mặc định sẽ tìm trong thư mục `models/` tại `server/models/`:
- **PPE**: `python test_model.py --source "path/to/img" --model "models/ppe_best.pt"`
- **Té ngã**: `python test_model.py --source "path/to/img" --model "models/fall_best.pt"`

## Cấu trúc thư mục
Dự án được gom hoàn toàn vào thư mục `server/` để tách biệt với `client/`:
- `server/`
  - `models/`: Chứa các file trọng số AI đã được huấn luyện.
  - `safety_agent/`: Logic lõi xử lý Perception, Logic, Actions.
  - `train_agent.py`: Script huấn luyện.
  - `test_model.py`: Script chạy thử.
  - `main.py`: Entry point.
  - `runs/`: Kết quả huấn luyện (bị git bỏ qua).
  - `datasets/`: Dữ liệu (bị git bỏ qua).
