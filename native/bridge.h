#pragma once
#include <stdint.h>

// All calls, including destruction, require the creating OpenGL context current.
void* mm_create(int inputWidth, int inputHeight, int outputWidth, int outputHeight);
bool mm_scale(void* handle, unsigned sourceTexture, unsigned destinationTexture,
              int displayWidth = 0, int displayHeight = 0, bool smooth = true, bool edgeSmoothing = false);
void mm_destroy(void* handle);
const char* mm_error();
