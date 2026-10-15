package com.extrarawstyle.veinminerplus.startup;

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.electronwill.nightconfig.toml.TomlParser;

import cpw.mods.cl.JarModuleFinder;
import cpw.mods.cl.ModularURLHandler;
import cpw.mods.cl.ModuleClassLoader;
import cpw.mods.cl.UnionURLStreamHandler;
import cpw.mods.jarhandling.SecureJar;
import net.minecraftforge.fml.earlydisplay.DisplayWindow;
import net.minecraftforge.fml.earlydisplay.RenderElement;
import net.minecraftforge.fml.earlydisplay.SimpleBufferBuilder;
import net.minecraftforge.fml.earlydisplay.SimpleFont;
import net.minecraftforge.fml.loading.FMLConfig;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.ImmediateWindowHandler;
import net.minecraftforge.fml.loading.ImmediateWindowProvider;
import net.minecraftforge.fml.loading.ModDirTransformerDiscoverer;
import net.minecraftforge.fml.loading.VersionInfo;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import sun.misc.Unsafe;

public final class PackagedStartupCheck {
    private static final String MODULE_NAME = "veinminerplus_startup";
    private static final String STARTUP_CLASS = "com.extrarawstyle.veinminerplus.startup.StartupBootstrap";
    private static final String STARTUP_PATH = "com/extrarawstyle/veinminerplus/startup";

