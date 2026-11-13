package com.yesmenn.vilc;

import com.yesmenn.vilc.client.YMVilcClient;
import com.yesmenn.vilc.client.screen.ConfigScreenBridge;
import com.yesmenn.vilc.platform.NeoForgePlatform;
import com.yesmenn.vilc.platform.Platform;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(YMVilc.MODID)
public final class YMVilcNeoForge {

    public YMVilcNeoForge(IEventBus modBus, ModContainer container) {
        Platform.init(new NeoForgePlatform());
        YMVilcClient.initialize();
        modBus.addListener(YMVilcClient::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(YMVilcClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(YMVilcClient::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(YMVilcClient::onDebugText);
        IConfigScreenFactory configScreenFactory =
                (ignored, parent) -> ConfigScreenBridge.create(parent);
        container.registerExtensionPoint(IConfigScreenFactory.class, configScreenFactory);
    }
}
