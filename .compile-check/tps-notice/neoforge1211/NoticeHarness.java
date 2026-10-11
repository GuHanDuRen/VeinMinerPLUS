import java.util.*;
public class NoticeHarness {
static Component blastRadiusReductionNotice; static int blastRadiusReductionNoticeTicks;
    static void showBlastRadiusReduced(double tps, int oldDistance, int newDistance) {
        blastRadiusReductionNotice = Component.translatable("message.veinminerplus.blast_radius_reduced_notice",
                String.format(Locale.ROOT, "%.1f", tps), oldDistance, newDistance);
        blastRadiusReductionNoticeTicks = 200;
    }
    static void renderBlastRadiusReductionNotice(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (blastRadiusReductionNotice == null || minecraft.options.hideGui
                || minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }

        int width = minecraft.getWindow().getGuiScaledWidth();
        int bottomY = minecraft.getWindow().getGuiScaledHeight() - 82;
        var lines = minecraft.font.split(blastRadiusReductionNotice, Math.max(1, Math.min(520, width - 24)));
        int y = bottomY - (lines.size() - 1) * minecraft.font.lineHeight;
        int textWidth = 0;
        for (var line : lines) {
            textWidth = Math.max(textWidth, minecraft.font.width(line));
        }
        int left = (width - textWidth) / 2;
        graphics.fill(left - 4, y - 2, left + textWidth + 4, bottomY + minecraft.font.lineHeight, 0xC0101820);
        for (var line : lines) {
            int lineWidth = minecraft.font.width(line);
            int x = (width - lineWidth) / 2;
            graphics.drawString(minecraft.font, line, x, y, 0xFFFFC66D, false);
            y += minecraft.font.lineHeight;
        }
    }
    private static void tickBlastRadiusReductionNotice() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || blastRadiusReductionNoticeTicks <= 1) {
            blastRadiusReductionNotice = null;
            blastRadiusReductionNoticeTicks = 0;
        } else {
            blastRadiusReductionNoticeTicks--;
        }
    }
static void show() { showBlastRadiusReduced(9.5, Integer.MAX_VALUE, 1073741823); }
          static void tick() { tickBlastRadiusReductionNotice(); }
          static boolean active() { return blastRadiusReductionNotice != null; }
          static int ticks() { return blastRadiusReductionNoticeTicks; }
          static void render() { renderBlastRadiusReductionNotice(new GuiGraphics()); }
