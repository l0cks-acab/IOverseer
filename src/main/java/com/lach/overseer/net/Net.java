package com.lach.overseer.net;

import com.lach.overseer.Overseer;
import com.lach.overseer.machine.MachineEntry;
import com.lach.overseer.server.Subscriptions;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;
import java.util.Optional;

public final class Net {
    private Net() {}

    /** C2S: start watching. Empty screen pos = tablet, else the Monitor Screen at that pos. */
    public record Watch(Optional<BlockPos> screen) implements CustomPacketPayload {
        public static final Type<Watch> TYPE = new Type<>(Overseer.id("watch"));
        public static final StreamCodec<ByteBuf, Watch> CODEC = ByteBufCodecs.optional(BlockPos.STREAM_CODEC).map(Watch::new, Watch::screen);
        @Override public Type<Watch> type() { return TYPE; }
    }

    /** C2S: stop watching. */
    public record Unwatch() implements CustomPacketPayload {
        public static final Type<Unwatch> TYPE = new Type<>(Overseer.id("unwatch"));
        public static final StreamCodec<ByteBuf, Unwatch> CODEC = StreamCodec.unit(new Unwatch());
        @Override public Type<Unwatch> type() { return TYPE; }
    }

    /** C2S: toggle a pin on the player's tablet. */
    public record TogglePin(GlobalPos pos) implements CustomPacketPayload {
        public static final Type<TogglePin> TYPE = new Type<>(Overseer.id("toggle_pin"));
        public static final StreamCodec<ByteBuf, TogglePin> CODEC = GlobalPos.STREAM_CODEC.map(TogglePin::new, TogglePin::pos);
        @Override public Type<TogglePin> type() { return TYPE; }
    }

    /** C2S: flip the live preview on the player's tablet. */
    public record TogglePreview() implements CustomPacketPayload {
        public static final Type<TogglePreview> TYPE = new Type<>(Overseer.id("toggle_preview"));
        public static final StreamCodec<ByteBuf, TogglePreview> CODEC = StreamCodec.unit(new TogglePreview());
        @Override public Type<TogglePreview> type() { return TYPE; }
    }

    /** S2C: summary for the held tablet's screen. Counts are working, idle, output full, no power. */
    public record Preview(int working, int idle, int full, int noPower, long energy, long energyMax, long draw) implements CustomPacketPayload {
        public static final Type<Preview> TYPE = new Type<>(Overseer.id("preview"));
        public static final StreamCodec<ByteBuf, Preview> CODEC = StreamCodec.of(
                (buf, p) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, p.working);
                    ByteBufCodecs.VAR_INT.encode(buf, p.idle);
                    ByteBufCodecs.VAR_INT.encode(buf, p.full);
                    ByteBufCodecs.VAR_INT.encode(buf, p.noPower);
                    ByteBufCodecs.VAR_LONG.encode(buf, p.energy);
                    ByteBufCodecs.VAR_LONG.encode(buf, p.energyMax);
                    ByteBufCodecs.VAR_LONG.encode(buf, p.draw);
                },
                buf -> new Preview(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
                        ByteBufCodecs.VAR_LONG.decode(buf)));
        @Override public Type<Preview> type() { return TYPE; }
    }

    /** S2C: machine list. */
    public record Snapshot(List<MachineEntry> entries, int radius) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(Overseer.id("snapshot"));
        public static final StreamCodec<FriendlyByteBuf, Snapshot> CODEC = StreamCodec.composite(
                MachineEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), Snapshot::entries,
                ByteBufCodecs.VAR_INT, Snapshot::radius,
                Snapshot::new);
        @Override public Type<Snapshot> type() { return TYPE; }
    }

    /** S2C: a pinned machine just went red or orange. */
    public record Alert(MachineEntry entry) implements CustomPacketPayload {
        public static final Type<Alert> TYPE = new Type<>(Overseer.id("alert"));
        public static final StreamCodec<FriendlyByteBuf, Alert> CODEC = MachineEntry.STREAM_CODEC.map(Alert::new, Alert::entry);
        @Override public Type<Alert> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(Watch.TYPE, cast(Watch.CODEC), (p, ctx) -> ctx.enqueueWork(() -> Subscriptions.watch((ServerPlayer) ctx.player(), p.screen())));
        r.playToServer(Unwatch.TYPE, cast(Unwatch.CODEC), (p, ctx) -> ctx.enqueueWork(() -> Subscriptions.unwatch((ServerPlayer) ctx.player())));
        r.playToServer(TogglePin.TYPE, cast(TogglePin.CODEC), (p, ctx) -> ctx.enqueueWork(() -> Subscriptions.togglePin((ServerPlayer) ctx.player(), p.pos())));
        r.playToServer(TogglePreview.TYPE, cast(TogglePreview.CODEC), (p, ctx) -> ctx.enqueueWork(() -> Subscriptions.togglePreview((ServerPlayer) ctx.player())));
        r.playToClient(Preview.TYPE, cast(Preview.CODEC), (p, ctx) -> ctx.enqueueWork(() -> com.lach.overseer.client.ClientHooks.preview(p)));
        r.playToClient(Snapshot.TYPE, cast(Snapshot.CODEC), (p, ctx) -> ctx.enqueueWork(() -> com.lach.overseer.client.ClientHooks.snapshot(p)));
        r.playToClient(Alert.TYPE, cast(Alert.CODEC), (p, ctx) -> ctx.enqueueWork(() -> com.lach.overseer.client.ClientHooks.alert(p)));
    }

    @SuppressWarnings("unchecked")
    private static <T> StreamCodec<RegistryFriendlyByteBuf, T> cast(StreamCodec<? super RegistryFriendlyByteBuf, T> c) {
        return (StreamCodec<RegistryFriendlyByteBuf, T>) c;
    }
}
