package com.yesmenn.veilirislights.compat.veil;

public final class VoxelShadowGridState {

    private static boolean ready;

    private VoxelShadowGridState() {
    }

    public static boolean isReady() {
        return ready;
    }

    public static void setReady(boolean ready) {
        VoxelShadowGridState.ready = ready;
    }
}
