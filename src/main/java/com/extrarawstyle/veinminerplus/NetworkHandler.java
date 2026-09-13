package com.extrarawstyle.veinminerplus;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class NetworkHandler {
    private static final String PROTOCOL_VERSION = "5";
    static final int MAX_WHITELIST_TEXT_LENGTH = 4096;

    private NetworkHandler() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(KeyStatePayload.TYPE, KeyStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ChainEvents.setKeyHeld(context.player(), payload.held())));
        registrar.playToServer(ModeChangePayload.TYPE, ModeChangePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ChainEvents.setMode(context.player(), payload.mode())));
        registrar.playToServer(ConfigRequestPayload.TYPE, ConfigRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        if (player.hasPermissions(2)) {
                            openConfigScreen(player);
                        } else {
                            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                                    "message.veinminerplus.config_permission"), true);
                        }
                    }
                }));
        registrar.playToClient(ConfigSnapshotPayload.TYPE, ConfigSnapshotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> VeinMinerPlusClient.openConfigScreen(payload)));
        registrar.playToServer(ConfigUpdatePayload.TYPE, ConfigUpdatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.hasPermissions(2)) {
                        applyConfig(player, payload);
                    }
                }));
    }

    static void sendKeyState(boolean held) {
        PacketDistributor.sendToServer(new KeyStatePayload(held));
    }

    static void sendModeChange(ChainMode mode) {
        PacketDistributor.sendToServer(new ModeChangePayload(mode.id()));
    }

    static void openConfigScreen(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, ConfigSnapshotPayload.current(player));
    }

    static void sendConfigUpdate(ConfigUpdatePayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private static void applyConfig(ServerPlayer player, ConfigUpdatePayload payload) {
        Config.MAX_NORMAL_BLOCKS.set(Mth.clamp(payload.maxNormalBlocks(), 32, Integer.MAX_VALUE));
        Config.MAX_NORMAL_BLOCKS_PER_TICK.set(Mth.clamp(payload.maxNormalBlocksPerTick(), 1, Integer.MAX_VALUE));
        Config.MAX_BLAST_BLOCKS.set(Mth.clamp(payload.maxBlastBlocks(), 32, Integer.MAX_VALUE));
        Config.MAX_BLAST_BLOCKS_PER_TICK.set(Mth.clamp(payload.maxBlastBlocksPerTick(), 1, Integer.MAX_VALUE));
        Config.BLAST_SEARCH_DISTANCE.set(Mth.clamp(payload.blastSearchDistance(), 3, Integer.MAX_VALUE));
        Config.BLAST_CHUNK_SCANS_PER_TICK.set(Mth.clamp(payload.blastChunkScansPerTick(), 1,
                Config.MAX_BLAST_CHUNK_SCANS_PER_TICK));
        Config.BLAST_LOW_TPS_THRESHOLD.set(Mth.clamp(payload.blastLowTpsThreshold(), 5, 20));
        Config.BLAST_MANHATTAN.set(payload.blastManhattan());
        Config.BLAST_AUTO_REDUCE_RADIUS.set(payload.blastAutoReduceRadius());
        Config.CONSUME_HUNGER.set(payload.consumeHunger());
        Config.BLOCK_WHITELIST.set(Config.parseWhitelistText(payload.blockWhitelist()));
        Config.DEFAULT_MODE.set(Mth.clamp(payload.mode(), 0, ChainMode.values().length - 1));
        ChainEvents.setMode(player, payload.mode());
        Config.SPEC.save();
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.veinminerplus.config_saved"), false);
    }

    public record KeyStatePayload(boolean held) implements CustomPacketPayload {
        public static final Type<KeyStatePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(VeinMinerPlus.MODID, "key_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, KeyStatePayload> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeBoolean(payload.held),
                buffer -> new KeyStatePayload(buffer.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ModeChangePayload(int mode) implements CustomPacketPayload {
        public static final Type<ModeChangePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(VeinMinerPlus.MODID, "mode_change"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ModeChangePayload> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeVarInt(payload.mode),
                buffer -> new ModeChangePayload(buffer.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ConfigRequestPayload() implements CustomPacketPayload {
        public static final Type<ConfigRequestPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(VeinMinerPlus.MODID, "config_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigRequestPayload> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                },
                buffer -> new ConfigRequestPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ConfigSnapshotPayload(int maxNormalBlocks, int maxNormalBlocksPerTick,
            int maxBlastBlocks, int maxBlastBlocksPerTick, int blastSearchDistance,
            int blastChunkScansPerTick, int blastLowTpsThreshold, boolean blastManhattan,
            boolean blastAutoReduceRadius,
            boolean consumeHunger, int mode, String blockWhitelist) implements CustomPacketPayload {
        public static final Type<ConfigSnapshotPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(VeinMinerPlus.MODID, "config_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSnapshotPayload> STREAM_CODEC = StreamCodec.of(
                NetworkHandler::writeConfigSnapshot, NetworkHandler::readConfigSnapshot);

        static ConfigSnapshotPayload current(ServerPlayer player) {
            return new ConfigSnapshotPayload(Config.MAX_NORMAL_BLOCKS.getAsInt(),
                    Config.MAX_NORMAL_BLOCKS_PER_TICK.getAsInt(), Config.MAX_BLAST_BLOCKS.getAsInt(),
                    Config.MAX_BLAST_BLOCKS_PER_TICK.getAsInt(), Config.BLAST_SEARCH_DISTANCE.getAsInt(),
                    Config.BLAST_CHUNK_SCANS_PER_TICK.getAsInt(),
                    Config.BLAST_LOW_TPS_THRESHOLD.getAsInt(), Config.BLAST_MANHATTAN.getAsBoolean(),
                    Config.BLAST_AUTO_REDUCE_RADIUS.getAsBoolean(), Config.CONSUME_HUNGER.getAsBoolean(),
                    ChainEvents.getMode(player).id(), Config.whitelistText());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ConfigUpdatePayload(int maxNormalBlocks, int maxNormalBlocksPerTick,
            int maxBlastBlocks, int maxBlastBlocksPerTick, int blastSearchDistance,
            int blastChunkScansPerTick, int blastLowTpsThreshold, boolean blastManhattan,
            boolean blastAutoReduceRadius,
            boolean consumeHunger, int mode, String blockWhitelist) implements CustomPacketPayload {
        public static final Type<ConfigUpdatePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(VeinMinerPlus.MODID, "config_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigUpdatePayload> STREAM_CODEC = StreamCodec.of(
                NetworkHandler::writeConfigUpdate, NetworkHandler::readConfigUpdate);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void writeConfigSnapshot(RegistryFriendlyByteBuf buffer, ConfigSnapshotPayload payload) {
        writeConfig(buffer, payload.maxNormalBlocks(), payload.maxNormalBlocksPerTick(), payload.maxBlastBlocks(),
                payload.maxBlastBlocksPerTick(), payload.blastSearchDistance(), payload.blastChunkScansPerTick(),
                payload.blastLowTpsThreshold(),
                payload.blastManhattan(), payload.blastAutoReduceRadius(), payload.consumeHunger(), payload.mode(),
                payload.blockWhitelist());
    }

    private static ConfigSnapshotPayload readConfigSnapshot(RegistryFriendlyByteBuf buffer) {
        ConfigValues values = readConfig(buffer);
        return new ConfigSnapshotPayload(values.maxNormalBlocks(), values.maxNormalBlocksPerTick(),
                values.maxBlastBlocks(), values.maxBlastBlocksPerTick(), values.blastSearchDistance(),
                values.blastChunkScansPerTick(), values.blastLowTpsThreshold(), values.blastManhattan(),
                values.blastAutoReduceRadius(),
                values.consumeHunger(), values.mode(), values.blockWhitelist());
    }

    private static void writeConfigUpdate(RegistryFriendlyByteBuf buffer, ConfigUpdatePayload payload) {
        writeConfig(buffer, payload.maxNormalBlocks(), payload.maxNormalBlocksPerTick(), payload.maxBlastBlocks(),
                payload.maxBlastBlocksPerTick(), payload.blastSearchDistance(), payload.blastChunkScansPerTick(),
                payload.blastLowTpsThreshold(),
                payload.blastManhattan(), payload.blastAutoReduceRadius(), payload.consumeHunger(), payload.mode(),
                payload.blockWhitelist());
    }

    private static ConfigUpdatePayload readConfigUpdate(RegistryFriendlyByteBuf buffer) {
        ConfigValues values = readConfig(buffer);
        return new ConfigUpdatePayload(values.maxNormalBlocks(), values.maxNormalBlocksPerTick(),
                values.maxBlastBlocks(), values.maxBlastBlocksPerTick(), values.blastSearchDistance(),
                values.blastChunkScansPerTick(), values.blastLowTpsThreshold(), values.blastManhattan(),
                values.blastAutoReduceRadius(),
                values.consumeHunger(), values.mode(), values.blockWhitelist());
    }

    private static void writeConfig(RegistryFriendlyByteBuf buffer, int maxNormalBlocks,
            int maxNormalBlocksPerTick, int maxBlastBlocks, int maxBlastBlocksPerTick,
            int blastSearchDistance, int blastChunkScansPerTick, int blastLowTpsThreshold, boolean blastManhattan,
            boolean blastAutoReduceRadius, boolean consumeHunger, int mode, String blockWhitelist) {
        buffer.writeVarInt(maxNormalBlocks);
        buffer.writeVarInt(maxNormalBlocksPerTick);
        buffer.writeVarInt(maxBlastBlocks);
        buffer.writeVarInt(maxBlastBlocksPerTick);
        buffer.writeVarInt(blastSearchDistance);
        buffer.writeVarInt(blastChunkScansPerTick);
        buffer.writeVarInt(blastLowTpsThreshold);
        buffer.writeBoolean(blastManhattan);
        buffer.writeBoolean(blastAutoReduceRadius);
        buffer.writeBoolean(consumeHunger);
        buffer.writeVarInt(mode);
        buffer.writeUtf(blockWhitelist == null ? "" : blockWhitelist, MAX_WHITELIST_TEXT_LENGTH);
    }

    private static ConfigValues readConfig(RegistryFriendlyByteBuf buffer) {
        return new ConfigValues(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readVarInt(), buffer.readUtf(MAX_WHITELIST_TEXT_LENGTH));
    }

    private record ConfigValues(int maxNormalBlocks, int maxNormalBlocksPerTick, int maxBlastBlocks,
            int maxBlastBlocksPerTick, int blastSearchDistance, int blastChunkScansPerTick,
            int blastLowTpsThreshold,
            boolean blastManhattan, boolean blastAutoReduceRadius, boolean consumeHunger, int mode,
            String blockWhitelist) {
    }
}
