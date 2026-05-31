package com.coobird.staticlogistics.item;

import com.coobird.staticlogistics.api.BlueprintData;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.client.key.SLKeyMappings;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.gui.screen.BlueprintGroupScreen;
import com.coobird.staticlogistics.registry.SLDataComponents;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.ContainerConfig;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class BlueprintItem extends Item {

    public BlueprintItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide) {
                boolean had = !stack.getOrDefault(SLDataComponents.BLUEPRINT_DATA.get(), BlueprintData.EMPTY).isEmpty()
                    || !stack.getOrDefault(SLDataComponents.BLUEPRINT_ANCHOR.get(), "").isEmpty()
                    || !stack.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), "").isEmpty();
                if (had) {
                    stack.remove(SLDataComponents.BLUEPRINT_DATA.get());
                    stack.remove(SLDataComponents.BLUEPRINT_ANCHOR.get());
                    stack.remove(SLDataComponents.SELECTED_GROUP.get());
                    stack.remove(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get());
                    stack.remove(SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get());
                    player.displayClientMessage(Component.translatable("msg.staticlogistics.blueprint.cleared")
                        .withStyle(ChatFormatting.YELLOW), true);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        if (level.isClientSide) {
            if (FMLEnvironment.dist.isClient()) {
                BlueprintGroupScreen screen = new BlueprintGroupScreen(stack);
                Minecraft.getInstance().setScreen(screen);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }


    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!player.isSecondaryUseActive()) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        BlockPos clickedPos = context.getClickedPos();

        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;

        BlueprintData data = stack.getOrDefault(SLDataComponents.BLUEPRINT_DATA.get(), BlueprintData.EMPTY);
        String previewStr = stack.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), "");

        if (!data.isEmpty()) {
            if (!previewStr.isEmpty()) {
                BlockPos previewAnchor = posFromString(previewStr);
                if (previewAnchor != null && previewAnchor.equals(clickedPos)) {
                    int rotation = stack.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get(), 0);
                    doPaste(serverLevel, player, stack, clickedPos, rotation);
                    stack.remove(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get());
                    stack.remove(SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get());
                } else {
                    // 移动预览到新位置
                    stack.set(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), clickedPos.toShortString());
                    player.displayClientMessage(Component.translatable(
                            "msg.staticlogistics.blueprint.preview_moved", clickedPos.toShortString())
                        .withStyle(ChatFormatting.AQUA), true);
                }
                return InteractionResult.SUCCESS;
            }

            // 首次进入预览：从右键点击位置开始
            stack.set(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), clickedPos.toShortString());
            stack.set(SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get(), 0);
            player.displayClientMessage(Component.translatable(
                    "msg.staticlogistics.blueprint.preview_enter", clickedPos.toShortString())
                .withStyle(ChatFormatting.AQUA), true);
            serverLevel.playSound(null, clickedPos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.8f, 1.5f);
            return InteractionResult.SUCCESS;
        }

        return selectOrCopy(serverLevel, player, stack, clickedPos);
    }

    private InteractionResult selectOrCopy(ServerLevel level, Player player, ItemStack stack, BlockPos clickedPos) {
        String group = findPlayerGroup(player);
        if (group.isEmpty()) {
            player.displayClientMessage(
                Component.translatable("msg.staticlogistics.blueprint.select_group").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.SUCCESS;
        }

        String anchorStr = stack.getOrDefault(SLDataComponents.BLUEPRINT_ANCHOR.get(), "");

        if (anchorStr.isEmpty()) {
            stack.set(SLDataComponents.BLUEPRINT_ANCHOR.get(), clickedPos.toShortString());
            stack.set(SLDataComponents.SELECTED_GROUP.get(), group);
            player.displayClientMessage(
                Component.translatable("msg.staticlogistics.blueprint.anchor_set", clickedPos.toShortString())
                    .withStyle(ChatFormatting.GREEN), true);
            level.playSound(null, clickedPos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5f, 1.0f);
        } else {
            BlockPos anchor = posFromString(anchorStr);
            if (anchor == null) {
                stack.remove(SLDataComponents.BLUEPRINT_ANCHOR.get());
                return InteractionResult.SUCCESS;
            }
            copyRegion(level, player, stack, anchor, clickedPos, group);
        }
        return InteractionResult.SUCCESS;
    }

    private void copyRegion(ServerLevel level, Player player, ItemStack stack, BlockPos a, BlockPos b, String group) {
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());

        int volume = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > 4096) {
            player.displayClientMessage(
                Component.translatable("msg.staticlogistics.blueprint.too_large", volume).withStyle(ChatFormatting.RED), true);
            return;
        }

        BlockPos anchor = a;
        LinkManager mgr = LinkManager.get(level);
        List<BlueprintData.BlockEntry> entries = new ArrayList<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockPos rel = pos.subtract(anchor);
                    Map<Direction, BlueprintData.FaceEntry> faces = new LinkedHashMap<>();
                    List<BlockPos> allLinks = new ArrayList<>();
                    CompoundTag containerNbt = new CompoundTag();

                    for (Direction face : Direction.values()) {
                        long key = LinkManager.posToKey(pos, face);
                        FaceConfigComposite cfg = mgr.getFaceConfig(key);
                        if (cfg == null || cfg.isDefault()) continue;
                        if (!cfg.faceConfig.getGroupIds().contains(group)) continue;

                        CompoundTag faceTag = getFaceTag(cfg);
                        CompoundTag filterTag = cfg.filterConfig.getUpgrades().serializeNBT(level.registryAccess());

                        faces.put(face, new BlueprintData.FaceEntry(faceTag, faceTag, filterTag));

                        for (LogisticsNode linked : cfg.getLinkedNodes()) {
                            BlockPos linkedRel = linked.gPos().pos().subtract(anchor);
                            allLinks.add(linkedRel);
                        }
                    }

                    ContainerConfig cc = mgr.getContainerConfig(pos);
                    if (cc != null && !cc.isDefault()) {
                        containerNbt = cc.getUpgrades().serializeNBT(level.registryAccess());
                    }

                    if (!faces.isEmpty() || !containerNbt.isEmpty()) {
                        entries.add(new BlueprintData.BlockEntry(rel, faces, containerNbt, allLinks));
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            player.displayClientMessage(
                Component.translatable("msg.staticlogistics.blueprint.empty").withStyle(ChatFormatting.YELLOW), true);
        } else {
            BlueprintData data = new BlueprintData(anchor, b, group, entries);
            stack.set(SLDataComponents.BLUEPRINT_DATA.get(), data);
            stack.remove(SLDataComponents.BLUEPRINT_ANCHOR.get());
            player.displayClientMessage(Component.translatable("msg.staticlogistics.blueprint.copied", entries.size(), anchor.toShortString())
                .withStyle(ChatFormatting.GREEN), true);
            level.playSound(null, anchor.offset(maxX - minX, maxY - minY, maxZ - minZ),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }

    private static CompoundTag getFaceTag(FaceConfigComposite cfg) {
        CompoundTag faceTag = new CompoundTag();
        faceTag.putInt("input_channel", cfg.linkConfig.getInputChannel());
        faceTag.putInt("output_channel", cfg.linkConfig.getOutputChannel());
        faceTag.putString("strategy", cfg.linkConfig.getStrategy().name());
        faceTag.putString("extraction_mode", cfg.linkConfig.getExtractionMode().name());
        faceTag.putInt("priority", cfg.linkConfig.getPriority());
        faceTag.putBoolean("global_input", cfg.isGlobalInputEnabled());
        faceTag.putBoolean("global_output", cfg.isGlobalOutputEnabled());
        faceTag.putInt("selected_types_mask", cfg.getSelectedTypesMask());
        return faceTag;
    }

    private void doPaste(ServerLevel level, Player player, ItemStack stack,
                         BlockPos newAnchor, int rotation) {
        BlueprintData data = stack.getOrDefault(SLDataComponents.BLUEPRINT_DATA.get(), BlueprintData.EMPTY);
        if (data.isEmpty()) return;

        GlobalLogisticsManager globalMgr = GlobalLogisticsManager.get(level.getServer());
        LinkManager mgr = LinkManager.get(level);
        int count = 0;
        int skipped = 0;

        int invalidCount = 0;
        for (BlueprintData.BlockEntry entry : data.blocks()) {
            BlockPos absPos = rotateRelToAbs(entry.relativePos(), newAnchor, rotation);
            if (level.getBlockEntity(absPos) == null) {
                invalidCount++;
                continue;
            }
            boolean anyValid = false;
            for (var faceEntry : entry.faces().entrySet()) {
                Direction rotatedFace = rotateDirection(faceEntry.getKey(), rotation);
                if (com.coobird.staticlogistics.transfer.handler.TransferUtils
                    .hasLogisticsCapability(level, absPos, rotatedFace)) {
                    anyValid = true;
                    break;
                }
            }
            if (!anyValid) invalidCount++;
        }
        if (invalidCount > 0) {
            player.displayClientMessage(
                Component.translatable("msg.staticlogistics.blueprint.skipped_no_cap", invalidCount)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        if (!player.isCreative()) {
            Map<String, Integer> needed = tallyUpgrades(data);
            if (!needed.isEmpty()) {
                skipped = consumeUpgrades(player, needed);
                if (skipped > 0) {
                    player.displayClientMessage(
                        Component.translatable("msg.staticlogistics.blueprint.missing_upgrades", skipped)
                            .withStyle(ChatFormatting.YELLOW), true);
                }
            }
        }

        for (BlueprintData.BlockEntry entry : data.blocks()) {
            BlockPos rel = entry.relativePos();
            BlockPos absPos = rotateRelToAbs(rel, newAnchor, rotation);
            ContainerConfig cc = mgr.getOrCreateContainerConfig(absPos);

            if (!entry.containerUpgrades().isEmpty()) {
                cc.getUpgrades().deserializeNBT(level.registryAccess(), entry.containerUpgrades());
                cc.markDirty();
                mgr.markContainerDirty(absPos.asLong());
            }

            for (var faceEntry : entry.faces().entrySet()) {
                Direction originalFace = faceEntry.getKey();
                Direction rotatedFace = rotateDirection(originalFace, rotation);
                BlueprintData.FaceEntry fe = faceEntry.getValue();
                FaceConfigComposite cfg = mgr.getOrCreateFaceConfig(absPos, rotatedFace);

                try (var ignored = cfg.beginBulkEdit()) {
                    CompoundTag ft = fe.faceConfig();
                    cfg.linkConfig.setInputChannel(ft.getInt("input_channel"));
                    cfg.linkConfig.setOutputChannel(ft.getInt("output_channel"));
                    String stratName = ft.getString("strategy");
                    if (!stratName.isEmpty()) {
                        try {
                            cfg.linkConfig.setStrategy(
                                com.coobird.staticlogistics.api.type.DistributionStrategy.valueOf(stratName));
                        } catch (Exception ignored2) {
                        }
                    }
                    String extName = ft.getString("extraction_mode");
                    if (!extName.isEmpty()) {
                        try {
                            cfg.linkConfig.setExtractionMode(
                                com.coobird.staticlogistics.api.type.ExtractionMode.valueOf(extName));
                        } catch (Exception ignored2) {
                        }
                    }
                    cfg.linkConfig.setPriority(ft.getInt("priority"));
                    cfg.setGlobalInputEnabled(ft.getBoolean("global_input"));
                    cfg.setGlobalOutputEnabled(ft.getBoolean("global_output"));
                    cfg.setSelectedTypesMask(ft.getInt("selected_types_mask"));

                    if (!fe.filterUpgrades().isEmpty()) {
                        cfg.filterConfig.getUpgrades().deserializeNBT(level.registryAccess(), fe.filterUpgrades());
                    }

                    cfg.faceConfig.setOwner(player.getUUID(), player.getGameProfile().getName(), player.getGameProfile());
                }

                mgr.markFaceDirty(LinkManager.posToKey(absPos, rotatedFace));
                mgr.refreshLocalCache(LinkManager.posToKey(absPos, rotatedFace), absPos, rotatedFace, cfg);
                mgr.syncNodeToDimension(new LogisticsNode(GlobalPos.of(level.dimension(), absPos), rotatedFace));
                count++;
            }
        }

        for (BlueprintData.BlockEntry entry : data.blocks()) {
            BlockPos absPos = rotateRelToAbs(entry.relativePos(), newAnchor, rotation);
            for (Direction face : entry.faces().keySet()) {
                Direction rotatedFace = rotateDirection(face, rotation);
                FaceConfigComposite cfg = mgr.getFaceConfig(LinkManager.posToKey(absPos, rotatedFace));
                if (cfg != null) {
                    cfg.faceConfig.addGroupId(data.groupId());
                    mgr.syncNodeToDimension(new LogisticsNode(GlobalPos.of(level.dimension(), absPos), rotatedFace));
                }
            }
        }

        for (BlueprintData.BlockEntry entry : data.blocks()) {
            BlockPos absPos = rotateRelToAbs(entry.relativePos(), newAnchor, rotation);
            for (var faceEntry : entry.faces().entrySet()) {
                Direction rotatedFace = rotateDirection(faceEntry.getKey(), rotation);
                FaceConfigComposite srcCfg = mgr.getFaceConfig(LinkManager.posToKey(absPos, rotatedFace));
                if (srcCfg == null) continue;

                for (BlockPos relLink : entry.linkedTo()) {
                    BlockPos absLinkPos = rotateRelToAbs(relLink, newAnchor, rotation);
                    for (Direction dstFace : Direction.values()) {
                        long dstKey = LinkManager.posToKey(absLinkPos, dstFace);
                        FaceConfigComposite dstCfg = mgr.getFaceConfig(dstKey);
                        if (dstCfg != null && !dstCfg.isDefault()) {
                            LogisticsNode srcNode = new LogisticsNode(
                                GlobalPos.of(level.dimension(), absPos), rotatedFace);
                            LogisticsNode dstNode = new LogisticsNode(
                                GlobalPos.of(level.dimension(), absLinkPos), dstFace);
                            srcCfg.addLinkedNode(dstNode);
                            dstCfg.addLinkedNode(srcNode);
                            globalMgr.markReverseLinksStale();
                            mgr.syncNodeToDimension(srcNode);
                            mgr.syncNodeToDimension(dstNode);
                            break;
                        }
                    }
                }
            }
        }

        mgr.markDirtyBatch(() -> {
        });

        player.displayClientMessage(Component.translatable("msg.staticlogistics.blueprint.pasted", count, newAnchor.toShortString()).withStyle(ChatFormatting.GREEN), true);
        level.playSound(null, newAnchor, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    /**
     * 根据旋转把相对坐标映射为绝对坐标
     */
    public static BlockPos rotateRelToAbs(BlockPos rel, BlockPos anchor, int rotation) {
        return switch (rotation & 3) {
            case 1 -> anchor.offset(-rel.getZ(), rel.getY(), rel.getX());   // 90°
            case 2 -> anchor.offset(-rel.getX(), rel.getY(), -rel.getZ());  // 180°
            case 3 -> anchor.offset(rel.getZ(), rel.getY(), -rel.getX());   // 270°
            default -> anchor.offset(rel);  // 0°
        };
    }

    /**
     * 面方向跟着旋转
     */
    public static Direction rotateDirection(Direction face, int rotation) {
        if (face.getAxis() == Direction.Axis.Y) return face;
        Direction[] h = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        int idx = 0;
        for (int i = 0; i < 4; i++) {
            if (h[i] == face) {
                idx = i;
                break;
            }
        }
        return h[(idx + rotation) & 3];
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BlueprintData data = stack.getOrDefault(SLDataComponents.BLUEPRINT_DATA.get(), BlueprintData.EMPTY);
        String anchorStr = stack.getOrDefault(SLDataComponents.BLUEPRINT_ANCHOR.get(), "");
        String previewStr = stack.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), "");

        if (!data.isEmpty()) {
            int faceCount = 0, containerCount = 0;
            Set<String> upgradeNames = new LinkedHashSet<>();
            for (BlueprintData.BlockEntry entry : data.blocks()) {
                faceCount += entry.faces().size();
                if (!entry.containerUpgrades().isEmpty()) {
                    containerCount++;
                    tallyUpgradeNames(upgradeNames, entry.containerUpgrades());
                }
                for (BlueprintData.FaceEntry fe : entry.faces().values()) {
                    if (!fe.filterUpgrades().isEmpty()) tallyUpgradeNames(upgradeNames, fe.filterUpgrades());
                }
            }
            int minRelX = 0, minRelY = 0, minRelZ = 0, maxRelX = 0, maxRelY = 0, maxRelZ = 0;
            for (BlueprintData.BlockEntry e : data.blocks()) {
                BlockPos r = e.relativePos();
                if (r.getX() < minRelX) minRelX = r.getX();
                if (r.getY() < minRelY) minRelY = r.getY();
                if (r.getZ() < minRelZ) minRelZ = r.getZ();
                if (r.getX() > maxRelX) maxRelX = r.getX();
                if (r.getY() > maxRelY) maxRelY = r.getY();
                if (r.getZ() > maxRelZ) maxRelZ = r.getZ();
            }
            BlockPos from = data.anchor().offset(minRelX, minRelY, minRelZ);
            BlockPos to = data.anchor().offset(maxRelX, maxRelY, maxRelZ);

            tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.region",
                from.toShortString(), to.toShortString()).withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.info").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.group", data.groupId()).withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.face_count", faceCount).withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.container",
                Component.translatable(containerCount > 0 ? "gui.staticlogistics.true" : "gui.staticlogistics.false")).withStyle(ChatFormatting.WHITE));
            if (!upgradeNames.isEmpty()) {
                tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.upgrades").withStyle(ChatFormatting.WHITE));
                for (String name : upgradeNames)
                    tooltip.add(Component.literal("    " + name).withStyle(ChatFormatting.AQUA));
            }

            if (!previewStr.isEmpty()) {
                tooltip.add(Component.empty());
                int rot = stack.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get(), 0);
                tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.preview", previewStr,
                    rot * 90).withStyle(ChatFormatting.AQUA));
            }
            tooltip.add(Component.empty());
        } else if (!anchorStr.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.anchor", anchorStr).withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.empty());
        }
        String selectedGroup = stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "");
        if (!selectedGroup.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.group",
                Component.literal(selectedGroup)).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.use").withStyle(ChatFormatting.GRAY));
        if (FMLEnvironment.dist.isClient()) {
            tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.scroll",
                SLKeyMappings.BLUEPRINT_PREVIEW_MOVE.getTranslatedKeyMessage(),
                SLKeyMappings.BLUEPRINT_PREVIEW_ROTATE.getTranslatedKeyMessage(),
                SLKeyMappings.BLUEPRINT_PREVIEW_MOVE_Y.getTranslatedKeyMessage()
            ).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.staticlogistics.blueprint.clear").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    private static void tallyUpgradeNames(Set<String> names, CompoundTag nbt) {
        var items = nbt.getList("Items", 10);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag itemTag = items.getCompound(i);
            if (itemTag.isEmpty()) continue;
            String id = itemTag.getString("id");
            if (id.isEmpty()) continue;
            names.add(Component.translatable("item." + id.replace(':', '.')).getString());
        }
    }

    private static Map<String, Integer> tallyUpgrades(BlueprintData data) {
        Map<String, Integer> needed = new LinkedHashMap<>();
        for (BlueprintData.BlockEntry entry : data.blocks()) {
            tallyFromHandler(needed, entry.containerUpgrades());
            for (BlueprintData.FaceEntry fe : entry.faces().values()) tallyFromHandler(needed, fe.filterUpgrades());
        }
        return needed;
    }

    private static void tallyFromHandler(Map<String, Integer> needed, CompoundTag nbt) {
        if (nbt.isEmpty()) return;
        var items = nbt.getList("Items", 10);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag itemTag = items.getCompound(i);
            if (itemTag.isEmpty()) continue;
            String id = itemTag.getString("id");
            needed.merge(id, itemTag.getInt("count"), Integer::sum);
        }
    }

    private static int consumeUpgrades(Player player, Map<String, Integer> needed) {
        int totalSkipped = 0;
        for (var e : needed.entrySet()) {
            int remaining = e.getValue();
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
                ItemStack slot = inventory.getItem(i);
                if (!slot.isEmpty() && BuiltInRegistries.ITEM.getKey(slot.getItem()).toString().equals(e.getKey())) {
                    int take = Math.min(remaining, slot.getCount());
                    slot.shrink(take);
                    remaining -= take;
                }
            }
            totalSkipped += remaining;
        }
        return totalSkipped;
    }

    private static String findPlayerGroup(Player player) {
        for (var hand : InteractionHand.values()) {
            String g = player.getItemInHand(hand).getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "");
            if (!g.isEmpty()) return g;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            String g = player.getInventory().getItem(i).getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "");
            if (!g.isEmpty()) return g;
        }
        return "";
    }

    @Nullable
    private static BlockPos posFromString(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] parts = s.split(", ");
        if (parts.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
