package com.yesmenn.veilirislights.compat.veil.mixin;

import foundry.veil.api.resource.VeilResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Files;
import java.nio.file.Path;

@Mixin(targets = "foundry.veil.impl.resource.VeilResourceManagerImpl$PackResourceListener", remap = false)
public class MixinPackResourceListener {

    @Inject(method = "listen", at = @At("HEAD"), cancellable = true)
    private void veilIrisLights$skipMissingDevelopmentPath(
            VeilResource<?> resource,
            Path listenPath,
            CallbackInfo ci) {
        Path folder = listenPath.getParent();
        if (folder != null && !Files.isDirectory(folder)) {
            ci.cancel();
        }
    }
}
