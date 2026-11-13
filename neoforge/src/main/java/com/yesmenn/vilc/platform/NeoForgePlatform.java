package com.yesmenn.vilc.platform;

import com.yesmenn.vilc.YMVilc;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class NeoForgePlatform implements Platform.Impl {

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public String modVersion() {
        return ModList.get()
                .getModContainerById(YMVilc.MODID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("unknown");
    }
}
