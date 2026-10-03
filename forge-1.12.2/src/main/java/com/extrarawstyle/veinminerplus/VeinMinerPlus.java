package com.extrarawstyle.veinminerplus;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.command.CommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

@Mod(modid = VeinMinerPlus.MODID, name = VeinMinerPlus.NAME, version = VeinMinerPlus.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        dependencies = "required-after:forge@[14.23.5.2860,)")
public class VeinMinerPlus {
    public static final String MODID = "veinminerplus";
    public static final String NAME = "VeinMinerPlus";
    public static final String VERSION = "1.2.10-forge1122";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.extrarawstyle.veinminerplus.ClientProxy",
            serverSide = "com.extrarawstyle.veinminerplus.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Config.load(new File(event.getModConfigurationDirectory(), MODID + ".cfg"));
        NetworkHandler.register();
        MinecraftForge.EVENT_BUS.register(new ChainEvents());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        CommandEvents.register((CommandHandler) event.getServer().getCommandManager());
    }
}
