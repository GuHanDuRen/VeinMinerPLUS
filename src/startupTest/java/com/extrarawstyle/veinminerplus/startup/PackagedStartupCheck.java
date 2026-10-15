package com.extrarawstyle.veinminerplus.startup;

import com.electronwill.nightconfig.toml.TomlParser;
import cpw.mods.cl.JarModuleFinder;
import cpw.mods.cl.ModuleClassLoader;
import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.SecureJar;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.TransformerDiscovererConstants;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper;
import net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

public final class PackagedStartupCheck {
    private static final String MODULE_NAME = "veinminerplus_startup";
    private static final String STARTUP_PACKAGE = "com.extrarawstyle.veinminerplus.startup";
    private static final String EMBEDDED_MOD = "/META-INF/veinminerplus/veinminerplus-mod.jar";

    public static void main(String[] arguments) throws Exception {
        require(arguments.length == 1, "Pass the packaged NeoForge JAR");
        Path packagedPath = Path.of(arguments[0]).toAbsolutePath().normalize();
        require(Files.isRegularFile(packagedPath), "Packaged JAR is missing");
        require(TransformerDiscovererConstants.shouldLoadInServiceLayer(packagedPath),
                "FML would not discover the early services");
        SecureJar outerJar = SecureJar.from(packagedPath);
        RecordingPipeline pipeline = new RecordingPipeline();
        try {
            require(MODULE_NAME.equals(outerJar.name()), "Outer module name is incorrect");
            ModuleLayer serviceLayer = moduleLayer(outerJar, ModuleLayer.boot(), "SERVICE");
            ClassLoader serviceLoader = serviceLayer.findLoader(MODULE_NAME);
            Module serviceModule = serviceLayer.findModule(MODULE_NAME).orElseThrow();
            GraphicsBootstrapper bootstrap = onlyService(serviceLayer, GraphicsBootstrapper.class);
            ImmediateWindowProvider provider = onlyService(serviceLayer, ImmediateWindowProvider.class);
            IModFileCandidateLocator locator = onlyService(serviceLayer, IModFileCandidateLocator.class);
            for (Object service : List.of(bootstrap, provider, locator)) {
                require(service.getClass().getModule() == serviceModule, "A service escaped the SERVICE module");
                require(service.getClass().getClassLoader() == serviceLoader, "A service came from the classpath");
            }
            require(MODULE_NAME.equals(provider.name()), "Custom window provider has the wrong name");
            require(provider.getClass().getDeclaredMethod("updateModuleReads", ModuleLayer.class)
                    .getDeclaringClass() == provider.getClass(), "Window must own the module handoff");

            var resource = locator.getClass().getResource(EMBEDDED_MOD);
            require(resource != null && "union".equals(resource.getProtocol()),
                    "Embedded mod resource did not use the real union filesystem");
            checkEnvironmentDiscovery(locator, pipeline);
            require(pipeline.modFile != null, "Embedded mod was not discovered");
            IModFile modFile = pipeline.modFile;
            require(modFile.getType() == IModFile.Type.MOD, "Embedded mod has the wrong loading type");
            require(modFile.getModInfos().size() == 1
                    && "veinminerplus".equals(modFile.getModInfos().getFirst().getModId()),
                    "FML did not parse the expected mod metadata");
            require(Files.isRegularFile(modFile.findResource("com/extrarawstyle/veinminerplus/VeinMinerPlus.class")),
                    "Embedded gameplay entry point is missing");
            require(Files.isRegularFile(modFile.findResource("assets/veinminerplus/lang/zh_cn.json"))
                    && Files.isRegularFile(modFile.findResource("assets/veinminerplus/lang/en_us.json")),
                    "Embedded language resources are missing");
            require(!Files.exists(modFile.findResource(STARTUP_PACKAGE.replace('.', '/'))),
                    "Early service classes leaked into the GAME mod");
            require(!modFile.getSecureJar().moduleDataProvider().descriptor().packages()
                    .contains(STARTUP_PACKAGE), "GAME mod contains the SERVICE package");
            ModuleLayer gameLayer = moduleLayer(modFile.getSecureJar(), serviceLayer, "GAME");
            ClassLoader gameLoader = gameLayer.findLoader("veinminerplus");
            Module gameModule = gameLayer.findModule("veinminerplus").orElseThrow();
            Class<?> gameplayClass = Class.forName(gameModule, "com.extrarawstyle.veinminerplus.VeinMinerPlus");
            require(gameplayClass != null && gameplayClass.getModule() == gameModule
                    && gameplayClass.getClassLoader() == gameLoader, "Gameplay class did not belong to GAME");
            checkServerBootstrap(bootstrap, provider);
            System.out.println("Packaged SERVICE discovery, union resources, FML metadata, GAME module, dev/server guards and client activation/restoration passed");
        } finally {
            if (pipeline.modFile != null) {
                pipeline.modFile.getSecureJar().close();
            }
            outerJar.close();
        }
    }

