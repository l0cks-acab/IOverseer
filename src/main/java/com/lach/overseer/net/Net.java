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
        r.playToClient(Snapshot.TYPE, cast(Snapshot.CODEC), (p, ctx) -> ctx.enqueueWork(() -> com.lach.overseer.client.ClientHooks.snapshot(p)));
        r.playToClient(Alert.TYPE, cast(Alert.CODEC), (p, ctx) -> ctx.enqueueWork(() -> com.lach.overseer.client.ClientHooks.alert(p)));
    }

    @SuppressWarnings("unchecked")
    private static <T> StreamCodec<RegistryFriendlyByteBuf, T> cast(StreamCodec<? super RegistryFriendlyByteBuf, T> c) {
        return (StreamCodec<RegistryFriendlyByteBuf, T>) c;
    }
}
