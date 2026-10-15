package com.extrarawstyle.veinminerplus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.EncoderException;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforgespi.language.IModInfo;

public final class CompatibilityCheck {
    public static void main(String[] arguments) throws Exception {
        checkOptionalClasses();
        checkPackets();
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        loadConfig();
        checkDebugCommands();
        checkAePickupIsolation();
        checkBlacklist();
        checkBlacklistCache();
        System.out.println("Compatibility checks passed: optional AE class loading, key/config packet round trips,"
                + " packet limits, debug command permissions and persistence, AE pickup isolation,"
                + " all-mode blacklist priority, wildcard rules, live tag updates and cache invalidation.");
    }

    private static void checkOptionalClasses() throws Exception {
        ClassLoader loader = CompatibilityCheck.class.getClassLoader();
        require(loader.getResource("appeng/api/stacks/AEItemKey.class") == null, "AE leaked into runtime dependencies");
        require(loader.getResource("de/mari_023/ae2wtlib/AE2wtlibEvents.class") == null,
                "Wireless terminals leaked into runtime dependencies");
        Class<?> adapter = Class.forName("com.extrarawstyle.veinminerplus.Ae2DropStorage", true, loader);
        require(adapter.getDeclaredMethods().length > 0, "Optional adapter failed to load without AE");
        Class.forName("com.extrarawstyle.veinminerplus.mixin.Ae2MagnetPickupMixin", true, loader);
        Class.forName("com.extrarawstyle.veinminerplus.mixin.ItemEntityMergeMixin", true, loader);
    }

