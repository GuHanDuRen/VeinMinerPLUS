package com.extrarawstyle.veinminerplus;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.oredict.OreDictionary;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = VeinMinerPlus.MODID, value = Side.CLIENT)
public final class VeinMinerPlusClient {
    static final KeyBinding CHAIN_KEY = new KeyBinding("key.veinminerplus.chain", Keyboard.KEY_GRAVE,
            "key.categories.veinminerplus");
    static final KeyBinding COPY_BLOCK_ID_KEY = new KeyBinding("key.veinminerplus.copy_block_id",
            Keyboard.KEY_NONE, "key.categories.veinminerplus");
    static final KeyBinding COPY_ORE_TAGS_KEY = new KeyBinding("key.veinminerplus.copy_ore_tags",
            Keyboard.KEY_NONE, "key.categories.veinminerplus");
    static final KeyBinding WHITELIST_SELECT_KEY = new KeyBinding("key.veinminerplus.whitelist_select",
            Keyboard.KEY_RETURN, "key.categories.veinminerplus");
    static final KeyBinding XRAY_KEY = new KeyBinding("key.veinminerplus.xray", Keyboard.KEY_APOSTROPHE,
            "key.categories.veinminerplus");
    static ChainMode clientMode = ChainMode.NORMAL;
    private static int clientNormalLimit = Config.DEFAULT_MAX_NORMAL_BLOCKS;
    private static World estimatedWorld;
    private static BlockPos estimatedOrigin;
    private static Block estimatedBlock;
    private static int estimatedCount;
    private static int estimatedInteractionCount;
    private static boolean estimatedContainer;
    private static EnumFacing.Axis estimatedFaceAxis;
    private static final int FINAL_PROGRESS_DISPLAY_TICKS = 20;
    private static boolean keyStateSent;
    private static boolean registered;
    private static VeinMinerConfigScreen whitelistSelectionScreen;
    private static VeinMinerConfigScreen pendingWhitelistSelectionReturn;
    private static boolean whitelistSelectionActive;
    private static int chainProgressCount;
    private static boolean chainProgressVisible;
    private static int chainProgressHideTicks;
    private static long lastProgressSequence = -1L;

