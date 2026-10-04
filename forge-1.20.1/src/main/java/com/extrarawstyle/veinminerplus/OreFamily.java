package com.extrarawstyle.veinminerplus;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/** Shared ore-family naming used by blast matching and client xray selection. */
public final class OreFamily {
    private static final String ORE_SUFFIX = "_ore";
    private static final String[] HOST_STONES = { "deepslate", "slate", "stone", "endstone", "netherrack",
            "nether", "end", "other", "blackstone", "basalt", "tuff", "granite", "diorite", "andesite",
            "marble", "limestone" };

    private OreFamily() {
    }

    public static String key(Block block) {
        ResourceLocation id = block == null ? null : BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) {
            return null;
        }

        String path = id.getPath();
        if (!path.endsWith(ORE_SUFFIX) || path.length() == ORE_SUFFIX.length()) {
            return null;
        }

        String base = path.substring(0, path.length() - ORE_SUFFIX.length());
        for (String host : HOST_STONES) {
            String suffix = "_" + host;
            if (base.length() > suffix.length() && base.endsWith(suffix)) {
                base = base.substring(0, base.length() - suffix.length());
                break;
            }
            String prefix = host + "_";
            if (base.length() > prefix.length() && base.startsWith(prefix)) {
                base = base.substring(prefix.length());
                break;
            }
        }

        return base.isEmpty() ? null : id.getNamespace() + ":" + base;
    }
}
