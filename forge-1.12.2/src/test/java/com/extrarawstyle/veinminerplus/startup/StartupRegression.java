package com.extrarawstyle.veinminerplus.startup;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.Side;

public final class StartupRegression {
    private static final String SPLASH = "net.minecraftforge.fml.client.SplashProgress";
    private static final String RENDERER = "com/extrarawstyle/veinminerplus/startup/StartupRenderer";

    public static void main(String[] arguments) throws Exception {
        Path directory = Files.createTempDirectory("veinminerplus-startup-regression");
        try {
            setField(FMLInjectionData.class, "minecraftHome", directory.toFile());
            configAndServer(directory);
            gif();
            bytecode(new File(arguments[0]), new File(arguments[1]));
        } finally {
            Files.deleteIfExists(directory.resolve("config/veinminerplus-client.cfg"));
            Files.deleteIfExists(directory.resolve("config"));
            Files.deleteIfExists(directory);
        }
        System.out.println("Startup regression passed: config default/disable/preservation, server guard, "
                + "29 transparent GIF frames at 20 ms, real Forge bytecode and JVM verification.");
    }

    private static void configAndServer(Path directory) throws Exception {
        setField(FMLLaunchHandler.class, "side", Side.SERVER);
        Path config = directory.resolve("config/veinminerplus-client.cfg");
        try (GuardLoader loader = new GuardLoader()) {
            Class<?> plugin = loader.loadClass(StartupLoadingPlugin.class.getName());
            Object instance = plugin.newInstance();
            String[] transformers = (String[]) plugin.getMethod("getASMTransformerClass").invoke(instance);
            require(transformers.length == 0, "Server registered a startup transformer");
            require(!Files.exists(config), "Server created a client configuration");
            Class<?> transformer = loader.loadClass(StartupTransformer.class.getName());
            byte[] input = { 1, 2, 3 };
            Object result = transformer.getMethod("transform", String.class, String.class, byte[].class)
                    .invoke(transformer.newInstance(), SPLASH, SPLASH, input);
            require(result == input, "Server changed client bytecode");
        }
        setField(FMLLaunchHandler.class, "side", Side.CLIENT);
        require(new StartupLoadingPlugin().getASMTransformerClass().length == 1, "Default is not enabled");
        require(Files.isRegularFile(config), "Default client config was not created early");
        Configuration values = new Configuration(config.toFile());
        require(values.get(Configuration.CATEGORY_CLIENT, "customStartupAnimation", false).getBoolean(),
                "Saved default is not true");
        values.get(Configuration.CATEGORY_CLIENT, "customStartupAnimation", true).set(false);
        values.get(Configuration.CATEGORY_CLIENT, "unrelatedSetting", "keep").set("keep");
        values.save();
        require(new StartupLoadingPlugin().getASMTransformerClass().length == 0,
                "Disabled configuration registered a transformer");
        Configuration reloaded = new Configuration(config.toFile());
        require(!reloaded.get(Configuration.CATEGORY_CLIENT, "customStartupAnimation", true).getBoolean(),
                "Disabled value was overwritten");
        require("keep".equals(reloaded.get(Configuration.CATEGORY_CLIENT, "unrelatedSetting", "").getString()),
                "Unrelated config value was changed");
    }

