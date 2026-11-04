#define GL_SILENCE_DEPRECATION
#import <Foundation/Foundation.h>
#import <Metal/Metal.h>
#import <MetalFX/MetalFX.h>
#import <IOSurface/IOSurface.h>
#import <OpenGL/OpenGL.h>
#import <OpenGL/CGLIOSurface.h>
#import <OpenGL/gl3.h>
#include <string>
#include "bridge.h"
#include "edge_smoothing.h"
#ifndef METALMINER_SELFTEST
#include <jni.h>
#endif

static thread_local std::string lastError;
static bool fail(NSString* message) {
    lastError = message.UTF8String ?: "Unknown MetalFX error";
    return false;
}
const char* mm_error() { return lastError.c_str(); }

// Raw OpenGL calls must leave Minecraft's cached GL state valid.
struct GlState {
    GLint readFbo, drawFbo, rectangle;
    GLboolean scissor, srgb;
    GlState() {
        glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &readFbo);
        glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &drawFbo);
        glGetIntegerv(GL_TEXTURE_BINDING_RECTANGLE, &rectangle);
        scissor = glIsEnabled(GL_SCISSOR_TEST);
        srgb = glIsEnabled(GL_FRAMEBUFFER_SRGB);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_FRAMEBUFFER_SRGB);
    }
    ~GlState() {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readFbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFbo);
        glBindTexture(GL_TEXTURE_RECTANGLE, rectangle);
        if (scissor) glEnable(GL_SCISSOR_TEST);
        if (srgb) glEnable(GL_FRAMEBUFFER_SRGB);
    }
};

struct Surface {
    IOSurfaceRef surface = nullptr;
    id<MTLTexture> texture = nil;
    GLuint glTexture = 0, fbo = 0;
    ~Surface() {
        if (fbo) glDeleteFramebuffers(1, &fbo);
        if (glTexture) glDeleteTextures(1, &glTexture);
        texture = nil;
        if (surface) CFRelease(surface);
    }
    bool create(id<MTLDevice> device, int width, int height, MTLTextureUsage usage) {
        size_t rowBytes = IOSurfaceAlignProperty(kIOSurfaceBytesPerRow, width * 4);
        NSDictionary* properties = @{
            (id)kIOSurfaceWidth: @(width), (id)kIOSurfaceHeight: @(height),
            (id)kIOSurfaceBytesPerElement: @4, (id)kIOSurfaceBytesPerRow: @(rowBytes),
            (id)kIOSurfaceAllocSize: @(rowBytes * height),
            (id)kIOSurfacePixelFormat: @((uint32_t)0x42475241) // BGRA
        };
        surface = IOSurfaceCreate((__bridge CFDictionaryRef)properties);
        if (!surface) return fail(@"Cannot allocate shared IOSurface");
        MTLTextureDescriptor* descriptor = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatBGRA8Unorm width:width height:height mipmapped:NO];
        descriptor.storageMode = MTLStorageModeShared;
        descriptor.usage = usage | MTLTextureUsageRenderTarget;
        texture = [device newTextureWithDescriptor:descriptor iosurface:surface plane:0];
        if (!texture) return fail(@"Cannot create Metal texture from IOSurface");
        glGenTextures(1, &glTexture);
        glBindTexture(GL_TEXTURE_RECTANGLE, glTexture);
        glTexParameteri(GL_TEXTURE_RECTANGLE, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_RECTANGLE, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_RECTANGLE, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_RECTANGLE, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        CGLError result = CGLTexImageIOSurface2D(CGLGetCurrentContext(), GL_TEXTURE_RECTANGLE,
            GL_RGBA8, width, height, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, surface, 0);
        if (result != kCGLNoError) return fail([NSString stringWithFormat:@"OpenGL/IOSurface sharing failed: %s", CGLErrorString(result)]);
        glGenFramebuffers(1, &fbo);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_RECTANGLE, glTexture, 0);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            return fail(@"Shared IOSurface framebuffer is incomplete");
        return true;
    }
};

