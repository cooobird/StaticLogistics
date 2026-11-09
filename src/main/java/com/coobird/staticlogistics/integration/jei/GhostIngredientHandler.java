package com.coobird.staticlogistics.integration.jei;

import com.coobird.staticlogistics.client.gui.screen.BaseFilterScreen;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("rawtypes")
public class GhostIngredientHandler implements IGhostIngredientHandler<BaseFilterScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(BaseFilterScreen screen, ITypedIngredient<I> typedIngredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        Optional<ItemStack> itemStack = typedIngredient.getItemStack();
        if (itemStack.isPresent() && !itemStack.get().isEmpty()) {
            for (int slot = 0; slot < 36; slot++) targets.add(createItemTarget(screen, slot));
            return targets;
        }

        Optional<FluidStack> fluidStack = typedIngredient.getIngredient(ForgeTypes.FLUID_STACK);
        if (fluidStack.isPresent() && !fluidStack.get().isEmpty()) {
            for (int slot = 0; slot < 36; slot++) targets.add(createFluidTarget(screen, slot));
        }
        return targets;
    }

    private <I> Target<I> createItemTarget(BaseFilterScreen screen, int slot) {
        return new Target<>() {
            @Override
            public Rect2i getArea() {
                return screen.getFilterSlotArea(slot);
            }

            @Override
            public void accept(I ingredient) {
                if (ingredient instanceof ItemStack) {
                    screen.acceptGhostIngredient(slot, (ItemStack) ingredient);
                }
            }
        };
    }

    private <I> Target<I> createFluidTarget(BaseFilterScreen screen, int slot) {
        return new Target<>() {
            @Override
            public Rect2i getArea() {
                return screen.getFilterSlotArea(slot);
            }

            @Override
            public void accept(I ingredient) {
                if (ingredient instanceof FluidStack) {
                    screen.acceptGhostIngredient(slot, (FluidStack) ingredient);
                }
            }
        };
    }

    @Override
    public void onComplete() {
    }
}
