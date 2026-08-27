package com.extrarawstyle.veinminerplus;

import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;

public final class NetworkHandler {
    private static final String PROTOCOL_VERSION = "2";
    private static int packetId;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(VeinMinerPlus.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private NetworkHandler() {
    }

    static void register() {
        CHANNEL.registerMessage(packetId++, KeyStatePayload.class,
                (payload, buffer) -> buffer.writeBoolean(payload.held()),
                buffer -> new KeyStatePayload(buffer.readBoolean()),
                NetworkHandler::handleKeyState,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++, ModeChangePayload.class,
                (payload, buffer) -> buffer.writeVarInt(payload.mode()),
                buffer -> new ModeChangePayload(buffer.readVarInt()),
                NetworkHandler::handleModeChange,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(packetId++, ConfigSnapshotPayload.class,
                NetworkHandler::writeConfigSnapshot, NetworkHandler::readConfigSnapshot,
                NetworkHandler::handleConfigSnapshot, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(packetId++, ConfigUpdatePayload.class,
                NetworkHandler::writeConfigUpdate, NetworkHandler::readConfigUpdate,
                NetworkHandler::handleConfigUpdate, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    static void sendKeyState(boolean held) {
        CHANNEL.sendToServer(new KeyStatePayload(held));
    }

    static void sendModeChange(ChainMode mode) {
        CHANNEL.sendToServer(new ModeChangePayload(mode.ordinal()));
    }

    static void openConfigScreen(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), ConfigSnapshotPayload.current(player));
    }

    static void sendConfigUpdate(ConfigUpdatePayload payload) {
        CHANNEL.sendToServer(payload);
    }

    private static void handleKeyState(KeyStatePayload payload, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ChainEvents.setKeyHeld(player, payload.held());
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleModeChange(ModeChangePayload payload, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ChainEvents.setMode(player, payload.mode());
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleConfigSnapshot(ConfigSnapshotPayload payload,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> VeinMinerPlusClient.openConfigScreen(payload));
        context.setPacketHandled(true);
    }

    private static void handleConfigUpdate(ConfigUpdatePayload payload, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.hasPermissions(2)) {
                Config.MAX_NORMAL_BLOCKS.set(Mth.clamp(payload.maxNormalBlocks(), 32, 32767));
                Config.MAX_NORMAL_BLOCKS_PER_TICK.set(Mth.clamp(payload.maxNormalBlocksPerTick(), 1, 384));
                Config.MAX_BLAST_BLOCKS.set(Mth.clamp(payload.maxBlastBlocks(), 32, 32767));
                Config.MAX_BLAST_BLOCKS_PER_TICK.set(Mth.clamp(payload.maxBlastBlocksPerTick(), 1, 512));
                Config.BLAST_SEARCH_DISTANCE.set(Mth.clamp(payload.blastSearchDistance(), 3, 128));
                Config.BLAST_LOW_TPS_THRESHOLD.set(Mth.clamp(payload.blastLowTpsThreshold(), 5, 20));
                Config.BLAST_MANHATTAN.set(payload.blastManhattan());
                Config.BLAST_AUTO_REDUCE_RADIUS.set(payload.blastAutoReduceRadius());
                Config.CONSUME_HUNGER.set(payload.consumeHunger());
                Config.DEFAULT_MODE.set(Mth.clamp(payload.mode(), 0, ChainMode.values().length - 1));
                ChainEvents.setMode(player, payload.mode());
                Config.SPEC.save();
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.veinminerplus.config_saved"), false);
            }
        });
        context.setPacketHandled(true);
    }

    private record KeyStatePayload(boolean held) {
    }

    private record ModeChangePayload(int mode) {
    }

    public record ConfigSnapshotPayload(int maxNormalBlocks, int maxNormalBlocksPerTick,
            int maxBlastBlocks, int maxBlastBlocksPerTick, int blastSearchDistance,
            int blastLowTpsThreshold, boolean blastManhattan, boolean blastAutoReduceRadius,
            boolean consumeHunger, int mode) {
        static ConfigSnapshotPayload current(ServerPlayer player) {
            return new ConfigSnapshotPayload(Config.MAX_NORMAL_BLOCKS.get(), Config.MAX_NORMAL_BLOCKS_PER_TICK.get(),
                    Config.MAX_BLAST_BLOCKS.get(), Config.MAX_BLAST_BLOCKS_PER_TICK.get(),
                    Config.BLAST_SEARCH_DISTANCE.get(), Config.BLAST_LOW_TPS_THRESHOLD.get(),
                    Config.BLAST_MANHATTAN.get(), Config.BLAST_AUTO_REDUCE_RADIUS.get(),
                    Config.CONSUME_HUNGER.get(), ChainEvents.getMode(player).ordinal());
        }
    }

    public record ConfigUpdatePayload(int maxNormalBlocks, int maxNormalBlocksPerTick,
            int maxBlastBlocks, int maxBlastBlocksPerTick, int blastSearchDistance,
            int blastLowTpsThreshold, boolean blastManhattan, boolean blastAutoReduceRadius,
            boolean consumeHunger, int mode) {
    }

    private static void writeConfigSnapshot(ConfigSnapshotPayload payload, net.minecraft.network.FriendlyByteBuf buffer) {
        writeConfig(buffer, payload.maxNormalBlocks(), payload.maxNormalBlocksPerTick(), payload.maxBlastBlocks(),
                payload.maxBlastBlocksPerTick(), payload.blastSearchDistance(), payload.blastLowTpsThreshold(),
                payload.blastManhattan(), payload.blastAutoReduceRadius(), payload.consumeHunger(), payload.mode());
    }

    private static ConfigSnapshotPayload readConfigSnapshot(net.minecraft.network.FriendlyByteBuf buffer) {
        ConfigValues values = readConfig(buffer);
        return new ConfigSnapshotPayload(values.maxNormalBlocks(), values.maxNormalBlocksPerTick(),
                values.maxBlastBlocks(), values.maxBlastBlocksPerTick(), values.blastSearchDistance(),
                values.blastLowTpsThreshold(), values.blastManhattan(), values.blastAutoReduceRadius(),
                values.consumeHunger(), values.mode());
    }

    private static void writeConfigUpdate(ConfigUpdatePayload payload, net.minecraft.network.FriendlyByteBuf buffer) {
        writeConfig(buffer, payload.maxNormalBlocks(), payload.maxNormalBlocksPerTick(), payload.maxBlastBlocks(),
                payload.maxBlastBlocksPerTick(), payload.blastSearchDistance(), payload.blastLowTpsThreshold(),
                payload.blastManhattan(), payload.blastAutoReduceRadius(), payload.consumeHunger(), payload.mode());
    }

    private static ConfigUpdatePayload readConfigUpdate(net.minecraft.network.FriendlyByteBuf buffer) {
        ConfigValues values = readConfig(buffer);
        return new ConfigUpdatePayload(values.maxNormalBlocks(), values.maxNormalBlocksPerTick(),
                values.maxBlastBlocks(), values.maxBlastBlocksPerTick(), values.blastSearchDistance(),
                values.blastLowTpsThreshold(), values.blastManhattan(), values.blastAutoReduceRadius(),
                values.consumeHunger(), values.mode());
    }

    private static void writeConfig(net.minecraft.network.FriendlyByteBuf buffer, int maxNormalBlocks,
            int maxNormalBlocksPerTick, int maxBlastBlocks, int maxBlastBlocksPerTick,
            int blastSearchDistance, int blastLowTpsThreshold, boolean blastManhattan,
            boolean blastAutoReduceRadius, boolean consumeHunger, int mode) {
        buffer.writeVarInt(maxNormalBlocks);
        buffer.writeVarInt(maxNormalBlocksPerTick);
        buffer.writeVarInt(maxBlastBlocks);
        buffer.writeVarInt(maxBlastBlocksPerTick);
        buffer.writeVarInt(blastSearchDistance);
        buffer.writeVarInt(blastLowTpsThreshold);
        buffer.writeBoolean(blastManhattan);
        buffer.writeBoolean(blastAutoReduceRadius);
        buffer.writeBoolean(consumeHunger);
        buffer.writeVarInt(mode);
    }

    private static ConfigValues readConfig(net.minecraft.network.FriendlyByteBuf buffer) {
        return new ConfigValues(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readVarInt());
    }

    private record ConfigValues(int maxNormalBlocks, int maxNormalBlocksPerTick, int maxBlastBlocks,
            int maxBlastBlocksPerTick, int blastSearchDistance, int blastLowTpsThreshold,
            boolean blastManhattan, boolean blastAutoReduceRadius, boolean consumeHunger, int mode) {
    }
}
