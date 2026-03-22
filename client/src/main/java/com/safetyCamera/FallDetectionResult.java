package com.safetyCamera;

/**
 * Data contract representing the JSON response from the backend AI server 
 * for the Fall Detection module.
 */
public class FallDetectionResult {
    
    private boolean fallDetected;
    private float confidence;
    private Bbox bbox;

    public static class Bbox {
        private int x;
        private int y;
        private int width;
        private int height;

        public int getX() { return x; }
        public void setX(int x) { this.x = x; }
        
        public int getY() { return y; }
        public void setY(int y) { this.y = y; }
        
        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }
        
        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }
    }

    public boolean isFallDetected() { 
        return fallDetected; 
    }
    public void setFallDetected(boolean fallDetected) { 
        this.fallDetected = fallDetected; 
    }
    
    public float getConfidence() { 
        return confidence; 
    }
    public void setConfidence(float confidence) { 
        this.confidence = confidence; 
    }

    public Bbox getBbox() { 
        return bbox; 
    }
    public void setBbox(Bbox bbox) { 
        this.bbox = bbox; 
    }
}
