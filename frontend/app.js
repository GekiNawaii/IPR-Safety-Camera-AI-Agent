/**
 * HumanTrack — Continuous 24/7 human detection using TensorFlow.js COCO-SSD.
 * All inference runs locally in the browser. Zero data sent to any server.
 *
 * Architecture:
 *  • Render loop  — requestAnimationFrame @ ~60fps: draws camera + last detections
 *  • Detect loop  — setInterval @ configurable ms: runs COCO-SSD, updates cache
 */

// ─── State ─────────────────────────────────────────────────────────────────────
let stream          = null;
let animFrameId     = null;
let detectorModel   = null;    // cocoSsd model instance
let isModelLoaded   = false;
let isDetecting     = false;   // guard: prevent overlapping inference calls
let detectionIntervalId = null;

let lastDetections  = [];      // cached results from last inference pass
let isMirrored      = true;
let showLabels      = true;
let boxPadding      = 8;
let confidenceThresh = 0.50;  // 0–1
let detectIntervalMs = 150;   // ms between inference calls

// FPS / stats
let fpsCounter   = 0;
let fpsLastTime  = performance.now();
let currentFps   = 0;
let totalFrames  = 0;
let detectCount  = 0;         // total inference calls

// Off-screen canvas used for downscaled inference input
const inferCanvas = document.createElement('canvas');
const inferCtx    = inferCanvas.getContext('2d');
const INFER_W     = 640;
const INFER_H     = 360;
inferCanvas.width  = INFER_W;
inferCanvas.height = INFER_H;

// ─── DOM refs ──────────────────────────────────────────────────────────────────
const video       = document.getElementById('video');
const canvas      = document.getElementById('canvas');
const ctx         = canvas.getContext('2d');

const placeholder   = document.getElementById('placeholder');
const statusBadge   = document.getElementById('status-badge');
const statusText    = document.getElementById('status-text');
const modelBadge    = document.getElementById('model-badge');
const modelText     = document.getElementById('model-text');
const hudFps        = document.getElementById('hud-fps');
const hudObjects    = document.getElementById('hud-objects');
const hudRes        = document.getElementById('hud-res');
const btnStart      = document.getElementById('btn-start');
const btnStop       = document.getElementById('btn-stop');
const statObjects   = document.getElementById('stat-objects');
const statFps       = document.getElementById('stat-fps');
const statLargest   = document.getElementById('stat-largest');
const statFrames    = document.getElementById('stat-frames');

// ─── Model Loading ─────────────────────────────────────────────────────────────
async function loadModel() {
  setModelStatus('loading', 'Loading…');
  try {
    // cocoSsd is a global exposed by the CDN script
    detectorModel = await cocoSsd.load({ base: 'lite_mobilenet_v2' });
    isModelLoaded = true;
    setModelStatus('ready', 'Model Ready');
    console.log('[HumanTrack] COCO-SSD model loaded.');
  } catch (err) {
    console.error('[HumanTrack] Model load failed:', err);
    setModelStatus('error', 'Model Error');
  }
}

// Kick off model loading immediately on page load
loadModel();

// ─── Camera ────────────────────────────────────────────────────────────────────
async function startCamera() {
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      video: { width: { ideal: 1280 }, height: { ideal: 720 } },
      audio: false
    });
    video.srcObject = stream;
    await video.play();

    const track    = stream.getVideoTracks()[0];
    const settings = track.getSettings();
    const W = settings.width  || video.videoWidth  || 1280;
    const H = settings.height || video.videoHeight || 720;

    canvas.width  = W;
    canvas.height = H;

    hudRes.textContent = `RES: ${W}×${H}`;
    placeholder.classList.add('hidden');
    btnStart.disabled = true;
    btnStop.disabled  = false;
    setStatus('active', 'Camera On');

    lastDetections = [];

    // Start render loop
    animFrameId = requestAnimationFrame(renderLoop);

    // Start detection loop (waits until model is ready)
    startDetectionLoop();

  } catch (err) {
    console.error('[HumanTrack] Camera error:', err);
    alert(`Could not access camera:\n${err.message}\n\nMake sure you allow camera permissions.`);
  }
}

