# metal miner

A simple MetalFX spatial upscaler for **Minecraft 26.3 on Apple Silicon Macs**.
This Fabric client mod is built for Sodium 0.9.3-beta.1 and Iris 1.11.7, and
works with shaders. It enlarges the world image before Minecraft draws the
interface. **Only tested on an M1 Pro with 16 GB RAM.**

## Use

Install `dist/metal-miner-1.0.0+mc26.3.jar` in a compatible Fabric profile's `mods` directory,
then restart Minecraft. Open **Mods → metal miner → Configure**.

| Setting | World width and height | World pixel count |
| --- | --- | --- |
| Quality | 66.7% of output | About 44% |
| Balanced (default) | 58% of output | About 34% |
| Performance | 50% of output | 25% |
| Ultra Performance | 33.3% of output | About 11% |
| Garbage | 25% of output | 6.25% |
| Hot Garbage | 12.5% of output | About 1.56% |
| Off | 100% | 100% |

The top-center **Presets** dropdown selects the quality level. The **Settings**
dropdown switches between **Basic** and **Advanced** on the same screen. Basic
shows only the two dropdowns, world resolution, window resolution and Done.
Advanced also exposes resolution and filtering controls. The selected view is
saved and reopens as you left it, including after restarting the game. Presets
apply on the next rendered world frame. Chat, inventory, menus, text, the hotbar and the normal
crosshair render at the window's full framebuffer resolution for every preset.
The player's hand, world labels and world-space effects are part of the world image.
Garbage and Hot Garbage appear only in Advanced. Switching to Basic while either
is active, or while using Custom, selects Balanced. The smallest presets trade
away substantial detail; their names are intentional.
Lower input resolutions can increase artifacts, especially around distant mobs.

### Advanced / Custom

Select **Settings → Advanced** to reveal the resolution and filtering controls
beneath the dropdowns. Choosing another preset keeps the panel open and updates
its controls, so you can compare scales. Switching Basic/Advanced only changes
visibility for Quality, Balanced, Performance, Ultra Performance and Off. Switching
to Basic from Custom, Garbage or Hot Garbage selects Balanced.

Editing a valid value, changing units, or changing either filter immediately
selects **Custom** and adds it to the Presets dropdown. Custom is absent until
you make an edit, and disappears when you choose a named preset. Previously saved
Custom settings are also recognized when reopening the screen. Changes apply
after a short typing pause, or on closing the screen/collapsing Advanced, to avoid
rebuilding render buffers for every keystroke. Invalid values keep the last valid
setting. Dropdowns support mouse, arrow keys, Enter and Escape; clicking outside
dismisses the menu without changing a covered control.

- **World input:** 12.5–100% of the MetalFX output, or a fixed pixel height.
- **MetalFX output:** 25–100% of the window framebuffer, or a fixed pixel height.
- **Final resize:** Smooth (default) or Pixel-sharp. This affects the last resize
  when the MetalFX output is smaller than the window. It does not change the HUD.
- **Edge smoothing:** Off (default) or On. A single-frame edge filter runs on the
  MetalFX output before the final resize and before the native HUD. It can soften
  remaining jagged edges, with some loss of fine detail and extra GPU work. It
  can be combined with other anti-aliasing effects.

Width follows the window's aspect ratio. Pixel heights are capped so input ≤ output
≤ window; the panel shows the effective sizes. Pixel heights accept 64–16384 before
those caps. Settings persist between launches. Selecting any named preset clears
every custom override: output returns to the full window, fixed heights are disabled,
final resizing returns to Smooth, and edge smoothing turns Off. The Advanced
panel stays open if you were using it.

For example, on a 2800×1800 framebuffer, **input 50% + output 1080 pixels high** means:

```text
840×540 world → 1680×1080 MetalFX → 2800×1800 screen → native HUD
```

The final resize is a simple GPU stretch. Reducing the MetalFX output saves both
world rendering and upscaler work, at the cost of a softer world image. It still
needs a native framebuffer for the crisp UI and cannot guarantee 60 FPS in CPU-heavy
scenes. MetalFX spatial has no public sharpness-strength control; the filter above
controls the final resize only.

The mod requests a Retina framebuffer even if Sodium Extra's **Reduce resolution
on Mac** is enabled. That option reduces the entire window, including the UI;
Metal Miner's presets replace its role. Use Off to compare with full-resolution
rendering. Removing the JAR restores Sodium Extra's normal behavior.

MetalFX spatial scaling uses one finished frame; it does not supply temporal
anti-aliasing, invent unseen detail, or generate extra frames. Thin foliage and
distant fences can still lose detail at Performance. Balanced is the suggested
starting point. Optional post-upscale edge smoothing also uses only the current
frame; it does not recover detail missing at low input resolutions.