    private static ModuleLayer moduleLayer(SecureJar jar, ModuleLayer parent, String name) {
        Configuration configuration = Configuration.resolveAndBind(
                JarModuleFinder.of(jar), List.of(parent.configuration()), ModuleFinder.of(), Set.of(jar.name()));
        // FML dependencies stay on the test classpath; product classes must come from their named module.
        ModuleClassLoader loader = new ModuleClassLoader(name, configuration, List.of(parent, ModuleLayer.boot()),
                PackagedStartupCheck.class.getClassLoader());
        return ModuleLayer.defineModules(configuration, List.of(parent), ignored -> loader).layer();
    }

    private static <T> T onlyService(ModuleLayer layer, Class<T> type) {
        List<T> services = ServiceLoader.load(layer, type).stream().map(ServiceLoader.Provider::get).toList();
        require(services.size() == 1, "Expected exactly one " + type.getSimpleName());
        return services.getFirst();
    }

    private static void checkEnvironmentDiscovery(IModFileCandidateLocator locator, RecordingPipeline pipeline)
            throws Exception {
        var production = FMLLoader.class.getDeclaredField("production");
        production.setAccessible(true);
        boolean originalProduction = production.getBoolean(null);
        try {
            production.setBoolean(null, false);
            RecordingPipeline devPipeline = new RecordingPipeline();
            locator.findCandidates(null, devPipeline);
            require(devPipeline.modFile == null, "Dev launch should keep the direct gameplay source set");
            production.setBoolean(null, true);
            locator.findCandidates(null, pipeline);
        } finally {
            production.setBoolean(null, originalProduction);
        }
    }

