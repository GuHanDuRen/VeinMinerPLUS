package com.extrarawstyle.veinminerplus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.oredict.OreDictionary;
import org.lwjgl.input.Mouse;

public class VeinMinerConfigScreen extends GuiScreen {
    private static final int WHITELIST_ROW_HEIGHT = 20;
    private static final int WHITELIST_MAX_VISIBLE_ROWS = 5;
    private static final int WHITELIST_MAX_ROWS = Config.MAX_WHITELIST_ENTRIES + 1;
    private static final int WHITELIST_CONTROL_GAP = 4;
    private static final int WHITELIST_CONTROL_WIDTH = 20;
    private static final int WHITELIST_REMOVE_BUTTON_BASE = 1000;
    private static final int CONTENT_TOP = 34;
    private static final int FIELD_ROW_HEIGHT = 30;
    private static final int FIELD_HEIGHT = 18;
    private static final int COLUMN_GAP = 8;
    private static final int WHITELIST_LABEL_HEIGHT = 10;
    private static final int WHITELIST_SECTION_GAP = 4;
    private static final int BACKGROUND_COLOR = 0xED101820;
    private static final int PANEL_COLOR = 0xF018242D;
    private static final int BORDER_COLOR = 0xFF2B414C;
    private static final int HEADER_COLOR = 0xFF1D3039;
    private static final int ACCENT_COLOR = 0xFF59D8BC;
    private static final int TEXT_COLOR = 0xFFE5EEF2;
    private static final int MUTED_TEXT_COLOR = 0xFF96ADB7;
    private static final String[] FIELD_LABELS = {
            "screen.veinminerplus.config.normal_limit", "screen.veinminerplus.config.normal_speed",
            "screen.veinminerplus.config.blast_limit", "screen.veinminerplus.config.blast_speed",
            "screen.veinminerplus.config.blast_distance", "screen.veinminerplus.config.blast_scan_speed",
            "screen.veinminerplus.config.low_tps"
    };
    private static final int[] FIELD_MINS = { 32, 1, 32, 1, 3, 1, 5 };
    private static final int[] FIELD_MAXES = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
            Integer.MAX_VALUE, Integer.MAX_VALUE, Config.MAX_BLAST_CHUNK_SCANS_PER_TICK, 20 };
    private static final Predicate<String> DIGITS_ONLY = new Predicate<String>() {
        @Override
        public boolean apply(String input) {
            for (int i = 0; i < input.length(); i++) {
                char character = input.charAt(i);
                if (character < '0' || character > '9') {
                    return false;
                }
            }
            return true;
        }
    };
    private final NetworkHandler.ConfigSnapshotMessage initial;
    private final List<GuiTextField> fields = new ArrayList<GuiTextField>();
    private final List<GuiTextField> whitelistRows = new ArrayList<GuiTextField>();
    private final List<GuiButton> whitelistRemoveButtons = new ArrayList<GuiButton>();
    private int whitelistLineCount;
    private int whitelistScrollRow;
    private String whitelistDraft;
    private int whitelistBaseY;
    private int whitelistViewportBottom;
    private int whitelistVisibleRows;
    private int whitelistX;
    private int whitelistWidth;
    private int contentTop;
    private int fieldRowHeight;
    private int fieldHeight;
    private int controlsTop;
    private int controlsBottom;
    private boolean manhattan;
    private boolean autoReduce;
    private boolean consumeHunger;
    private ChainMode mode;

    public VeinMinerConfigScreen(NetworkHandler.ConfigSnapshotMessage initial) {
        this.initial = initial;
        manhattan = initial.blastManhattan;
        autoReduce = initial.blastAutoReduceRadius;
        consumeHunger = initial.consumeHunger;
        mode = ChainMode.fromOrdinal(initial.mode);
    }

    @Override
    public void initGui() {
        captureDraftValues();
        String[] values = currentFieldValues();
        fields.clear();
        whitelistRemoveButtons.clear();
        int left = contentLeft();
        int columnWidth = columnWidth();
        boolean compact = height < 300;
        contentTop = compact ? 30 : CONTENT_TOP;
        fieldRowHeight = compact ? 26 : FIELD_ROW_HEIGHT;
        fieldHeight = compact ? 14 : FIELD_HEIGHT;
        for (int i = 0; i < values.length; i++) {
            int x = left + (i % 2) * (columnWidth + COLUMN_GAP);
            int y = contentTop + 11 + (i / 2) * fieldRowHeight;
            addField(x, y, columnWidth, values[i]);
        }

        int fullWidth = columnWidth * 2 + COLUMN_GAP;
        controlsTop = contentTop + 4 * fieldRowHeight + 4;
        int buttonHeight = compact ? 18 : 20;
        int buttonStep = buttonHeight + 4;
        buttonList.add(new StyledButton(100, left, controlsTop, columnWidth, buttonHeight,
                toggle("screen.veinminerplus.config.manhattan", manhattan)));
        buttonList.add(new StyledButton(101, left + columnWidth + COLUMN_GAP, controlsTop, columnWidth, buttonHeight,
                toggle("screen.veinminerplus.config.auto_radius", autoReduce)));
        buttonList.add(new StyledButton(102, left, controlsTop + buttonStep,
                compact ? columnWidth : fullWidth, buttonHeight,
                toggle("screen.veinminerplus.config.consume_hunger", consumeHunger)));
        buttonList.add(new StyledButton(103, compact ? left + columnWidth + COLUMN_GAP : left,
                controlsTop + buttonStep * (compact ? 1 : 2), compact ? columnWidth : fullWidth,
                buttonHeight, modeText()));
        controlsBottom = controlsTop + buttonStep * (compact ? 1 : 2) + buttonHeight;

        int whitelistY = controlsBottom + WHITELIST_SECTION_GAP + WHITELIST_LABEL_HEIGHT + 4;
        whitelistBaseY = whitelistY;
        whitelistVisibleRows = Math.max(1,
                Math.min(WHITELIST_MAX_VISIBLE_ROWS, (height - whitelistBaseY - 32) / WHITELIST_ROW_HEIGHT));
        whitelistViewportBottom = whitelistBaseY + whitelistVisibleRows * WHITELIST_ROW_HEIGHT;
        whitelistX = left;
        whitelistWidth = Math.max(48,
                fullWidth - WHITELIST_CONTROL_WIDTH * 2 - WHITELIST_CONTROL_GAP * 2);
        initializeWhitelistRows();
        int whitelistRemoveX = whitelistX + whitelistWidth + WHITELIST_CONTROL_GAP;
        for (int slot = 0; slot < whitelistVisibleRows; slot++) {
            GuiButton removeButton = new StyledButton(WHITELIST_REMOVE_BUTTON_BASE + slot,
                    whitelistRemoveX, whitelistBaseY + slot * WHITELIST_ROW_HEIGHT,
                    WHITELIST_CONTROL_WIDTH, FIELD_HEIGHT, "-");
            whitelistRemoveButtons.add(removeButton);
            buttonList.add(removeButton);
        }
        int whitelistAddX = whitelistRemoveX + WHITELIST_CONTROL_WIDTH + WHITELIST_CONTROL_GAP;
        buttonList.add(new StyledButton(104, whitelistAddX, whitelistY,
                WHITELIST_CONTROL_WIDTH, FIELD_HEIGHT, "+"));
        layoutWhitelistRows();

        int footerWidth = Math.min(100, Math.max(64, (width - 20 - 10) / 3));
        int footerY = height - 22;
        int footerTotalWidth = footerWidth * 3 + 10;
        int footerLeft = (width - footerTotalWidth) / 2;
        buttonList.add(new StyledButton(202, footerLeft, footerY, footerWidth, 20,
                I18n.format("screen.veinminerplus.config.reset_defaults")));
        buttonList.add(new StyledButton(200, footerLeft + footerWidth + 5, footerY, footerWidth, 20,
                I18n.format("gui.done")));
        buttonList.add(new StyledButton(201, footerLeft + (footerWidth + 5) * 2, footerY, footerWidth, 20,
                I18n.format("gui.cancel")));
    }

    private void initializeWhitelistRows() {
        String source = whitelistDraft != null ? whitelistDraft : initial.blockWhitelist;
        if (source == null || source.trim().isEmpty()) {
            source = Config.whitelistText();
        }

        List<String> lines = new ArrayList<String>();
        for (String line : (source == null ? "" : source).replace("\r\n", "\n")
                .split("[,;\\r\\n]", -1)) {
            if (!line.trim().isEmpty()) {
                lines.add(line.trim());
            }
        }
        if (lines.isEmpty()) {
            lines.add("*ore");
        }
        if (lines.size() < WHITELIST_MAX_ROWS) {
            lines.add("");
        }
        whitelistLineCount = Math.min(WHITELIST_MAX_ROWS, lines.size());
        whitelistRows.clear();
        for (int index = 0; index < WHITELIST_MAX_ROWS; index++) {
            GuiTextField row = new StyledTextField(20 + index, fontRenderer, whitelistX, whitelistBaseY,
                    whitelistWidth, FIELD_HEIGHT);
            row.setMaxStringLength(Config.MAX_WHITELIST_ENTRY_LENGTH);
            row.setText(index < whitelistLineCount ? lines.get(index) : "");
            whitelistRows.add(row);
        }
        whitelistScrollRow = Math.min(whitelistScrollRow, maxWhitelistScroll());
        layoutWhitelistRows();
    }

    private int maxWhitelistScroll() {
        return Math.max(0, whitelistLineCount - whitelistVisibleRows);
    }

    private void captureWhitelistDraft() {
        if (whitelistRows.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<String>();
        for (int index = 0; index < whitelistLineCount; index++) {
            lines.add(whitelistRows.get(index).getText());
        }
        whitelistDraft = joinWhitelistLines(lines);
    }

    private String whitelistText() {
        captureWhitelistDraft();
        return whitelistDraft == null ? "" : whitelistDraft;
    }

    private void setWhitelistLines(List<String> values) {
        List<String> lines = new ArrayList<String>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    lines.add(value.trim());
                }
                if (lines.size() >= Config.MAX_WHITELIST_ENTRIES) {
                    break;
                }
            }
        }
        if (lines.isEmpty()) {
            lines.add("*ore");
        }
        lines.add("");
        whitelistLineCount = Math.min(WHITELIST_MAX_ROWS, lines.size());
        whitelistDraft = joinWhitelistLines(lines);
        for (int index = 0; index < whitelistRows.size(); index++) {
            whitelistRows.get(index).setText(index < whitelistLineCount ? lines.get(index) : "");
        }
        whitelistScrollRow = Math.min(whitelistScrollRow, maxWhitelistScroll());
        layoutWhitelistRows();
    }

    private static String joinWhitelistLines(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append('\n');
            }
            result.append(value == null ? "" : value.trim());
        }
        return result.toString();
    }

    private void layoutWhitelistRows() {
        for (int index = 0; index < whitelistRows.size(); index++) {
            GuiTextField row = whitelistRows.get(index);
            int y = whitelistBaseY + (index - whitelistScrollRow) * WHITELIST_ROW_HEIGHT;
            row.x = whitelistX;
            row.y = y;
            row.setVisible(index < whitelistLineCount
                    && y + FIELD_HEIGHT > whitelistBaseY
                    && y < whitelistViewportBottom);
        }
        for (int slot = 0; slot < whitelistRemoveButtons.size(); slot++) {
            GuiButton button = whitelistRemoveButtons.get(slot);
            int rowIndex = whitelistScrollRow + slot;
            int y = whitelistBaseY + slot * WHITELIST_ROW_HEIGHT;
            boolean visible = rowIndex < whitelistLineCount && y < whitelistViewportBottom;
            button.x = whitelistX + whitelistWidth + WHITELIST_CONTROL_GAP;
            button.y = y;
            button.visible = visible;
            button.enabled = visible;
        }
    }

    private boolean insideWhitelistViewport(int mouseX, int mouseY) {
        return mouseX >= whitelistX && mouseX < whitelistX + whitelistWidth
                && mouseY >= whitelistBaseY && mouseY < whitelistViewportBottom;
    }

    private boolean scrollWhitelist(int wheel) {
        if (wheel == 0 || maxWhitelistScroll() <= 0) {
            return false;
        }
        int direction = wheel > 0 ? -1 : 1;
        int next = MathHelper.clamp(whitelistScrollRow + direction, 0, maxWhitelistScroll());
        if (next == whitelistScrollRow) {
            return false;
        }
        captureWhitelistDraft();
        whitelistScrollRow = next;
        layoutWhitelistRows();
        return true;
    }

    private String[] currentFieldValues() {
        String[] values = {
                Integer.toString(initial.maxNormalBlocks), Integer.toString(initial.maxNormalBlocksPerTick),
                Integer.toString(initial.maxBlastBlocks), Integer.toString(initial.maxBlastBlocksPerTick),
                Integer.toString(initial.blastSearchDistance), Integer.toString(initial.blastChunkScansPerTick),
                Integer.toString(initial.blastLowTpsThreshold)
        };
        for (int i = 0; i < Math.min(values.length, fields.size()); i++) {
            values[i] = fields.get(i).getText();
        }
        return values;
    }

    private int columnWidth() {
        return Math.min(150, Math.max(80, (width - 40 - COLUMN_GAP) / 2));
    }

    private int contentLeft() {
        int fullWidth = columnWidth() * 2 + COLUMN_GAP;
        return (width - fullWidth) / 2;
    }

    private void addField(int x, int y, int fieldWidth, String value) {
        GuiTextField field = new StyledTextField(fields.size(), fontRenderer, x, y, fieldWidth, fieldHeight);
        field.setValidator(DIGITS_ONLY);
        field.setText(value);
        field.setMaxStringLength(6);
        fields.add(field);
    }

    private String toggle(String key, boolean value) {
        return I18n.format(key) + ": " + I18n.format(value ? "options.on" : "options.off");
    }

    private String modeText() {
        return I18n.format("screen.veinminerplus.config.mode", I18n.format(mode.translationKey()));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 100) {
            manhattan = !manhattan;
            button.displayString = toggle("screen.veinminerplus.config.manhattan", manhattan);
        } else if (button.id == 101) {
            autoReduce = !autoReduce;
            button.displayString = toggle("screen.veinminerplus.config.auto_radius", autoReduce);
        } else if (button.id == 102) {
            consumeHunger = !consumeHunger;
            button.displayString = toggle("screen.veinminerplus.config.consume_hunger", consumeHunger);
        } else if (button.id == 103) {
            mode = ChainMode.cycle(mode, 1);
            button.displayString = modeText();
        } else if (button.id == 104) {
            beginWhitelistSelection();
        } else if (button.id >= WHITELIST_REMOVE_BUTTON_BASE
                && button.id < WHITELIST_REMOVE_BUTTON_BASE + whitelistRemoveButtons.size()) {
            int slot = button.id - WHITELIST_REMOVE_BUTTON_BASE;
            removeWhitelistEntry(whitelistScrollRow + slot);
        } else if (button.id == 202) {
            resetDefaults();
        } else if (button.id == 200) {
            save();
        } else if (button.id == 201) {
            mc.displayGuiScreen(null);
        }
    }

    private void beginWhitelistSelection() {
        VeinMinerPlusClient.beginWhitelistSelection(this);
    }

    void captureDraftValues() {
        captureWhitelistDraft();
    }

    void addAimedWhitelistEntry(IBlockState state) {
        if (state == null) {
            return;
        }
        String current = whitelistText();
        List<String> entries = new ArrayList<String>(Config.parseWhitelistText(current));
        if (entries.isEmpty()) {
            entries.add("*ore");
        }
        ItemStack stack = new ItemStack(state.getBlock(), 1, state.getBlock().getMetaFromState(state));
        int[] oreIds = OreDictionary.getOreIDs(stack);
        for (int oreId : oreIds) {
            String rule = Config.normalizeRule("ore:" + OreDictionary.getOreName(oreId));
            if (rule != null) {
                entries.add(rule);
            }
        }
        ResourceLocation id = state.getBlock().getRegistryName();
        if (id != null) {
            entries.add(id.toString());
        }
        setWhitelistLines(Config.normalizeWhitelist(entries));
        whitelistScrollRow = maxWhitelistScroll();
        layoutWhitelistRows();
    }

    private void removeWhitelistEntry(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= whitelistLineCount) {
            return;
        }
        List<String> lines = new ArrayList<String>();
        for (int index = 0; index < whitelistLineCount; index++) {
            lines.add(whitelistRows.get(index).getText());
        }
        lines.remove(rowIndex);
        setWhitelistLines(lines);
    }

    private void resetDefaults() {
        int[] defaults = {
                Config.DEFAULT_MAX_NORMAL_BLOCKS,
                Config.DEFAULT_MAX_NORMAL_BLOCKS_PER_TICK,
                Config.DEFAULT_MAX_BLAST_BLOCKS,
                Config.DEFAULT_MAX_BLAST_BLOCKS_PER_TICK,
                Config.DEFAULT_BLAST_SEARCH_DISTANCE,
                Config.DEFAULT_BLAST_CHUNK_SCANS_PER_TICK,
                Config.DEFAULT_BLAST_LOW_TPS_THRESHOLD
        };
        for (int index = 0; index < fields.size() && index < defaults.length; index++) {
            fields.get(index).setText(Integer.toString(defaults[index]));
        }
        manhattan = Config.DEFAULT_BLAST_MANHATTAN;
        autoReduce = Config.DEFAULT_BLAST_AUTO_REDUCE_RADIUS;
        consumeHunger = Config.DEFAULT_CONSUME_HUNGER;
        mode = ChainMode.fromOrdinal(Config.DEFAULT_MODE_ORDINAL);
        setWhitelistLines(Config.DEFAULT_BLOCK_WHITELIST);
        for (GuiButton button : buttonList) {
            if (button.id == 100) {
                button.displayString = toggle("screen.veinminerplus.config.manhattan", manhattan);
            } else if (button.id == 101) {
                button.displayString = toggle("screen.veinminerplus.config.auto_radius", autoReduce);
            } else if (button.id == 102) {
                button.displayString = toggle("screen.veinminerplus.config.consume_hunger", consumeHunger);
            } else if (button.id == 103) {
                button.displayString = modeText();
            }
        }
    }

    private void save() {
        int normal = parse(0, initial.maxNormalBlocks, FIELD_MINS[0], FIELD_MAXES[0]);
        int normalTick = parse(1, initial.maxNormalBlocksPerTick, FIELD_MINS[1], FIELD_MAXES[1]);
        int blast = parse(2, initial.maxBlastBlocks, FIELD_MINS[2], FIELD_MAXES[2]);
        int blastTick = parse(3, initial.maxBlastBlocksPerTick, FIELD_MINS[3], FIELD_MAXES[3]);
        int distance = parse(4, initial.blastSearchDistance, FIELD_MINS[4], FIELD_MAXES[4]);
        int scanPerTick = parse(5, initial.blastChunkScansPerTick, FIELD_MINS[5], FIELD_MAXES[5]);
        int lowTps = parse(6, initial.blastLowTpsThreshold, FIELD_MINS[6], FIELD_MAXES[6]);
        String whitelist = String.join("\n", Config.parseWhitelistText(whitelistText()));
        NetworkHandler.sendConfigUpdate(new NetworkHandler.ConfigUpdateMessage(normal, normalTick, blast, blastTick,
                distance, scanPerTick, lowTps, manhattan, autoReduce, consumeHunger, mode.id(), whitelist));
        VeinMinerPlusClient.setClientMode(mode);
        mc.displayGuiScreen(null);
    }

    private int parse(int index, int fallback, int min, int max) {
        try {
            return MathHelper.clamp(Integer.parseInt(fields.get(index).getText()), min, max);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    @Override
    public void updateScreen() {
        for (GuiTextField field : fields) {
            field.updateCursorCounter();
        }
        for (GuiTextField row : whitelistRows) {
            row.updateCursorCounter();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        for (GuiTextField field : fields) {
            field.textboxKeyTyped(typedChar, keyCode);
        }
        for (GuiTextField row : whitelistRows) {
            row.textboxKeyTyped(typedChar, keyCode);
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        for (GuiTextField field : fields) {
            field.mouseClicked(mouseX, mouseY, mouseButton);
        }
        for (GuiTextField row : whitelistRows) {
            row.mouseClicked(mouseX, mouseY, mouseButton);
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int mouseX = Mouse.getEventX() * width / mc.displayWidth;
            int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            if (insideWhitelistViewport(mouseX, mouseY) && scrollWhitelist(wheel)) {
                return;
            }
        }
        super.handleMouseInput();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(0, 0, width, height, BACKGROUND_COLOR);
        int left = contentLeft();
        int columnWidth = columnWidth();
        int right = left + columnWidth * 2 + COLUMN_GAP;
        drawRect(left - 8, 6, right + 8, 25, HEADER_COLOR);
        drawRect(left - 8, 6, right + 8, 7, ACCENT_COLOR);
        String title = fontRenderer.trimStringToWidth(I18n.format("screen.veinminerplus.config.title"),
                right - left);
        fontRenderer.drawString(title, (width - fontRenderer.getStringWidth(title)) / 2, 12, TEXT_COLOR);
        drawPanel(left - 8, contentTop - 5, right + 8, controlsTop - 5, PANEL_COLOR, BORDER_COLOR);
        drawPanel(left - 8, controlsTop - 3, right + 8, controlsBottom + 3, PANEL_COLOR, BORDER_COLOR);
        drawPanel(left - 8, whitelistBaseY - WHITELIST_LABEL_HEIGHT - 3, right + 8,
                whitelistViewportBottom, PANEL_COLOR, BORDER_COLOR);
        drawRect(left - 8, whitelistBaseY - WHITELIST_LABEL_HEIGHT - 3, left - 6,
                whitelistBaseY - 2, ACCENT_COLOR);
        drawRect(0, height - 24, width, height, BACKGROUND_COLOR);
        drawRect(left - 8, height - 25, right + 8, height - 24, BORDER_COLOR);
        for (int i = 0; i < fields.size(); i++) {
            int x = left + (i % 2) * (columnWidth + COLUMN_GAP);
            int y = contentTop + (i / 2) * fieldRowHeight;
            String label = fontRenderer.trimStringToWidth(I18n.format(FIELD_LABELS[i]), columnWidth);
            fontRenderer.drawString(label, x, y, MUTED_TEXT_COLOR);
            fields.get(i).drawTextBox();
        }
        if (!whitelistRows.isEmpty()) {
            int whitelistY = whitelistBaseY;
            String label = fontRenderer.trimStringToWidth(I18n.format("screen.veinminerplus.config.whitelist"),
                    right - left);
            fontRenderer.drawString(label, left, whitelistY - WHITELIST_LABEL_HEIGHT, TEXT_COLOR);
            for (GuiTextField row : whitelistRows) {
                row.drawTextBox();
            }
            drawWhitelistScrollbar();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        for (int i = 0; i < fields.size(); i++) {
            GuiTextField field = fields.get(i);
            if (mouseX >= field.x && mouseX < field.x + field.width
                    && mouseY >= field.y - 11 && mouseY < field.y + field.height) {
                List<String> tooltip = new ArrayList<String>();
                tooltip.add(I18n.format(FIELD_LABELS[i]));
                tooltip.add(FIELD_MINS[i] + " - " + FIELD_MAXES[i]);
                drawHoveringText(tooltip, mouseX, mouseY);
                break;
            }
        }
    }

    private void drawWhitelistScrollbar() {
        int max = maxWhitelistScroll();
        if (max <= 0) {
            return;
        }
        int trackX = whitelistX + whitelistWidth + 2;
        int trackTop = whitelistBaseY;
        int trackHeight = whitelistViewportBottom - whitelistBaseY;
        int thumbHeight = Math.max(10, trackHeight * whitelistVisibleRows / whitelistLineCount);
        int travel = Math.max(0, trackHeight - thumbHeight);
        int thumbY = trackTop + (int) Math.round(travel * (double) whitelistScrollRow / max);
        drawRect(trackX, trackTop, trackX + 2, whitelistViewportBottom, BORDER_COLOR);
        drawRect(trackX, thumbY, trackX + 2, thumbY + thumbHeight, ACCENT_COLOR);
    }

    private static void drawPanel(int left, int top, int right, int bottom, int fill, int border) {
        drawRect(left, top, right, bottom, fill);
        drawRect(left, top, right, top + 1, border);
        drawRect(left, bottom - 1, right, bottom, border);
        drawRect(left, top, left + 1, bottom, border);
        drawRect(right - 1, top, right, bottom, border);
    }

    private final class StyledButton extends GuiButton {
        private StyledButton(int id, int x, int y, int width, int height, String text) {
            super(id, x, y, width, height, text);
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            if (!visible) {
                return;
            }
            hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
            boolean primary = id == 200;
            int fill = primary ? ACCENT_COLOR : HEADER_COLOR;
            if (hovered && enabled) {
                fill = primary ? 0xFF80E5CE : 0xFF29434B;
            }
            int border = hovered && enabled || primary ? ACCENT_COLOR : BORDER_COLOR;
            drawPanel(x, y, x + width, y + height, fill, border);
            boolean toggleButton = id == 100 || id == 101 || id == 102;
            boolean on = id == 100 ? manhattan : id == 101 ? autoReduce : consumeHunger;
            if (toggleButton) {
                drawRect(x + 3, y + 5, x + 5, y + height - 5, on ? ACCENT_COLOR : MUTED_TEXT_COLOR);
            }
            int textColor = enabled ? primary ? 0xFF102820 : TEXT_COLOR : MUTED_TEXT_COLOR;
            String text = minecraft.fontRenderer.trimStringToWidth(displayString, width - 12);
            minecraft.fontRenderer.drawString(text, x + (width - minecraft.fontRenderer.getStringWidth(text)) / 2,
                    y + (height - 8) / 2, textColor);
            mouseDragged(minecraft, mouseX, mouseY);
        }
    }

    private static final class StyledTextField extends GuiTextField {
        private StyledTextField(int id, FontRenderer font, int x, int y, int width, int height) {
            super(id, font, x, y, width, height);
            setTextColor(TEXT_COLOR);
            setDisabledTextColour(MUTED_TEXT_COLOR);
        }

        @Override
        public void drawTextBox() {
            if (!getVisible()) {
                return;
            }
            drawPanel(x, y, x + width, y + height, 0xFF101820,
                    isFocused() ? ACCENT_COLOR : BORDER_COLOR);
            int insetY = (height - 8) / 2;
            setEnableBackgroundDrawing(false);
            x += 4;
            y += insetY;
            width -= 8;
            super.drawTextBox();
            width += 8;
            y -= insetY;
            x -= 4;
            setEnableBackgroundDrawing(true);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
