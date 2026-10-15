package com.extrarawstyle.veinminerplus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.item.ItemEntity;

@Mixin(value = ItemEntity.class, remap = false)
public abstract class Ae2BypassDropMergeMixin {
    @Inject(method = {
            "tryToMerge(Lnet/minecraft/world/entity/item/ItemEntity;)V",
            "m_32015_(Lnet/minecraft/world/entity/item/ItemEntity;)V"
    }, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void veinminerplus$preserveAeStorageBypass(ItemEntity other, CallbackInfo callback) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.getPersistentData().getBoolean("veinminerplusBypassAeStorage")
                != other.getPersistentData().getBoolean("veinminerplusBypassAeStorage")) {
            callback.cancel();
        }
    }
}
