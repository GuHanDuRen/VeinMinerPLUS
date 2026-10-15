package com.extrarawstyle.veinminerplus.startup;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;

import net.minecraftforge.fml.earlydisplay.DisplayWindow;
import net.minecraftforge.fml.earlydisplay.RenderElement;
import net.minecraftforge.fml.earlydisplay.SimpleBufferBuilder;
import net.minecraftforge.fml.earlydisplay.SimpleFont;
import net.minecraftforge.fml.loading.FMLConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.ImmediateWindowHandler;

public final class StartupBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger("VeinMinerPlusStartup");
    private static final Set<String> CLIENT_TARGETS = Set.of("forgeclient", "forgeclientuserdev", "forgeclientdev");
    private static DisplayWindow activeWindow;
    private static GifAnimation animation;

    private StartupBootstrap() {
    }

    static void install(String launchTarget) {
        if (!CLIENT_TARGETS.contains(launchTarget)
                || !FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL)
                || !"fmlearlywindow".equals(FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER))) {
            return;
        }
        try {
            if (!readEnabled(FMLPaths.CONFIGDIR.get().resolve("veinminerplus-client.toml"))) {
                return;
            }
            Field providerField = ImmediateWindowHandler.class.getDeclaredField("provider");
            providerField.setAccessible(true);
            Object provider = providerField.get(null);
            if (provider == null) {
                return;
            }
            Field delegateField = provider.getClass().getDeclaredField("delegate");
            delegateField.setAccessible(true);
            Object delegate = delegateField.get(provider);
            if (delegate.getClass() != DisplayWindow.class) {
                return;
            }
            DisplayWindow window = (DisplayWindow) delegate;
            ScheduledExecutorService scheduler = (ScheduledExecutorService) field("renderScheduler").get(window);
            ScheduledFuture<?> initialization = (ScheduledFuture<?>) field("initializationFuture").get(window);
            // Finish before mod discovery returns, so Minecraft cannot hand off the window during replacement.
            initialization.get(30, TimeUnit.SECONDS);
            scheduler.submit(() -> replaceAnvil(window)).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while preparing the startup animation; keeping the anvil", exception);
        } catch (Exception | LinkageError exception) {
            LOGGER.warn("Could not enable the startup animation; keeping the anvil", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static void replaceAnvil(DisplayWindow window) {
        GifAnimation loaded = null;
        try {
            List<RenderElement> elements = (List<RenderElement>) field("elements").get(window);
            SimpleFont font = (SimpleFont) field("font").get(window);
            Field spacing = SimpleFont.class.getDeclaredField("lineSpacing");
            Field descent = SimpleFont.class.getDeclaredField("descent");
            spacing.setAccessible(true);
            descent.setAccessible(true);
            loaded = GifAnimation.load(spacing.getInt(font) + descent.getInt(font));
            RenderElement replacement = loaded.createElement();
            Field buffer = RenderElement.class.getDeclaredField("bb");
            buffer.setAccessible(true);
            // FML's optional squirrel precedes these five standard elements.
            int index = elements.size() - 5;
            SimpleBufferBuilder originalBuffer = (SimpleBufferBuilder) buffer.get(elements.get(index));
            elements.set(index, replacement);
            originalBuffer.close();
            animation = loaded;
            activeWindow = window;
            LOGGER.info("Installed the bundled startup animation");
        } catch (Exception | LinkageError exception) {
            if (loaded != null) {
                loaded.close();
            }
            LOGGER.warn("Could not load the startup animation; keeping the anvil", exception);
        }
    }

    public static boolean finishIfWindowClosed() throws ReflectiveOperationException {
        if (activeWindow == null) {
            return true;
        }
        ScheduledExecutorService scheduler = (ScheduledExecutorService) field("renderScheduler").get(activeWindow);
        if (!scheduler.isShutdown()) {
            return false;
        }
        // Called by the client render tick after the standard loading overlay closes the window.
        animation.close();
        animation = null;
        activeWindow = null;
        return true;
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
                    "Replace the Forge startup anvil with the bundled animation. Requires a game restart.");
            Files.createDirectories(file.getParent());
            new TomlWriter().write(config, file, WritingMode.REPLACE);
        }
        return !Boolean.FALSE.equals(config.get("customStartupAnimation"));
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = DisplayWindow.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
