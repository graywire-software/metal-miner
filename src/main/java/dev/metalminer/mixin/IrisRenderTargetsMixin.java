package dev.metalminer.mixin;

import com.mojang.renderpearl.api.textures.GpuTexture;
import net.irisshaders.iris.gl.texture.DepthBufferFormat;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RenderTargets.class, remap = false)
public abstract class IrisRenderTargetsMixin {
    @Shadow private GpuTexture currentDepthTexture;
    @Shadow private int cachedDepthBufferVersion;

    // Iris's version counters belong to individual RenderTarget objects. Switching
    // objects can produce an equal counter but a different depth texture. Make its
    // existing reattachment path run on identity changes as well as version changes.
    @Inject(method = "resizeIfNeeded", at = @At("HEAD"))
    private void metalminer$trackTextureIdentity(int version, GpuTexture texture, int width, int height,
            DepthBufferFormat format, PackDirectives directives, CallbackInfoReturnable<Boolean> ci) {
        if (texture != currentDepthTexture) cachedDepthBufferVersion = version - 1;
    }
}
