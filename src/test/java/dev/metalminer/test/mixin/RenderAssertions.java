package dev.metalminer.test.mixin;

import dev.metalminer.*;
import dev.metalminer.test.IntegrationTest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class RenderAssertions {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void checkWorld(CallbackInfo ci) {
        var client = Minecraft.getInstance();
        var target = client.gameRenderer.mainRenderTarget();
        var window = client.getWindow();
        var resolution = MetalMiner.renderSettings().resolve(window.getWidth(), window.getHeight());
        int width = resolution.inputWidth();
        int height = resolution.inputHeight();
        if (target.width != width || target.height != height)
            throw new AssertionError("World target mismatch: " + target.width + "x" + target.height + " vs " + width + "x" + height + "; " + MetalMiner.status);
        IntegrationTest.worldFrames++;
    }
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
    private void checkUi(CallbackInfo ci) {
        var client = Minecraft.getInstance();
        var target = client.gameRenderer.mainRenderTarget();
        var state = client.gameRenderer.gameRenderState().windowRenderState;
        if (target.width != client.getWindow().getWidth() || target.height != client.getWindow().getHeight()
                || state.width != target.width || state.height != target.height)
            throw new AssertionError("GUI was not restored to native resolution");
    }
}
