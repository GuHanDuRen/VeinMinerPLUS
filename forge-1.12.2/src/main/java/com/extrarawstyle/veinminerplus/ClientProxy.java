package com.extrarawstyle.veinminerplus;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ClientProxy extends CommonProxy {
    @Override
    public void init() {
        VeinMinerPlusClient.registerKeyBinding();
    }
}
