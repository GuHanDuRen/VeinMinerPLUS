package com.extrarawstyle.veinminerplus;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

final class AeStorageCompat {
    private AeStorageCompat() {
    }

    static boolean isMagnetSupported() {
        return Loader.isModLoaded("appliedenergistics2") && Ae2.MAGNET != null;
    }

    static void insertDrops(EntityPlayerMP player, List<ItemStack> drops) {
        if (drops.isEmpty() || !Loader.isModLoaded("appliedenergistics2")) {
            return;
        }
        List<Consumer<ItemStack>> targets = Ae2.findTargets(player);
        for (ItemStack stack : drops) {
            for (Consumer<ItemStack> target : targets) {
                if (stack.isEmpty()) {
                    break;
                }
                target.accept(stack);
            }
        }
    }

    // Keep AE classes behind the mod check so AE remains optional.
    private static final class Ae2 {
        private static final appeng.api.definitions.IItemDefinition MAGNET = findMagnetDefinition();

        static List<Consumer<ItemStack>> findTargets(EntityPlayerMP player) {
            List<Consumer<ItemStack>> targets = new ArrayList<Consumer<ItemStack>>();
            if (MAGNET == null) {
                return targets;
            }
            for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
                addTarget(targets, player, player.inventory.getStackInSlot(slot), slot, false);
            }
            if (Loader.isModLoaded("baubles")) {
                IItemHandler baubles = getBaubles(player);
                if (baubles != null) {
                    for (int slot = 0; slot < baubles.getSlots(); slot++) {
                        addTarget(targets, player, baubles.getStackInSlot(slot), slot, true);
                    }
                }
            }
            return targets;
        }

        private static appeng.api.definitions.IItemDefinition findMagnetDefinition() {
            try {
                Method method = appeng.api.definitions.IMaterials.class.getMethod("cardMagnet");
                return (appeng.api.definitions.IItemDefinition) method.invoke(
                        appeng.api.AEApi.instance().definitions().materials());
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        private static IItemHandler getBaubles(EntityPlayer player) {
            try {
                Method method = Class.forName("baubles.api.BaublesApi")
                        .getMethod("getBaublesHandler", EntityPlayer.class);
                return (IItemHandler) method.invoke(null, player);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        private static void addTarget(List<Consumer<ItemStack>> targets, EntityPlayerMP player,
                ItemStack terminal, int slot, boolean bauble) {
            ItemStack magnet = getMagnet(terminal);
            if (magnet.isEmpty()) {
                return;
            }
            appeng.api.features.IWirelessTermHandler handler = appeng.api.AEApi.instance()
                    .registries().wireless().getWirelessTerminalHandler(terminal);
            if (handler == null) {
                return;
            }
            appeng.helpers.WirelessTerminalGuiObject host = new appeng.helpers.WirelessTerminalGuiObject(
                    handler, terminal, player, player.world, slot, bauble ? 1 : 0, Integer.MIN_VALUE);
            appeng.api.networking.IGridNode node = host.getActionableNode();
            if (node == null || !node.isActive()) {
                return;
            }
            appeng.api.storage.ICellWorkbenchItem card = (appeng.api.storage.ICellWorkbenchItem) magnet.getItem();
            IItemHandler filter = card.getConfigInventory(magnet);
            appeng.parts.automation.UpgradeInventory upgrades =
                    (appeng.parts.automation.UpgradeInventory) card.getUpgradesInventory(magnet);
            boolean fuzzy = upgrades.getInstalledUpgrades(appeng.api.config.Upgrades.FUZZY) > 0;
            boolean inverted = upgrades.getInstalledUpgrades(appeng.api.config.Upgrades.INVERTER) > 0;
            appeng.api.config.FuzzyMode fuzzyMode = card.getFuzzyMode(magnet);
            appeng.me.helpers.PlayerSource source = new appeng.me.helpers.PlayerSource(player, host);
            // Some UEL builds consume power during the host's SIMULATE call.
            appeng.api.networking.energy.IEnergySource energy = (amount, mode, multiplier) -> {
                boolean powered = mode == appeng.api.config.Actionable.SIMULATE
                        ? handler.hasPower(player, amount, terminal)
                        : handler.usePower(player, amount, terminal);
                return powered ? amount : 0.0D;
            };
            targets.add(stack -> {
                if (!matchesFilter(filter, fuzzy, inverted, fuzzyMode, stack)) {
                    return;
                }
                appeng.api.storage.data.IAEItemStack remainder = appeng.util.Platform.poweredInsert(
                        energy, host, appeng.util.item.AEItemStack.fromItemStack(stack), source);
                stack.setCount(remainder == null ? 0 : (int) remainder.getStackSize());
            });
        }

        private static ItemStack getMagnet(ItemStack terminal) {
            NBTTagCompound tag = terminal.getTagCompound();
            if (terminal.isEmpty() || tag == null) {
                return ItemStack.EMPTY;
            }
            ItemStackHandler upgrades = new ItemStackHandler(0);
            upgrades.deserializeNBT(tag.getCompoundTag("upgrades"));
            for (int slot = 0; slot < upgrades.getSlots(); slot++) {
                ItemStack upgrade = upgrades.getStackInSlot(slot);
                NBTTagCompound cardTag = upgrade.getTagCompound();
                if (MAGNET.isSameAs(upgrade)
                        && (cardTag == null || !cardTag.hasKey("enabled") || cardTag.getBoolean("enabled"))) {
                    return upgrade;
                }
            }
            return ItemStack.EMPTY;
        }

        private static boolean matchesFilter(IItemHandler filter, boolean fuzzy, boolean inverted,
                appeng.api.config.FuzzyMode fuzzyMode, ItemStack stack) {
            boolean empty = true;
            boolean matched = false;
            for (int slot = 0; slot < filter.getSlots(); slot++) {
                ItemStack configured = filter.getStackInSlot(slot);
                if (configured.isEmpty()) {
                    continue;
                }
                empty = false;
                matched = fuzzy
                        ? appeng.util.Platform.itemComparisons().isFuzzyEqualItem(configured, stack, fuzzyMode)
                        : appeng.util.Platform.itemComparisons().isSameItem(configured, stack);
                if (matched) {
                    break;
                }
            }
            return empty || matched != inverted;
        }
    }
}
