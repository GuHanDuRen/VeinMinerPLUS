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
        private static final int SLOT_SIZE = 18;
private static final int SLOT_COLUMNS = 9;
private static final int SLOT_COUNT = XrayClientState.SLOT_COUNT;
private static final int PANEL_WIDTH = 176;
private static final int PANEL_HEIGHT = 68;
private static final int BUTTON_TOP = 42;
private static final int BUTTON_WIDTH = 78;
private final List<XrayClientState.Selection> selected = new ArrayList<>();
private int left;
private int top;
private XrayClientState.Selection dragged;
private int draggedSlot = -1;
public XraySelectionScreen() {
        super(Component.translatable("screen.veinminerplus.xray.title"));
        selected.addAll(XrayClientState.selection());
    }
@Override
    protected void init() {
        super.init();
        repositionElements();
        VeinMinerPlus.LOGGER.info("XraySelectionScreen init: width={}, height={}, left={}, top={}", width, height, left, top);
    }
@Override
    protected void repositionElements() {
        left = (this.width - PANEL_WIDTH) / 2;
        top = (this.height - PANEL_HEIGHT) / 2;
        clearWidgets();
        addRenderableWidget(Button.builder(Component.translatable("screen.veinminerplus.xray.clear"), button -> clearSelected())
                .bounds(left + 7, top + BUTTON_TOP, BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> finish())
                .bounds(left + PANEL_WIDTH - 7 - BUTTON_WIDTH, top + BUTTON_TOP, BUTTON_WIDTH, 20).build());
    }
private void clearSelected() {
        for (int i = 0; i < selected.size(); i++) {
            selected.set(i, new XrayClientState.Selection(ItemStack.EMPTY, selected.get(i).color()));
        }
    }
private void finish() {
        onClose();
    }
@Override
    public void onClose() {
        XrayClientState.setSelection(selected);
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }
public Rect2i slotArea(int index) {
        int x = left + 7 + (index % SLOT_COLUMNS) * SLOT_SIZE;
        int y = top + 17;
        return new Rect2i(x, y, 18, 18);
    }
void acceptGhostStack(ItemStack stack, int index) {
        if (!XrayClientState.canAccept(stack) || index < 0 || index >= SLOT_COUNT) {
            return;
        }
        selected.set(index, new XrayClientState.Selection(stack.copyWithCount(1), selected.get(index).color()));
    }
static boolean isSelectable(ItemStack stack) {
        return XrayClientState.canAccept(stack);
    }
@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int index = 0; index < SLOT_COUNT; index++) {
            if (slotArea(index).contains((int) mouseX, (int) mouseY)) {
                XrayClientState.Selection selection = selected.get(index);
                if (button == 2 && minecraft != null) {
                    final int slot = index;
                    minecraft.setScreen(new XrayColorScreen(this, selection.color(), color ->
                            selected.set(slot, new XrayClientState.Selection(selected.get(slot).stack(), color))));
                } else if (button == 1) {
                    selected.set(index, new XrayClientState.Selection(ItemStack.EMPTY, selection.color()));
                } else if (button == 0 && !selection.stack().isEmpty()) {
                    dragged = selection;
                    draggedSlot = index;
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
@Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragged != null) {
            for (int index = 0; index < SLOT_COUNT; index++) {
                if (slotArea(index).contains((int) mouseX, (int) mouseY)) {
                    XrayClientState.Selection target = selected.get(index);
                    int sourceColor = selected.get(draggedSlot).color();
                    selected.set(draggedSlot, new XrayClientState.Selection(target.stack(), sourceColor));
                    selected.set(index, new XrayClientState.Selection(dragged.stack(), target.color()));
                    dragged = null;
                    return true;
                }
            }
            dragged = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    }
    static class XrayColorScreen extends Screen {
        static class ColorSlider { void syncColor() {} }
        private final XraySelectionScreen parent;
private final IntConsumer save;
private final List<ColorSlider> sliders = new ArrayList<>();
private int color;
private EditBox hex;
private Button done;
private boolean syncing;
XrayColorScreen(XraySelectionScreen parent, int color, IntConsumer save) {
        super(Component.translatable("screen.veinminerplus.xray.color_title"));
        this.parent = parent;
        this.color = color;
        this.save = save;
    }
private void readHex(String value) {
        if (syncing) {
            return;
        }
        boolean valid = value.matches("#?[0-9a-fA-F]{6}");
        done.active = valid;
        hex.setTextColor(valid ? 0xE0E0E0 : 0xFF5555);
        if (valid) {
            color = Integer.parseInt(value.startsWith("#") ? value.substring(1) : value, 16);
            for (ColorSlider slider : sliders) {
                slider.syncColor();
            }
        }
    }
private void setColor(int value) {
        color = value & 0xFFFFFF;
        syncing = true;
        hex.setValue(String.format(Locale.ROOT, "#%06X", color));
        hex.setTextColor(0xE0E0E0);
        done.active = true;
        for (ColorSlider slider : sliders) {
            slider.syncColor();
        }
        syncing = false;
    }
private void finish() {
        if (done.active) {
            save.accept(color);
            onClose();
        }
    }
@Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

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
