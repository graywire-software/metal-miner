#pragma once

// A single-frame, luma-guided edge filter. Samples along the local edge rather
// than blurring the whole image. Runs on the completed MetalFX output, never UI.
static const char* edgeSmoothingSource = R"METAL(
#include <metal_stdlib>
using namespace metal;

float brightness(float3 color) { return dot(color, float3(0.299, 0.587, 0.114)); }

kernel void smooth_edges(texture2d<float, access::sample> source [[texture(0)]],
                         texture2d<float, access::write> destination [[texture(1)]],
                         uint2 pixel [[thread_position_in_grid]]) {
    uint2 size(source.get_width(), source.get_height());
    if (any(pixel >= size)) return;
    constexpr sampler linearSampler(coord::normalized, address::clamp_to_edge, filter::linear);
    float2 texel = 1.0 / float2(size);
    float2 uv = (float2(pixel) + 0.5) * texel;
    float4 center = source.sample(linearSampler, uv);
    float nw = brightness(source.sample(linearSampler, uv + float2(-1, -1) * texel).rgb);
    float ne = brightness(source.sample(linearSampler, uv + float2( 1, -1) * texel).rgb);
    float sw = brightness(source.sample(linearSampler, uv + float2(-1,  1) * texel).rgb);
    float se = brightness(source.sample(linearSampler, uv + float2( 1,  1) * texel).rgb);
    float mid = brightness(center.rgb);
    float darkest = min(mid, min(min(nw, ne), min(sw, se)));
    float brightest = max(mid, max(max(nw, ne), max(sw, se)));
    if (brightest - darkest < max(0.03125, brightest * 0.125)) {
        destination.write(center, pixel);
        return;
    }
    float2 alongEdge(sw + se - nw - ne, nw + sw - ne - se);
    float stabilizer = max((nw + ne + sw + se) * 0.03125, 0.0078125);
    alongEdge /= min(abs(alongEdge.x), abs(alongEdge.y)) + stabilizer;
    alongEdge = clamp(alongEdge, float2(-8), float2(8)) * texel;
    float3 nearColor = (source.sample(linearSampler, uv - alongEdge / 6.0).rgb
                     + source.sample(linearSampler, uv + alongEdge / 6.0).rgb) * 0.5;
    float3 wideColor = nearColor * 0.5
                    + (source.sample(linearSampler, uv - alongEdge * 0.5).rgb
                     + source.sample(linearSampler, uv + alongEdge * 0.5).rgb) * 0.25;
    float wideBrightness = brightness(wideColor);
    float3 filtered = wideBrightness < darkest || wideBrightness > brightest ? nearColor : wideColor;
    destination.write(float4(filtered, center.a), pixel);
}
)METAL";
