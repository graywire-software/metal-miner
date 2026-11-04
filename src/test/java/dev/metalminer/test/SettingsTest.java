package dev.metalminer.test;

import dev.metalminer.*;
import com.google.gson.Gson;

/** Pure configuration and resolution tests; no Minecraft window required. */
public final class SettingsTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        Gson gson = new Gson();
        var migrated = gson.fromJson("{\"preset\":\"PERFORMANCE\"}", ScalingSettings.class).normalized();
        check(migrated.equals(ScalingSettings.forPreset(Preset.PERFORMANCE)), "Migrate v0.1 config");
        var ultra = ScalingSettings.forPreset(Preset.ULTRA_PERFORMANCE).resolve(3456, 2234);
        check(ultra.inputWidth() == 1152 && ultra.outputWidth() == 3456, "Ultra Performance ratio");
        var hot = ScalingSettings.forPreset(Preset.HOT_GARBAGE).resolve(1920, 1200);
        check(hot.inputWidth() == 240 && hot.inputHeight() == 150, "Hot Garbage ratio");
        var custom = migrated.withOutput(.75, 0);
        var r = custom.resolve(2800, 1800);
        check(custom.preset() == Preset.CUSTOM && r.inputWidth() == 1050 && r.inputHeight() == 675
                && r.outputWidth() == 2100 && r.outputHeight() == 1350 && r.nativeWidth() == 2800, "Independent input/output stages");
        custom = custom.withInput(.5, 540).withOutput(.75, 1080).withFilter(ScalingSettings.PresentationFilter.PIXEL_SHARP).withEdgeSmoothing(true);
        r = custom.resolve(2800, 1800);
        check(r.inputWidth() == 840 && r.inputHeight() == 540 && r.outputWidth() == 1680 && r.outputHeight() == 1080, "Fixed pixels preserve aspect ratio");
        check(gson.fromJson(gson.toJson(custom), ScalingSettings.class).normalized().equals(custom), "Custom round trip");
        var reset = ScalingSettings.forPreset(Preset.PERFORMANCE);
        check(reset.outputScale() == 1 && reset.inputHeight() == 0 && reset.outputHeight() == 0
                && reset.filter() == ScalingSettings.PresentationFilter.SMOOTH && !reset.edgeSmoothing(), "Preset clears every override");
        var legacyCustom = gson.fromJson("{\"preset\":\"CUSTOM\",\"inputScale\":0.25,\"outputScale\":0.75,\"inputHeight\":0,\"outputHeight\":0,\"filter\":\"PIXEL_SHARP\"}", ScalingSettings.class).normalized();
        check(!legacyCustom.edgeSmoothing() && legacyCustom.inputScale() == .25 && legacyCustom.outputScale() == .75
                && legacyCustom.filter() == ScalingSettings.PresentationFilter.PIXEL_SHARP, "Migrate v0.2 Custom without changing rendering");
        check(migrated.withEdgeSmoothing(true).preset() == Preset.CUSTOM, "Smoothing alone selects Custom");
        r = custom.withInput(.5, 9000).withOutput(.75, 8000).resolve(1001, 641);
        check(r.inputWidth() == 1001 && r.inputHeight() == 641 && r.outputWidth() == 1001 && r.outputHeight() == 641, "Clamp large pixel requests to output/window");
        for (int width : new int[]{2, 320, 1001, 1920, 3456}) for (int height : new int[]{2, 240, 641, 1200, 2234}) {
            for (Preset preset : Preset.values()) {
                if (preset == Preset.CUSTOM) continue;
                r = ScalingSettings.forPreset(preset).resolve(width, height);
                check(r.inputWidth() >= 2 && r.inputHeight() >= 2 && r.inputWidth() <= r.outputWidth()
                        && r.inputHeight() <= r.outputHeight() && r.outputWidth() <= width && r.outputHeight() <= height, "Resolution bounds: " + preset);
            }
        }
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, 0, .1249, 1.01}) {
            try { migrated.withInput(bad, 0); throw new AssertionError("Accepted invalid input scale " + bad); }
            catch (IllegalArgumentException expected) { }
        }
        System.out.println("PASS: legacy config, all presets, custom ratios/pixels, bounds, serialization, and reset defaults");
    }
}
