package com.yesmenn.vilc;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.yesmenn.vilc.client.screen.ConfigScreenBridge;

public final class YMVilcModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreenBridge::create;
    }
}
