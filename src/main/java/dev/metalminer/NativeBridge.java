package dev.metalminer;

import java.io.*;
import java.nio.file.*;

public final class NativeBridge {
    private static boolean loaded;
    private NativeBridge() {}
    public static void load() throws IOException {
        if (loaded) return;
        if (!System.getProperty("os.name").startsWith("Mac") ||
            !(System.getProperty("os.arch").equals("aarch64") || System.getProperty("os.arch").equals("arm64")))
            throw new IOException("Metal Miner requires an Apple Silicon Mac");
        try (InputStream input = NativeBridge.class.getResourceAsStream("/natives/macos-arm64/libmetalminer.dylib")) {
            if (input == null) throw new IOException("Bundled MetalFX library is missing");
            Path library = Files.createTempFile("metal-miner-", ".dylib");
            Files.copy(input, library, StandardCopyOption.REPLACE_EXISTING);
            library.toFile().deleteOnExit();
            System.load(library.toAbsolutePath().toString());
            loaded = true;
        }
    }
    public static native long create(int inputWidth, int inputHeight, int outputWidth, int outputHeight);
    public static native boolean scale(long handle, int source, int destination, int displayWidth, int displayHeight, boolean smooth, boolean edgeSmoothing);
    public static native void destroy(long handle);
    public static native String error();
}