    private static void checkPackets() {
        for (boolean held : List.of(false, true)) {
            for (boolean bypass : List.of(false, true)) {
                roundTrip(NetworkHandler.KeyStatePayload.STREAM_CODEC,
                        new NetworkHandler.KeyStatePayload(held, bypass));
            }
        }
        roundTrip(NetworkHandler.ConfigUpdatePayload.STREAM_CODEC, update("minecraft:*_ore", "minecraft:bedrock"));
        roundTrip(NetworkHandler.ConfigSnapshotPayload.STREAM_CODEC, snapshot("", ""));
        String maximumUpdate = "x".repeat(NetworkHandler.MAX_WHITELIST_TEXT_LENGTH);
        roundTrip(NetworkHandler.ConfigUpdatePayload.STREAM_CODEC, update(maximumUpdate, maximumUpdate));
        String maximumSnapshot = "x".repeat(NetworkHandler.MAX_CONFIG_SNAPSHOT_TEXT_LENGTH);
        roundTrip(NetworkHandler.ConfigSnapshotPayload.STREAM_CODEC, snapshot(maximumSnapshot, maximumSnapshot));
        roundTrip(NetworkHandler.BlacklistSyncPayload.STREAM_CODEC,
                new NetworkHandler.BlacklistSyncPayload(maximumSnapshot));
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            try {
                NetworkHandler.ConfigUpdatePayload.STREAM_CODEC.encode(buffer, update("", maximumUpdate + "x"));
                throw new AssertionError("Oversized client blacklist was accepted");
            } catch (EncoderException expected) {
                // Client updates must stay within the serverbound payload budget.
            }
        } finally {
            buffer.release();
        }
    }

    private static NetworkHandler.ConfigUpdatePayload update(String whitelist, String blacklist) {
        return new NetworkHandler.ConfigUpdatePayload(1024, 32, 32767, 64, 48, 8, 15,
                true, true, false, ChainMode.BLAST_ORES.id(), whitelist, blacklist);
    }

    private static NetworkHandler.ConfigSnapshotPayload snapshot(String whitelist, String blacklist) {
        return new NetworkHandler.ConfigSnapshotPayload(1024, 32, 32767, 64, 48, 8, 15,
                true, true, false, ChainMode.BLAST_ORES.id(), whitelist, blacklist);
    }

    private static <T> void roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T payload) {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            codec.encode(buffer, payload);
            require(payload.equals(codec.decode(buffer)), "Packet round trip changed " + payload.getClass().getName());
            require(buffer.readableBytes() == 0, "Packet decoder left unread fields");
        } finally {
            buffer.release();
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    private static void loadConfig() throws ReflectiveOperationException {
        CommentedConfig values = CommentedConfig.inMemory();
        Config.SPEC.correct(values);
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig")
                .getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
        constructor.setAccessible(true);
        Config.SPEC.acceptConfig((IConfigSpec.ILoadedConfig) constructor.newInstance(values, null, null));
    }

    private static void checkDebugCommands() throws Exception {
        var modInfo = (IModInfo) Proxy.newProxyInstance(CompatibilityCheck.class.getClassLoader(),
                new Class<?>[] { IModInfo.class }, (proxy, method, args) ->
                        "getModId".equals(method.getName()) ? "veinminerplus" : null);
        var container = new ModContainer(modInfo) {
            @Override
            public IEventBus getEventBus() {
                return null;
            }
        };
        var configConstructor = ModConfig.class.getDeclaredConstructor(ModConfig.Type.class, IConfigSpec.class,
                ModContainer.class, String.class, ReentrantLock.class);
        configConstructor.setAccessible(true);
        var modConfig = configConstructor.newInstance(ModConfig.Type.COMMON, Config.SPEC, container,
                "veinminerplus-common.toml", new ReentrantLock());
        var loadedConstructor = Class.forName("net.neoforged.fml.config.LoadedConfig")
                .getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
        loadedConstructor.setAccessible(true);
        Path directory = Path.of("debug-command-check").toAbsolutePath();
        FMLPaths.loadAbsolutePaths(directory);
        Path path = directory.resolve("config/veinminerplus-common.toml");
        try {
            CommentedConfig values = CommentedConfig.inMemory();
            Config.SPEC.correct(values);
            Config.SPEC.acceptConfig((IConfigSpec.ILoadedConfig) loadedConstructor.newInstance(values, path, modConfig));
            var dispatcher = new CommandDispatcher<CommandSourceStack>();
            new CommandEvents().onRegisterCommands(new RegisterCommandsEvent(dispatcher,
                    Commands.CommandSelection.DEDICATED, null));
            var source = new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, 2,
                    "console", Component.literal("console"), null, null);
            require(dispatcher.getRoot().getChild("veinminerplus").getChild("gui") != null,
                    "Adding debug removed the gui command");
            var suggestions = dispatcher.getCompletionSuggestions(
                    dispatcher.parse("veinminerplus debug ", source)).get().getList();
            require(suggestions.stream().map(suggestion -> suggestion.getText()).collect(
                    java.util.stream.Collectors.toSet()).equals(Set.of("true", "false")),
                    "Debug boolean completion is incomplete");
            for (boolean enabled : List.of(true, false)) {
                require(dispatcher.execute("veinminerplus debug " + enabled, source) == 1,
                        "Debug command failed from a non-player console");
                require(Config.DEBUG_LOGGING.getAsBoolean() == enabled, "Debug state did not change immediately");
                try (var reader = Files.newBufferedReader(path)) {
                    CommentedConfig saved = new TomlParser().parse(reader);
                    require(Boolean.valueOf(enabled).equals(saved.get("debugLogging")),
                            "Debug command did not persist to TOML");
                    Config.SPEC.acceptConfig((IConfigSpec.ILoadedConfig) loadedConstructor.newInstance(
                            saved, path, modConfig));
                    require(Config.DEBUG_LOGGING.getAsBoolean() == enabled, "Debug state did not survive config reload");
                }
            }
            for (String invalid : List.of("veinminerplus debug", "veinminerplus debug yes",
                    "veinminerplus debug true extra")) {
                try {
                    dispatcher.execute(invalid, source);
                    throw new AssertionError("Invalid debug command was accepted: " + invalid);
                } catch (CommandSyntaxException expected) {
                    require(!Config.DEBUG_LOGGING.getAsBoolean(), "Invalid command changed debug state");
                }
            }
            try {
                dispatcher.execute("veinminerplus debug true", source.withPermission(1));
                throw new AssertionError("Non-admin changed global debug logging");
            } catch (CommandSyntaxException expected) {
                require(!Config.DEBUG_LOGGING.getAsBoolean(), "Rejected command changed debug state");
            }
        } finally {
            Files.deleteIfExists(path);
            loadConfig();
        }
    }

    private static void checkAePickupIsolation() throws Exception {
        // Allocate only the entity data needed by the listener, without starting a world or loading AE.
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        ItemEntity item = (ItemEntity) unsafeClass.getMethod("allocateInstance", Class.class)
                .invoke(unsafe, ItemEntity.class);
        Method listener = Class.forName("com.extrarawstyle.veinminerplus.mixin.Ae2MagnetPickupMixin")
                .getDeclaredMethod("veinminerplus$skipBypassedDrop", ItemEntityPickupEvent.Pre.class,
                        CallbackInfo.class);
        listener.setAccessible(true);
        for (boolean protectedDrop : List.of(false, true)) {
            item.getPersistentData().putBoolean("veinminerplusBypassAeStorage", protectedDrop);
            for (TriState pickup : TriState.values()) {
                var event = new ItemEntityPickupEvent.Pre(null, item);
                event.setCanPickup(pickup);
                var callback = new CallbackInfo("handle", true);
                listener.invoke(null, event, callback);
                require(callback.isCancelled() == protectedDrop, "AE listener protection ignored the drop marker");
                require(event.canPickup() == pickup, "Skipping AE storage changed normal inventory pickup");
            }
        }
    }

    private static void checkBlacklist() throws Exception {
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        require(Config.parseWhitelistText("x".repeat(Config.MAX_WHITELIST_ENTRY_LENGTH)).isEmpty(),
                "Namespace expansion exceeded the rule length bound");
        require(Config.BLOCK_BLACKLIST.get().isEmpty(), "Blacklist should default to empty");
        Config.BLOCK_BLACKLIST.set(List.of("minecraft:stone"));
        require(Config.isBlockBlacklisted(stone), "Exact blacklist ID did not match");
        require(!Config.isBlockBlacklisted(dirt), "Unlisted block was rejected");
        Method eligibility = ChainEvents.class.getDeclaredMethod("eligibilityFailure", ServerLevel.class,
                ServerPlayer.class, BlockPos.class, BlockState.class, Block.class, ChainMode.class, Set.class);
        eligibility.setAccessible(true);
        for (ChainMode mode : ChainMode.values()) {
            String reason = (String) eligibility.invoke(null, null, null, BlockPos.ZERO, stone, Blocks.STONE,
                    mode, Set.of("*"));
            require("blacklisted".equals(reason), "Blacklist did not take priority in " + mode);
        }
        Config.BLOCK_BLACKLIST.set(List.of("*_ore"));
        require(Config.isBlockBlacklisted(Blocks.IRON_ORE.defaultBlockState()), "Path wildcard did not match");
        require(!Config.isBlockBlacklisted(stone), "Changing rules kept a stale blacklist match");
        Config.BLOCK_BLACKLIST.set(List.of("minecraft:*"));
        require(Config.isBlockBlacklisted(stone) && Config.isBlockBlacklisted(dirt),
                "Namespaced wildcard did not match");
        TagKey<Block> testTag = TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath("veinminerplus", "blacklist_test"));
        BuiltInRegistries.BLOCK.bindTags(Map.of(testTag, List.of(BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.STONE))));
        Config.BLOCK_BLACKLIST.set(List.of("#veinminerplus:blacklist_test"));
        require(Config.isBlockBlacklisted(stone), "Blacklist block tag did not match");
        BuiltInRegistries.BLOCK.bindTags(Map.of(testTag, List.of(BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.DIRT))));
        require(!Config.isBlockBlacklisted(stone) && Config.isBlockBlacklisted(dirt),
                "Tag reload kept a stale blacklist match");
        Config.setClientBlacklist("minecraft:stone");
        require(Config.isClientBlockBlacklisted(stone) && !Config.isClientBlockBlacklisted(dirt),
                "Client blacklist did not use server rules");
        Config.resetClientBlacklist();
        Config.BLOCK_BLACKLIST.set(List.of());
        require(!Config.isBlockBlacklisted(stone), "Clearing blacklist kept a stale match");
    }

    @SuppressWarnings("unchecked")
    private static void checkBlacklistCache() throws Exception {
        Field field = ChainEvents.class.getDeclaredField("PERSISTENT_CHUNK_SCAN_CACHE");
        field.setAccessible(true);
        Map<Long, List<BlockPos>> cache = (Map<Long, List<BlockPos>>) field.get(null);
        BlockState stone = Blocks.STONE.defaultBlockState();
        Config.BLOCK_BLACKLIST.set(List.of("minecraft:stone"));
        require(Config.isBlockBlacklisted(stone), "Blacklist cache setup failed");
        cache.put(1L, List.of(BlockPos.ZERO));
        cache.put(2L, List.of());
        require(Config.isBlockBlacklisted(stone) && cache.size() == 2,
                "Unchanged blacklist discarded useful scan results");
        Config.BLOCK_BLACKLIST.set(List.of());
        require(!Config.isBlockBlacklisted(stone) && cache.isEmpty(),
                "Removing a blacklist rule kept filtered chunk results");
        Config.BLOCK_BLACKLIST.set(List.of("minecraft:stone"));
        require(Config.isBlockBlacklisted(stone), "Blacklist reload setup failed");
        cache.put(1L, List.of(BlockPos.ZERO));
        cache.put(2L, List.of());
        loadConfig();
        require(!Config.isBlockBlacklisted(stone) && cache.isEmpty(),
                "Reloading the config kept filtered chunk results");
        cache.put(1L, List.of(BlockPos.ZERO));
        cache.put(2L, List.of());
        ChainEvents events = new ChainEvents();
        events.onTagsUpdated(new TagsUpdatedEvent(RegistryAccess.EMPTY, true, true));
        require(cache.size() == 2, "Integrated client tag notification discarded server scan results");
        events.onTagsUpdated(new TagsUpdatedEvent(RegistryAccess.EMPTY, false, false));
        require(cache.isEmpty(), "Reloading block tags kept filtered chunk results");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
