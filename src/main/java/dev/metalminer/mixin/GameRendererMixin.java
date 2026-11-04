package dev.metalminer.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.metalminer.Upscaler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.TextureFilteringMethod;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final @Mutable private RenderTarget mainRenderTarget;
    @Shadow @Final private RenderTarget hud3DTarget;
    @Shadow @Final private GameRenderState gameRenderState;
    @Shadow @Final private GlobalSettingsUniform globalSettingsUniform;
    @Unique private final Upscaler metalminer$upscaler = new Upscaler();
    @Unique private RenderTarget metalminer$output;
    @Unique private int metalminer$width, metalminer$height;
    @Unique private int metalminer$auxWidth, metalminer$auxHeight;

    // After vanilla checks the native framebuffer size, before clearing the world target.
    // All world passes then see this stable, smaller target and matching uniforms.
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;getDevice()Lcom/mojang/renderpearl/api/device/GpuDevice;", ordinal = 0))
    private void metalminer$beginWorld(CallbackInfo ci) {
        RenderTarget world = gameRenderState.shouldRenderLevel && !gameRenderState.windowRenderState.isMinimized
                ? metalminer$upscaler.prepare(mainRenderTarget) : null;
        if (world == null) {
            metalminer$resizeAuxiliary(mainRenderTarget.width, mainRenderTarget.height);
            return;
        }
        metalminer$output = mainRenderTarget;
        metalminer$width = gameRenderState.windowRenderState.width;
        metalminer$height = gameRenderState.windowRenderState.height;
        mainRenderTarget = world;
        gameRenderState.windowRenderState.width = world.width;
        gameRenderState.windowRenderState.height = world.height;
        metalminer$resizeAuxiliary(world.width, world.height);
    }

    // This is outside renderLevel(), after Iris's TAIL color conversion, outlines and
    // vanilla post effects, but before ANY of GuiRenderer's native-size drawing.
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
    private void metalminer$finishWorld(CallbackInfo ci) {
        if (metalminer$output == null) return;
        RenderTarget output = metalminer$output;
        metalminer$restoreTarget();
        metalminer$upscaler.present(output);
        var options = gameRenderState.optionsRenderState;
        globalSettingsUniform.update(metalminer$width, metalminer$height, options.glintStrength,
                gameRenderState.levelRenderState.gameTime, gameRenderState.levelRenderState.worldPartialTicks,
                options.menuBackgroundBlurriness, gameRenderState.levelRenderState.cameraRenderState.pos,
                options.textureFiltering == TextureFilteringMethod.RGSS);
    }

    @Unique private void metalminer$resizeAuxiliary(int width, int height) {
        // The actual HUD remains on the output target. hud3DTarget holds world-space
        // hand/effect depth; it must match the world attachments when post effects run.
        if (metalminer$auxWidth != width || metalminer$auxHeight != height || hud3DTarget.width != width || hud3DTarget.height != height) {
            hud3DTarget.resize(width, height);
            minecraft.levelRenderer.resize(width, height);
            metalminer$auxWidth = width; metalminer$auxHeight = height;
        }
    }
    @Unique private void metalminer$restoreTarget() {
        if (metalminer$output != null) {
            mainRenderTarget = metalminer$output;
            gameRenderState.windowRenderState.width = metalminer$width;
            gameRenderState.windowRenderState.height = metalminer$height;
            metalminer$output = null;
        }
    }
    @WrapMethod(method = "render")
    private void metalminer$restoreOnException(Operation<Void> original) {
        try { original.call(); }
        finally { metalminer$restoreTarget(); }
    }
    @Inject(method = "close", at = @At("HEAD"))
    private void metalminer$close(CallbackInfo ci) {
        metalminer$restoreTarget();
        metalminer$upscaler.close();
    }
}