    private static void gif() throws Exception {
        GifAnimation animation = GifAnimation.load();
        require(animation.width == 500 && animation.height == 500, "GIF dimensions changed");
        require(animation.pixels.length == 29, "GIF frames were lost");
        for (int frame = 0; frame < animation.pixels.length; frame++) {
            require(animation.frameEnds[frame] == (frame + 1) * 20, "GIF frame delay changed");
            require(animation.frameAt(frame * 20) == frame, "GIF frame start is incorrect");
            require(animation.frameAt(frame * 20 + 19) == frame, "GIF frame end is incorrect");
            int transparent = 0;
            int visible = 0;
            for (int pixel : animation.pixels[frame]) {
                if ((pixel >>> 24) == 0) {
                    transparent++;
                } else {
                    visible++;
                }
            }
            require(transparent > 0 && visible > 0, "GIF transparency was lost");
        }
        require(animation.frameAt(580) == 0 && animation.frameAt(579) == 28,
                "GIF looping duration changed");
        require(!Arrays.equals(animation.pixels[0], animation.pixels[14]), "GIF is static");
        try (InputStream resource = GifAnimation.class.getResourceAsStream(GifAnimation.RESOURCE)) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(readAll(resource));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) {
                hex.append(String.format("%02x", value & 255));
            }
            require("b9f8971e6eec56f3044656ba772c057cf7fd1aa0567b069644b87efb3595e15d".equals(hex.toString()),
                    "Bundled GIF bytes differ from the original asset");
        }
    }

    private static void bytecode(File forge, File minecraft) throws Exception {
        StartupTransformer transformer = new StartupTransformer();
        Map<String, byte[]> transformed = new HashMap<String, byte[]>();
        try (ZipFile zip = new ZipFile(forge)) {
            for (String name : new String[] { SPLASH, SPLASH + "$2" }) {
                byte[] original;
                try (InputStream input = zip.getInputStream(zip.getEntry(name.replace('.', '/') + ".class"))) {
                    original = readAll(input);
                }
                byte[] result = transformer.transform(name, name, original);
                require(!Arrays.equals(original, result), "Real Forge target was not patched: " + name);
                new ClassReader(result).accept(new CheckClassAdapter(new ClassWriter(0), true), 0);
                ClassNode type = new ClassNode();
                new ClassReader(result).accept(type, 0);
                int draw = 0;
                int close = 0;
                for (MethodNode method : type.methods) {
                    for (AbstractInsnNode instruction : method.instructions.toArray()) {
                        if (instruction instanceof MethodInsnNode) {
                            MethodInsnNode call = (MethodInsnNode) instruction;
                            if (RENDERER.equals(call.owner) && "draw".equals(call.name)) {
                                draw++;
                                require(instruction.getNext() instanceof JumpInsnNode,
                                        "Original Forge logo fallback is missing");
                            }
                            if (RENDERER.equals(call.owner) && "close".equals(call.name)) {
                                close++;
                            }
                        }
                    }
                }
                require(name.endsWith("$2") ? draw == 1 && close == 0 : draw == 0 && close == 1,
                        "Unexpected startup hook count");
                transformed.put(name, result);
                setField(FMLLaunchHandler.class, "side", Side.SERVER);
                require(transformer.transform(name, name, original) == original, "Server bytecode was changed");
                setField(FMLLaunchHandler.class, "side", Side.CLIENT);
            }
        }
        require(minecraft.isFile(), "Cached Minecraft bytecode is unavailable for JVM verification");
        try (BytecodeLoader loader = new BytecodeLoader(forge, minecraft, transformed)) {
            for (String name : transformed.keySet()) {
                Class<?> type = loader.loadClass(name);
                type.getDeclaredMethods();
                type.getDeclaredConstructors();
                StringWriter diagnostics = new StringWriter();
                CheckClassAdapter.verify(new ClassReader(transformed.get(name)), loader, false,
                        new PrintWriter(diagnostics));
                require(diagnostics.toString().isEmpty(), "Real Forge verification failed: " + diagnostics);
            }
        }
        byte[] unrelated = { 1, 2, 3 };
        require(transformer.transform("example.Unrelated", "example.Unrelated", unrelated) == unrelated,
                "Unrelated bytecode was changed");
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

    private static void setField(Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class GuardLoader extends URLClassLoader {
        GuardLoader() {
            super(new URL[] { StartupLoadingPlugin.class.getProtectionDomain().getCodeSource().getLocation() },
                    StartupRegression.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("net.minecraft.client.") || name.startsWith("org.lwjgl.")
                    || name.startsWith("javax.imageio.")
                    || name.equals("com.extrarawstyle.veinminerplus.startup.StartupConfig")
                    || name.equals("com.extrarawstyle.veinminerplus.startup.StartupRenderer")) {
                throw new AssertionError("Server touched client code: " + name);
            }
            if (name.equals(StartupLoadingPlugin.class.getName()) || name.equals(StartupTransformer.class.getName())) {
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    type = findClass(name);
                }
                if (resolve) {
                    resolveClass(type);
                }
                return type;
            }
            return super.loadClass(name, resolve);
        }
    }

    private static final class BytecodeLoader extends URLClassLoader {
        private final Map<String, byte[]> replacements;

        BytecodeLoader(File forge, File minecraft, Map<String, byte[]> replacements) throws Exception {
            super(new URL[] { forge.toURI().toURL(), minecraft.toURI().toURL() },
                    StartupRegression.class.getClassLoader());
            this.replacements = replacements;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(SPLASH)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    byte[] bytes = replacements.get(name);
                    if (bytes == null) {
                        try (InputStream input = findResource(name.replace('.', '/') + ".class").openStream()) {
                            bytes = readAll(input);
                        } catch (Exception exception) {
                            throw new ClassNotFoundException(name, exception);
                        }
                    }
                    type = defineClass(name, bytes, 0, bytes.length);
                }
                if (resolve) {
                    resolveClass(type);
                }
                return type;
            }
            return super.loadClass(name, resolve);
        }
    }
}
