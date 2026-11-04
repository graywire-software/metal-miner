#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build
xcrun clang++ -std=c++17 -fobjc-arc -O2 -mmacosx-version-min=13.0 \
  -DMETALMINER_SELFTEST native/bridge.mm native/selftest.mm \
  -framework Foundation -framework Metal -framework MetalFX -framework IOSurface -framework OpenGL \
  -o build/native-selftest
./build/native-selftest
