import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

// Runs the production handler with small interaction fixtures, without launching Minecraft.
public final class RightClickRegression {
    private static final String FIXTURE = """
        import java.util.*;
        import java.lang.annotation.*;
        public class $CLASS$ {
            enum EventPriority { HIGHEST, HIGH, NORMAL, LOW, LOWEST }
            @Retention(RetentionPolicy.RUNTIME)
            @interface SubscribeEvent { EventPriority priority(); }
            enum ChainMode { NORMAL, USE_BLOCK, XRAY }
            enum InteractionHand { MAIN_HAND }
            enum Direction { UP }
            enum Item { ALLOY, COPPER, AXE, PASS }
            enum Block { LOG, CASING, OTHER }
            enum InteractionResult {
                PASS, FAIL, SUCCESS;
                boolean consumesAction() { return this == SUCCESS; }
            }
            static final class BlockTags { static final Object LOGS = new Object(); }
            static final class BlockPos { BlockPos immutable() { return this; } }
            static final class BlockHitResult {}
            static final class BlockState {
                final Block block;
                BlockState(Block block) { this.block = block; }
                boolean isAir() { return false; }
                boolean is(Object tag) { return block == Block.LOG; }
                Block getBlock() { return block; }
            }
            static final class ItemStack {
                final Item item;
                int count = 8;
                ItemStack(Item item) { this.item = item; }
                boolean isEmpty() { return count == 0; }
                Item getItem() { return item; }
            }
            static final class ServerLevel {
                BlockState state = new BlockState(Block.LOG);
                boolean mayInteract(ServerPlayer player, BlockPos pos) { return true; }
                BlockState getBlockState(BlockPos pos) { return state; }
            }
            static final class ServerPlayer {
                final UUID id = UUID.randomUUID();
                final ServerLevel level = new ServerLevel();
                final GameMode gameMode = new GameMode();
                ItemStack stack;
                ServerPlayer(Item item) { stack = new ItemStack(item); }
                UUID getUUID() { return id; }
                ServerLevel serverLevel() { return level; }
                ItemStack getItemInHand(InteractionHand hand) { return stack; }
            }
            static final class PlayerInteractEvent {
                static final class RightClickBlock {
                    final ServerPlayer player;
                    boolean canceled;
                    InteractionResult result = InteractionResult.PASS;
                    RightClickBlock(ServerPlayer player) { this.player = player; }
                    Object getEntity() { return player; }
                    boolean isCanceled() { return canceled; }
                    ItemStack getItemStack() { return player.stack; }
                    BlockPos getPos() { return new BlockPos(); }
                    Direction getFace() { return Direction.UP; }
                    InteractionHand getHand() { return InteractionHand.MAIN_HAND; }
                    BlockHitResult getHitVec() { return new BlockHitResult(); }
                    void setCanceled(boolean value) { canceled = value; }
                    void setCancellationResult(InteractionResult value) { result = value; }
                }
            }
            static final class RightClickJob {
                final Block targetBlock;
                final Item item;
                RightClickJob(ServerLevel level, ServerPlayer player, BlockPos pos, Block block,
                        InteractionHand hand, Direction face, Item item) {
                    this.targetBlock = block;
                    this.item = item;
                }
            }
            static final Map<UUID, Object> ACTIVE_JOBS = new HashMap<>();
            static final Map<UUID, RightClickJob> ACTIVE_RIGHT_CLICK_JOBS = new HashMap<>();
            static final Map<UUID, ChainMode> PLAYER_MODES = new HashMap<>();
            static final Set<UUID> RIGHT_CLICK_GUARD = new HashSet<>();
            static final Set<UUID> HELD_KEYS = new HashSet<>();
            static ChainMode configuredDefaultMode() { return ChainMode.NORMAL; }
            static final $CLASS$ handler = new $CLASS$();
            static EventPriority priority;
            static boolean protectedBlock;
            static int applications;
            static int depth;
            static int maxDepth;

            $HANDLER$

            static final class GameMode {
                InteractionResult useItemOn(ServerPlayer player, ServerLevel level, ItemStack stack,
                        InteractionHand hand, BlockHitResult hit) {
                    depth++;
                    maxDepth = Math.max(maxDepth, depth);
                    if (depth > 2) throw new AssertionError("recursive interaction");
                    try {
                        var event = new PlayerInteractEvent.RightClickBlock(player);
                        for (EventPriority step : EventPriority.values()) {
                            if (!event.canceled && step == priority) handler.onRightClickBlock(event);
                            if (!event.canceled && step == EventPriority.HIGH && protectedBlock) {
                                event.setCanceled(true);
                                event.setCancellationResult(InteractionResult.FAIL);
                            }
                            // Create's NORMAL-priority recipe handler consumes and cancels the event.
                            if (!event.canceled && step == EventPriority.NORMAL
                                    && level.state.block == Block.LOG
                                    && (stack.item == Item.ALLOY || stack.item == Item.COPPER)) {
                                apply(level, stack);
                                event.setCanceled(true);
                                event.setCancellationResult(InteractionResult.SUCCESS);
                            }
                        }
                        if (event.canceled) return event.result;
                        if (stack.item == Item.AXE && level.state.block == Block.LOG) {
                            apply(level, stack);
                            return InteractionResult.SUCCESS;
                        }
                        return InteractionResult.PASS;
                    } finally {
                        depth--;
                    }
                }
            }
            static void apply(ServerLevel level, ItemStack stack) {
                applications++;
                stack.count--;
                level.state = new BlockState(Block.CASING);
            }
            static ServerPlayer reset(Item item) {
                ACTIVE_RIGHT_CLICK_JOBS.clear();
                ACTIVE_JOBS.clear();
                PLAYER_MODES.clear();
                HELD_KEYS.clear();
                RIGHT_CLICK_GUARD.clear();
                protectedBlock = false;
                applications = depth = maxDepth = 0;
                ServerPlayer player = new ServerPlayer(item);
                HELD_KEYS.add(player.id);
                return player;
            }
            static void click(ServerPlayer player) {
                player.gameMode.useItemOn(player, player.level, player.stack, InteractionHand.MAIN_HAND,
                        new BlockHitResult());
                if (!RIGHT_CLICK_GUARD.isEmpty()) throw new AssertionError("guard leaked");
            }
            static void check(boolean condition, String message) {
                if (!condition) throw new AssertionError(message);
                System.out.println("PASS: " + message);
            }
            public static void run(boolean old) throws Exception {
                priority = $CLASS$.class.getDeclaredMethod("onRightClickBlock",
                        PlayerInteractEvent.RightClickBlock.class).getAnnotation(SubscribeEvent.class).priority();
                for (Item material : List.of(Item.ALLOY, Item.COPPER)) {
                    ServerPlayer player = reset(material);
                    click(player);
                    RightClickJob job = ACTIVE_RIGHT_CLICK_JOBS.get(player.id);
                    if (old) {
                        check(job == null && applications == 1, "old handler misses Create " + material);
                    } else {
                        check(job != null && job.targetBlock == Block.LOG && job.item == material,
                                "Create " + material + " queues original stripped logs");
                        check(applications == 1 && player.stack.count == 7 && maxDepth == 2,
                                material + " is applied once with guarded reentry");
                    }
                }
                if (old) return;
                ServerPlayer player = reset(Item.ALLOY);
                protectedBlock = true;
                click(player);
                check(applications == 0 && player.stack.count == 8 && ACTIVE_RIGHT_CLICK_JOBS.isEmpty(),
                        "protection cancellation prevents conversion and chaining");
                player = reset(Item.COPPER);
                HELD_KEYS.clear();
                click(player);
                check(applications == 1 && ACTIVE_RIGHT_CLICK_JOBS.isEmpty(), "no chain without held key");
                player = reset(Item.AXE);
                click(player);
                check(applications == 1 && ACTIVE_RIGHT_CLICK_JOBS.containsKey(player.id),
                        "ordinary item interaction still starts a chain");
                player = reset(Item.PASS);
                click(player);
                check(applications == 0 && ACTIVE_RIGHT_CLICK_JOBS.isEmpty(), "PASS does not start a chain");
                player = reset(Item.ALLOY);
                PLAYER_MODES.put(player.id, ChainMode.XRAY);
                click(player);
                check(applications == 1 && ACTIVE_RIGHT_CLICK_JOBS.isEmpty(), "xray mode does not chain use");
                player = reset(Item.ALLOY);
                ACTIVE_JOBS.put(player.id, new Object());
                click(player);
                check(applications == 1 && ACTIVE_RIGHT_CLICK_JOBS.isEmpty(), "active mining job prevents new use job");
            }
        }
        """;

