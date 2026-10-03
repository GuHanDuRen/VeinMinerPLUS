package com.extrarawstyle.veinminerplus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod.EventBusSubscriber(modid = VeinMinerPlus.MODID)
public final class ModEffects {
    private static final ResourceLocation CHAIN_SATURATION_ICON = new ResourceLocation(
            VeinMinerPlus.MODID, "textures/mob_effect/chain_saturation.png");
    public static Potion CHAIN_SATURATION;

    private ModEffects() {
    }

    @SubscribeEvent
    public static void registerPotions(RegistryEvent.Register<Potion> event) {
        CHAIN_SATURATION = new Potion(false, 0xE7B84B) {
            @Override
            @SideOnly(Side.CLIENT)
            public void renderInventoryEffect(PotionEffect effect, Gui gui, int x, int y, float z) {
                drawIcon(x + 6, y + 7, 1.0F);
            }

            @Override
            @SideOnly(Side.CLIENT)
            public void renderHUDEffect(PotionEffect effect, Gui gui, int x, int y, float z, float alpha) {
                drawIcon(x + 3, y + 3, alpha);
            }
        }.setRegistryName(VeinMinerPlus.MODID, "chain_saturation")
                .setPotionName("effect.veinminerplus.chain_saturation");
        event.getRegistry().register(CHAIN_SATURATION);
    }

    @SideOnly(Side.CLIENT)
    private static void drawIcon(int x, int y, float alpha) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(CHAIN_SATURATION_ICON);
        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0.0F, 0.0F, 18, 18, 18.0F, 18.0F);
        // The potion icon is rendered during Forge's HUD pass. Restore the
        // shared color immediately so later overlay elements do not inherit
        // the icon's fade alpha.
        GlStateManager.resetColor();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
