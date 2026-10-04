package com.extrarawstyle.veinminerplus;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;

/** Shared ore-family naming used by the legacy client x-ray selector. */
public final class OreFamily {
    private static final String[] HOST_STONES = { "deepslate", "slate", "stone", "endstone", "netherrack",
            "nether", "end", "other", "blackstone", "basalt", "tuff", "granite", "diorite", "andesite",
            "marble", "limestone" };

    private OreFamily() {
    }

    public static String key(Block block) {
        ResourceLocation id = block == null ? null : block.getRegistryName();
        if (id == null || !id.getResourcePath().endsWith("_ore")
                || id.getResourcePath().length() <= "_ore".length()) {
            return null;
        }
        String base = id.getResourcePath().substring(0, id.getResourcePath().length() - 4);
        for (String host : HOST_STONES) {
            String suffix = "_" + host;
            String prefix = host + "_";
            if (base.length() > suffix.length() && base.endsWith(suffix)) {
                base = base.substring(0, base.length() - suffix.length());
                break;
            }
            if (base.length() > prefix.length() && base.startsWith(prefix)) {
                base = base.substring(prefix.length());
                break;
            }
        }
        return base.isEmpty() ? null : id.getResourceDomain() + ":" + base;
    }
}
