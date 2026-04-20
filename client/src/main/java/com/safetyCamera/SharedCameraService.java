package com.safetyCamera;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A central service that reads from hardware cameras or video files 
 * exactly ONCE, and clones the frames to multiple subscribers.
 * This prevents OpenCV hardware lock contention when a user assigns 
 * multiple detection panels to the same underlying camera.
 */
public class SharedCameraService {

    private static final ConcurrentHashMap<String, StreamTask> streams = new ConcurrentHashMap<>();

    public interface FrameListener {
        void onFrame(Mat frame);
        void onError(String message);
    }

    public static synchronized void subscribe(String source, FrameListener listener) {
        StreamTask task = streams.computeIfAbsent(source, StreamTask::new);
        task.addListener(listener);
        if (!task.isRunning()) {
            task.start();
        }
    }

    public static synchronized void unsubscribe(String source, FrameListener listener) {
        StreamTask task = streams.get(source);
        if (task != null) {
            task.removeListener(listener);
            if (task.isEmpty()) {
                task.stop();
                streams.remove(source);
            }
        }
    }

    private static class StreamTask {
        private final String source;
        private final List<FrameListener> listeners = new CopyOnWriteArrayList<>();
        private final AtomicBoolean running = new AtomicBoolean(false);
        private Thread thread;
        
        public StreamTask(String source) { this.source = source; }
        public void addListener(FrameListener fl) { listeners.add(fl); }
        public void removeListener(FrameListener fl) { listeners.remove(fl); }
        public boolean isEmpty() { return listeners.isEmpty(); }
        public boolean isRunning() { return running.get(); }
        
        public void start() {
            running.set(true);
            thread = new Thread(this::loop, "Stream-" + source.replaceAll("[^a-zA-Z0-9]", ""));
            thread.setDaemon(true);
            thread.start();
        }
        
        public void stop() {
            running.set(false);
            if (thread != null) thread.interrupt();
        }
        
        private void loop() {
            VideoCapture capture = null;
            IpCameraCapture ipCapture = null;
            long frameInterval = 66; // max ~15 FPS
            
            try {
                boolean ready = false;
                if (source.startsWith("http")) {
                    ipCapture = new IpCameraCapture(source);
                    ready = ipCapture.isConnected();
                } else {
                    capture = new VideoCapture();
                    ready = source.matches("\\d+")
                        ? capture.open(Integer.parseInt(source))
                        : capture.open(source);
                }
                
                if (!ready) {
                    notifyError("Failed to connect to source");
                    return;
                }
                if (capture != null) {
                    // Set low res at hardware level to allow many streams smoothly
                    capture.set(Videoio.CAP_PROP_FRAME_WIDTH, 640);
                    capture.set(Videoio.CAP_PROP_FRAME_HEIGHT, 480);
                }
                
                Mat frame = new Mat();
                while (running.get()) {
                    long start = System.currentTimeMillis();
                    
                    boolean success = false;
                    if (ipCapture != null) {
                        Mat temp = ipCapture.readFrame();
                        if (temp != null && !temp.empty()) {
                            temp.copyTo(frame);
                            temp.release();
                            success = true;
                        }
                    } else {
                        if (capture.read(frame) && !frame.empty()) {
                            success = true;
                        } else if (!source.matches("\\d+") && !source.startsWith("http")) {
                            // Video file ended, loop back to the beginning
                            capture.set(Videoio.CAP_PROP_POS_FRAMES, 0);
                            Thread.sleep(30); 
                            continue;
                        } else {
                            // Hardware disconnect
                            notifyError("Camera connection lost.");
                            break;
                        }
                    }
                    
                    if (success) {
                        for (FrameListener l : listeners) {
                            l.onFrame(frame.clone());  // Clone for thread isolation
                        }
                    }

                    long elapsed = System.currentTimeMillis() - start;
                    long sleep = frameInterval - elapsed;
                    if (sleep > 0) {
                        Thread.sleep(sleep);
                    }
                }
            } catch (InterruptedException e) {
                // Stopped intentionally
            } catch (Exception e) {
                notifyError("Stream error: " + e.getMessage());
            } finally {
                if (capture != null && capture.isOpened()) capture.release();
                if (ipCapture != null) ipCapture.release();
            }
        }
        
        private void notifyError(String err) {
            for (FrameListener l : listeners) l.onError(err);
        }
    }
}
