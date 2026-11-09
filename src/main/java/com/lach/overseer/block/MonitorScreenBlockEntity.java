package com.lach.overseer.block;

import com.lach.overseer.Config;
import com.lach.overseer.Registry;
import com.lach.overseer.machine.MachineEntry;
import com.lach.overseer.machine.Machines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MonitorScreenBlockEntity extends BlockEntity {
    private List<MachineEntry> entries = List.of();
    private int tick;

    public MonitorScreenBlockEntity(BlockPos pos, BlockState state) {
        super(Registry.SCREEN_BE.get(), pos, state);
    }

    public List<MachineEntry> entries() {
        return entries;
    }

    void serverTick() {
        if (++tick % 20 != 0 || !(level instanceof ServerLevel sl)) return;
        if (!sl.hasNearbyAlivePlayer(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, 32)) return;
        List<MachineEntry> list = new ArrayList<>(Machines.scan(sl, worldPosition, Config.SCAN_RADIUS.get(), List.of()));
        list.sort(Comparator.comparingInt((MachineEntry e) -> e.status().ordinal()).thenComparingDouble(e -> e.pos().distSqr(worldPosition)));
        if (!list.equals(entries)) {
            entries = List.copyOf(list);
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider regs) {
        CompoundTag t = new CompoundTag();
        t.put("entries", MachineEntry.CODEC.listOf().encodeStart(NbtOps.INSTANCE, entries).getOrThrow());
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.loadAdditional(tag, regs);
        if (tag.contains("entries", Tag.TAG_LIST)) {
            entries = MachineEntry.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("entries")).result().orElse(List.of());
        }
    }
}
