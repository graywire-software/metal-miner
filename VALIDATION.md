# Validation — 4 November 2026

Tested on this machine: Apple M1 Pro with 16 GB RAM, macOS 27.2, arm64 Java 25.

## Version 1.0.0 — release candidate

The Basic label is `World:`. Balanced is the default when no rendering config
exists. Basic hides Garbage and Hot Garbage; switching to Basic from either of
those or Custom selects Balanced. The isolated client test covered the actual
dropdown interactions, fresh-config default, saved view preference, Custom edits,
rendering transitions, native GUI and a graphics reload. It passed after more
than 5,900 world frames and shut down normally. Screenshots show the new Basic
and Advanced layouts at the test client's native GUI resolution.

The 1.0.0 JAR contains the arm64 MetalFX bridge, license and icon. A separate
source JAR contains the project's Java, native bridge, build scripts and docs,
with no game or third-party binaries. The GPU bridge code is unchanged from
the earlier native GPU test; no wider machine compatibility is claimed.

Logs: `build/build-v1.0.log` and `build/integration-v1.0-console.log`.
Screenshots: `build/run/screenshots/test-v1.0-basic.png` and
`build/run/screenshots/test-v1.0-advanced-balanced.png`.

## Version 0.3.1 — settings refinements

Basic now renders only the Presets and Settings dropdowns, world resolution,
Window resolution, and Done. The window line uses `Window:` in both views. The
Basic/Advanced choice is stored separately from rendering settings and survives
closing and reopening the screen and game restart; selecting a preset does not
reset the view.

The isolated client checks both reopen paths and the saved preference. Screenshots
were inspected at the profile's GUI scale. The existing GPU bridge and rendering
code are unchanged apart from a comment; the integration test still checks native
UI dimensions during world rendering.

## Version 0.3.0

The settings screen now has Presets and Basic/Advanced dropdowns. Advanced expands
in place. Tests operate the actual widgets through mouse and keyboard events and
verify:

- Basic hides the advanced controls; opening Advanced preserves the preset and
  the same Screen object.
- Choosing Ultra Performance, Garbage, Hot Garbage and Performance updates the
  visible input scale while keeping Advanced open.
- Custom is initially absent. A valid resolution or edge-smoothing edit selects
  it immediately and adds it to the menu. Selecting a named preset resets every
  override and removes Custom again.
- Invalid numeric input keeps the last valid setting. Collapsing/reopening the
  pane preserves edits. Custom values, including smoothing, persist to JSON.
- Arrow keys and Enter choose dropdown items; Escape dismisses the menu without
  leaving the screen. Outside clicks dismiss the menu without affecting controls.
- Previously saved v0.1 preset-only and v0.2 Custom configurations migrate without
  enabling smoothing or changing their existing rendering settings.

The native test ran with **Metal API Validation enabled**. It exercises smoothing
at 1×, 2× and 8×, odd dimensions, reduced outputs and both final resize filters.
A diagonal-edge test verifies that smoothing changes edge pixels while preserving
flat regions and alpha, and that toggling it off restores the original output
exactly without recreating the scaler. Color, orientation and GL-state checks pass.
The optional pass writes directly to the shared output surface after MetalFX; no
CPU readback or extra OpenGL render pass is introduced. Synthetic timing samples
are recorded in the log but are too short/noisy to establish a performance delta.

The isolated Minecraft run passed after more than 6,400 world frames, using
the full copied mod loadout and a disposable world. Runtime assertions check the world size and native UI dimensions every
frame. Cases include smoothing with percentage and fixed-height outputs, both final
resize filters, preset resets, window resize, Off and a graphics reload.
Original profile graphics settings are preserved; the new edge filter is Off by
default. Other Apple Silicon models were not tested in this run.

Logs: `build/build-v0.3.log`, `build/native-test-v0.3.log`, and
`build/integration-v0.3-console.log`. Visually inspected screenshots are in
`build/run/screenshots/test-v0.3-*.png` (Basic, Advanced and both dropdowns).

## Version 0.2.0

Added Ultra Performance (1/3), Garbage (1/4), Hot Garbage (1/8), and Custom input/output
resolution controls. The pure settings tests cover old-config migration, round-trip
serialization, preset resets, valid bounds, percentage and pixel-height calculations,
and oversized input/output requests.

The native GPU test also covers 1×, 2×, 4× and 8× scaling, a MetalFX output smaller
than the presentation target, and both Smooth and Pixel-sharp final resizing. Color,
orientation and GL-state checks pass. It ran with **Metal API Validation enabled**.
The MetalFX output now uses private storage as required by Apple's API, followed by
a GPU copy into the shared surface. The measured synthetic cost was 3.27 ms/frame
at the same 3456×2234 output size (with validation enabled).

The new Minecraft test passed all stages after more than 6,000 world frames. It exercises:

- Opening Advanced without changing the preset, editing the actual numeric fields,
  immediate Custom selection, debounced application, and saving Custom settings.
- All three new presets with the full existing mod loadout.
- Percentage output reduction, fixed input/output pixel heights, and Pixel-sharp scaling.
- Returning to Performance with all custom overrides reset.
- Resizing the window while a fixed-height Custom output remains active, plus Off,
  returning to Quality, and graphics reload.
