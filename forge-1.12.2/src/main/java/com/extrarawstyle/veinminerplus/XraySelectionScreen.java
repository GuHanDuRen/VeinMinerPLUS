package com.extrarawstyle.veinminerplus;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.RayTraceResult;

/** Compact vanilla-style nine-slot ore selector for Forge 1.12.2. */
public final class XraySelectionScreen extends GuiScreen {
    private static final int SLOT_SIZE = 18;
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 68;
    private static final int BUTTON_TOP = 42;
    private static final int BUTTON_WIDTH = 78;
    private static final ResourceLocation CHEST_TEXTURE = new ResourceLocation(
            "textures/gui/container/generic_54.png");

    private final List<XrayClientState.Selection> selected = new ArrayList<XrayClientState.Selection>();
    private int left;
    private int top;
    private XrayClientState.Selection dragged;
    private int draggedSlot = -1;

    public XraySelectionScreen() {
        selected.addAll(XrayClientState.selection());
    }

    @Override
    public void initGui() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        buttonList.clear();
        buttonList.add(new GuiButton(1, left + 7, top + BUTTON_TOP, BUTTON_WIDTH, 20,
                I18n.format("screen.veinminerplus.xray.clear")));
        buttonList.add(new GuiButton(2, left + PANEL_WIDTH - 7 - BUTTON_WIDTH, top + BUTTON_TOP, BUTTON_WIDTH,
                20, I18n.format("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1) {
            for (int index = 0; index < selected.size(); index++) {
                XrayClientState.Selection value = selected.get(index);
                selected.set(index, new XrayClientState.Selection(ItemStack.EMPTY, value.color()));
            }
        } else if (button.id == 2) {
            onGuiClosed();
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void onGuiClosed() {
        XrayClientState.setSelection(selected);
    }

    int slotLeft(int index) {
        return left + 7 + (index % 9) * SLOT_SIZE;
    }

    int slotTop(int index) {
        return top + 17;
    }

    boolean isInsideSlot(int index, int mouseX, int mouseY) {
        return mouseX >= slotLeft(index) && mouseX < slotLeft(index) + SLOT_SIZE
                && mouseY >= slotTop(index) && mouseY < slotTop(index) + SLOT_SIZE;
    }

    void acceptGhostStack(ItemStack stack, int index) {
        if (index >= 0 && index < selected.size() && XrayClientState.canAccept(stack)) {
            selected.set(index, new XrayClientState.Selection(stack.copy(), selected.get(index).color()));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        for (int index = 0; index < XrayClientState.SLOT_COUNT; index++) {
            if (!isInsideSlot(index, mouseX, mouseY)) {
                continue;
            }
            XrayClientState.Selection value = selected.get(index);
            if (mouseButton == 2) {
                final int slot = index;
                mc.displayGuiScreen(new XrayColorScreen(this, value.color(), color -> selected.set(slot,
                        new XrayClientState.Selection(selected.get(slot).stack(), color))));
            } else if (mouseButton == 1) {
                selected.set(index, new XrayClientState.Selection(ItemStack.EMPTY, value.color()));
            } else if (mouseButton == 0 && !value.stack().isEmpty()) {
                dragged = value;
                draggedSlot = index;
            } else if (mouseButton == 0) {
                ItemStack aimed = aimedOreStack();
                if (XrayClientState.canAccept(aimed)) {
                    selected.set(index, new XrayClientState.Selection(aimed, value.color()));
                }
            }
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private ItemStack aimedOreStack() {
        if (mc == null || mc.world == null || mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK) {
            return ItemStack.EMPTY;
        }
        IBlockState state = mc.world.getBlockState(mc.objectMouseOver.getBlockPos());
        if (state == null || state.getBlock() == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(state.getBlock(), 1, state.getBlock().getMetaFromState(state));
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && dragged != null) {
            for (int index = 0; index < XrayClientState.SLOT_COUNT; index++) {
                if (isInsideSlot(index, mouseX, mouseY)) {
                    XrayClientState.Selection target = selected.get(index);
                    int sourceColor = selected.get(draggedSlot).color();
                    selected.set(draggedSlot, new XrayClientState.Selection(target.stack(), sourceColor));
                    selected.set(index, new XrayClientState.Selection(dragged.stack(), target.color()));
                    dragged = null;
                    return;
                }
            }
            dragged = null;
            return;
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(CHEST_TEXTURE);
        drawTexturedModalRect(left, top, 0, 0, PANEL_WIDTH, 35);
        drawTexturedModalRect(left, top + 35, 0, 126, PANEL_WIDTH, 13);
        drawTexturedModalRect(left, top + 48, 0, 126, PANEL_WIDTH, 13);
        drawTexturedModalRect(left, top + 61, 0, 126, PANEL_WIDTH, 4);
        drawCenteredString(fontRenderer, I18n.format("screen.veinminerplus.xray.title"), width / 2, top + 6,
                0x404040);
        RenderItem renderItem = mc.getRenderItem();
        for (int index = 0; index < XrayClientState.SLOT_COUNT; index++) {
            XrayClientState.Selection value = selected.get(index);
            if (!value.stack().isEmpty() && (dragged == null || draggedSlot != index)) {
                renderItem.renderItemAndEffectIntoGUI(value.stack(), slotLeft(index) + 1, slotTop(index) + 1);
            }
            Gui.drawRect(slotLeft(index) + 1, slotTop(index) + 16, slotLeft(index) + 17, slotTop(index) + 18,
                    0xFF000000 | value.color());
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (dragged != null) {
            renderItem.renderItemAndEffectIntoGUI(dragged.stack(), mouseX - 8, mouseY - 8);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
