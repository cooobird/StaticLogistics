package com.coobird.staticlogistics.integration.jade;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.logic.TransferRegistries;
import com.coobird.staticlogistics.registry.SLDataComponents;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.TransferLogManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElementHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Jade 集成：指向有物流连接的方块时显示面数据。
 */
@WailaPlugin(StaticLogistics.MODID)
public class SLJadePlugin implements IWailaPlugin {

    static final ResourceLocation PLUGIN_ID = StaticLogistics.asResource("jade");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(new LogisticsDataProvider(), Block.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new LogisticsComponentProvider(), Block.class);
    }

    private static class LogisticsDataProvider implements IServerDataProvider<BlockAccessor> {
        @Override
        public ResourceLocation getUid() {
            return PLUGIN_ID;
        }

        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
            BlockEntity be = accessor.getBlockEntity();
            if (be == null) return;
            Level level = be.getLevel();
            if (!(level instanceof ServerLevel serverLevel)) return;
            BlockPos pos = be.getBlockPos();

            // 获取玩家手持配置器的选中组
            var player = accessor.getPlayer();
            String selectedGroup = "";
            if (player != null) {
                ItemStack mainHand = player.getMainHandItem();
                if (mainHand.getItem() instanceof LinkConfiguratorItem) {
                    selectedGroup = mainHand.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "");
                }
                if (selectedGroup.isEmpty()) {
                    ItemStack offHand = player.getOffhandItem();
                    if (offHand.getItem() instanceof LinkConfiguratorItem) {
                        selectedGroup = offHand.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "");
                    }
                }
            }

            // 没有选中组则不显示
            if (selectedGroup.isEmpty()) return;

            LinkManager mgr = LinkManager.get(serverLevel);
            CompoundTag facesTag = new CompoundTag();

            for (Direction face : Direction.values()) {
                long key = LinkManager.posToKey(pos, face);
                FaceConfigComposite cfg = mgr.getFaceConfig(key);
                if (cfg == null || cfg.isDefault()) continue;
                // 只显示属于选中组的面
                if (!cfg.faceConfig.getGroupIds().contains(selectedGroup)) continue;

                CompoundTag faceTag = new CompoundTag();
                String groups = String.join(", ", cfg.faceConfig.getGroupIds());
                faceTag.putString("groups", groups);

                if (cfg.isGlobalOutputEnabled() && cfg.isGlobalInputEnabled()) {
                    faceTag.putString("role", "both");
                } else if (cfg.isGlobalOutputEnabled()) {
                    faceTag.putString("role", "output");
                } else if (cfg.isGlobalInputEnabled()) {
                    faceTag.putString("role", "input");
                }

                List<String> activeTypes = new ArrayList<>();
                for (TransferType type : TransferRegistries.getAllActive()) {
                    if (cfg.isTypeSelected(type)) {
                        activeTypes.add(type.id().getPath());
                    }
                }
                faceTag.putString("types", String.join(",", activeTypes));
                faceTag.putInt("linked", cfg.getLinkedNodes().size());
                int keepStock = cfg.linkConfig.getKeepStock();
                if (keepStock > 0) faceTag.putInt("keep_stock", keepStock);

                // 频道、策略、优先级、所有者
                int inCh = cfg.linkConfig.getInputChannel();
                int outCh = cfg.linkConfig.getOutputChannel();
                if (inCh > 0) faceTag.putInt("in_channel", inCh);
                if (outCh > 0) faceTag.putInt("out_channel", outCh);
                faceTag.putString("strategy", cfg.linkConfig.getStrategy().getDescriptionId());
                faceTag.putString("extraction_mode", cfg.linkConfig.getExtractionMode().getDescriptionId());
                faceTag.putInt("priority", cfg.linkConfig.getPriority());
                if (cfg.faceConfig.getOwner() != null) {
                    faceTag.putString("owner", cfg.faceConfig.getOwnerName());
                }

                // 传输统计
                long faceKey = LinkManager.posToKey(pos, face);
                TransferLogManager.NodeStats nodeStats = TransferLogManager.get().getPerNodeStats(faceKey);
                if (nodeStats != null) {
                    faceTag.putLong("sent", nodeStats.sentAmount);
                    faceTag.putLong("received", nodeStats.receivedAmount);
                }

                facesTag.put(face.getName(), faceTag);
            }

            if (!facesTag.isEmpty()) {
                tag.put("sl_faces", facesTag);
            }

            // 全局传输统计
            TransferLogManager logMgr = TransferLogManager.get();
            long timeSince = logMgr.getTimeSinceLastTransfer();
            if (timeSince >= 0) {
                tag.putDouble("sl_rate", logMgr.getTransfersPerMinute());
                tag.putLong("sl_last_ms", timeSince);
            }
        }
    }

    private static class LogisticsComponentProvider implements IBlockComponentProvider {

        @Override
        public ResourceLocation getUid() {
            return PLUGIN_ID;
        }

        private static String formatDuration(long ms) {
            if (ms < 0) return "";
            if (ms < 1000) return ms + "ms";
            long seconds = ms / 1000;
            if (seconds < 60) return seconds + "s";
            long minutes = seconds / 60;
            seconds %= 60;
            if (minutes < 60) return minutes + "m " + seconds + "s";
            long hours = minutes / 60;
            minutes %= 60;
            return hours + "h " + minutes + "m";
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag tag = accessor.getServerData();
            CompoundTag facesTag = tag.getCompound("sl_faces");
            if (facesTag.isEmpty()) return;

            // 只显示玩家指向的那个面
            Direction hitFace = accessor.getHitResult().getDirection();
            CompoundTag faceTag = facesTag.getCompound(hitFace.getSerializedName());
            if (faceTag == null || faceTag.isEmpty()) return;

            String role = faceTag.getString("role");
            String roleKey = switch (role) {
                case "both" -> "jade.staticlogistics.both";
                case "output" -> "jade.staticlogistics.output";
                case "input" -> "jade.staticlogistics.input";
                default -> null;
            };
            if (roleKey == null) return;

            ITooltip boxContent = IElementHelper.get().tooltip();

            // 面名 + 角色 + 组
            String groups = faceTag.getString("groups");
            int linked = faceTag.getInt("linked");
            String linkedStr = linked > 0
                ? Component.translatable("jade.staticlogistics.linked", linked).getString()
                : "";
            MutableComponent line = Component.literal(hitFace.getSerializedName().toUpperCase() + " ")
                .append(Component.translatable(roleKey));
            if (!groups.isEmpty()) {
                line.append(Component.literal(" ")
                    .append(Component.translatable("jade.staticlogistics.group_label", groups)));
            }
            if (!linkedStr.isEmpty()) {
                line.append(Component.literal(linkedStr));
            }
            boxContent.add(line);

            // 输入端信息
            boolean hasIn = role.equals("input") || role.equals("both");
            boolean hasOut = role.equals("output") || role.equals("both");
            int inCh = faceTag.getInt("in_channel");
            int outCh = faceTag.getInt("out_channel");
            int priority = faceTag.getInt("priority");
            int keepStock = faceTag.getInt("keep_stock");
            String strategyKey = faceTag.getString("strategy");
            String extractionKey = faceTag.getString("extraction_mode");

            if (hasIn) {
                boxContent.add(Component.translatable("jade.staticlogistics.section_input").withStyle(ChatFormatting.AQUA));
                if (inCh > 0) {
                    boxContent.add(Component.literal("  ")
                        .append(Component.translatable("jade.staticlogistics.channel", inCh))
                        .withStyle(ChatFormatting.GRAY));
                }
                boxContent.add(Component.literal("  ")
                    .append(Component.translatable("jade.staticlogistics.priority", priority))
                    .withStyle(ChatFormatting.GRAY));
                if (keepStock > 0) {
                    boxContent.add(Component.literal("  ")
                        .append(Component.translatable("jade.staticlogistics.keep_stock", keepStock))
                        .withStyle(ChatFormatting.GRAY));
                }
            }

            if (hasOut) {
                boxContent.add(Component.translatable("jade.staticlogistics.section_output").withStyle(ChatFormatting.YELLOW));
                if (outCh > 0) {
                    boxContent.add(Component.literal("  ")
                        .append(Component.translatable("jade.staticlogistics.channel", outCh))
                        .withStyle(ChatFormatting.GRAY));
                }
                if (!strategyKey.isEmpty()) {
                    boxContent.add(Component.literal("  ")
                        .append(Component.translatable("jade.staticlogistics.strategy_label",
                            Component.translatable(strategyKey)))
                        .withStyle(ChatFormatting.GRAY));
                }
                if (!extractionKey.isEmpty()) {
                    boxContent.add(Component.literal("  ")
                        .append(Component.translatable("jade.staticlogistics.extraction_label",
                            Component.translatable(extractionKey)))
                        .withStyle(ChatFormatting.GRAY));
                }
            }

            // 所有者
            String owner = faceTag.getString("owner");
            if (owner != null && !owner.isEmpty()) {
                boxContent.add(Component.literal("  ")
                    .append(Component.translatable("jade.staticlogistics.owner", owner))
                    .withStyle(ChatFormatting.GRAY));
            }

            // 传输统计
            long sent = faceTag.getLong("sent");
            long received = faceTag.getLong("received");
            if (sent > 0 || received > 0) {
                boxContent.add(Component.translatable("jade.staticlogistics.transfer_stats", sent, received).withStyle(ChatFormatting.GRAY));
            }

            // 全局速率
            double rate = tag.getDouble("sl_rate");
            long lastMs = tag.getLong("sl_last_ms");
            if (rate > 0 || lastMs > 0) {
                String rateStr = String.format("%.1f", rate);
                String lastStr = formatDuration(lastMs);
                boxContent.add(Component.literal("  ")
                    .append(Component.translatable("jade.staticlogistics.global_stats", rateStr, lastStr))
                    .withStyle(ChatFormatting.GRAY));
            }

            if (!boxContent.isEmpty()) {
                tooltip.add(IElementHelper.get().box(boxContent, BoxStyle.getNestedBox()));
            }
        }
    }
}
