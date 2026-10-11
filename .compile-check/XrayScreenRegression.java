import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

// Exercises production screen input and draft lifecycle methods without Minecraft or inventory packets.
public final class XrayScreenRegression {
    private static final Set<String> SELECTION_FIELDS = Set.of("SLOT_SIZE", "SLOT_COLUMNS", "SLOT_COUNT",
            "PANEL_WIDTH", "PANEL_HEIGHT", "BUTTON_TOP", "BUTTON_WIDTH", "selected", "left", "top", "dragged",
            "draggedSlot");
    private static final Set<String> SELECTION_METHODS = Set.of("<init>", "init", "repositionElements",
            "slotArea", "mouseClicked", "mouseReleased", "acceptGhostStack",
            "isSelectable", "clearSelected", "finish", "onClose");
    private static final Set<String> COLOR_FIELDS = Set.of("parent", "save", "sliders", "color", "hex", "done", "syncing");
    private static final Set<String> COLOR_METHODS = Set.of("<init>", "readHex", "setColor", "finish", "onClose");
    private static final String FIXTURE = """
        import java.util.*;
        import java.util.function.*;
        public class XrayScreenFixture {
            static int assertions;
            static final Minecraft GAME = new Minecraft();
            record Component() {
                static Component translatable(String key, Object... arguments) { return new Component(); }
            }
            static class CommonComponents { static final Component GUI_DONE = new Component(); }
            static class VeinMinerPlus {
                static final Logger LOGGER = new Logger();
                static class Logger { void info(String message, Object... arguments) {} }
            }
            static class Screen {
                Minecraft minecraft = GAME;
                int width = 640, height = 360;
                Screen(Component title) {}
                protected void init() {}
                protected void repositionElements() {}
                void clearWidgets() {}
                <T> T addRenderableWidget(T widget) { return widget; }
                public boolean mouseClicked(double x, double y, int button) { return false; }
                public boolean mouseReleased(double x, double y, int button) { return false; }
                public void onClose() {}
            }
            static class Minecraft {
                Screen screen;
                void setScreen(Screen screen) { this.screen = screen; }
            }
            static class ItemStack {
                static final ItemStack EMPTY = new ItemStack("", 0);
                final String id;
                final int count;
                ItemStack(String id, int count) { this.id = id; this.count = count; }
                boolean isEmpty() { return count == 0; }
                ItemStack copyWithCount(int count) { return new ItemStack(id, count); }
            }
            record Rect2i(int x, int y, int width, int height) {
                boolean contains(int px, int py) { return px >= x && px < x + width && py >= y && py < y + height; }
            }
            static class Button {
                boolean active = true;
                static Builder builder(Component label, Consumer<Button> callback) { return new Builder(); }
                static class Builder {
                    Builder bounds(int x, int y, int width, int height) { return this; }
                    Button build() { return new Button(); }
                }
            }
            static class EditBox {
                String value;
                int textColor;
                void setValue(String value) { this.value = value; }
                void setTextColor(int color) { textColor = color; }
            }
            static class XrayClientState {
                static final int SLOT_COUNT = 9, DEFAULT_COLOR = 0xFF5555;
                record Selection(ItemStack stack, int color) {}
                static List<Selection> saved = empty();
                static int saves;
                static List<Selection> empty() {
                    List<Selection> slots = new ArrayList<>();
                    for (int i = 0; i < SLOT_COUNT; i++) slots.add(new Selection(ItemStack.EMPTY, DEFAULT_COLOR));
                    return slots;
                }
                static List<Selection> selection() { return new ArrayList<>(saved); }
                static void setSelection(List<Selection> value) { saved = new ArrayList<>(value); saves++; }
                static boolean canAccept(ItemStack stack) { return !stack.isEmpty() && stack.id.endsWith("ore"); }
            }
            static class XraySelectionScreen extends Screen {
                $SELECTION$
            }
            static class XrayColorScreen extends Screen {
                static class ColorSlider { void syncColor() {} }
                $COLOR$
            }
            static void check(boolean condition, String label) {
                assertions++;
                if (!condition) throw new AssertionError(label);
            }
            static XraySelectionScreen reset() {
                XrayClientState.saved = XrayClientState.empty();
                XrayClientState.saves = 0;
                XraySelectionScreen screen = new XraySelectionScreen();
                screen.init();
                GAME.setScreen(screen);
                return screen;
            }
            static int count(XraySelectionScreen screen) {
                return (int) screen.selected.stream().filter(slot -> !slot.stack().isEmpty()).count();
            }
            static void click(XraySelectionScreen screen, Rect2i area, int button) {
                screen.mouseClicked(area.x + 8, area.y + 8, button);
            }
            static void release(XraySelectionScreen screen, Rect2i area) {
                screen.mouseReleased(area.x + 8, area.y + 8, 0);
            }
            static XrayColorScreen openColor(XraySelectionScreen screen, int slot) {
                click(screen, screen.slotArea(slot), 2);
                check(GAME.screen instanceof XrayColorScreen, "middle click opens editor");
                XrayColorScreen editor = (XrayColorScreen) GAME.screen;
                editor.hex = new EditBox();
                editor.done = new Button();
                editor.sliders.add(new XrayColorScreen.ColorSlider());
                return editor;
            }
            public static void run() {
                XraySelectionScreen screen = reset();
                screen.selected.set(5, new XrayClientState.Selection(ItemStack.EMPTY, 0x123456));
                ItemStack source = new ItemStack("uranium_ore", 64);
                screen.acceptGhostStack(source, 5);
                check(count(screen) == 1 && !screen.selected.get(5).stack().isEmpty(), "JEI fills only requested slot");
                check(screen.selected.get(5).color() == 0x123456, "JEI insertion preserves target preset color");
                check(screen.selected.get(5).stack().count == 1 && source.count == 64
                        && screen.selected.get(5).stack() != source, "JEI ghost copies one item without changing source");
                screen.acceptGhostStack(new ItemStack("nether_uranium_ore", 12), 5);
                check(screen.selected.get(5).color() == 0x123456 && screen.selected.get(5).stack().count == 1,
                        "JEI replacement preserves target color and copies one item");
                screen.acceptGhostStack(new ItemStack("stone", 64), 5);
                check(screen.selected.get(5).stack().id.equals("nether_uranium_ore"), "non-ore ghost inputs are rejected");

                for (int index = 0; index < 36; index++) {
                    int x = screen.left + 15 + (index % 9) * 18;
                    int y = index < 9 ? screen.top + 114 : screen.top + 56 + ((index - 9) / 9) * 18;
                    screen.mouseClicked(x, y, 0);
                    release(screen, screen.slotArea(0));
                }
                check(count(screen) == 1 && screen.selected.get(0).stack().isEmpty() && screen.dragged == null,
                        "removed inventory and hotbar coordinates cannot start hidden item drags");

                screen.selected.set(1, new XrayClientState.Selection(new ItemStack("iron_ore", 1), 0xABCDEF));
                click(screen, screen.slotArea(5), 0);
                release(screen, screen.slotArea(1));
                check(screen.selected.get(1).stack().id.equals("nether_uranium_ore")
                        && screen.selected.get(1).color() == 0xABCDEF, "swapped ore uses destination slot preset color");
                check(screen.selected.get(5).stack().id.equals("iron_ore")
                        && screen.selected.get(5).color() == 0x123456, "displaced ore uses source slot preset color");
                screen.selected.set(3, new XrayClientState.Selection(ItemStack.EMPTY, 0x445566));
                click(screen, screen.slotArea(1), 0);
                release(screen, screen.slotArea(3));
                check(screen.selected.get(3).stack().id.equals("nether_uranium_ore")
                        && screen.selected.get(3).color() == 0x445566, "moving ore into empty slot applies its preset color");
                check(screen.selected.get(1).stack().isEmpty() && screen.selected.get(1).color() == 0xABCDEF,
                        "vacated source slot retains its own preset color");
                click(screen, screen.slotArea(3), 0);
                release(screen, screen.slotArea(1));
                check(screen.selected.get(1).stack().id.equals("nether_uranium_ore")
                        && screen.selected.get(1).color() == 0xABCDEF && screen.selected.get(3).stack().isEmpty()
                        && screen.selected.get(3).color() == 0x445566, "moving ore back keeps both slot presets fixed");
                click(screen, screen.slotArea(5), 0);
                release(screen, screen.slotArea(5));
                check(screen.selected.get(5).stack().id.equals("iron_ore")
                        && screen.selected.get(5).color() == 0x123456, "same-slot drag leaves ore and preset unchanged");
                click(screen, screen.slotArea(1), 0);
                screen.mouseReleased(0, 0, 0);
                check(screen.selected.get(1).stack().id.equals("nether_uranium_ore"), "drop outside cancels without deleting ghost");
                click(screen, screen.slotArea(1), 1);
                check(screen.selected.get(1).stack().isEmpty() && screen.selected.get(1).color() == 0xABCDEF,
                        "right-click clears ore but keeps slot color");

                XrayColorScreen editor = openColor(screen, 1);
                editor.readHex("#12345");
                check(!editor.done.active, "incomplete HEX disables completion");
                editor.finish();
                check(GAME.screen == editor && screen.selected.get(1).color() == 0xABCDEF,
                        "invalid HEX cannot save or close the editor");
                editor.readHex("#55aaff");
                check(editor.done.active && editor.color == 0x55AAFF, "valid HEX updates color preview");
                editor.finish();
                check(GAME.screen == screen && screen.selected.get(1).stack().isEmpty()
                        && screen.selected.get(1).color() == 0x55AAFF, "empty slot color saves and returns same parent draft");
                screen.init();
                screen.repositionElements();
                check(screen.selected.get(5).stack().id.equals("iron_ore") && screen.selected.get(1).color() == 0x55AAFF,
                        "return and resize do not reload or discard selection draft");

                editor = openColor(screen, 5);
                editor.readHex("00FF00");
                editor.onClose();
                check(GAME.screen == screen && screen.selected.get(5).color() == 0x123456,
                        "cancel returns parent without committing valid draft color");
                editor = openColor(screen, 5);
                editor.setColor(0xFFAA00);
                check(editor.hex.value.equals("#FFAA00") && editor.done.active, "preset synchronizes HEX and enables save");
                editor.finish();
                check(screen.selected.get(5).color() == 0xFFAA00
                        && screen.selected.get(5).stack().id.equals("iron_ore"), "filled slot color edit preserves ore");

                screen.clearSelected();
                check(count(screen) == 0 && screen.selected.get(1).color() == 0x55AAFF
                        && screen.selected.get(5).color() == 0xFFAA00 && screen.selected.get(3).color() == 0x445566,
                        "clear empties all ghosts and retains slot color presets");
                screen.finish();
                check(XrayClientState.saved.size() == 9 && XrayClientState.saves == 1
                        && XrayClientState.saved.get(5).color() == 0xFFAA00
                        && XrayClientState.saved.get(3).color() == 0x445566 && GAME.screen == null,
                        "completion saves all nine slots including empty slot colors once");
                System.out.println("PASS: " + assertions + " production screen input and color-draft assertions");
            }
        }
        """;

