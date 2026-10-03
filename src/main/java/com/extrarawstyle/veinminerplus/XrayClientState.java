package com.extrarawstyle.veinminerplus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import org.joml.Vector3f;

final class XrayClientState {
    private static final int SLOT_COUNT = 36;
    private static final int SEARCH_RADIUS = 48;
    private static final int CHUNK_RADIUS = (SEARCH_RADIUS + 15) >> 4;
    private static final int CHUNKS_PER_TICK = 2;
    private static final int MAX_RENDERED_BLOCKS = 2048;

    private static final List<BlockPos> CHUNK_OFFSETS = createChunkOffsets();
    private static final List<ItemStack> SELECTED_STACKS = new ArrayList<>();
    private static final Set<String> SELECTED_FAMILIES = new HashSet<>();
    private static final Map<Long, List<BlockPos>> MATCHES_BY_CHUNK = new HashMap<>();

    private static String scanFamilySignature = "";
    private static int nextChunkOffset;
    private static int centerChunkX;
    private static int centerChunkZ;
    private static int lastPlayerChunkX = Integer.MIN_VALUE;
    private static int lastPlayerChunkZ = Integer.MIN_VALUE;

    private XrayClientState() {
    }

    static List<ItemStack> selectedStacks() {
        List<ItemStack> copy = new ArrayList<>(SELECTED_STACKS.size());
        for (ItemStack stack : SELECTED_STACKS) {
            copy.add(stack.copy());
        }
        return copy;
    }

    static void setSelectedStacks(List<ItemStack> stacks) {
        SELECTED_STACKS.clear();
        SELECTED_FAMILIES.clear();
        for (ItemStack stack : stacks) {
            String family = familyForStack(stack);
            if (family != null && SELECTED_FAMILIES.add(family)) {
                SELECTED_STACKS.add(stack.copyWithCount(1));
                if (SELECTED_STACKS.size() >= SLOT_COUNT) {
                    break;
                }
            }
        }
        invalidateScan();
    }

    static String familyForStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        for (Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            if (block.asItem() == stack.getItem()) {
                String family = OreFamily.key(block);
                if (family != null) {
                    return family;
                }
            }
        }
        return null;
    }

    static boolean canAccept(ItemStack stack) {
        return familyForStack(stack) != null;
    }

    static void tick(Minecraft minecraft) {
        if (!VeinMinerPlusClient.isXrayMode() || minecraft.level == null || minecraft.player == null
                || SELECTED_FAMILIES.isEmpty()) {
            invalidateScan();
            return;
        }

        int chunkX = minecraft.player.blockPosition().getX() >> 4;
        int chunkZ = minecraft.player.blockPosition().getZ() >> 4;
        String signature = String.join("|", SELECTED_FAMILIES);
        if (!signature.equals(scanFamilySignature) || chunkX != lastPlayerChunkX || chunkZ != lastPlayerChunkZ) {
            scanFamilySignature = signature;
            centerChunkX = chunkX;
            centerChunkZ = chunkZ;
            lastPlayerChunkX = chunkX;
            lastPlayerChunkZ = chunkZ;
            nextChunkOffset = 0;
            MATCHES_BY_CHUNK.clear();
        }

        for (int i = 0; i < CHUNKS_PER_TICK && nextChunkOffset < CHUNK_OFFSETS.size(); i++) {
            BlockPos offset = CHUNK_OFFSETS.get(nextChunkOffset++);
            scanChunk(minecraft, centerChunkX + offset.getX(), centerChunkZ + offset.getZ());
        }

        MATCHES_BY_CHUNK.keySet().removeIf(key -> {
            int x = (int) (key >> 32);
            int z = (int) (key & 0xffffffffL);
            return Math.abs(x - centerChunkX) > CHUNK_RADIUS || Math.abs(z - centerChunkZ) > CHUNK_RADIUS;
        });
    }

    private static void scanChunk(Minecraft minecraft, int chunkX, int chunkZ) {
        if (!minecraft.level.hasChunkAt(new BlockPos(chunkX << 4, 0, chunkZ << 4))) {
            return;
        }

        long key = chunkKey(chunkX, chunkZ);
        List<BlockPos> matches = new ArrayList<>();
        minecraft.level.getChunk(chunkX, chunkZ).findBlocks(
                state -> {
                    String family = OreFamily.key(state.getBlock());
                    return family != null && SELECTED_FAMILIES.contains(family);
                }, (pos, state) -> {
                    if (withinRadius(minecraft, pos)) {
                        matches.add(pos.immutable());
                    }
                });
        MATCHES_BY_CHUNK.put(key, matches);
    }

    private static boolean withinRadius(Minecraft minecraft, BlockPos pos) {
        return minecraft.player.blockPosition().distSqr(pos) <= (long) SEARCH_RADIUS * SEARCH_RADIUS;
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || !VeinMinerPlusClient.isXrayMode() || SELECTED_FAMILIES.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        Camera camera = event.getCamera();
        Vector3f cameraPosition = camera.getPosition().toVector3f();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        int rendered = 0;
        RenderSystem.disableDepthTest();
        try {
            for (List<BlockPos> matches : MATCHES_BY_CHUNK.values()) {
                for (BlockPos pos : matches) {
                    if (rendered++ >= MAX_RENDERED_BLOCKS) {
                        break;
                    }
                    BlockState state = minecraft.level.getBlockState(pos);
                    String family = OreFamily.key(state.getBlock());
                    if (family == null || !SELECTED_FAMILIES.contains(family)) {
                        continue;
                    }
                    pose.pushPose();
                    pose.translate(pos.getX() - cameraPosition.x(), pos.getY() - cameraPosition.y(),
                            pos.getZ() - cameraPosition.z());
                    minecraft.getBlockRenderer().renderSingleBlock(state, pose, buffers, LightTexture.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY);
                    pose.popPose();
                }
                if (rendered >= MAX_RENDERED_BLOCKS) {
                    break;
                }
            }
        } finally {
            buffers.endBatch();
            RenderSystem.enableDepthTest();
        }
    }

    private static void invalidateScan() {
        scanFamilySignature = "";
        MATCHES_BY_CHUNK.clear();
        nextChunkOffset = 0;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static List<BlockPos> createChunkOffsets() {
        List<BlockPos> offsets = new ArrayList<>();
        for (int x = -CHUNK_RADIUS; x <= CHUNK_RADIUS; x++) {
            for (int z = -CHUNK_RADIUS; z <= CHUNK_RADIUS; z++) {
                offsets.add(new BlockPos(x, 0, z));
            }
        }
        offsets.sort(Comparator.comparingLong(pos -> (long) pos.getX() * pos.getX() + (long) pos.getZ() * pos.getZ()));
        return List.copyOf(offsets);
    }
}
