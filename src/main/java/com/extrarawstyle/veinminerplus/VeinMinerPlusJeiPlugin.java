package com.extrarawstyle.veinminerplus;

import java.util.ArrayList;
import java.util.List;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class VeinMinerPlusJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(VeinMinerPlus.MODID, "jei_plugin");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(XraySelectionScreen.class, screen -> {
            if (screen.screenWidth() <= 0 || screen.screenHeight() <= 0) {
                return null;
            }
            // JEI compares cached geometry with the next snapshot, including after resize.
            return new SelectionGuiProperties(XraySelectionScreen.class, screen.guiLeft(), screen.guiTop(),
                    screen.guiWidth(), screen.guiHeight(), screen.screenWidth(), screen.screenHeight());
        });
        registration.addGhostIngredientHandler(XraySelectionScreen.class,
                new IGhostIngredientHandler<XraySelectionScreen>() {
                    @Override
                    public <I> List<Target<I>> getTargetsTyped(XraySelectionScreen screen,
                            ITypedIngredient<I> ingredient, boolean doStart) {
                        if (!(ingredient.getIngredient() instanceof ItemStack stack)
                                || !XraySelectionScreen.isSelectable(stack)) {
                            return List.of();
                        }

                        List<Target<I>> targets = new ArrayList<>(36);
                        for (int index = 0; index < 36; index++) {
                            final int slot = index;
                            Rect2i area = screen.slotArea(slot);
                            targets.add(new Target<I>() {
                                @Override
                                public Rect2i getArea() {
                                    return area;
                                }

                                @Override
                                public void accept(I value) {
                                    if (value instanceof ItemStack itemStack) {
                                        screen.acceptGhostStack(itemStack, slot);
                                    }
                                }
                            });
                        }
                        return targets;
                    }

                    @Override
                    public void onComplete() {
                    }
                });
    }

    private record SelectionGuiProperties(Class<? extends Screen> screenClass, int guiLeft, int guiTop,
            int guiXSize, int guiYSize, int screenWidth, int screenHeight) implements IGuiProperties {
    }
}