- Native framebuffer and render-state dimensions immediately before every GUI draw.

Logs: `build/integration-v0.2-console.log` and `build/native-test.log`.
UI captures: `build/run/screenshots/test-v0.2-settings.png` and
`build/run/screenshots/test-v0.2-advanced.png`.

### Version 0.2.0 performance sample

Same disposable world and fixed camera direction, with the copied graphics settings,
VSync and AFK limiting off. Each case settles for six seconds and samples roughly five seconds of frame
counts against wall time. macOS constrained the requested window to **2800×1658**
framebuffer pixels in this run; this is a different output size from the original
0.1.0 sample below, so the two tables are not a controlled version comparison.

| Setting | Average FPS | Average frame interval |
| --- | ---: | ---: |
| Performance | 52.9 | 18.90 ms |
| Ultra Performance | 73.3 | 13.65 ms |
| Garbage | 81.6 | 12.26 ms |
| Hot Garbage | 89.8 | 11.13 ms |
| Custom: input 50%, output 1080 pixels high, Smooth | 81.6 | 12.26 ms |
| Custom: input 50%, output 900 pixels high, Smooth | 86.0 | 11.63 ms |

All cases kept the GUI at 2800×1658. This is one short test scene, not a 60 FPS
guarantee across worlds, motion, thermal conditions or display sizes. Benchmark
logs are in `build/benchmark-v0.2-console.log`; screenshots use `benchmark-v0.2-`
filenames. The 1080-pixel Custom output is a useful starting point for balancing
detail against speed.

## Version 0.1.0 — original integration

Used a separate client in `build/run`, with copies of the entire `holeworld` mod
list, resource packs and graphics configuration. A disposable demo-seed world was
created for testing. No original saves or launcher account tokens were used.

- Minecraft 26.3 / Fabric Loader 0.19.5
- Sodium 0.9.3-beta.1 / Iris 1.11.7
- Sodium Extra 0.9.4, Physics Mod Pro v188i, and all other installed mods retained
- Original Sodium Extra low-resolution setting enabled; mod still obtained a 2×
  Retina framebuffer (960×600 window → 1920×1200 framebuffer)

The integration run completed successfully after more than 4,000 world frames.
Runtime assertions checked the world size before rendering and the native target
and render-state dimensions immediately before every GUI render.

Passed transitions:

1. Quality → Balanced → Performance → Off → Quality.
2. Window resize from 1920×1200 to 2002×1282 output, including rounded input sizes.
3. Toggle the active post-processing pipeline off and back on.
4. Open and close the settings screen while in a world.
5. Normal game shutdown and native resource cleanup.

Screenshots were captured and visually inspected for the rendered world, HUD,
settings screen, native mode and benchmark output. The first run found a cached
sky-target failure on preset changes; the final implementation fixes that and
Iris's per-target depth-version collision. The full transition run then passed.

## Performance sample

Separate run with a fixed camera direction, 8-chunk render distance,
the copied graphics settings, and **2800×1800 output**.
VSync and AFK frame limiting were disabled only in the disposable test profile.
Each preset received six seconds of settling followed by approximately five seconds
of frame-count/wall-clock measurement. The original profile's frame limit and VSync
settings were preserved.

| Preset | Input | Average FPS | Average frame interval |
| --- | --- | ---: | ---: |
| Quality | 1867×1200 | 30.7 | 32.62 ms |
| Balanced | 1624×1044 | 36.2 | 27.63 ms |
| Performance | 1400×900 | 42.0 | 23.81 ms |
| Off / native | 2800×1800 | 20.9 | 47.90 ms |

This is one short scene sample, not a general FPS guarantee. It does not measure
input latency or 1% lows, and it is not a comparison against Sodium Extra's reduced
whole-window resolution. CPU-heavy areas and graphics settings can change the result.
Capture files and full logs are in `build/run/screenshots` and
`build/benchmark-console.log`.

## Native GPU test

`bash scripts/native-test.sh` creates a real accelerated CGL context and calls
MetalFX on the M1 Pro. It verifies:

- Shared IOSurface allocation and real MetalFX execution.
- Correct RGB channels, vertical orientation and flat-color values (within 3/255).
- Restored read/draw framebuffer bindings, scissor enable and framebuffer-sRGB state.
- Repeated destruction/recreation and odd input sizes.
- Rejection of invalid dimensions.
- No OpenGL errors in the exercised native operations.

Synthetic bridge-plus-scaler cost: **3.13 ms/frame** for 2304×1489 → 3456×2234,
averaged over 30 frames after warmup. This excludes Minecraft world rendering.
Pixel readback is used only in the test assertions, never in the mod's upscaling path.

## Packaging and limits

The JAR includes this project's Java classes, native arm64 library, license and
the user's icon. The SVG is preserved in `icon/icon.svg`; a 512×512 PNG is used
by Mod Menu. No test driver or third-party game/mod binary is bundled.

Not exercised: long play sessions, all dimensions, multiplayer,
external displays, minimized/fullscreen transitions, sleep/wake, HDR, Distant
Horizons, driver-reset recovery or other Minecraft/Iris/Sodium versions. The failure
fallback is implemented but GPU-command failure injection was not tested.
