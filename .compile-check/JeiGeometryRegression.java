import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.gui.overlay.GuiPropertiesCache;

// Isolated regression against the installed JEI cache and the built mod's record.
public final class JeiGeometryRegression {
    private static final int[] size = {0, 0};

    private static IGuiProperties oldProperties() {
        return (IGuiProperties) Proxy.newProxyInstance(IGuiProperties.class.getClassLoader(),
                new Class<?>[] {IGuiProperties.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "screenClass" -> Object.class;
                    case "guiLeft" -> (size[0] - 200) / 2;
                    case "guiTop" -> (size[1] - 196) / 2;
                    case "guiXSize" -> 200;
                    case "guiYSize" -> 196;
                    case "screenWidth" -> size[0];
                    case "screenHeight" -> size[1];
                    default -> throw new AssertionError(method.getName());
                });
    }

    private static IGuiProperties snapshot(Constructor<?> constructor, int width, int height) throws Exception {
        return (IGuiProperties) constructor.newInstance(Object.class, (width - 200) / 2, (height - 196) / 2,
                200, 196, width, height);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        System.out.println("PASS: " + message);
    }

    public static void main(String[] args) throws Exception {
        GuiPropertiesCache<Object> oldCache = new GuiPropertiesCache<>(ignored -> null);
        oldCache.createUpdater(() -> {}).updateGuiProperties(oldProperties()).update();
        size[0] = 640;
        size[1] = 360;
        oldCache.createUpdater(() -> {}).updateGuiProperties(oldProperties()).update();
        check(!oldCache.hasValidScreen(), "old mutable geometry reproduces JEI staying hidden after initialization");

        Class<?> type = Class.forName("com.extrarawstyle.veinminerplus.VeinMinerPlusJeiPlugin$SelectionGuiProperties");
        Constructor<?> constructor = type.getDeclaredConstructor(Class.class, int.class, int.class, int.class,
                int.class, int.class, int.class);
        constructor.setAccessible(true);
        GuiPropertiesCache<Object> cache = new GuiPropertiesCache<>(ignored -> null);
        AtomicInteger updates = new AtomicInteger();
        cache.createUpdater(updates::incrementAndGet).updateGuiProperties(null).update();
        check(!cache.hasValidScreen(), "uninitialized screen has no GUI properties");
        IGuiProperties first = snapshot(constructor, 640, 360);
        cache.createUpdater(updates::incrementAndGet).updateGuiProperties(first).update();
        check(cache.hasValidScreen() && updates.get() == 1, "initialized snapshot enables JEI");
        cache.createUpdater(updates::incrementAndGet).updateGuiProperties(snapshot(constructor, 640, 360)).update();
        check(updates.get() == 1, "unchanged dimensions do not trigger another layout");
        cache.createUpdater(updates::incrementAndGet).updateGuiProperties(snapshot(constructor, 960, 540)).update();
        check(cache.hasValidScreen() && updates.get() == 2, "resize triggers JEI layout refresh");
        check(first.screenWidth() == 640 && first.screenHeight() == 360, "cached snapshot stays immutable");
        cache.createUpdater(updates::incrementAndGet).updateGuiProperties(null).update();
        check(!cache.hasValidScreen(), "closing the selector clears JEI geometry");
        cache.createUpdater(updates::incrementAndGet).updateGuiProperties(snapshot(constructor, 640, 360)).update();
        check(cache.hasValidScreen(), "reopening the selector enables JEI again");
    }
}
