package com.extrarawstyle.veinminerplus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraftforge.event.entity.player.EntityItemPickupEvent;

@Pseudo
@Mixin(targets = "de.mari_023.ae2wtlib.AE2wtlibForge", remap = false)
public abstract class Ae2WirelessTerminalsPickupMixin {
    @Inject(method = "handle(Lnet/minecraftforge/event/entity/player/EntityItemPickupEvent;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void veinminerplus$bypassAeStorage(EntityItemPickupEvent event, CallbackInfo callback) {
        if (event.getItem().getPersistentData().getBoolean("veinminerplusBypassAeStorage")) {
            callback.cancel();
        }
    }
}
