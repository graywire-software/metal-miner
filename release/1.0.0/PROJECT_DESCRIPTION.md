# metal miner

metal miner is a simple MetalFX spatial upscaler for Minecraft Java on Apple Silicon Macs. It renders the world at a lower resolution, upscales the finished image, and then lets Minecraft draw menus, text and the HUD at the window's native resolution. It is a client mod for **Minecraft 26.3 + Fabric + Sodium**, designed to work with shaders through Iris.

**Requirements:** Apple Silicon Mac, Minecraft 26.3, Fabric Loader 0.19.5 or newer, Java 25 or newer, Sodium 0.9.3-beta.1, and Iris 1.11.7. Mod Menu 21.0.0 or newer provides the in-game Configure button. The exact Iris and Sodium versions are required by this release; other versions have not been verified.

Choose Quality, Balanced, Performance or Ultra Performance in the Presets menu. Balanced is the default. Advanced reveals Garbage, Hot Garbage, independent world input and MetalFX output resolution controls, final resize filtering and optional edge smoothing. Editing an advanced control selects Custom. Switching back to Basic from Custom, Garbage or Hot Garbage selects Balanced.

Lower input resolutions may improve frame rate, but can increase image artifacts, especially on distant mobs. Very low presets also lose fine detail. The optional edge filter can soften edges and fine texture. This mod does spatial upscaling; it does not generate frames or restore detail that the world never rendered. Results depend on the scene and settings.

**Only tested on an M1 Pro with 16 GB RAM.** Compatibility and performance on other Apple Silicon Macs have not yet been verified.

Install the JAR in a compatible Fabric profile's `mods` folder, restart Minecraft, and open **Mods → metal miner → Configure**. Install Mod Menu to access this screen. The mod is licensed under MIT.