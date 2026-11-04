package dev.metalminer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One screen: dropdowns control the active preset and how many controls are shown. */
public final class SettingsScreen extends Screen {
    private static final Preset[] CHOICES = {Preset.QUALITY, Preset.BALANCED, Preset.PERFORMANCE,
            Preset.ULTRA_PERFORMANCE, Preset.GARBAGE, Preset.HOT_GARBAGE, Preset.OFF};
    private static boolean advancedOnly(Preset preset) {
        return preset == Preset.CUSTOM || preset == Preset.GARBAGE || preset == Preset.HOT_GARBAGE;
    }
    private enum Menu { PRESETS, SETTINGS }
    private final Screen parent;
    private boolean advanced;
    private boolean inputValid = true, outputValid = true;
    private Button presets, settings;
    private Menu openMenu;
    private final List<Button> menuItems = new ArrayList<>();
    private int menuIndex;
    private int left, panelWidth, columnWidth;

    public SettingsScreen(Screen parent) {
        super(Component.literal("metal miner"));
        this.parent = parent;
        this.advanced = MetalMiner.advancedView();
    }

    @Override protected void init() {
        openMenu = null;
        menuItems.clear();
        inputValid = outputValid = true;
        panelWidth = Math.min(324, width - 24);
        columnWidth = (panelWidth - 8) / 2;
        left = (width - panelWidth) / 2;
        presets = addRenderableWidget(Button.builder(presetLabel(), button -> toggleMenu(Menu.PRESETS))
                .bounds(left, 40, columnWidth, 20)
                .tooltip(Tooltip.create(Component.literal("Choose a preset. Resets all advanced controls to its defaults."))).build());
        settings = addRenderableWidget(Button.builder(Component.literal((advanced ? "Advanced" : "Basic") + "  ▼"), button -> toggleMenu(Menu.SETTINGS))
                .bounds(left + columnWidth + 8, 40, columnWidth, 20)
                .tooltip(Tooltip.create(Component.literal("Show or hide advanced controls without changing your settings."))).build());
        if (advanced) {
            addResolutionRow(true, 82);
            addResolutionRow(false, 132);
            addRenderableWidget(Button.builder(filterLabel(), button -> {
                var current = MetalMiner.settings();
                var next = current.filter() == ScalingSettings.PresentationFilter.SMOOTH
                        ? ScalingSettings.PresentationFilter.PIXEL_SHARP : ScalingSettings.PresentationFilter.SMOOTH;
                edit(current.withFilter(next));
                button.setMessage(filterLabel());
            }).bounds(left, 172, columnWidth, 20).tooltip(Tooltip.create(Component.literal(
                    "Final resize to the screen when MetalFX output is smaller. Smooth blends pixels; Pixel-sharp keeps hard pixel edges."))).build());
            addRenderableWidget(Button.builder(smoothingLabel(), button -> {
                edit(MetalMiner.settings().withEdgeSmoothing(!MetalMiner.settings().edgeSmoothing()));
                button.setMessage(smoothingLabel());
            }).bounds(left + columnWidth + 8, 172, columnWidth, 20).tooltip(Tooltip.create(Component.literal(
                    "Smooths edges after MetalFX, before the final resize. World only. Adds GPU work and may soften detail."))).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 - 100, height - 28, 200, 20).build());
    }

    private Component presetLabel() { return Component.literal(MetalMiner.preset().label + "  ▼"); }
    private Component filterLabel() { return Component.literal("Final resize: " + MetalMiner.settings().filter().label); }
    private Component smoothingLabel() { return Component.literal("Edge smoothing: " + (MetalMiner.settings().edgeSmoothing() ? "On" : "Off")); }
    private void edit(ScalingSettings value) {
        MetalMiner.stageCustom(value);
        presets.setMessage(presetLabel());
    }
    private ScalingSettings.Resolution resolution() {
        var window = minecraft.getWindow();
        return MetalMiner.settings().resolve(Math.max(2, window.getWidth()), Math.max(2, window.getHeight()));
    }

