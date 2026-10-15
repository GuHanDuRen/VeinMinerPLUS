package com.extrarawstyle.veinminerplus;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

final class Ae2DropStorage {
    private Ae2DropStorage() {
    }

    static boolean isAvailable() {
        return ModList.get().isLoaded("ae2wtlib");
    }

    static boolean hasMagnet(ServerPlayer player) {
        return isAvailable() && WirelessTerminals.hasMagnet(player);
    }

    static void insert(ServerPlayer player, ItemStack stack) {
        if (!stack.isEmpty() && isAvailable()) {
            WirelessTerminals.insert(player, stack);
        }
    }

    private static final class WirelessTerminals {
        private static boolean hasMagnet(ServerPlayer player) {
            var handler = de.mari_023.ae2wtlib.wct.CraftingTerminalHandler.getCraftingTerminalHandler(player);
            if (hasMagnet(handler.getCraftingTerminal())) {
                return true;
            }
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                if (hasMagnet(player.getInventory().getItem(slot))) {
                    return true;
                }
            }
            for (ItemStack terminal : accessories(player)) {
                if (hasMagnet(terminal)) {
                    return true;
                }
            }
            return false;
        }

        private static void insert(ServerPlayer player, ItemStack stack) {
            var handler = de.mari_023.ae2wtlib.wct.CraftingTerminalHandler.getCraftingTerminalHandler(player);
            ItemStack selected = handler.getCraftingTerminal();
            var grid = hasMagnet(selected) && handler.inRange() ? handler.getTargetGrid() : null;
            if (grid != null && grid.getStorageService() != null) {
                // The native API mutates the stack even when only part of it fits.
                de.mari_023.ae2wtlib.AE2wtlibEvents.insertStackInME(stack, player);
                return;
            }
            var host = findFallback(player, selected);
            if (host == null) {
                return;
            }
            var tag = host.getItemStack().getOrCreateTag();
            var config = appeng.util.ConfigInventory.configTypes(27, () -> {
            });
            config.readFromChildTag(tag, "insertConfig");
            var builder = appeng.util.prioritylist.IPartitionList.builder();
            builder.fuzzyMode(appeng.api.config.FuzzyMode.IGNORE_ALL);
            for (int slot = 0; slot < config.size(); slot++) {
                builder.add(config.getKey(slot));
            }
            var key = appeng.api.stacks.AEItemKey.of(stack);
            var mode = tag.getBoolean("insertMode")
                    ? appeng.api.config.IncludeExclude.WHITELIST : appeng.api.config.IncludeExclude.BLACKLIST;
            if (builder.build().matchesFilter(key, mode)) {
                long inserted = host.getInventory().insert(key, stack.getCount(),
                        appeng.api.config.Actionable.MODULATE, new appeng.me.helpers.PlayerSource(player, null));
                stack.shrink((int) inserted);
            }
        }

        private static boolean hasMagnet(ItemStack terminal) {
            return de.mari_023.ae2wtlib.wut.WUTHandler.hasTerminal(terminal, "crafting")
                    && de.mari_023.ae2wtlib.wct.magnet_card.MagnetHandler.getMagnetMode(terminal).pickupToME();
        }

        private static java.util.List<ItemStack> accessories(ServerPlayer player) {
            var terminals = new java.util.ArrayList<ItemStack>();
            if (de.mari_023.ae2wtlib.Platform.trinketsPresent()) {
                de.mari_023.ae2wtlib.curio.CurioHelper.addAllCurios(terminals, player);
            }
            return terminals;
        }

        private static de.mari_023.ae2wtlib.terminal.WTMenuHost findFallback(
                ServerPlayer player, ItemStack selected) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack terminal = player.getInventory().getItem(slot);
                if (terminal != selected && hasMagnet(terminal)) {
                    var host = appeng.menu.locator.MenuLocators.forInventorySlot(slot)
                            .locate(player, de.mari_023.ae2wtlib.terminal.WTMenuHost.class);
                    if (isConnected(host)) {
                        return host;
                    }
                }
            }
            for (ItemStack terminal : accessories(player)) {
                if (terminal != selected && hasMagnet(terminal)
                        && terminal.getItem() instanceof de.mari_023.ae2wtlib.terminal.IUniversalWirelessTerminalItem item
                        && item.getMenuHost(player, null, terminal) instanceof de.mari_023.ae2wtlib.terminal.WTMenuHost host
                        && isConnected(host)) {
                    return host;
                }
            }
            return null;
        }

        private static boolean isConnected(de.mari_023.ae2wtlib.terminal.WTMenuHost host) {
            return host != null && host.stillValid() && host.rangeCheck() && host.getActionableNode() != null
                    && host.getActionableNode().getGrid().getStorageService() != null;
        }
    }
}
