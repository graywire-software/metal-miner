package dev.metalminer;

/** Immutable configuration. Zero pixel height means use the corresponding ratio. */
public record ScalingSettings(Preset preset, double inputScale, double outputScale,
                              int inputHeight, int outputHeight, PresentationFilter filter, boolean edgeSmoothing) {
    public enum PresentationFilter {
        SMOOTH("Smooth"), PIXEL_SHARP("Pixel-sharp");
        public final String label;
        PresentationFilter(String label) { this.label = label; }
    }
    public static ScalingSettings forPreset(Preset preset) {
        if (preset == null || preset == Preset.CUSTOM) throw new IllegalArgumentException("Choose a named preset");
        return new ScalingSettings(preset, preset.scale, 1, 0, 0, PresentationFilter.SMOOTH, false);
    }
    public ScalingSettings normalized() {
        // Also migrates the original {"preset":"QUALITY"} configuration.
        if (preset != Preset.CUSTOM) return forPreset(preset);
        if (!Double.isFinite(inputScale) || inputScale < .125 || inputScale > 1 ||
            !Double.isFinite(outputScale) || outputScale < .25 || outputScale > 1 ||
            (inputHeight != 0 && (inputHeight < 64 || inputHeight > 16384)) ||
            (outputHeight != 0 && (outputHeight < 64 || outputHeight > 16384)))
            throw new IllegalArgumentException("Custom resolutions are out of range");
        return new ScalingSettings(Preset.CUSTOM, inputScale, outputScale, inputHeight, outputHeight,
                filter == null ? PresentationFilter.SMOOTH : filter, edgeSmoothing);
    }
    public ScalingSettings withInput(double ratio, int height) {
        return new ScalingSettings(Preset.CUSTOM, ratio, outputScale, height, outputHeight, filter, edgeSmoothing).normalized();
    }
    public ScalingSettings withOutput(double ratio, int height) {
        return new ScalingSettings(Preset.CUSTOM, inputScale, ratio, inputHeight, height, filter, edgeSmoothing).normalized();
    }
    public ScalingSettings withFilter(PresentationFilter value) {
        return new ScalingSettings(Preset.CUSTOM, inputScale, outputScale, inputHeight, outputHeight, value, edgeSmoothing).normalized();
    }
    public ScalingSettings withEdgeSmoothing(boolean value) {
        return new ScalingSettings(Preset.CUSTOM, inputScale, outputScale, inputHeight, outputHeight, filter, value).normalized();
    }
    public Resolution resolve(int nativeWidth, int nativeHeight) {
        if (nativeWidth < 2 || nativeHeight < 2) throw new IllegalArgumentException("Window is too small");
        if (preset == Preset.OFF) return new Resolution(nativeWidth, nativeHeight, nativeWidth, nativeHeight, nativeWidth, nativeHeight);
        double outputRatio = outputHeight > 0 ? Math.min(1, (double)outputHeight / nativeHeight) : outputScale;
        int oh = dimension(nativeHeight, outputRatio), ow = dimension(nativeWidth, outputRatio);
        double inputRatio = inputHeight > 0 ? Math.min(1, (double)inputHeight / oh) : inputScale;
        int ih = dimension(oh, inputRatio), iw = dimension(ow, inputRatio);
        return new Resolution(iw, ih, ow, oh, nativeWidth, nativeHeight);
    }
    private static int dimension(int size, double scale) { return Math.max(2, Math.min(size, (int)Math.round(size * scale))); }
    public record Resolution(int inputWidth, int inputHeight, int outputWidth, int outputHeight, int nativeWidth, int nativeHeight) {
        public String inputLabel() { return inputWidth + " × " + inputHeight; }
        public String outputLabel() { return outputWidth + " × " + outputHeight; }
        public String nativeLabel() { return nativeWidth + " × " + nativeHeight; }
    }
}
