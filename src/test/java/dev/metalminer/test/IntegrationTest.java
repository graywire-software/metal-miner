package dev.metalminer.test;

import dev.metalminer.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.nio.file.*;
import net.irisshaders.iris.Iris;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.input.KeyEvent;
import java.util.List;
import com.google.gson.Gson;

/** Development-only test mod. Never included in the release JAR. */
public final class IntegrationTest implements ClientModInitializer {
    public static int worldFrames;
    private static final boolean BENCHMARK = Boolean.getBoolean("metalminer.benchmark");
    private boolean started;
    private static final ScalingSettings BASE = ScalingSettings.forPreset(Preset.PERFORMANCE);
    private static final ScalingSettings[] CASES = {
        ScalingSettings.forPreset(Preset.QUALITY), ScalingSettings.forPreset(Preset.ULTRA_PERFORMANCE),
        ScalingSettings.forPreset(Preset.GARBAGE), ScalingSettings.forPreset(Preset.HOT_GARBAGE),
        BASE.withOutput(.75, 0).withEdgeSmoothing(true), BASE.withInput(.5, 540).withOutput(1, 1080).withEdgeSmoothing(true),
        BASE.withInput(.5, 360).withOutput(1, 720).withFilter(ScalingSettings.PresentationFilter.PIXEL_SHARP).withEdgeSmoothing(true),
        BASE, BASE.withOutput(1, 900), ScalingSettings.forPreset(Preset.OFF), ScalingSettings.forPreset(Preset.QUALITY)
    };
    private static final ScalingSettings[] BENCH_CASES = {
        BASE, ScalingSettings.forPreset(Preset.ULTRA_PERFORMANCE), ScalingSettings.forPreset(Preset.GARBAGE),
        ScalingSettings.forPreset(Preset.HOT_GARBAGE), BASE.withOutput(1, 1080), BASE.withOutput(1, 900)
    };
    private int step;
    private int ticks;
    private boolean captured;
    private long sampleStart;
    private int sampleFrames;

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!started && client.gui.screen() instanceof TitleScreen && client.gui.overlay() == null) {
                started = true;
                if (BENCHMARK) MetalMiner.select(Preset.PERFORMANCE);
                if (Files.exists(client.gameDirectory.toPath().resolve("saves/MetalMiner_Test/level.dat"))) {
                    client.createWorldOpenFlows().openWorld("MetalMiner_Test", () -> {});
                } else {
                    client.createWorldOpenFlows().createFreshLevel("MetalMiner_Test", MinecraftServer.DEMO_SETTINGS,
                            WorldOptions.DEMO_OPTIONS, WorldPresets::createNormalWorldDimensions, client.gui.screen());
                }
            }
            if (client.level == null || client.player == null || worldFrames < 30) return;
            ticks++;
            if (BENCHMARK) {
                client.player.setYRot(0); client.player.setXRot(15);
                if (ticks == 1) client.getWindow().setWindowed(1400, 900);
                if (ticks == 120) { sampleStart = System.nanoTime(); sampleFrames = worldFrames; }
                if (ticks == 220) {
                    double seconds = (System.nanoTime() - sampleStart) / 1e9;
                    double fps = (worldFrames - sampleFrames) / seconds;
                    MetalMiner.LOG.info("BENCHMARK {}: {} FPS, {} ms/frame at {}x{}", MetalMiner.preset(),
                            String.format("%.1f", fps), String.format("%.2f", 1000 / fps), client.getWindow().getWidth(), client.getWindow().getHeight());
                    MetalMiner.LOG.info("BENCHMARK case {} settings {}", step, MetalMiner.settings());
                    Screenshot.grab(client.gameDirectory, "benchmark-v0.2-" + step + "-" + MetalMiner.preset() + ".png", client.gameRenderer.mainRenderTarget(), 1, message -> {});
                }
                if (ticks >= 240) {
                    step++;
                    if (step >= BENCH_CASES.length) { MetalMiner.LOG.info("BENCHMARK COMPLETE"); client.stop(); }
                    else { apply(BENCH_CASES[step]); ticks = 0; }
                }
                return;
            }
            if (ticks == 120 && !captured) {
                captured = true;
                Screenshot.grab(client.gameDirectory, "test-" + step + "-" + MetalMiner.preset() + ".png",
                        client.gameRenderer.mainRenderTarget(), 1, message -> MetalMiner.LOG.info("TEST screenshot: {}", message.getString()));
                MetalMiner.LOG.info("TEST preset {} rendered {} world frames; {}", MetalMiner.preset(), worldFrames, MetalMiner.status);
            }
            if (step == 0) testUi(client);
            if (ticks >= 160) {
                step++;
                if (step >= CASES.length) {
                    MetalMiner.LOG.info("TEST PASSED v1.0.0: unified dropdown UI, keyboard/mouse, conditional Custom, presets/reset/persistence, post-upscale smoothing, native UI and resize");
                    client.stop();
                } else {
                    apply(CASES[step]);
                    if (step == 7 && !MetalMiner.settings().equals(BASE)) throw new AssertionError("Preset did not reset advanced settings");
                    if (step == 8) client.getWindow().setWindowed(1001, 641);
                    if (step == 10) {
                        try { Iris.reload(); } catch (Exception e) { throw new RuntimeException(e); }
                    }
                    ticks = 0; captured = false;
                }
            }
        });
    }
    private SettingsScreen ui;
    private void testUi(Minecraft client) {
        if (ticks == 30) {
            check(MetalMiner.preset() == Preset.BALANCED, "Fresh install should default to Balanced");
            ui = new SettingsScreen(null);
            client.gui.setScreen(ui);
            check(fields().isEmpty(), "Basic should hide advanced controls");
            check(ui.children().size() == 3, "Basic should contain only the two dropdowns and Done");
        }
        if (ticks == 40) {
            capture(client, "basic");
            click(ui, "Balanced  ▼");
            check(!hasButton("Custom") && !hasButton("✓ Custom"), "Custom should be absent initially");
            check(!hasButton("Garbage") && !hasButton("Hot Garbage"), "Basic should hide extreme presets");
        }
        if (ticks == 45) {
            capture(client, "presets");
            ui.keyPressed(key(41)); // Escape closes only the menu.
            check(client.gui.screen() == ui && !hasButton("Hot Garbage"), "Escape should dismiss dropdown only");
            click(ui, "Basic  ▼");
        }
        if (ticks == 50) {
            capture(client, "settings-menu");
            ui.keyPressed(key(81)); // Down from Basic, then Enter to expand Advanced.
            ui.keyPressed(key(40));
            check(client.gui.screen() == ui && fields().size() == 2, "Advanced must expand on the same screen");
            check(MetalMiner.preset() == Preset.BALANCED, "Opening Advanced changed preset");
            check(MetalMiner.advancedView() && savedView(client), "Advanced view must be saved");
        }
        if (ticks == 55) {
            capture(client, "advanced-balanced");
            for (Preset preset : new Preset[]{Preset.ULTRA_PERFORMANCE, Preset.GARBAGE, Preset.HOT_GARBAGE, Preset.PERFORMANCE}) {
                click(ui, MetalMiner.preset().label + "  ▼");
                click(ui, preset.label);
                check(fields().size() == 2 && client.gui.screen() == ui, "Preset must keep Advanced expanded");
                check(Math.abs(Double.parseDouble(fields().getFirst().getValue()) - preset.scale * 100) < .06,
                        "Preset should update visible scale");
            }
            // Click outside a popup on a covered control: it must only close the menu.
            click(ui, "Performance  ▼");
            var value = MetalMiner.settings();
            clickAt(ui, ui.width - 4, 132);
            check(MetalMiner.settings().equals(value), "Click outside dropdown changed settings");
            fields().get(1).setValue("bad");
            check(MetalMiner.preset() == Preset.PERFORMANCE, "Invalid edit should not select Custom");
            fields().get(1).setValue("75");
            check(MetalMiner.preset() == Preset.CUSTOM, "Valid edit should select Custom immediately");
            fields().get(0).setValue("50");
            click(ui, "Edge smoothing: Off");
            check(MetalMiner.settings().edgeSmoothing(), "Smoothing control should work");
            click(ui, "Custom  ▼");
            check(hasButton("✓ Custom"), "Custom must appear while active");
        }
        if (ticks == 60) {
            capture(client, "custom-menu");
            click(ui, "✓ Custom");
            var value = MetalMiner.settings();
            check(value.preset() == Preset.CUSTOM && fields().size() == 2,
                    "Selecting Custom in Advanced must preserve its settings");
        }
        if (ticks == 85) {
            capture(client, "advanced-custom");
            try {
                var saved = new Gson().fromJson(Files.readString(client.gameDirectory.toPath().resolve("config/metal-miner.json")), ScalingSettings.class).normalized();
                check(saved.equals(MetalMiner.settings()) && saved.edgeSmoothing(), "Custom and smoothing must persist");
            } catch (java.io.IOException e) { throw new RuntimeException(e); }
            click(ui, "Advanced  ▼"); click(ui, "Basic");
            check(fields().isEmpty() && MetalMiner.settings().equals(ScalingSettings.forPreset(Preset.BALANCED)),
                    "Custom must return to Balanced on Basic");
            click(ui, "Balanced  ▼");
            check(!hasButton("Garbage") && !hasButton("Hot Garbage") && !hasButton("Custom"),
                    "Basic must hide advanced-only presets");
            ui.keyPressed(key(41));
            click(ui, "Basic  ▼"); click(ui, "Advanced");
            check(fields().size() == 2 && MetalMiner.preset() == Preset.BALANCED,
                    "Returning to Advanced must preserve the Basic reset");
            for (Preset extreme : new Preset[]{Preset.GARBAGE, Preset.HOT_GARBAGE}) {
                click(ui, "Balanced  ▼"); click(ui, extreme.label);
                click(ui, "Advanced  ▼"); click(ui, "Basic");
                check(MetalMiner.settings().equals(ScalingSettings.forPreset(Preset.BALANCED)),
                        extreme + " must return to Balanced on Basic");
                click(ui, "Basic  ▼"); click(ui, "Advanced");
            }
            click(ui, "Balanced  ▼"); click(ui, "Performance");
            check(MetalMiner.settings().equals(BASE) && fields().get(1).getValue().equals("100"), "Named preset must reset all controls");
            click(ui, "Performance  ▼");
            check(!hasButton("Custom") && !hasButton("✓ Custom"), "Custom must disappear after a named preset");
            ui.keyPressed(key(41));
            click(ui, "Edge smoothing: Off");
            check(MetalMiner.preset() == Preset.CUSTOM, "Smoothing alone must select Custom");
            click(ui, "Custom  ▼"); click(ui, "Quality");
        }
        if (ticks == 100) {
            capture(client, "advanced-reset");
            ui.onClose();
            check(client.gui.screen() == null, "Done must return to parent");
            ui = new SettingsScreen(null);
            client.gui.setScreen(ui);
            check(fields().size() == 2 && savedView(client), "Reopening must retain Advanced view");
        }
        if (ticks == 110) {
            click(ui, "Advanced  ▼"); click(ui, "Basic");
            check(fields().isEmpty() && ui.children().size() == 3 && !savedView(client), "Basic view must be saved");
            ui.onClose();
            ui = new SettingsScreen(null);
            client.gui.setScreen(ui);
            check(fields().isEmpty() && ui.children().size() == 3, "Reopening must retain Basic view");
            ui.onClose();
        }
    }
    private static boolean savedView(Minecraft client) {
        try {
            var json = new Gson().fromJson(Files.readString(client.gameDirectory.toPath().resolve("config/metal-miner-ui.json")),
                    com.google.gson.JsonObject.class);
            return json.get("advanced").getAsBoolean();
        } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }
    private static KeyEvent key(int scancode) { return new KeyEvent(scancode, 0, 0); }
    private List<EditBox> fields() {
        return ui.children().stream().filter(x -> x instanceof EditBox).map(x -> (EditBox)x).toList();
    }
    private boolean hasButton(String label) {
        return ui.children().stream().anyMatch(x -> x instanceof Button b && b.getMessage().getString().equals(label));
    }
    private static void click(Screen screen, String label) {
        Button button = screen.children().stream().filter(x -> x instanceof Button b && b.getMessage().getString().equals(label))
                .map(x -> (Button)x).findFirst().orElseThrow(() -> new AssertionError("Missing button " + label));
        clickAt(screen, button.getX() + button.getWidth() / 2, button.getY() + button.getHeight() / 2);
    }
    private static void clickAt(Screen screen, double x, double y) {
        var event = new MouseButtonEvent(x, y, new MouseButtonInfo(1, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    private static void capture(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, "test-v1.0-" + name + ".png", client.gameRenderer.mainRenderTarget(), 1, message -> {});
    }
    private static void apply(ScalingSettings settings) {
        if (settings.preset() == Preset.CUSTOM) MetalMiner.custom(settings);
        else MetalMiner.select(settings.preset());
    }
}
