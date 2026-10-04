package com.extrarawstyle.veinminerplus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class XrayColorScreen extends Screen {
    private static final int PANEL_WIDTH = 216;
    private static final int PANEL_HEIGHT = 192;
    private static final int[] PRESETS = {0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0xAA55FF};

    private final XraySelectionScreen parent;
    private final IntConsumer save;
    private final List<ColorSlider> sliders = new ArrayList<>();
    private int color;
    private int left;
    private int top;
    private EditBox hex;
    private Button done;
    private boolean syncing;

    XrayColorScreen(XraySelectionScreen parent, int color, IntConsumer save) {
        super(Component.translatable("screen.veinminerplus.xray.color_title"));
        this.parent = parent;
        this.color = color;
        this.save = save;
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        sliders.clear();
        for (int channel = 0; channel < 3; channel++) {
            ColorSlider slider = new ColorSlider(left + 10, top + 28 + channel * 24, channel);
            sliders.add(addRenderableWidget(slider));
        }
        hex = new EditBox(font, left + 58, top + 104, 148, 20,
                Component.translatable("screen.veinminerplus.xray.hex"));
        hex.setMaxLength(7);
        hex.setFilter(value -> value.matches("#?[0-9a-fA-F]{0,6}"));
        hex.setResponder(this::readHex);
        addRenderableWidget(hex);
        for (int index = 0; index < PRESETS.length; index++) {
            int preset = PRESETS[index];
            addRenderableWidget(Button.builder(Component.literal("■").withStyle(style -> style.withColor(preset)),
                    button -> setColor(preset)).bounds(left + 10 + index * 33, top + 132, 31, 20)
                    .tooltip(Tooltip.create(Component.literal(String.format(Locale.ROOT, "#%06X", preset)))).build());
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + 10, top + 162, 94, 20).build());
        done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> finish())
                .bounds(left + 112, top + 162, 94, 20).build());
        setColor(color);
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && done.active) {
            finish();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep this editor consistent with the unblurred selection screen.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x80000000);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF000000);
        graphics.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, 0xFF555555);
        graphics.fill(left + 1, top + 1, left + PANEL_WIDTH - 3, top + PANEL_HEIGHT - 3, 0xFFFFFFFF);
        graphics.fill(left + 3, top + 3, left + PANEL_WIDTH - 3, top + PANEL_HEIGHT - 3, 0xFFC6C6C6);
        graphics.drawString(font, title, left + 10, top + 10, 0xFF404040, false);
        graphics.drawString(font, Component.translatable("screen.veinminerplus.xray.hex"), left + 10, top + 110,
                0xFF404040, false);
        graphics.fill(left + 154, top + 28, left + 206, top + 96, 0xFF202020);
        int preview = 0xFF000000 | color;
        graphics.fill(left + 164, top + 45, left + 196, top + 47, preview);
        graphics.fill(left + 164, top + 75, left + 196, top + 77, preview);
        graphics.fill(left + 164, top + 45, left + 166, top + 77, preview);
        graphics.fill(left + 194, top + 45, left + 196, top + 77, preview);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private final class ColorSlider extends AbstractSliderButton {
        private final int shift;
        private final String label;

        ColorSlider(int x, int y, int channel) {
            super(x, y, 136, 20, Component.empty(), 0.0);
            shift = (2 - channel) * 8;
            label = new String[] {"R", "G", "B"}[channel];
            syncColor();
        }

        void syncColor() {
            value = ((color >> shift) & 0xFF) / 255.0;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ": " + Math.round(value * 255.0)));
        }

        @Override
        protected void applyValue() {
            setColor((color & ~(0xFF << shift)) | ((int) Math.round(value * 255.0) << shift));
        }
    }
}
