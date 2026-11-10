package com.yesmenn.veilirislights.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class VeilIrisLightOcclusion {

    private static final Set<ResourceLocation> NON_OCCLUDING_BLOCKS =
            ConcurrentHashMap.newKeySet();

    static {
        registerColoredLights("yesmenn");
        registerColoredLights("lightsyesmenn");
    }

    private VeilIrisLightOcclusion() {
    }

    public static void registerNonOccluding(ResourceLocation blockId) {
        NON_OCCLUDING_BLOCKS.add(blockId);
    }

    public static void unregisterNonOccluding(ResourceLocation blockId) {
        NON_OCCLUDING_BLOCKS.remove(blockId);
    }

    public static boolean isNonOccluding(BlockState state) {
        return NON_OCCLUDING_BLOCKS.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    private static void registerColoredLights(String namespace) {
        registerNonOccluding(ResourceLocation.fromNamespaceAndPath(namespace, "colored_light"));
        registerNonOccluding(ResourceLocation.fromNamespaceAndPath(namespace, "colored_light_tube"));
        registerNonOccluding(ResourceLocation.fromNamespaceAndPath(namespace, "colored_light_panel"));
        registerNonOccluding(ResourceLocation.fromNamespaceAndPath(namespace, "colored_light_spotlight"));
        registerNonOccluding(ResourceLocation.fromNamespaceAndPath(namespace, "invisible_colored_light_spotlight"));
    }
}