struct Scaler {
    CGLContextObj context;
    int iw, ih, ow, oh;
    id<MTLDevice> device;
    id<MTLCommandQueue> queue;
    id<MTLFXSpatialScaler> effect;
    id<MTLTexture> scaledTexture;
    id<MTLComputePipelineState> smoothingPipeline;
    Surface input, output;
    GLuint sourceFbo = 0, destinationFbo = 0;
    Scaler(int a, int b, int c, int d): context(CGLGetCurrentContext()), iw(a), ih(b), ow(c), oh(d) {}
    ~Scaler() {
        if (sourceFbo) glDeleteFramebuffers(1, &sourceFbo);
        if (destinationFbo) glDeleteFramebuffers(1, &destinationFbo);
    }
    bool initialize() {
        device = MTLCreateSystemDefaultDevice();
        if (!device || ![MTLFXSpatialScalerDescriptor supportsDevice:device])
            return fail(@"MetalFX spatial upscaling is unavailable on this GPU");
        if (!device.hasUnifiedMemory) return fail(@"This build requires an Apple Silicon GPU");
        if ((NSUInteger)ow > 16384 || (NSUInteger)oh > 16384) return fail(@"Output exceeds supported dimensions");
        queue = [device newCommandQueue];
        if (!queue) return fail(@"Cannot create Metal command queue");
        MTLFXSpatialScalerDescriptor* descriptor = [MTLFXSpatialScalerDescriptor new];
        descriptor.inputWidth = iw; descriptor.inputHeight = ih;
        descriptor.outputWidth = ow; descriptor.outputHeight = oh;
        descriptor.colorTextureFormat = MTLPixelFormatBGRA8Unorm;
        descriptor.outputTextureFormat = MTLPixelFormatBGRA8Unorm;
        // The world image has already been tone-mapped and encoded.
        descriptor.colorProcessingMode = MTLFXSpatialScalerColorProcessingModePerceptual;
        effect = [descriptor newSpatialScalerWithDevice:device];
        if (!effect) return fail(@"MetalFX rejected these dimensions or formats");
        if (!input.create(device, iw, ih, effect.colorTextureUsage) ||
            !output.create(device, ow, oh, MTLTextureUsageShaderRead | MTLTextureUsageShaderWrite)) return false;
        // MetalFX requires a private output texture. Copy it on the GPU into the
        // shared IOSurface before handing ownership back to OpenGL.
        MTLTextureDescriptor* scaledDescriptor = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatBGRA8Unorm width:ow height:oh mipmapped:NO];
        scaledDescriptor.storageMode = MTLStorageModePrivate;
        scaledDescriptor.usage = effect.outputTextureUsage | MTLTextureUsageShaderRead;
        scaledTexture = [device newTextureWithDescriptor:scaledDescriptor];
        if (!scaledTexture) return fail(@"Cannot allocate private MetalFX output");
        effect.colorTexture = input.texture;
        effect.outputTexture = scaledTexture;
        effect.inputContentWidth = iw; effect.inputContentHeight = ih;
        glGenFramebuffers(1, &sourceFbo);
        glGenFramebuffers(1, &destinationFbo);
        return true;
    }
    bool initializeSmoothing() {
        if (smoothingPipeline) return true;
        NSError* error = nil;
        id<MTLLibrary> library = [device newLibraryWithSource:[NSString stringWithUTF8String:edgeSmoothingSource] options:nil error:&error];
        if (!library) return fail(error.localizedDescription ?: @"Cannot compile edge smoothing");
        id<MTLFunction> function = [library newFunctionWithName:@"smooth_edges"];
        if (!function) return fail(@"Edge smoothing function is missing");
        smoothingPipeline = [device newComputePipelineStateWithFunction:function error:&error];
        return smoothingPipeline ? true : fail(error.localizedDescription ?: @"Cannot create edge smoothing pipeline");
    }
    bool scale(GLuint source, GLuint destination, int displayWidth, int displayHeight, bool smooth, bool edgeSmoothing) {
        if (CGLGetCurrentContext() != context) return fail(@"MetalFX called from the wrong OpenGL context");
        if (displayWidth == 0) displayWidth = ow;
        if (displayHeight == 0) displayHeight = oh;
        if (displayWidth < ow || displayHeight < oh || displayWidth > 16384 || displayHeight > 16384)
            return fail(@"Invalid presentation dimensions");
        if (edgeSmoothing && !initializeSmoothing()) return false;
        GlState saved;
        glBindFramebuffer(GL_READ_FRAMEBUFFER, sourceFbo);
        glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, source, 0);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        if (glCheckFramebufferStatus(GL_READ_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) return fail(@"World framebuffer is incomplete");
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, input.fbo);
        glBlitFramebuffer(0, 0, iw, ih, 0, 0, iw, ih, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        // Ownership crosses graphics APIs. These waits are intentional: no stale frames,
        // asynchronous overwrite, CPU pixel readback, or extra frame of latency.
        glFinish();
        id<MTLCommandBuffer> command = [queue commandBuffer];
        if (!command) return fail(@"Cannot allocate Metal command buffer");
        command.label = @"Metal Miner spatial upscale";
        [effect encodeToCommandBuffer:command];
        if (edgeSmoothing) {
            // Write straight to the shared surface; no extra image copy or GL state.
            id<MTLComputeCommandEncoder> filter = [command computeCommandEncoder];
            if (!filter) return fail(@"Cannot create edge smoothing encoder");
            filter.label = @"Metal Miner post-upscale edge smoothing";
            [filter setComputePipelineState:smoothingPipeline];
            [filter setTexture:scaledTexture atIndex:0];
            [filter setTexture:output.texture atIndex:1];
            [filter dispatchThreads:MTLSizeMake(ow, oh, 1) threadsPerThreadgroup:MTLSizeMake(16, 16, 1)];
            [filter endEncoding];
        } else {
            id<MTLBlitCommandEncoder> copy = [command blitCommandEncoder];
            if (!copy) return fail(@"Cannot create Metal output copy encoder");
            [copy copyFromTexture:scaledTexture sourceSlice:0 sourceLevel:0 sourceOrigin:MTLOriginMake(0, 0, 0)
                      sourceSize:MTLSizeMake(ow, oh, 1) toTexture:output.texture destinationSlice:0
                destinationLevel:0 destinationOrigin:MTLOriginMake(0, 0, 0)];
            [copy endEncoding];
        }
        [command commit];
        [command waitUntilCompleted];
        if (command.status == MTLCommandBufferStatusError)
            return fail(command.error.localizedDescription ?: @"MetalFX GPU command failed");
        glBindFramebuffer(GL_READ_FRAMEBUFFER, output.fbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, destinationFbo);
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, destination, 0);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) return fail(@"Native-resolution framebuffer is incomplete");
        glBlitFramebuffer(0, 0, ow, oh, 0, 0, displayWidth, displayHeight, GL_COLOR_BUFFER_BIT,
                          smooth ? GL_LINEAR : GL_NEAREST);
        return true;
    }
};

