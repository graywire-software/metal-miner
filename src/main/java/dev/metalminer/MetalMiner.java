package dev.metalminer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.io.IOException;

public final class MetalMiner implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("Metal Miner");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("metal-miner.json");
    private static final Path UI_CONFIG = FabricLoader.getInstance().getConfigDir().resolve("metal-miner-ui.json");
    private record UiState(boolean advanced) {}
    private static volatile boolean advancedView;
    private static volatile ScalingSettings settings = ScalingSettings.forPreset(Preset.BALANCED);
    private static volatile ScalingSettings appliedSettings = settings;
    private static long pendingSince;
    public static volatile String status = "Ready. Enter a world to start MetalFX.";

    @Override public void onInitializeClient() {
        if (Files.exists(CONFIG)) {
            try {
                settings = GSON.fromJson(Files.readString(CONFIG), ScalingSettings.class).normalized();
                appliedSettings = settings;
            } catch (IOException | RuntimeException error) {
                settings = appliedSettings = ScalingSettings.forPreset(Preset.OFF);
                status = "Invalid settings. Choose a preset to reset them.";
                LOG.warn("Cannot read settings; using native resolution", error);
            }
        }
        if (Files.exists(UI_CONFIG)) {
            try {
                UiState saved = GSON.fromJson(Files.readString(UI_CONFIG), UiState.class);
                advancedView = saved != null && saved.advanced();
            } catch (IOException | RuntimeException error) {
                LOG.warn("Cannot read UI preference; using Basic view", error);
            }
        }
        LOG.info("metal miner 1.0.0 for Minecraft 26.3; preset {}", settings.preset());
    }
    public static Preset preset() { return settings.preset(); }
    public static ScalingSettings settings() { return settings; }
    public static ScalingSettings renderSettings() { return appliedSettings; }
    public static boolean advancedView() { return advancedView; }
    public static void setAdvancedView(boolean value) {
        if (advancedView == value) return;
        advancedView = value;
        try {
            Files.createDirectories(UI_CONFIG.getParent());
            Path temporary = Files.createTempFile(UI_CONFIG.getParent(), "metal-miner-ui-", ".tmp");
            try {
                Files.writeString(temporary, GSON.toJson(new UiState(value)) + "\n");
                try { Files.move(temporary, UI_CONFIG, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temporary, UI_CONFIG, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
        } catch (IOException e) {
            LOG.warn("Cannot save UI preference; view will reset on next launch", e);
        }
    }
    public static void select(Preset value) {
        // Selecting a preset discards every advanced override, including a pending edit.
        commit(ScalingSettings.forPreset(value));
    }
    public static void custom(ScalingSettings value) { commit(value.normalized()); }
    public static void stageCustom(ScalingSettings value) {
        if (value.preset() != Preset.CUSTOM) throw new IllegalArgumentException("Expected Custom settings");
        settings = value.normalized();
        pendingSince = System.nanoTime();
    }
    public static void flushPending(boolean immediately) {
        if (pendingSince != 0 && (immediately || System.nanoTime() - pendingSince >= 450_000_000L)) commit(settings);
    }
    private static void commit(ScalingSettings value) {
        try {
            Files.createDirectories(CONFIG.getParent());
            Path temporary = Files.createTempFile(CONFIG.getParent(), "metal-miner-", ".tmp");
            try {
                Files.writeString(temporary, GSON.toJson(value) + "\n");
                try { Files.move(temporary, CONFIG, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temporary, CONFIG, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
            settings = appliedSettings = value;
            pendingSince = 0;
            status = value.preset() == Preset.OFF ? "Off. World and interface use full resolution." : "Settings selected. Applies on the next world frame.";
        } catch (IOException e) {
            status = "Could not save settings. See the game log.";
            LOG.error("Cannot save settings", e);
            settings = appliedSettings;
            pendingSince = 0;
        }
    }
}
