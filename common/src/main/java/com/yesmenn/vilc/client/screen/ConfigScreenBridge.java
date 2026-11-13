package com.yesmenn.vilc.client.screen;

import com.yesmenn.ducttape.client.kit.settings.YmSettings;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Function;

/**
 * Entry point for every VILC config screen (mod list and key binding). Mods that build a combined settings
 * screen including VILC's options can register it here, so all entry points open the same screen.
 */
public final class ConfigScreenBridge {
    private static volatile Function<Screen, Screen> combinedScreenFactory;

    private ConfigScreenBridge() {
    }

    public static Screen create(Screen parent) {
        Function<Screen, Screen> factory = combinedScreenFactory;
        if (factory != null) {
            Screen combined = factory.apply(parent);
            if (combined != null) {
                return combined;
            }
        }
        return YmSettings.screen(parent, Component.translatable("screen.ymvilc.config"), VilcSettings.SECTION);
    }

    /** Public API: lets an addon replace VILC's screen with a combined one. Called reflectively by ymCL. */
    public static void registerCombinedScreen(Function<Screen, Screen> factory) {
        combinedScreenFactory = factory;
    }
}
