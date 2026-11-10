package com.yesmenn.veilirislights.platform;

import java.nio.file.Path;

public final class Platform {
    private static volatile Impl instance;

    private Platform() {}

    public static void init(Impl impl) {
        instance = impl;
    }

    public static Path getConfigDir() {
        return instance.configDir();
    }

    public static String getModVersion() {
        return instance.modVersion();
    }

    public interface Impl {
        Path configDir();
        String modVersion();
    }
}
