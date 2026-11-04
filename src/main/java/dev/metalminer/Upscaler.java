package dev.metalminer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import org.lwjgl.opengl.GL33C;
import java.io.IOException;

/** Owned exclusively by the render thread. No game texture is owned by native code. */
public final class Upscaler implements AutoCloseable {
    private RenderTarget world;
    private long handle;
    private int outputWidth, outputHeight;
    private boolean failed;
    private int fallbackRead, fallbackDraw;
    private long frames;
    private String activeStatus;
    private ScalingSettings.Resolution lastResolution;
    private Preset lastPreset;
    private ScalingSettings.PresentationFilter filter;
    private boolean edgeSmoothing;

    public RenderTarget prepare(RenderTarget output) {
        ScalingSettings settings = MetalMiner.renderSettings();
        Preset preset = settings.preset();
        if (preset == Preset.OFF || failed) { close(); return null; }
        if (!(output.getColorTexture() instanceof GlTexture)) {
            fail("MetalFX needs the OpenGL renderer used by Iris", null);
            return null;
        }
        if (output.width < 2 || output.height < 2) return null;
        var resolution = settings.resolve(output.width, output.height);
        int iw = resolution.inputWidth(), ih = resolution.inputHeight();
        int ow = resolution.outputWidth(), oh = resolution.outputHeight();
        filter = settings.filter();
        edgeSmoothing = settings.edgeSmoothing();
        try {
            if (world == null || world.width != iw || world.height != ih || outputWidth != ow || outputHeight != oh) {
                close();
                NativeBridge.load();
                handle = NativeBridge.create(iw, ih, ow, oh);
                if (handle == 0) throw new IOException(NativeBridge.error());
                world = new TextureTarget("Metal Miner world", iw, ih, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
                outputWidth = ow; outputHeight = oh;
            }
            if (!resolution.equals(lastResolution) || lastPreset != preset) {
                lastResolution = resolution; lastPreset = preset;
                activeStatus = preset.label + ": " + resolution.inputLabel() + " → " + resolution.outputLabel()
                        + (ow != output.width || oh != output.height ? " → screen " + resolution.nativeLabel() : "");
                MetalMiner.LOG.info("MetalFX {}: world {}x{} -> upscale {}x{} -> screen/UI {}x{}", preset, iw, ih, ow, oh, output.width, output.height);
            }
            MetalMiner.status = activeStatus;
            return world;
        } catch (IOException | LinkageError | RuntimeException e) {
            fail("MetalFX unavailable: " + e.getMessage(), e);
            close();
            return null;
        }
    }

    public void present(RenderTarget output) {
        int source = ((GlTexture)world.getColorTexture()).glId();
        int destination = ((GlTexture)output.getColorTexture()).glId();
        if (!NativeBridge.scale(handle, source, destination, output.width, output.height,
                filter == ScalingSettings.PresentationFilter.SMOOTH, edgeSmoothing)) {
            // Keep this frame visible, then render natively on subsequent frames.
            fail("MetalFX stopped: " + NativeBridge.error(), null);
            fallback(source, destination, output);
        } else if (++frames == 1) {
            MetalMiner.LOG.info("First MetalFX world frame presented before native GUI rendering");
        }
    }

    private void fallback(int source, int destination, RenderTarget output) {
        int read = GL33C.glGetInteger(GL33C.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL33C.glGetInteger(GL33C.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean scissor = GL33C.glIsEnabled(GL33C.GL_SCISSOR_TEST);
        boolean srgb = GL33C.glIsEnabled(GL33C.GL_FRAMEBUFFER_SRGB);
        try {
            if (fallbackRead == 0) fallbackRead = GL33C.glGenFramebuffers();
            if (fallbackDraw == 0) fallbackDraw = GL33C.glGenFramebuffers();
            GL33C.glDisable(GL33C.GL_SCISSOR_TEST); GL33C.glDisable(GL33C.GL_FRAMEBUFFER_SRGB);
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, fallbackRead);
            GL33C.glFramebufferTexture2D(GL33C.GL_READ_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_TEXTURE_2D, source, 0);
            GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, fallbackDraw);
            GL33C.glFramebufferTexture2D(GL33C.GL_DRAW_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_TEXTURE_2D, destination, 0);
            GL33C.glBlitFramebuffer(0, 0, world.width, world.height, 0, 0, output.width, output.height, GL33C.GL_COLOR_BUFFER_BIT, GL33C.GL_LINEAR);
        } finally {
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, read);
            GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, draw);
            if (scissor) GL33C.glEnable(GL33C.GL_SCISSOR_TEST);
            if (srgb) GL33C.glEnable(GL33C.GL_FRAMEBUFFER_SRGB);
        }
    }
    private void fail(String reason, Throwable error) {
        failed = true;
        MetalMiner.status = reason + ". Using native rendering; restart to retry.";
        MetalMiner.LOG.error(MetalMiner.status, error);
    }
    @Override public void close() {
        if (handle != 0) { NativeBridge.destroy(handle); handle = 0; }
        if (world != null) { world.destroyBuffers(); world = null; }
        if (fallbackRead != 0) { GL33C.glDeleteFramebuffers(fallbackRead); fallbackRead = 0; }
        if (fallbackDraw != 0) { GL33C.glDeleteFramebuffers(fallbackDraw); fallbackDraw = 0; }
    }
}
