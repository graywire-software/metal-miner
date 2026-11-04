package dev.metalminer;

public enum Preset {
    OFF("Off", 1.0, "Render the world at full resolution."),
    QUALITY("Quality", 2.0 / 3.0, "Sharper detail. Renders about 44% of the pixels."),
    BALANCED("Balanced", 0.58, "A balance of detail and speed. About 34% of the pixels."),
    PERFORMANCE("Performance", 0.5, "Fewer pixels for more speed. About 25% of the pixels."),
    ULTRA_PERFORMANCE("Ultra Performance", 1.0 / 3.0, "Maximum speed. About 11% of the pixels; less fine detail."),
    GARBAGE("Garbage", .25, "25% width and height. Very low detail; about 6% of the pixels."),
    HOT_GARBAGE("Hot Garbage", .125, "12.5% width and height. Extremely low detail; about 2% of the pixels."),
    CUSTOM("Custom", 2.0 / 3.0, "Your advanced resolution and scaling settings.");

    public final String label;
    public final double scale;
    public final String description;
    Preset(String label, double scale, String description) {
        this.label = label; this.scale = scale; this.description = description;
    }
    public int dimension(int output) { return Math.max(2, (int)Math.round(output * scale)); }
}
