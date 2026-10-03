package com.extrarawstyle.veinminerplus;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(
            Registries.MOB_EFFECT, VeinMinerPlus.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> CHAIN_SATURATION = EFFECTS.register(
            "chain_saturation", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xE7B84B) {
            });

    private ModEffects() {
    }

    static void register(IEventBus eventBus) {
        EFFECTS.register(eventBus);
    }
}
