package com.extrarawstyle.veinminerplus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ChainEvents {
    // Work is deliberately bounded so one large blast cannot monopolize the server thread.
    private static final int SEARCH_CHECKS_PER_TICK = 16384;
    private static final int SEARCH_CHECKS_PER_CENTER = 256;
    private static final int BLOCK_BREAKS_PER_TICK = 8;

    private static final TagKey<Block> ORE_BLOCKS = TagKey.create(Registries.BLOCK,
            ResourceLocation.withDefaultNamespace("ores"));
    private static final TagKey<Block> FORGE_ORE_BLOCKS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("forge", "ores"));
    private static final TagKey<Block> COMMON_ORE_BLOCKS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("c", "ores"));
    private static final List<BlockPos> NORMAL_OFFSETS = createNormalOffsets();
    private static final Map<String, List<BlockPos>> BLAST_OFFSETS = new ConcurrentHashMap<>();
    private static final Map<UUID, ChainMode> PLAYER_MODES = new HashMap<>();
    private static final Set<UUID> HELD_KEYS = new HashSet<>();
    private static final Map<UUID, ChainJob> ACTIVE_JOBS = new HashMap<>();
    private static final Set<UUID> PENDING_JOBS = new HashSet<>();
    private static final Map<UUID, DropBuffer> PENDING_DROPS = new HashMap<>();
    private static final Map<UUID, BlockPos> PENDING_DROP_ORIGINS = new HashMap<>();
    private static final ThreadLocal<DropBuffer> CAPTURING_DROPS = new ThreadLocal<>();
    private static final Map<UUID, BreakFace> LAST_BREAK_FACES = new HashMap<>();
    private static final Map<UUID, HungerSnapshot> HUNGER_STARTS = new HashMap<>();
    private static final Map<UUID, HungerSnapshot> FINAL_HUNGER_RESTORES = new HashMap<>();

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        if (event.getEntity() instanceof ServerPlayer player) {
            ChainJob job = ACTIVE_JOBS.get(id);
            if (job != null) {
                job.finish();
            }
            DropBuffer pendingDrops = PENDING_DROPS.remove(id);
            if (pendingDrops != null) {
                pendingDrops.flush(player.serverLevel(), player);
            }
        }
        PLAYER_MODES.remove(id);
        HELD_KEYS.remove(id);
        PENDING_JOBS.remove(id);
        PENDING_DROP_ORIGINS.remove(id);
        LAST_BREAK_FACES.remove(id);
        HUNGER_STARTS.remove(id);
        HungerSnapshot finalRestore = FINAL_HUNGER_RESTORES.remove(id);
        if (event.getEntity() instanceof ServerPlayer player) {
            if (finalRestore != null) {
                finalRestore.restoreExact(player);
            }
            player.removeEffect(ModEffects.CHAIN_SATURATION.get());
        }
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
            LAST_BREAK_FACES.put(player.getUUID(), new BreakFace(event.getPos().immutable(), event.getFace()));
            if (!Config.CONSUME_HUNGER.get() && !player.isCreative()) {
                HUNGER_STARTS.put(player.getUUID(), HungerSnapshot.capture(player));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockBreakSnapshot(BlockEvent.BreakEvent event) {
        if (!event.isCanceled()
                && event.getPlayer() instanceof ServerPlayer player
                && HELD_KEYS.contains(player.getUUID())
                && !ACTIVE_JOBS.containsKey(player.getUUID())
                && !Config.CONSUME_HUNGER.get()
                && !player.isCreative()) {
            HUNGER_STARTS.put(player.getUUID(), HungerSnapshot.capture(player));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()
                || !(event.getPlayer() instanceof ServerPlayer player)
                || !HELD_KEYS.contains(player.getUUID())
                || ACTIVE_JOBS.containsKey(player.getUUID())
                || PENDING_JOBS.contains(player.getUUID())
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        BlockPos target = event.getPos().immutable();
        BlockState state = event.getState();
        ChainMode mode = PLAYER_MODES.getOrDefault(player.getUUID(), configuredDefaultMode());
        if (!isEligible(level, player, target, state, state.getBlock(), mode)) {
            return;
        }

        // Keep the chain bound to the tool stack that started it. Inventory
        // auto-refill can otherwise replace the stack between server ticks.
        ItemStack toolStack = player.getMainHandItem();

        Direction face = resolveBreakFace(player, target);
        DropBuffer drops = new DropBuffer();
        PENDING_DROPS.put(player.getUUID(), drops);
        PENDING_DROP_ORIGINS.put(player.getUUID(), target);
        PENDING_JOBS.add(player.getUUID());
        PENDING_JOBS.remove(player.getUUID());
        HungerSnapshot hungerBefore = HUNGER_STARTS.remove(player.getUUID());
        ChainJob job = new ChainJob(level, player, target, state.getBlock(), face, mode, drops, hungerBefore,
                toolStack);
        ACTIVE_JOBS.put(player.getUUID(), job);
        job.refreshHungerStatus();
        showProgress(player, 1);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)
                || event.loadedFromDisk()
                || !(event.getEntity() instanceof ItemEntity || event.getEntity() instanceof ExperienceOrb)) {
            return;
        }

        DropBuffer drops = CAPTURING_DROPS.get();
        if (drops != null) {
            captureEntity(event, drops);
            return;
        }

        for (Map.Entry<UUID, DropBuffer> entry : PENDING_DROPS.entrySet()) {
            BlockPos origin = PENDING_DROP_ORIGINS.get(entry.getKey());
            if (origin != null && event.getEntity().blockPosition().distSqr(origin) <= 9.0D) {
                captureEntity(event, entry.getValue());
                return;
            }
        }
    }

    private static void captureEntity(EntityJoinLevelEvent event, DropBuffer drops) {
        if (event.getEntity() instanceof ItemEntity item) {
            drops.add(item.getItem());
            event.setCanceled(true);
        } else if (event.getEntity() instanceof ExperienceOrb orb) {
            drops.addExperience(orb.getValue());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (ChainJob job : new ArrayList<>(ACTIVE_JOBS.values())) {
            job.tick();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        ChainJob job = ACTIVE_JOBS.get(player.getUUID());
        if (job != null && job.restoreHungerAtTickEnd()) {
            return;
        }

        HungerSnapshot finalRestore = FINAL_HUNGER_RESTORES.remove(player.getUUID());
        if (finalRestore != null) {
            finalRestore.restoreExact(player);
            player.removeEffect(ModEffects.CHAIN_SATURATION.get());
        }
    }

    static void setKeyHeld(Player player, boolean held) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (held) {
            HELD_KEYS.add(serverPlayer.getUUID());
            if (!Config.CONSUME_HUNGER.get() && !serverPlayer.isCreative()) {
                HUNGER_STARTS.put(serverPlayer.getUUID(), HungerSnapshot.capture(serverPlayer));
            }
        } else {
            HELD_KEYS.remove(serverPlayer.getUUID());
            HUNGER_STARTS.remove(serverPlayer.getUUID());
        }
    }

    static void setMode(Player player, int ordinal) {
        if (player instanceof ServerPlayer serverPlayer) {
            PLAYER_MODES.put(serverPlayer.getUUID(), ChainMode.fromOrdinal(ordinal));
        }
    }

    private static boolean isEligible(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state,
            Block targetBlock, ChainMode mode) {
        return isEligible(level, player, pos, state, targetBlock, mode, configuredWhitelist());
    }

    private static boolean isEligible(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state,
            Block targetBlock, ChainMode mode, Set<String> whitelist) {
        if (state.isAir() || !level.mayInteract(player, pos)) {
            return false;
        }
        // The whitelist scopes blast searches; normal and area modes already
        // restrict candidates to the block that started the chain.
        if (mode.isBlast() && !isWhitelisted(state, whitelist)) {
            return false;
        }

        boolean matches = switch (mode) {
            case BLAST_ANY -> true;
            case BLAST_ORES -> isOre(state);
            case BLAST_LOGS -> state.is(BlockTags.LOGS);
            default -> state.getBlock() == targetBlock;
        };
        return matches
                && state.getDestroySpeed(level, pos) >= 0.0F
                && !isContainer(level, pos, state)
                && (player.isCreative() || ForgeHooks.isCorrectToolForDrops(state, player));
    }

    private static Set<String> configuredWhitelist() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(
                Config.effectiveWhitelist(Config.BLOCK_WHITELIST.get())));
    }

    private static boolean isWhitelisted(BlockState state, Set<String> whitelist) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (whitelist.isEmpty()) {
            return true;
        }
        if (id == null) {
            return false;
        }
        for (String rule : whitelist) {
            if (rule.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(rule.substring(1));
                if (tagId != null && state.is(TagKey.create(Registries.BLOCK, tagId))) {
                    return true;
                }
            } else if (rule.indexOf('*') >= 0) {
                String candidate = rule.indexOf(':') >= 0 ? id.toString() : id.getPath();
                if (wildcardMatches(rule, candidate)) {
                    return true;
                }
            } else if (rule.equals(id.toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean wildcardMatches(String pattern, String value) {
        int patternIndex = 0;
        int valueIndex = 0;
        int starIndex = -1;
        int retryIndex = 0;
        while (valueIndex < value.length()) {
            if (patternIndex < pattern.length()
                    && pattern.charAt(patternIndex) == value.charAt(valueIndex)) {
                patternIndex++;
                valueIndex++;
            } else if (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
                starIndex = patternIndex++;
                retryIndex = valueIndex;
            } else if (starIndex >= 0) {
                patternIndex = starIndex + 1;
                valueIndex = ++retryIndex;
            } else {
                return false;
            }
        }
        while (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
            patternIndex++;
        }
        return patternIndex == pattern.length();
    }

    private static boolean isContainer(ServerLevel level, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof Container
                || state.getMenuProvider(level, pos) != null
                || blockEntity != null && blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
    }

    private static boolean isOre(BlockState state) {
        if (state.is(ORE_BLOCKS) || state.is(FORGE_ORE_BLOCKS) || state.is(COMMON_ORE_BLOCKS)) {
            return true;
        }

        // Some mod packs do not add their ores to the shared ore tags.
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && id.getPath().endsWith("_ore");
    }

    private static boolean breakOne(ServerLevel level, ServerPlayer player, BlockPos pos, Block targetBlock,
            ChainMode mode, Set<String> whitelist) {
        BlockState state = level.getBlockState(pos);
        if (!isEligible(level, player, pos, state, targetBlock, mode, whitelist)) {
            return false;
        }
        // Use the same server-side entry point as a real player break. This keeps
        // BlockEvent, drops, tool damage, block entities, and client updates in sync.
        ChainJob job = ACTIVE_JOBS.get(player.getUUID());
        DropBuffer previous = CAPTURING_DROPS.get();
        if (job != null) {
            CAPTURING_DROPS.set(job.drops);
        }
        try {
            boolean consumeHunger = Config.CONSUME_HUNGER.get() && !player.isCreative();
            float exhaustionBefore = consumeHunger ? 0.0F : player.getFoodData().getExhaustionLevel();
            boolean broken = player.gameMode.destroyBlock(pos);
            if (broken && consumeHunger) {
                player.getFoodData().addExhaustion(0.005F);
            } else if (broken && !player.isCreative()) {
                float exhaustionAfter = player.getFoodData().getExhaustionLevel();
                if (exhaustionAfter > exhaustionBefore) {
                    player.getFoodData().setExhaustion(exhaustionBefore);
                }
            }
            if (broken && job != null) {
                job.refreshHungerStatus();
            }
            return broken;
        } finally {
            if (previous == null) {
                CAPTURING_DROPS.remove();
            } else {
                CAPTURING_DROPS.set(previous);
            }
        }
    }

    private static BlockState getBlockStateForSearch(ServerLevel level, BlockPos pos) {
        // Force a full chunk read so blast searches can cross the loaded-area boundary.
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        return level.getBlockState(pos);
    }

    private static BlockPos areaOffset(BlockPos start, Direction face, int first, int second) {
        return switch (face.getAxis()) {
            case X -> start.offset(0, first, second);
            case Y -> start.offset(first, 0, second);
            case Z -> start.offset(first, second, 0);
        };
    }

    private static Direction resolveBreakFace(ServerPlayer player, BlockPos target) {
        BreakFace breakFace = LAST_BREAK_FACES.remove(player.getUUID());
        if (breakFace != null && breakFace.pos().equals(target) && breakFace.face() != null) {
            return breakFace.face();
        }

        // BreakEvent does not carry the hit face. Derive a stable fallback
        // from the player's current view instead of defaulting to UP, which
        // would make an area chain tunnel downward from the origin.
        var look = player.getLookAngle();
        return Direction.getNearest(look.x, look.y, look.z).getOpposite();
    }

    private static List<BlockPos> createNormalOffsets() {
        List<BlockPos> offsets = new ArrayList<>(26);
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || y != 0 || z != 0) {
                        offsets.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return Collections.unmodifiableList(offsets);
    }

    private static List<BlockPos> blastOffsets(int distance, boolean manhattan) {
        String key = distance + ":" + manhattan;
        return BLAST_OFFSETS.computeIfAbsent(key, ignored -> createBlastOffsets(distance, manhattan));
    }

    private static void refreshHungerStatusEffect(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(ModEffects.CHAIN_SATURATION.get(), 40, 255, false, true, true));
    }

    private static List<BlockPos> createBlastOffsets(int distance, boolean manhattan) {
        List<BlockPos> offsets = new ArrayList<>();
        for (int x = -distance; x <= distance; x++) {
            for (int y = -distance; y <= distance; y++) {
                for (int z = -distance; z <= distance; z++) {
                    long squaredDistance = (long) x * x + (long) y * y + (long) z * z;
                    int manhattanDistance = Math.abs(x) + Math.abs(y) + Math.abs(z);
                    if ((x != 0 || y != 0 || z != 0)
                            && (manhattan ? manhattanDistance <= distance : squaredDistance <= (long) distance * distance)) {
                        offsets.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        offsets.sort(Comparator
                .comparingLong((BlockPos pos) -> (long) pos.getX() * pos.getX()
                        + (long) pos.getY() * pos.getY()
                        + (long) pos.getZ() * pos.getZ())
                .thenComparingInt(pos -> Math.abs(pos.getY()))
                .thenComparingInt(pos -> Math.abs(pos.getX()))
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return Collections.unmodifiableList(offsets);
    }

    private static void showProgress(ServerPlayer player, int count) {
        player.displayClientMessage(Component.translatable("message.veinminerplus.progress", count), true);
    }

    private static final class DropBuffer {
        private final List<ItemStack> items = new ArrayList<>();
        private int experience;

        private void add(ItemStack stack) {
            if (stack.isEmpty()) {
                return;
            }

            int remaining = stack.getCount();
            for (ItemStack existing : items) {
                if (!ItemStack.isSameItemSameTags(existing, stack)) {
                    continue;
                }
                int space = existing.getMaxStackSize() - existing.getCount();
                if (space <= 0) {
                    continue;
                }

                int amount = Math.min(space, remaining);
                existing.setCount(existing.getCount() + amount);
                remaining -= amount;
                if (remaining == 0) {
                    return;
                }
            }

            int maxStackSize = stack.getMaxStackSize();
            while (remaining > 0) {
                int amount = Math.min(maxStackSize, remaining);
                items.add(stack.copyWithCount(amount));
                remaining -= amount;
            }
        }

        private void addExperience(int amount) {
            experience += amount;
        }

        private void flush(ServerLevel level, ServerPlayer player) {
            if (!level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
                clear();
                return;
            }

            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();
            for (ItemStack stack : items) {
                ItemEntity item = new ItemEntity(level, x, y, z, stack);
                item.setDefaultPickUpDelay();
                item.setDeltaMovement(0.0D, 0.0D, 0.0D);
                level.addFreshEntity(item);
            }
            if (experience > 0) {
                ExperienceOrb.award(level, player.position(), experience);
            }
            clear();
        }

        private void clear() {
            items.clear();
            experience = 0;
        }
    }

    private static final class ChainJob {
        private final ServerLevel level;
        private final ServerPlayer player;
        private final BlockPos origin;
        private final Block targetBlock;
        private final Direction face;
        private final ChainMode mode;
        private final ItemStack toolStack;
        private final Set<BlockPos> examined = new HashSet<>();
        private final Deque<SearchNode> frontier = new ArrayDeque<>();
        private final Deque<BlockPos> sparseCenters = new ArrayDeque<>();
        private final PriorityQueue<BlockPos> sparseTargets;
        private final Map<Long, List<BlockPos>> sparseChunkMatches = new HashMap<>();
        private final Map<Long, LevelChunk> loadedChunks = new HashMap<>();
        private List<BlockPos> graphOffsets;
        private final int totalLimit;
        private final int areaDepthLimit;
        private final boolean sparseBlast;
        private final boolean blastManhattan;
        private final DropBuffer drops;
        private final Set<String> whitelist;
        private int blastDistance;
        private boolean lowTpsWarned;
        private final HungerSnapshot hungerSnapshot;
        private boolean finished;
        private int brokenCount = 1;
        private int areaDepth = 1;
        private int areaIndex;

        private ChainJob(ServerLevel level, ServerPlayer player, BlockPos origin, Block targetBlock,
                Direction face, ChainMode mode, DropBuffer drops, HungerSnapshot hungerBefore,
                ItemStack toolStack) {
            this.level = level;
            this.player = player;
            this.origin = origin;
            this.targetBlock = targetBlock;
            this.face = face;
            this.mode = mode;
            this.drops = drops;
            this.toolStack = toolStack;
            this.whitelist = configuredWhitelist();
            this.hungerSnapshot = !Config.CONSUME_HUNGER.get() && !player.isCreative()
                    ? hungerBefore == null ? HungerSnapshot.capture(player) : hungerBefore
                    : null;
            this.totalLimit = mode.isBlast() ? Config.MAX_BLAST_BLOCKS.get() : Config.MAX_NORMAL_BLOCKS.get();
            this.areaDepthLimit = Config.MAX_NORMAL_BLOCKS.get();
            this.blastDistance = Config.BLAST_SEARCH_DISTANCE.get();
            this.blastManhattan = Config.BLAST_MANHATTAN.get();
            this.graphOffsets = mode.isBlast()
                    ? blastOffsets(blastDistance, blastManhattan)
                    : NORMAL_OFFSETS;
            BlockState targetState = targetBlock.defaultBlockState();
            this.sparseBlast = mode == ChainMode.BLAST_ORES
                    || mode == ChainMode.BLAST_LOGS
                    || mode == ChainMode.BLAST_SAME && (isOre(targetState) || targetState.is(BlockTags.LOGS));
            this.sparseTargets = new PriorityQueue<>(Comparator
                    .comparingLong((BlockPos pos) -> squaredDistance(origin, pos))
                    .thenComparingInt(BlockPos::getY)
                    .thenComparingInt(BlockPos::getX)
                    .thenComparingInt(BlockPos::getZ));

            examined.add(origin);
            loadedChunks.put(chunkKey(origin), level.getChunk(origin.getX() >> 4, origin.getZ() >> 4));
            if (!mode.isArea()) {
                if (sparseBlast) {
                    sparseCenters.addLast(origin);
                } else {
                    frontier.addLast(new SearchNode(origin, 0));
                }
            }
        }

        private void tick() {
            PENDING_DROPS.remove(player.getUUID(), drops);
            PENDING_DROP_ORIGINS.remove(player.getUUID(), origin);
            if (!isToolUnchanged()) {
                finish();
                return;
            }
            if (level.getBlockState(origin).is(targetBlock)) {
                finish();
                return;
            }
            if (!HELD_KEYS.contains(player.getUUID()) || player.isRemoved() || player.isSpectator()
                    || player.serverLevel() != level) {
                finish();
                return;
            }

            if (mode.isBlast()) {
                adjustBlastRadiusForTps();
            }

            if (mode.isArea()) {
                tickArea();
            } else if (sparseBlast) {
                tickSparseBlast();
            } else {
                tickGraph();
            }
        }

        private boolean isToolUnchanged() {
            return player.getMainHandItem() == toolStack;
        }

        private void tickGraph() {
            int checks = 0;
            int breaks = 0;
            int breakLimit = mode.isBlast() ? Config.MAX_BLAST_BLOCKS_PER_TICK.get()
                    : mode == ChainMode.NORMAL ? Config.MAX_NORMAL_BLOCKS_PER_TICK.get()
                    : BLOCK_BREAKS_PER_TICK;
            while (checks < SEARCH_CHECKS_PER_TICK && breaks < breakLimit
                    && !frontier.isEmpty() && brokenCount < totalLimit
                    && HELD_KEYS.contains(player.getUUID()) && isToolUnchanged()) {
                SearchNode node = frontier.removeFirst();
                int centerChecks = 0;
                while (centerChecks < SEARCH_CHECKS_PER_CENTER
                        && checks < SEARCH_CHECKS_PER_TICK
                        && breaks < breakLimit
                        && node.nextOffset() < graphOffsets.size()) {
                    BlockPos offset = graphOffsets.get(node.nextOffset());
                    node.advance();
                    BlockPos candidate = node.position().offset(offset.getX(), offset.getY(), offset.getZ());
                    checks++;
                    centerChecks++;
                    if (!examined.add(candidate) || !level.isInWorldBounds(candidate)) {
                        continue;
                    }

                    BlockState state = getBlockStateForSearch(level, candidate, loadedChunks);
                    if (isEligible(level, player, candidate, state, targetBlock, mode, whitelist)
                            && breakOne(level, player, candidate, targetBlock, mode, whitelist)) {
                        brokenCount++;
                        breaks++;
                        frontier.addLast(new SearchNode(candidate, 0));
                    }
                }
                if (node.nextOffset() < graphOffsets.size()) {
                    frontier.addLast(node);
                }
            }

            if (breaks > 0) {
                showProgress(player, brokenCount);
            }

            if (!isToolUnchanged() || !HELD_KEYS.contains(player.getUUID()) || frontier.isEmpty()
                    || brokenCount >= totalLimit) {
                finish();
            }
        }

        private void tickArea() {
            int size = mode == ChainMode.AREA_1X1 ? 1 : 3;
            int planeSize = size * size;
            int breaks = 0;
            while (breaks < BLOCK_BREAKS_PER_TICK && areaDepth <= areaDepthLimit
                    && HELD_KEYS.contains(player.getUUID()) && isToolUnchanged()) {
                if (areaIndex >= planeSize) {
                    areaDepth++;
                    areaIndex = 0;
                    continue;
                }

                int startOffset = -(size / 2);
                int first = startOffset + areaIndex / size;
                int second = startOffset + areaIndex % size;
                areaIndex++;
                // The hit face points back toward the player. Advance into the block instead.
                BlockPos candidate = areaOffset(origin, face, first, second).relative(face.getOpposite(), areaDepth);
                if (!level.isInWorldBounds(candidate) || !examined.add(candidate)) {
                    continue;
                }

                BlockState state = getBlockStateForSearch(level, candidate, loadedChunks);
                if (isEligible(level, player, candidate, state, targetBlock, mode, whitelist)
                        && breakOne(level, player, candidate, targetBlock, mode, whitelist)) {
                    brokenCount++;
                    breaks++;
                }
            }

            if (breaks > 0) {
                showProgress(player, brokenCount);
            }

            if (!isToolUnchanged() || !HELD_KEYS.contains(player.getUUID()) || areaDepth > areaDepthLimit) {
                finish();
            }
        }

        private void tickSparseBlast() {
            int breaks = 0;
            int scannedCenters = 0;
            int breakLimit = Config.MAX_BLAST_BLOCKS_PER_TICK.get();
            int centerLimit = Math.max(32, breakLimit);
            while (breaks < breakLimit && brokenCount < totalLimit && HELD_KEYS.contains(player.getUUID())
                    && isToolUnchanged()) {
                while (sparseTargets.isEmpty() && !sparseCenters.isEmpty() && scannedCenters < centerLimit) {
                    scanSparseCenter(sparseCenters.removeFirst());
                    scannedCenters++;
                }
                if (sparseTargets.isEmpty()) {
                    break;
                }

                BlockPos candidate = sparseTargets.poll();
                BlockState state = getBlockStateForSearch(level, candidate, loadedChunks);
                if (isEligible(level, player, candidate, state, targetBlock, mode, whitelist)
                        && breakOne(level, player, candidate, targetBlock, mode, whitelist)) {
                    brokenCount++;
                    breaks++;
                    sparseCenters.addLast(candidate);
                }
            }

            if (breaks > 0) {
                showProgress(player, brokenCount);
            }
            if (!isToolUnchanged() || !HELD_KEYS.contains(player.getUUID())
                    || sparseTargets.isEmpty() && sparseCenters.isEmpty()
                    || brokenCount >= totalLimit) {
                finish();
            }
        }

        private void scanSparseCenter(BlockPos center) {
            int distance = blastDistance;
            int minChunkX = (center.getX() - distance) >> 4;
            int maxChunkX = (center.getX() + distance) >> 4;
            int minChunkZ = (center.getZ() - distance) >> 4;
            int maxChunkZ = (center.getZ() + distance) >> 4;

            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    long key = chunkKey(chunkX, chunkZ);
                    LevelChunk chunk = loadedChunks.get(key);
                    if (chunk == null) {
                        chunk = level.getChunk(chunkX, chunkZ);
                        loadedChunks.put(key, chunk);
                    }

                    List<BlockPos> matches = sparseChunkMatches.get(key);
                    if (matches == null) {
                        matches = new ArrayList<>();
                        List<BlockPos> positions = matches;
                        chunk.findBlocks(this::matchesSparseState,
                                (pos, state) -> positions.add(pos.immutable()));
                        sparseChunkMatches.put(key, matches);
                    }

                    for (BlockPos pos : matches) {
                        if (withinBlastDistance(center, pos, distance, blastManhattan) && examined.add(pos)) {
                            sparseTargets.add(pos);
                        }
                    }
                }
            }
        }

        private void adjustBlastRadiusForTps() {
            double tps = currentTps(level);
            if (lowTpsWarned || tps >= Config.BLAST_LOW_TPS_THRESHOLD.get()) {
                return;
            }

            lowTpsWarned = true;
            if (Config.BLAST_AUTO_REDUCE_RADIUS.get() && blastDistance > 3) {
                int oldDistance = blastDistance;
                blastDistance = Math.max(3, blastDistance / 2);
                graphOffsets = blastOffsets(blastDistance, blastManhattan);
                sparseTargets.clear();
                player.displayClientMessage(Component.translatable("message.veinminerplus.blast_radius_reduced",
                        String.format("%.1f", tps), oldDistance, blastDistance), true);
            } else {
                player.displayClientMessage(Component.translatable("message.veinminerplus.blast_radius_too_large",
                        String.format("%.1f", tps), blastDistance), true);
            }
        }

        private boolean matchesSparseState(BlockState state) {
            return isWhitelisted(state, whitelist) && switch (mode) {
                case BLAST_ORES -> isOre(state);
                case BLAST_LOGS -> state.is(BlockTags.LOGS);
                default -> state.is(targetBlock);
            };
        }

        private void finish() {
            if (finished) {
                return;
            }
            finished = true;
            if (hungerSnapshot != null) {
                hungerSnapshot.restoreExact(player);
                FINAL_HUNGER_RESTORES.put(player.getUUID(), hungerSnapshot);
            }
            drops.flush(player.serverLevel(), player);
            ACTIVE_JOBS.remove(player.getUUID(), this);
        }

        private boolean restoreHungerAtTickEnd() {
            if (hungerSnapshot == null) {
                return false;
            }
            hungerSnapshot.restoreExact(player);
            refreshHungerStatus();
            return true;
        }

        private void refreshHungerStatus() {
            if (hungerSnapshot != null) {
                refreshHungerStatusEffect(player);
            }
        }

    }

    private static BlockState getBlockStateForSearch(ServerLevel level, BlockPos pos,
            Map<Long, LevelChunk> loadedChunks) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        LevelChunk chunk = loadedChunks.computeIfAbsent(chunkKey(chunkX, chunkZ),
                key -> level.getChunk(chunkX, chunkZ));
        return chunk.getBlockState(pos);
    }

    private static long chunkKey(BlockPos pos) {
        return chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static long squaredDistance(BlockPos first, BlockPos second) {
        long dx = (long) first.getX() - second.getX();
        long dy = (long) first.getY() - second.getY();
        long dz = (long) first.getZ() - second.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean withinBlastDistance(BlockPos first, BlockPos second, int distance, boolean manhattan) {
        if (manhattan) {
            return Math.abs(first.getX() - second.getX())
                    + Math.abs(first.getY() - second.getY())
                    + Math.abs(first.getZ() - second.getZ()) <= distance;
        }
        return squaredDistance(first, second) <= (long) distance * distance;
    }

    static ChainMode getMode(Player player) {
        return PLAYER_MODES.getOrDefault(player.getUUID(), configuredDefaultMode());
    }

    private static ChainMode configuredDefaultMode() {
        return ChainMode.fromOrdinal(Config.DEFAULT_MODE.get());
    }

    private static double currentTps(ServerLevel level) {
        float millis = level.getServer().getAverageTickTime();
        return millis <= 0.0F ? 20.0D : Math.min(20.0D, 1000.0D / millis);
    }

    private static final class SearchNode {
        private final BlockPos position;
        private int nextOffset;

        private SearchNode(BlockPos position, int nextOffset) {
            this.position = position;
            this.nextOffset = nextOffset;
        }

        private BlockPos position() {
            return position;
        }

        private int nextOffset() {
            return nextOffset;
        }

        private void advance() {
            nextOffset++;
        }
    }

    private record BreakFace(BlockPos pos, Direction face) {
    }

    private record HungerSnapshot(int foodLevel, float saturation, float exhaustion) {
        private static HungerSnapshot capture(ServerPlayer player) {
            var food = player.getFoodData();
            return new HungerSnapshot(food.getFoodLevel(), food.getSaturationLevel(), food.getExhaustionLevel());
        }

        private void restoreExact(ServerPlayer player) {
            var food = player.getFoodData();
            food.setFoodLevel(foodLevel);
            food.setSaturation(saturation);
            food.setExhaustion(exhaustion);
        }
    }
}
