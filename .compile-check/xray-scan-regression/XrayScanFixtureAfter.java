import java.util.*;
import java.util.function.*;
public class XrayScanFixtureAfter {
    record BlockPos(int x, int y, int z) {
        int getX() { return x; }
        int getY() { return y; }
        int getZ() { return z; }
        BlockPos immutable() { return this; }
        double distSqr(BlockPos other) {
            long dx = x - other.x, dy = y - other.y, dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }
    enum Block { IRON, STONE, URANIUM, NETHER_URANIUM, DEEPSLATE_URANIUM }
    record BlockState(Block block) { Block getBlock() { return block; } }
    static final class OreFamily {
        static String key(Block block) {
            return switch (block) {
                case IRON -> "minecraft:iron";
                case URANIUM, NETHER_URANIUM, DEEPSLATE_URANIUM -> "alltheores:uranium";
                default -> null;
            };
        }
    }
    static final class Player {
        BlockPos pos = new BlockPos(0, 64, 0);
        BlockPos blockPosition() { return pos; }
    }
    static final class ClientLevel {
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        final Set<Long> unloaded = new HashSet<>();
        int probes, scans;
        boolean hasChunkAt(BlockPos pos) {
            probes++;
            return !unloaded.contains(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
        }
        Chunk getChunk(int x, int z) {
            if (unloaded.contains(chunkKey(x, z))) throw new AssertionError("unloaded chunk requested");
            return new Chunk(this, x, z);
        }
        BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, new BlockState(Block.STONE));
        }
    }
    record Chunk(ClientLevel level, int x, int z) {
        void findBlocks(Predicate<BlockState> predicate, BiConsumer<BlockPos, BlockState> visitor) {
            level.scans++;
            level.blocks.forEach((pos, state) -> {
                if ((pos.getX() >> 4) == x && (pos.getZ() >> 4) == z && predicate.test(state)) {
                    visitor.accept(pos, state);
                }
            });
        }
    }
    static final class Minecraft {
        static Minecraft current;
        ClientLevel level = new ClientLevel();
        Player player = new Player();
        static Minecraft getInstance() { return current; }
        Minecraft renderBuffers() { return this; }
        MultiBufferSource.BufferSource bufferSource() { return new MultiBufferSource.BufferSource(); }
    }
    static final class VeinMinerPlusClient {
        static boolean xray = true;
        static boolean isXrayMode() { return xray; }
    }
    static final Object XRAY_RENDER_TYPE = new Object();
    static final class Vec3 { double x, y, z; }
    static final class RenderLevelStageEvent {
        enum Stage { AFTER_LEVEL }
        Stage getStage() { return Stage.AFTER_LEVEL; }
        RenderLevelStageEvent getCamera() { return this; }
        Vec3 getPosition() { return new Vec3(); }
        Object getModelViewMatrix() { return this; }
    }
    static final class PoseStack {
        double x, y, z;
        void mulPose(Object matrix) {}
        void pushPose() {}
        void popPose() {}
        void translate(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }
    static final class MultiBufferSource {
        static final class BufferSource {
            Object getBuffer(Object type) { return type; }
            void endBatch(Object type) {}
        }
    }
    static final Map<BlockPos, Integer> DRAWN = new HashMap<>();
    static final class LevelRenderer {
        static void renderLineBox(PoseStack pose, Object buffer,
                double x0, double y0, double z0, double x1, double y1, double z1,
                float red, float green, float blue, float alpha) {
            if (x0 != 0 || y0 != 0 || z0 != 0 || x1 != 1 || y1 != 1 || z1 != 1 || alpha != 1) {
                throw new AssertionError("expected an opaque outline of the whole selected block");
            }
            DRAWN.put(new BlockPos((int) pose.x, (int) pose.y, (int) pose.z),
                    Math.round(red * 255) << 16 | Math.round(green * 255) << 8 | Math.round(blue * 255));
        }
    }
    static void loadSelection() {}
    static final int DEFAULT_COLOR = 16733525;
private static final int SEARCH_RADIUS = 48;
private static final int CHUNK_RADIUS = (SEARCH_RADIUS + 15) >> 4;
private static final int CHUNKS_PER_TICK = 2;
private static final int MAX_RENDERED_BLOCKS = 2048;
private static final List<BlockPos> CHUNK_OFFSETS = createChunkOffsets();
private static final Map<Block, Integer> SELECTED_BLOCK_COLORS = new HashMap<>();
private static final Map<Long, List<BlockPos>> MATCHES_BY_CHUNK = new HashMap<>();
private static ClientLevel scanLevel;
private static int nextChunkOffset;
private static int centerChunkX;
private static int centerChunkZ;
private static int lastPlayerChunkX = Integer.MIN_VALUE;
private static int lastPlayerChunkZ = Integer.MIN_VALUE;

static boolean hasSelection() {
    loadSelection();
    return !SELECTED_BLOCK_COLORS.isEmpty();
}

static void tick(Minecraft minecraft) {
    if (!VeinMinerPlusClient.isXrayMode() || minecraft.level == null || minecraft.player == null || !hasSelection()) {
        invalidateScan();
        return;
    }
    int chunkX = minecraft.player.blockPosition().getX() >> 4;
    int chunkZ = minecraft.player.blockPosition().getZ() >> 4;
    if (minecraft.level != scanLevel || chunkX != lastPlayerChunkX || chunkZ != lastPlayerChunkZ) {
        scanLevel = minecraft.level;
        centerChunkX = chunkX;
        centerChunkZ = chunkZ;
        lastPlayerChunkX = chunkX;
        lastPlayerChunkZ = chunkZ;
        nextChunkOffset = 0;
        MATCHES_BY_CHUNK.clear();
    }
    for (int i = 0; i < CHUNKS_PER_TICK; i++) {
        BlockPos offset = CHUNK_OFFSETS.get(nextChunkOffset);
        nextChunkOffset = (nextChunkOffset + 1) % CHUNK_OFFSETS.size();
        scanChunk(minecraft, centerChunkX + offset.getX(), centerChunkZ + offset.getZ());
    }
    MATCHES_BY_CHUNK.keySet().removeIf((key)->{
        int x = (int)(key >> 32);
        int z = (int)(key & 4294967295L);
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
    minecraft.level.getChunk(chunkX, chunkZ).findBlocks((state)->SELECTED_BLOCK_COLORS.containsKey(state.getBlock()), (pos,state)->{
        if (withinRadius(minecraft, pos)) {
            matches.add(pos.immutable());
        }
    });
    MATCHES_BY_CHUNK.put(key, matches);
}

private static boolean withinRadius(Minecraft minecraft, BlockPos pos) {
    return minecraft.player.blockPosition().distSqr(pos) <= (long)SEARCH_RADIUS * SEARCH_RADIUS;
}

static void render(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || !VeinMinerPlusClient.isXrayMode() || SELECTED_BLOCK_COLORS.isEmpty()) {
        return;
    }
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.level == null || minecraft.player == null) {
        return;
    }
    Vec3 cameraPosition = event.getCamera().getPosition();
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
                if (!SELECTED_BLOCK_COLORS.containsKey(state.getBlock())) {
                    continue;
                }
                rendered++;
                pose.pushPose();
                try {
                    pose.translate(pos.getX() - cameraPosition.x, pos.getY() - cameraPosition.y, pos.getZ() - cameraPosition.z);
                    int color = colorForBlock(state.getBlock());
                    LevelRenderer.renderLineBox(pose, buffers.getBuffer(XRAY_RENDER_TYPE), 0, 0, 0, 1, 1, 1, ((color >> 16) & 255) / 255.0F, ((color >> 8) & 255) / 255.0F, (color & 255) / 255.0F, 1.0F);
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
    return SELECTED_BLOCK_COLORS.getOrDefault(block, DEFAULT_COLOR);
}

private static void invalidateScan() {
    scanLevel = null;
    MATCHES_BY_CHUNK.clear();
    nextChunkOffset = 0;
}

private static long chunkKey(int chunkX, int chunkZ) {
    return ((long)chunkX << 32) ^ (chunkZ & 4294967295L);
}

private static List<BlockPos> createChunkOffsets() {
    List<BlockPos> offsets = new ArrayList<>();
    for (int x = -CHUNK_RADIUS; x <= CHUNK_RADIUS; x++) {
        for (int z = -CHUNK_RADIUS; z <= CHUNK_RADIUS; z++) {
            offsets.add(new BlockPos(x, 0, z));
        }
    }
    offsets.sort(Comparator.comparingLong((pos)->(long)pos.getX() * pos.getX() + (long)pos.getZ() * pos.getZ()));
    return List.copyOf(offsets);
}

    static final BlockPos ORE = new BlockPos(1, 64, 1);
    static final BlockPos URANIUM = new BlockPos(2, 64, 1);
    static final BlockPos NETHER = new BlockPos(3, 64, 1);
    static final BlockPos DEEPSLATE = new BlockPos(4, 64, 1);
    static final List<String> failures = new ArrayList<>();
    static int assertions, observedTicks;
    static Minecraft reset() {
        invalidateScan();
        select(Map.of(Block.IRON, 0xFF5555));
        VeinMinerPlusClient.xray = true;
        Minecraft.current = new Minecraft();
        return Minecraft.current;
    }
    static void select(Map<Block, Integer> colors) {
        SELECTED_BLOCK_COLORS.clear();
        SELECTED_BLOCK_COLORS.putAll(colors);
        
    }
    static void draw() {
        DRAWN.clear();
        render(new RenderLevelStageEvent());
    }
    static void advance(Minecraft minecraft, int ticks) {
        for (int i = 0; i < ticks; i++) {
            minecraft.level.probes = minecraft.level.scans = 0;
            tick(minecraft);
            observedTicks++;
            if (minecraft.level.probes > 2 || minecraft.level.scans > 2) {
                throw new AssertionError("scan budget exceeded at tick " + observedTicks);
            }
        }
    }
    static boolean found(BlockPos pos) {
        return MATCHES_BY_CHUNK.values().stream().anyMatch(matches -> matches.contains(pos));
    }
    static void check(boolean condition, String label) {
        assertions++;
        if (!condition) failures.add(label);
    }
    static void place(Minecraft minecraft) {
        minecraft.level.blocks.put(ORE, new BlockState(Block.IRON));
    }
    public static List<String> run() {
        Minecraft minecraft = reset();
        place(minecraft);
        minecraft.player.pos = new BlockPos(0, 253, 0);
        advance(minecraft, 25);
        check(!found(ORE), "high altitude excludes distant ore");
        minecraft.player.pos = new BlockPos(0, 64, 0);
        advance(minecraft, 25);
        check(found(ORE), "same-chunk descent refreshes ore matches");

        minecraft = reset();
        place(minecraft);
        minecraft.level.unloaded.add(chunkKey(0, 0));
        advance(minecraft, 25);
        check(!found(ORE), "unloaded chunk is not scanned");
        minecraft.level.unloaded.clear();
        advance(minecraft, 25);
        check(found(ORE), "newly loaded chunk is revisited");

        minecraft = reset();
        advance(minecraft, 25);
        check(!found(ORE), "empty world has no matches");
        place(minecraft);
        advance(minecraft, 25);
        check(found(ORE), "ore placed after first scan becomes visible");

        minecraft = reset();
        place(minecraft);
        advance(minecraft, 25);
        check(found(ORE), "initial loaded ore is scanned");
        minecraft.level.blocks.clear();
        advance(minecraft, 25);
        check(!found(ORE), "removed ore expires from scan cache");

        minecraft = reset();
        place(minecraft);
        advance(minecraft, 25);
        minecraft.level.unloaded.add(chunkKey(0, 0));
        advance(minecraft, 25);
        check(!MATCHES_BY_CHUNK.containsKey(chunkKey(0, 0)), "unloaded chunk drops cached matches");

        minecraft = reset();
        place(minecraft);
        advance(minecraft, 25);
        minecraft.level = new ClientLevel();
        advance(minecraft, 1);
        check(!found(ORE), "world change at identical coordinates clears old matches immediately");

        minecraft = reset();
        place(minecraft);
        advance(minecraft, 25);
        select(Map.of());
        advance(minecraft, 1);
        check(MATCHES_BY_CHUNK.isEmpty(), "empty selection clears scan cache");

        minecraft = reset();
        place(minecraft);
        advance(minecraft, 25);
        VeinMinerPlusClient.xray = false;
        advance(minecraft, 1);
        check(MATCHES_BY_CHUNK.isEmpty(), "leaving xray clears scan cache");

        minecraft = reset();
        minecraft.level.blocks.put(URANIUM, new BlockState(Block.URANIUM));
        minecraft.level.blocks.put(NETHER, new BlockState(Block.NETHER_URANIUM));
        minecraft.level.blocks.put(DEEPSLATE, new BlockState(Block.DEEPSLATE_URANIUM));
        select(Map.of(Block.URANIUM, 0xE01020));
        advance(minecraft, 25);
        check(found(URANIUM), "selected uranium scans successfully");
        check(!found(NETHER) && !found(DEEPSLATE), "single exact selection excludes other uranium variants from scans");
        draw();
        check(DRAWN.get(URANIUM) != null && DRAWN.get(URANIUM) == 0xE01020,
                "selected uranium outline uses its slot color");
        check(DRAWN.keySet().equals(Set.of(URANIUM)), "single exact selection renders only uranium");

        select(Map.of(Block.URANIUM, 0xE01020, Block.NETHER_URANIUM, 0x2030F0));
        invalidateScan();
        advance(minecraft, 25);
        check(found(URANIUM) && found(NETHER), "two exact selections scan both selected variants");
        check(!found(DEEPSLATE), "two exact selections exclude the unselected third uranium variant from scans");
        draw();
        check(DRAWN.get(URANIUM) != null && DRAWN.get(URANIUM) == 0xE01020
                && DRAWN.get(NETHER) != null && DRAWN.get(NETHER) == 0x2030F0,
                "two selected variants render with independent slot colors");
        check(DRAWN.keySet().equals(Set.of(URANIUM, NETHER)), "two exact selections render only their own variants");

        select(Map.of(Block.NETHER_URANIUM, 0x2030F0));
        draw();
        check(DRAWN.keySet().equals(Set.of(NETHER)), "render guard rejects a removed exact selection even in stale scan cache");
        invalidateScan();
        advance(minecraft, 1);
        check(!found(URANIUM) && found(NETHER), "selection invalidation rescans the center immediately");
        minecraft.level.blocks.put(NETHER, new BlockState(Block.DEEPSLATE_URANIUM));
        draw();
        check(DRAWN.isEmpty(), "render guard rejects a cached block replaced by an unselected family variant");

        select(Map.of());
        draw();
        check(DRAWN.isEmpty(), "empty selection cannot render cached outlines");
        System.out.println("Checked " + assertions + " assertions and " + observedTicks
                + " ticks with at most 2 chunk probes/scans per tick.");
        return List.copyOf(failures);
    }
}
