package com.coobird.staticlogistics.integration.jade;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.group.GroupService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.coobird.staticlogistics.transfer.NodeQueryService;
import com.coobird.staticlogistics.transfer.NodeQuerySnapshot;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
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

/**
 * Jade 集成：指向有物流连接的方块时显示面数据。
 */
@WailaPlugin(StaticLogistics.MODID)
public class SLJadePlugin implements IWailaPlugin {

    static final ResourceLocation PLUGIN_ID = StaticLogistics.asResource("jade");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(new LogisticsDataProvider(), BlockEntity.class);
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
            tag.remove("sl_faces");
            BlockEntity be = accessor.getBlockEntity();
            if (be == null) return;
            Level level = be.getLevel();
            if (!(level instanceof ServerLevel serverLevel)) return;
            BlockPos pos = be.getBlockPos();

            // 获取玩家手持配置器的选中组
            var player = accessor.getPlayer();
            String selectedGroup = "";
            GroupKey selectedGroupKey = null;
            if (player != null) {
                ItemStack mainHand = player.getMainHandItem();
                if (mainHand.getItem() instanceof LinkConfiguratorItem) {
                    selectedGroup = PortItemStackExtension.getDataOrDefault(mainHand, SLDataComponents.SELECTED_GROUP, "");
                    selectedGroupKey = PortItemStackExtension.getData(
                        mainHand, SLDataComponents.SELECTED_GROUP_KEY.get());
                }
                if (selectedGroup.isEmpty()) {
                    ItemStack offHand = player.getOffhandItem();
                    if (offHand.getItem() instanceof LinkConfiguratorItem) {
                        selectedGroup = PortItemStackExtension.getDataOrDefault(offHand, SLDataComponents.SELECTED_GROUP, "");
                        selectedGroupKey = PortItemStackExtension.getData(
                            offHand, SLDataComponents.SELECTED_GROUP_KEY.get());
                    }
                }
            }

            // 没有选中组则不显示
            if (selectedGroup.isEmpty()) return;
            if (selectedGroupKey == null) {
                if (player == null) return;
                var migratedGroup = PlayerGroupStore.get(serverLevel.getServer())
                    .findGroup(player.getUUID(), selectedGroup);
                if (migratedGroup == null) return;
                selectedGroupKey = migratedGroup.key();
            }

            CompoundTag facesTag = new CompoundTag();

            for (Direction face : Direction.values()) {
                NodeQuerySnapshot snapshot = NodeQueryService.query(serverLevel, pos, face).orElse(null);
                if (snapshot == null) continue;
                if (player == null || !GroupService.canAccess(snapshot.ownerId(), player)) continue;
                // 只显示属于选中组的面
                if (!snapshot.groupKeys().contains(selectedGroupKey)) continue;

                CompoundTag faceTag = new CompoundTag();
                String groups = String.join(", ", snapshot.groups());
                faceTag.putString("groups", groups);

                if (snapshot.outputEnabled() && snapshot.inputEnabled()) {
                    faceTag.putString("role", "both");
                } else if (snapshot.outputEnabled()) {
                    faceTag.putString("role", "output");
                } else if (snapshot.inputEnabled()) {
                    faceTag.putString("role", "input");
                }

                ListTag outputTypes = new ListTag();
                for (ResourceLocation typeId : snapshot.outputTypeIds()) {
                    outputTypes.add(StringTag.valueOf(typeId.toString()));
                }
                ListTag acceptedTypes = new ListTag();
                for (ResourceLocation typeId : snapshot.acceptedTypeIds()) {
                    acceptedTypes.add(StringTag.valueOf(typeId.toString()));
                }
                faceTag.put("output_type_ids", outputTypes);
                faceTag.put("accepted_type_ids", acceptedTypes);
                faceTag.putInt("linked", snapshot.linkedNodes().size());
                int keepStock = snapshot.keepStock();
                if (keepStock > 0) faceTag.putInt("keep_stock", keepStock);

                // 频道、策略、优先级、所有者
                int inCh = snapshot.inputChannel();
                int outCh = snapshot.outputChannel();
                if (inCh > 0) faceTag.putInt("in_channel", inCh);
                if (outCh > 0) faceTag.putInt("out_channel", outCh);
                faceTag.putString("strategy", snapshot.strategyDescriptionId());
                faceTag.putString("extraction_mode", snapshot.extractionDescriptionId());
                faceTag.putInt("priority", snapshot.priority());
                if (snapshot.ownerId() != null) {
                    faceTag.putString("owner", snapshot.ownerName());
                }

                // 传输统计
                faceTag.putLong("sent", snapshot.sentAmount());
                faceTag.putLong("received", snapshot.receivedAmount());
                faceTag.putDouble("rate", snapshot.transfersPerMinute());
                if (snapshot.lastTransferAgeTicks() >= 0) {
                    faceTag.putLong("last_ms", snapshot.lastTransferAgeTicks() * 50L);
                }

                facesTag.put(face.getName(), faceTag);
            }

            if (!facesTag.isEmpty()) {
                tag.put("sl_faces", facesTag);
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
                addResourceTypeLine(boxContent, "jade.staticlogistics.receive_types", faceTag, "accepted_type_ids");
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
                addResourceTypeLine(boxContent, "jade.staticlogistics.transfer_types", faceTag, "output_type_ids");
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

            // 面速率
            double rate = faceTag.getDouble("rate");
            long lastMs = faceTag.getLong("last_ms");
            if (rate > 0 || faceTag.contains("last_ms", Tag.TAG_LONG)) {
                String rateStr = String.format("%.1f", rate);
                String lastStr = formatDuration(lastMs);
                boxContent.add(Component.literal("  ")
                    .append(Component.translatable("jade.staticlogistics.face_stats", rateStr, lastStr))
                    .withStyle(ChatFormatting.GRAY));
            }

            if (!boxContent.isEmpty()) {
                tooltip.add(IElementHelper.get().box(boxContent, BoxStyle.DEFAULT));
            }
        }

        private static Component buildResourceTypeList(CompoundTag faceTag, String listKey) {
            ListTag typeIds = faceTag.getList(listKey, Tag.TAG_STRING);
            MutableComponent result = Component.empty();
            boolean hasType = false;
            for (int index = 0; index < typeIds.size(); index++) {
                ResourceLocation typeId = ResourceLocation.tryParse(typeIds.getString(index));
                if (typeId == null) continue;

                if (hasType) result.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                LogisticsResource<?> type = TransferRegistries.get(typeId);
                if (type == null) {
                    result.append(Component.literal(typeId.toString()).withStyle(ChatFormatting.DARK_GRAY));
                } else {
                    result.append(Component.translatable(type.translationKey())
                        .withStyle(style -> style.withColor(type.color())));
                }
                hasType = true;
            }
            return hasType ? result : Component.translatable("jade.staticlogistics.no_resource_types");
        }

        private static void addResourceTypeLine(ITooltip tooltip, String translationKey,
                                                CompoundTag faceTag, String listKey) {
            tooltip.add(Component.literal("  ")
                .append(Component.translatable(translationKey, buildResourceTypeList(faceTag, listKey)))
                .withStyle(ChatFormatting.GRAY));
        }
    }
}
