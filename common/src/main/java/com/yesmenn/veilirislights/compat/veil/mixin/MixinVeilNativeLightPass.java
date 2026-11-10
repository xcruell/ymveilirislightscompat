package com.yesmenn.veilirislights.compat.veil.mixin;

import foundry.veil.Veil;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.dynamicbuffer.DynamicBufferType;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.CullFrustum;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Veil refuses to enable its albedo/normal dynamic buffers while Iris is loaded, but still runs its
 * deferred light pass. That pass then samples the missing texture as albedo, which shows up as a
 * full-screen magenta checkerboard and a magenta first-person hand. VILC renders the lights itself,
 * so Veil's own pass is skipped whenever the albedo buffer is unavailable.
 */
@Mixin(value = VeilRenderSystem.class, remap = false)
public abstract class MixinVeilNativeLightPass {

    @Inject(method = "drawLights", at = @At("HEAD"), cancellable = true)
    private static void vilc$skipPassWithoutAlbedo(
            ProfilerFiller profiler,
            CullFrustum cullFrustum,
            boolean renderInscattering,
            CallbackInfoReturnable<Boolean> cir) {
        if (Veil.IRIS && (VeilRenderSystem.renderer().getActiveBuffers() & DynamicBufferType.ALBEDO.getMask()) == 0) {
            AdvancedFbo.unbind();
            cir.setReturnValue(false);
        }
    }
}