    private VeinMinerPlusClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft.player == null || minecraft.world == null) {
                clearChainProgress();
                lastProgressSequence = -1;
                keyStateSent = false;
                whitelistSelectionScreen = null;
                pendingWhitelistSelectionReturn = null;
                whitelistSelectionActive = false;
                estimatedWorld = null;
                estimatedOrigin = null;
                estimatedBlock = null;
                estimatedCount = 0;
                estimatedInteractionCount = 0;
                estimatedContainer = false;
                estimatedFaceAxis = null;
                return;
            }
            if (chainProgressVisible && chainProgressHideTicks > 0) {
                chainProgressHideTicks--;
                if (chainProgressHideTicks == 0) {
                    clearChainProgress();
                }
            }
            restorePendingWhitelistSelection(minecraft);
            syncKeyState();
            XrayClientState.tick(minecraft);
        }
    }

    static void registerKeyBinding() {
        if (!registered) {
            ClientRegistry.registerKeyBinding(CHAIN_KEY);
            ClientRegistry.registerKeyBinding(COPY_BLOCK_ID_KEY);
            ClientRegistry.registerKeyBinding(COPY_ORE_TAGS_KEY);
            ClientRegistry.registerKeyBinding(WHITELIST_SELECT_KEY);
            ClientRegistry.registerKeyBinding(XRAY_KEY);
            registered = true;
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.KeyInputEvent event) {
        if (whitelistSelectionActive) {
            handleWhitelistSelectionInput();
            syncKeyState();
            return;
        }
        while (WHITELIST_SELECT_KEY.isPressed()) {
            // Drain presses made outside selection mode so they cannot confirm
            // a later selection unexpectedly.
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (XRAY_KEY.isPressed() && minecraft.player != null && minecraft.world != null
                && minecraft.currentScreen == null) {
            clientMode = ChainMode.XRAY;
            NetworkHandler.sendModeChange(clientMode);
            minecraft.displayGuiScreen(new XraySelectionScreen());
            return;
        }
        if (minecraft.player != null && minecraft.world != null && minecraft.currentScreen == null) {
            if (COPY_BLOCK_ID_KEY.isPressed()) {
                copyBlockId();
            }
            if (COPY_ORE_TAGS_KEY.isPressed()) {
                copyOreTags();
            }
        }
        syncKeyState();
    }

    static void beginWhitelistSelection(VeinMinerConfigScreen screen) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (screen == null || minecraft.player == null || minecraft.world == null
                || minecraft.currentScreen != screen) {
            return;
        }
        screen.captureDraftValues();
        while (WHITELIST_SELECT_KEY.isPressed()) {
            // Clear any press queued before entering selection mode.
        }
        whitelistSelectionScreen = screen;
        whitelistSelectionActive = true;
        minecraft.displayGuiScreen(null);
        showClipboardMessage(minecraft, "message.veinminerplus.whitelist_select_prompt");
    }

    private static void handleWhitelistSelectionInput() {
        int keyCode = Keyboard.getEventKey();
        if (Keyboard.getEventKeyState() && keyCode == Keyboard.KEY_ESCAPE) {
            finishWhitelistSelection(false);
            return;
        }
        if (WHITELIST_SELECT_KEY.isPressed()
                || (Keyboard.getEventKeyState() && keyCode == Keyboard.KEY_NUMPADENTER)) {
            confirmWhitelistSelection();
        }
    }

    private static void confirmWhitelistSelection() {
        Minecraft minecraft = Minecraft.getMinecraft();
        VeinMinerConfigScreen screen = whitelistSelectionScreen;
        if (screen == null) {
            whitelistSelectionActive = false;
            return;
        }

        IBlockState state = getAimedBlockState(minecraft);
        if (state == null) {
            showClipboardMessage(minecraft, "message.veinminerplus.whitelist_select_no_block");
            return;
        }
        screen.addAimedWhitelistEntry(state);
        finishWhitelistSelection(true);
    }

    private static void finishWhitelistSelection(boolean confirmed) {
        VeinMinerConfigScreen screen = whitelistSelectionScreen;
        whitelistSelectionScreen = null;
        whitelistSelectionActive = false;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (confirmed) {
            showClipboardMessage(minecraft, "message.veinminerplus.whitelist_entry_added");
        }
        if (minecraft != null && screen != null) {
            pendingWhitelistSelectionReturn = screen;
        }
    }

    private static void restorePendingWhitelistSelection(Minecraft minecraft) {
        if (pendingWhitelistSelectionReturn == null || minecraft == null
                || minecraft.player == null || minecraft.world == null) {
            return;
        }

        VeinMinerConfigScreen screen = pendingWhitelistSelectionReturn;
        pendingWhitelistSelectionReturn = null;
        minecraft.displayGuiScreen(screen);
    }

    private static void copyBlockId() {
        Minecraft minecraft = Minecraft.getMinecraft();
        IBlockState state = getAimedBlockState(minecraft);
        if (state == null) {
            showClipboardMessage(minecraft, "message.veinminerplus.copy_no_block");
            return;
        }

        ResourceLocation id = state.getBlock().getRegistryName();
        if (id == null) {
            showClipboardMessage(minecraft, "message.veinminerplus.copy_no_block");
            return;
        }

        String value = id.toString();
        GuiScreen.setClipboardString(value);
        showClipboardMessage(minecraft, "message.veinminerplus.block_id_copied", value);
    }

    private static void copyOreTags() {
        Minecraft minecraft = Minecraft.getMinecraft();
        IBlockState state = getAimedBlockState(minecraft);
        if (state == null) {
            showClipboardMessage(minecraft, "message.veinminerplus.copy_no_block");
            return;
        }

        ItemStack stack = new ItemStack(state.getBlock(), 1, state.getBlock().getMetaFromState(state));
        LinkedHashSet<String> entries = new LinkedHashSet<String>();
        for (int oreId : OreDictionary.getOreIDs(stack)) {
            String oreName = OreDictionary.getOreName(oreId);
            if (oreName != null && !oreName.isEmpty()) {
                entries.add("ore:" + oreName);
            }
        }
        if (entries.isEmpty()) {
            showClipboardMessage(minecraft, "message.veinminerplus.no_ore_tags");
            return;
        }

        List<String> sorted = new ArrayList<String>(entries);
        Collections.sort(sorted);
        String value = String.join("\n", sorted);
        GuiScreen.setClipboardString(value);
        showClipboardMessage(minecraft, "message.veinminerplus.ore_tags_copied", sorted.size());
    }

    private static IBlockState getAimedBlockState(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.world == null
                || minecraft.currentScreen != null || minecraft.objectMouseOver == null
                || minecraft.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK) {
            return null;
        }
        return minecraft.world.getBlockState(minecraft.objectMouseOver.getBlockPos());
    }

    private static void showClipboardMessage(Minecraft minecraft, String key, Object... args) {
        if (minecraft.player != null) {
            minecraft.player.sendStatusMessage(new TextComponentTranslation(key, args), true);
        }
    }

    static void openConfigScreen(NetworkHandler.ConfigSnapshotMessage config) {
        whitelistSelectionScreen = null;
        pendingWhitelistSelectionReturn = null;
        whitelistSelectionActive = false;
        clientMode = ChainMode.fromOrdinal(config.mode);
        clientNormalLimit = config.maxNormalBlocks;
        Minecraft.getMinecraft().displayGuiScreen(new VeinMinerConfigScreen(config));
    }

    static void setClientMode(ChainMode mode) {
        clientMode = mode;
    }

    static boolean isXrayMode() {
        return clientMode == ChainMode.XRAY;
    }

    static void updateChainProgress(long sequence, boolean active, int count) {
        // Netty preserves channel order, but scheduled client tasks can still
        // be interleaved with other work. Never let an older packet overwrite
        // the newest visible total.
        if (sequence <= lastProgressSequence) {
            return;
        }
        lastProgressSequence = sequence;
        chainProgressCount = Math.max(0, count);
        if (active) {
            chainProgressVisible = chainProgressCount > 0;
            chainProgressHideTicks = 0;
        } else if (chainProgressCount > 0) {
            // Keep the final total readable for a short, fixed period. There is
            // no alpha fade or refresh race as with the vanilla action bar.
            chainProgressVisible = true;
            chainProgressHideTicks = FINAL_PROGRESS_DISPLAY_TICKS;
        } else {
            clearChainProgress();
        }
    }

    static void clearChainProgress() {
        chainProgressCount = 0;
        chainProgressVisible = false;
        chainProgressHideTicks = 0;
    }

    static void renderChainProgress(ScaledResolution resolution) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null || minecraft.world == null || minecraft.gameSettings.hideGUI
                || !chainProgressVisible) {
            return;
        }
        String text = net.minecraft.client.resources.I18n.format("message.veinminerplus.progress",
                Integer.valueOf(chainProgressCount));
        int width = minecraft.fontRenderer.getStringWidth(text);
        // Reserve room for the configured maximum count so the box and text do
        // not jump horizontally when the counter grows from one to two digits.
        String maxText = net.minecraft.client.resources.I18n.format("message.veinminerplus.progress",
                Integer.valueOf(99999));
        int boxWidth = Math.max(width, minecraft.fontRenderer.getStringWidth(maxText));
        int x = (resolution.getScaledWidth() - boxWidth) / 2;
        int textX = x + (boxWidth - width) / 2;
        int y = Math.max(2, resolution.getScaledHeight() - 59);

        // Render after the complete vanilla HUD. Do not use pushAttrib/popAttrib
        // here: those raw OpenGL calls do not update GlStateManager's cached
        // state, so the next overlay can be rendered with stale blend/color
        // state. Keep the state changes on GlStateManager and restore the HUD's
        // normal 2D state explicitly in a finally block.
        beginOverlayRender();
        try {
            Gui.drawRect(x - 4, y - 2, x + boxWidth + 4, y + 9, 0x90000000);
            restoreOverlayBlend();
            minecraft.fontRenderer.drawStringWithShadow(text, textX, y, 0xFFFFFF);
        } finally {
            endOverlayRender();
        }
    }

    static void syncKeyState() {
        Minecraft minecraft = Minecraft.getMinecraft();
        boolean held = minecraft.player != null && minecraft.world != null && minecraft.currentScreen == null
                && CHAIN_KEY.isKeyDown();
        if (held != keyStateSent) {
            NetworkHandler.sendKeyState(held);
            keyStateSent = held;
        }
    }

    static void releaseKeyState() {
        if (keyStateSent) {
            NetworkHandler.sendKeyState(false);
            keyStateSent = false;
        }
    }

    static boolean isModeSelectorOpen() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft.player != null && minecraft.world != null && minecraft.currentScreen == null
                && CHAIN_KEY.isKeyDown() && GuiScreen.isShiftKeyDown();
    }

    static void renderModeMenu() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null || minecraft.world == null || minecraft.currentScreen != null
                || !CHAIN_KEY.isKeyDown()) {
            return;
        }
        updateEstimatedChainCount(minecraft);
        String text;
        if (GuiScreen.isShiftKeyDown()) {
            text = "";
            for (ChainMode mode : ChainMode.values()) {
                String line = (mode == clientMode ? TextFormatting.YELLOW : TextFormatting.WHITE)
                        + modeText(mode);
                text += line + "\n";
            }
        } else {
            text = modeText(clientMode);
        }
        String[] lines = text.split("\\n");
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, minecraft.fontRenderer.getStringWidth(line));
        }
        beginOverlayRender();
        try {
            int right = width + 10;
            int bottom = 8 + lines.length * 10;
            Gui.drawRect(3, 3, right, bottom, 0xD02D3834);
            Gui.drawRect(3, 3, right, 4, 0xFF8FAFA2);
            Gui.drawRect(3, bottom - 1, right, bottom, 0xFF8FAFA2);
            Gui.drawRect(3, 3, 4, bottom, 0xFF8FAFA2);
            Gui.drawRect(right - 1, 3, right, bottom, 0xFF8FAFA2);
            Gui.drawRect(4, 4, 6, bottom - 1, 0xFFE1BA6B);
            if (GuiScreen.isShiftKeyDown()) {
                int selected = clientMode.ordinal();
                Gui.drawRect(6, 5 + selected * 10, right - 2, 15 + selected * 10, 0x80536C5E);
            }
            restoreOverlayBlend();
            for (int i = 0; i < lines.length; i++) {
                minecraft.fontRenderer.drawStringWithShadow(lines[i], 6, 5 + i * 10, 0xFFFFFFFF);
            }
        } finally {
            endOverlayRender();
        }
    }

    private static String modeText(ChainMode mode) {
        String name = net.minecraft.client.resources.I18n.format(mode.translationKey());
        if (mode == ChainMode.NORMAL || mode == ChainMode.USE_BLOCK) {
            Object estimate = mode == ChainMode.NORMAL ? Integer.valueOf(estimatedCount) : interactionEstimate();
            return net.minecraft.client.resources.I18n.format("hud.veinminerplus.mode", name, estimate);
        }
        return name;
    }

    private static Object interactionEstimate() {
        if (estimatedContainer || estimatedOrigin == null || estimatedBlock == Blocks.AIR) {
            return Integer.valueOf(estimatedInteractionCount);
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft.player != null
                && (!minecraft.player.getHeldItemMainhand().isEmpty()
                        || !minecraft.player.getHeldItemOffhand().isEmpty()) ? "?" : Integer.valueOf(0);
    }

    private static void updateEstimatedChainCount(Minecraft minecraft) {
        if (minecraft.world == null || minecraft.objectMouseOver == null
                || minecraft.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK) {
            estimatedWorld = null;
            estimatedOrigin = null;
            estimatedBlock = null;
            estimatedCount = 0;
            estimatedInteractionCount = 0;
            estimatedContainer = false;
            estimatedFaceAxis = null;
            return;
        }

        BlockPos origin = minecraft.objectMouseOver.getBlockPos();
        Block target = minecraft.world.getBlockState(origin).getBlock();
        EnumFacing face = minecraft.objectMouseOver.sideHit == null
                ? EnumFacing.UP : minecraft.objectMouseOver.sideHit;
        EnumFacing.Axis faceAxis = face.getAxis();
        if (minecraft.world == estimatedWorld && origin.equals(estimatedOrigin)
                && target == estimatedBlock && faceAxis == estimatedFaceAxis) {
            return;
        }

        estimatedWorld = minecraft.world;
        estimatedOrigin = origin;
        estimatedBlock = target;
        estimatedFaceAxis = faceAxis;
        estimatedCount = 0;
        estimatedInteractionCount = 0;
        estimatedContainer = false;
        if (target == Blocks.AIR) {
            return;
        }

        int limit = Math.min(Math.max(1, clientNormalLimit), 4096);
        estimatedCount = countConnected(minecraft.world, estimatedOrigin, target, limit);
        TileEntity tile = minecraft.world.getTileEntity(estimatedOrigin);
        estimatedContainer = tile instanceof IInventory || target instanceof BlockContainer
                || tile != null && tile.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (!estimatedContainer) {
            return;
        }
        estimatedInteractionCount = countConnected(minecraft.world, estimatedOrigin, target, limit);
    }

    private static int countConnected(World world, BlockPos origin, Block target, int limit) {
        ArrayDeque<BlockPos> frontier = new ArrayDeque<BlockPos>();
        Set<BlockPos> seen = new HashSet<BlockPos>();
        frontier.add(origin);
        seen.add(origin);
        int count = 1;
        while (!frontier.isEmpty() && count < limit) {
            BlockPos center = frontier.removeFirst();
            for (int x = -1; x <= 1 && count < limit; x++) {
                for (int y = -1; y <= 1 && count < limit; y++) {
                    for (int z = -1; z <= 1 && count < limit; z++) {
                        if (x == 0 && y == 0 && z == 0) {
                            continue;
                        }
                        BlockPos candidate = center.add(x, y, z);
                        if (seen.add(candidate) && world.isBlockLoaded(candidate)
                                && world.getBlockState(candidate).getBlock() == target) {
                            frontier.addLast(candidate);
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    private static void beginOverlayRender() {
        GlStateManager.pushMatrix();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GlStateManager.enableAlpha();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        resetOverlayColor();
    }

    private static void endOverlayRender() {
        // These are the states Forge's HUD leaves for the next overlay pass.
        // Calling them through GlStateManager keeps its cache synchronized with
        // the actual OpenGL state after Gui.drawRect/font rendering.
        resetOverlayColor();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableLighting();
        GlStateManager.enableAlpha();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.popMatrix();
    }

    private static void resetOverlayColor() {
        // Other HUD mods may set the color through raw OpenGL, bypassing
        // GlStateManager's cache. Invalidate the cached value before forcing
        // white so the reset reaches the driver as well.
        GlStateManager.resetColor();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void restoreOverlayBlend() {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
    }
}
