package com.coobird.staticlogistics.storage.config;

import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.config.serializer.ConfigSerializer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class FaceConfigComposite {
    public final FaceConfig faceConfig;
    public final LinkConfig linkConfig;
    public final FilterConfig filterConfig;
    public ContainerConfig sharedContainerConfig;

    private int selectedTypesMask = 0;
    private int version = 0;
    private Consumer<FaceConfigComposite> onDirty = (c) -> {
    };

    public FaceConfigComposite() {
        this.faceConfig = new FaceConfig();
        this.linkConfig = new LinkConfig();
        this.filterConfig = new FilterConfig();
        setupDirtyCallback();
    }

    private void setupDirtyCallback() {
        this.faceConfig.setOnDirty(c -> markDirty());
        this.linkConfig.setOnDirty(c -> markDirty());
        this.filterConfig.setOnDirty(c -> markDirty());
    }

    public void markDirty() {
        version++;
        if (onDirty != null) onDirty.accept(this);
    }

    public void setOnDirty(@Nullable Consumer<FaceConfigComposite> onDirty) {
        this.onDirty = onDirty;
    }

    public NodeRole determineRole() {
        return linkConfig.determineRole();
    }

    public int getTransferLimit(TransferType type) {
        if (sharedContainerConfig == null) {
            return Math.min(type.baseStackSize(), SLConfig.getMaxTransferLimit());
        }
        int stackMult = sharedContainerConfig.getStackMultiplier();
        long limit = (long) type.baseStackSize() * stackMult;
        int maxAllowed = SLConfig.getMaxTransferLimit();
        if (limit > maxAllowed) {
            return maxAllowed;
        }
        return (int) limit;
    }

    public int getVersion() {
        return version;
    }

    void setVersion(int version) {
        this.version = version;
    }

    public CompoundTag serializeNBT(HolderLookup.Provider p) {
        CompoundTag tag = ConfigSerializer.serializeNBT(this, p);
        tag.putInt("version", version);
        return tag;
    }

    public void deserializeNBT(HolderLookup.Provider p, CompoundTag nbt) {
        ConfigSerializer.deserializeNBT(this, p, nbt);
        if (nbt.contains("version")) {
            version = nbt.getInt("version");
        }
    }

    public boolean isDefault() {
        return faceConfig.isDefault() && linkConfig.isDefault() && filterConfig.isDefault() &&
            (sharedContainerConfig == null || sharedContainerConfig.isDefault());
    }

    public int getSelectedTypesMask() {
        return selectedTypesMask;
    }

    public void setSelectedTypesMask(int mask) {
        this.selectedTypesMask = mask;
        markDirty();
    }

    public boolean isTypeSelected(TransferType type) {
        return (selectedTypesMask & type.getFlag()) != 0;
    }
}