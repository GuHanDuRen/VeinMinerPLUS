package com.extrarawstyle.veinminerplus;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import net.minecraft.client.renderer.Rect2i;

public final class XraySelectionScreen extends Screen {
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_COLUMNS = 9;
    private static final int SLOT_ROWS = 4;
    private static final int SLOT_COUNT = SLOT_COLUMNS * SLOT_ROWS;
    private static final int PANEL_WIDTH = 176;
    private static final int CHEST_AREA_HEIGHT = 90;
    private static final int PANEL_HEIGHT = 122;
    private static final int BUTTON_TOP = 97;
    private static final int BUTTON_WIDTH = 80;
    private static final ResourceLocation GENERIC_CHEST_TEXTURE = ResourceLocation
            .withDefaultNamespace("textures/gui/container/generic_54.png");

    private final List<ItemStack> selected = new ArrayList<>();
    private int left;
    private int top;
    private ItemStack dragged = ItemStack.EMPTY;

    public XraySelectionScreen() {
        super(Component.translatable("screen.veinminerplus.xray.title"));
        selected.addAll(XrayClientState.selectedStacks());
        while (selected.size() < SLOT_COUNT) {
            selected.add(ItemStack.EMPTY);
        }
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.veinminerplus.xray.clear"), button -> clearSelected())
                .bounds(left + 4, top + BUTTON_TOP, BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> finish())
                .bounds(left + PANEL_WIDTH - 4 - BUTTON_WIDTH, top + BUTTON_TOP, BUTTON_WIDTH, 20).build());
    }

    private void clearSelected() {
        for (int i = 0; i < selected.size(); i++) {
            selected.set(i, ItemStack.EMPTY);
        }
    }

    private void finish() {
        List<ItemStack> values = selected.stream().filter(stack -> !stack.isEmpty()).toList();
        XrayClientState.setSelectedStacks(values);
        onClose();
    }

    @Override
    public void onClose() {
        List<ItemStack> values = selected.stream().filter(stack -> !stack.isEmpty()).toList();
        XrayClientState.setSelectedStacks(values);
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    public Rect2i slotArea(int index) {
        int x = left + 7 + (index % SLOT_COLUMNS) * SLOT_SIZE;
        int y = top + 17 + (index / SLOT_COLUMNS) * SLOT_SIZE;
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
        selected.set(index, stack.copyWithCount(1));
    }

    static boolean isSelectable(ItemStack stack) {
        return XrayClientState.canAccept(stack);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int index = 0; index < SLOT_COUNT; index++) {
                if (slotArea(index).contains((int) mouseX, (int) mouseY)) {
                    ItemStack stack = selected.get(index);
                    if (!stack.isEmpty()) {
                        dragged = stack.copy();
                        selected.set(index, ItemStack.EMPTY);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && !dragged.isEmpty()) {
            for (int index = 0; index < SLOT_COUNT; index++) {
                if (slotArea(index).contains((int) mouseX, (int) mouseY)) {
                    acceptGhostStack(dragged, index);
                    dragged = ItemStack.EMPTY;
                    return true;
                }
            }
            dragged = ItemStack.EMPTY;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the world sharp behind the selector; vanilla's default screen
        // background applies a blur shader.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0x50000000);
        graphics.blit(GENERIC_CHEST_TEXTURE, left, top, 0.0F, 0.0F, PANEL_WIDTH, CHEST_AREA_HEIGHT, 176, 222);
        graphics.fill(left + 1, top + CHEST_AREA_HEIGHT, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1,
                0xFF8B8B8B);
        graphics.fill(left, top + CHEST_AREA_HEIGHT, left + 1, top + PANEL_HEIGHT, 0xFF555555);
        graphics.fill(left + PANEL_WIDTH - 1, top + CHEST_AREA_HEIGHT, left + PANEL_WIDTH, top + PANEL_HEIGHT,
                0xFF555555);
        graphics.fill(left, top + PANEL_HEIGHT - 1, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF373737);
        graphics.drawString(font, title, left + 8, top + 6, 0xFF404040, false);

        for (int index = 0; index < SLOT_COUNT; index++) {
            Rect2i area = slotArea(index);
            ItemStack stack = selected.get(index);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, area.getX() + 1, area.getY() + 1);
                graphics.renderItemDecorations(font, stack, area.getX() + 1, area.getY() + 1);
            }
        }

        if (!dragged.isEmpty()) {
            graphics.renderItem(dragged, mouseX - 8, mouseY - 8);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        for (int index = 0; index < SLOT_COUNT; index++) {
            ItemStack stack = selected.get(index);
            Rect2i area = slotArea(index);
            if (!stack.isEmpty() && area.contains(mouseX, mouseY)) {
                graphics.renderTooltip(font, stack, mouseX, mouseY);
                break;
            }
        }
    }
}
