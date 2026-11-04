package dev.metalminer.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Shadow @Final @Mutable private RenderTarget renderTarget;
    @Inject(method = "render", at = @At("HEAD"))
    private void metalminer$currentWorldTarget(CallbackInfo ci) {
        renderTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
    }
}
