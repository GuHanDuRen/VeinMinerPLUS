package com.extrarawstyle.veinminerplus;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.block.BlockColored;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandHandler;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.oredict.OreDictionary;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class CompatibilityRegression {
    public static void main(String[] arguments) throws Exception {
        Field side = FMLLaunchHandler.class.getDeclaredField("side");
        side.setAccessible(true);
        side.set(null, Side.SERVER);
        packets();
        optionalAe();
        config();
        blacklist();
        lateOreRegistration();
        for (String argument : arguments) {
            aeApi(new File(argument));
        }
        System.out.println("Compatibility regression passed: key state, distinct rule packets and length limits, "
                + "config persistence, debug command/permissions/completion, blacklist precedence/cache invalidation, "
                + "metadata, late ore registration, "
                + "client sync, absent AE isolation.");
        if (arguments.length == 0) {
            System.out.println("AE binary API checks skipped: supply -PaeCompatibilityJars or downloaded research JARs.");
        }
    }

    private static void packets() throws Exception {
        for (boolean held : new boolean[] { false, true }) {
            for (boolean bypass : new boolean[] { false, true }) {
                ByteBuf buffer = Unpooled.buffer();
                try {
                    new NetworkHandler.KeyStateMessage(held, bypass).toBytes(buffer);
                    require(buffer.readableBytes() == 2, "Key packet lost a flag");
                    NetworkHandler.KeyStateMessage decoded = new NetworkHandler.KeyStateMessage();
                    decoded.fromBytes(buffer);
                    require(decoded.isHeld() == held && decoded.isBypassAeStorage() == bypass,
                            "Key flags did not round-trip");
                } finally {
                    buffer.release();
                }
            }
        }
        List<String> rules = new ArrayList<String>();
        for (int index = 0; index < Config.MAX_WHITELIST_ENTRIES; index++) {
            rules.add(String.format("test:%03d", index) + repeat('a', 120));
        }
        String whitelist = String.join("\n", Config.normalizeWhitelist(rules));
        Collections.reverse(rules);
        String blacklist = String.join("\n", Config.normalizeWhitelist(rules));
        require(whitelist.length() == 33023, "Maximum legal rule list changed");
        NetworkHandler.ConfigSnapshotMessage snapshot = new NetworkHandler.ConfigSnapshotMessage();
        snapshot.maxNormalBlocks = 123;
        snapshot.mode = ChainMode.BLAST_ORES.id();
        snapshot.blockWhitelist = whitelist;
        snapshot.blockBlacklist = blacklist;
        ByteBuf buffer = Unpooled.buffer();
        try {
            snapshot.toBytes(buffer);
            NetworkHandler.ConfigSnapshotMessage decoded = new NetworkHandler.ConfigSnapshotMessage();
            decoded.fromBytes(buffer);
            require(decoded.maxNormalBlocks == 123 && decoded.mode == ChainMode.BLAST_ORES.id()
                    && whitelist.equals(decoded.blockWhitelist) && blacklist.equals(decoded.blockBlacklist),
                    "Maximum server snapshot was truncated or mixed up");
            require(!buffer.isReadable(), "Snapshot left unread fields");
            buffer.clear();
            snapshot.toBytes(buffer);
            final ByteBuf snapshotBuffer = buffer;
            expectInvalid(() -> new NetworkHandler.ConfigUpdateMessage().fromBytes(snapshotBuffer));
            buffer.clear();
            NetworkHandler.BlacklistSyncMessage sync = new NetworkHandler.BlacklistSyncMessage(blacklist);
            sync.toBytes(buffer);
            NetworkHandler.BlacklistSyncMessage decodedSync = new NetworkHandler.BlacklistSyncMessage();
            decodedSync.fromBytes(buffer);
            Field value = NetworkHandler.BlacklistSyncMessage.class.getDeclaredField("blockBlacklist");
            value.setAccessible(true);
            require(blacklist.equals(value.get(decodedSync)), "Login blacklist was truncated");
        } finally {
            buffer.release();
        }
        buffer = Unpooled.buffer();
        try {
            NetworkHandler.ConfigUpdateMessage update = new NetworkHandler.ConfigUpdateMessage(
                    64, 8, 128, 16, 12, 2, 15, true, false, false, 0,
                    repeat('a', NetworkHandler.MAX_WHITELIST_TEXT_LENGTH), "minecraft:stone");
            update.toBytes(buffer);
            NetworkHandler.ConfigUpdateMessage decoded = new NetworkHandler.ConfigUpdateMessage();
            decoded.fromBytes(buffer);
            require(update.blockWhitelist.equals(decoded.blockWhitelist)
                    && update.blockBlacklist.equals(decoded.blockBlacklist), "Config update mixed up rule lists");
            buffer.clear();
            update.blockBlacklist = repeat('b', NetworkHandler.MAX_WHITELIST_TEXT_LENGTH + 1);
            final ByteBuf target = buffer;
            expectInvalid(() -> update.toBytes(target));
            buffer.clear();
            buffer.writeInt(-1);
            expectInvalid(() -> new NetworkHandler.BlacklistSyncMessage().fromBytes(target));
        } finally {
            buffer.release();
        }
    }

    private static void config() throws Exception {
        require(Config.DEFAULT_BLOCK_BLACKLIST.isEmpty(), "Blacklist default is not empty");
        require(Config.normalizeRule(repeat('a', 119)) == null, "Implicit namespace exceeded entry limit");
        require(Config.normalizeRule(repeat('a', 118)).length() == 128, "Valid maximum ID was rejected");
        List<String> expected = Arrays.asList("minecraft:stone", "ore:oretin", "techmod:*_danger");
        require(expected.equals(Config.parseWhitelistText(
                "stone,\nMinecraft:STONE; ore:oreTin\ntechmod:*_danger\n#ignored\nbad id")),
                "Rule normalization changed");
        Path directory = Files.createTempDirectory("veinminerplus-rules-regression");
        Path file = directory.resolve("veinminerplus.cfg");
        try {
            Field minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
            minecraftHome.setAccessible(true);
            minecraftHome.set(null, directory.toFile());
            Bootstrap.register();
            Config.load(file.toFile());
            require(Config.blockBlacklist.isEmpty(), "New configuration enables a blacklist");
            require(!Config.isBlockBlacklisted(Blocks.STONE.getDefaultState()), "Default blocks stone");
            Config.blockBlacklist = new ArrayList<String>(expected);
            Config.save();
            Config.blockBlacklist.clear();
            require(!Config.isBlockBlacklisted(Blocks.STONE.getDefaultState()), "Cleared rules still block stone");
            UnGeneratedChunkOptimization.cacheResult(10, 20, Collections.<BlockPos>emptyList());
            Config.load(file.toFile());
            require(expected.equals(Config.blockBlacklist), "Blacklist did not persist");
            require(Config.isBlockBlacklisted(Blocks.STONE.getDefaultState()), "Reload ignored blacklist");
            require(UnGeneratedChunkOptimization.getCachedResult(10, 20) == null,
                    "Config reload retained an obsolete chunk scan");
            debugCommand(file);
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    private static void debugCommand(Path file) throws Exception {
        CommandHandler manager = new CommandHandler() {
            @Override
            protected MinecraftServer getServer() {
                return null;
            }
        };
        CommandEvents.register(manager);
        ICommand command = manager.getCommands().get("veinminerplus");
        require(command != null, "VeinMinerPlus command was not registered");
        List<ITextComponent> messages = new ArrayList<ITextComponent>();
        ICommandSender console = commandSender(true, messages);
        require(command.checkPermission(null, console), "Administrator cannot use VeinMinerPlus commands");
        require(!command.checkPermission(null, commandSender(false, messages)),
                "VeinMinerPlus command lost permission level 2");
        require(command.getTabCompletions(null, console, new String[] { "" }, null)
                .containsAll(Arrays.asList("gui", "debug")), "Debug subcommand was not suggested");
        require(command.getTabCompletions(null, console, new String[] { "debug", "" }, null)
                .containsAll(Arrays.asList("true", "false")), "Debug boolean values were not suggested");
        require(!Config.debugLogging, "Debug logging is enabled by default");
        command.execute(null, console, new String[] { "debug", "true" });
        require(Config.debugLogging, "Debug true was not immediately applied");
        Config.debugLogging = false;
        Config.load(file.toFile());
        require(Config.debugLogging, "Debug true was not saved to disk");
        command.execute(null, console, new String[] { "debug", "false" });
        require(!Config.debugLogging, "Debug false was not immediately applied");
        Config.debugLogging = true;
        Config.load(file.toFile());
        require(!Config.debugLogging, "Debug false was not saved to disk");
        require(messages.size() == 2
                && "message.veinminerplus.debug_enabled".equals(((TextComponentTranslation) messages.get(0)).getKey())
                && "message.veinminerplus.debug_disabled".equals(((TextComponentTranslation) messages.get(1)).getKey()),
                "Debug command feedback did not reflect the saved value");
        for (String[] invalid : new String[][] {
                { "debug" }, { "debug", "invalid" }, { "debug", "true", "extra" } }) {
            try {
                command.execute(null, console, invalid);
                throw new AssertionError("Invalid debug command was accepted: " + Arrays.toString(invalid));
            } catch (WrongUsageException expected) {
                require(!Config.debugLogging, "Invalid debug command changed the config");
            }
        }
    }

    private static ICommandSender commandSender(boolean administrator, List<ITextComponent> messages) {
        return (ICommandSender) Proxy.newProxyInstance(CompatibilityRegression.class.getClassLoader(),
                new Class<?>[] { ICommandSender.class }, (proxy, method, arguments) -> {
                    if ("canUseCommand".equals(method.getName())) {
                        require(Integer.valueOf(2).equals(arguments[0]), "Unexpected command permission level");
                        return administrator;
                    }
                    if ("sendMessage".equals(method.getName())) {
                        messages.add((ITextComponent) arguments[0]);
                        return null;
                    }
                    throw new AssertionError("Debug command required a player: " + method.getName());
                });
    }

    private static void blacklist() throws Exception {
        IBlockState stone = Blocks.STONE.getDefaultState();
        Config.blockBlacklist = new ArrayList<String>(Collections.singletonList("minecraft:stone"));
        Method eligibility = ChainEvents.class.getDeclaredMethod("eligibilityFailure", WorldServer.class,
                EntityPlayerMP.class, BlockPos.class, IBlockState.class, Block.class, ChainMode.class, Set.class);
        eligibility.setAccessible(true);
        for (ChainMode mode : ChainMode.values()) {
            require("blacklisted".equals(eligibility.invoke(null, null, null, null, stone, Blocks.STONE,
                    mode, Collections.singleton("minecraft:stone"))), "Blacklist failed in " + mode);
        }
        UnGeneratedChunkOptimization.cacheResult(10, 20, Collections.<BlockPos>emptyList());
        UnGeneratedChunkOptimization.cacheResult(11, 20, Collections.singletonList(new BlockPos(176, 64, 320)));
        require(Config.isBlockBlacklisted(stone), "Blacklist changed unexpectedly");
        require(UnGeneratedChunkOptimization.getCachedResult(10, 20) != null
                && UnGeneratedChunkOptimization.getCachedResult(11, 20) != null,
                "Unchanged rules discarded chunk scans");
        Config.blockBlacklist.clear();
        require(!Config.isBlockBlacklisted(stone), "Server matcher retained removed rules");
        require(UnGeneratedChunkOptimization.getCachedResult(10, 20) == null
                && UnGeneratedChunkOptimization.getCachedResult(11, 20) == null,
                "Removing blacklist retained partial or empty chunk scans");
        IBlockState red = Blocks.WOOL.getDefaultState().withProperty(BlockColored.COLOR, EnumDyeColor.RED);
        IBlockState blue = Blocks.WOOL.getDefaultState().withProperty(BlockColored.COLOR, EnumDyeColor.BLUE);
        OreDictionary.registerOre("vmpRegressionRedWool",
                new ItemStack(Blocks.WOOL, 1, EnumDyeColor.RED.getMetadata()));
        Config.blockBlacklist = Collections.singletonList("ore:vmpregressionredwool");
        require(Config.isBlockBlacklisted(red), "Metadata OreDictionary rule did not match");
        require(!Config.isBlockBlacklisted(blue), "Matcher cached one metadata result for every block state");
        Config.blockBlacklist = Collections.singletonList("minecraft:w*");
        require(Config.isBlockBlacklisted(blue) && !Config.isBlockBlacklisted(stone), "Wildcard rule failed");
        Config.blockBlacklist = Collections.singletonList("minecraft:stone");
        Config.setClientBlacklist("minecraft:wool");
        require(!Config.isClientBlockBlacklisted(stone) && Config.isClientBlockBlacklisted(blue),
                "Client ignored the server's separate blacklist");
        Config.resetClientBlacklist();
        require(!Config.isClientBlockBlacklisted(blue), "Disconnect did not reset client rules");
        Config.blockBlacklist = new ArrayList<String>();
    }

    private static void optionalAe() throws Exception {
        try (NoAeLoader loader = new NoAeLoader()) {
            Class<?> compat = loader.loadClass(AeStorageCompat.class.getName());
            Method supported = compat.getDeclaredMethod("isMagnetSupported");
            supported.setAccessible(true);
            require(Boolean.FALSE.equals(supported.invoke(null)), "Missing AE reported magnet support");
            Method insert = compat.getDeclaredMethod("insertDrops", EntityPlayerMP.class, List.class);
            insert.setAccessible(true);
            insert.invoke(null, null, Collections.singletonList("nonempty"));
        }
    }

    private static void lateOreRegistration() {
        IBlockState blue = Blocks.WOOL.getDefaultState().withProperty(BlockColored.COLOR, EnumDyeColor.BLUE);
        Config.blockBlacklist = Collections.singletonList("ore:vmplateregisterblue");
        Config.setClientBlacklist("ore:vmplateregisterblue");
        require(!Config.isBlockBlacklisted(blue) && !Config.isClientBlockBlacklisted(blue),
                "Unregistered ore rule unexpectedly matched");
        UnGeneratedChunkOptimization.cacheResult(10, 20, Collections.<BlockPos>emptyList());
        ChainEvents events = new ChainEvents();
        try {
            ItemStack registered = new ItemStack(Blocks.WOOL, 1, EnumDyeColor.BLUE.getMetadata());
            OreDictionary.registerOre("vmpLateRegisterBlue", registered);
            // The headless runner does not apply Forge's event subscription transformer.
            events.onOreRegister(new OreDictionary.OreRegisterEvent("vmpLateRegisterBlue", registered));
            require(Config.isBlockBlacklisted(blue) && Config.isClientBlockBlacklisted(blue),
                    "OreRegisterEvent retained server or client negative matching results");
            require(UnGeneratedChunkOptimization.getCachedResult(10, 20) == null,
                    "OreRegisterEvent retained an obsolete sparse chunk result");
        } finally {
            Config.blockBlacklist = new ArrayList<String>();
            Config.resetClientBlacklist();
        }
    }

    private static void aeApi(File file) throws Exception {
        ClassNode adapter = new ClassNode();
        try (InputStream input = CompatibilityRegression.class.getResourceAsStream("AeStorageCompat$Ae2.class")) {
            require(input != null, "Compiled AE adapter is missing");
            new ClassReader(readAll(input)).accept(adapter, 0);
        }
        int references = 0;
        try (ApiClasses api = new ApiClasses(file)) {
            for (MethodNode method : adapter.methods) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) instruction;
                        if (call.owner.startsWith("appeng/")) {
                            require(api.hasMember(call.owner, call.name, call.desc, true, new HashSet<String>()),
                                    file.getName() + " lacks " + call.owner + "." + call.name + call.desc);
                            references++;
                        }
                    } else if (instruction instanceof FieldInsnNode) {
                        FieldInsnNode access = (FieldInsnNode) instruction;
                        if (access.owner.startsWith("appeng/")) {
                            require(api.hasMember(access.owner, access.name, access.desc, false,
                                    new HashSet<String>()), file.getName() + " lacks " + access.owner + "." + access.name);
                            references++;
                        }
                    }
                }
            }
        }
        require(references > 20, "AE API regression did not inspect the complete adapter");
        System.out.println("AE binary API compatible: " + file.getName() + " (" + references + " references).");
    }

    private static String repeat(char value, int count) {
        char[] result = new char[count];
        Arrays.fill(result, value);
        return new String(result);
    }

    private static byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static void expectInvalid(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Oversized or malformed rules were silently accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class NoAeLoader extends URLClassLoader {
        NoAeLoader() {
            super(new URL[] { AeStorageCompat.class.getProtectionDomain().getCodeSource().getLocation() },
                    CompatibilityRegression.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("appeng.") || name.startsWith("baubles.")
                    || name.startsWith(AeStorageCompat.class.getName() + "$")) {
                throw new AssertionError("Missing AE touched optional code: " + name);
            }
            if (name.equals("net.minecraftforge.fml.common.Loader") || name.equals(AeStorageCompat.class.getName())) {
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    if (name.equals(AeStorageCompat.class.getName())) {
                        type = findClass(name);
                    } else {
                        ClassWriter writer = new ClassWriter(0);
                        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name.replace('.', '/'), null,
                                "java/lang/Object", null);
                        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                                "isModLoaded", "(Ljava/lang/String;)Z", null, null);
                        method.visitCode();
                        method.visitInsn(Opcodes.ICONST_0);
                        method.visitInsn(Opcodes.IRETURN);
                        method.visitMaxs(1, 1);
                        method.visitEnd();
                        writer.visitEnd();
                        byte[] bytes = writer.toByteArray();
                        type = defineClass(name, bytes, 0, bytes.length);
                    }
                }
                if (resolve) {
                    resolveClass(type);
                }
                return type;
            }
            return super.loadClass(name, resolve);
        }
    }

    private static final class ApiClasses implements AutoCloseable {
        private final ZipFile zip;
        private final Map<String, ClassNode> classes = new HashMap<String, ClassNode>();

        ApiClasses(File file) throws Exception {
            zip = new ZipFile(file);
        }

        boolean hasMember(String owner, String name, String descriptor, boolean method, Set<String> visited)
                throws Exception {
            if (owner == null || !visited.add(owner)) {
                return false;
            }
            ClassNode type = classes.get(owner);
            if (type == null) {
                if (zip.getEntry(owner + ".class") == null) {
                    return false;
                }
                type = new ClassNode();
                try (InputStream input = zip.getInputStream(zip.getEntry(owner + ".class"))) {
                    new ClassReader(readAll(input)).accept(type,
                            ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
                classes.put(owner, type);
            }
            if (method) {
                for (MethodNode candidate : type.methods) {
                    if (name.equals(candidate.name) && descriptor.equals(candidate.desc)) {
                        return true;
                    }
                }
                if ("<init>".equals(name)) {
                    return false;
                }
            } else {
                for (FieldNode candidate : type.fields) {
                    if (name.equals(candidate.name) && descriptor.equals(candidate.desc)) {
                        return true;
                    }
                }
            }
            if (hasMember(type.superName, name, descriptor, method, visited)) {
                return true;
            }
            for (String implemented : type.interfaces) {
                if (hasMember(implemented, name, descriptor, method, visited)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void close() throws Exception {
            zip.close();
        }
    }
}
