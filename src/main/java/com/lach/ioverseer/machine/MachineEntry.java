package com.lach.ioverseer.machine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** One machine row as sent to clients. */
public record MachineEntry(ResourceLocation dim, BlockPos pos, ResourceLocation block, MachineStatus status,
                           long energy, long energyMax, long fluid, long fluidMax,
                           int slotsUsed, int slots, boolean pinned, java.util.List<Gauge> gauges) {

    public static final Codec<MachineEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("dim").forGetter(MachineEntry::dim),
            BlockPos.CODEC.fieldOf("pos").forGetter(MachineEntry::pos),
            ResourceLocation.CODEC.fieldOf("block").forGetter(MachineEntry::block),
            Codec.INT.xmap(MachineStatus::byId, MachineStatus::ordinal).fieldOf("status").forGetter(MachineEntry::status),
            Codec.LONG.fieldOf("e").forGetter(MachineEntry::energy),
            Codec.LONG.fieldOf("em").forGetter(MachineEntry::energyMax),
            Codec.LONG.fieldOf("f").forGetter(MachineEntry::fluid),
            Codec.LONG.fieldOf("fm").forGetter(MachineEntry::fluidMax),
            Codec.INT.fieldOf("su").forGetter(MachineEntry::slotsUsed),
            Codec.INT.fieldOf("s").forGetter(MachineEntry::slots),
            Codec.BOOL.fieldOf("p").forGetter(MachineEntry::pinned),
            Gauge.CODEC.listOf().optionalFieldOf("g", java.util.List.of()).forGetter(MachineEntry::gauges)
    ).apply(i, MachineEntry::new));

    public static final StreamCodec<FriendlyByteBuf, MachineEntry> STREAM_CODEC = StreamCodec.of(
            (buf, e) -> {
                buf.writeResourceLocation(e.dim);
                buf.writeBlockPos(e.pos);
                buf.writeResourceLocation(e.block);
                buf.writeByte(e.status.ordinal());
                buf.writeVarLong(e.energy);
                buf.writeVarLong(e.energyMax);
                buf.writeVarLong(e.fluid);
                buf.writeVarLong(e.fluidMax);
                buf.writeVarInt(e.slotsUsed);
                buf.writeVarInt(e.slots);
                buf.writeBoolean(e.pinned);
                buf.writeVarInt(e.gauges.size());
                for (Gauge g : e.gauges) Gauge.STREAM_CODEC.encode(buf, g);
            },
            buf -> new MachineEntry(buf.readResourceLocation(), buf.readBlockPos(), buf.readResourceLocation(),
                    MachineStatus.byId(buf.readByte()), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                    buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), readGauges(buf)));

    private static java.util.List<Gauge> readGauges(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 16);
        java.util.List<Gauge> out = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(Gauge.STREAM_CODEC.decode(buf));
        return out;
    }

    public MachineEntry withPinned(boolean p) {
        return new MachineEntry(dim, pos, block, status, energy, energyMax, fluid, fluidMax, slotsUsed, slots, p, gauges);
    }

    public boolean hasEnergy() { return energyMax > 0; }
    public boolean hasFluid() { return fluidMax > 0; }
    public boolean hasItems() { return slots > 0; }
}
