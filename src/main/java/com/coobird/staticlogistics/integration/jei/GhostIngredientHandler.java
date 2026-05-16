package com.coobird.staticlogistics.integration.jei;

import com.coobird.staticlogistics.gui.screen.BaseFilterScreen;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("rawtypes")
public class GhostIngredientHandler implements IGhostIngredientHandler<BaseFilterScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(BaseFilterScreen screen, ITypedIngredient<I> typedIngredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        Rect2i area = screen.getFilterGridArea();
        if (area == null) return targets;

        Optional<ItemStack> itemStack = typedIngredient.getItemStack();
        if (itemStack.isPresent() && !itemStack.get().isEmpty()) {
            targets.add(createItemTarget(screen, area));
            return targets;
        }

        Optional<FluidStack> fluidStack = typedIngredient.getIngredient(NeoForgeTypes.FLUID_STACK);
        if (fluidStack.isPresent() && !fluidStack.get().isEmpty()) {
            targets.add(createFluidTarget(screen, area));
        }
        return targets;
    }

    private <I> Target<I> createItemTarget(BaseFilterScreen screen, Rect2i area) {
        return new Target<>() {
            @Override
            public Rect2i getArea() {
                return area;
            }

            @Override
            public void accept(I ingredient) {
                if (ingredient instanceof ItemStack) {
                    screen.acceptGhostIngredient((ItemStack) ingredient);
                }
            }
        };
    }

    private <I> Target<I> createFluidTarget(BaseFilterScreen screen, Rect2i area) {
        return new Target<>() {
            @Override
            public Rect2i getArea() {
                return area;
            }

            @Override
            public void accept(I ingredient) {
                if (ingredient instanceof FluidStack) {
                    screen.acceptGhostIngredient((FluidStack) ingredient);
                }
            }
        };
    }

    @Override
    public void onComplete() {
    }
}