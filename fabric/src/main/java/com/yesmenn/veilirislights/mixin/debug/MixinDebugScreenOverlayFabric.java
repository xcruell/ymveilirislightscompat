package com.yesmenn.veilirislights.mixin.debug;

import com.yesmenn.veilirislights.client.VeilIrisLightsClientCommon;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public abstract class MixinDebugScreenOverlayFabric {

    @Inject(method = "getSystemInformation", at = @At("RETURN"))
    private void vilc$appendDiagnostics(CallbackInfoReturnable<List<String>> cir) {
        VeilIrisLightsClientCommon.appendDebugText(cir.getReturnValue());
    }
}
