package com.coobird.staticlogistics.content.menu;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.item.UpgradeItem;
import com.coobird.staticlogistics.content.registry.SLMenuTypes;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.logistics.node.*;
import com.coobird.staticlogistics.network.s2c.S2CConfiguratorReadyPayload;
import com.coobird.staticlogistics.network.s2c.S2CTransferRatesPayload;
import com.coobird.staticlogistics.transfer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.mesdag.portlib.network.PortPacketDistributor;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;

import java.util.*;

public class LinkConfiguratorMenu extends AbstractContainerMenu {
    private LogisticsNode observedNode;
    private GlobalPos observedPeer;
    private LiveTransferRates observedRates;
    private long nextRateSyncTick;
    private List<LogisticsNode> observedPeers = List.of();
    private boolean observedPeersTruncated;
    private Map<LogisticsNode, Map<ResourceLocation, LiveTransferRates.Rate>> clientPeerRates = Map.of();
    private boolean clientPeersTruncated;
    private boolean clientPeerRatesReady;
    private Set<LogisticsNode> clientUnavailablePeers = Set.of();
    private Map<ResourceLocation, LiveTransferRates.Rate> clientRates = Map.of();
    private String clientDiagnostic = "";

    public void acceptPeerRates(Map<LogisticsNode, Map<ResourceLocation, LiveTransferRates.Rate>> rates, boolean truncated, Set<LogisticsNode> unavailable) {
        clientPeerRates = Map.copyOf(rates);
        clientPeersTruncated = truncated;
        clientPeerRatesReady = true;
        clientUnavailablePeers = Set.copyOf(unavailable);
    }

    public Map<LogisticsNode, Map<ResourceLocation, LiveTransferRates.Rate>> getPeerRates() {
        return clientPeerRates;
    }

    public boolean arePeerRatesTruncated() {
        return clientPeersTruncated;
    }

    public boolean arePeerRatesReady() {
        return clientPeerRatesReady;
    }

    public void clearPeerRates() {
        clientPeerRates = Map.of();
        clientPeerRatesReady = false;
        clientPeersTruncated = false;
        clientUnavailablePeers = Set.of();
    }

    public Set<LogisticsNode> getUnavailablePeers() {
        return clientUnavailablePeers;
    }

    public void acceptTransferDiagnostic(String diagnostic) {
        clientDiagnostic = diagnostic;
    }

    public String getTransferDiagnostic() {
        return clientDiagnostic;
    }

