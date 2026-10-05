package com.extrarawstyle.veinminerplus;

import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = VeinMinerPlus.MODID, value = Side.CLIENT)
public final class VeinMinerPlusClientEvents {
    private VeinMinerPlusClientEvents() {
    }

    @SubscribeEvent
    public static void onMouse(MouseEvent event) {
        if (!VeinMinerPlusClient.isModeSelectorOpen() || event.getDwheel() == 0) {
            return;
        }
        int direction = event.getDwheel() > 0 ? -1 : 1;
        VeinMinerPlusClient.clientMode = ChainMode.cycle(VeinMinerPlusClient.clientMode, direction);
        NetworkHandler.sendModeChange(VeinMinerPlusClient.clientMode);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRender(RenderGameOverlayEvent.Post event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.TEXT) {
            return;
        }
        if (event.getType() == RenderGameOverlayEvent.ElementType.ALL) {
            VeinMinerPlusClient.renderChainProgress(event.getResolution());
            VeinMinerPlusClient.renderLowTpsRadiusNotice(event.getResolution());
            VeinMinerPlusClient.renderModeMenu();
        }
    }
}