    private void toggleMenu(Menu menu) {
        boolean same = openMenu == menu;
        closeMenu();
        if (same) return;
        openMenu = menu;
        clearFocus();
        if (menu == Menu.PRESETS) {
            for (Preset preset : CHOICES) {
                if (!advanced && (preset == Preset.GARBAGE || preset == Preset.HOT_GARBAGE)) continue;
                addMenuItem(preset.label, MetalMiner.preset() == preset, () -> {
                    MetalMiner.select(preset);
                    closeMenu();
                    rebuildWidgets(); // Keep the Advanced pane visible while comparing presets.
                    setFocused(presets);
                });
            }
            if (MetalMiner.preset() == Preset.CUSTOM) addMenuItem("Custom", true, this::closeMenu);
        } else {
            for (boolean show : new boolean[]{false, true}) addMenuItem(show ? "Advanced" : "Basic", advanced == show, () -> {
                closeMenu();
                if (!show && advancedOnly(MetalMiner.preset())) MetalMiner.select(Preset.BALANCED);
                else MetalMiner.flushPending(true);
                advanced = show;
                MetalMiner.setAdvancedView(show);
                rebuildWidgets();
                setFocused(settings);
            });
        }
        setFocused(menuItems.get(menuIndex));
    }
    private void addMenuItem(String label, boolean selected, Runnable action) {
        int x = openMenu == Menu.PRESETS ? presets.getX() : settings.getX();
        if (selected) menuIndex = menuItems.size();
        Button item = Button.builder(Component.literal((selected ? "✓ " : "") + label), button -> action.run())
                .bounds(x, 62 + menuItems.size() * 18, columnWidth, 18).build();
        menuItems.add(addWidget(item)); // Render above base controls in a separate stratum.
    }
    private void closeMenu() {
        if (openMenu == null) return;
        Button trigger = openMenu == Menu.PRESETS ? presets : settings;
        clearFocus();
        for (Button item : menuItems) removeWidget(item);
        menuItems.clear();
        openMenu = null;
        menuIndex = 0;
        setFocused(trigger);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (openMenu == null) return super.mouseClicked(event, doubleClick);
        // An expanded menu owns the click; never let it reach a covered edit box.
        for (Button item : List.copyOf(menuItems))
            if (item.isMouseOver(event.x(), event.y())) { item.mouseClicked(event, doubleClick); return true; }
        if (presets.isMouseOver(event.x(), event.y())) { presets.mouseClicked(event, doubleClick); return true; }
        if (settings.isMouseOver(event.x(), event.y())) { settings.mouseClicked(event, doubleClick); return true; }
        closeMenu();
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (openMenu == null) return super.keyPressed(event);
        if (event.isEscape() || event.isCycleFocus()) { closeMenu(); return true; }
        if (event.isUp() || event.isDown()) {
            menuIndex = Math.floorMod(menuIndex + (event.isUp() ? -1 : 1), menuItems.size());
            setFocused(menuItems.get(menuIndex));
        } else if (event.isSelection() || event.isConfirmation()) menuItems.get(menuIndex).onPress(event);
        return true;
    }
    @Override public boolean charTyped(CharacterEvent event) { return openMenu != null || super.charTyped(event); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (openMenu == null) return super.mouseScrolled(x, y, horizontal, vertical);
        if (vertical != 0) {
            menuIndex = Math.floorMod(menuIndex + (vertical > 0 ? -1 : 1), menuItems.size());
            setFocused(menuItems.get(menuIndex));
        }
        return true;
    }

