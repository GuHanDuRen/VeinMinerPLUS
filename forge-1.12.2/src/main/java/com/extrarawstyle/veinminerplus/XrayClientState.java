package com.extrarawstyle.veinminerplus;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = VeinMinerPlus.MODID, value = Side.CLIENT)
final class XrayClientState {
    static final int SLOT_COUNT = 9;
    static final int DEFAULT_COLOR = 0xFF5555;
    private static final int[] DEFAULT_COLORS = { 0xFFAA00, 0xFF5555, 0xFFFF55, 0x55FF55, 0x55FFFF,
            0x5599FF, 0xAA55FF, 0xFF55AA, 0xFFFFFF };
    private static final int SEARCH_RADIUS = 48;
    private static final int CHUNK_RADIUS = (SEARCH_RADIUS + 15) >> 4;
    private static final int CHUNKS_PER_TICK = 2;
    private static final int MAX_RENDERED_BLOCKS = 2048;

    private static final List<BlockPos> CHUNK_OFFSETS = createChunkOffsets();
    private static final List<Selection> SELECTION = new ArrayList<Selection>();
    private static final Map<Block, Integer> SELECTED_BLOCK_COLORS = new HashMap<Block, Integer>();
    private static final Map<Long, List<BlockPos>> MATCHES_BY_CHUNK = new HashMap<Long, List<BlockPos>>();
    private static boolean selectionLoaded;
    private static net.minecraft.world.World scanWorld;
    private static int centerChunkX;
    private static int centerChunkZ;
    private static int nextChunkOffset;
    private static int lastPlayerChunkX = Integer.MIN_VALUE;
    private static int lastPlayerChunkZ = Integer.MIN_VALUE;

    private XrayClientState() {
    }

    static final class Selection {
        private final ItemStack stack;
        private final int color;

        Selection(ItemStack stack, int color) {
            this.stack = stack == null ? ItemStack.EMPTY : stack;
            this.color = color & 0xFFFFFF;
        }

        ItemStack stack() { return stack; }
        int color() { return color; }
    }

    static int defaultColor(int index) {
        return DEFAULT_COLORS[index % DEFAULT_COLORS.length];
    }

    static List<Selection> selection() {
        loadSelection();
        List<Selection> copy = new ArrayList<Selection>(SLOT_COUNT);
        for (Selection entry : SELECTION) {
            copy.add(new Selection(entry.stack().copy(), entry.color()));
        }
        return copy;
    }

    static void setSelection(List<Selection> entries) {
        selectionLoaded = true;
        SELECTION.clear();
        SELECTED_BLOCK_COLORS.clear();
        for (int index = 0; index < SLOT_COUNT; index++) {
            Selection entry = index < entries.size() ? entries.get(index)
                    : new Selection(ItemStack.EMPTY, defaultColor(index));
            Block block = blockForStack(entry.stack());
            boolean valid = OreFamily.key(block) != null;
            Selection normalized = new Selection(valid ? entry.stack().copy() : ItemStack.EMPTY, entry.color());
            SELECTION.add(normalized);
            if (valid) {
                SELECTED_BLOCK_COLORS.put(block, normalized.color());
            }
        }
        invalidateScan();
        saveSelection();
    }

    static boolean canAccept(ItemStack stack) {
        return OreFamily.key(blockForStack(stack)) != null;
    }

    static void tick(Minecraft minecraft) {
        if (!VeinMinerPlusClient.isXrayMode() || minecraft.world == null || minecraft.player == null
                || !hasSelection()) {
            invalidateScan();
            return;
        }
        int chunkX = minecraft.player.getPosition().getX() >> 4;
        int chunkZ = minecraft.player.getPosition().getZ() >> 4;
        if (minecraft.world != scanWorld || chunkX != lastPlayerChunkX || chunkZ != lastPlayerChunkZ) {
            scanWorld = minecraft.world;
            centerChunkX = chunkX;
            centerChunkZ = chunkZ;
            lastPlayerChunkX = chunkX;
            lastPlayerChunkZ = chunkZ;
            nextChunkOffset = 0;
            MATCHES_BY_CHUNK.clear();
        }
        for (int index = 0; index < CHUNKS_PER_TICK; index++) {
            BlockPos offset = CHUNK_OFFSETS.get(nextChunkOffset);
            nextChunkOffset = (nextChunkOffset + 1) % CHUNK_OFFSETS.size();
            scanChunk(minecraft, centerChunkX + offset.getX(), centerChunkZ + offset.getZ());
        }
        List<Long> stale = new ArrayList<Long>();
        for (Long key : MATCHES_BY_CHUNK.keySet()) {
            int x = (int) (key.longValue() >> 32);
            int z = (int) key.longValue();
            if (Math.abs(x - centerChunkX) > CHUNK_RADIUS || Math.abs(z - centerChunkZ) > CHUNK_RADIUS) {
                stale.add(key);
            }
        }
        for (Long key : stale) {
            MATCHES_BY_CHUNK.remove(key);
        }
    }

