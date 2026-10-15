package com.extrarawstyle.veinminerplus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.item.ItemEntity;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMergeMixin {
    @Inject(method = "tryToMerge", at = @At("HEAD"), cancellable = true, require = 1)
    private void veinminerplus$keepBypassedDropsSeparate(ItemEntity other, CallbackInfo callback) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.getPersistentData().getBoolean("veinminerplusBypassAeStorage")
                != other.getPersistentData().getBoolean("veinminerplusBypassAeStorage")) {
            callback.cancel();
        }
    }
}