    public void observeTransferRates(ConnectionKey connection, boolean enabled) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!enabled) {
            stopObservingRates();
            return;
        }
        if (observedRates != null || !hasTarget || resolveValidatedNode(serverPlayer) == null
            || !connection.groupKey().equals(groupKey) || !connection.contains(getTargetNode().gPos())
            || !GlobalLogisticsManager.get(serverPlayer.server).getConnections(groupKey).contains(connection)) return;
        observedNode = getTargetNode();
        observedPeer = connection.first().equals(observedNode.gPos()) ? connection.second() : connection.first();
        observedRates = TransferLogManager.get(serverPlayer.server).observe(observedNode, observedPeer);
        refreshObservedPeers(serverPlayer);
        nextRateSyncTick = 0;
    }

    private void stopObservingRates() {
        if (observedRates != null) observedPeers.forEach(observedRates::releasePeer);
        if (observedNode != null && player instanceof ServerPlayer serverPlayer)
            TransferLogManager.get(serverPlayer.server).stopObserving(observedNode, observedPeer);
        observedNode = null;
        observedPeer = null;
        observedRates = null;
        observedPeers = List.of();
        clientPeerRates = Map.of();
        clientPeersTruncated = false;
        clientPeerRatesReady = false;
        clientUnavailablePeers = Set.of();
        clientRates = Map.of();
        clientDiagnostic = "";
    }

    public void acceptTransferRates(Map<ResourceLocation, LiveTransferRates.Rate> rates) {
        clientRates = Map.copyOf(rates);
    }

    public LiveTransferRates.Rate getTransferRate(ResourceLocation type) {
        return clientRates.getOrDefault(type, LiveTransferRates.Rate.ZERO);
    }

    @Override
    public void removed(Player player) {
        stopObservingRates();
        super.removed(player);
    }

    private boolean clientBatchSelection;

    public void setClientBatchSelection(boolean batch) {
        clientBatchSelection = batch;
    }

    private static final int FILTER_SLOTS = 1;
    private static final int UPGRADE_SLOTS = ContainerConfig.UPGRADE_SLOT_COUNT;
    private static final int TOTAL_CONFIG_SLOTS = FILTER_SLOTS + UPGRADE_SLOTS;
    private static final int INV_SLOT_START = TOTAL_CONFIG_SLOTS;
    private static final int INV_SLOT_END = INV_SLOT_START + 27;
    private static final int HOTBAR_SLOT_START = INV_SLOT_END;
    private static final int HOTBAR_SLOT_END = HOTBAR_SLOT_START + 9;

    public static final int INPUT_FILTER_X = MenuLayout.NODE_FILTER_X;
    public static final int INPUT_FILTER_Y = MenuLayout.NODE_SLOT_Y;
    public static final int UPGRADE_X = MenuLayout.NODE_UPGRADE_X;
    public static final int UPGRADE_Y = MenuLayout.NODE_SLOT_Y;

    private BlockPos pos;
    private Direction face;
    private int channel;
    private ResourceKey<Level> targetDimension;
    private GroupKey groupKey;
    private boolean hasTarget;
    private final int toolSlot;
    private final Player player;
    private final SwitchingItemHandler filterHandler = new SwitchingItemHandler(FILTER_SLOTS);
    private final SwitchingItemHandler containerHandler = new SwitchingItemHandler(UPGRADE_SLOTS);
    private EndpointConfig faceConfig;
    private ContainerConfig containerConfig;
    private List<ResourceLocation> selectedTypeIds = List.of();

    private final DataSlot redstoneSlot = DataSlot.standalone();
    private final DataSlot editableTargetSlot = DataSlot.standalone();

    public boolean canEditTarget() {
        if (!hasTarget) return false;
        if (player instanceof ServerPlayer serverPlayer)
            return resolveValidatedNode(serverPlayer) != null;
        return editableTargetSlot.get() != 0;
    }

    private final DataSlot enabledSlot = DataSlot.standalone();

    public boolean isChannelEnabled() {
        return enabledSlot.get() != 0;
    }

    public ChannelRedstoneMode getRedstoneMode() {
        return ChannelRedstoneMode.values()[Math.floorMod(redstoneSlot.get(), ChannelRedstoneMode.values().length)];
    }
    private final DataSlot globalInputSlot = DataSlot.standalone();
    private final DataSlot globalOutputSlot = DataSlot.standalone();
    private final DataSlot strategySlot = DataSlot.standalone();
    private final DataSlot extractionModeSlot = DataSlot.standalone();
    private final LongMenuValue prioritySlot = new LongMenuValue();
    private final LongMenuValue keepStockSlot = new LongMenuValue();


    private final DataSlot speedMultSlot = DataSlot.standalone();
    private final DataSlot rangeMultSlot = DataSlot.standalone();
    private final DataSlot stackMultSlot = DataSlot.standalone();
    private final DataSlot dimensionSlot = DataSlot.standalone();
    private final DataSlot visibleSideSlot = DataSlot.standalone();

    private final ItemStack[] lastUpgradeStacks = new ItemStack[UPGRADE_SLOTS];
    private long lastConfigGeneration = -1L;

    public LinkConfiguratorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, readOpenData(buf));
    }

    public LinkConfiguratorMenu(int containerId, Inventory playerInventory, LogisticsNode node, GroupKey groupKey, boolean inputSide) {
        this(containerId, playerInventory, node.gPos().pos(), node.face(),
            node.gPos().dimension(), groupKey, inputSide, node.channel(), List.of(),
            findToolSlot(playerInventory));
    }

    public LinkConfiguratorMenu(int containerId, Inventory playerInventory, int toolSlot) {
        this(containerId, playerInventory, null, null, null, null, true, 0, List.of(), toolSlot);
    }

    private LinkConfiguratorMenu(int containerId, Inventory playerInventory, OpenData data) {
        this(containerId, playerInventory, data.pos(), data.face(), data.dimension(),
            data.groupKey(), data.inputSide(), data.channel(), data.initialTypeIds(), data.toolSlot());
    }

    private LinkConfiguratorMenu(
        int containerId,
        Inventory playerInventory,
        BlockPos pos,
        Direction face,
        ResourceKey<Level> targetDimension,
        GroupKey groupKey,
        boolean inputSide,
        int channel,
        List<ResourceLocation> initialTypeIds,
        int toolSlot
    ) {
        super(SLMenuTypes.LINK_CONFIGURATOR_MENU.get(), containerId);
        this.pos = pos;
        this.face = face;
        this.channel = channel;
        this.targetDimension = targetDimension;
        this.groupKey = groupKey;
        this.hasTarget = pos != null && face != null
            && targetDimension != null && groupKey != null;
        this.toolSlot = toolSlot;
        this.player = playerInventory.player;
        this.selectedTypeIds = initialTypeIds;
        if (hasTarget && channel >= 0) getToolStack().set(SLDataComponents.SELECTED_CHANNEL.get(), channel);

        this.addDataSlot(globalInputSlot);
        this.addDataSlot(editableTargetSlot);
        this.addDataSlot(redstoneSlot);
        this.addDataSlot(enabledSlot);
        this.addDataSlot(globalOutputSlot);
        this.addDataSlot(strategySlot);
        this.addDataSlot(extractionModeSlot);
        prioritySlot.register(this::addDataSlot);
        keepStockSlot.register(this::addDataSlot);


        addDataSlot(speedMultSlot);
        addDataSlot(rangeMultSlot);
        addDataSlot(stackMultSlot);
        addDataSlot(dimensionSlot);
        visibleSideSlot.set(inputSide ? 1 : 0);
        addDataSlot(visibleSideSlot);

        Arrays.fill(lastUpgradeStacks, ItemStack.EMPTY);

        ServerLevel serverLevel = !hasTarget || player.getServer() == null
            ? null : player.getServer().getLevel(targetDimension);
        if (serverLevel != null && pos != null && face != null) {
            LinkManager mgr = LinkManager.get(serverLevel);
            if (mgr != null) {
                this.faceConfig = mgr.getEndpointConfig(new EndpointAddress(pos.asLong(), face, channel));
                this.containerConfig = mgr.getOrCreateContainerConfig(pos);
                filterHandler.bind(faceConfig == null
                    ? new ItemStackHandler(FILTER_SLOTS)
                    : faceConfig.filterConfig.getUpgrades());
                containerHandler.bind(containerConfig.getUpgrades());
                syncFaceSlots();
                syncContainerSlots();
                cacheUpgradeStacks();
            }
        }

        this.addSlot(new FilterSlot(filterHandler, 0, INPUT_FILTER_X, INPUT_FILTER_Y, UpgradeType.FILTER));
        this.addSlot(new UpgradeSlot(containerHandler, ContainerConfig.UPGRADE_SLOT,
            UPGRADE_X, UPGRADE_Y, UpgradeType.COMBINED));

        addPlayerInventory(playerInventory);
    }

    private static List<ResourceLocation> readInitialTypeIds(FriendlyByteBuf buf) {
        if (buf.readableBytes() <= 0) return List.of();
        int size = buf.readVarInt();
        List<ResourceLocation> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(buf.readResourceLocation());
        }
        return TransferTypeSelection.sanitize(ids);
    }

    private static OpenData readOpenData(FriendlyByteBuf buf) {
        int toolSlot = buf.readVarInt();
        if (!buf.readBoolean()) {
            return new OpenData(null, null, null, null, true, 0, List.of(), toolSlot);
        }
        BlockPos pos = buf.readBlockPos();
        Direction face = buf.readEnum(Direction.class);
        int channel = buf.readVarInt();
        List<ResourceLocation> typeIds = readInitialTypeIds(buf);
        ResourceKey<Level> dimension = ResourceKey.create(
            Registries.DIMENSION, buf.readResourceLocation());
        GroupKey groupKey = GroupKey.STREAM_CODEC.decode((PortRegistryFriendlyByteBuf) buf);
        boolean inputSide = buf.readBoolean();
        return new OpenData(pos, face, dimension, groupKey, inputSide, channel, typeIds, toolSlot);
    }

    public static void writeEmptyOpenData(FriendlyByteBuf buf, int toolSlot) {
        buf.writeVarInt(toolSlot);
        buf.writeBoolean(false);
    }

    public static void writeTargetOpenData(
        FriendlyByteBuf buf,
        LogisticsNode node,
        GroupKey groupKey,
        boolean inputSide,
        EndpointConfig config,
        int toolSlot
    ) {
        buf.writeVarInt(toolSlot);
        buf.writeBoolean(true);
        buf.writeBlockPos(node.gPos().pos());
        buf.writeEnum(node.face());
        buf.writeVarInt(node.channel());
        writeInitialTypeIds(buf, config);
        writeRemoteContext(buf, node, groupKey, inputSide);
    }

    private static void writeInitialTypeIds(FriendlyByteBuf buf, EndpointConfig config) {
        List<ResourceLocation> ids = config == null || !config.isGlobalOutputEnabled()
            ? List.of() : config.getSelectedTypeIds();
        buf.writeVarInt(ids.size());
        for (ResourceLocation id : ids) {
            buf.writeResourceLocation(id);
        }
    }

    private static void writeRemoteContext(
        FriendlyByteBuf buf, LogisticsNode node, GroupKey groupKey, boolean inputSide) {
        buf.writeResourceLocation(node.gPos().dimension().location());
        GroupKey.STREAM_CODEC.encode((PortRegistryFriendlyByteBuf) buf, groupKey);
        buf.writeBoolean(inputSide);
    }

    public boolean isGlobalInputEnabled() {
        return globalInputSlot.get() == 1;
    }

    public boolean isGlobalOutputEnabled() {
        return globalOutputSlot.get() == 1;
    }

    public DistributionStrategy getStrategy() {
        int idx = strategySlot.get();
        var vals = DistributionStrategyRegistry.getValues();
        return idx >= 0 && idx < vals.size() ? vals.get(idx) : DistributionStrategyRegistry.SEQUENTIAL;
    }

    public ExtractionMode getExtractionMode() {
        int idx = extractionModeSlot.get();
        var vals = ExtractionMode.values();
        return idx >= 0 && idx < vals.length ? vals[idx] : ExtractionMode.SEQUENTIAL;
    }

    public long getPriority() {
        return prioritySlot.get();
    }

    public long getKeepStock() {
        return keepStockSlot.get();
    }

    public List<ResourceLocation> getSelectedTypeIds() {
        return selectedTypeIds;
    }

    public int getSpeedMultiplier() {
        return speedMultSlot.get();
    }

    public int getRangeMultiplier() {
        return rangeMultSlot.get();
    }

    public int getStackMultiplier() {
        return stackMultSlot.get();
    }

    public boolean isDimensionEffective() {
        return dimensionSlot.get() == 1;
    }

    public void setGlobalInputEnabled(boolean enabled) {
        if (faceConfig != null && faceConfig.isGlobalInputEnabled() != enabled) {
            faceConfig.setGlobalInputEnabled(enabled);
            syncFaceSlots();
        }
    }

    public void setGlobalOutputEnabled(boolean enabled) {
        if (faceConfig != null && faceConfig.isGlobalOutputEnabled() != enabled) {
            faceConfig.setGlobalOutputEnabled(enabled);
            syncFaceSlots();
        }
    }

    public void setStrategy(DistributionStrategy s) {
        if (faceConfig != null && faceConfig.linkConfig.getStrategy() != s) {
            faceConfig.setDistributionStrategy(s);
            syncFaceSlots();
        }
    }

    public void setExtractionMode(ExtractionMode m) {
        if (faceConfig != null && faceConfig.linkConfig.getExtractionMode() != m) {
            faceConfig.setExtractionMode(m);
            syncFaceSlots();
        }
    }

    public void setKeepStock(long v) {
        if (faceConfig != null && faceConfig.linkConfig.getKeepStock() != v) {
            faceConfig.setKeepStock(v);
            syncFaceSlots();
        }
    }

    public void acceptSelectedTypeIds(List<ResourceLocation> ids) {
        selectedTypeIds = List.copyOf(ids);

    }

    /// 数据同步。

    public void syncFaceSlots() {
        if (faceConfig == null) return;
        redstoneSlot.set(faceConfig.linkConfig.getRedstoneMode().ordinal());
        enabledSlot.set(faceConfig.linkConfig.isEnabled() ? 1 : 0);
        ServerLevel level = player.getServer() == null
            ? null : player.getServer().getLevel(targetDimension);
        if (level != null && pos != null && face != null) {
            NodeQuerySnapshot snapshot = NodeQueryService.query(level, getTargetNode()).orElse(null);
            if (snapshot != null) {
                globalInputSlot.set(snapshot.inputEnabled() ? 1 : 0);
                globalOutputSlot.set(snapshot.outputEnabled() ? 1 : 0);
                strategySlot.set(DistributionStrategyRegistry.getValues().indexOf(
                    DistributionStrategyRegistry.byName(snapshot.strategyId().toString())));
                extractionModeSlot.set(snapshot.extractionMode().ordinal());
                prioritySlot.set(snapshot.priority());
                keepStockSlot.set(snapshot.keepStock());
                selectedTypeIds = snapshot.selectedTypeIds();

                return;
            }
        }
        syncFaceSlotsFromConfig();
    }

    private void syncFaceSlotsFromConfig() {
        globalInputSlot.set(faceConfig.isGlobalInputEnabled() ? 1 : 0);
        globalOutputSlot.set(faceConfig.isGlobalOutputEnabled() ? 1 : 0);
        strategySlot.set(DistributionStrategyRegistry.getValues().indexOf(faceConfig.linkConfig.getStrategy()));
        extractionModeSlot.set(faceConfig.linkConfig.getExtractionMode().ordinal());
        prioritySlot.set(faceConfig.linkConfig.getPriority());
        keepStockSlot.set(faceConfig.linkConfig.getKeepStock());
        selectedTypeIds = faceConfig.getSelectedTypeIds();

    }

    public void syncContainerSlots() {
        if (containerConfig == null) return;
        speedMultSlot.set((int) Math.min(LogisticsCalculator.getSpeedMultiplier(containerConfig), Integer.MAX_VALUE));
        rangeMultSlot.set((int) Math.min(LogisticsCalculator.getRangeMultiplier(containerConfig), Integer.MAX_VALUE));
        stackMultSlot.set((int) Math.min(LogisticsCalculator.getStackMultiplier(containerConfig), Integer.MAX_VALUE));
        dimensionSlot.set(LogisticsCalculator.isDimensionEffective(containerConfig) ? 1 : 0);
    }

    private void cacheUpgradeStacks() {
        if (containerConfig == null) return;
        for (int i = 0; i < UPGRADE_SLOTS; i++)
            lastUpgradeStacks[i] = containerConfig.getUpgrades().getStackInSlot(i).copy();
        lastConfigGeneration = SLConfig.configGeneration.get();
    }

    private boolean hasUpgradeStacksChanged() {
        if (containerConfig == null) return false;
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            if (!ItemStack.matches(containerConfig.getUpgrades().getStackInSlot(i), lastUpgradeStacks[i]))
                return true;
        }
        return false;
    }

    @Override
    public void broadcastChanges() {
        if (player instanceof ServerPlayer) editableTargetSlot.set(canEditTarget() ? 1 : 0);
        if (observedRates != null && player instanceof ServerPlayer serverPlayer) {
            long tick = serverPlayer.server.overworld().getGameTime();
            if (tick >= nextRateSyncTick) {
                nextRateSyncTick = tick + 5;
                if (!hasTarget || !observedNode.equals(getTargetNode()) || resolveValidatedNode(serverPlayer) == null)
                    stopObservingRates();
                else {
                    refreshObservedPeers(serverPlayer);
                    PortPacketDistributor.sendToPlayer(serverPlayer,
                        new S2CTransferRatesPayload(containerId, observedNode, observedRates.snapshot(), transferDiagnostic(serverPlayer), observedPeersTruncated, peerRateSnapshot(serverPlayer), unavailableRatePeers(serverPlayer)));
                }
            }
        }
        if (hasUpgradeStacksChanged()
            || lastConfigGeneration != SLConfig.configGeneration.get()) {
            syncContainerSlots();
            cacheUpgradeStacks();
        }
        super.broadcastChanges();
    }

    private void refreshObservedPeers(ServerPlayer player) {
        var peers = GlobalLogisticsManager.get(player.server).getLinkedNodes(groupKey, observedNode)
            .stream().sorted(ConnectionKey.NODE_ORDER).toList();
        observedPeersTruncated = peers.size() > 256;
        var next = List.copyOf(peers.subList(0, Math.min(256, peers.size())));
        var previousSet = new HashSet<>(observedPeers);
        var nextSet = new HashSet<>(next);
        observedPeers.stream().filter(peer -> !nextSet.contains(peer)).forEach(observedRates::releasePeer);
        next.stream().filter(peer -> !previousSet.contains(peer)).forEach(observedRates::includePeer);
        observedPeers = next;
    }

    private Set<LogisticsNode> unavailableRatePeers(ServerPlayer player) {
        Set<LogisticsNode> result = new HashSet<>();
        for (LogisticsNode peer : observedPeers) {
            var level = player.server.getLevel(peer.gPos().dimension());
            var pos = peer.gPos().pos();
            if (level == null || !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4))
                result.add(peer);
            else {
                var config = LinkManager.get(level).getEndpointConfig(EndpointAddress.of(peer));
                if (config == null || !config.canPlayerAccess(player)) result.add(peer);
            }
        }
        return result;
    }

    private Map<LogisticsNode, Map<ResourceLocation, LiveTransferRates.Rate>> peerRateSnapshot(ServerPlayer player) {
        var available = observedRates.peerSnapshots();
        var unavailable = unavailableRatePeers(player);
        Map<LogisticsNode, Map<ResourceLocation, LiveTransferRates.Rate>> result = new LinkedHashMap<>();
        for (LogisticsNode peer : observedPeers)
            result.put(peer, unavailable.contains(peer) ? Map.of() : available.getOrDefault(peer, Map.of()));
        return result;
    }

    private String transferDiagnostic(ServerPlayer player) {
        var validated = resolveValidatedNode(player);
        if (validated == null) return "state:unknown";
        var config = validated.config();
        if (!config.linkConfig.isEnabled()) return "state:disabled";
        if (config.isGlobalOutputEnabled() && config.getSelectedTypeIds().isEmpty()) return "state:no_types";
        var peerLevel = player.server.getLevel(observedPeer.dimension());
        if (peerLevel == null || !peerLevel.getChunkSource().hasChunk(observedPeer.pos().getX() >> 4, observedPeer.pos().getZ() >> 4))
            return "state:unloaded";
        var container = config.getContainerConfig();
        if (container != null
            && !container.allowsRedstone(validated.level(), config.linkConfig.getRedstoneMode()))
            return "state:redstone";
        if (config.isGlobalOutputEnabled() && container != null) {
            boolean sameDimension = observedNode.gPos().dimension().equals(observedPeer.dimension());
            if (!sameDimension && !LogisticsCalculator.isDimensionEffective(container)) return "state:dimension";
            if (sameDimension && !LogisticsCalculator.isDimensionEffective(container)
                && LogisticsCalculator.getMaxTransferBlocks(container) != Integer.MAX_VALUE
                && !LogisticsCalculator.assessTransferRange(validated.level(), observedNode.gPos(), observedPeer, container).allowed())
                return "state:range";
        }
        // 只在另一端唯一时给出针对该端的结论；多面连接不能拿某一个面的状态代表全部。
        var peers = GlobalLogisticsManager.get(player.server).getLinkedNodes(groupKey, observedNode)
            .stream().filter(node -> node.gPos().equals(observedPeer)).toList();
        if (peers.size() == 1) {
            var peer = peers.get(0);
            var peerConfig = LinkManager.get(peerLevel).getEndpointConfig(EndpointAddress.of(peer));
            if (peerConfig != null) {
                if (!peerConfig.linkConfig.isEnabled()) return "state:peer_disabled";
                if (peerConfig.isGlobalOutputEnabled() && peerConfig.getSelectedTypeIds().isEmpty())
                    return "state:peer_no_types";
                var peerContainer = peerConfig.getContainerConfig();
                if (peerContainer != null && !peerContainer.allowsRedstone(peerLevel, peerConfig.linkConfig.getRedstoneMode()))
                    return "state:peer_redstone";
                if (peerConfig.isGlobalOutputEnabled() && peerContainer != null) {
                    boolean sameDimension = peer.gPos().dimension().equals(observedNode.gPos().dimension());
                    if (!sameDimension && !LogisticsCalculator.isDimensionEffective(peerContainer))
                        return "state:peer_dimension";
                    if (sameDimension && !LogisticsCalculator.isDimensionEffective(peerContainer)
                        && LogisticsCalculator.getMaxTransferBlocks(peerContainer) != Integer.MAX_VALUE
                        && !LogisticsCalculator.assessTransferRange(peerLevel, peer.gPos(), observedNode.gPos(), peerContainer).allowed())
                        return "state:peer_range";
                }
            }
        }
        if (TransferLogManager.get(player.server).recentlyObservedNoCandidate(observedNode, observedPeer))
            return "state:no_candidate";
        TransferEntry entry = TransferLogManager.get(player.server).latestFor(observedNode, observedPeer);
        return entry == null ? "" : entry.success() ? "success" : entry.failureReason() == null ? "failure" : entry.failureReason();
    }

    /// 槽位管理。

    private void addPlayerInventory(Inventory inv) {
        int x = MenuLayout.LINK_INVENTORY_X + MenuLayout.INVENTORY_SLOT_X;
        int ty = MenuLayout.LINK_INVENTORY_Y + MenuLayout.INVENTORY_SLOT_Y;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                this.addSlot(new Slot(inv, col + row * 9 + 9, x + col * 18, ty + row * 18));
        int hy = MenuLayout.LINK_INVENTORY_Y + MenuLayout.HOTBAR_SLOT_Y;
        for (int col = 0; col < 9; col++)
            this.addSlot(new Slot(inv, col, x + col * 18, hy));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.isActive() || !slot.mayPickup(player) || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem().copy();
        ItemStack result = stack.copy();

        if (index < TOTAL_CONFIG_SLOTS) {
            if (!moveItemStackTo(stack, INV_SLOT_START, HOTBAR_SLOT_END, true))
                return ItemStack.EMPTY;
        } else {
            if (!(stack.getItem() instanceof UpgradeItem)) return ItemStack.EMPTY;
            boolean moved = false;
            for (int i = 0; i < TOTAL_CONFIG_SLOTS; i++) {
                Slot configSlot = this.slots.get(i);
                if (configSlot.mayPlace(stack) && moveItemStackTo(stack, i, i + 1, false)) {
                    moved = true;
                    break;
                }
            }
            if (!moved) return ItemStack.EMPTY;
        }

        slot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
        if (stack.getCount() == result.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(getToolStack().getItem() instanceof LinkConfiguratorItem)) return false;
        if (!hasTarget) return true;
        if (!(player instanceof ServerPlayer serverPlayer)
        ) {
            return player.level().isClientSide;
        }
        return new NodeMutationService()
            .resolveRemoteView(serverPlayer, getTargetNode(), groupKey) != null;
    }

    public BlockPos getPos() {
        return pos;
    }

    public boolean hasTarget() {
        return hasTarget;
    }

    public void acknowledgeConfiguration() {
        acknowledgeConfiguration(0L);
    }

    public void acknowledgeEdit(long requestId, boolean accepted) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        syncFaceSlots();
        syncContainerSlots();
        broadcastChanges();
        PortPacketDistributor.sendToPlayer(serverPlayer,
            new S2CConfiguratorReadyPayload(containerId, requestId, accepted, selectedTypeIds));
    }

    public void acknowledgeConfiguration(long requestId) {
        acknowledgeConfiguration(requestId, true);
    }

    public void acknowledgeConfiguration(long requestId, boolean accepted) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        syncFaceSlots();
        syncContainerSlots();
        broadcastFullState();
        PortPacketDistributor.sendToPlayer(serverPlayer,
            new S2CConfiguratorReadyPayload(containerId, requestId, accepted, selectedTypeIds));
    }

    public ItemStack getToolStack() {
        if (toolSlot < 0 || toolSlot >= player.getInventory().getContainerSize()) {
            return ItemStack.EMPTY;
        }
        return player.getInventory().getItem(toolSlot);
    }

    public Direction getFace() {
        return face;
    }

    public ResourceKey<Level> getTargetDimension() {
        return targetDimension;
    }

    public LogisticsNode getTargetNode() {
        if (!hasTarget) {
            throw new IllegalStateException("No node is selected");
        }
        return new LogisticsNode(GlobalPos.of(targetDimension, pos), face, channel);
    }

    public GroupKey getRemoteGroupKey() {
        return groupKey;
    }

    public boolean isInputSideVisible() {
        return visibleSideSlot.get() == 1;
    }

    public boolean isOutputSideVisible() {
        return !isInputSideVisible();
    }

    /// 判断槽位是否属于过滤器或容器升级配置区。
    public boolean isConfigurationSlot(Slot slot) {
        int index = slots.indexOf(slot);
        return index >= 0 && index < TOTAL_CONFIG_SLOTS;
    }

    /// 客户端立即切换当前节点的显示身份，服务端随后发送权威槽位和数据槽内容。
    /// 该操作不会创建新的 Menu，因此不会重置鼠标位置或预览画布状态。
    public void selectTarget(
        LogisticsNode node,
        GroupKey groupKey,
        boolean inputSide,
        List<ResourceLocation> selectedTypeIds
    ) {
        bindTargetIdentity(node, groupKey, inputSide);
        faceConfig = null;
        containerConfig = null;
        filterHandler.clear();
        containerHandler.clear();
        clearSyncedConfiguration();
        this.selectedTypeIds = TransferTypeSelection.sanitize(selectedTypeIds);

    }

    /// 服务端在权限验证完成后，把现有 Menu 重新绑定到目标节点。
    public void selectTarget(
        NodeMutationService.ValidatedNode node,
        GroupKey groupKey,
        boolean inputSide
    ) {
        LogisticsNode target = node.node();
        bindTargetIdentity(target, groupKey, inputSide);
        faceConfig = node.config();
        containerConfig = node.manager().getContainerConfig(node.pos());
        if (containerConfig == null && canEditTarget())
            containerConfig = node.manager().getOrCreateContainerConfig(node.pos());
        filterHandler.bind(faceConfig.filterConfig.getUpgrades());
        if (containerConfig != null) containerHandler.bind(containerConfig.getUpgrades());
        else containerHandler.clear();
        syncFaceSlots();
        syncContainerSlots();
        cacheUpgradeStacks();
    }

    /// 解除当前节点绑定，但保留连接配置器本身及玩家物品栏槽位。
    public void clearTarget() {
        stopObservingRates();
        pos = null;
        face = null;
        targetDimension = null;
        groupKey = null;
        hasTarget = false;
        faceConfig = null;
        containerConfig = null;
        visibleSideSlot.set(1);
        filterHandler.clear();
        containerHandler.clear();
        clearSyncedConfiguration();
    }

    private void bindTargetIdentity(
        LogisticsNode node,
        GroupKey groupKey,
        boolean inputSide
    ) {
        if (!hasTarget || !node.equals(getTargetNode())) stopObservingRates();
        this.groupKey = groupKey;
        pos = node.gPos().pos();
        face = node.face();
        channel = node.channel();
        if (channel >= 0) getToolStack().set(SLDataComponents.SELECTED_CHANNEL.get(), channel);
        targetDimension = node.gPos().dimension();
        hasTarget = true;
        visibleSideSlot.set(inputSide ? 1 : 0);
    }

    private void clearSyncedConfiguration() {
        redstoneSlot.set(0);
        enabledSlot.set(0);
        globalInputSlot.set(0);
        globalOutputSlot.set(0);
        strategySlot.set(0);
        extractionModeSlot.set(0);
        prioritySlot.set(0);
        keepStockSlot.set(0);

        speedMultSlot.set(0);
        rangeMultSlot.set(0);
        stackMultSlot.set(0);
        dimensionSlot.set(0);
        selectedTypeIds = List.of();
        Arrays.fill(lastUpgradeStacks, ItemStack.EMPTY);
    }

    /// 在当前菜单内切换所查看的连接面方向，不重新创建容器界面。
    public void selectVisibleSide(boolean inputSide) {
        visibleSideSlot.set(inputSide ? 1 : 0);
    }

    /// 双向通道同时展示两侧配置；单向通道保留各自紧凑布局。
    public boolean showsInputSettings() {
        return isInputSideVisible() || isGlobalInputEnabled() && isGlobalOutputEnabled();
    }

    public boolean showsOutputSettings() {
        return isOutputSideVisible() || isGlobalInputEnabled() && isGlobalOutputEnabled();
    }

    public boolean allowsEdit(ChannelConfigurationEdit edit) {
        if (!hasTarget || channel < 0) return false;
        if (edit instanceof ChannelConfigurationEdit.FilterEdit) return true;
        if (edit instanceof ChannelConfigurationEdit.NumberEdit) return showsInputSettings();
        if (edit instanceof ChannelConfigurationEdit.RedstoneEdit || edit instanceof ChannelConfigurationEdit.EnabledEdit || edit instanceof ChannelConfigurationEdit.RoleEdit)
            return true;
        if (!showsOutputSettings()) {
            return edit instanceof ChannelConfigurationEdit.BooleanEdit inputToggle
                && inputToggle.field() == ChannelConfigurationEdit.BooleanField.GLOBAL_INPUT;
        }
        return edit instanceof ChannelConfigurationEdit.BooleanEdit outputToggle
            && outputToggle.field() == ChannelConfigurationEdit.BooleanField.GLOBAL_OUTPUT
            || edit instanceof ChannelConfigurationEdit.StrategyEdit
            || edit instanceof ChannelConfigurationEdit.ExtractionEdit
            || edit instanceof ChannelConfigurationEdit.SelectedTypesEdit
            || edit instanceof ChannelConfigurationEdit.TypePatchEdit;
    }

    public NodeMutationService.ValidatedNode
    resolveValidatedNode(ServerPlayer serverPlayer) {
        return new NodeMutationService()
            .resolveRemote(serverPlayer, getTargetNode(), groupKey);
    }

    public EndpointConfig getEndpointConfig() {
        return faceConfig;
    }

    public ContainerConfig getContainerConfig() {
        return containerConfig;
    }

    private class FilterSlot extends SlotItemHandler {
        private final UpgradeType[] allowedTypes;

        FilterSlot(IItemHandler handler, int index, int x, int y, UpgradeType... allowed) {
            super(handler, index, x, y);
            this.allowedTypes = allowed;
        }

        @Override
        public boolean isActive() {
            return !clientBatchSelection && hasTarget && channel >= 0;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!isActive() || !canEditTarget()) return false;
            if (!(stack.getItem() instanceof UpgradeItem upg)) return false;
            for (UpgradeType t : allowedTypes) if (upg.getType() == t) return true;
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return isActive() && canEditTarget();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }

    private class UpgradeSlot extends SlotItemHandler {
        private final UpgradeType[] allowedTypes;

        UpgradeSlot(IItemHandler handler, int index, int x, int y, UpgradeType... allowed) {
            super(handler, index, x, y);
            this.allowedTypes = allowed;
        }

        @Override
        public boolean isActive() {
            return !SLConfig.isSimpleMode() && !clientBatchSelection && hasTarget
                && (channel < 0 || showsOutputSettings());
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!isActive() || !canEditTarget()) return false;
            if (!(stack.getItem() instanceof UpgradeItem upg)) return false;
            for (UpgradeType t : allowedTypes) if (upg.getType() == t) return true;
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return isActive() && canEditTarget();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }

    /// 槽位对象保持不变，仅切换实际数据源，避免选择节点时重建容器菜单。
    private static final class SwitchingItemHandler
        implements IItemHandlerModifiable {
        private final int slotCount;
        private IItemHandlerModifiable delegate;

        private SwitchingItemHandler(int slotCount) {
            this.slotCount = slotCount;
            clear();
        }

        private void bind(IItemHandlerModifiable delegate) {
            if (delegate.getSlots() != slotCount) {
                throw new IllegalArgumentException(
                    "Item handler slot count does not match");
            }
            this.delegate = delegate;
        }

        private void clear() {
            delegate = new ItemStackHandler(slotCount);
        }

        @Override
        public int getSlots() {
            return slotCount;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return delegate.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return delegate.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return delegate.isItemValid(slot, stack);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            delegate.setStackInSlot(slot, stack);
        }
    }

    private record OpenData(
        BlockPos pos,
        Direction face,
        ResourceKey<Level> dimension,
        GroupKey groupKey,
        boolean inputSide,
        int channel,
        List<ResourceLocation> initialTypeIds,
        int toolSlot
    ) {
    }

    public static int findToolSlot(Inventory inventory) {
        int selected = inventory.selected;
        if (inventory.getItem(selected).getItem() instanceof LinkConfiguratorItem) {
            return selected;
        }
        int offhand = 40;
        if (offhand < inventory.getContainerSize()
            && inventory.getItem(offhand).getItem() instanceof LinkConfiguratorItem) {
            return offhand;
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).getItem() instanceof LinkConfiguratorItem) {
                return slot;
            }
        }
        return -1;
    }
}