    private static SimpleJavaFileObject source(String name, String code) {
        return new SimpleJavaFileObject(URI.create("string:///" + name + ".java"), JavaFileObject.Kind.SOURCE) {
            public CharSequence getCharContent(boolean ignoreEncodingErrors) { return code; }
        };
    }

    private static String extract(Path path, String className, Set<String> fields, Set<String> methods) throws Exception {
        String code = Files.readString(path);
        var compiler = ToolProvider.getSystemJavaCompiler();
        JavacTask parser = (JavacTask) compiler.getTask(null, null, null, List.of("-proc:none"), null,
                List.of(source(className, code)));
        var positions = Trees.instance(parser).getSourcePositions();
        StringBuilder result = new StringBuilder();
        Set<String> extracted = new HashSet<>();
        for (var tree : parser.parse()) {
            new TreeScanner<Void, Void>() {
                public Void visitClass(ClassTree type, Void ignored) {
                    return type.getSimpleName().contentEquals(className) ? super.visitClass(type, ignored) : null;
                }
                public Void visitVariable(VariableTree field, Void ignored) {
                    if (fields.contains(field.getName().toString())) {
                        result.append(code, (int) positions.getStartPosition(tree, field),
                                (int) positions.getEndPosition(tree, field)).append('\n');
                    }
                    return null;
                }
                public Void visitMethod(MethodTree method, Void ignored) {
                    if (methods.contains(method.getName().toString())) {
                        result.append(code, (int) positions.getStartPosition(tree, method),
                                (int) positions.getEndPosition(tree, method)).append('\n');
                        extracted.add(method.getName().toString());
                    }
                    return null;
                }
            }.scan(tree, null);
        }
        if (!extracted.equals(methods)) throw new AssertionError("missing production methods: " + extracted);
        return result.toString();
    }

    public static void main(String[] args) throws Exception {
        Path sources = Path.of("src/main/java/com/extrarawstyle/veinminerplus");
        String code = FIXTURE.replace("$SELECTION$", extract(sources.resolve("XraySelectionScreen.java"),
                        "XraySelectionScreen", SELECTION_FIELDS, SELECTION_METHODS))
                .replace("$COLOR$", extract(sources.resolve("XrayColorScreen.java"),
                        "XrayColorScreen", COLOR_FIELDS, COLOR_METHODS));
        Path output = Path.of(".compile-check/xray-screen-regression");
        Files.createDirectories(output);
        Files.writeString(output.resolve("XrayScreenFixture.java"), code);
        if (!ToolProvider.getSystemJavaCompiler().getTask(null, null, null, List.of("-d", output.toString()), null,
                List.of(source("XrayScreenFixture", code))).call()) throw new AssertionError("fixture compilation failed");
        try (var loader = new URLClassLoader(new java.net.URL[] {output.toUri().toURL()})) {
            loader.loadClass("XrayScreenFixture").getMethod("run").invoke(null);
        }
    }
}