    public static void main(String[] arguments) throws Exception {
        require(arguments.length == 1, "Pass the packaged Forge JAR");
        // BootstrapLauncher normally loads this handler from its BOOT module.
        Class.forName(ModuleClassLoader.class.getName());
        accessibleField(ModularURLHandler.class, "handlers").set(ModularURLHandler.INSTANCE,
                Map.of("union", new UnionURLStreamHandler()));
        Path packagedPath = Path.of(arguments[0]).toAbsolutePath().normalize();
        Path testDirectory = Files.createTempDirectory("veinminerplus-packaged-startup-");
        try {
            Files.createDirectories(testDirectory.resolve("mods"));
            Path discoveredPath = testDirectory.resolve("mods").resolve(packagedPath.getFileName());
            Files.copy(packagedPath, discoveredPath);
            var candidates = new ModDirTransformerDiscoverer().candidates(testDirectory, "forgeserver");
            require(candidates.size() == 1 && candidates.get(0).paths()[0].equals(discoveredPath),
                    "Actual FML discovery did not place the wrapper in SERVICE");
            require(ModDirTransformerDiscoverer.allExcluded().contains(discoveredPath),
                    "Wrapper would also be scanned as a gameplay mod");
            SecureJar outer = SecureJar.from(packagedPath);
            checkServerWithoutGraphics(outer);
            require(MODULE_NAME.equals(outer.name()), "Outer module name is incorrect");
            require(!outer.getPackages().contains("com.extrarawstyle.veinminerplus"),
                    "Gameplay classes leaked into SERVICE");
            require(!Files.exists(outer.getPath(STARTUP_PATH + "/StartupAnimationCheck.class")),
                    "Test classes leaked into the wrapper");
            require(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    Files.readAllBytes(outer.getPath("assets/veinminerplus/textures/gui/startup.gif"))))
                    .equals("b9f8971e6eec56f3044656ba772c057cf7fd1aa0567b069644b87efb3595e15d"),
                    "Packaged GIF differs from the original attachment");
            ModuleLayer serviceLayer = moduleLayer(outer, ModuleLayer.boot(), "SERVICE");
            var services = ServiceLoader.load(serviceLayer, IModLocator.class).stream()
                    .map(ServiceLoader.Provider::get)
                    .filter(service -> service.getClass().getModule().getName().equals(MODULE_NAME)).toList();
            require(services.size() == 1, "Expected one embedded mod locator");
            IModLocator locator = services.get(0);
            Module serviceModule = serviceLayer.findModule(MODULE_NAME).orElseThrow();
            require(locator.getClass().getModule() == serviceModule
                    && locator.getClass().getClassLoader() == serviceLayer.findLoader(MODULE_NAME),
                    "Locator was loaded from the classpath");
            var nested = locator.getClass().getResource("/META-INF/veinminerplus/veinminerplus-mod.jar");
            require(nested != null && "union".equals(nested.getProtocol()),
                    "Embedded mod did not use FML's union filesystem");
            IModFile modFile = discoverGameMod(locator);
            require(modFile.getType() == IModFile.Type.MOD && modFile.getModInfos().size() == 1
                    && "veinminerplus".equals(modFile.getModInfos().get(0).getModId()),
                    "FML did not parse the embedded gameplay mod");
            require(Files.isRegularFile(modFile.findResource("assets/veinminerplus/lang/zh_cn.json"))
                    && Files.isRegularFile(modFile.findResource("assets/veinminerplus/lang/en_us.json")),
                    "Embedded language resources are missing");
            require(!Files.exists(modFile.findResource(STARTUP_PATH)),
                    "Early SERVICE classes leaked into GAME");
            checkReobfuscation(modFile);
            ModuleLayer gameLayer = moduleLayer(modFile.getSecureJar(), serviceLayer, "GAME");
            require(gameLayer.findModule(MODULE_NAME).orElseThrow() == serviceModule,
                    "Client cleanup cannot find the parent SERVICE module");
            Module gameModule = gameLayer.findModule("veinminerplus").orElseThrow();
            Class<?> gameplayClass = Class.forName(gameModule, "com.extrarawstyle.veinminerplus.VeinMinerPlus");
            require(gameplayClass != null && gameplayClass.getModule() == gameModule,
                    "Gameplay entry point was not loaded in GAME");
            Class<?> bootstrap = Class.forName(serviceModule, STARTUP_CLASS);
            checkBootstrap(locator, bootstrap);
            System.out.println("Packaged Forge checks passed: actual discovery, named SERVICE/GAME modules, nested reobfuscated mod, config guards, headless installation and cleanup.");
        } finally {
            try (var paths = Files.walk(testDirectory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static ModuleLayer moduleLayer(SecureJar jar, ModuleLayer parent, String name) {
        Configuration configuration = Configuration.resolveAndBind(
                JarModuleFinder.of(jar), List.of(parent.configuration()), ModuleFinder.of(), Set.of(jar.name()));
        ModuleClassLoader loader = new ModuleClassLoader(name, configuration, List.of(parent, ModuleLayer.boot()));
        loader.setFallbackClassLoader(PackagedStartupCheck.class.getClassLoader());
        return ModuleLayer.defineModules(configuration, List.of(parent), ignored -> loader).layer();
    }

    private static void checkServerWithoutGraphics(SecureJar outer) throws Exception {
        List<String> graphicsLoads = new ArrayList<>();
        ClassLoader serverLoader = new ClassLoader(PackagedStartupCheck.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.minecraftforge.fml.earlydisplay.") || name.startsWith("org.lwjgl.")) {
                    graphicsLoads.add(name);
                    throw new ClassNotFoundException("Graphics library is absent on the dedicated server: " + name);
                }
                if (!name.startsWith("com.extrarawstyle.veinminerplus.startup.")) {
                    return super.loadClass(name, resolve);
                }
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        try {
                            byte[] bytes = Files.readAllBytes(outer.getPath(name.replace('.', '/') + ".class"));
                            loaded = defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException exception) {
                            throw new ClassNotFoundException(name, exception);
                        }
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
        };
        IModLocator locator = (IModLocator) serverLoader
                .loadClass("com.extrarawstyle.veinminerplus.startup.EmbeddedModLocator")
                .getConstructor().newInstance();
        for (String target : List.of("forgeserver", "forgeserverdev", "forgeserveruserdev")) {
            locator.initArguments(Map.of("launchTarget", target));
        }
        require(graphicsLoads.isEmpty(), "Server bootstrap tried to load client graphics classes: " + graphicsLoads);
        require(!Files.exists(FMLPaths.CONFIGDIR.get().resolve("veinminerplus-client.toml")),
                "Server without graphics created a client config");
    }

    private static IModFile discoverGameMod(IModLocator locator) throws Exception {
        Field production = accessibleField(FMLLoader.class, "production");
        boolean original = production.getBoolean(null);
        accessibleField(FMLLoader.class, "versionInfo").set(null,
                new VersionInfo("47.4.13", "1.20.1", "20230612.114412", "net.minecraftforge"));
        try {
            production.setBoolean(null, false);
            require(locator.scanMods().isEmpty(), "Dev launch should retain the direct gameplay source set");
            production.setBoolean(null, true);
            var mods = locator.scanMods();
            require(mods.size() == 1 && mods.get(0).ex() == null && mods.get(0).file() != null,
                    "Embedded mod locator failed");
            return mods.get(0).file();
        } finally {
            production.setBoolean(null, original);
        }
    }

    private static void checkReobfuscation(IModFile modFile) throws Exception {
        boolean[] found = { false };
        ClassReader reader = new ClassReader(Files.readAllBytes(modFile.findResource(
                "com/extrarawstyle/veinminerplus/VeinMinerPlusClientEvents.class")));
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                            boolean isInterface) {
                        if (owner.equals("net/minecraft/client/Minecraft")) {
                            require(!name.equals("getInstance"), "Unreobfuscated Minecraft method in the embedded JAR");
                            if (name.equals("m_91087_")) {
                                found[0] = true;
                            }
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        require(found[0], "Embedded gameplay JAR did not contain the expected reobfuscated call");
    }

    private static void checkBootstrap(IModLocator locator, Class<?> bootstrap) throws Exception {
        byte[] originalFml = Files.readAllBytes(FMLPaths.FMLCONFIG.get());
        Path clientConfig = FMLPaths.CONFIGDIR.get().resolve("veinminerplus-client.toml");
        for (String target : List.of("forgeserver", "forgeserverdev", "forgeserveruserdev", "unknown", "")) {
            locator.initArguments(Map.of("launchTarget", target));
        }
        locator.initArguments(Map.of());
        require(!Files.exists(clientConfig), "Server bootstrap created a client config");
        require(accessibleField(bootstrap, "activeWindow").get(null) == null,
                "Server bootstrap enabled a window");
        Files.writeString(clientConfig, "# Preserve this comment\ncustomStartupAnimation = false\nextra = 17\n");
        byte[] disabled = Files.readAllBytes(clientConfig);
        locator.initArguments(Map.of("launchTarget", "forgeclient"));
        require(Arrays.equals(disabled, Files.readAllBytes(clientConfig)),
                "Disabled client setting was rewritten");
        require(accessibleField(bootstrap, "activeWindow").get(null) == null,
                "Disabled client setting enabled the animation");
        checkWindowGuards(locator, clientConfig);
        Files.delete(clientConfig);
        for (int elementCount : new int[] { 5, 6 }) {
            checkInstallation(locator, bootstrap, elementCount);
        }
        try (var reader = Files.newBufferedReader(clientConfig)) {
            require(Boolean.TRUE.equals(new TomlParser().parse(reader).get("customStartupAnimation")),
                    "Default client switch was not enabled");
        }
        require(Arrays.equals(originalFml, Files.readAllBytes(FMLPaths.FMLCONFIG.get())),
                "Animation bootstrap changed fml.toml");
    }

    private static void checkWindowGuards(IModLocator locator, Path clientConfig) throws Exception {
        String provider = FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER);
        boolean control = FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL);
        byte[] clientBytes = Files.readAllBytes(clientConfig);
        try {
            FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL, false);
            byte[] fmlBytes = Files.readAllBytes(FMLPaths.FMLCONFIG.get());
            locator.initArguments(Map.of("launchTarget", "forgeclient"));
            require(Arrays.equals(fmlBytes, Files.readAllBytes(FMLPaths.FMLCONFIG.get())),
                    "Disabled early window changed fml.toml");
            FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL, control);
            FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER, "custom_test_provider");
            fmlBytes = Files.readAllBytes(FMLPaths.FMLCONFIG.get());
            locator.initArguments(Map.of("launchTarget", "forgeclient"));
            require(Arrays.equals(fmlBytes, Files.readAllBytes(FMLPaths.FMLCONFIG.get())),
                    "Existing custom window provider was changed");
            require(Arrays.equals(clientBytes, Files.readAllBytes(clientConfig)),
                    "Window guards rewrote the client config");
        } finally {
            FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL, control);
            FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER, provider);
        }
    }

    private static void checkInstallation(IModLocator locator, Class<?> bootstrap, int elementCount) throws Exception {
        Field provider = accessibleField(ImmediateWindowHandler.class, "provider");
        Object originalProvider = provider.get(null);
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
        List<RenderElement> elements = new ArrayList<>();
        try {
            DisplayWindow window = new DisplayWindow();
            SimpleFont font = (SimpleFont) unsafe().allocateInstance(SimpleFont.class);
            accessibleField(SimpleFont.class, "lineSpacing").setInt(font, 24);
            accessibleField(SimpleFont.class, "descent").setInt(font, -5);
            for (int index = 0; index < elementCount; index++) {
                elements.add(blankElement());
            }
            List<RenderElement> originals = List.copyOf(elements);
            SimpleBufferBuilder originalBuffer = (SimpleBufferBuilder) accessibleField(RenderElement.class, "bb")
                    .get(elements.get(elementCount - 5));
            accessibleField(DisplayWindow.class, "elements").set(window, elements);
            accessibleField(DisplayWindow.class, "font").set(window, font);
            accessibleField(DisplayWindow.class, "renderScheduler").set(window, scheduler);
            accessibleField(DisplayWindow.class, "initializationFuture")
                    .set(window, scheduler.schedule(() -> { }, 0, TimeUnit.MILLISECONDS));
            Class<?> wrapper = Class.forName("net.minecraftforge.fml.loading.ImmediateWindowHandler$Wrapper");
            var wrapperConstructor = wrapper.getDeclaredConstructor(ImmediateWindowProvider.class);
            wrapperConstructor.setAccessible(true);
            provider.set(null, wrapperConstructor.newInstance(window));
            locator.initArguments(Map.of("launchTarget", "forgeclient"));
            require(accessibleField(bootstrap, "activeWindow").get(null) == window,
                    "Headless fixture did not install the GIF in the real DisplayWindow");
            require(elements.size() == elementCount && elements.get(elementCount - 5) != originals.get(elementCount - 5),
                    "Anvil was not replaced");
            for (int index = 0; index < elementCount; index++) {
                if (index != elementCount - 5) {
                    require(elements.get(index) == originals.get(index), "A non-icon loading element was replaced");
                }
            }
            require(accessibleField(SimpleBufferBuilder.class, "bufferAddr").getLong(originalBuffer) == 0,
                    "Removed anvil's native buffer was not freed");
            Object animation = accessibleField(bootstrap, "animation").get(null);
            SimpleBufferBuilder replacementBuffer = (SimpleBufferBuilder) accessibleField(RenderElement.class, "bb")
                    .get(elements.get(elementCount - 5));
            var cleanup = bootstrap.getMethod("finishIfWindowClosed");
            require(Boolean.FALSE.equals(cleanup.invoke(null)), "Animation was released before the loader window closed");
            scheduler.shutdown();
            require(Boolean.TRUE.equals(cleanup.invoke(null)), "Animation was not released after the window closed");
            require(accessibleField(animation.getClass(), "closed").getBoolean(animation)
                    && accessibleField(SimpleBufferBuilder.class, "bufferAddr").getLong(replacementBuffer) == 0,
                    "Animation cleanup leaked native data");
            require(Boolean.TRUE.equals(cleanup.invoke(null)), "Repeated cleanup was not harmless");
            for (int index = 0; index < elementCount; index++) {
                if (index != elementCount - 5) {
                    ((SimpleBufferBuilder) accessibleField(RenderElement.class, "bb").get(elements.get(index))).close();
                }
            }
        } finally {
            provider.set(null, originalProvider);
            scheduler.shutdownNow();
            require(scheduler.awaitTermination(10, TimeUnit.SECONDS), "Headless render scheduler did not stop");
        }
    }

    private static RenderElement blankElement() throws Exception {
        Class<?> rendererType = Class.forName("net.minecraftforge.fml.earlydisplay.RenderElement$Renderer");
        Class<?> initializerType = Class.forName("net.minecraftforge.fml.earlydisplay.RenderElement$Initializer");
        Object renderer = Proxy.newProxyInstance(rendererType.getClassLoader(), new Class<?>[] { rendererType },
                (proxy, method, arguments) -> null);
        Object initializer = Proxy.newProxyInstance(initializerType.getClassLoader(), new Class<?>[] { initializerType },
                (proxy, method, arguments) -> renderer);
        var constructor = RenderElement.class.getDeclaredConstructor(initializerType);
        constructor.setAccessible(true);
        return constructor.newInstance(initializer);
    }

    private static Unsafe unsafe() throws Exception {
        return (Unsafe) accessibleField(Unsafe.class, "theUnsafe").get(null);
    }

    private static Field accessibleField(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