    private void addResolutionRow(boolean input, int y) {
        var current = MetalMiner.settings();
        boolean pixels = (input ? current.inputHeight() : current.outputHeight()) > 0;
        addRenderableWidget(Button.builder(Component.literal(pixels ? "Pixels high" : "Percent"), button -> {
            var now = MetalMiner.settings();
            var sizes = resolution();
            if (input) {
                double ratio = (double)sizes.inputHeight() / sizes.outputHeight();
                edit(now.withInput(Math.max(.125, Math.min(1, ratio)), pixels ? 0 : Math.max(64, sizes.inputHeight())));
            } else {
                double ratio = (double)sizes.outputHeight() / sizes.nativeHeight();
                edit(now.withOutput(Math.max(.25, Math.min(1, ratio)), pixels ? 0 : Math.max(64, sizes.outputHeight())));
            }
            rebuildWidgets();
        }).bounds(left, y, 94, 20).tooltip(Tooltip.create(Component.literal(input
                ? "Percent of MetalFX output, or fixed height in pixels. Width follows the window's aspect ratio."
                : "Percent of the window, or fixed height in pixels. Width follows the window's aspect ratio."))).build());
        EditBox value = new EditBox(font, left + 100, y, panelWidth - 192, 20,
                Component.literal(input ? "World input resolution" : "MetalFX output resolution"));
        value.setMaxLength(8);
        value.setTooltip(Tooltip.create(Component.literal(pixels ? "Pixel height: 64–16384, capped to fit the output/window."
                : input ? "Input: 12.5–100% of the MetalFX output." : "Output: 25–100% of the window.")));
        value.setValue(pixels ? Integer.toString(input ? current.inputHeight() : current.outputHeight())
                : number(100 * (input ? current.inputScale() : current.outputScale())));
        value.setResponder(text -> {
            try {
                double parsed = Double.parseDouble(text);
                double minimum = pixels ? 64 : input ? 12.5 : 25;
                if (!Double.isFinite(parsed) || parsed < minimum || parsed > (pixels ? 16384 : 100) || (pixels && parsed != Math.rint(parsed)))
                    throw new IllegalArgumentException();
                var now = MetalMiner.settings();
                edit(input ? now.withInput(pixels ? now.inputScale() : parsed / 100, pixels ? (int)parsed : 0)
                        : now.withOutput(pixels ? now.outputScale() : parsed / 100, pixels ? (int)parsed : 0));
                value.setTextColor(0xFFFFFFFF);
                if (input) inputValid = true; else outputValid = true;
            } catch (IllegalArgumentException e) {
                value.setTextColor(0xFFFF7777);
                if (input) inputValid = false; else outputValid = false;
            }
        });
        addRenderableWidget(value);
        for (int direction : new int[]{-1, 1}) {
            addRenderableWidget(Button.builder(Component.literal(direction < 0 ? "−" : "+"), button -> {
                try {
                    double parsed = Double.parseDouble(value.getValue());
                    double minimum = pixels ? 64 : input ? 12.5 : 25;
                    double next = Math.max(minimum, Math.min(pixels ? 16384 : 100, parsed + direction * (pixels ? 90 : 5)));
                    value.setValue(number(next));
                } catch (NumberFormatException ignored) { }
            }).bounds(left + panelWidth - (direction < 0 ? 86 : 40), y, 40, 20).build());
        }
    }
    private static String number(double value) {
        return value == Math.rint(value) ? Integer.toString((int)value) : String.format(Locale.ROOT, "%.1f", value);
    }
    @Override public void tick() { MetalMiner.flushPending(false); presets.setMessage(presetLabel()); }
    @Override public void removed() { MetalMiner.flushPending(true); }
    @Override public void onClose() { MetalMiner.flushPending(true); minecraft.gui.setScreen(parent); }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, openMenu == null ? mouseX : -1, openMenu == null ? mouseY : -1, delta);
        if (advanced) graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.centeredText(font, "Presets", left + columnWidth / 2, 28, 0xFFDDDDDD);
        graphics.centeredText(font, "Settings", left + columnWidth + 8 + columnWidth / 2, 28, 0xFFDDDDDD);
        var sizes = resolution();
        if (advanced) {
            graphics.text(font, "World input: " + sizes.inputLabel(), left, 70, 0xFFDDDDDD);
            graphics.text(font, "MetalFX output: " + sizes.outputLabel(), left, 120, 0xFFDDDDDD);
            graphics.text(font, !inputValid ? "Invalid input. Last valid value is kept."
                    : MetalMiner.settings().inputHeight() == 0 ? "% of MetalFX output" : "Pixels high; capped at MetalFX output", left, 106, inputValid ? 0xFFAAAAAA : 0xFFFF7777);
            graphics.text(font, !outputValid ? "Invalid output. Last valid value is kept."
                    : MetalMiner.settings().outputHeight() == 0 ? "% of screen; UI stays native" : "Pixels high; capped at screen size", left, 156, outputValid ? 0xFFAAAAAA : 0xFFFF7777);
            graphics.centeredText(font, "Window: " + sizes.nativeLabel(), width / 2, height - 42, 0xFFDDDDDD);
        } else {
            graphics.centeredText(font, "World: " + sizes.inputLabel(), width / 2, 104, 0xFFAACCFF);
            graphics.centeredText(font, "Window: " + sizes.nativeLabel(), width / 2, 130, 0xFFDDDDDD);
        }
        if (openMenu != null) {
            graphics.nextStratum();
            int x = menuItems.getFirst().getX(), y = menuItems.getFirst().getY();
            graphics.fill(x - 1, y - 1, x + columnWidth + 1, y + menuItems.size() * 18 + 1, 0xFF1A1A1A);
            graphics.outline(x - 1, y - 1, columnWidth + 2, menuItems.size() * 18 + 2, 0xFF999999);
            for (Button item : menuItems) item.extractRenderState(graphics, mouseX, mouseY, delta);
        }
    }
}
