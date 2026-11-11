<img width="256" height="256" alt="icon" src="https://github.com/user-attachments/assets/9185f649-f334-456e-94ec-9720364fe8e2" />

# metal miner

A simple MetalFX spatial upscaler that **supports Iris shaders** and is built
for **Minecraft 26.3 and Fabric**. The mod takes rendered OpenGL frames
and upscales them with MetalFX, then draws the UI at the window's native
resolution.

## Why Make an Upscaler?

I tried multiple MetalFX upscaler mods and they either:
1. didn't work with the latest version of Fabric
2. didn't support Iris shaders.

Because of this, I decided to write my own.

## Requirements

- Apple Silicon Mac running macOS 13 or later
- Minecraft/Fabric 26.3 + Sodium + Iris
- [Mod Menu](https://github.com/TerraformersMC/ModMenu)

## How to Use

Install `dist/metal-miner-1.0.0+mc26.3.jar` in a compatible Fabric profile's `mods` directory,
then restart Minecraft. Open **Mods → metal miner → Configure**.

<img width="1920" height="1200" alt="basic" src="https://github.com/user-attachments/assets/75553c80-c1de-4ea4-ad65-5ece98fbaeaa" />


| Setting | World width and height | World pixel count |
| --- | --- | --- |
| Quality | 66.7% of output | About 44% |
| Balanced **(default)** | 58% of output | About 34% |
| Performance | 50% of output | 25% |
| Ultra Performance | 33.3% of output | About 11% |
| Garbage *(Advanced only)* | 25% of output | 6.25% |
| Hot Garbage *(Advanced only)* | 12.5% of output | About 1.56% |
| Off | 100% | 100% |

The top-center **Presets** dropdown selects the quality level. The **Settings**
dropdown switches between **Basic** and **Advanced** on the same screen. Basic
shows only the two dropdowns, world resolution, window resolution and Done.
Advanced also exposes resolution and filtering controls. The selected view is
saved and reopens as you left it, including after restarting the game. Presets
apply on the next rendered world frame. Game UI is rendered at the window's full
framebuffer resolution for every preset. Lower input resolutions can increase
artifacts, especially around distant mobs.

### Advanced Settings

<img width="1920" height="1200" alt="advanced" src="https://github.com/user-attachments/assets/f7441eb6-6be6-4792-b71e-a9fa6f6c8ba1" />


Select **Settings → Advanced** to reveal the resolution and filtering controls
beneath the dropdowns.

- **World input:** 12.5–100% of the MetalFX output, or a fixed pixel height.
- **MetalFX output:** 25–100% of the window framebuffer, or a fixed pixel height.
- **Final resize:** Smooth (default) or Pixel-sharp. This affects the last resize
  when the MetalFX output is smaller than the window. It does not change the HUD.
- **Edge smoothing:** Off (default) or On. A single-frame edge filter runs on the
  MetalFX output before the final resize and before the native HUD. It can soften
  remaining jagged edges, with some loss of fine detail and extra GPU work. It
  can be combined with other anti-aliasing effects.

For example, on a 2800×1800 framebuffer, **input 50% + output 1080 pixels high** means:

```text
840×540 world → 1680×1080 MetalFX → 2800×1800 screen → native HUD
```

### Configuration

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

### Logging

If MetalFX cannot initialize, the game uses native rendering and reports the reason
in the settings screen and game log. A runtime GPU error falls back for the current
frame, then uses native rendering until restart. The mod rejects loading alongside
Metal Upscaler because both change the same rendering boundary.

## Building

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
