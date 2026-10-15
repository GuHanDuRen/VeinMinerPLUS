package com.extrarawstyle.veinminerplus;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class NetworkHandler {
    private static final String PROTOCOL_VERSION = "5";
    public static final int MAX_WHITELIST_TEXT_LENGTH = 4096;
    public static final int MAX_CONFIG_SNAPSHOT_TEXT_LENGTH =
            Config.MAX_WHITELIST_ENTRIES * (Config.MAX_WHITELIST_ENTRY_LENGTH + 1);
    private static SimpleNetworkWrapper CHANNEL;
    private static int packetId;
    private static long progressSequence;

    private NetworkHandler() {
    }

    public static void register() {
        if (CHANNEL != null) {
            return;
        }
        CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(VeinMinerPlus.MODID + ":main");
        CHANNEL.registerMessage(KeyStateHandler.class, KeyStateMessage.class, packetId++, Side.SERVER);
        CHANNEL.registerMessage(ModeChangeHandler.class, ModeChangeMessage.class, packetId++, Side.SERVER);
        CHANNEL.registerMessage(ConfigRequestHandler.class, ConfigRequestMessage.class, packetId++, Side.SERVER);
        CHANNEL.registerMessage(ConfigSnapshotHandler.class, ConfigSnapshotMessage.class, packetId++, Side.CLIENT);
        CHANNEL.registerMessage(ConfigUpdateHandler.class, ConfigUpdateMessage.class, packetId++, Side.SERVER);
        CHANNEL.registerMessage(ProgressHandler.class, ProgressMessage.class, packetId++, Side.CLIENT);
        CHANNEL.registerMessage(LowTpsRadiusNoticeHandler.class, LowTpsRadiusMessage.class, packetId++, Side.CLIENT);
        CHANNEL.registerMessage(BlacklistSyncHandler.class, BlacklistSyncMessage.class, packetId++, Side.CLIENT);
    }

    public static void sendKeyState(boolean held, boolean bypassAeStorage) {
        CHANNEL.sendToServer(new KeyStateMessage(held, bypassAeStorage));
    }

    public static void sendModeChange(ChainMode mode) {
        CHANNEL.sendToServer(new ModeChangeMessage(mode.id()));
    }

    public static void sendProgress(EntityPlayerMP player, int count) {
        if (player != null && CHANNEL != null) {
            CHANNEL.sendTo(new ProgressMessage(true, count, nextProgressSequence()), player);
        }
    }

    public static void clearProgress(EntityPlayerMP player, int count) {
        if (player != null && CHANNEL != null) {
            CHANNEL.sendTo(new ProgressMessage(false, count, nextProgressSequence()), player);
        }
    }

    public static void sendLowTpsRadiusNotice(EntityPlayerMP player, double tps, int oldDistance,
            int reducedDistance) {
        if (player != null && CHANNEL != null) {
            CHANNEL.sendTo(new LowTpsRadiusMessage(tps, oldDistance, reducedDistance), player);
        }
    }

    private static long nextProgressSequence() {
        return ++progressSequence;
    }

    public static void openConfigScreen(EntityPlayerMP player) {
        CHANNEL.sendTo(new ConfigSnapshotMessage(player), player);
    }

    public static void sendConfigUpdate(ConfigUpdateMessage payload) {
        CHANNEL.sendToServer(payload);
    }

    public static void syncBlacklist(EntityPlayerMP player) {
        if (CHANNEL != null) {
            CHANNEL.sendTo(new BlacklistSyncMessage(Config.blacklistText()), player);
        }
    }

    public static void broadcastBlacklist() {
        if (CHANNEL != null && net.minecraftforge.fml.common.FMLCommonHandler.instance()
                .getMinecraftServerInstance() != null) {
            CHANNEL.sendToAll(new BlacklistSyncMessage(Config.blacklistText()));
        }
    }

    public static class KeyStateMessage implements IMessage {
        private boolean held;
        private boolean bypassAeStorage;

        public KeyStateMessage() {
        }

        public KeyStateMessage(boolean held, boolean bypassAeStorage) {
            this.held = held;
            this.bypassAeStorage = bypassAeStorage;
        }

        public boolean isHeld() {
            return held;
        }

        public boolean isBypassAeStorage() {
            return bypassAeStorage;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            held = buf.readBoolean();
            bypassAeStorage = buf.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeBoolean(held);
            buf.writeBoolean(bypassAeStorage);
        }
    }

    public static class ModeChangeMessage implements IMessage {
        private int mode;

        public ModeChangeMessage() {
        }

        public ModeChangeMessage(int mode) {
            this.mode = mode;
        }

        public int getMode() {
            return mode;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            mode = buf.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(mode);
        }
    }

    public static class ConfigRequestMessage implements IMessage {
        public ConfigRequestMessage() {
        }

        @Override
        public void fromBytes(ByteBuf buf) {
        }

        @Override
        public void toBytes(ByteBuf buf) {
        }
    }

    public static class ConfigSnapshotMessage implements IMessage {
        public int maxNormalBlocks;
        public int maxNormalBlocksPerTick;
        public int maxBlastBlocks;
        public int maxBlastBlocksPerTick;
        public int blastSearchDistance;
        public int blastChunkScansPerTick;
        public int blastLowTpsThreshold;
        public boolean blastManhattan;
        public boolean blastAutoReduceRadius;
        public boolean consumeHunger;
        public int mode;
        public String blockWhitelist;
        public String blockBlacklist;

        public ConfigSnapshotMessage() {
        }

        public ConfigSnapshotMessage(EntityPlayerMP player) {
            maxNormalBlocks = Config.maxNormalBlocks;
            maxNormalBlocksPerTick = Config.maxNormalBlocksPerTick;
            maxBlastBlocks = Config.maxBlastBlocks;
            maxBlastBlocksPerTick = Config.maxBlastBlocksPerTick;
            blastSearchDistance = Config.blastSearchDistance;
            blastChunkScansPerTick = Config.blastChunkScansPerTick;
            blastLowTpsThreshold = Config.blastLowTpsThreshold;
            blastManhattan = Config.blastManhattan;
            blastAutoReduceRadius = Config.blastAutoReduceRadius;
            consumeHunger = Config.consumeHunger;
            mode = ChainEvents.getMode(player).id();
            blockWhitelist = Config.whitelistText();
            blockBlacklist = Config.blacklistText();
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            maxNormalBlocks = buf.readInt();
            maxNormalBlocksPerTick = buf.readInt();
            maxBlastBlocks = buf.readInt();
            maxBlastBlocksPerTick = buf.readInt();
            blastSearchDistance = buf.readInt();
            blastChunkScansPerTick = buf.readInt();
            blastLowTpsThreshold = buf.readInt();
            blastManhattan = buf.readBoolean();
            blastAutoReduceRadius = buf.readBoolean();
            consumeHunger = buf.readBoolean();
            mode = buf.readInt();
            blockWhitelist = readWhitelist(buf, ruleTextLimit());
            blockBlacklist = readWhitelist(buf, ruleTextLimit());
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(maxNormalBlocks);
            buf.writeInt(maxNormalBlocksPerTick);
            buf.writeInt(maxBlastBlocks);
            buf.writeInt(maxBlastBlocksPerTick);
            buf.writeInt(blastSearchDistance);
            buf.writeInt(blastChunkScansPerTick);
            buf.writeInt(blastLowTpsThreshold);
            buf.writeBoolean(blastManhattan);
            buf.writeBoolean(blastAutoReduceRadius);
            buf.writeBoolean(consumeHunger);
            buf.writeInt(mode);
            writeWhitelist(buf, blockWhitelist, ruleTextLimit());
            writeWhitelist(buf, blockBlacklist, ruleTextLimit());
        }

        protected int ruleTextLimit() {
            return MAX_CONFIG_SNAPSHOT_TEXT_LENGTH;
        }
    }

    public static class ConfigUpdateMessage extends ConfigSnapshotMessage {
        public ConfigUpdateMessage() {
        }

        public ConfigUpdateMessage(int maxNormalBlocks, int maxNormalBlocksPerTick, int maxBlastBlocks,
                int maxBlastBlocksPerTick, int blastSearchDistance, int blastChunkScansPerTick,
                int blastLowTpsThreshold,
                boolean blastManhattan, boolean blastAutoReduceRadius, boolean consumeHunger, int mode,
                String blockWhitelist, String blockBlacklist) {
            this.maxNormalBlocks = maxNormalBlocks;
            this.maxNormalBlocksPerTick = maxNormalBlocksPerTick;
            this.maxBlastBlocks = maxBlastBlocks;
            this.maxBlastBlocksPerTick = maxBlastBlocksPerTick;
            this.blastSearchDistance = blastSearchDistance;
            this.blastChunkScansPerTick = blastChunkScansPerTick;
            this.blastLowTpsThreshold = blastLowTpsThreshold;
            this.blastManhattan = blastManhattan;
            this.blastAutoReduceRadius = blastAutoReduceRadius;
            this.consumeHunger = consumeHunger;
            this.mode = mode;
            this.blockWhitelist = blockWhitelist;
            this.blockBlacklist = blockBlacklist;
        }

        @Override
        protected int ruleTextLimit() {
            return MAX_WHITELIST_TEXT_LENGTH;
        }
    }

    public static class BlacklistSyncMessage implements IMessage {
        private String blockBlacklist;

        public BlacklistSyncMessage() {
        }

        public BlacklistSyncMessage(String blockBlacklist) {
            this.blockBlacklist = blockBlacklist;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            blockBlacklist = readWhitelist(buf, MAX_CONFIG_SNAPSHOT_TEXT_LENGTH);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            writeWhitelist(buf, blockBlacklist, MAX_CONFIG_SNAPSHOT_TEXT_LENGTH);
        }
    }

    public static class ProgressMessage implements IMessage {
        private boolean active;
        private int count;
        private long sequence;

        public ProgressMessage() {
        }

        public ProgressMessage(boolean active, int count, long sequence) {
            this.active = active;
            this.count = count;
            this.sequence = sequence;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            active = buf.readBoolean();
            count = buf.readInt();
            sequence = buf.readLong();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeBoolean(active);
            buf.writeInt(count);
            buf.writeLong(sequence);
        }
    }

    public static class LowTpsRadiusMessage implements IMessage {
        private double tps;
        private int oldDistance;
        private int reducedDistance;

        public LowTpsRadiusMessage() {
        }

        public LowTpsRadiusMessage(double tps, int oldDistance, int reducedDistance) {
            this.tps = tps;
            this.oldDistance = oldDistance;
            this.reducedDistance = reducedDistance;
        }

        public double getTps() {
            return tps;
        }

        public int getOldDistance() {
            return oldDistance;
        }

        public int getReducedDistance() {
            return reducedDistance;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            tps = buf.readDouble();
            oldDistance = buf.readInt();
            reducedDistance = buf.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeDouble(tps);
            buf.writeInt(oldDistance);
            buf.writeInt(reducedDistance);
        }
    }

    private static void writeWhitelist(ByteBuf buf, String value, int maxLength) {
        String text = value == null ? "" : value;
        if (text.length() > maxLength) {
            throw new IllegalArgumentException("Block rule text exceeds " + maxLength + " characters");
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readWhitelist(ByteBuf buf, int maxLength) {
        if (buf.readableBytes() < 4) {
            throw new IllegalArgumentException("Missing block rule text length");
        }
        int length = buf.readInt();
        if (length < 0 || length > maxLength * 4 || length > buf.readableBytes()) {
            throw new IllegalArgumentException("Invalid block rule text length: " + length);
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.length() > maxLength) {
            throw new IllegalArgumentException("Block rule text exceeds " + maxLength + " characters");
        }
        return text;
    }

    public static class KeyStateHandler implements IMessageHandler<KeyStateMessage, IMessage> {
        @Override
        public IMessage onMessage(final KeyStateMessage message, MessageContext context) {
            if (context.getServerHandler() != null) {
                context.getServerHandler().player.getServerWorld().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        ChainEvents.setKeyHeld(context.getServerHandler().player, message.isHeld(),
                                message.isBypassAeStorage());
                    }
                });
            }
            return null;
        }
    }

    public static class ModeChangeHandler implements IMessageHandler<ModeChangeMessage, IMessage> {
        @Override
        public IMessage onMessage(final ModeChangeMessage message, MessageContext context) {
            if (context.getServerHandler() != null) {
                context.getServerHandler().player.getServerWorld().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        ChainEvents.setMode(context.getServerHandler().player, message.getMode());
                    }
                });
            }
            return null;
        }
    }

    public static class ConfigRequestHandler implements IMessageHandler<ConfigRequestMessage, IMessage> {
        @Override
        public IMessage onMessage(final ConfigRequestMessage message, MessageContext context) {
            if (context.getServerHandler() != null) {
                context.getServerHandler().player.getServerWorld().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        EntityPlayerMP player = context.getServerHandler().player;
                        if (player != null && player.canUseCommand(2, "veinminerplus")) {
                            openConfigScreen(player);
                        } else if (player != null) {
                            player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(
                                    "message.veinminerplus.config_permission"), true);
                        }
                    }
                });
            }
            return null;
        }
    }

    public static class ConfigSnapshotHandler implements IMessageHandler<ConfigSnapshotMessage, IMessage> {
        @Override
        public IMessage onMessage(final ConfigSnapshotMessage message, MessageContext context) {
            if (context.side == Side.CLIENT) {
                net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        VeinMinerPlusClient.openConfigScreen(message);
                    }
                });
            }
            return null;
        }
    }

    public static class ConfigUpdateHandler implements IMessageHandler<ConfigUpdateMessage, IMessage> {
        @Override
        public IMessage onMessage(final ConfigUpdateMessage message, MessageContext context) {
            if (context.getServerHandler() != null) {
                context.getServerHandler().player.getServerWorld().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        EntityPlayerMP player = context.getServerHandler().player;
                        if (player != null && player.canUseCommand(2, "veinminerplus")) {
                            Config.maxNormalBlocks = clamp(message.maxNormalBlocks, 32, Integer.MAX_VALUE);
                            Config.maxNormalBlocksPerTick = clamp(message.maxNormalBlocksPerTick, 1, Integer.MAX_VALUE);
                            Config.maxBlastBlocks = clamp(message.maxBlastBlocks, 32, Integer.MAX_VALUE);
                            Config.maxBlastBlocksPerTick = clamp(message.maxBlastBlocksPerTick, 1, Integer.MAX_VALUE);
                            Config.blastSearchDistance = clamp(message.blastSearchDistance, 3, Integer.MAX_VALUE);
                            Config.blastChunkScansPerTick = clamp(message.blastChunkScansPerTick, 1,
                                    Config.MAX_BLAST_CHUNK_SCANS_PER_TICK);
                            Config.blastLowTpsThreshold = clamp(message.blastLowTpsThreshold, 5, 20);
                            Config.blastManhattan = message.blastManhattan;
                            Config.blastAutoReduceRadius = message.blastAutoReduceRadius;
                            Config.consumeHunger = message.consumeHunger;
                            Config.blockWhitelist = new java.util.ArrayList<String>(
                                    Config.parseWhitelistText(message.blockWhitelist));
                            Config.blockBlacklist = new java.util.ArrayList<String>(
                                    Config.parseWhitelistText(message.blockBlacklist));
                            Config.defaultMode = clamp(message.mode, 0, ChainMode.values().length - 1);
                            ChainEvents.setMode(player, Config.defaultMode);
                            Config.save();
                            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation(
                                    "message.veinminerplus.config_saved"));
                        }
                    }
                });
            }
            return null;
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    public static class ProgressHandler implements IMessageHandler<ProgressMessage, IMessage> {
        @Override
        public IMessage onMessage(final ProgressMessage message, MessageContext context) {
            if (context.side == Side.CLIENT) {
                net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        VeinMinerPlusClient.updateChainProgress(message.sequence, message.active, message.count);
                    }
                });
            }
            return null;
        }
    }

    public static class BlacklistSyncHandler implements IMessageHandler<BlacklistSyncMessage, IMessage> {
        @Override
        public IMessage onMessage(final BlacklistSyncMessage message, MessageContext context) {
            if (context.side == Side.CLIENT) {
                net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        VeinMinerPlusClient.updateBlacklist(message.blockBlacklist);
                    }
                });
            }
            return null;
        }
    }

    public static class LowTpsRadiusNoticeHandler implements IMessageHandler<LowTpsRadiusMessage, IMessage> {
        @Override
        public IMessage onMessage(final LowTpsRadiusMessage message, MessageContext context) {
            if (context.side == Side.CLIENT) {
                net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        VeinMinerPlusClient.updateLowTpsRadiusNotice(message.getTps(),
                                message.getOldDistance(), message.getReducedDistance());
                    }
                });
            }
            return null;
        }
    }
}