Rendering configuration is stored in `config/metal-miner.json`. The Basic/Advanced
view choice is stored separately in `config/metal-miner-ui.json`, so selecting a
rendering preset never resets the view. The original preset-only
format and v0.2 Custom settings are still accepted; missing edgeSmoothing defaults to false. A Custom configuration can look like:

```json
{
  "preset": "CUSTOM",
  "inputScale": 0.5,
  "outputScale": 1.0,
  "inputHeight": 0,
  "outputHeight": 1080,
  "filter": "SMOOTH",
  "edgeSmoothing": false
}
```

`python3 scripts/install.py` installs into `holeworld` and backs up any earlier
Metal Miner JAR and configuration files.
To reverse an installation, run `python3 scripts/uninstall.py /path/to/manifest.json`
with the rollback record printed by the installer. Removing only the Metal Miner
JAR also disables all of its rendering changes. No world files are changed by installation.

If MetalFX cannot initialize, the game uses native rendering and reports the reason
in the settings screen and game log. A runtime GPU error falls back for the current
frame, then uses native rendering until restart. The mod rejects loading alongside
Metal Upscaler because both change the same rendering boundary.

## Build on this Mac

Requires a JDK 25+, Xcode with a macOS 13+ SDK, Python 3, and the installed profile.
The build uses the exact local game and mod JARs. Minecraft 26.3 is unobfuscated,
so this narrowly targeted build does not need a mappings/remapping toolchain.
No Minecraft or third-party mod code or binaries are included in the output. The bundled native library is compiled from `native/bridge.mm`.

```sh
export METALMINER_JDK="/path/to/jdk/Contents/Home"
python3 scripts/build.py
bash scripts/native-test.sh
python3 scripts/integration-test.py
python3 scripts/package-release.py
```

The locally downloaded Azul JDK is also detected via `.tools/java-home`.
`build.py --app /path/to/ModrinthApp --profile holeworld` can change the dependency
location. Build tools and decompiled research are ignored and are not release assets.
The packaging script writes the upload JAR, project icon, real UI screenshots,
checksums, submission notes and a source JAR to `release/1.0.0` after the integration
test. The source is MIT-licensed; the compiled mod includes no game or other mod
binaries.

The integration test launches a separate offline client in `build/run`, copies the
mod and graphics configuration files, and creates a disposable world. It never opens the profile's
original saves or reads launcher account tokens. Its test-only mod is not packaged
in the release. Offline authentication warnings and the profile's missing AppleSkin
cloth-config integration are unrelated to this mod.

## Rendering design

1. Keep the original native-sized main target for presentation and GUI rendering.
2. After the game's window resize check, temporarily select a smaller world target
   and matching render-state dimensions. All world passes see that same size.
3. Finish Iris's final color conversion, outlines and vanilla world post effects.
4. Blit the completed world to an IOSurface shared with Metal. Run
   `MTLFXSpatialScaler` in perceptual color mode into its required private Metal
   output texture. Copy it into a shared IOSurface, or run the optional edge
   smoothing compute pass which writes directly into that surface. Then blit
   it to the original native target. A reduced MetalFX output is stretched using
   the selected final filter. There is no CPU pixel readback in this path.
5. Restore native dimensions and global uniforms before `GuiRenderer.render()`.

The bridge uses `glFinish` and a Metal command completion wait to transfer ownership
safely between the APIs. This costs time, so gains depend on how much world shading
the lower resolution saves. It does not help CPU-limited scenes. Its native test
measures the bridge plus scaler separately from world rendering.

Iris 1.11.7 tracks depth texture versions per render target. A small compatibility
mixin also checks texture identity, avoiding stale attachments when two different
targets have equal version counters. The sky renderer follows the active target.
Resources are reused between frames and recreated when dimensions change. OpenGL
state is restored after native calls; the main target is restored even if rendering
throws an exception.

## Scope

This release is pinned to the installed game, Iris and Sodium
versions. Upgrades need a compatibility review. Distant Horizons, other rendering
backends, HDR output, frame generation and temporal MetalFX are outside this build's
tested scope. macOS 13+ and an arm64 JVM are required.

See `VALIDATION.md` for the actual checks and their limits.

References: [Apple's MetalFX rendering guidance](https://developer.apple.com/videos/play/wwdc2022/10103/),
[Apple's spatial scaler documentation](https://developer.apple.com/documentation/metalfx/mtlfxspatialscalerdescriptor),
[Fabric's 26.3 changes](https://www.fabricmc.net/2026/09/15/263.html),
[Iris source](https://github.com/IrisShaders/Iris).
The user-provided Metal Upscaler 26.2 JAR was inspected for compatibility research;
it is not bundled or used at runtime.
