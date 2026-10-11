package com.extrarawstyle.veinminerplus;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.fml.loading.FMLPaths;

final class XrayClientState {
    static final int SLOT_COUNT = 9;
    static final int DEFAULT_COLOR = 0xFF5555;
    private static final int SEARCH_RADIUS = 48;
    private static final int CHUNK_RADIUS = (SEARCH_RADIUS + 15) >> 4;
    private static final int CHUNKS_PER_TICK = 2;
    private static final int MAX_RENDERED_BLOCKS = 2048;

    private static final RenderType XRAY_RENDER_TYPE = RenderType.create("veinminerplus_xray_outline",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, RenderType.SMALL_BUFFER_SIZE,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(2.0)))
                    .setCullState(RenderStateShard.NO_CULL)
                    // Apply this at draw time so terrain cannot hide the outline.
                    .setDepthTestState(new RenderStateShard.DepthTestStateShard("always", org.lwjgl.opengl.GL11.GL_ALWAYS) {
                        @Override
                        public void setupRenderState() {
                            RenderSystem.disableDepthTest();
                        }

                        @Override
                        public void clearRenderState() {
                            RenderSystem.enableDepthTest();
                        }
                    })
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private static final List<BlockPos> CHUNK_OFFSETS = createChunkOffsets();
    private static final List<Selection> SELECTION = new ArrayList<>();
    private static final Set<String> SELECTED_FAMILIES = new HashSet<>();
    private static final Map<Block, Integer> SELECTED_BLOCK_COLORS = new HashMap<>();
    private static final Map<String, Integer> SELECTED_FAMILY_COLORS = new HashMap<>();
    private static final Map<Long, List<BlockPos>> MATCHES_BY_CHUNK = new HashMap<>();
    private static boolean selectionLoaded;

    private static String scanFamilySignature = "";
    private static ClientLevel scanLevel;
    private static int nextChunkOffset;
    private static int centerChunkX;
    private static int centerChunkZ;
    private static int lastPlayerChunkX = Integer.MIN_VALUE;
    private static int lastPlayerChunkZ = Integer.MIN_VALUE;

    private XrayClientState() {
    }

    record Selection(ItemStack stack, int color) {
    }

    static List<Selection> selection() {
        loadSelection();
        List<Selection> copy = new ArrayList<>(SLOT_COUNT);
        for (Selection entry : SELECTION) {
            copy.add(new Selection(entry.stack().copy(), entry.color()));
        }
        return copy;
    }

    static void setSelection(List<Selection> entries) {
        selectionLoaded = true;
        applySelection(entries);
        saveSelection();
    }

    private static void applySelection(List<Selection> entries) {
        SELECTION.clear();
        SELECTED_FAMILIES.clear();
        SELECTED_BLOCK_COLORS.clear();
        SELECTED_FAMILY_COLORS.clear();
        for (int index = 0; index < SLOT_COUNT; index++) {
            Selection entry = index < entries.size() ? entries.get(index) : new Selection(ItemStack.EMPTY, DEFAULT_COLOR);
            Block block = blockForStack(entry.stack());
            String family = OreFamily.key(block);
            int color = entry.color() & 0xFFFFFF;
            SELECTION.add(new Selection(family == null ? ItemStack.EMPTY : entry.stack().copyWithCount(1), color));
            if (family != null) {
                SELECTED_FAMILIES.add(family);
                SELECTED_BLOCK_COLORS.putIfAbsent(block, color);
                SELECTED_FAMILY_COLORS.putIfAbsent(family, color);
            }
        }
        invalidateScan();
    }

    private static Path selectionFile() {
        return FMLPaths.CONFIGDIR.get().resolve("veinminerplus-xray.json");
    }

    private static void loadSelection() {
        if (selectionLoaded) {
            return;
        }
        selectionLoaded = true;
        List<Selection> entries = new ArrayList<>();
        Path file = selectionFile();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
                    if (entries.size() >= SLOT_COUNT) {
                        break;
                    }
                    JsonObject value = element.getAsJsonObject();
                    ResourceLocation id = value.has("item") ? ResourceLocation.tryParse(value.get("item").getAsString()) : null;
                    ItemStack stack = id == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.get(id).getDefaultInstance();
                    int color = value.has("color") ? value.get("color").getAsInt() : DEFAULT_COLOR;
                    entries.add(new Selection(stack, color));
                }
            } catch (IOException | RuntimeException exception) {
                VeinMinerPlus.LOGGER.warn("Could not load x-ray selections from {}", file, exception);
            }
        }
        applySelection(entries);
    }

    private static void saveSelection() {
        JsonArray values = new JsonArray();
        for (Selection entry : SELECTION) {
            JsonObject value = new JsonObject();
            if (!entry.stack().isEmpty()) {
                value.addProperty("item", BuiltInRegistries.ITEM.getKey(entry.stack().getItem()).toString());
            }
            value.addProperty("color", entry.color());
            values.add(value);
        }
        Path file = selectionFile();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(values, writer);
            }
        } catch (IOException exception) {
            VeinMinerPlus.LOGGER.warn("Could not save x-ray selections to {}", file, exception);
        }
    }

    private static Block blockForStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block.asItem() == stack.getItem()) {
                return block;
            }
        }
        return null;
    }

    static String familyForStack(ItemStack stack) {
        return OreFamily.key(blockForStack(stack));
    }

    static boolean canAccept(ItemStack stack) {
        return familyForStack(stack) != null;
    }

    static boolean hasSelection() {
        loadSelection();
        return !SELECTED_FAMILIES.isEmpty();
    }

    static void tick(Minecraft minecraft) {
        if (!VeinMinerPlusClient.isXrayMode() || minecraft.level == null || minecraft.player == null
                || !hasSelection()) {
            invalidateScan();
            return;
        }

        int chunkX = minecraft.player.blockPosition().getX() >> 4;
        int chunkZ = minecraft.player.blockPosition().getZ() >> 4;
        String signature = String.join("|", SELECTED_FAMILIES);
        if (minecraft.level != scanLevel || !signature.equals(scanFamilySignature)
                || chunkX != lastPlayerChunkX || chunkZ != lastPlayerChunkZ) {
            scanLevel = minecraft.level;
            scanFamilySignature = signature;
            centerChunkX = chunkX;
            centerChunkZ = chunkZ;
            lastPlayerChunkX = chunkX;
            lastPlayerChunkZ = chunkZ;
            nextChunkOffset = 0;
            MATCHES_BY_CHUNK.clear();
        }

        // Keep revisiting chunks so descending, late chunk loads and new ores refresh the results.
        for (int i = 0; i < CHUNKS_PER_TICK; i++) {
            BlockPos offset = CHUNK_OFFSETS.get(nextChunkOffset);
            nextChunkOffset = (nextChunkOffset + 1) % CHUNK_OFFSETS.size();
            scanChunk(minecraft, centerChunkX + offset.getX(), centerChunkZ + offset.getZ());
        }

        MATCHES_BY_CHUNK.keySet().removeIf(key -> {
            int x = (int) (key >> 32);
            int z = (int) (key & 0xffffffffL);
            return Math.abs(x - centerChunkX) > CHUNK_RADIUS || Math.abs(z - centerChunkZ) > CHUNK_RADIUS;
        });
    }

    private static void scanChunk(Minecraft minecraft, int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        if (!minecraft.level.hasChunkAt(new BlockPos(chunkX << 4, 0, chunkZ << 4))) {
            MATCHES_BY_CHUNK.remove(key);
            return;
        }

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
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL
                || !VeinMinerPlusClient.isXrayMode() || SELECTED_FAMILIES.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        Vec3 cameraPosition = event.getCamera().getPosition();
        // AFTER_LEVEL runs after vanilla has popped the world model-view matrix.
        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        int rendered = 0;
        try {
            for (List<BlockPos> matches : MATCHES_BY_CHUNK.values()) {
                for (BlockPos pos : matches) {
                    if (rendered >= MAX_RENDERED_BLOCKS) {
                        break;
                    }
                    if (!withinRadius(minecraft, pos) || !minecraft.level.hasChunkAt(pos)) {
                        continue;
                    }
                    BlockState state = minecraft.level.getBlockState(pos);
                    String family = OreFamily.key(state.getBlock());
                    if (family == null || !SELECTED_FAMILIES.contains(family)) {
                        continue;
                    }
                    rendered++;
                    pose.pushPose();
                    try {
                        pose.translate(pos.getX() - cameraPosition.x, pos.getY() - cameraPosition.y,
                                pos.getZ() - cameraPosition.z);
                        int color = colorForBlock(state.getBlock());
                        LevelRenderer.renderLineBox(pose, buffers.getBuffer(XRAY_RENDER_TYPE), 0, 0, 0, 1, 1, 1,
                                ((color >> 16) & 255) / 255.0F, ((color >> 8) & 255) / 255.0F,
                                (color & 255) / 255.0F, 1.0F);
                    } finally {
                        pose.popPose();
                    }
                }
                if (rendered >= MAX_RENDERED_BLOCKS) {
                    break;
                }
            }
        } finally {
            buffers.endBatch(XRAY_RENDER_TYPE);
        }
    }

    private static int colorForBlock(Block block) {
        return SELECTED_BLOCK_COLORS.getOrDefault(block,
                SELECTED_FAMILY_COLORS.getOrDefault(OreFamily.key(block), DEFAULT_COLOR));
    }

    private static void invalidateScan() {
        scanLevel = null;
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
