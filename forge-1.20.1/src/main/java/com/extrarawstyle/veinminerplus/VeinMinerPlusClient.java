package com.extrarawstyle.veinminerplus;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VeinMinerPlus.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VeinMinerPlusClient {
    static final KeyMapping CHAIN_KEY = new KeyMapping(
            "key.veinminerplus.chain",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_GRAVE_ACCENT,
            "key.categories.veinminerplus");
    static final KeyMapping COPY_BLOCK_ID_KEY = new KeyMapping(
            "key.veinminerplus.copy_block_id",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories.veinminerplus");
    static final KeyMapping COPY_ORE_TAGS_KEY = new KeyMapping(
            "key.veinminerplus.copy_ore_tags",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories.veinminerplus");
    static final KeyMapping WHITELIST_SELECT_KEY = new KeyMapping(
            "key.veinminerplus.whitelist_select",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_ENTER,
            "key.categories.veinminerplus");
    static final KeyMapping XRAY_KEY = new KeyMapping(
            "key.veinminerplus.xray",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_APOSTROPHE,
            "key.categories.veinminerplus");

    static ChainMode clientMode = ChainMode.NORMAL;
    private static int clientNormalLimit = Config.DEFAULT_MAX_NORMAL_BLOCKS;
    private static Level estimatedLevel;
    private static BlockPos estimatedOrigin;
    private static Block estimatedBlock;
    private static int estimatedCount;
    private static int estimatedInteractionCount;
    private static boolean estimatedContainer;
    private static Direction.Axis estimatedFaceAxis;
    private static boolean keyStateSent;
    private static VeinMinerConfigScreen whitelistSelectionScreen;
    private static VeinMinerConfigScreen pendingWhitelistSelectionReturn;
    private static Component blastRadiusReductionNotice;
    private static int blastRadiusReductionNoticeTicks;

    private VeinMinerPlusClient() {
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(CHAIN_KEY);
        event.register(COPY_BLOCK_ID_KEY);
        event.register(COPY_ORE_TAGS_KEY);
        event.register(WHITELIST_SELECT_KEY);
        event.register(XRAY_KEY);
    }

    static void copyBlockId() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isInGame(minecraft)) {
            return;
        }
        BlockState state = getAimedBlockState(minecraft);
        if (state == null) {
            showClipboardMessage(minecraft, "message.veinminerplus.copy_no_block");
            return;
        }

        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) {
            showClipboardMessage(minecraft, "message.veinminerplus.copy_no_block");
            return;
        }

        String value = id.toString();
        minecraft.keyboardHandler.setClipboard(value);
        showClipboardMessage(minecraft, "message.veinminerplus.block_id_copied", value);
    }

    static void copyOreTags() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isInGame(minecraft)) {
            return;
        }
        BlockState state = getAimedBlockState(minecraft);
        if (state == null) {
            showClipboardMessage(minecraft, "message.veinminerplus.copy_no_block");
            return;
        }

        String value = state.getTags()
                .filter(tag -> tag.location().getPath().contains("ore"))
                .map(tag -> "#" + tag.location())
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.joining("\n"));
        if (value.isEmpty()) {
            showClipboardMessage(minecraft, "message.veinminerplus.no_ore_tags");
            return;
        }

        minecraft.keyboardHandler.setClipboard(value);
        int count = value.split("\\n").length;
        showClipboardMessage(minecraft, "message.veinminerplus.ore_tags_copied", count);
    }

    private static boolean isInGame(Minecraft minecraft) {
        return minecraft.player != null && minecraft.level != null && minecraft.screen == null;
    }

    private static BlockState getAimedBlockState(Minecraft minecraft) {
        if (!isInGame(minecraft) || !(minecraft.hitResult instanceof BlockHitResult hit)) {
            return null;
        }
        return minecraft.level.getBlockState(hit.getBlockPos());
    }

    private static void showClipboardMessage(Minecraft minecraft, String key, Object... args) {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(key, args), true);
        }
    }

    static void openConfigScreen(NetworkHandler.ConfigSnapshotPayload config) {
        whitelistSelectionScreen = null;
        pendingWhitelistSelectionReturn = null;
        clientMode = ChainMode.fromOrdinal(config.mode());
        clientNormalLimit = config.maxNormalBlocks();
        Minecraft.getInstance().setScreen(new VeinMinerConfigScreen(config));
    }

    static void showBlastRadiusReduced(double tps, int oldDistance, int newDistance) {
        blastRadiusReductionNotice = Component.translatable("message.veinminerplus.blast_radius_reduced_notice",
                String.format(Locale.ROOT, "%.1f", tps), oldDistance, newDistance);
        blastRadiusReductionNoticeTicks = 200;
    }

    static void renderBlastRadiusReductionNotice(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (blastRadiusReductionNotice == null || minecraft.options.hideGui
                || minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }

        int width = minecraft.getWindow().getGuiScaledWidth();
        int bottomY = minecraft.getWindow().getGuiScaledHeight() - 82;
        var lines = minecraft.font.split(blastRadiusReductionNotice, Math.max(1, Math.min(520, width - 24)));
        int y = bottomY - (lines.size() - 1) * minecraft.font.lineHeight;
        int textWidth = 0;
        for (var line : lines) {
            textWidth = Math.max(textWidth, minecraft.font.width(line));
        }
        int left = (width - textWidth) / 2;
        graphics.fill(left - 4, y - 2, left + textWidth + 4, bottomY + minecraft.font.lineHeight, 0xC0101820);
        for (var line : lines) {
            int lineWidth = minecraft.font.width(line);
            int x = (width - lineWidth) / 2;
            graphics.drawString(minecraft.font, line, x, y, 0xFFFFC66D, false);
            y += minecraft.font.lineHeight;
        }
    }

    static void tickBlastRadiusReductionNotice() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || blastRadiusReductionNoticeTicks <= 1) {
            blastRadiusReductionNotice = null;
            blastRadiusReductionNoticeTicks = 0;
        } else {
            blastRadiusReductionNoticeTicks--;
        }
    }

    static void beginWhitelistSelection(VeinMinerConfigScreen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != screen) {
            return;
        }

        screen.captureDraftValues();
        whitelistSelectionScreen = screen;
        minecraft.setScreen(null);
        showClipboardMessage(minecraft, "message.veinminerplus.whitelist_select_prompt");
    }

    static boolean handleWhitelistSelectionKey(int key, int scanCode, int action) {
        if (whitelistSelectionScreen == null) {
            return false;
        }
        if (action != GLFW.GLFW_PRESS) {
            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            queueWhitelistSelectionReturn("message.veinminerplus.whitelist_select_cancelled");
        } else if (WHITELIST_SELECT_KEY.matches(key, scanCode)
                || (WHITELIST_SELECT_KEY.getKey().getValue() == GLFW.GLFW_KEY_ENTER
                        && key == GLFW.GLFW_KEY_KP_ENTER)) {
            confirmWhitelistSelection();
        }
        return true;
    }

    private static void confirmWhitelistSelection() {
        Minecraft minecraft = Minecraft.getInstance();
        VeinMinerConfigScreen screen = whitelistSelectionScreen;
        if (screen == null) {
            return;
        }

        BlockState state = getAimedBlockState(minecraft);
        if (state == null || state.isAir()) {
            showClipboardMessage(minecraft, "message.veinminerplus.whitelist_select_no_block");
            return;
        }

        screen.addAimedWhitelistEntry(state);
        queueWhitelistSelectionReturn("message.veinminerplus.whitelist_entry_added");
    }

    private static void queueWhitelistSelectionReturn(String messageKey) {
        Minecraft minecraft = Minecraft.getInstance();
        VeinMinerConfigScreen screen = whitelistSelectionScreen;
        if (screen == null) {
            return;
        }

        whitelistSelectionScreen = null;
        pendingWhitelistSelectionReturn = screen;
        showClipboardMessage(minecraft, messageKey);
    }

    static void restorePendingWhitelistSelection() {
        if (pendingWhitelistSelectionReturn == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            pendingWhitelistSelectionReturn = null;
            return;
        }

        VeinMinerConfigScreen screen = pendingWhitelistSelectionReturn;
        pendingWhitelistSelectionReturn = null;
        minecraft.setScreen(screen);
    }

    static void setClientMode(ChainMode mode) {
        clientMode = mode;
    }

    static boolean isXrayMode() {
        return clientMode == ChainMode.XRAY;
    }

    static void syncKeyState() {
        boolean held = isChainKeyActive(Minecraft.getInstance());
        if (held == keyStateSent) {
            return;
        }

        NetworkHandler.sendKeyState(held);
        keyStateSent = held;
    }

    static void releaseKeyState() {
        if (!keyStateSent) {
            return;
        }

        NetworkHandler.sendKeyState(false);
        keyStateSent = false;
    }

    static boolean isModeSelectorOpen() {
        return isChainKeyActive(Minecraft.getInstance()) && Screen.hasShiftDown();
    }

    static void renderModeMenu(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isChainKeyActive(minecraft)) {
            return;
        }

        updateEstimatedChainCount(minecraft);
        if (Screen.hasShiftDown()) {
            renderAllModes(graphics, minecraft);
        } else {
            renderCurrentMode(graphics, minecraft);
        }
    }

    private static boolean isChainKeyActive(Minecraft minecraft) {
        return minecraft.player != null && minecraft.level != null && minecraft.screen == null && CHAIN_KEY.isDown();
    }

    private static void renderCurrentMode(GuiGraphics graphics, Minecraft minecraft) {
        Component text = modeText(clientMode);
        int width = minecraft.font.width(text);
        renderModeFrame(graphics, 12 + width, 18);
        graphics.drawString(minecraft.font, text, 8, 8, 0xFFFFFFFF);
    }

    private static void renderAllModes(GuiGraphics graphics, Minecraft minecraft) {
        ChainMode[] modes = ChainMode.values();
        int width = 0;
        for (ChainMode mode : modes) {
            width = Math.max(width, minecraft.font.width(modeText(mode)));
        }

        int lineHeight = minecraft.font.lineHeight;
        int right = 12 + width;
        renderModeFrame(graphics, right, 12 + modes.length * lineHeight);
        for (int index = 0; index < modes.length; index++) {
            ChainMode mode = modes[index];
            if (mode == clientMode) {
                graphics.fill(7, 7 + index * lineHeight, right - 2,
                        7 + (index + 1) * lineHeight, 0x80536C5E);
            }
            int color = mode == clientMode ? 0xFFFFD475 : 0xFFFFFFFF;
            graphics.drawString(minecraft.font, modeText(mode), 8, 8 + index * lineHeight, color);
        }
    }

    private static void renderModeFrame(GuiGraphics graphics, int right, int bottom) {
        graphics.fill(4, 4, right, bottom, 0xD02D3834);
        graphics.renderOutline(4, 4, right - 4, bottom - 4, 0xFF8FAFA2);
        graphics.fill(5, 5, 7, bottom - 1, 0xFFE1BA6B);
    }

    private static Component modeText(ChainMode mode) {
        Component name = Component.translatable(mode.translationKey());
        return switch (mode) {
            case NORMAL -> Component.translatable("hud.veinminerplus.mode", name, estimatedCount);
            case USE_BLOCK -> Component.translatable("hud.veinminerplus.mode", name, interactionEstimate());
            case XRAY -> XrayClientState.hasSelection() ? name
                    : Component.translatable("hud.veinminerplus.xray_empty", name);
            default -> name;
        };
    }

    private static Object interactionEstimate() {
        if (estimatedContainer || estimatedOrigin == null || estimatedBlock.defaultBlockState().isAir()) {
            return estimatedInteractionCount;
        }
        var player = Minecraft.getInstance().player;
        return player != null && (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty())
                ? "?" : 0;
    }

    private static void updateEstimatedChainCount(Minecraft minecraft) {
        if (minecraft.level == null || !(minecraft.hitResult instanceof BlockHitResult hit)) {
            estimatedLevel = null;
            estimatedOrigin = null;
            estimatedBlock = null;
            estimatedCount = 0;
            estimatedInteractionCount = 0;
            estimatedContainer = false;
            estimatedFaceAxis = null;
            return;
        }

        BlockPos origin = hit.getBlockPos();
        Block target = minecraft.level.getBlockState(origin).getBlock();
        Direction.Axis faceAxis = hit.getDirection().getAxis();
        if (minecraft.level == estimatedLevel && origin.equals(estimatedOrigin)
                && target == estimatedBlock && faceAxis == estimatedFaceAxis) {
            return;
        }

        estimatedLevel = minecraft.level;
        estimatedOrigin = origin.immutable();
        estimatedBlock = target;
        estimatedFaceAxis = faceAxis;
        estimatedCount = 0;
        estimatedInteractionCount = 0;
        estimatedContainer = false;
        if (target.defaultBlockState().isAir()) {
            return;
        }

        int limit = Math.min(Math.max(1, clientNormalLimit), 4096);
        estimatedCount = countConnected(minecraft.level, estimatedOrigin, target, limit);
        BlockEntity blockEntity = minecraft.level.getBlockEntity(estimatedOrigin);
        estimatedContainer = blockEntity instanceof Container
                || minecraft.level.getBlockState(estimatedOrigin).getMenuProvider(minecraft.level, estimatedOrigin) != null
                || blockEntity != null && blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
        if (!estimatedContainer) {
            return;
        }
        estimatedInteractionCount = countConnected(minecraft.level, estimatedOrigin, target, limit);
    }

    private static int countConnected(Level level, BlockPos origin, Block target, int limit) {
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
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
                        BlockPos candidate = center.offset(x, y, z);
                        if (seen.add(candidate) && level.hasChunkAt(candidate)
                                && level.getBlockState(candidate).is(target)) {
                            frontier.addLast(candidate);
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }
}