static final String[] TEMPLATES = {"Low TPS (%s): blast radius automatically reduced from %s to %s","TPS 过低（%s），本次爆破范围已自动降低：%s → %s"};

    static final class Options { boolean hideGui; }
    static final class Settings { boolean hideGUI; }
    static final class Window {
        int width = 640, height = 360;
        int getGuiScaledWidth() { return width; }
        int getGuiScaledHeight() { return height; }
    }
    static final class Minecraft {
        static final Minecraft INSTANCE = new Minecraft();
        Object player = new Object(), level = new Object(), world = new Object(), screen, currentScreen;
        final Options options = new Options();
        final Settings gameSettings = new Settings();
        final Window window = new Window();
        final Font font = new Font(), fontRenderer = font;
        static Minecraft getInstance() { return INSTANCE; }
        static Minecraft getMinecraft() { return INSTANCE; }
        Window getWindow() { return window; }
    }
    static final class Component {
        final String text;
        Component(String text) { this.text = text; }
        static Component translatable(String key, Object... args) {
            return new Component(net.minecraft.client.resources.I18n.format(key, args));
        }
    }
    static final class Font {
        final int lineHeight = 9, FONT_HEIGHT = 9;
        int width(String text) {
            return text.codePoints().map(c -> c > 255 ? 9 : 6).sum();
        }
        int getStringWidth(String text) { return width(text); }
        List<String> split(Component component, int max) { return listFormattedStringToWidth(component.text, max); }
        List<String> listFormattedStringToWidth(String text, int max) {
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (char c : text.toCharArray()) {
                if (line.length() > 0 && width(line.toString() + c) > max) {
                    lines.add(line.toString()); line.setLength(0);
                }
                line.append(c);
            }
            lines.add(line.toString());
            return lines;
        }
        int drawStringWithShadow(String text, float x, float y, int color) {
            DRAW.add(new int[] {(int)x, (int)y, width(text), color}); return width(text);
        }
    }
    static final List<int[]> DRAW = new ArrayList<>(), BOX = new ArrayList<>();
    static final class GuiGraphics {
        void fill(int l, int t, int r, int b, int color) { BOX.add(new int[] {l,t,r,b,color}); }
        void drawString(Font font, String text, int x, int y, int color, boolean shadow) {
            DRAW.add(new int[] {x,y,font.width(text),color});
        }
    }
    static final class Gui {
        static void drawRect(int l, int t, int r, int b, int color) { BOX.add(new int[] {l,t,r,b,color}); }
    }
    static final class ScaledResolution {
        int getScaledWidth() { return Minecraft.INSTANCE.window.width; }
        int getScaledHeight() { return Minecraft.INSTANCE.window.height; }
    }
    static final class NetworkHandler {
        record LowTpsRadiusMessage(double tps, int oldDistance, int reducedDistance) {
            double getTps() { return tps; }
            int getOldDistance() { return oldDistance; }
            int getReducedDistance() { return reducedDistance; }
        }
    }
    static final class TickEvent {
        enum Phase { START, END }
        record ClientTickEvent(Phase phase) { }
    }
    static void beginOverlayRender() { }
    static void restoreOverlayBlend() { }
    static void endOverlayRender() { }
    static void clearChainProgress() { }
    static void restorePendingWhitelistSelection(Minecraft minecraft) { }
    static void syncKeyState() { }
    static final class XrayClientState { static void tick(Minecraft minecraft) { } }
    static boolean chainProgressVisible, keyStateSent, whitelistSelectionActive, estimatedContainer;
    static int chainProgressHideTicks, estimatedCount, estimatedInteractionCount;
    static long lastProgressSequence;
    static Object whitelistSelectionScreen, pendingWhitelistSelectionReturn, estimatedWorld,
            estimatedOrigin, estimatedBlock, estimatedFaceAxis;

    static int checks;
    static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    static void resetWorld() {
        Minecraft mc = Minecraft.INSTANCE;
        mc.player = new Object(); mc.level = new Object(); mc.world = new Object();
        mc.screen = null; mc.currentScreen = null;
        mc.options.hideGui = false; mc.gameSettings.hideGUI = false;
    }
    static void draw() { DRAW.clear(); BOX.clear(); render(); }
    public static void main(String[] args) {
        resetWorld(); draw(); check(DRAW.isEmpty(), "initially hidden");
        show(); check(ticks() == 200 && active(), "start at 200 ticks");
        for (int i = 1; i <= 200; i++) {
            tick(); check(ticks() == 200 - i, "tick count " + i);
            check(active() == (i < 200), "active at tick " + i);
        }
        draw(); check(DRAW.isEmpty(), "expires without render calls");
        show(); for (int i = 0; i < 50; i++) tick(); show();
        check(ticks() == 200, "new notice resets timer");
        Minecraft mc = Minecraft.INSTANCE;
        for (int gate = 0; gate < 4; gate++) {
            resetWorld(); show();
            if (gate == 0) { mc.options.hideGui = true; mc.gameSettings.hideGUI = true; }
            if (gate == 1) mc.player = null;
            if (gate == 2) { mc.level = null; mc.world = null; }
            if (gate == 3) { mc.screen = new Object(); mc.currentScreen = new Object(); }
            draw(); check(DRAW.isEmpty() && BOX.isEmpty(), "render gate " + gate);
        }
        for (int gate = 0; gate < 2; gate++) {
            resetWorld(); show();
            if (gate == 0) mc.player = null; else { mc.level = null; mc.world = null; }
            tick(); check(!active() && ticks() == 0, "disconnect gate " + gate);
            resetWorld(); draw(); check(DRAW.isEmpty(), "no stale notice on reconnect");
        }
        boolean wrapped = false;
        for (String template : TEMPLATES) {
            net.minecraft.client.resources.I18n.template = template;
            for (int[] size : new int[][] {{640,360},{320,240},{1920,1080}}) {
                resetWorld(); mc.window.width = size[0]; mc.window.height = size[1]; show(); draw();
                check(!DRAW.isEmpty(), "visible without key or GUI");
                wrapped |= DRAW.size() > 1;
                check(DRAW.get(DRAW.size()-1)[1] == size[1]-82, "last line anchored");
                for (int[] text : DRAW) {
                    check(text[0] >= 0 && text[0] + text[2] <= size[0], "horizontal fit");
                    check(text[1] >= 0 && text[1] + 9 <= size[1]-72, "above actionbar");
                    check(text[0] == (size[0]-text[2])/2, "centered line");
                    check(text[3] == 0xFFFFC66D, "yellow text");
                }
                for (int[] box : BOX) {
                    check(box[0] >= 0 && box[2] <= size[0] && box[1] >= 0, "background fit");
                    check(box[3] <= size[1]-72, "background avoids actionbar");
                }
            }
        }
        check(wrapped, "long text exercised wrapping");
        System.out.println("PASS " + checks + " assertions: timer, refresh, gates, disconnect, wrapping, positions, color");
    }

}