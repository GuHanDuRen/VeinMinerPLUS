import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

// Runs production exact-match scan/render methods with small world fixtures, without launching Minecraft.
public final class XrayScanRegression {
    private static final Set<String> FIELDS = Set.of("SEARCH_RADIUS", "CHUNK_RADIUS", "CHUNKS_PER_TICK",
            "CHUNK_OFFSETS", "SELECTED_FAMILIES", "MATCHES_BY_CHUNK", "scanFamilySignature", "scanLevel",
            "nextChunkOffset", "centerChunkX", "centerChunkZ", "lastPlayerChunkX", "lastPlayerChunkZ",
            "SELECTED_BLOCK_COLORS", "SELECTED_FAMILY_COLORS", "DEFAULT_COLOR", "MAX_RENDERED_BLOCKS");
    private static final Set<String> METHODS = Set.of("tick", "scanChunk", "withinRadius", "invalidateScan",
            "chunkKey", "createChunkOffsets", "render", "colorForBlock", "hasSelection");
    private static final String FIXTURE = """
        import java.util.*;
        import java.util.function.*;
        public class $CLASS$ {
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
            $PRODUCTION$
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
                $LEGACY_SELECTION$
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
        """;

    private static SimpleJavaFileObject source(String name, String code) {
        return new SimpleJavaFileObject(URI.create("string:///" + name + ".java"), JavaFileObject.Kind.SOURCE) {
            public CharSequence getCharContent(boolean ignoreEncodingErrors) { return code; }
        };
    }

    private static List<?> runSource(Path path, String name) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        StringBuilder production = new StringBuilder();
        Set<String> extractedMethods = new java.util.HashSet<>();
        JavacTask parser = (JavacTask) compiler.getTask(null, null, null, List.of("-proc:none"), null,
                List.of(source("XrayClientState", Files.readString(path))));
        for (var tree : parser.parse()) {
            new TreeScanner<Void, Void>() {
                public Void visitVariable(VariableTree field, Void ignored) {
                    if (FIELDS.contains(field.getName().toString())) production.append(field).append(";\n");
                    return null;
                }
                public Void visitMethod(MethodTree method, Void ignored) {
                    if (METHODS.contains(method.getName().toString())) {
                        production.append(method).append('\n');
                        extractedMethods.add(method.getName().toString());
                    }
                    return null;
                }
            }.scan(tree, null);
        }
        if (!extractedMethods.equals(METHODS)) throw new AssertionError("production scan methods missing");
        Path output = Path.of(".compile-check", "xray-scan-regression");
        Files.createDirectories(output);
        String legacySelection = production.indexOf("SELECTED_FAMILIES") < 0 ? "" : """
                SELECTED_FAMILIES.clear();
                SELECTED_FAMILY_COLORS.clear();
                colors.forEach((block, color) -> {
                    SELECTED_FAMILIES.add(OreFamily.key(block));
                    SELECTED_FAMILY_COLORS.putIfAbsent(OreFamily.key(block), color);
                });
                """;
        String code = FIXTURE.replace("$CLASS$", name).replace("$PRODUCTION$", production)
                .replace("$LEGACY_SELECTION$", legacySelection);
        Files.writeString(output.resolve(name + ".java"), code);
        if (!compiler.getTask(null, null, null, List.of("-d", output.toString()), null,
                List.of(source(name, code))).call()) throw new AssertionError("fixture compilation failed");
        try (var loader = new URLClassLoader(new java.net.URL[] {output.toUri().toURL()})) {
            return (List<?>) loader.loadClass(name).getMethod("run").invoke(null);
        }
    }

    public static void main(String[] args) throws Exception {
        List<?> beforeFailures = runSource(Path.of(args[0]), "XrayScanFixtureBefore");
        Set<String> expected = Set.of(
                "single exact selection excludes other uranium variants from scans",
                "single exact selection renders only uranium",
                "two exact selections exclude the unselected third uranium variant from scans",
                "two exact selections render only their own variants",
                "render guard rejects a removed exact selection even in stale scan cache",
                "selection invalidation rescans the center immediately",
                "render guard rejects a cached block replaced by an unselected family variant");
        if (!new java.util.HashSet<>(beforeFailures).equals(expected)) {
            throw new AssertionError("unexpected old behavior failures: " + beforeFailures);
        }
        beforeFailures.forEach(failure -> System.out.println("REPRODUCED OLD FAILURE: " + failure));
        if (args.length < 2) return;
        List<?> afterFailures = runSource(Path.of(args[1]), "XrayScanFixtureAfter");
        if (!afterFailures.isEmpty()) throw new AssertionError("new behavior failures: " + afterFailures);
        System.out.println("PASS: all production scan regressions; before = " + args[0] + ", after = " + args[1]);
    }
}
