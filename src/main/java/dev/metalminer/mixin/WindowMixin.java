package dev.metalminer.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Window.class)
public abstract class WindowMixin {
    // Apply after Sodium Extra's window-density option. Resolution presets control
    // ONLY the world, so the OS window always needs a Retina backing framebuffer.
    @ModifyArg(method = "createWindow", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/device/GpuBackend;createWindow(Ljava/lang/String;IIJ)J"), index = 3, order = 2000)
    private long metalminer$retinaWindow(long flags) {
        return System.getProperty("os.name").startsWith("Mac") ? flags | SDLVideo.SDL_WINDOW_HIGH_PIXEL_DENSITY : flags;
    }
}
