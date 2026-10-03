package com.extrarawstyle.veinminerplus;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(
            ForgeRegistries.MOB_EFFECTS, VeinMinerPlus.MODID);

    public static final RegistryObject<MobEffect> CHAIN_SATURATION = EFFECTS.register(
            "chain_saturation", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xE7B84B) {
            });

    private ModEffects() {
    }

    static void register(IEventBus eventBus) {
        EFFECTS.register(eventBus);
    }
}
