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
import java.util.Locale;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockNewLog;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.oredict.OreDictionary;

public final class ChainEvents {
    private static final int SEARCH_CHECKS_PER_TICK = 16384;
    private static final int SEARCH_CHECKS_PER_CENTER = 256;
    private static final int SEARCH_CHUNK_LOADS_PER_TICK = 1;
    private static final int CHAIN_FAIL_NOTICE_COOLDOWN = 40;
    // Loading a missing chunk can synchronously generate terrain, so keep it to one
    // chunk per server tick while sparse blast cursors walk outward.
    private static final int SPARSE_CHUNK_LOADS_PER_TICK = 1;
    // Ores that differ only by host stone are the same ore to a player, so a vein
    // that crosses the stone/deepslate boundary must still chain as one deposit.
    // These host names are stripped from a block id before families are compared.
    private static final String ORE_SUFFIX = "_ore";
    private static final String[] ORE_HOST_STONES = { "deepslate", "slate", "stone", "endstone", "netherrack",
            "nether", "end", "other", "blackstone", "basalt", "tuff", "granite", "diorite", "andesite", "marble",
            "limestone" };

    private static final List<BlockPos> NORMAL_OFFSETS = createNormalOffsets();
    // Pure memoisation of oreFamilyKey; bounded by the size of the block registry.
    private static final Map<Block, String> ORE_FAMILY_KEYS = new HashMap<Block, String>();
    private static final Map<UUID, ChainFailNotice> CHAIN_FAIL_NOTICES = new HashMap<UUID, ChainFailNotice>();
    private static final Map<UUID, ChainMode> PLAYER_MODES = new HashMap<UUID, ChainMode>();
    private static final Set<UUID> HELD_KEYS = new HashSet<UUID>();
    private static final Map<UUID, ChainJob> ACTIVE_JOBS = new HashMap<UUID, ChainJob>();
    private static final Map<UUID, RightClickJob> ACTIVE_RIGHT_CLICK_JOBS = new HashMap<UUID, RightClickJob>();
    private static final Set<UUID> RIGHT_CLICK_GUARD = new HashSet<UUID>();
    private static final Map<UUID, DropBuffer> PENDING_DROPS = new HashMap<UUID, DropBuffer>();
    private static final Map<UUID, BlockPos> PENDING_ORIGINS = new HashMap<UUID, BlockPos>();
    private static final Map<UUID, BreakFace> LAST_FACES = new HashMap<UUID, BreakFace>();
    private static final Map<UUID, HungerSnapshot> HUNGER_STARTS = new HashMap<UUID, HungerSnapshot>();
    private static final ThreadLocal<DropCapture> CAPTURING_DROPS = new ThreadLocal<DropCapture>();

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.player.getUniqueID();
        ChainJob job = ACTIVE_JOBS.remove(id);
        if (job != null) {
            job.finish();
        }
        RightClickJob rightClickJob = ACTIVE_RIGHT_CLICK_JOBS.remove(id);
        if (rightClickJob != null) {
            rightClickJob.finish();
        }
        DropBuffer drops = PENDING_DROPS.remove(id);
        if (drops != null && event.player.world instanceof WorldServer) {
            drops.flush((WorldServer) event.player.world, (EntityPlayerMP) event.player);
        }
        PLAYER_MODES.remove(id);
        HELD_KEYS.remove(id);
        PENDING_ORIGINS.remove(id);
        LAST_FACES.remove(id);
        HUNGER_STARTS.remove(id);
    }

    @SubscribeEvent
    public void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntityPlayer() instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
            LAST_FACES.put(player.getUniqueID(), new BreakFace(event.getPos(), event.getFace()));
            if (!Config.consumeHunger && !player.isCreative()) {
                HUNGER_STARTS.put(player.getUniqueID(), HungerSnapshot.capture(player));
            }
        }
    }

    // Capture the original block before other mods consume the event.
    // The guarded interaction below still runs the full interaction event pipeline.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntityPlayer() instanceof EntityPlayerMP)
                || event.getWorld().isRemote) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
        UUID id = player.getUniqueID();
        if (event.isCanceled()
                || RIGHT_CLICK_GUARD.contains(id)
                || !HELD_KEYS.contains(id)
                || ACTIVE_JOBS.containsKey(id)
                || ACTIVE_RIGHT_CLICK_JOBS.containsKey(id)
                || event.getItemStack().isEmpty()) {
            return;
        }

        WorldServer world = (WorldServer) event.getWorld();
        BlockPos origin = event.getPos();
        IBlockState state = world.getBlockState(origin);
        if (state.getBlock() == Blocks.AIR || !world.isBlockModifiable(player, origin)) {
            return;
        }

        ChainMode mode = getMode(player);
        if (mode != ChainMode.USE_BLOCK && !(mode == ChainMode.NORMAL && isLog(state))) {
            return;
        }

        EnumHand hand = event.getHand();
        ItemStack stack = player.getHeldItem(hand);
        net.minecraft.item.Item item = stack.getItem();
        Vec3d hit = event.getHitVec();
        EnumFacing face = event.getFace() == null ? EnumFacing.UP : event.getFace();
        RIGHT_CLICK_GUARD.add(id);
        EnumActionResult result;
        try {
            result = player.interactionManager.processRightClickBlock(player, world, stack, hand, origin, face,
                    (float) (hit.x - origin.getX()), (float) (hit.y - origin.getY()),
                    (float) (hit.z - origin.getZ()));
        } finally {
            RIGHT_CLICK_GUARD.remove(id);
        }

        event.setCanceled(true);
        event.setCancellationResult(result);
        if (result == EnumActionResult.SUCCESS) {
            ACTIVE_RIGHT_CLICK_JOBS.put(id,
                    new RightClickJob(world, player, origin, state.getBlock(), hand, face, item));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreakSnapshot(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getPlayer() instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.getPlayer();
            if (HELD_KEYS.contains(player.getUniqueID()) && !ACTIVE_JOBS.containsKey(player.getUniqueID())
                    && !Config.consumeHunger && !player.isCreative()) {
                HUNGER_STARTS.put(player.getUniqueID(), HungerSnapshot.capture(player));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof EntityPlayerMP)
                || !(event.getWorld() instanceof WorldServer)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.getPlayer();
        UUID id = player.getUniqueID();
        if (!HELD_KEYS.contains(id) || ACTIVE_JOBS.containsKey(id)) {
            return;
        }
        WorldServer world = (WorldServer) event.getWorld();
        BlockPos pos = event.getPos();
        IBlockState state = event.getState();
        ChainMode mode = getMode(player);
        if (mode == ChainMode.XRAY) {
            return;
        }
        if (mode == ChainMode.USE_BLOCK && !isContainer(world, pos)) {
            return;
        }
        String rejection = eligibilityFailure(world, player, pos, state, state.getBlock(), mode,
                configuredWhitelist());
        if (rejection != null) {
            debug("break ignored reason={} mode={} target={}", rejection, mode, blockId(state.getBlock()));
            if (mode == ChainMode.BLAST_SAME) {
                showChainFailed(player, state.getBlock());
            }
            return;
        }
        EnumFacing face = resolveBreakFace(player, pos);
        DropBuffer drops = new DropBuffer();
        PENDING_DROPS.put(id, drops);
        PENDING_ORIGINS.put(id, pos);
        HungerSnapshot hunger = HUNGER_STARTS.remove(id);
        // Keep the chain bound to the selected slot while allowing inventory
        // auto-refill to replace a broken tool in that slot.
        int toolSlot = player.inventory.currentItem;
        ChainJob job = new ChainJob(world, player, pos, state.getBlock(), face, mode, drops, hunger, toolSlot);
        ACTIVE_JOBS.put(id, job);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoin(EntityJoinWorldEvent event) {
        if (!(event.getWorld() instanceof WorldServer) || event.getWorld().isRemote) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof EntityItem) && !(entity instanceof EntityXPOrb)) {
            return;
        }
        BlockPos entityPos = entity.getPosition();
        DropCapture direct = CAPTURING_DROPS.get();
        if (direct != null && direct.world == event.getWorld()
                && entityPos.distanceSq(direct.origin) <= 9.0D) {
            captureEntity(event, direct.drops);
            return;
        }
        DropBuffer nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Map.Entry<UUID, DropBuffer> entry : PENDING_DROPS.entrySet()) {
            ChainJob job = ACTIVE_JOBS.get(entry.getKey());
            BlockPos origin = PENDING_ORIGINS.get(entry.getKey());
            if (job != null && job.world == event.getWorld() && origin != null) {
                double distance = entityPos.distanceSq(origin);
                if (distance <= 9.0D && distance < nearestDistance) {
                    nearest = entry.getValue();
                    nearestDistance = distance;
                }
            }
        }
        if (nearest != null) {
            captureEntity(event, nearest);
        }
    }

    private static void captureEntity(EntityJoinWorldEvent event, DropBuffer drops) {
        if (event.getEntity() instanceof EntityItem) {
            drops.add(((EntityItem) event.getEntity()).getItem());
            event.setCanceled(true);
        } else if (event.getEntity() instanceof EntityXPOrb) {
            drops.addExperience(((EntityXPOrb) event.getEntity()).xpValue);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (ChainJob job : new ArrayList<ChainJob>(ACTIVE_JOBS.values())) {
            job.tick();
        }
        for (RightClickJob job : new ArrayList<RightClickJob>(ACTIVE_RIGHT_CLICK_JOBS.values())) {
            job.tick();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        ChainJob job = ACTIVE_JOBS.get(player.getUniqueID());
        if (job != null && job.restoreHungerAtTickEnd()) {
            return;
        }
    }

    public static void setKeyHeld(EntityPlayer player, boolean held) {
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        UUID id = player.getUniqueID();
        debug("key state player={} held={}", player.getName(), held);
        if (held) {
            HELD_KEYS.add(id);
            if (!Config.consumeHunger && !player.isCreative()) {
                HUNGER_STARTS.put(id, HungerSnapshot.capture((EntityPlayerMP) player));
            }
        } else {
            HELD_KEYS.remove(id);
            HUNGER_STARTS.remove(id);
        }
    }

    public static void setMode(EntityPlayer player, int ordinal) {
        if (player != null) {
            ChainMode mode = ChainMode.fromOrdinal(ordinal);
            PLAYER_MODES.put(player.getUniqueID(), mode);
            debug("mode state player={} requested={} resolved={}", player.getName(), ordinal, mode);
        }
    }

    public static ChainMode getMode(EntityPlayer player) {
        ChainMode mode = PLAYER_MODES.get(player.getUniqueID());
        return mode == null ? ChainMode.fromOrdinal(Config.defaultMode) : mode;
    }

    private static boolean isEligible(WorldServer world, EntityPlayerMP player, BlockPos pos, IBlockState state,
            Block targetBlock, ChainMode mode) {
        return isEligible(world, player, pos, state, targetBlock, mode, configuredWhitelist());
    }

    private static boolean isEligible(WorldServer world, EntityPlayerMP player, BlockPos pos, IBlockState state,
            Block targetBlock, ChainMode mode, Set<String> whitelist) {
        return eligibilityFailure(world, player, pos, state, targetBlock, mode, whitelist) == null;
    }

    private static String eligibilityFailure(WorldServer world, EntityPlayerMP player, BlockPos pos, IBlockState state,
            Block targetBlock, ChainMode mode, Set<String> whitelist) {
        if (mode == ChainMode.XRAY) {
            return "mode unavailable";
        }
        if (!world.isBlockLoaded(pos) || state.getBlock() == Blocks.AIR) {
            return "air";
        }
        if (!world.isBlockModifiable(player, pos)) {
            return "mayInteract=false";
        }
        boolean matches;
        switch (mode) {
        case BLAST_ANY:
            matches = true;
            break;
        case BLAST_ORES:
            matches = isOre(state);
            break;
        case BLAST_LOGS:
            matches = isLog(state);
            break;
        case BLAST_SAME:
            matches = sameTarget(state, targetBlock);
            break;
        default:
            matches = state.getBlock() == targetBlock;
            break;
        }
        // Whitelist entries extend only the all-ores blast mode; they do not
        // replace that mode's original rule.
        if (mode == ChainMode.BLAST_ORES && isWhitelisted(state, whitelist)) {
            matches = true;
        }
        if (!matches) {
            return "mode mismatch";
        }
        boolean container = isContainer(world, pos);
        if (mode == ChainMode.USE_BLOCK && !container) {
            return "not container";
        }
        if (container && mode != ChainMode.USE_BLOCK) {
            return "container";
        }
        if (!player.isCreative() && !ForgeHooks.canHarvestBlock(state.getBlock(), player, world, pos)) {
            return "harvestCheck=false";
        }
        return null;
    }

    private static Set<String> configuredWhitelist() {
        return new LinkedHashSet<String>(Config.effectiveWhitelist(Config.blockWhitelist));
    }

    private static boolean isWhitelisted(IBlockState state, Set<String> whitelist) {
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        Block block = state.getBlock();
        ResourceLocation id = block.getRegistryName();
        if (id == null) {
            return false;
        }
        String idText = id.toString().toLowerCase(Locale.ROOT);
        String path = id.getResourcePath().toLowerCase(Locale.ROOT);
        for (String rule : whitelist) {
            if (rule == null) {
                continue;
            }
            if (rule.startsWith("ore:")) {
                if (hasOreDictionaryName(state, rule.substring(4))) {
                    return true;
                }
            } else if (rule.indexOf('*') >= 0) {
                String candidate = rule.indexOf(':') >= 0 ? idText : path;
                if (wildcardMatches(rule, candidate)) {
                    return true;
                }
            } else if (rule.equals(idText)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasOreDictionaryName(IBlockState state, String expectedName) {
        if (expectedName == null || expectedName.isEmpty()) {
            return false;
        }
        try {
            Block block = state.getBlock();
            ItemStack stack = new ItemStack(block, 1, block.getMetaFromState(state));
            int[] ids = OreDictionary.getOreIDs(stack);
            String expected = expectedName.toLowerCase(Locale.ROOT);
            for (int id : ids) {
                if (OreDictionary.getOreName(id).toLowerCase(Locale.ROOT).equals(expected)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean wildcardMatches(String pattern, String value) {
        int patternIndex = 0;
        int valueIndex = 0;
        int starIndex = -1;
        int retryIndex = 0;
        while (valueIndex < value.length()) {
            if (patternIndex < pattern.length() && pattern.charAt(patternIndex) == value.charAt(valueIndex)) {
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

    private static boolean isContainer(World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof IInventory || world.getBlockState(pos).getBlock() instanceof BlockContainer) {
            return true;
        }
        return tile != null && tile.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
    }

    /**
     * Same-block blast match. Ores that differ only by host stone (allthemodium_ore
     * versus allthemodium_slate_ore, or diamond_ore versus deepslate_diamond_ore)
     * belong to one deposit, so they chain together instead of stopping at the
     * stone/deepslate boundary.
     */
    private static boolean sameTarget(IBlockState state, Block targetBlock) {
        if (state.getBlock() == targetBlock) {
            return true;
        }
        String targetFamily = oreFamilyKey(targetBlock);
        return targetFamily != null && targetFamily.equals(oreFamilyKey(state.getBlock()));
    }

    /** Ore family of a block, or null when the block is not an ore. */
    private static String oreFamilyKey(Block block) {
        if (block == null) {
            return null;
        }
        String cached = ORE_FAMILY_KEYS.get(block);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }

        String family = computeOreFamilyKey(block);
        ORE_FAMILY_KEYS.put(block, family == null ? "" : family);
        return family;
    }

    private static String computeOreFamilyKey(Block block) {
        ResourceLocation id = block.getRegistryName();
        if (id == null) {
            return null;
        }
        String path = id.getResourcePath();
        if (!path.endsWith(ORE_SUFFIX) || path.length() == ORE_SUFFIX.length()) {
            return null;
        }

        String base = path.substring(0, path.length() - ORE_SUFFIX.length());
        for (String host : ORE_HOST_STONES) {
            String suffix = "_" + host;
            if (base.length() > suffix.length() && base.endsWith(suffix)) {
                base = base.substring(0, base.length() - suffix.length());
                break;
            }
            String prefix = host + "_";
            if (base.length() > prefix.length() && base.startsWith(prefix)) {
                base = base.substring(prefix.length());
                break;
            }
        }
        return base.isEmpty() ? null : id.getResourceDomain() + ":" + base;
    }

    private static boolean isOre(IBlockState state) {
        Block block = state.getBlock();
        try {
            ItemStack stack = new ItemStack(block, 1, block.getMetaFromState(state));
            int[] ids = OreDictionary.getOreIDs(stack);
            for (int id : ids) {
                if (OreDictionary.getOreName(id).toLowerCase().startsWith("ore")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        ResourceLocation key = block.getRegistryName();
        return key != null && key.getResourcePath().endsWith(ORE_SUFFIX);
    }

    private static boolean isLog(IBlockState state) {
        if (state.getBlock() instanceof BlockLog || state.getBlock() instanceof BlockNewLog) {
            return true;
        }
        try {
            ItemStack stack = new ItemStack(state.getBlock(), 1, state.getBlock().getMetaFromState(state));
            int[] ids = OreDictionary.getOreIDs(stack);
            for (int id : ids) {
                if ("logWood".equals(OreDictionary.getOreName(id))) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean breakOne(WorldServer world, EntityPlayerMP player, BlockPos pos, Block targetBlock,
            ChainMode mode, Set<String> whitelist) {
        IBlockState state = world.getBlockState(pos);
        ChainJob job = ACTIVE_JOBS.get(player.getUniqueID());
        if (job != null && !job.isToolSlotUnchanged()) {
            return false;
        }
        if (!isEligible(world, player, pos, state, targetBlock, mode, whitelist)) {
            return false;
        }
        DropCapture previous = CAPTURING_DROPS.get();
        if (job != null) {
            CAPTURING_DROPS.set(new DropCapture(job.drops, world, pos));
        }
        try {
            boolean broken = player.interactionManager.tryHarvestBlock(pos);
            if (broken && Config.consumeHunger && !player.isCreative()) {
                player.getFoodStats().addExhaustion(0.005F);
            }
            if (broken && job != null) {
                job.refreshHungerEffect();
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

    private static List<BlockPos> createNormalOffsets() {
        List<BlockPos> offsets = new ArrayList<BlockPos>(26);
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

    private static List<BlockPos> interactionOffsets(EnumFacing face) {
        return NORMAL_OFFSETS;
    }

    private static List<BlockPos> interactionMiningOffsets(WorldServer world, BlockPos origin, Block targetBlock,
            EnumFacing face) {
        return NORMAL_OFFSETS;
    }

    private static void showProgress(EntityPlayerMP player, int count) {
        NetworkHandler.sendProgress(player, count);
    }

    private static void clearProgress(EntityPlayerMP player, int count) {
        NetworkHandler.clearProgress(player, count);
    }

    private static void showChainFailed(EntityPlayerMP player, Block block) {
        if (block == null) {
            return;
        }
        UUID id = player.getUniqueID();
        long now = player.world.getTotalWorldTime();
        ChainFailNotice last = CHAIN_FAIL_NOTICES.get(id);
        if (last != null && last.block == block && now - last.tick < CHAIN_FAIL_NOTICE_COOLDOWN) {
            return;
        }
        CHAIN_FAIL_NOTICES.put(id, new ChainFailNotice(block, now));
        player.sendStatusMessage(new TextComponentTranslation("message.veinminerplus.chain_failed",
                block.getLocalizedName()), true);
    }

    private static void debug(String message, Object... args) {
        if (Config.debugLogging) {
            VeinMinerPlus.LOGGER.info(String.format(java.util.Locale.ROOT, message.replace("{}", "%s"), args));
        }
    }

    private static String blockId(Block block) {
        ResourceLocation id = block == null ? null : block.getRegistryName();
        return id == null ? "<unregistered>" : id.toString();
    }

    private static final class ChainFailNotice {
        final Block block;
        final long tick;

        ChainFailNotice(Block block, long tick) {
            this.block = block;
            this.tick = tick;
        }
    }

    private static final class DropCapture {
        final DropBuffer drops;
        final WorldServer world;
        final BlockPos origin;

        DropCapture(DropBuffer drops, WorldServer world, BlockPos origin) {
            this.drops = drops;
            this.world = world;
            this.origin = origin;
        }
    }

    private static final class DropBuffer {
        private final List<ItemStack> items = new ArrayList<ItemStack>();
        private int experience;

        void add(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return;
            }
            int remaining = stack.getCount();
            for (ItemStack existing : items) {
                if (!ItemStack.areItemsEqual(existing, stack)
                        || !ItemStack.areItemStackTagsEqual(existing, stack)) {
                    continue;
                }
                int space = existing.getMaxStackSize() - existing.getCount();
                int amount = Math.min(space, remaining);
                existing.grow(amount);
                remaining -= amount;
                if (remaining <= 0) {
                    return;
                }
            }
            while (remaining > 0) {
                int amount = Math.min(stack.getMaxStackSize(), remaining);
                ItemStack copy = stack.copy();
                copy.setCount(amount);
                items.add(copy);
                remaining -= amount;
            }
        }

        void addExperience(int amount) {
            experience += amount;
        }

        void flush(WorldServer world, EntityPlayerMP player) {
            if (!world.getGameRules().getBoolean("doTileDrops")) {
                items.clear();
                experience = 0;
                return;
            }
            for (ItemStack stack : items) {
                EntityItem entity = new EntityItem(world, player.posX, player.posY, player.posZ, stack);
                entity.setPickupDelay(10);
                entity.motionX = entity.motionY = entity.motionZ = 0.0D;
                world.spawnEntity(entity);
            }
            while (experience > 0) {
                int split = EntityXPOrb.getXPSplit(experience);
                experience -= split;
                world.spawnEntity(new EntityXPOrb(world, player.posX, player.posY, player.posZ, split));
            }
            items.clear();
            experience = 0;
        }
    }

    private static final class RightClickJob {
        private final WorldServer world;
        private final EntityPlayerMP player;
        private final Block targetBlock;
        private final EnumHand hand;
        private final EnumFacing face;
        private final net.minecraft.item.Item item;
        private final int toolSlot;
        private final List<BlockPos> offsets;
        private final Deque<SearchNode> frontier = new ArrayDeque<SearchNode>();
        private final Set<BlockPos> examined = new HashSet<BlockPos>();
        private int attempted = 1;
        private boolean finished;

        RightClickJob(WorldServer world, EntityPlayerMP player, BlockPos origin, Block targetBlock,
                EnumHand hand, EnumFacing face, net.minecraft.item.Item item) {
            this.world = world;
            this.player = player;
            this.targetBlock = targetBlock;
            this.hand = hand;
            this.face = face;
            this.item = item;
            this.toolSlot = player.inventory.currentItem;
            this.offsets = interactionOffsets(face);
            examined.add(origin);
            frontier.addLast(new SearchNode(origin));
        }

        void tick() {
            if (!isHeldItemUnchanged()
                    || !HELD_KEYS.contains(player.getUniqueID())
                    || player.isDead
                    || player.world != world) {
                finish();
                return;
            }

            int attemptsThisTick = 0;
            int checks = 0;
            int attemptLimit = Config.maxNormalBlocksPerTick;
            int totalLimit = Config.maxNormalBlocks;
            while (checks < SEARCH_CHECKS_PER_TICK && attemptsThisTick < attemptLimit
                    && attempted < totalLimit
                    && !frontier.isEmpty()
                    && isHeldItemUnchanged()
                    && HELD_KEYS.contains(player.getUniqueID())) {
                SearchNode node = frontier.peekFirst();
                if (node.next == offsets.size()) {
                    frontier.removeFirst();
                    continue;
                }
                BlockPos offset = offsets.get(node.next++);
                checks++;
                BlockPos candidate = node.pos.add(offset.getX(), offset.getY(), offset.getZ());
                if (!isWorldPos(candidate) || !world.isBlockLoaded(candidate)
                        || !examined.add(candidate) || !world.isBlockModifiable(player, candidate)
                        || world.getBlockState(candidate).getBlock() != targetBlock) {
                    continue;
                }

                frontier.addLast(new SearchNode(candidate));
                attempted++;
                attemptsThisTick++;
                useOn(candidate);
            }

            if (!isHeldItemUnchanged() || !HELD_KEYS.contains(player.getUniqueID())
                    || frontier.isEmpty() || attempted >= totalLimit) {
                finish();
            }
        }

        private boolean isHeldItemUnchanged() {
            ItemStack stack = player.getHeldItem(hand);
            return !stack.isEmpty()
                    && stack.getItem() == item
                    && (hand != EnumHand.MAIN_HAND || player.inventory.currentItem == toolSlot);
        }

        private void useOn(BlockPos pos) {
            UUID id = player.getUniqueID();
            RIGHT_CLICK_GUARD.add(id);
            try {
                player.interactionManager.processRightClickBlock(player, world, player.getHeldItem(hand), hand, pos,
                        face, 0.5F + face.getDirectionVec().getX() * 0.5F,
                        0.5F + face.getDirectionVec().getY() * 0.5F,
                        0.5F + face.getDirectionVec().getZ() * 0.5F);
            } finally {
                RIGHT_CLICK_GUARD.remove(id);
            }
        }

        void finish() {
            if (finished) {
                return;
            }
            finished = true;
            ACTIVE_RIGHT_CLICK_JOBS.remove(player.getUniqueID(), this);
        }
    }

    private static final class ChainJob {
        private final WorldServer world;
        private final EntityPlayerMP player;
        private final BlockPos origin;
        private final Block targetBlock;
        private final EnumFacing face;
        private final ChainMode mode;
        private final DropBuffer drops;
        private final HungerSnapshot hunger;
        private final int toolSlot;
        private final Set<String> whitelist;
        private final Set<BlockPos> examined = new HashSet<BlockPos>();
        private final Deque<SearchNode> frontier = new ArrayDeque<SearchNode>();
        private final Deque<SparseScanCursor> sparseScans = new ArrayDeque<SparseScanCursor>();
        private final PriorityQueue<BlockPos> sparseTargets;
        private final Map<Long, List<BlockPos>> sparseChunkMatches = new HashMap<Long, List<BlockPos>>();
        private final Map<IBlockState, Boolean> sparseStateMatches = new HashMap<IBlockState, Boolean>();
        private final List<BlockPos> blastCenters = new ArrayList<BlockPos>();
        private final List<BlockPos> offsets;
        private final boolean sparseBlast;
        private int blastDistance;
        private final boolean blastManhattan;
        private int searchChunkLoadsThisTick;
        private int sparseChunkLoadsThisTick;
        private int sparseChunkScanBudget;
        private boolean lowTpsWarned;
        private int brokenCount = 1;
        private boolean sawSameTarget;
        private int sparseChunksScanned;
        private int sparseMatchedPositions;
        private int areaDepth = 1;
        private int areaIndex;
        private int lastProgressCount;
        private boolean progressShown;
        private boolean finished;

        ChainJob(WorldServer world, EntityPlayerMP player, BlockPos origin, Block targetBlock, EnumFacing face,
                ChainMode mode, DropBuffer drops, HungerSnapshot hungerBefore, int toolSlot) {
            this.world = world;
            this.player = player;
            this.origin = origin;
            this.targetBlock = targetBlock;
            this.face = face;
            this.mode = mode;
            this.toolSlot = toolSlot;
            this.whitelist = configuredWhitelist();
            // The manually mined block is the center of the first plane for area modes above 1x1.
            this.areaDepth = mode.isArea() && mode != ChainMode.AREA_1X1 ? 0 : 1;
            this.drops = drops;
            this.hunger = !Config.consumeHunger && !player.isCreative()
                    ? hungerBefore == null ? HungerSnapshot.capture(player) : hungerBefore : null;
            this.blastDistance = Config.blastSearchDistance;
            this.blastManhattan = Config.blastManhattan;
            this.sparseBlast = mode.isBlast();
            this.offsets = mode == ChainMode.USE_BLOCK
                    ? interactionMiningOffsets(world, origin, targetBlock, face) : NORMAL_OFFSETS;
            this.sparseTargets = new PriorityQueue<BlockPos>(new Comparator<BlockPos>() {
                @Override
                public int compare(BlockPos first, BlockPos second) {
                    long firstDistance = squaredDistance(origin, first);
                    long secondDistance = squaredDistance(origin, second);
                    if (firstDistance != secondDistance) {
                        return firstDistance < secondDistance ? -1 : 1;
                    }
                    if (first.getY() != second.getY()) {
                        return Integer.compare(first.getY(), second.getY());
                    }
                    if (first.getX() != second.getX()) {
                        return Integer.compare(first.getX(), second.getX());
                    }
                    return Integer.compare(first.getZ(), second.getZ());
                }
            });
            examined.add(origin);
            if (mode.isBlast()) {
                blastCenters.add(origin);
            }
            if (!mode.isArea()) {
                if (sparseBlast) {
                    sparseScans.addLast(new SparseScanCursor(origin));
                } else {
                    frontier.add(new SearchNode(origin));
                }
            }
            debug("job start player={} mode={} target={} pos={} distance={} manhattan={} whitelist={}",
                    player.getName(), mode, blockId(targetBlock), origin, blastDistance,
                    blastManhattan, whitelist);
            refreshHungerEffect();
            // Keep the first, manually mined block in the visible progress total.
            sendProgress();
        }

        void tick() {
            UUID id = player.getUniqueID();
            PENDING_DROPS.remove(id, drops);
            PENDING_ORIGINS.remove(id, origin);
            if (!isToolSlotUnchanged()) {
                finish();
                return;
            }
            if (world.getBlockState(origin).getBlock() == targetBlock) {
                finish();
                return;
            }
            if (!HELD_KEYS.contains(id) || player.isDead || player.isSpectator() || player.world != world) {
                finish();
                return;
            }
            if (mode.isBlast()) {
                adjustBlastRadiusForTps();
            }
            searchChunkLoadsThisTick = 0;
            if (mode.isArea()) {
                tickArea();
            } else if (sparseBlast) {
                tickSparseBlast();
            } else {
                tickGraph();
            }
        }

        private boolean isToolSlotUnchanged() {
            return player.inventory.currentItem == toolSlot;
        }

        private void tickGraph() {
            int checks = 0;
            int breaks = 0;
            int breakLimit = mode.isBlast() ? Config.maxBlastBlocksPerTick : Config.maxNormalBlocksPerTick;
            graphSearch:
            while (checks < SEARCH_CHECKS_PER_TICK && breaks < breakLimit && !frontier.isEmpty()
                    && brokenCount < (mode.isBlast() ? Config.maxBlastBlocks : Config.maxNormalBlocks)
                    && HELD_KEYS.contains(player.getUniqueID()) && isToolSlotUnchanged()) {
                SearchNode node = frontier.removeFirst();
                int centerChecks = 0;
                while (centerChecks < SEARCH_CHECKS_PER_CENTER && checks < SEARCH_CHECKS_PER_TICK
                        && breaks < breakLimit && node.next < offsets.size() && isToolSlotUnchanged()) {
                    BlockPos offset = offsets.get(node.next++);
                    BlockPos candidate = node.pos.add(offset.getX(), offset.getY(), offset.getZ());
                    checks++;
                    centerChecks++;
                    if (!isWorldPos(candidate) || !examined.add(candidate)) {
                        continue;
                    }
                    if (!ensureSearchChunkLoaded(candidate)) {
                        examined.remove(candidate);
                        node.next--;
                        frontier.addFirst(node);
                        break graphSearch;
                    }
                    if (mode == ChainMode.BLAST_SAME && sameTarget(world.getBlockState(candidate), targetBlock)) {
                        sawSameTarget = true;
                    }
                    if (breakOne(world, player, candidate, targetBlock, mode, whitelist)) {
                        brokenCount++;
                        breaks++;
                    }
                }
                if (node.next < offsets.size()) {
                    frontier.addLast(node);
                }
            }
            if (breaks > 0) {
                sendProgressIfReady();
            }
            if (!isToolSlotUnchanged() || !HELD_KEYS.contains(player.getUniqueID()) || frontier.isEmpty()
                    || brokenCount >= (mode.isBlast() ? Config.maxBlastBlocks : Config.maxNormalBlocks)) {
                finish();
            }
        }

        private void tickSparseBlast() {
            int checks = 0;
            int breaks = 0;
            int breakLimit = Config.maxBlastBlocksPerTick;
            sparseChunkLoadsThisTick = 0;
            sparseChunkScanBudget = Config.blastChunkScansPerTick;
            while (checks < SEARCH_CHECKS_PER_TICK && breaks < breakLimit
                    && brokenCount < Config.maxBlastBlocks && HELD_KEYS.contains(player.getUniqueID())
                    && isToolSlotUnchanged()) {
                if (sparseTargets.isEmpty()) {
                    SparseScanCursor scan = sparseScans.peekFirst();
                    if (scan == null) {
                        break;
                    }
                    checks += scan.scan(SEARCH_CHECKS_PER_TICK - checks);
                    if (scan.finished) {
                        sparseScans.removeFirst();
                    }
                    if (sparseTargets.isEmpty() || checks >= SEARCH_CHECKS_PER_TICK) {
                        continue;
                    }
                }

                BlockPos candidate = sparseTargets.poll();
                checks++;
                IBlockState state = world.getBlockState(candidate);
                String rejection = eligibilityFailure(world, player, candidate, state, targetBlock, mode, whitelist);
                debug("candidate mode={} target={} pos={} actual={} eligible={} reason={}", mode,
                        blockId(targetBlock), candidate, blockId(state.getBlock()), rejection == null,
                        rejection == null ? "-" : rejection);
                if (rejection == null && breakOne(world, player, candidate, targetBlock, mode, whitelist)) {
                    debug("candidate break mode={} target={} pos={} broken=true", mode,
                            blockId(targetBlock), candidate);
                    brokenCount++;
                    breaks++;
                    blastCenters.add(candidate);
                    sparseScans.addLast(new SparseScanCursor(candidate));
                }
            }
            if (breaks > 0) {
                sendProgressIfReady();
            }
            if (!isToolSlotUnchanged() || !HELD_KEYS.contains(player.getUniqueID())
                    || searchExhausted()
                    || brokenCount >= Config.maxBlastBlocks) {
                finish();
            }
        }

        private void adjustBlastRadiusForTps() {
            double tps = currentTps(world);
            if (lowTpsWarned || tps >= Config.blastLowTpsThreshold) {
                return;
            }

            lowTpsWarned = true;
            if (Config.blastAutoReduceRadius && blastDistance > 3) {
                int oldDistance = blastDistance;
                blastDistance = Math.max(3, blastDistance / 2);
                sparseTargets.clear();
                sparseScans.clear();
                frontier.clear();
                examined.clear();
                if (sparseBlast) {
                    for (BlockPos center : blastCenters) {
                        examined.add(center);
                        sparseScans.addLast(new SparseScanCursor(center));
                    }
                } else {
                    examined.add(origin);
                    frontier.addLast(new SearchNode(origin));
                }
                NetworkHandler.sendLowTpsRadiusNotice(player, tps, oldDistance, blastDistance);
            } else {
                player.sendStatusMessage(new TextComponentTranslation(
                        "message.veinminerplus.blast_radius_too_large",
                        String.format(java.util.Locale.ROOT, "%.1f", tps), blastDistance), true);
            }
        }

        private boolean matchesSparseState(IBlockState state) {
            boolean modeMatches;
            if (mode == ChainMode.BLAST_SAME) {
                modeMatches = sameTarget(state, targetBlock);
            } else if (mode == ChainMode.BLAST_ANY) {
                modeMatches = state.getBlock() != Blocks.AIR;
            } else {
                Boolean cached = sparseStateMatches.get(state);
                if (cached != null) {
                    modeMatches = cached.booleanValue();
                } else {
                    modeMatches = mode == ChainMode.BLAST_ORES ? isOre(state) : isLog(state);
                    sparseStateMatches.put(state, Boolean.valueOf(modeMatches));
                }
            }
            return modeMatches || (mode == ChainMode.BLAST_ORES && isWhitelisted(state, whitelist));
        }

        private void tickArea() {
            int size = mode.areaSize();
            int plane = size * size;
            int breaks = 0;
            // Area modes share the configurable normal per-tick budget instead of a
            // fixed low constant, so they are no longer throttled to 8 blocks per tick.
            while (breaks < Config.maxNormalBlocksPerTick && brokenCount < Config.maxNormalBlocks
                    && areaDepth <= Config.maxNormalBlocks
                    && HELD_KEYS.contains(player.getUniqueID()) && isToolSlotUnchanged()) {
                if (areaIndex >= plane) {
                    areaDepth++;
                    areaIndex = 0;
                    continue;
                }
                int start = -(size / 2);
                int first = start + areaIndex / size;
                int second = start + areaIndex % size;
                areaIndex++;
                BlockPos candidate = areaOffset(origin, face, first, second).offset(face.getOpposite(), areaDepth);
                if (!isWorldPos(candidate) || !examined.add(candidate)) {
                    continue;
                }
                if (!ensureSearchChunkLoaded(candidate)) {
                    examined.remove(candidate);
                    areaIndex--;
                    break;
                }
                if (breakOne(world, player, candidate, targetBlock, mode, whitelist)) {
                    brokenCount++;
                    breaks++;
                }
            }
            if (breaks > 0) {
                sendProgressIfReady();
            }
            if (!isToolSlotUnchanged() || !HELD_KEYS.contains(player.getUniqueID()) || brokenCount >= Config.maxNormalBlocks
                    || areaDepth > Config.maxNormalBlocks) {
                finish();
            }
        }

        private boolean ensureSearchChunkLoaded(BlockPos pos) {
            if (world.isBlockLoaded(pos)) {
                return true;
            }
            if (searchChunkLoadsThisTick >= SEARCH_CHUNK_LOADS_PER_TICK) {
                return false;
            }
            searchChunkLoadsThisTick++;
            return world.getChunkProvider().provideChunk(pos.getX() >> 4, pos.getZ() >> 4) != null;
        }

        private void finish() {
            if (finished) {
                return;
            }
            finished = true;
            if (mode == ChainMode.BLAST_SAME && brokenCount <= 1 && !sawSameTarget && searchExhausted()) {
                showChainFailed(player, targetBlock);
            }
            debug("job finish player={} mode={} target={} broken={} sawSame={} chunksScanned={} matchedPositions={} exhausted={}",
                    player.getName(), mode, blockId(targetBlock), brokenCount, sawSameTarget,
                    sparseChunksScanned, sparseMatchedPositions, searchExhausted());
            // Send one terminal state instead of an active refresh immediately
            // followed by a clear packet. The client keeps this final count for a
            // fixed period, so the visible counter never transitions through an
            // empty frame at the end of a fast 3x3 job.
            clearProgress(player, brokenCount);
            if (hunger != null) {
                hunger.restore(player);
                player.removePotionEffect(ModEffects.CHAIN_SATURATION);
            }
            PENDING_DROPS.remove(player.getUniqueID(), drops);
            PENDING_ORIGINS.remove(player.getUniqueID(), origin);
            drops.flush(player.getServerWorld(), player);
            ACTIVE_JOBS.remove(player.getUniqueID(), this);
        }

        private boolean searchExhausted() {
            return sparseBlast ? sparseTargets.isEmpty() && sparseScans.isEmpty() : frontier.isEmpty();
        }

        private void sendProgressIfReady() {
            if (!progressShown || lastProgressCount != brokenCount) {
                sendProgress();
            }
        }

        private void sendProgress() {
            showProgress(player, brokenCount);
            lastProgressCount = brokenCount;
            progressShown = true;
        }

        boolean restoreHungerAtTickEnd() {
            if (hunger == null) {
                return false;
            }
            hunger.restore(player);
            refreshHungerEffect();
            return true;
        }

        void refreshHungerEffect() {
            if (hunger != null && ModEffects.CHAIN_SATURATION != null) {
                player.addPotionEffect(new PotionEffect(ModEffects.CHAIN_SATURATION, 40, 255, false, true));
            }
        }

        private final class SparseScanCursor {
            private final BlockPos center;
            private final int centerChunkX;
            private final int centerChunkZ;
            private final int minChunkX;
            private final int maxChunkX;
            private final int minChunkZ;
            private final int maxChunkZ;
            private final int maxRing;
            private int ring;
            private int edgeIndex;
            private int chunkX;
            private int chunkZ;
            private Chunk currentChunk;
            private List<BlockPos> currentMatches;
            private int matchIndex;
            private int blockIndex;
            private boolean scanningBlocks;
            private boolean finished;

            SparseScanCursor(BlockPos center) {
                this.center = center;
                this.centerChunkX = center.getX() >> 4;
                this.centerChunkZ = center.getZ() >> 4;
                this.minChunkX = (center.getX() - blastDistance) >> 4;
                this.maxChunkX = (center.getX() + blastDistance) >> 4;
                this.minChunkZ = (center.getZ() - blastDistance) >> 4;
                this.maxChunkZ = (center.getZ() + blastDistance) >> 4;
                this.maxRing = Math.max(Math.max(centerChunkX - minChunkX, maxChunkX - centerChunkX),
                        Math.max(centerChunkZ - minChunkZ, maxChunkZ - centerChunkZ));
                selectNextChunk();
            }

            int scan(int budget) {
                int checks = 0;
                while (checks < budget && !finished && isToolSlotUnchanged()) {
                    if (currentChunk == null && currentMatches == null) {
                        long key = chunkKey(chunkX, chunkZ);
                        checks++;
                        if (sparseChunkMatches.containsKey(key)) {
                            currentMatches = sparseChunkMatches.get(key);
                            matchIndex = 0;
                        } else {
                            if (sparseChunkScanBudget <= 0) {
                                return checks;
                            }
                            sparseChunkScanBudget--;
                            currentChunk = world.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
                            if (currentChunk == null) {
                                if (sparseChunkLoadsThisTick >= SPARSE_CHUNK_LOADS_PER_TICK) {
                                    // Leave this chunk as the cursor's current work item and resume
                                    // on the next tick after the load budget is reset.
                                    return budget;
                                }
                                currentChunk = world.getChunkProvider().provideChunk(chunkX, chunkZ);
                                sparseChunkLoadsThisTick++;
                                if (currentChunk == null) {
                                    sparseChunkMatches.put(key, Collections.<BlockPos>emptyList());
                                    selectNextChunk();
                                }
                            } else {
                                currentMatches = new ArrayList<BlockPos>();
                                blockIndex = 0;
                                scanningBlocks = true;
                            }
                        }
                        if (currentChunk != null && currentMatches == null) {
                            currentMatches = new ArrayList<BlockPos>();
                            blockIndex = 0;
                            scanningBlocks = true;
                        }
                        continue;
                    }

                    if (scanningBlocks) {
                        if (blockIndex >= 16 * 16 * 256) {
                            sparseChunkMatches.put(chunkKey(chunkX, chunkZ), currentMatches);
                            sparseChunksScanned++;
                            sparseMatchedPositions += currentMatches.size();
                            if (!currentMatches.isEmpty()) {
                                debug("chunk matches mode={} target={} chunk=({}, {}) count={}", mode,
                                        blockId(targetBlock), chunkX, chunkZ, currentMatches.size());
                            }
                            selectNextChunk();
                            continue;
                        }
                        int sectionIndex = blockIndex >> 12;
                        ExtendedBlockStorage[] storageArray = currentChunk.getBlockStorageArray();
                        ExtendedBlockStorage storage = sectionIndex < storageArray.length
                                ? storageArray[sectionIndex] : null;
                        if (storage == null || storage.isEmpty()) {
                            blockIndex = (sectionIndex + 1) << 12;
                            checks++;
                            continue;
                        }
                        int localIndex = blockIndex++ & 4095;
                        int localX = localIndex & 15;
                        int localZ = localIndex >> 4 & 15;
                        int localY = localIndex >> 8 & 15;
                        IBlockState state = storage.get(localX, localY, localZ);
                        checks++;
                        if (matchesSparseState(state)) {
                            BlockPos pos = new BlockPos((chunkX << 4) + localX,
                                    (sectionIndex << 4) + localY, (chunkZ << 4) + localZ);
                            currentMatches.add(pos);
                            if (mode == ChainMode.BLAST_SAME) {
                                sawSameTarget = true;
                            }
                            addTarget(pos);
                        }
                        continue;
                    }

                    if (matchIndex < currentMatches.size()) {
                        addTarget(currentMatches.get(matchIndex++));
                        checks++;
                    } else {
                        selectNextChunk();
                    }
                }
                return checks;
            }

            private void addTarget(BlockPos pos) {
                if (withinBlastDistance(center, pos, blastDistance, blastManhattan) && examined.add(pos)) {
                    sparseTargets.add(pos);
                }
            }

            private void selectNextChunk() {
                currentChunk = null;
                currentMatches = null;
                matchIndex = 0;
                blockIndex = 0;
                scanningBlocks = false;
                while (ring <= maxRing) {
                    int edgeLength = ring == 0 ? 1 : 8 * ring;
                    while (edgeIndex < edgeLength) {
                        int index = edgeIndex++;
                        int offsetX;
                        int offsetZ;
                        if (ring == 0) {
                            offsetX = 0;
                            offsetZ = 0;
                        } else {
                            int topLength = 2 * ring + 1;
                            if (index < topLength) {
                                offsetX = -ring + index;
                                offsetZ = -ring;
                            } else if ((index -= topLength) < 2 * ring) {
                                offsetX = ring;
                                offsetZ = -ring + 1 + index;
                            } else if ((index -= 2 * ring) < 2 * ring) {
                                offsetX = ring - 1 - index;
                                offsetZ = ring;
                            } else {
                                index -= 2 * ring;
                                offsetX = -ring;
                                offsetZ = ring - 1 - index;
                            }
                        }
                        int nextChunkX = centerChunkX + offsetX;
                        int nextChunkZ = centerChunkZ + offsetZ;
                        if (nextChunkX >= minChunkX && nextChunkX <= maxChunkX
                                && nextChunkZ >= minChunkZ && nextChunkZ <= maxChunkZ) {
                            chunkX = nextChunkX;
                            chunkZ = nextChunkZ;
                            return;
                        }
                    }
                    ring++;
                    edgeIndex = 0;
                }
                finished = true;
            }
        }
    }

    private static BlockPos areaOffset(BlockPos start, EnumFacing face, int first, int second) {
        switch (face.getAxis()) {
        case X:
            return start.add(0, first, second);
        case Y:
            return start.add(first, 0, second);
        default:
            return start.add(first, second, 0);
        }
    }

    private static EnumFacing resolveBreakFace(EntityPlayerMP player, BlockPos target) {
        BreakFace savedFace = LAST_FACES.remove(player.getUniqueID());
        if (savedFace != null && savedFace.pos.equals(target) && savedFace.face != null) {
            return savedFace.face;
        }

        // BreakEvent has no hit-face field. Derive a fallback from the current
        // view instead of defaulting to UP, which makes area chains mine down.
        net.minecraft.util.math.Vec3d look = player.getLookVec();
        return EnumFacing.getFacingFromVector((float) look.x, (float) look.y, (float) look.z).getOpposite();
    }

    private static boolean isWorldPos(BlockPos pos) {
        return pos.getY() >= 0 && pos.getY() < 256;
    }

    private static double currentTps(WorldServer world) {
        MinecraftServer server = world.getMinecraftServer();
        if (server == null || server.tickTimeArray == null) {
            return 20.0D;
        }
        double totalMillis = 0.0D;
        int samples = 0;
        for (long nanos : server.tickTimeArray) {
            if (nanos > 0L) {
                totalMillis += nanos / 1000000.0D;
                samples++;
            }
        }
        if (samples == 0 || totalMillis <= 0.0D) {
            return 20.0D;
        }
        double averageMillis = totalMillis / samples;
        return Math.min(20.0D, 1000.0D / averageMillis);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (long) chunkX << 32 ^ chunkZ & 0xffffffffL;
    }

    private static long squaredDistance(BlockPos first, BlockPos second) {
        long x = (long) first.getX() - second.getX();
        long y = (long) first.getY() - second.getY();
        long z = (long) first.getZ() - second.getZ();
        return x * x + y * y + z * z;
    }

    private static boolean withinBlastDistance(BlockPos first, BlockPos second, int distance, boolean manhattan) {
        if (manhattan) {
            return Math.abs(first.getX() - second.getX()) + Math.abs(first.getY() - second.getY())
                    + Math.abs(first.getZ() - second.getZ()) <= distance;
        }
        return squaredDistance(first, second) <= (long) distance * distance;
    }

    private static final class SearchNode {
        final BlockPos pos;
        int next;

        SearchNode(BlockPos pos) {
            this.pos = pos;
        }
    }

    private static final class BreakFace {
        final BlockPos pos;
        final EnumFacing face;

        BreakFace(BlockPos pos, EnumFacing face) {
            this.pos = pos;
            this.face = face;
        }
    }

    private static final class HungerSnapshot {
        private final NBTTagCompound state;

        HungerSnapshot(NBTTagCompound state) {
            this.state = state;
        }

        static HungerSnapshot capture(EntityPlayerMP player) {
            NBTTagCompound state = new NBTTagCompound();
            player.getFoodStats().writeNBT(state);
            return new HungerSnapshot(state);
        }

        void restore(EntityPlayerMP player) {
            player.getFoodStats().readNBT(state);
        }
    }
}
