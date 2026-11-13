package com.yesmenn.vilc.mixin.iris;

import com.google.common.collect.ImmutableSet;
import com.yesmenn.vilc.compat.light.VilcSceneLightCompositor;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IrisRenderingPipeline.class)
public abstract class MixinIrisFinalCompositeBridge {

    @Shadow(remap = false)
    @Final
    private RenderTargets renderTargets;

    @Shadow(remap = false)
    @Final
    private ImmutableSet<Integer> flippedAfterTranslucent;

    @Inject(method = "finalizeLevelRendering", at = @At("TAIL"), remap = false)
    private void vilc$afterIrisFinalComposite(CallbackInfo callbackInfo) {
        this.vilc$drawLightsIntoIrisTarget();
    }

    @Inject(method = "destroy", at = @At("HEAD"), remap = false)
    private void vilc$releaseIrisCompositeResources(CallbackInfo callbackInfo) {
        VilcSceneLightCompositor.releaseGpuResources();
    }

    @Unique
    private void vilc$drawLightsIntoIrisTarget() {
        VilcSceneLightCompositor.renderShaderpackLights(this.renderTargets, this.flippedAfterTranslucent);
    }
}
