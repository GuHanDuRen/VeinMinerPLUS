package com.extrarawstyle.veinminerplus;

import java.util.Locale;
import java.util.function.IntConsumer;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

/** Simple preset/HEX editor for one x-ray outline color. */
final class XrayColorScreen extends GuiScreen {
    private static final int[] PRESETS = { 0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0xAA55FF };
    private final XraySelectionScreen parent;
    private final IntConsumer save;
    private int color;
    private GuiTextField hex;

    XrayColorScreen(XraySelectionScreen parent, int color, IntConsumer save) {
        this.parent = parent;
        this.color = color & 0xFFFFFF;
        this.save = save;
    }

    @Override
    public void initGui() {
        int left = (width - 216) / 2;
        int top = (height - 150) / 2;
        buttonList.clear();
        for (int index = 0; index < PRESETS.length; index++) {
            buttonList.add(new GuiButton(10 + index, left + 10 + index * 33, top + 78, 31, 20,
                    String.format(Locale.ROOT, "#%06X", PRESETS[index])));
        }
        buttonList.add(new GuiButton(1, left + 10, top + 112, 94, 20, I18n.format("gui.cancel")));
        buttonList.add(new GuiButton(2, left + 112, top + 112, 94, 20, I18n.format("gui.done")));
        hex = new GuiTextField(3, fontRenderer, left + 58, top + 42, 148, 20);
        hex.setMaxStringLength(7);
        hex.setText(String.format(Locale.ROOT, "#%06X", color));
        hex.setValidator(value -> value.matches("#?[0-9a-fA-F]{0,6}"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id >= 10 && button.id < 10 + PRESETS.length) {
            color = PRESETS[button.id - 10];
            hex.setText(String.format(Locale.ROOT, "#%06X", color));
        } else if (button.id == 2) {
            if (readHex()) {
                save.accept(color);
                mc.displayGuiScreen(parent);
            }
        } else if (button.id == 1) {
            mc.displayGuiScreen(parent);
        }
    }

    private boolean readHex() {
        String value = hex.getText();
        if (!value.matches("#?[0-9a-fA-F]{6}")) {
            return false;
        }
        color = Integer.parseInt(value.startsWith("#") ? value.substring(1) : value, 16);
        return true;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        hex.textboxKeyTyped(typedChar, keyCode);
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        hex.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = (width - 216) / 2;
        int top = (height - 150) / 2;
        drawRect(left, top, left + 216, top + 150, 0xFFC6C6C6);
        drawCenteredString(fontRenderer, I18n.format("screen.veinminerplus.xray.color_title"), width / 2, top + 10,
                0x404040);
        drawString(fontRenderer, I18n.format("screen.veinminerplus.xray.hex"), left + 10, top + 48, 0x404040);
        drawRect(left + 164, top + 22, left + 196, top + 54, 0xFF000000 | color);
        hex.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
