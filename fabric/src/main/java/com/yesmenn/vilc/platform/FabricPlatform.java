package com.yesmenn.vilc.platform;

import com.yesmenn.vilc.YMVilc;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class FabricPlatform implements Platform.Impl {

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public String modVersion() {
        return FabricLoader.getInstance()
                .getModContainer(YMVilc.MODID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
