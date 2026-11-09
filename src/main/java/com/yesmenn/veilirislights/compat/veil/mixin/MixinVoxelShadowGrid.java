package com.yesmenn.veilirislights.compat.veil.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yesmenn.veilirislights.api.VeilIrisLightOcclusion;
import com.yesmenn.veilirislights.compat.veil.VoxelShadowGridState;
import com.yesmenn.veilirislights.config.LightRenderConfig;
import foundry.veil.api.client.registry.LightTypeRegistry;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.DDALightData;
import foundry.veil.api.client.render.light.data.LightData;
import foundry.veil.api.client.registry.LightTypeRegistry.LightType;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.impl.client.render.light.VoxelShadowGrid;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.BufferUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11C.GL_RED;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL15C.GL_STREAM_DRAW;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.glBufferData;
import static org.lwjgl.opengl.GL15C.glBufferSubData;
import static org.lwjgl.opengl.GL15C.glGenBuffers;
import static org.lwjgl.opengl.GL21C.GL_PIXEL_UNPACK_BUFFER;
import static org.lwjgl.opengl.GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING;
import static org.lwjgl.opengl.GL45C.nglTextureSubImage3D;

@Mixin(value = VoxelShadowGrid.class, remap = false)
public abstract class MixinVoxelShadowGrid {

    @Unique
    private static final TagKey<Block> veilIrisLights$nonOccluding =
            TagKey.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath("veil_iris_lights", "non_occluding"));

    @Unique
    private static final int veilIrisLights$gridBytes =
            VoxelShadowGrid.GRID_SIZE * VoxelShadowGrid.GRID_SIZE * VoxelShadowGrid.GRID_SIZE;

    @Unique
    private static final ByteBuffer veilIrisLights$uploadStaging =
            BufferUtils.createByteBuffer(veilIrisLights$gridBytes);

    @Unique
    private static int veilIrisLights$uploadPbo;

    @Shadow
    private static int textureId;

    @Shadow
    private static ByteBuffer gridBuffer;

    @Shadow
    private static ByteBuffer buildBuffer;

    @Inject(method = "hasOccludedLights", at = @At("HEAD"), cancellable = true)
    private static void veilIrisLights$detectCompatLights(CallbackInfoReturnable<Boolean> cir) {
        // With VILC voxel shadows enabled every light is shadowed, so the grid is needed whenever any light exists,
        // even if the light itself has Veil occlusion disabled.
        boolean forceShadows = LightRenderConfig.get().voxelShadows;
        if (veilIrisLights$needsGrid(LightTypeRegistry.POINT.get(), forceShadows)
                || veilIrisLights$needsGrid(LightTypeRegistry.AREA.get(), forceShadows)
                || veilIrisLights$needsGrid(LightTypeRegistry.SPOT.get(), forceShadows)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private static <T extends LightData> boolean veilIrisLights$needsGrid(LightType<T> type, boolean forceShadows) {
        for (LightRenderHandle<T> handle : VeilRenderSystem.renderer().getLightRenderer().getLights(type)) {
            LightData light = handle.isValid() ? handle.getLightData() : null;
            if (light != null && (forceShadows
                    || light instanceof DDALightData occludable && occludable.isOcclusionEnabled())) {
                return true;
            }
        }
        return false;
    }

    @Inject(method = "setup", at = @At("RETURN"))
    private static void veilIrisLights$trackGridState(CallbackInfo ci) {
        VoxelShadowGridState.setReady(gridBuffer != null);
    }

    @Inject(method = "voxelOccupancy", at = @At("HEAD"), cancellable = true)
    private static void veilIrisLights$expandShadowCasters(
            ClientLevel level,
            BlockPos pos,
            BlockState state,
            CallbackInfoReturnable<Byte> cir) {
        if (state.is(veilIrisLights$nonOccluding)
                || VeilIrisLightOcclusion.isNonOccluding(state)) {
            cir.setReturnValue((byte) 0);
        } else if (state.getBlock() instanceof LeavesBlock) {
            cir.setReturnValue((byte) 0x60);
        }
    }

    /**
     * Stages voxel data in a persistent pixel buffer so the driver never reads
     * directly from Veil's transient native buffer.
     *
     * @author YesMenn
     * @reason Avoid native NVIDIA crashes in state-dependent glTexSubImage3D.
     */
    @Overwrite
    private static void uploadBuffer(ByteBuffer buffer) {
        if (buffer == null || buffer.capacity() < veilIrisLights$gridBytes || textureId == 0) {
            return;
        }
        RenderSystem.assertOnRenderThread();

        ByteBuffer source = buffer.duplicate();
        source.clear();
        source.limit(veilIrisLights$gridBytes);
        veilIrisLights$uploadStaging.clear();
        veilIrisLights$uploadStaging.put(source);
        veilIrisLights$uploadStaging.flip();

        int previousPbo = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        if (veilIrisLights$uploadPbo == 0) {
            veilIrisLights$uploadPbo = glGenBuffers();
        }

        try {
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, veilIrisLights$uploadPbo);
            glBufferData(GL_PIXEL_UNPACK_BUFFER, veilIrisLights$gridBytes, GL_STREAM_DRAW);
            glBufferSubData(GL_PIXEL_UNPACK_BUFFER, 0, veilIrisLights$uploadStaging);
            nglTextureSubImage3D(
                    textureId,
                    0,
                    0,
                    0,
                    0,
                    VoxelShadowGrid.GRID_SIZE,
                    VoxelShadowGrid.GRID_SIZE,
                    VoxelShadowGrid.GRID_SIZE,
                    GL_RED,
                    GL_UNSIGNED_BYTE,
                    0L);
        } finally {
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, previousPbo);
        }
    }
}
