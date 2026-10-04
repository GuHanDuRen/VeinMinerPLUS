package com.extrarawstyle.veinminerplus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.ChatFormatting;

import net.minecraft.client.renderer.Rect2i;

public final class XraySelectionScreen extends Screen {
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_COLUMNS = 9;
    private static final int SLOT_COUNT = XrayClientState.SLOT_COUNT;
    private static final int PANEL_WIDTH = 176;
    private static final int CHEST_AREA_HEIGHT = 35;
    private static final int PANEL_HEIGHT = 68;
    private static final int BUTTON_TOP = 42;
    private static final int BUTTON_WIDTH = 78;
    private static final ResourceLocation GENERIC_CHEST_TEXTURE = ResourceLocation
            .withDefaultNamespace("textures/gui/container/generic_54.png");

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

    int guiLeft() {
        return left;
    }

    int guiTop() {
        return top;
    }

    int guiWidth() {
        return PANEL_WIDTH;
    }

    int guiHeight() {
        return PANEL_HEIGHT;
    }

    int screenWidth() {
        return width;
    }

    int screenHeight() {
        return height;
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

    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the world sharp behind the selector; vanilla's default screen
        // background applies a blur shader.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0x50000000);
        graphics.blit(GENERIC_CHEST_TEXTURE, left, top, 0.0F, 0.0F, PANEL_WIDTH, CHEST_AREA_HEIGHT, 256, 256);
        graphics.blit(GENERIC_CHEST_TEXTURE, left, top + CHEST_AREA_HEIGHT, 0.0F, 126.0F, PANEL_WIDTH, 13, 256, 256);
        graphics.blit(GENERIC_CHEST_TEXTURE, left, top + 48, 0.0F, 126.0F, PANEL_WIDTH, 13, 256, 256);
        graphics.blit(GENERIC_CHEST_TEXTURE, left, top + 61, 0.0F, 126.0F, PANEL_WIDTH, 4, 256, 256);
        graphics.blit(GENERIC_CHEST_TEXTURE, left, top + 65, 0.0F, 219.0F, PANEL_WIDTH, 3, 256, 256);
        graphics.drawString(font, title, left + 8, top + 6, 0xFF404040, false);

        for (int index = 0; index < SLOT_COUNT; index++) {
            Rect2i area = slotArea(index);
            XrayClientState.Selection selection = selected.get(index);
            ItemStack stack = selection.stack();
            if (!stack.isEmpty() && (dragged == null || draggedSlot != index)) {
                graphics.renderItem(stack, area.getX() + 1, area.getY() + 1);
            }
            graphics.fill(area.getX() + 1, area.getY() + 16, area.getX() + 17, area.getY() + 18,
                    0xFF000000 | selection.color());
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (dragged != null) {
            graphics.renderItem(dragged.stack(), mouseX - 8, mouseY - 8);
            return;
        }
        for (int index = 0; index < SLOT_COUNT; index++) {
            XrayClientState.Selection selection = selected.get(index);
            Rect2i area = slotArea(index);
            if (area.contains(mouseX, mouseY)) {
                List<Component> tooltip = new ArrayList<>();
                if (!selection.stack().isEmpty()) {
                    tooltip.addAll(Screen.getTooltipFromItem(minecraft, selection.stack()));
                }
                tooltip.add(Component.translatable("screen.veinminerplus.xray.color_hint",
                        String.format(Locale.ROOT, "#%06X", selection.color())).withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("screen.veinminerplus.xray.remove_hint").withStyle(ChatFormatting.GRAY));
                graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
                return;
            }
        }
    }
}
