package com.extrarawstyle.veinminerplus;

import java.util.ArrayList;
import java.util.List;

import appeng.api.storage.MEStorage;
import appeng.integration.modules.curios.CuriosIntegration;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import de.mari_023.ae2wtlib.api.terminal.WUTHandler;
import de.mari_023.ae2wtlib.wct.CraftingTerminalHandler;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetHandler;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetHost;
import de.mari_023.ae2wtlib.wut.WTDefinitions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

final class Ae2DropStorage {
    private Ae2DropStorage() {
    }

    static boolean isAvailable() {
        return ModList.get().isLoaded("ae2") && ModList.get().isLoaded("ae2wtlib");
    }

    static void store(ServerPlayer player, List<ItemStack> stacks) {
        if (isAvailable()) {
            WirelessTerminals.store(player, stacks);
        }
    }

    static boolean hasMagnet(ServerPlayer player) {
        return isAvailable() && WirelessTerminals.hasMagnet(player);
    }

    // Keep optional AE classes unloaded when the wireless terminals mod is absent.
    private static final class WirelessTerminals {
        private static boolean hasMagnet(ServerPlayer player) {
            var handler = CraftingTerminalHandler.getCraftingTerminalHandler(player);
            if (MagnetHandler.getMagnetMode(handler.getCraftingTerminal()).pickupToME()) {
                return true;
            }
            for (var locator : candidateLocators(player)) {
                if (MagnetHandler.getMagnetMode(locator.locateItem(player)).pickupToME()) {
                    return true;
                }
            }
            return false;
        }

        private static void store(ServerPlayer player, List<ItemStack> stacks) {
            TerminalStorage terminal = findStorage(player);
            if (terminal == null) {
                return;
            }
            var filter = terminal.magnet().getInsertFilter();
            var mode = terminal.magnet().getInsertMode();
            if (filter.isEmpty() && mode == appeng.api.config.IncludeExclude.WHITELIST) {
                return;
            }
            var source = new appeng.me.helpers.PlayerSource(player);
            for (ItemStack stack : stacks) {
                var key = appeng.api.stacks.AEItemKey.of(stack);
                if (key != null && filter.matchesFilter(key, mode)) {
                    long inserted = terminal.inventory().insert(key, stack.getCount(),
                            appeng.api.config.Actionable.MODULATE, source);
                    stack.shrink((int) inserted);
                }
            }
        }

        private static TerminalStorage findStorage(ServerPlayer player) {
            var handler = CraftingTerminalHandler.getCraftingTerminalHandler(player);
            if (MagnetHandler.getMagnetMode(handler.getCraftingTerminal()).pickupToME() && handler.inRange()) {
                var grid = handler.getTargetGrid();
                var magnet = handler.getMagnetHost();
                if (grid != null && magnet != null) {
                    return new TerminalStorage(grid.getStorageService().getInventory(), magnet);
                }
            }
            for (var locator : candidateLocators(player)) {
                if (!MagnetHandler.getMagnetMode(locator.locateItem(player)).pickupToME()) {
                    continue;
                }
                var host = locator.locate(player, WTMenuHost.class);
                if (host == null || !host.isValid()) {
                    continue;
                }
                host.updateConnectedAccessPoint();
                host.updateLinkStatus();
                if (!host.getLinkStatus().connected()) {
                    continue;
                }
                var node = host.getActionableNode();
                var grid = node == null ? null : node.getGrid();
                if (grid == null) {
                    continue;
                }
                // Bind the native magnetic filter to this candidate without changing WTLib's selected terminal.
                var filterHost = new CraftingTerminalHandler(player) {
                    @Override
                    public ItemStack getCraftingTerminal() {
                        return host.getItemStack();
                    }
                };
                return new TerminalStorage(grid.getStorageService().getInventory(), new MagnetHost(filterHost));
            }
            return null;
        }

        private static List<ItemMenuHostLocator> candidateLocators(ServerPlayer player) {
            List<ItemMenuHostLocator> locators = new ArrayList<>();
            var curios = player.getCapability(CuriosIntegration.ITEM_HANDLER);
            if (curios != null) {
                for (int index = 0; index < curios.getSlots(); index++) {
                    if (WUTHandler.hasTerminal(curios.getStackInSlot(index), WTDefinitions.CRAFTING)) {
                        locators.add(MenuLocators.forCurioSlot(index));
                    }
                }
            }
            for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
                if (WUTHandler.hasTerminal(player.getInventory().getItem(index), WTDefinitions.CRAFTING)) {
                    locators.add(MenuLocators.forInventorySlot(index));
                }
            }
            return locators;
        }

        private record TerminalStorage(MEStorage inventory, MagnetHost magnet) {
        }
    }
}
