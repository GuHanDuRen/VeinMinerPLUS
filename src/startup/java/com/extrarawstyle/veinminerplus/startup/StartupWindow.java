package com.extrarawstyle.veinminerplus.startup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.earlydisplay.RenderElement;
import net.neoforged.fml.earlydisplay.SimpleBufferBuilder;
import net.neoforged.fml.earlydisplay.SimpleFont;

public final class StartupWindow extends DisplayWindow {
    private ScheduledFuture<?> animationSetup;
    private GifAnimation animation;
    private Method overlayFactory;

    @Override
    public String name() {
        return StartupBootstrap.PROVIDER_NAME;
    }

    @Override
    public Runnable initialize(String[] arguments) {
        StartupBootstrap.restoreProvider();
        Runnable tick = super.initialize(arguments);
        try {
            ScheduledExecutorService scheduler = (ScheduledExecutorService) field("renderScheduler").get(this);
            ScheduledFuture<?> initialization = (ScheduledFuture<?>) field("initializationFuture").get(this);
            animationSetup = scheduler.schedule(this::replaceFox,
                    Math.max(0, initialization.getDelay(TimeUnit.NANOSECONDS)), TimeUnit.NANOSECONDS);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            StartupBootstrap.LOGGER.warn("Could not install the startup animation; keeping the fox", exception);
        }
        return tick;
    }

    @SuppressWarnings("unchecked")
    private void replaceFox() {
        GifAnimation loaded = null;
        try {
            List<RenderElement> elements = (List<RenderElement>) field("elements").get(this);
            SimpleFont font = (SimpleFont) field("font").get(this);
            Field spacing = SimpleFont.class.getDeclaredField("lineSpacing");
            Field descent = SimpleFont.class.getDeclaredField("descent");
            spacing.setAccessible(true);
            descent.setAccessible(true);
            loaded = GifAnimation.load(spacing.getInt(font) + descent.getInt(font));
            // The five standard elements may be preceded by FML's optional squirrel.
            Field buffer = RenderElement.class.getDeclaredField("bb");
            buffer.setAccessible(true);
            RenderElement original = elements.set(elements.size() - 5, loaded.createElement());
            ((SimpleBufferBuilder) buffer.get(original)).close();
            animation = loaded;
            StartupBootstrap.LOGGER.info("Installed the bundled startup animation");
        } catch (Exception | LinkageError exception) {
            if (loaded != null) {
                loaded.close();
            }
            StartupBootstrap.LOGGER.warn("Could not load the startup animation; keeping the fox", exception);
        }
    }

    @Override
    public long setupMinecraftWindow(IntSupplier width, IntSupplier height, Supplier<String> title, LongSupplier monitor) {
        if (animationSetup != null) {
            try {
                animationSetup.get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while preparing the loading window", exception);
            } catch (ExecutionException exception) {
                throw new IllegalStateException("Could not prepare the loading window", exception);
            }
        }
        return super.setupMinecraftWindow(width, height, title, monitor);
    }

    @Override
    public void updateModuleReads(ModuleLayer layer) {
        Module neoforge = layer.findModule("neoforge").orElseThrow();
        // addReads must be called from the subclass's own module.
        StartupWindow.class.getModule().addReads(neoforge);
        Class<?> overlay = Class.forName(neoforge, "net.neoforged.neoforge.client.loading.NeoForgeLoadingOverlay");
        try {
            overlayFactory = overlay.getMethod("newInstance", Supplier.class, Supplier.class, Consumer.class, DisplayWindow.class);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not connect the loading overlay", exception);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Supplier<T> loadingOverlay(Supplier<?> minecraft, Supplier<?> reload, Consumer<Optional<Throwable>> onFinish,
            boolean fade) {
        try {
            return (Supplier<T>) overlayFactory.invoke(null, minecraft, reload, onFinish, this);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create the loading overlay", exception);
        }
    }

    @Override
    public void close() {
        if (animation != null) {
            animation.close();
            animation = null;
        }
        super.close();
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = DisplayWindow.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
