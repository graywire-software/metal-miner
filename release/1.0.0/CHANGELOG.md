# metal miner 1.0.0 for Minecraft 26.3

First release package for Apple Silicon Macs. Renders the world below native resolution, upscales it with MetalFX, and keeps menus and the HUD crisp.

- Balanced is the default preset. Basic shows the two dropdowns, World and Window resolutions, and Done.
- Garbage and Hot Garbage appear only in Advanced. Switching to Basic from either one or Custom selects Balanced.
- Advanced offers independent input and output resolutions, final resize filtering, and optional edge smoothing.
- Basic/Advanced view preference persists between visits and game launches.
- The Basic label is now “World:”.

Only tested on an M1 Pro with 16 GB RAM. Lower input resolutions can increase artifacts, especially on distant mobs.