    private static SimpleJavaFileObject source(String name, String text) {
        return new SimpleJavaFileObject(URI.create("string:///" + name + ".java"), JavaFileObject.Kind.SOURCE) {
            public CharSequence getCharContent(boolean ignoreEncodingErrors) { return text; }
        };
    }

    public static void main(String[] args) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        Path sourcePath = Path.of(args[0]);
        String[] handler = {null};
        JavacTask parser = (JavacTask) compiler.getTask(null, null, null, List.of("-proc:none"), null,
                List.of(source("ChainEvents", Files.readString(sourcePath))));
        for (var tree : parser.parse()) {
            new TreeScanner<Void, Void>() {
                public Void visitMethod(MethodTree method, Void ignored) {
                    if (method.getName().contentEquals("onRightClickBlock")) handler[0] = method.toString();
                    return null;
                }
            }.scan(tree, null);
        }
        if (handler[0] == null) throw new AssertionError("production handler not found");
        for (boolean old : List.of(true, false)) {
            String name = old ? "RightClickFixtureOld" : "RightClickFixture";
            String method = old ? handler[0].replace("EventPriority.HIGHEST", "EventPriority.LOWEST") : handler[0];
            Path output = Path.of(".compile-check", "right-click-regression");
            Files.createDirectories(output);
            String code = FIXTURE.replace("$CLASS$", name).replace("$HANDLER$", method);
            if (!compiler.getTask(null, null, null, List.of("-d", output.toString()), null,
                    List.of(source(name, code))).call()) throw new AssertionError("fixture compilation failed");
            try (var loader = new URLClassLoader(new java.net.URL[] {output.toUri().toURL()})) {
                loader.loadClass(name).getMethod("run", boolean.class).invoke(null, old);
            }
        }
        System.out.println("Checked production handler: " + sourcePath);
    }
}
