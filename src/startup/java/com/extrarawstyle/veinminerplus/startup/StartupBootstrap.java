package com.extrarawstyle.veinminerplus.startup;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;

import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper;

public final class StartupBootstrap implements GraphicsBootstrapper {
    static final String PROVIDER_NAME = "veinminerplus_startup";
    static final Logger LOGGER = LoggerFactory.getLogger("VeinMinerPlusStartup");
    private static final Set<String> CLIENT_TARGETS = Set.of("forgeclient", "forgeclientuserdev", "forgeclientdev");
    private static String originalProvider = "fmlearlywindow";

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public void bootstrap(String[] arguments) {
        if (!isClientLaunch(arguments)
                || !FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL)
                || !"fmlearlywindow".equals(FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER))) {
            return;
        }
        try {
            if (!readEnabled(FMLPaths.CONFIGDIR.get().resolve("veinminerplus-client.toml"))) {
                return;
            }
            Field instanceField = FMLConfig.class.getDeclaredField("INSTANCE");
            Field configField = FMLConfig.class.getDeclaredField("configData");
            instanceField.setAccessible(true);
            configField.setAccessible(true);
            CommentedConfig config = (CommentedConfig) configField.get(instanceField.get(null));
            originalProvider = config.get("earlyWindowProvider");
            // Select our provider for this launch. initialize() restores the saved FML setting.
            config.set("earlyWindowProvider", PROVIDER_NAME);
        } catch (IOException | ReflectiveOperationException | RuntimeException exception) {
            LOGGER.warn("Could not enable the startup animation; using the default loading window", exception);
        }
    }

    static boolean isClientLaunch(String[] arguments) {
        for (int index = 0; index < arguments.length; index++) {
            String argument = arguments[index];
            if ("--launchTarget".equals(argument) && index + 1 < arguments.length) {
                return CLIENT_TARGETS.contains(arguments[index + 1]);
            }
            if (argument.startsWith("--launchTarget=")) {
                return CLIENT_TARGETS.contains(argument.substring("--launchTarget=".length()));
            }
        }
        return false;
    }

    static boolean readEnabled(Path file) throws IOException {
        CommentedConfig config = CommentedConfig.of(LinkedHashMap::new, TomlFormat.instance());
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                config = new TomlParser().parse(reader);
            }
        }
        if (!config.contains("customStartupAnimation")) {
            config.set("customStartupAnimation", true);
            config.setComment("customStartupAnimation",
                    "Replace the NeoForge startup fox with the bundled animation. Requires a game restart.");
            Files.createDirectories(file.getParent());
            new TomlWriter().write(config, file, WritingMode.REPLACE);
        }
        return !Boolean.FALSE.equals(config.get("customStartupAnimation"));
    }

    static void restoreProvider() {
        FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER, originalProvider);
    }
}