void* mm_create(int iw, int ih, int ow, int oh) {
    @autoreleasepool {
        lastError.clear();
        if (!CGLGetCurrentContext()) { fail(@"An active macOS OpenGL context is required"); return nullptr; }
        if (iw < 2 || ih < 2 || ow < iw || oh < ih || ow > 16384 || oh > 16384) {
            fail(@"Invalid upscale dimensions"); return nullptr;
        }
        GlState saved;
        Scaler* scaler = new Scaler(iw, ih, ow, oh);
        @try {
            if (scaler->initialize()) return scaler;
        } @catch (NSException* e) { fail(e.reason); }
        delete scaler;
        return nullptr;
    }
}
bool mm_scale(void* handle, unsigned source, unsigned destination, int displayWidth, int displayHeight, bool smooth, bool edgeSmoothing) {
    @autoreleasepool {
        if (!handle || !source || !destination || source == destination) return fail(@"Invalid MetalFX texture handle");
        @try { return static_cast<Scaler*>(handle)->scale(source, destination, displayWidth, displayHeight, smooth, edgeSmoothing); }
        @catch (NSException* e) { return fail(e.reason); }
    }
}
void mm_destroy(void* handle) {
    @autoreleasepool { if (handle) { GlState saved; delete static_cast<Scaler*>(handle); } }
}

#ifndef METALMINER_SELFTEST
extern "C" JNIEXPORT jlong JNICALL Java_dev_metalminer_NativeBridge_create(JNIEnv*, jclass, jint iw, jint ih, jint ow, jint oh) {
    return reinterpret_cast<jlong>(mm_create(iw, ih, ow, oh));
}
extern "C" JNIEXPORT jboolean JNICALL Java_dev_metalminer_NativeBridge_scale(JNIEnv*, jclass, jlong handle, jint source, jint destination, jint displayWidth, jint displayHeight, jboolean smooth, jboolean edgeSmoothing) {
    return mm_scale(reinterpret_cast<void*>(handle), source, destination, displayWidth, displayHeight, smooth, edgeSmoothing);
}
extern "C" JNIEXPORT void JNICALL Java_dev_metalminer_NativeBridge_destroy(JNIEnv*, jclass, jlong handle) {
    mm_destroy(reinterpret_cast<void*>(handle));
}
extern "C" JNIEXPORT jstring JNICALL Java_dev_metalminer_NativeBridge_error(JNIEnv* env, jclass) {
    return env->NewStringUTF(mm_error());
}
#endif