    private static void scanChunk(Minecraft minecraft, int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        if (!minecraft.world.isChunkGeneratedAt(chunkX, chunkZ)) {
            MATCHES_BY_CHUNK.remove(key);
            return;
        }
        Chunk chunk = minecraft.world.getChunkFromChunkCoords(chunkX, chunkZ);
        List<BlockPos> matches = new ArrayList<BlockPos>();
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 256; y++) {
                    IBlockState state = chunk.getBlockState(new BlockPos(minX + x, y, minZ + z));
                    if (SELECTED_BLOCK_COLORS.containsKey(state.getBlock())) {
                        BlockPos pos = new BlockPos(minX + x, y, minZ + z);
                        if (withinRadius(minecraft, pos)) {
                            matches.add(pos);
                        }
                    }
                }
            }
        }
        MATCHES_BY_CHUNK.put(key, matches);
    }

    private static boolean withinRadius(Minecraft minecraft, BlockPos pos) {
        return minecraft.player.getDistanceSqToCenter(pos) <= (long) SEARCH_RADIUS * SEARCH_RADIUS;
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderWorldLastEvent event) {
        if (!VeinMinerPlusClient.isXrayMode() || SELECTED_BLOCK_COLORS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || minecraft.player == null) {
            return;
        }
        double viewX = minecraft.getRenderManager().viewerPosX;
        double viewY = minecraft.getRenderManager().viewerPosY;
        double viewZ = minecraft.getRenderManager().viewerPosZ;
        GlStateManager.pushMatrix();
        GlStateManager.translate(-viewX, -viewY, -viewZ);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.glLineWidth(2.0F);
        int rendered = 0;
        try {
            for (List<BlockPos> matches : MATCHES_BY_CHUNK.values()) {
                for (BlockPos pos : matches) {
                    if (rendered >= MAX_RENDERED_BLOCKS || !withinRadius(minecraft, pos)) {
                        break;
                    }
                    IBlockState state = minecraft.world.getBlockState(pos);
                    Integer color = SELECTED_BLOCK_COLORS.get(state.getBlock());
                    if (color == null) {
                        continue;
                    }
                    rendered++;
                    float red = ((color.intValue() >> 16) & 255) / 255.0F;
                    float green = ((color.intValue() >> 8) & 255) / 255.0F;
                    float blue = (color.intValue() & 255) / 255.0F;
                    RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(pos), red, green, blue, 1.0F);
                }
                if (rendered >= MAX_RENDERED_BLOCKS) {
                    break;
                }
            }
        } finally {
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    private static Block blockForStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Block block = Block.getBlockFromItem(stack.getItem());
        return block == Blocks.AIR ? null : block;
    }

    private static boolean hasSelection() {
        loadSelection();
        return !SELECTED_BLOCK_COLORS.isEmpty();
    }

    private static void invalidateScan() {
        scanWorld = null;
        MATCHES_BY_CHUNK.clear();
        nextChunkOffset = 0;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static List<BlockPos> createChunkOffsets() {
        List<BlockPos> offsets = new ArrayList<BlockPos>();
        for (int x = -CHUNK_RADIUS; x <= CHUNK_RADIUS; x++) {
            for (int z = -CHUNK_RADIUS; z <= CHUNK_RADIUS; z++) {
                offsets.add(new BlockPos(x, 0, z));
            }
        }
        Collections.sort(offsets, new Comparator<BlockPos>() {
            @Override
            public int compare(BlockPos left, BlockPos right) {
                long a = (long) left.getX() * left.getX() + (long) left.getZ() * left.getZ();
                long b = (long) right.getX() * right.getX() + (long) right.getZ() * right.getZ();
                return Long.compare(a, b);
            }
        });
        return offsets;
    }

    private static File selectionFile() {
        return new File(Minecraft.getMinecraft().mcDataDir, "config/veinminerplus-xray.json");
    }

    private static void loadSelection() {
        if (selectionLoaded) {
            return;
        }
        selectionLoaded = true;
        List<Selection> entries = new ArrayList<Selection>();
        File file = selectionFile();
        if (file.isFile()) {
            try (Reader reader = new java.io.FileReader(file)) {
                JsonArray values = new JsonParser().parse(reader).getAsJsonArray();
                for (JsonElement element : values) {
                    if (entries.size() >= SLOT_COUNT) {
                        break;
                    }
                    JsonObject value = element.getAsJsonObject();
                    ResourceLocation id = value.has("item") ? new ResourceLocation(value.get("item").getAsString()) : null;
                    Item item = id == null ? null : Item.REGISTRY.getObject(id);
                    ItemStack stack = item == null ? ItemStack.EMPTY : new ItemStack(item);
                    int color = value.has("color") ? value.get("color").getAsInt() : defaultColor(entries.size());
                    entries.add(new Selection(stack, color));
                }
            } catch (Exception exception) {
                VeinMinerPlus.LOGGER.warn("Could not load x-ray selections from {}", file, exception);
            }
        }
        SELECTION.clear();
        SELECTED_BLOCK_COLORS.clear();
        for (int index = 0; index < SLOT_COUNT; index++) {
            Selection entry = index < entries.size() ? entries.get(index)
                    : new Selection(ItemStack.EMPTY, defaultColor(index));
            Block block = blockForStack(entry.stack());
            boolean valid = OreFamily.key(block) != null;
            Selection normalized = new Selection(valid ? entry.stack() : ItemStack.EMPTY, entry.color());
            SELECTION.add(normalized);
            if (valid) {
                SELECTED_BLOCK_COLORS.put(block, normalized.color());
            }
        }
    }

    private static void saveSelection() {
        File file = selectionFile();
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            JsonArray values = new JsonArray();
            for (Selection entry : SELECTION) {
                JsonObject value = new JsonObject();
                if (!entry.stack().isEmpty() && entry.stack().getItem().getRegistryName() != null) {
                    value.addProperty("item", entry.stack().getItem().getRegistryName().toString());
                }
                value.addProperty("color", entry.color());
                values.add(value);
            }
            try (Writer writer = new java.io.FileWriter(file)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(values, writer);
            }
        } catch (Exception exception) {
            VeinMinerPlus.LOGGER.warn("Could not save x-ray selections to {}", file, exception);
        }
    }
}
