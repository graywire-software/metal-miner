#define GL_SILENCE_DEPRECATION
#import <OpenGL/OpenGL.h>
#import <OpenGL/gl3.h>
#include <cstdio>
#include <cstdlib>
#include <chrono>
#include <vector>
#include "bridge.h"

static void require(bool condition, const char* message) {
    if (!condition) { fprintf(stderr, "FAIL: %s (%s)\n", message, mm_error()); exit(1); }
}
static GLuint texture(int width, int height, const void* pixels) {
    GLuint id; glGenTextures(1, &id); glBindTexture(GL_TEXTURE_2D, id);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    return id;
}
int main() {
    CGLPixelFormatAttribute attributes[] = {kCGLPFAOpenGLProfile, (CGLPixelFormatAttribute)kCGLOGLPVersion_3_2_Core, kCGLPFAAccelerated, (CGLPixelFormatAttribute)0};
    CGLPixelFormatObj format; GLint count; CGLContextObj context;
    require(CGLChoosePixelFormat(attributes, &format, &count) == kCGLNoError, "Choose OpenGL format");
    require(CGLCreateContext(format, nullptr, &context) == kCGLNoError, "Create OpenGL context");
    CGLDestroyPixelFormat(format); CGLSetCurrentContext(context);
    printf("GPU: %s\n", glGetString(GL_RENDERER));
    require(!mm_create(0, 32, 64, 64), "Reject invalid dimensions");
    struct TestCase { int iw, ih, ow, oh, dw, dh; bool smooth; bool edges = false; };
    for (auto t : {TestCase{127,79,254,158,254,158,true}, {128,80,256,160,384,240,true},
                   {129,81,258,162,387,243,false}, {160,100,1280,800,1280,800,true},
                   {160,100,640,400,1280,800,true}, {256,160,256,160,384,240,true},
                   {127,79,254,158,254,158,true,true}, {129,81,258,162,387,243,false,true},
                   {160,100,1280,800,1280,800,true,true}, {256,160,256,160,384,240,true,true}}) {
        int iw = t.iw, ih = t.ih, ow = t.ow, oh = t.oh;
        std::vector<unsigned char> pixels(iw * ih * 4);
        for (int y = 0; y < ih; ++y) for (int x = 0; x < iw; ++x) {
            int p = (y * iw + x) * 4;
            pixels[p] = y < ih / 2 ? 220 : 25;
            pixels[p + 1] = x < iw / 2 ? 180 : 35;
            pixels[p + 2] = 75; pixels[p + 3] = 255;
        }
        GLuint src = texture(iw, ih, pixels.data()), dst = texture(t.dw, t.dh, nullptr);
        GLuint fbo; glGenFramebuffers(1, &fbo); glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, dst, 0);
        glEnable(GL_SCISSOR_TEST); glScissor(0, 0, 1, 1); glEnable(GL_FRAMEBUFFER_SRGB);
        void* scaler = mm_create(iw, ih, ow, oh); require(scaler, "Create shared-surface MetalFX scaler");
        require(mm_scale(scaler, src, dst, t.dw, t.dh, t.smooth, t.edges), "Upscale color pattern and fill native output");
        GLint read, draw; glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &read); glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &draw);
        require(read == (GLint)fbo && draw == (GLint)fbo && glIsEnabled(GL_SCISSOR_TEST) && glIsEnabled(GL_FRAMEBUFFER_SRGB), "Preserve caller GL state");
        glDisable(GL_SCISSOR_TEST); glDisable(GL_FRAMEBUFFER_SRGB);
        for (int y : {t.dh / 4, t.dh * 3 / 4}) for (int x : {t.dw / 4, t.dw * 3 / 4}) {
            unsigned char pixel[4]; glReadPixels(x, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            int r = y < t.dh / 2 ? 220 : 25, g = x < t.dw / 2 ? 180 : 35;
            require(abs(pixel[0] - r) <= 3 && abs(pixel[1] - g) <= 3 && abs(pixel[2] - 75) <= 3,
                "Preserve orientation, RGB channels, and perceptual color");
        }
        require(glGetError() == GL_NO_ERROR, "No OpenGL errors");
        require(!mm_scale(scaler, src, dst, ow - 1, oh), "Reject a presentation target smaller than MetalFX output");
        mm_destroy(scaler); glDeleteFramebuffers(1, &fbo); glDeleteTextures(1, &src); glDeleteTextures(1, &dst);
    }
    // A stair-stepped diagonal at 1x isolates the post-upscale filter. Verify that
    // it changes edge pixels, leaves distant flat pixels alone, and toggles off cleanly.
    {
        int w = 129, h = 81;
        std::vector<unsigned char> diagonal(w * h * 4), raw(w * h * 4), filtered(w * h * 4), restored(w * h * 4);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int p = (y * w + x) * 4;
            diagonal[p] = diagonal[p+1] = diagonal[p+2] = x > 12 + y * 3 / 2 ? 235 : 20;
            diagonal[p+3] = 255;
        }
        GLuint source = texture(w, h, diagonal.data()), target = texture(w, h, nullptr), fbo;
        glGenFramebuffers(1, &fbo); glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target, 0);
        void* handle = mm_create(w, h, w, h); require(handle, "Diagonal scaler");
        require(mm_scale(handle, source, target), "Unfiltered diagonal");
        glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, raw.data());
        require(mm_scale(handle, source, target, w, h, true, true), "Smooth diagonal");
        glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, filtered.data());
        require(mm_scale(handle, source, target), "Disable smoothing without rebuilding scaler");
        glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, restored.data());
        int changed = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int p = (y * w + x) * 4;
            if (abs(filtered[p] - raw[p]) > 3) changed++;
            if (abs(x - 12 - y * 3 / 2) > 12) require(abs(filtered[p] - raw[p]) <= 1, "Smoothing preserves flat regions");
            require(filtered[p+3] == raw[p+3], "Smoothing preserves alpha");
        }
        require(changed > 40, "Smoothing actually changes diagonal edges");
        require(raw == restored, "Disabling smoothing restores original output exactly");
        require(glGetError() == GL_NO_ERROR, "No OpenGL errors after edge smoothing");
        mm_destroy(handle); glDeleteFramebuffers(1, &fbo); glDeleteTextures(1, &source); glDeleteTextures(1, &target);
        printf("Edge smoothing changed %d diagonal pixels; flat regions and alpha preserved\n", changed);
    }
    int ow = 3456, oh = 2234, iw = 2304, ih = 1489;
    GLuint src = texture(iw, ih, nullptr), dst = texture(ow, oh, nullptr);
    void* scaler = mm_create(iw, ih, ow, oh); require(scaler, "Create Retina-size scaler");
    for (int i = 0; i < 5; ++i) require(mm_scale(scaler, src, dst), "Warmup");
    auto start = std::chrono::steady_clock::now();
    for (int i = 0; i < 30; ++i) require(mm_scale(scaler, src, dst), "Retina benchmark");
    glFinish();
    double ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - start).count() / 30;
    printf("Retina 2304x1489 -> 3456x2234 bridge + MetalFX: %.2f ms/frame (synthetic; excludes world rendering)\n", ms);
    for (int i = 0; i < 5; ++i) require(mm_scale(scaler, src, dst, ow, oh, true, true), "Smoothing warmup");
    start = std::chrono::steady_clock::now();
    for (int i = 0; i < 30; ++i) require(mm_scale(scaler, src, dst, ow, oh, true, true), "Smoothing benchmark");
    glFinish();
    ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - start).count() / 30;
    printf("Same Retina bridge + MetalFX + edge smoothing: %.2f ms/frame (synthetic)\n", ms);
    mm_destroy(scaler); glDeleteTextures(1, &src); glDeleteTextures(1, &dst);
    CGLSetCurrentContext(nullptr); CGLDestroyContext(context);
    puts("PASS: 1x/2x/4x/8x scaling, both final filters, optional edge smoothing, color/orientation, GL state, recreation, and invalid dimensions");
}
