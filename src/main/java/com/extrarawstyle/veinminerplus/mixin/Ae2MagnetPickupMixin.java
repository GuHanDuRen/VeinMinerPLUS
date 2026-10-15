package com.extrarawstyle.veinminerplus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

@Pseudo
@Mixin(targets = "de.mari_023.ae2wtlib.AE2wtlibForge", remap = false)
public abstract class Ae2MagnetPickupMixin {
    @Inject(method = "handle(Lnet/neoforged/neoforge/event/entity/player/ItemEntityPickupEvent$Pre;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void veinminerplus$skipBypassedDrop(ItemEntityPickupEvent.Pre event, CallbackInfo callback) {
        if (event.getItemEntity().getPersistentData().getBoolean("veinminerplusBypassAeStorage")) {
            callback.cancel();
        }
    }
}
