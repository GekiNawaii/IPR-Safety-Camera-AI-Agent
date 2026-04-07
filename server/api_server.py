from fastapi import FastAPI, File, UploadFile, Form
from fastapi.responses import FileResponse
import uvicorn
import cv2
import numpy as np
import os
import sys
import tempfile

sys.path.append(os.getcwd())
from safety_agent.agent import SafetyAgent

app = FastAPI(title="IPR Safety Camera - AI API")

# ── Load models once at server startup ────────────────────────────────────────
PPE_MODEL = "models/ppe_best.pt"
if not os.path.exists(PPE_MODEL):
    PPE_MODEL = "yolov8n.pt"   # graceful fallback if model file missing

print(f"--- API Server Starting ---")
print(f"PPE  model : {PPE_MODEL}")
print(f"Pose model : yolov8n-pose.pt (always stock)")
agent = SafetyAgent(model_path=PPE_MODEL)


# ── /detect  ──────────────────────────────────────────────────────────────────
@app.post("/detect")
async def detect(file: UploadFile = File(...), mode: str = Form("SAFETY_GEAR")):
    """
    Receive a JPEG frame from the Java client.
    'mode' controls which pipeline runs:
      SAFETY_GEAR       → MISSING_PPE violations only
      FALLING_DETECTION → FALL_DETECTED violations only
      others            → all violations
    """
    contents = await file.read()
    nparr    = np.frombuffer(contents, np.uint8)
    img      = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    if img is None:
        return {"error": "Could not decode image", "violations": []}

    m = mode.strip().upper()
    data       = agent.perception.detect(img)
    violations = agent.logic.analyze(data, mode=m)

    return {"violations": violations}




# ── /detect-video ─────────────────────────────────────────────────────────────
@app.post("/detect-video")
async def detect_video(file: UploadFile = File(...)):
    """
    Receive an uploaded video file, run AI on every frame, and return a summary.
    """
    suffix = os.path.splitext(file.filename)[-1] or ".mp4"
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp_in:
        tmp_in.write(await file.read())
        input_path = tmp_in.name

    cap = cv2.VideoCapture(input_path)
    if not cap.isOpened():
        return {"error": "Could not open video file"}

    fps        = cap.get(cv2.CAP_PROP_FPS) or 25
    total_frames = 0
    events     = []

    while True:
        ok, frame = cap.read()
        if not ok:
            break
        total_frames += 1
        if total_frames % 5 != 0:   # process every 5th frame for speed
            continue

        data       = agent.perception.detect(frame)
        violations = agent.logic.analyze(data)

        for v in violations:
            if not v.get("safe", True):
                events.append({
                    "type"    : v["type"],
                    "time_sec": round(total_frames / fps, 1),
                    "details" : v.get("details", []),
                })

    cap.release()
    os.unlink(input_path)

    return {
        "total_frames"    : total_frames,
        "total_violations": len(events),
        "events"          : events,
    }


# ── /download-video ───────────────────────────────────────────────────────────
@app.get("/download-video/{filename}")
async def download_video(filename: str):
    path = os.path.join(tempfile.gettempdir(), filename)
    if not os.path.exists(path):
        return {"error": "File not found"}
    return FileResponse(path, media_type="video/mp4", filename=filename)


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
