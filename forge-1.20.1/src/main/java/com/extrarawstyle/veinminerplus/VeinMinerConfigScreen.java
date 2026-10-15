package com.extrarawstyle.veinminerplus;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class VeinMinerConfigScreen extends Screen {
    private static final int WHITELIST_ROW_HEIGHT = 22;
    private static final int CONTROL_HEIGHT = 20;
    private static final int SECTION_HEADER_HEIGHT = 20;
    private static final int WHITELIST_VISIBLE_ROWS = 5;
    private static final int WHITELIST_MAX_ROWS = Config.MAX_WHITELIST_ENTRIES + 1;
    private static final int WHITELIST_CONTROL_GAP = 4;
    private static final int WHITELIST_CONTROL_WIDTH = 20;
    private static final int PANEL_COLOR = 0xF018242D;
    private static final int PANEL_BORDER_COLOR = 0xFF52636B;
    private static final int HEADER_COLOR = 0xFF1D3039;
    private static final int ACCENT_COLOR = 0xFF9AB4AB;
    private static final int TEXT_COLOR = 0xFFE5EEF2;
    private static final int MUTED_TEXT_COLOR = 0xFF96ADB7;

    private final NetworkHandler.ConfigSnapshotPayload initial;
    private final List<LabeledField> fields = new ArrayList<>();
    private final List<HelpArea> helpAreas = new ArrayList<>();
    private final List<SectionArea> sections = new ArrayList<>();
    private final List<PositionedWidget> contentWidgets = new ArrayList<>();
    private final List<Renderable> contentRenderables = new ArrayList<>();
    private final List<Renderable> footerRenderables = new ArrayList<>();
    private final List<String> draftValues = new ArrayList<>();
    private final RuleListEditor whitelistEditor = new RuleListEditor(false);
    private final RuleListEditor blacklistEditor = new RuleListEditor(true);
    private RuleListEditor selectingRuleList = whitelistEditor;
    private boolean manhattan;
    private boolean autoReduceRadius;
    private boolean consumeHunger;
    private ChainMode mode;
    private Button manhattanButton;
    private Button autoReduceRadiusButton;
    private Button consumeHungerButton;
    private Button modeButton;
    private Button doneButton;
    private double scrollOffset;
    private int maxScroll;
    private int viewportLeft;
    private int viewportTop;
    private int viewportRight;
    private int viewportBottom;
    private int panelX;
    private int panelWidth;
    private int footerLeft;
    private int footerRight;
    private int footerTop;

    VeinMinerConfigScreen(NetworkHandler.ConfigSnapshotPayload initial) {
        super(Component.translatable("screen.veinminerplus.config.title"));
        this.initial = initial;
        this.manhattan = initial.blastManhattan();
        this.autoReduceRadius = initial.blastAutoReduceRadius();
        this.consumeHunger = initial.consumeHunger();
        this.mode = ChainMode.fromOrdinal(initial.mode());
    }

    @Override
    protected void init() {
        captureDraftValues();
        clearWidgets();
        fields.clear();
        helpAreas.clear();
        sections.clear();
        contentWidgets.clear();
        contentRenderables.clear();
        footerRenderables.clear();

        panelWidth = Math.min(560, Math.max(260, width - 24));
        panelX = (width - panelWidth) / 2;
        viewportLeft = panelX;
        viewportTop = 46;
        viewportRight = panelX + panelWidth;
        viewportBottom = Math.max(viewportTop + 40, height - 36);

        int contentX = panelX + 12;
        int contentWidth = panelWidth - 24;
        boolean compactGrid = width >= 390;
        int cellGap = compactGrid ? 12 : 0;
        int cellWidth = compactGrid ? (contentWidth - cellGap) / 2 : contentWidth;
        int secondX = compactGrid ? contentX + cellWidth + cellGap : contentX;
        int top = 50;
        int headerHeight = SECTION_HEADER_HEIGHT;
        int rowHeight = 24;
        int sectionPadding = 8;
        int sectionGap = 8;

        int normalRows = compactGrid ? 1 : 2;
        int blastRows = compactGrid ? 3 : 5;
        int normalHeight = headerHeight + rowHeight * normalRows + sectionPadding;
        int blastHeight = headerHeight + rowHeight * blastRows + sectionPadding;
        int performanceHeight = headerHeight + rowHeight * (compactGrid ? 1 : 2) + sectionPadding;
        int playerHeight = headerHeight + rowHeight * (compactGrid ? 2 : 3) + sectionPadding
                + (WHITELIST_VISIBLE_ROWS - 1) * WHITELIST_ROW_HEIGHT
                + WHITELIST_VISIBLE_ROWS * WHITELIST_ROW_HEIGHT + sectionGap;

        int normalY = top;
        int blastY = normalY + normalHeight + sectionGap;
        int performanceY = blastY + blastHeight + sectionGap;
        int playerY = performanceY + performanceHeight + sectionGap;
        int contentBottom = playerY + playerHeight;
        maxScroll = Math.max(0, contentBottom - viewportBottom);
        scrollOffset = Mth.clamp(scrollOffset, 0.0D, (double) maxScroll);

        sections.add(new SectionArea(panelX, normalY, panelWidth, normalHeight,
                "screen.veinminerplus.config.section.normal"));
        sections.add(new SectionArea(panelX, blastY, panelWidth, blastHeight,
                "screen.veinminerplus.config.section.blast"));
        sections.add(new SectionArea(panelX, performanceY, panelWidth, performanceHeight,
                "screen.veinminerplus.config.section.performance"));
        sections.add(new SectionArea(panelX, playerY, panelWidth, playerHeight,
                "screen.veinminerplus.config.section.player"));

        int normalRow = normalY + headerHeight;
        addField("screen.veinminerplus.config.normal_limit", initial.maxNormalBlocks(), 32, Integer.MAX_VALUE,
                contentX, normalRow, cellWidth);
        addField("screen.veinminerplus.config.normal_speed", initial.maxNormalBlocksPerTick(), 1, Integer.MAX_VALUE,
                compactGrid ? secondX : contentX, compactGrid ? normalRow : normalRow + rowHeight, cellWidth);

        int blastRowOne = blastY + headerHeight;
        int blastRowTwo = compactGrid ? blastRowOne + rowHeight : blastRowOne + rowHeight * 2;
        addField("screen.veinminerplus.config.blast_limit", initial.maxBlastBlocks(), 32, Integer.MAX_VALUE,
                contentX, blastRowOne, cellWidth);
        addField("screen.veinminerplus.config.blast_speed", initial.maxBlastBlocksPerTick(), 1, Integer.MAX_VALUE,
                compactGrid ? secondX : contentX, compactGrid ? blastRowOne : blastRowOne + rowHeight, cellWidth);
        addField("screen.veinminerplus.config.blast_distance", initial.blastSearchDistance(), 3, Integer.MAX_VALUE,
                 contentX, blastRowTwo, cellWidth);
        manhattanButton = addContentButton(Button.builder(toggleText("screen.veinminerplus.config.manhattan", manhattan), button -> {
            manhattan = !manhattan;
            button.setMessage(toggleText("screen.veinminerplus.config.manhattan", manhattan));
        }).bounds(compactGrid ? secondX : contentX,
                compactGrid ? blastRowTwo : blastRowOne + rowHeight * 3, cellWidth, 20).build(StyledButton::new),
                compactGrid ? blastRowTwo : blastRowOne + rowHeight * 3);
        helpAreas.add(new HelpArea(compactGrid ? secondX : contentX,
                 compactGrid ? blastRowTwo : blastRowOne + rowHeight * 3, cellWidth, 20,
                 "screen.veinminerplus.config.manhattan.description"));
        int blastRowThree = compactGrid ? blastRowTwo + rowHeight : blastRowOne + rowHeight * 4;
        addField("screen.veinminerplus.config.blast_scan_speed", initial.blastChunkScansPerTick(), 1,
                Config.MAX_BLAST_CHUNK_SCANS_PER_TICK, contentX, blastRowThree, cellWidth);

        int performanceRow = performanceY + headerHeight;
        addField("screen.veinminerplus.config.low_tps", initial.blastLowTpsThreshold(), 5, 20,
                contentX, performanceRow, cellWidth);
        autoReduceRadiusButton = addContentButton(Button.builder(toggleText("screen.veinminerplus.config.auto_radius", autoReduceRadius), button -> {
            autoReduceRadius = !autoReduceRadius;
            button.setMessage(toggleText("screen.veinminerplus.config.auto_radius", autoReduceRadius));
        }).bounds(compactGrid ? secondX : contentX,
                compactGrid ? performanceRow : performanceRow + rowHeight, cellWidth, 20).build(StyledButton::new),
                compactGrid ? performanceRow : performanceRow + rowHeight);
        helpAreas.add(new HelpArea(compactGrid ? secondX : contentX,
                compactGrid ? performanceRow : performanceRow + rowHeight, cellWidth, 20,
                "screen.veinminerplus.config.auto_radius.description"));

        int behaviorRow = playerY + headerHeight;
        consumeHungerButton = addContentButton(Button.builder(toggleText("screen.veinminerplus.config.consume_hunger", consumeHunger), button -> {
            consumeHunger = !consumeHunger;
            button.setMessage(toggleText("screen.veinminerplus.config.consume_hunger", consumeHunger));
        }).bounds(contentX, behaviorRow, cellWidth, 20).build(StyledButton::new), behaviorRow);
        helpAreas.add(new HelpArea(contentX, behaviorRow, cellWidth, 20,
                "screen.veinminerplus.config.hunger.description"));
        modeButton = addContentButton(Button.builder(modeText(), button -> {
            mode = ChainMode.cycle(mode, 1);
            button.setMessage(modeText());
        }).bounds(compactGrid ? secondX : contentX,
                compactGrid ? behaviorRow : behaviorRow + rowHeight, cellWidth, 20).build(StyledButton::new),
                compactGrid ? behaviorRow : behaviorRow + rowHeight);
        helpAreas.add(new HelpArea(compactGrid ? secondX : contentX,
                compactGrid ? behaviorRow : behaviorRow + rowHeight, cellWidth, 20,
                "screen.veinminerplus.config.mode.description"));

        int whitelistBaseY = behaviorRow + (compactGrid ? rowHeight : rowHeight * 2);
        whitelistEditor.initialize(contentX, whitelistBaseY, contentWidth, initial.blockWhitelist());
        blacklistEditor.initialize(contentX,
                whitelistBaseY + WHITELIST_VISIBLE_ROWS * WHITELIST_ROW_HEIGHT + sectionGap,
                contentWidth, initial.blockBlacklist());

        int footerGap = 6;
        int actionWidth = Math.min(104, Math.max(64, (width - 24 - footerGap * 2) / 3));
        int footerTotalWidth = actionWidth * 3 + footerGap * 2;
        footerLeft = (width - footerTotalWidth) / 2;
        footerRight = footerLeft + footerTotalWidth;
        footerTop = height - 26;
        Button resetButton = addRenderableWidget(
                Button.builder(Component.translatable("screen.veinminerplus.config.reset_defaults"), button -> resetDefaults())
                        .bounds(footerLeft, footerTop, actionWidth, 20).build(StyledButton::new));
        doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> save())
                .bounds(footerLeft + actionWidth + footerGap, footerTop, actionWidth, 20).build(StyledButton::new));
        Button cancelButton = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(footerLeft + (actionWidth + footerGap) * 2, footerTop, actionWidth, 20).build(StyledButton::new));
        footerRenderables.add(resetButton);
        footerRenderables.add(doneButton);
        footerRenderables.add(cancelButton);
        draftValues.clear();
        layoutContentWidgets();
        updateSaveState();
    }

    @Override
    public void tick() {
        super.tick();
        updateSaveState();
    }

    private void updateSaveState() {
        doneButton.active = normalizedRules(whitelistEditor).length() <= NetworkHandler.MAX_WHITELIST_TEXT_LENGTH
                && normalizedRules(blacklistEditor).length() <= NetworkHandler.MAX_WHITELIST_TEXT_LENGTH;
    }

    private static String normalizedRules(RuleListEditor editor) {
        return String.join("\n", Config.parseWhitelistText(editor.text()));
    }

    void captureDraftValues() {
        if (!fields.isEmpty()) {
            draftValues.clear();
            for (LabeledField field : fields) {
                draftValues.add(field.box().getValue());
            }
        }
        whitelistEditor.captureDraft();
        blacklistEditor.captureDraft();
    }

    boolean isSelectingBlacklist() {
        return selectingRuleList == blacklistEditor;
    }

    void addAimedWhitelistEntry(BlockState state) {
        String current = selectingRuleList.text();
        List<String> entries = new ArrayList<>(Config.parseWhitelistText(current == null ? "" : current));
        if (!isSelectingBlacklist() && entries.isEmpty()) {
            entries.add("*ore");
        }
        int originalSize = entries.size();
        if (!isSelectingBlacklist()) {
            state.getTags().filter(tag -> tag.location().getPath().contains("ore"))
                    .forEach(tag -> entries.add("#" + tag.location()));
        }
        if (entries.size() == originalSize) {
            var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (id != null) {
                entries.add(id.toString());
            }
        }
        selectingRuleList.setLines(Config.normalizeWhitelist(entries));
        selectingRuleList.scrollRow = selectingRuleList.maxScrollRow();
        selectingRuleList.layoutRows();
    }

    private void resetDefaults() {
        int[] defaults = {
                Config.DEFAULT_MAX_NORMAL_BLOCKS,
                Config.DEFAULT_MAX_NORMAL_BLOCKS_PER_TICK,
                Config.DEFAULT_MAX_BLAST_BLOCKS,
                Config.DEFAULT_MAX_BLAST_BLOCKS_PER_TICK,
                Config.DEFAULT_BLAST_SEARCH_DISTANCE,
                Config.DEFAULT_BLAST_LOW_TPS_THRESHOLD
        };
        for (int index = 0; index < fields.size() && index < defaults.length; index++) {
            fields.get(index).box().setValue(Integer.toString(defaults[index]));
        }
        manhattan = Config.DEFAULT_BLAST_MANHATTAN;
        autoReduceRadius = Config.DEFAULT_BLAST_AUTO_REDUCE_RADIUS;
        consumeHunger = Config.DEFAULT_CONSUME_HUNGER;
        mode = ChainMode.fromOrdinal(Config.DEFAULT_MODE_ORDINAL);
        whitelistEditor.setLines(Config.DEFAULT_BLOCK_WHITELIST);
        blacklistEditor.setLines(Config.DEFAULT_BLOCK_BLACKLIST);
        manhattanButton.setMessage(toggleText("screen.veinminerplus.config.manhattan", manhattan));
        autoReduceRadiusButton.setMessage(toggleText("screen.veinminerplus.config.auto_radius", autoReduceRadius));
        consumeHungerButton.setMessage(toggleText("screen.veinminerplus.config.consume_hunger", consumeHunger));
        modeButton.setMessage(modeText());
    }

    private void addField(String translationKey, int value, int min, int max, int x, int baseY, int cellWidth) {
        int boxWidth = Math.min(84, Math.max(64, cellWidth / 3));
        int boxX = x + cellWidth - boxWidth - 4;
        EditBox box = new StyledEditBox(boxX, baseY, boxWidth, CONTROL_HEIGHT, Component.translatable(translationKey));
        int index = fields.size();
        String draftValue = index < draftValues.size() ? draftValues.get(index) : Integer.toString(value);
        box.setValue(draftValue);
        box.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        fields.add(new LabeledField(translationKey, x, baseY, cellWidth, min, max, box));
        helpAreas.add(new HelpArea(x, baseY, cellWidth, 20, translationKey + ".description"));
        addRenderableWidget(box);
        contentWidgets.add(new PositionedWidget(box, baseY));
        contentRenderables.add(box);
    }

    private Button addContentButton(Button button, int baseY) {
        addRenderableWidget(button);
        contentWidgets.add(new PositionedWidget(button, baseY));
        contentRenderables.add(button);
        return button;
    }

    private Component toggleText(String key, boolean value) {
        return Component.translatable("screen.veinminerplus.config.toggle",
                Component.translatable(key), Component.translatable(value ? "options.on" : "options.off"));
    }

    private Component modeText() {
        return Component.translatable("screen.veinminerplus.config.mode",
                Component.translatable(mode.translationKey()));
    }

    private void save() {
        int normalBlocks = parse(fields.get(0).box(), initial.maxNormalBlocks(), 32, Integer.MAX_VALUE);
        int normalPerTick = parse(fields.get(1).box(), initial.maxNormalBlocksPerTick(), 1, Integer.MAX_VALUE);
        int blastBlocks = parse(fields.get(2).box(), initial.maxBlastBlocks(), 32, Integer.MAX_VALUE);
        int blastPerTick = parse(fields.get(3).box(), initial.maxBlastBlocksPerTick(), 1, Integer.MAX_VALUE);
        int distance = parse(fields.get(4).box(), initial.blastSearchDistance(), 3, Integer.MAX_VALUE);
        int scanPerTick = parse(fields.get(5).box(), initial.blastChunkScansPerTick(), 1,
                Config.MAX_BLAST_CHUNK_SCANS_PER_TICK);
        int lowTps = parse(fields.get(6).box(), initial.blastLowTpsThreshold(), 5, 20);
        String whitelist = normalizedRules(whitelistEditor);
        String blacklist = normalizedRules(blacklistEditor);
        if (whitelist.length() > NetworkHandler.MAX_WHITELIST_TEXT_LENGTH
                || blacklist.length() > NetworkHandler.MAX_WHITELIST_TEXT_LENGTH) {
            doneButton.active = false;
            return;
        }
        NetworkHandler.sendConfigUpdate(new NetworkHandler.ConfigUpdatePayload(normalBlocks, normalPerTick,
                blastBlocks, blastPerTick, distance, scanPerTick, lowTps, manhattan, autoReduceRadius,
                consumeHunger, mode.id(), whitelist, blacklist));
        VeinMinerPlusClient.setClientMode(mode);
        onClose();
    }

    private static int parse(EditBox box, int fallback, int min, int max) {
        try {
            return Mth.clamp(Integer.parseInt(box.getValue()), min, max);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void layoutContentWidgets() {
        int offset = (int) Math.round(scrollOffset);
        for (PositionedWidget positioned : contentWidgets) {
            positioned.widget().setY(positioned.baseY() - offset);
        }
        whitelistEditor.layoutRows();
        blacklistEditor.layoutRows();
    }

    private boolean scrollContent(double mouseX, double mouseY, double delta) {
        if (whitelistEditor.containsViewport(mouseX, mouseY) && whitelistEditor.scroll(delta)
                || blacklistEditor.containsViewport(mouseX, mouseY) && blacklistEditor.scroll(delta)) {
            return true;
        }
        if (!insideContentViewport(mouseX, mouseY) || maxScroll <= 0 || delta == 0.0D) {
            return false;
        }

        double next = Mth.clamp(scrollOffset - delta * 16.0D, 0.0D, (double) maxScroll);
        if (next == scrollOffset) {
            return false;
        }

        scrollOffset = next;
        layoutContentWidgets();
        return true;
    }

    private boolean insideContentViewport(double mouseX, double mouseY) {
        return mouseX >= viewportLeft && mouseX < viewportRight
                && mouseY >= viewportTop && mouseY < viewportBottom;
    }

    private boolean insideFooter(double mouseX, double mouseY) {
        return mouseY >= footerTop - 2 && mouseY < height
                && mouseX >= footerLeft && mouseX < footerRight;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return scrollContent(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (insideFooter(mouseX, mouseY)) {
            for (Renderable renderable : footerRenderables) {
                if (renderable instanceof AbstractWidget widget && widget.mouseClicked(mouseX, mouseY, button)) {
                    setFocused(widget);
                    if (button == 0) {
                        setDragging(true);
                    }
                    return true;
                }
            }
            return false;
        }
        return insideContentViewport(mouseX, mouseY) && super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0xA0101820);
        drawHeader(graphics);

        graphics.enableScissor(viewportLeft, viewportTop, viewportRight, viewportBottom);
        drawPanels(graphics);
        for (Renderable renderable : contentRenderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        drawSectionTitles(graphics);
        for (LabeledField field : fields) {
            drawFieldLabel(graphics, field);
        }
        whitelistEditor.drawLabel(graphics);
        whitelistEditor.drawScrollbar(graphics);
        blacklistEditor.drawLabel(graphics);
        blacklistEditor.drawScrollbar(graphics);
        graphics.disableScissor();

        graphics.fill(panelX, height - 32, panelX + panelWidth, height, 0xED101820);
        graphics.fill(panelX, height - 32, panelX + panelWidth, height - 31, PANEL_BORDER_COLOR);
        for (Renderable renderable : footerRenderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        drawScrollbar(graphics);
        for (HelpArea area : helpAreas) {
            if (area.contains(mouseX, mouseY, scrollOffset) && insideContentViewport(mouseX, mouseY)) {
                graphics.renderTooltip(font,
                        font.split(Component.translatable(area.translationKey()), Math.min(280, width - 24)),
                        mouseX, mouseY);
                break;
            }
        }
        if (!doneButton.active && doneButton.isMouseOver(mouseX, mouseY)) {
            graphics.renderTooltip(font,
                    font.split(Component.translatable("screen.veinminerplus.config.rules_too_long",
                            NetworkHandler.MAX_WHITELIST_TEXT_LENGTH), Math.min(280, width - 24)), mouseX, mouseY);
        }
    }

    private void drawPanels(GuiGraphics graphics) {
        int offset = (int) Math.round(scrollOffset);
        for (SectionArea section : sections) {
            int y = section.baseY() - offset;
            int right = section.x() + section.width();
            drawFrame(graphics, section.x(), y, section.width(), section.height(), PANEL_COLOR, PANEL_BORDER_COLOR);
            graphics.fill(section.x() + 1, y + 1, right - 1, y + SECTION_HEADER_HEIGHT - 4, HEADER_COLOR);
            graphics.fill(section.x() + 1, y + 1, section.x() + 3, y + SECTION_HEADER_HEIGHT - 4, ACCENT_COLOR);
        }
    }

    private void drawSectionTitles(GuiGraphics graphics) {
        int offset = (int) Math.round(scrollOffset);
        for (SectionArea section : sections) {
            graphics.drawString(font, Component.translatable(section.translationKey()), section.x() + 12,
                    section.baseY() - offset + 5, ACCENT_COLOR, false);
        }
    }

    private void drawFieldLabel(GuiGraphics graphics, LabeledField field) {
        int offset = (int) Math.round(scrollOffset);
        int y = field.baseY() - offset;
        int labelWidth = Math.max(40, field.cellWidth() - field.box().getWidth() - 12);
        String label = Component.translatable(field.translationKey()).getString();
        graphics.drawString(font, font.plainSubstrByWidth(label, labelWidth), field.x() + 4, y + 6,
                TEXT_COLOR, false);
    }

    private void drawHeader(GuiGraphics graphics) {
        graphics.fill(panelX, 6, panelX + panelWidth, 40, 0xED101820);
        graphics.fill(panelX, 6, panelX + 3, 40, ACCENT_COLOR);
        graphics.drawCenteredString(font, title, width / 2, 12, TEXT_COLOR);
        graphics.drawCenteredString(font, Component.translatable("screen.veinminerplus.config.subtitle"),
                width / 2, 28, MUTED_TEXT_COLOR);
    }

    private void drawScrollbar(GuiGraphics graphics) {
        if (maxScroll <= 0) {
            return;
        }

        int trackX = viewportRight + 4;
        int trackHeight = viewportBottom - viewportTop;
        int contentHeight = maxScroll + trackHeight;
        int thumbHeight = Math.max(20, trackHeight * trackHeight / contentHeight);
        int travel = trackHeight - thumbHeight;
        int thumbY = viewportTop + (int) Math.round(travel * scrollOffset / maxScroll);
        graphics.fill(trackX, viewportTop, trackX + 3, viewportBottom, PANEL_BORDER_COLOR);
        graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, ACCENT_COLOR);
    }

    private static void drawFrame(GuiGraphics graphics, int x, int y, int width, int height, int fill, int border) {
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }

    private final class RuleListEditor {
        private final boolean blacklist;
        private final List<EditBox> rows = new ArrayList<>();
        private final List<Button> removeButtons = new ArrayList<>();
        private int lineCount;
        private int scrollRow;
        private String draft;
        private int baseY;
        private int viewportBottom;
        private int x;
        private int width;
        private int removeX;
        private int labelX;
        private int labelWidth;

        private RuleListEditor(boolean blacklist) {
            this.blacklist = blacklist;
        }

        private String translationKey() {
            return "screen.veinminerplus.config." + (blacklist ? "blacklist" : "whitelist");
        }

        private void initialize(int contentX, int y, int contentWidth, String initialText) {
            baseY = y;
            viewportBottom = baseY + WHITELIST_VISIBLE_ROWS * WHITELIST_ROW_HEIGHT;
            labelX = contentX + 4;
            labelWidth = Math.min(132, Math.max(76, contentWidth / 3));
            x = contentX + labelWidth;
            width = Math.max(40,
                    contentWidth - labelWidth - WHITELIST_CONTROL_WIDTH * 2 - WHITELIST_CONTROL_GAP * 2);
            removeX = x + width + WHITELIST_CONTROL_GAP;
            String source = draft != null ? draft : initialText;
            if (!blacklist && (source == null || source.trim().isEmpty())) {
                source = Config.whitelistText();
            }
            List<String> lines = new ArrayList<>();
            for (String line : (source == null ? "" : source).split("[,;\\r\\n]", -1)) {
                if (!line.trim().isEmpty()) {
                    lines.add(line.trim());
                }
            }

            for (EditBox row : rows) {
                removeWidget(row);
            }
            for (Button button : removeButtons) {
                removeWidget(button);
            }
            rows.clear();
            removeButtons.clear();
            for (int index = 0; index < WHITELIST_MAX_ROWS; index++) {
                EditBox row = new StyledEditBox(x, baseY, width, CONTROL_HEIGHT,
                        Component.translatable(translationKey()));
                row.setMaxLength(Config.MAX_WHITELIST_ENTRY_LENGTH);
                addRenderableWidget(row);
                contentRenderables.add(row);
                rows.add(row);

                int rowIndex = index;
                Button removeButton = Button.builder(Component.literal("-"), button -> removeEntry(rowIndex))
                        .bounds(removeX, baseY, WHITELIST_CONTROL_WIDTH, CONTROL_HEIGHT).build(StyledButton::new);
                addRenderableWidget(removeButton);
                contentRenderables.add(removeButton);
                removeButtons.add(removeButton);
            }
            setLines(lines);
            int addX = removeX + WHITELIST_CONTROL_WIDTH + WHITELIST_CONTROL_GAP;
            addContentButton(Button.builder(Component.literal("+"), button -> {
                selectingRuleList = this;
                VeinMinerPlusClient.beginWhitelistSelection(VeinMinerConfigScreen.this);
            }).bounds(addX, baseY, WHITELIST_CONTROL_WIDTH, CONTROL_HEIGHT).build(StyledButton::new), baseY);
            helpAreas.add(new HelpArea(contentX, baseY, contentWidth,
                    WHITELIST_VISIBLE_ROWS * WHITELIST_ROW_HEIGHT, translationKey() + ".description"));
        }

        private List<String> values() {
            List<String> values = new ArrayList<>();
            for (int index = 0; index < lineCount; index++) {
                values.add(rows.get(index).getValue().trim());
            }
            return values;
        }

        private void captureDraft() {
            if (!rows.isEmpty()) {
                draft = String.join("\n", values());
            }
        }

        private String text() {
            captureDraft();
            return draft == null ? "" : draft;
        }

        private void setLines(List<String> values) {
            List<String> lines = new ArrayList<>();
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    lines.add(value.trim());
                }
                if (lines.size() >= Config.MAX_WHITELIST_ENTRIES) {
                    break;
                }
            }
            if (!blacklist && lines.isEmpty()) {
                lines.add("*ore");
            }
            lines.add("");
            lineCount = lines.size();
            draft = String.join("\n", lines);
            for (int index = 0; index < rows.size(); index++) {
                rows.get(index).setValue(index < lineCount ? lines.get(index) : "");
            }
            scrollRow = Math.min(scrollRow, maxScrollRow());
            layoutRows();
        }

        private void removeEntry(int index) {
            if (index >= 0 && index < lineCount) {
                List<String> values = values();
                values.remove(index);
                setLines(values);
            }
        }

        private int maxScrollRow() {
            return Math.max(0, lineCount - WHITELIST_VISIBLE_ROWS);
        }

        private void layoutRows() {
            int outerOffset = (int) Math.round(scrollOffset);
            int innerOffset = scrollRow * WHITELIST_ROW_HEIGHT;
            for (int index = 0; index < rows.size(); index++) {
                EditBox row = rows.get(index);
                Button removeButton = removeButtons.get(index);
                int y = baseY + index * WHITELIST_ROW_HEIGHT - innerOffset - outerOffset;
                row.setX(x);
                row.setY(y);
                removeButton.setX(removeX);
                removeButton.setY(y);
                boolean visible = index < lineCount
                        && y + WHITELIST_ROW_HEIGHT > VeinMinerConfigScreen.this.viewportTop
                        && y < VeinMinerConfigScreen.this.viewportBottom
                        && y + WHITELIST_ROW_HEIGHT > baseY - outerOffset
                        && y < viewportBottom - outerOffset;
                row.setVisible(visible);
                removeButton.visible = visible;
                removeButton.active = visible;
            }
        }

        private boolean containsViewport(double mouseX, double mouseY) {
            int offset = (int) Math.round(scrollOffset);
            return mouseX >= x && mouseX < removeX + WHITELIST_CONTROL_WIDTH
                    && mouseY >= baseY - offset && mouseY < viewportBottom - offset
                    && mouseY >= VeinMinerConfigScreen.this.viewportTop
                    && mouseY < VeinMinerConfigScreen.this.viewportBottom;
        }

        private boolean scroll(double delta) {
            int next = Mth.clamp(scrollRow + (delta > 0.0D ? -1 : 1), 0, maxScrollRow());
            if (delta == 0.0D || next == scrollRow) {
                return false;
            }
            captureDraft();
            scrollRow = next;
            layoutRows();
            return true;
        }

        private void drawLabel(GuiGraphics graphics) {
            int y = baseY - (int) Math.round(scrollOffset);
            String label = Component.translatable(translationKey()).getString();
            graphics.drawString(font, font.plainSubstrByWidth(label, labelWidth), labelX, y + 6, TEXT_COLOR, false);
        }

        private void drawScrollbar(GuiGraphics graphics) {
            int max = maxScrollRow();
            if (max <= 0) {
                return;
            }
            int offset = (int) Math.round(scrollOffset);
            int top = Math.max(VeinMinerConfigScreen.this.viewportTop, baseY - offset);
            int bottom = Math.min(VeinMinerConfigScreen.this.viewportBottom, viewportBottom - offset);
            if (bottom <= top) {
                return;
            }
            int trackX = x + width + 2;
            int height = bottom - top;
            int thumbHeight = Math.max(12, height * WHITELIST_VISIBLE_ROWS / lineCount);
            int travel = Math.max(0, height - thumbHeight);
            int thumbY = top + (int) Math.round(travel * (double) scrollRow / max);
            graphics.fill(trackX, top, trackX + 2, bottom, PANEL_BORDER_COLOR);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, ACCENT_COLOR);
        }
    }

    private final class StyledButton extends Button {
        private StyledButton(Button.Builder builder) {
            super(builder);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean highlighted = isHoveredOrFocused();
            boolean primary = this == doneButton;
            boolean enabledToggle = (this == manhattanButton && manhattan)
                    || (this == autoReduceRadiusButton && autoReduceRadius)
                    || (this == consumeHungerButton && consumeHunger);
            int fill = primary ? (highlighted ? 0xFF82E5CE : ACCENT_COLOR)
                    : (highlighted ? 0xFF2D4855 : 0xFF21333E);
            int border = highlighted || primary ? ACCENT_COLOR : PANEL_BORDER_COLOR;
            drawFrame(graphics, getX(), getY(), getWidth(), getHeight(), fill, border);
            if (this == manhattanButton || this == autoReduceRadiusButton || this == consumeHungerButton) {
                graphics.fill(getX() + 1, getY() + 4, getX() + 3, getY() + getHeight() - 4,
                        enabledToggle ? ACCENT_COLOR : MUTED_TEXT_COLOR);
            }
            int color = !active ? MUTED_TEXT_COLOR : primary ? 0xFF101820 : enabledToggle ? ACCENT_COLOR : TEXT_COLOR;
            renderScrollingString(graphics, font, 6, color);
        }
    }

    private final class StyledEditBox extends EditBox {
        private StyledEditBox(int x, int y, int width, int height, Component message) {
            super(VeinMinerConfigScreen.this.font, x, y, width, height, message);
            setTextColor(TEXT_COLOR);
            setTextColorUneditable(MUTED_TEXT_COLOR);
        }

        @Override
        public int getInnerWidth() {
            return getWidth() - 8;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            drawFrame(graphics, getX(), getY(), getWidth(), getHeight(), 0xFF101A22,
                    isFocused() ? ACCENT_COLOR : PANEL_BORDER_COLOR);
            // Keep native cursor, selection and input behavior, replacing only the frame.
            setBordered(false);
            graphics.pose().pushPose();
            graphics.pose().translate(4, (getHeight() - 8) / 2, 0);
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            graphics.pose().popPose();
            setBordered(true);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record LabeledField(String translationKey, int x, int baseY, int cellWidth, int min, int max,
            EditBox box) {
    }

    private record SectionArea(int x, int baseY, int width, int height, String translationKey) {
    }

    private record PositionedWidget(AbstractWidget widget, int baseY) {
    }

    private record HelpArea(int x, int baseY, int width, int height, String translationKey) {
        private boolean contains(double mouseX, double mouseY, double scrollOffset) {
            int y = baseY - (int) Math.round(scrollOffset);
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
