package com.coobird.staticlogistics.client.key;

import com.coobird.staticlogistics.client.gui.screen.BaseFilterScreen;
import com.coobird.staticlogistics.client.gui.screen.BlueprintGroupScreen;
import com.coobird.staticlogistics.client.gui.screen.LinkConfiguratorScreen;
import com.coobird.staticlogistics.content.item.BlueprintItem;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;

import java.util.function.BooleanSupplier;

/**
 * 按功能场景隔离键位冲突，避免默认键相同但永远不会同时生效的映射互相冲突。
 */
final class SLKeyConflictContexts {
    static final IKeyConflictContext BLUEPRINT_IN_GAME = inGame(Domain.BLUEPRINT, () ->
        mainHandItem() instanceof BlueprintItem);
    static final IKeyConflictContext LINK_CONFIGURATOR_IN_GAME = inGame(Domain.LINK_CONFIGURATOR, () ->
        mainHandItem() instanceof LinkConfiguratorItem || offhandItem() instanceof LinkConfiguratorItem);

    static final IKeyConflictContext FILTER_GUI = inGui(Domain.FILTER, () ->
        Minecraft.getInstance().screen instanceof BaseFilterScreen<?>);
    static final IKeyConflictContext LINK_ENDPOINT_GUI = inGui(Domain.LINK_ENDPOINT, () ->
        Minecraft.getInstance().screen instanceof LinkConfiguratorScreen screen
            && screen.hasNodeTarget());
    static final IKeyConflictContext NETWORK_PREVIEW_GUI = inGui(Domain.NETWORK_PREVIEW, () ->
        Minecraft.getInstance().screen instanceof LinkConfiguratorScreen);
    static final IKeyConflictContext GROUP_SCREEN = inGui(Domain.GROUP, () ->
        Minecraft.getInstance().screen instanceof LinkConfiguratorScreen
            || Minecraft.getInstance().screen instanceof BlueprintGroupScreen);

    private SLKeyConflictContexts() {
    }

    private static IKeyConflictContext inGame(Domain domain, BooleanSupplier active) {
        return new ScopedContext(domain, KeyConflictContext.IN_GAME, active);
    }

    private static IKeyConflictContext inGui(Domain domain, BooleanSupplier active) {
        return new ScopedContext(domain, KeyConflictContext.GUI, active);
    }

    private static Item mainHandItem() {
        var player = Minecraft.getInstance().player;
        return player == null ? Items.AIR : player.getMainHandItem().getItem();
    }

    private static Item offhandItem() {
        var player = Minecraft.getInstance().player;
        return player == null ? Items.AIR : player.getOffhandItem().getItem();
    }

    private enum Domain {
        BLUEPRINT,
        LINK_CONFIGURATOR,
        FILTER,
        LINK_ENDPOINT,
        NETWORK_PREVIEW,
        GROUP
    }

    private record ScopedContext(
        Domain domain,
        KeyConflictContext baseContext,
        BooleanSupplier active
    ) implements IKeyConflictContext {
        @Override
        public boolean isActive() {
            return baseContext.isActive() && active.getAsBoolean();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            if (other instanceof ScopedContext scoped) return domain == scoped.domain;
            return other == baseContext || other == KeyConflictContext.UNIVERSAL;
        }
    }
}
