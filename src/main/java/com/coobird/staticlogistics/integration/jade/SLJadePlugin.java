package com.coobird.staticlogistics.integration.jade;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;

import net.minecraft.ChatFormatting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import snownee.jade.api.*;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElementHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Jade 集成：指向有物流连接的方块时显示面数据。
 */
@WailaPlugin(Staticlogistics.MODID)
public class SLJadePlugin implements IWailaPlugin {

    static final ResourceLocation PLUGIN_ID = Staticlogistics.asResource("jade");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(new LogisticsDataProvider(), Block.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new LogisticsComponentProvider(), Block.class);
    }

    /**
     * 服务端数据提供者：将面配置打包为 NBT 发送给客户端。
     */
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

            LinkManager mgr = LinkManager.get(serverLevel);
            CompoundTag facesTag = new CompoundTag();

            for (Direction face : Direction.values()) {
                long key = LinkManager.posToKey(pos, face);
                FaceConfigComposite cfg = mgr.getFaceConfig(key);
                if (cfg == null || cfg.isDefault()) continue;

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

                facesTag.put(face.getName(), faceTag);
            }

            if (!facesTag.isEmpty()) {
                tag.put("sl_faces", facesTag);
            }
        }
    }

    /**
     * 客户端渲染：解析 NBT 并显示面数据。
     */
    private static class LogisticsComponentProvider implements IBlockComponentProvider {
        private static final Component TITLE = Component.translatable("jade.staticlogistics.title").withStyle(ChatFormatting.GOLD);

        @Override
        public ResourceLocation getUid() {
            return PLUGIN_ID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag tag = accessor.getServerData();
            CompoundTag facesTag = tag.getCompound("sl_faces");
            if (facesTag.isEmpty()) return;

            tooltip.add(TITLE);

            ITooltip boxTooltip = IElementHelper.get().tooltip();
            for (String faceName : facesTag.getAllKeys()) {
                CompoundTag faceTag = facesTag.getCompound(faceName);
                Direction face = Direction.byName(faceName);
                if (face == null) continue;

                String groups = faceTag.getString("groups");
                String role = faceTag.getString("role");
                int linked = faceTag.getInt("linked");
                int keepStock = faceTag.getInt("keep_stock");

                String roleKey = switch (role) {
                    case "both" -> "jade.staticlogistics.both";
                    case "output" -> "jade.staticlogistics.output";
                    case "input" -> "jade.staticlogistics.input";
                    default -> null;
                };
                if (roleKey == null) continue;

                String linkedStr = linked > 0
                    ? Component.translatable("jade.staticlogistics.linked", linked).getString()
                    : "";
                String stockStr = keepStock > 0
                    ? Component.translatable("jade.staticlogistics.keep_stock", keepStock).getString()
                    : "";

                Component line = Component.translatable("jade.staticlogistics.face_info",
                    faceName.toUpperCase(),
                    Component.translatable(roleKey).getString(),
                    groups, linkedStr, stockStr);

                boxTooltip.add(line);
            }

            if (!boxTooltip.isEmpty()) {
                tooltip.add(IElementHelper.get().box(boxTooltip, BoxStyle.getNestedBox()));
            }
        }
    }
}
