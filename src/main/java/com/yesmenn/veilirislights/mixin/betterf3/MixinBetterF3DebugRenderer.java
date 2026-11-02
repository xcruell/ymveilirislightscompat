package com.yesmenn.veilirislights.mixin.betterf3;

import com.yesmenn.veilirislights.compat.light.VilcSceneLightCompositor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Pseudo
@Mixin(targets = "me.cominixo.betterf3.utils.DebugRenderer", remap = false)
public abstract class MixinBetterF3DebugRenderer {

    @Inject(method = "newText", at = @At("HEAD"), remap = false, require = 0)
    private static void veilIrisLights$appendDiagnostics(
            Minecraft minecraft,
            boolean left,
            List<String> leftLines,
            List<String> rightLines,
            CallbackInfoReturnable<List<Component>> cir) {
        if (!left) {
            VilcSceneLightCompositor.appendDiagnostics(rightLines);
        }
    }
}