function stopCamera() {
  if (animFrameId)        { cancelAnimationFrame(animFrameId); animFrameId = null; }
  if (detectionIntervalId){ clearInterval(detectionIntervalId); detectionIntervalId = null; }
  if (stream)             { stream.getTracks().forEach(t => t.stop()); stream = null; }

  video.srcObject = null;
  lastDetections  = [];

  ctx.clearRect(0, 0, canvas.width, canvas.height);
  placeholder.classList.remove('hidden');

  btnStart.disabled = false;
  btnStop.disabled  = true;
  setStatus('idle', 'Idle');
  hudFps.textContent     = 'FPS: --';
  hudObjects.textContent = 'Humans: 0';
  hudRes.textContent     = 'RES: --';
  updateStats(0, 0, '—');
}

// ─── Render Loop (~60 fps) ─────────────────────────────────────────────────────
function renderLoop(timestamp) {
  animFrameId = requestAnimationFrame(renderLoop);
  if (!video.videoWidth) return;

  const W = canvas.width;
  const H = canvas.height;

  // Draw video frame
  ctx.save();
  if (isMirrored) { ctx.translate(W, 0); ctx.scale(-1, 1); }
  ctx.drawImage(video, 0, 0, W, H);
  ctx.restore();

  // Overlay last known detections (updated asynchronously by detect loop)
  drawDetections(lastDetections, W, H);

  // FPS counter
  fpsCounter++;
  totalFrames++;
  const elapsed = timestamp - fpsLastTime;
  if (elapsed >= 1000) {
    currentFps   = Math.round((fpsCounter * 1000) / elapsed);
    fpsCounter   = 0;
    fpsLastTime  = timestamp;
    hudFps.textContent  = `FPS: ${currentFps}`;
    statFps.textContent = currentFps;
    statFrames.textContent = totalFrames;
  }
}

// ─── Detection Loop (async, every detectIntervalMs) ───────────────────────────
function startDetectionLoop() {
  if (detectionIntervalId) clearInterval(detectionIntervalId);

  detectionIntervalId = setInterval(async () => {
    if (!isModelLoaded || isDetecting || !stream || !video.videoWidth) return;

    isDetecting = true;
    try {
      // Downscale video to inference canvas for speed
      inferCtx.save();
      if (isMirrored) {
        inferCtx.translate(INFER_W, 0);
        inferCtx.scale(-1, 1);
      }
      inferCtx.drawImage(video, 0, 0, INFER_W, INFER_H);
      inferCtx.restore();

      // Run detection; returns [{bbox:[x,y,w,h], class, score}, ...]
      const raw = await detectorModel.detect(inferCanvas);

      // Filter for persons above confidence threshold
      const scaleX = canvas.width  / INFER_W;
      const scaleY = canvas.height / INFER_H;

      lastDetections = raw
        .filter(d => d.class === 'person' && d.score >= confidenceThresh)
        .map(d => ({
          x:     d.bbox[0] * scaleX,
          y:     d.bbox[1] * scaleY,
          w:     d.bbox[2] * scaleX,
          h:     d.bbox[3] * scaleY,
          score: d.score
        }));

      detectCount++;
      updateHUD();

    } catch (err) {
      console.warn('[HumanTrack] Detect error:', err);
    } finally {
      isDetecting = false;
    }
  }, detectIntervalMs);
}

function updateHUD() {
  const n       = lastDetections.length;
  const largest = n > 0
    ? Math.round(Math.max(...lastDetections.map(d => d.w * d.h)))
    : 0;

  hudObjects.textContent = `Humans: ${n}`;
  updateStats(n, currentFps, n ? largest : '—');

  if (n > 0) setStatus('tracking', `Tracking ${n > 1 ? n + ' humans' : '1 human'}`);
  else       setStatus('active',   'Camera On');
}