    private static void checkServerBootstrap(GraphicsBootstrapper bootstrap, ImmediateWindowProvider provider)
            throws Exception {
        Path testDirectory = Files.createTempDirectory("veinminerplus-packaged-startup-");
        try {
            FMLPaths.loadAbsolutePaths(testDirectory);
            FMLConfig.load();
            byte[] originalFmlConfig = Files.readAllBytes(FMLPaths.FMLCONFIG.get());
            String originalProvider = FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER);
            bootstrap.bootstrap(new String[] { "--launchTarget", "forgeserver" });
            bootstrap.bootstrap(new String[] { "--launchTarget=forgeserver" });
            require(originalProvider.equals(FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER)),
                    "Server bootstrap changed the selected window provider");
            require(Arrays.equals(originalFmlConfig, Files.readAllBytes(FMLPaths.FMLCONFIG.get())),
                    "Server bootstrap modified fml.toml");
            require(!Files.exists(FMLPaths.CONFIGDIR.get().resolve("veinminerplus-client.toml")),
                    "Server bootstrap created the client animation config");
            checkClientBootstrap(bootstrap, provider, originalProvider, originalFmlConfig);
            var window = DisplayWindow.class.getDeclaredField("window");
            var scheduler = DisplayWindow.class.getDeclaredField("renderScheduler");
            window.setAccessible(true);
            scheduler.setAccessible(true);
            require(window.getLong(provider) == 0 && scheduler.get(provider) == null,
                    "Headless service discovery created a GL window or render thread");
        } finally {
            try (var paths = Files.walk(testDirectory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void checkClientBootstrap(GraphicsBootstrapper bootstrap, ImmediateWindowProvider provider,
            String originalProvider, byte[] originalFmlConfig) throws Exception {
        Path clientConfig = FMLPaths.CONFIGDIR.get().resolve("veinminerplus-client.toml");
        bootstrap.bootstrap(new String[] { "--launchTarget", "forgeclient" });
        require(provider.name().equals(FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER)),
                "Enabled client bootstrap did not select the animation provider");
        require(Files.isRegularFile(clientConfig), "Default client animation config was not created");
        try (var reader = Files.newBufferedReader(clientConfig)) {
            require(Boolean.TRUE.equals(new TomlParser().parse(reader).get("customStartupAnimation")),
                    "Default startup animation setting was not true");
        }
        require(Arrays.equals(originalFmlConfig, Files.readAllBytes(FMLPaths.FMLCONFIG.get())),
                "Bootstrap persisted its temporary provider selection too early");
        FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER, provider.name());
        try (var reader = Files.newBufferedReader(FMLPaths.FMLCONFIG.get())) {
            require(provider.name().equals(new TomlParser().parse(reader).get("earlyWindowProvider")),
                    "Simulated FML provider persistence failed");
        }
        var restoreProvider = bootstrap.getClass().getDeclaredMethod("restoreProvider");
        restoreProvider.setAccessible(true);
        restoreProvider.invoke(null);
        require(originalProvider.equals(FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER)),
                "Window initialization did not restore the saved provider");
        require(Arrays.equals(originalFmlConfig, Files.readAllBytes(FMLPaths.FMLCONFIG.get())),
                "Restoring the temporary provider changed the saved FML config");

        Files.writeString(clientConfig, "# Explicit user setting\ncustomStartupAnimation = false\notherOption = 17\n");
        byte[] disabledClientConfig = Files.readAllBytes(clientConfig);
        bootstrap.bootstrap(new String[] { "--launchTarget=forgeclient" });
        require(originalProvider.equals(FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER)),
                "Disabled animation selected a custom window provider");
        require(Arrays.equals(disabledClientConfig, Files.readAllBytes(clientConfig)),
                "Bootstrap rewrote an explicit false setting");
        require(Arrays.equals(originalFmlConfig, Files.readAllBytes(FMLPaths.FMLCONFIG.get())),
                "Disabled animation modified fml.toml");

        Files.writeString(clientConfig, "customStartupAnimation = true\n");
        byte[] enabledClientConfig = Files.readAllBytes(clientConfig);
        boolean originalControl = FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL);
        try {
            FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL, false);
            byte[] disabledWindowConfig = Files.readAllBytes(FMLPaths.FMLCONFIG.get());
            bootstrap.bootstrap(new String[] { "--launchTarget", "forgeclient" });
            require(originalProvider.equals(FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER)),
                    "Bootstrap bypassed the disabled early-window setting");
            require(Arrays.equals(disabledWindowConfig, Files.readAllBytes(FMLPaths.FMLCONFIG.get()))
                    && Arrays.equals(enabledClientConfig, Files.readAllBytes(clientConfig)),
                    "Disabled early window modified a config file");
        } finally {
            FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL, originalControl);
        }
        try {
            FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER, "another_window_provider");
            byte[] customWindowConfig = Files.readAllBytes(FMLPaths.FMLCONFIG.get());
            bootstrap.bootstrap(new String[] { "--launchTarget", "forgeclient" });
            require("another_window_provider".equals(
                    FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER)),
                    "Bootstrap replaced an existing custom window provider");
            require(Arrays.equals(customWindowConfig, Files.readAllBytes(FMLPaths.FMLCONFIG.get()))
                    && Arrays.equals(enabledClientConfig, Files.readAllBytes(clientConfig)),
                    "Existing custom window provider modified a config file");
        } finally {
            FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER, originalProvider);
        }
        require(Arrays.equals(originalFmlConfig, Files.readAllBytes(FMLPaths.FMLCONFIG.get())),
                "Client bootstrap checks did not restore the original FML settings");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingPipeline implements IDiscoveryPipeline {
        private IModFile modFile;

        @Override
        public Optional<IModFile> addJarContent(JarContents contents, ModFileDiscoveryAttributes attributes,
                IncompatibleFileReporting reporting) {
            IModFile file = readModFile(contents, attributes);
            require(file != null && addModFile(file), "FML rejected the embedded mod");
            return Optional.of(file);
        }

        @Override
        public IModFile readModFile(JarContents contents, ModFileDiscoveryAttributes attributes) {
            return JarModsDotTomlModFileReader.createModFile(contents, attributes);
        }

        @Override
        public boolean addModFile(IModFile file) {
            require(modFile == null, "Embedded mod was discovered more than once");
            modFile = file;
            return true;
        }

        @Override
        public Optional<IModFile> addPath(List<Path> paths, ModFileDiscoveryAttributes attributes,
                IncompatibleFileReporting reporting) {
            throw new AssertionError("Locator should discover its embedded JarContents");
        }

        @Override
        public void addIssue(ModLoadingIssue issue) {
            throw new AssertionError("FML discovery issue: " + issue);
        }
    }
}
