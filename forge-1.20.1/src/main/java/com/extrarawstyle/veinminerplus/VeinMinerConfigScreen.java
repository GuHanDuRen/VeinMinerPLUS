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
    private static final int WHITELIST_ROW_HEIGHT = 20;
    private static final int WHITELIST_VISIBLE_ROWS = 5;
    private static final int WHITELIST_MAX_ROWS = Config.MAX_WHITELIST_ENTRIES + 1;
    private static final int WHITELIST_CONTROL_GAP = 4;
    private static final int WHITELIST_CONTROL_WIDTH = 20;
    private static final int PANEL_COLOR = 0x78000000;
    private static final int PANEL_BORDER_COLOR = 0x60505050;
    private static final int HEADER_COLOR = 0x40303030;
    private static final int MUTED_TEXT_COLOR = 0xFFB8B8B8;

    private final NetworkHandler.ConfigSnapshotPayload initial;
    private final List<LabeledField> fields = new ArrayList<>();
    private final List<HelpArea> helpAreas = new ArrayList<>();
    private final List<SectionArea> sections = new ArrayList<>();
    private final List<PositionedWidget> contentWidgets = new ArrayList<>();
    private final List<Renderable> contentRenderables = new ArrayList<>();
    private final List<Renderable> footerRenderables = new ArrayList<>();
    private final List<String> draftValues = new ArrayList<>();
    private final List<EditBox> whitelistRows = new ArrayList<>();
    private final List<Button> whitelistRemoveButtons = new ArrayList<>();
    private int whitelistLineCount;
    private int whitelistScrollRow;
    private String whitelistDraft;
    private int whitelistBaseY;
    private int whitelistViewportBottom;
    private int whitelistX;
    private int whitelistWidth;
    private int whitelistRemoveX;
    private int whitelistLabelX;
    private int whitelistLabelWidth;
    private boolean manhattan;
    private boolean autoReduceRadius;
    private boolean consumeHunger;
    private ChainMode mode;
    private Button manhattanButton;
    private Button autoReduceRadiusButton;
    private Button consumeHungerButton;
    private Button modeButton;
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
        viewportTop = 38;
        viewportRight = panelX + panelWidth;
        viewportBottom = Math.max(viewportTop + 80, height - 34);

        int contentX = panelX + 8;
        int contentWidth = panelWidth - 16;
        boolean compactGrid = width >= 390;
        int cellGap = compactGrid ? 8 : 0;
        int cellWidth = compactGrid ? (contentWidth - cellGap) / 2 : contentWidth;
        int secondX = compactGrid ? contentX + cellWidth + cellGap : contentX;
        int top = 42;
        int headerHeight = 14;
        int rowHeight = 20;
        int sectionPadding = 6;
        int sectionGap = 5;

        int normalRows = compactGrid ? 1 : 2;
        int blastRows = compactGrid ? 3 : 5;
        int normalHeight = headerHeight + rowHeight * normalRows + sectionPadding;
        int blastHeight = headerHeight + rowHeight * blastRows + sectionPadding;
        int performanceHeight = headerHeight + rowHeight * (compactGrid ? 1 : 2) + sectionPadding;
        int playerHeight = headerHeight + rowHeight * (compactGrid ? 2 : 3) + sectionPadding
                + (WHITELIST_VISIBLE_ROWS - 1) * WHITELIST_ROW_HEIGHT;

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
                compactGrid ? blastRowTwo : blastRowOne + rowHeight * 3, cellWidth, 20).build(),
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
                compactGrid ? performanceRow : performanceRow + rowHeight, cellWidth, 20).build(),
                compactGrid ? performanceRow : performanceRow + rowHeight);
        helpAreas.add(new HelpArea(compactGrid ? secondX : contentX,
                compactGrid ? performanceRow : performanceRow + rowHeight, cellWidth, 20,
                "screen.veinminerplus.config.auto_radius.description"));

        int behaviorRow = playerY + headerHeight;
        consumeHungerButton = addContentButton(Button.builder(toggleText("screen.veinminerplus.config.consume_hunger", consumeHunger), button -> {
            consumeHunger = !consumeHunger;
            button.setMessage(toggleText("screen.veinminerplus.config.consume_hunger", consumeHunger));
        }).bounds(contentX, behaviorRow, cellWidth, 20).build(), behaviorRow);
        helpAreas.add(new HelpArea(contentX, behaviorRow, cellWidth, 20,
                "screen.veinminerplus.config.hunger.description"));
        modeButton = addContentButton(Button.builder(modeText(), button -> {
            mode = ChainMode.cycle(mode, 1);
            button.setMessage(modeText());
        }).bounds(compactGrid ? secondX : contentX,
                compactGrid ? behaviorRow : behaviorRow + rowHeight, cellWidth, 20).build(),
                compactGrid ? behaviorRow : behaviorRow + rowHeight);
        helpAreas.add(new HelpArea(compactGrid ? secondX : contentX,
                compactGrid ? behaviorRow : behaviorRow + rowHeight, cellWidth, 20,
                "screen.veinminerplus.config.mode.description"));

        whitelistBaseY = behaviorRow + (compactGrid ? rowHeight : rowHeight * 2);
        whitelistViewportBottom = whitelistBaseY + WHITELIST_VISIBLE_ROWS * WHITELIST_ROW_HEIGHT;
        whitelistLabelX = contentX + 4;
        whitelistLabelWidth = Math.min(132, Math.max(76, contentWidth / 3));
        whitelistX = contentX + whitelistLabelWidth;
        whitelistWidth = Math.max(40,
                contentWidth - whitelistLabelWidth - WHITELIST_CONTROL_WIDTH * 2 - WHITELIST_CONTROL_GAP * 2);
        whitelistRemoveX = whitelistX + whitelistWidth + WHITELIST_CONTROL_GAP;
        initializeWhitelistRows();
        int whitelistAddX = whitelistRemoveX + WHITELIST_CONTROL_WIDTH + WHITELIST_CONTROL_GAP;
        addContentButton(Button.builder(Component.literal("+"), button -> beginWhitelistSelection())
                .bounds(whitelistAddX, whitelistBaseY, WHITELIST_CONTROL_WIDTH, WHITELIST_ROW_HEIGHT).build(),
                whitelistBaseY);
        helpAreas.add(new HelpArea(contentX, whitelistBaseY, contentWidth,
                WHITELIST_VISIBLE_ROWS * WHITELIST_ROW_HEIGHT,
                "screen.veinminerplus.config.whitelist.description"));

        int footerGap = 4;
        int actionWidth = Math.min(104, Math.max(64, (width - 24 - footerGap * 2) / 3));
        int footerTotalWidth = actionWidth * 3 + footerGap * 2;
        footerLeft = (width - footerTotalWidth) / 2;
        footerRight = footerLeft + footerTotalWidth;
        footerTop = height - 26;
        Button resetButton = addRenderableWidget(
                Button.builder(Component.translatable("screen.veinminerplus.config.reset_defaults"), button -> resetDefaults())
                        .bounds(footerLeft, footerTop, actionWidth, 20).build());
        Button doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> save())
                .bounds(footerLeft + actionWidth + footerGap, footerTop, actionWidth, 20).build());
        Button cancelButton = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(footerLeft + (actionWidth + footerGap) * 2, footerTop, actionWidth, 20).build());
        footerRenderables.add(resetButton);
        footerRenderables.add(doneButton);
        footerRenderables.add(cancelButton);
        draftValues.clear();
        layoutContentWidgets();
    }

    private void initializeWhitelistRows() {
        String source = whitelistDraft != null ? whitelistDraft : initial.blockWhitelist();
        if (source == null || source.trim().isEmpty()) {
            source = Config.whitelistText();
        }

        List<String> lines = new ArrayList<>();
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

        for (EditBox row : whitelistRows) {
            removeWidget(row);
        }
        for (Button button : whitelistRemoveButtons) {
            removeWidget(button);
        }
        whitelistRows.clear();
        whitelistRemoveButtons.clear();
        for (int index = 0; index < WHITELIST_MAX_ROWS; index++) {
            EditBox row = new EditBox(font, whitelistX, whitelistBaseY, whitelistWidth, WHITELIST_ROW_HEIGHT,
                    Component.translatable("screen.veinminerplus.config.whitelist"));
            row.setMaxLength(Config.MAX_WHITELIST_ENTRY_LENGTH);
            row.setValue(index < whitelistLineCount ? lines.get(index) : "");
            addRenderableWidget(row);
            contentRenderables.add(row);
            whitelistRows.add(row);

            int rowIndex = index;
            Button removeButton = Button.builder(Component.literal("-"), button -> removeWhitelistEntry(rowIndex))
                    .bounds(whitelistRemoveX, whitelistBaseY, WHITELIST_CONTROL_WIDTH, WHITELIST_ROW_HEIGHT)
                    .build();
            addRenderableWidget(removeButton);
            contentRenderables.add(removeButton);
            whitelistRemoveButtons.add(removeButton);
        }
        whitelistScrollRow = Math.min(whitelistScrollRow, maxWhitelistScroll());
        layoutWhitelistRows();
    }

    private int maxWhitelistScroll() {
        return Math.max(0, whitelistLineCount - WHITELIST_VISIBLE_ROWS);
    }

    private void captureWhitelistDraft() {
        if (whitelistRows.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < whitelistLineCount; index++) {
            lines.add(whitelistRows.get(index).getValue());
        }
        whitelistDraft = joinWhitelistLines(lines);
    }

    private String whitelistText() {
        captureWhitelistDraft();
        return whitelistDraft == null ? "" : whitelistDraft;
    }

    private void setWhitelistLines(List<String> values) {
        List<String> lines = new ArrayList<>();
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
            whitelistRows.get(index).setValue(index < whitelistLineCount ? lines.get(index) : "");
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

    void captureDraftValues() {
        if (!fields.isEmpty()) {
            draftValues.clear();
            for (LabeledField field : fields) {
                draftValues.add(field.box().getValue());
            }
        }
        captureWhitelistDraft();
    }

    private void beginWhitelistSelection() {
        VeinMinerPlusClient.beginWhitelistSelection(this);
    }

    void addAimedWhitelistEntry(BlockState state) {
        String current = whitelistText();
        List<String> entries = new ArrayList<>(Config.parseWhitelistText(current == null ? "" : current));
        if (entries.isEmpty()) {
            entries.add("*ore");
        }
        int originalSize = entries.size();
        state.getTags().filter(tag -> tag.location().getPath().contains("ore"))
                .forEach(tag -> entries.add("#" + tag.location()));
        if (entries.size() == originalSize) {
            var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (id != null) {
                entries.add(id.toString());
            }
        }
        setWhitelistLines(Config.normalizeWhitelist(entries));
        whitelistScrollRow = maxWhitelistScroll();
        layoutWhitelistRows();
    }

    private void removeWhitelistEntry(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= whitelistLineCount) {
            return;
        }

        List<String> lines = new ArrayList<>();
        for (int index = 0; index < whitelistLineCount; index++) {
            lines.add(whitelistRows.get(index).getValue());
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
                Config.DEFAULT_BLAST_LOW_TPS_THRESHOLD
        };
        for (int index = 0; index < fields.size() && index < defaults.length; index++) {
            fields.get(index).box().setValue(Integer.toString(defaults[index]));
        }
        manhattan = Config.DEFAULT_BLAST_MANHATTAN;
        autoReduceRadius = Config.DEFAULT_BLAST_AUTO_REDUCE_RADIUS;
        consumeHunger = Config.DEFAULT_CONSUME_HUNGER;
        mode = ChainMode.fromOrdinal(Config.DEFAULT_MODE_ORDINAL);
        setWhitelistLines(Config.DEFAULT_BLOCK_WHITELIST);
        manhattanButton.setMessage(toggleText("screen.veinminerplus.config.manhattan", manhattan));
        autoReduceRadiusButton.setMessage(toggleText("screen.veinminerplus.config.auto_radius", autoReduceRadius));
        consumeHungerButton.setMessage(toggleText("screen.veinminerplus.config.consume_hunger", consumeHunger));
        modeButton.setMessage(modeText());
    }

    private void addField(String translationKey, int value, int min, int max, int x, int baseY, int cellWidth) {
        int boxWidth = Math.min(84, Math.max(64, cellWidth / 3));
        int boxX = x + cellWidth - boxWidth - 4;
        EditBox box = new EditBox(font, boxX, baseY, boxWidth, 20, Component.translatable(translationKey));
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
        String whitelist = String.join("\n", Config.parseWhitelistText(whitelistText()));
        NetworkHandler.sendConfigUpdate(new NetworkHandler.ConfigUpdatePayload(normalBlocks, normalPerTick,
                blastBlocks, blastPerTick, distance, scanPerTick, lowTps, manhattan, autoReduceRadius,
                consumeHunger, mode.id(), whitelist));
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
        layoutWhitelistRows();
    }

    private void layoutWhitelistRows() {
        int outerOffset = (int) Math.round(scrollOffset);
        int innerOffset = whitelistScrollRow * WHITELIST_ROW_HEIGHT;
        for (int index = 0; index < whitelistRows.size(); index++) {
            EditBox row = whitelistRows.get(index);
            Button removeButton = whitelistRemoveButtons.get(index);
            int y = whitelistBaseY + index * WHITELIST_ROW_HEIGHT - innerOffset - outerOffset;
            row.setX(whitelistX);
            row.setY(y);
            removeButton.setX(whitelistRemoveX);
            removeButton.setY(y);
            boolean visible = index < whitelistLineCount
                    && y + WHITELIST_ROW_HEIGHT > viewportTop
                    && y < viewportBottom
                    && y + WHITELIST_ROW_HEIGHT > whitelistBaseY - outerOffset
                    && y < whitelistViewportBottom - outerOffset;
            row.setVisible(visible);
            removeButton.visible = visible;
            removeButton.active = visible;
        }
    }

    private boolean insideWhitelistViewport(double mouseX, double mouseY) {
        int outerOffset = (int) Math.round(scrollOffset);
        int top = whitelistBaseY - outerOffset;
        int bottom = whitelistViewportBottom - outerOffset;
        return mouseX >= whitelistX && mouseX < whitelistRemoveX + WHITELIST_CONTROL_WIDTH
                && mouseY >= top && mouseY < bottom
                && mouseY >= viewportTop && mouseY < viewportBottom;
    }

    private boolean scrollWhitelist(double delta) {
        if (delta == 0.0D || maxWhitelistScroll() <= 0) {
            return false;
        }
        int direction = delta > 0.0D ? -1 : 1;
        int next = Mth.clamp(whitelistScrollRow + direction, 0, maxWhitelistScroll());
        if (next == whitelistScrollRow) {
            return false;
        }
        captureWhitelistDraft();
        whitelistScrollRow = next;
        layoutWhitelistRows();
        return true;
    }

    private boolean scrollContent(double mouseX, double mouseY, double delta) {
        if (insideWhitelistViewport(mouseX, mouseY) && scrollWhitelist(delta)) {
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
        if (!insideContentViewport(mouseX, mouseY) && !insideFooter(mouseX, mouseY)) {
            return false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
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
        drawWhitelistLabel(graphics);
        drawWhitelistScrollbar(graphics);
        graphics.disableScissor();

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
    }

    private void drawPanels(GuiGraphics graphics) {
        if (sections.isEmpty()) {
            return;
        }

        int offset = (int) Math.round(scrollOffset);
        int top = sections.get(0).baseY() - offset;
        SectionArea last = sections.get(sections.size() - 1);
        int bottom = last.baseY() + last.height() - offset;
        int right = panelX + panelWidth;
        graphics.fill(panelX, top, right, bottom, PANEL_COLOR);
        graphics.fill(panelX, top, right, top + 1, PANEL_BORDER_COLOR);
        graphics.fill(panelX, bottom - 1, right, bottom, PANEL_BORDER_COLOR);
        graphics.fill(panelX, top, panelX + 1, bottom, PANEL_BORDER_COLOR);
        graphics.fill(right - 1, top, right, bottom, PANEL_BORDER_COLOR);
        for (int index = 0; index < sections.size(); index++) {
            SectionArea section = sections.get(index);
            int y = section.baseY() - offset;
            graphics.fill(panelX + 1, y, right - 1, y + 14, HEADER_COLOR);
            if (index > 0) {
                graphics.fill(panelX + 8, y - 3, right - 8, y - 2, PANEL_BORDER_COLOR);
            }
        }
    }

    private void drawSectionTitles(GuiGraphics graphics) {
        int offset = (int) Math.round(scrollOffset);
        for (SectionArea section : sections) {
            graphics.drawString(font, Component.translatable(section.translationKey()), section.x() + 8,
                    section.baseY() - offset + 3, 0xFFFFFFFF, false);
        }
    }

    private void drawFieldLabel(GuiGraphics graphics, LabeledField field) {
        int offset = (int) Math.round(scrollOffset);
        int y = field.baseY() - offset;
        int labelWidth = Math.max(40, field.cellWidth() - field.box().getWidth() - 12);
        String label = Component.translatable(field.translationKey()).getString();
        graphics.drawString(font, font.plainSubstrByWidth(label, labelWidth), field.x() + 4, y + 6,
                0xFFFFFFFF, false);
    }

    private void drawWhitelistLabel(GuiGraphics graphics) {
        if (whitelistRows.isEmpty()) {
            return;
        }
        int offset = (int) Math.round(scrollOffset);
        int y = whitelistBaseY - offset;
        String label = Component.translatable("screen.veinminerplus.config.whitelist").getString();
        graphics.drawString(font, font.plainSubstrByWidth(label, whitelistLabelWidth), whitelistLabelX, y + 6,
                0xFFFFFFFF, false);
    }

    private void drawWhitelistScrollbar(GuiGraphics graphics) {
        int max = maxWhitelistScroll();
        if (max <= 0) {
            return;
        }
        int outerOffset = (int) Math.round(scrollOffset);
        int top = Math.max(viewportTop, whitelistBaseY - outerOffset);
        int bottom = Math.min(viewportBottom, whitelistViewportBottom - outerOffset);
        if (bottom <= top) {
            return;
        }
        int trackX = whitelistX + whitelistWidth + 2;
        int trackHeight = bottom - top;
        int thumbHeight = Math.max(12, trackHeight * WHITELIST_VISIBLE_ROWS / whitelistLineCount);
        int travel = Math.max(0, trackHeight - thumbHeight);
        int thumbY = top + (int) Math.round(travel * (double) whitelistScrollRow / max);
        graphics.fill(trackX, top, trackX + 2, bottom, 0x50505050);
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xC0D0D0D0);
    }

    private void drawHeader(GuiGraphics graphics) {
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.veinminerplus.config.subtitle"),
                width / 2, 24, MUTED_TEXT_COLOR);
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
        graphics.fill(trackX, viewportTop, trackX + 3, viewportBottom, 0x50505050);
        graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, 0xC0D0D0D0);
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