// ─── Draw Detections ──────────────────────────────────────────────────────────
function drawDetections(detections, W, H) {
  if (!detections.length) return;
  ctx.save();

  for (const d of detections) {
    const bx = Math.max(0, d.x - boxPadding);
    const by = Math.max(0, d.y - boxPadding);
    const bw = Math.min(W - bx, d.w + boxPadding * 2);
    const bh = Math.min(H - by, d.h + boxPadding * 2);
    const pct = Math.round(d.score * 100);

    // ── Glow pass ──
    ctx.shadowColor = '#00e676';
    ctx.shadowBlur  = 14;
    ctx.strokeStyle = '#00e676';
    ctx.lineWidth   = 2.5;
    ctx.strokeRect(bx, by, bw, bh);

    // ── Crisp pass ──
    ctx.shadowBlur  = 0;
    ctx.strokeStyle = 'rgba(0,230,118,0.90)';
    ctx.lineWidth   = 2;
    ctx.strokeRect(bx, by, bw, bh);

    // ── Corner accents ──
    drawCorners(bx, by, bw, bh, 16, ctx);

    // ── Label ──
    if (showLabels) {
      const label    = `HUMAN  ${Math.round(bw)}×${Math.round(bh)} px  ${pct}%`;
      ctx.font       = '600 11px JetBrains Mono, monospace';
      ctx.textBaseline = 'bottom';

      const tw = ctx.measureText(label).width;
      const tx = Math.min(bx, W - tw - 6);
      const ty = Math.max(by - 2, 14);

      // label background
      ctx.fillStyle = 'rgba(0,0,0,0.70)';
      ctx.fillRect(tx - 4, ty - 14, tw + 10, 17);

      ctx.fillStyle = '#00e676';
      ctx.fillText(label, tx + 1, ty + 1);
    }
  }

  ctx.restore();
}

/** L-shaped corner accent marks */
function drawCorners(x, y, w, h, len, c) {
  c.strokeStyle = '#ffffff';
  c.lineWidth   = 2.5;
  c.shadowBlur  = 0;

  const corners = [
    [x,   y,    len, 0,   x,   y,   0,    len],
    [x+w, y,   -len, 0,   x+w, y,   0,    len],
    [x,   y+h,  len, 0,   x,   y+h, 0,   -len],
    [x+w, y+h, -len, 0,   x+w, y+h, 0,   -len],
  ];

  for (const [x1, y1, dx1, dy1, x2, y2, dx2, dy2] of corners) {
    c.beginPath(); c.moveTo(x1, y1); c.lineTo(x1+dx1, y1+dy1); c.stroke();
    c.beginPath(); c.moveTo(x2, y2); c.lineTo(x2+dx2, y2+dy2); c.stroke();
  }
}

// ─── UI Callbacks ─────────────────────────────────────────────────────────────
function toggleLabels(enabled)  { showLabels = enabled; }
function toggleMirror(enabled)  { isMirrored = enabled; lastDetections = []; }

function updateConfidence(val) {
  confidenceThresh = parseFloat(val);
  document.getElementById('val-confidence').textContent = `${Math.round(val * 100)}%`;
  lastDetections = [];
}

function updateDetectInterval(val) {
  detectIntervalMs = parseInt(val, 10);
  document.getElementById('val-interval').textContent = `${val}ms`;
  // Restart detect loop with new interval
  if (stream) startDetectionLoop();
}

function updatePadding(val) {
  boxPadding = parseInt(val, 10);
  document.getElementById('val-padding').textContent = val;
}

// ─── Status Helpers ───────────────────────────────────────────────────────────
function setStatus(state, text) {
  statusBadge.className  = `status-badge ${state}`;
  statusText.textContent = text;
}

function setModelStatus(state, text) {
  modelBadge.className  = `model-badge ${state}`;
  modelText.textContent = text;
}

function updateStats(humans, fps, largest) {
  statObjects.textContent = humans;
  statFps.textContent     = fps || currentFps;
  statLargest.textContent = largest;
  statFrames.textContent  = totalFrames;
}
