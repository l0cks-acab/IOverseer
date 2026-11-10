package com.lach.ioverseer.server;

import com.lach.ioverseer.Config;
import com.lach.ioverseer.Registry;
import com.lach.ioverseer.block.MonitorScreenBlockEntity;
import com.lach.ioverseer.machine.MachineEntry;
import com.lach.ioverseer.machine.MachineStatus;
import com.lach.ioverseer.machine.Machines;
import com.lach.ioverseer.net.Net;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/** Players with an open Overseer screen get a fresh snapshot every second. Pinned machines raise alerts. */
public final class Subscriptions {
    private record Sub(Optional<BlockPos> screen) {}

    private static final Map<UUID, Sub> SUBS = new HashMap<>();
    private static final Map<UUID, Map<GlobalPos, MachineStatus>> LAST = new HashMap<>();

    private Subscriptions() {}

    public static void watch(ServerPlayer p, Optional<BlockPos> screen) {
        if (screen.isPresent()) {
            BlockPos pos = screen.get();
            if (p.distanceToSqr(pos.getCenter()) > 64 || !(p.level().getBlockEntity(pos) instanceof MonitorScreenBlockEntity)) return;
        } else if (findTablet(p).isEmpty()) {
            return;
        }
        SUBS.put(p.getUUID(), new Sub(screen));
        send(p, SUBS.get(p.getUUID()));
    }

    public static void unwatch(ServerPlayer p) {
        SUBS.remove(p.getUUID());
    }

    public static void forget(UUID id) {
        SUBS.remove(id);
        LAST.remove(id);
    }

    public static void togglePin(ServerPlayer p, GlobalPos pos) {
        ItemStack tablet = findTablet(p);
        if (tablet.isEmpty()) {
            p.sendOverlayMessage(Component.translatable("ioverseer.msg.need_tablet"));
            return;
        }
        boolean pinned = togglePin(tablet, pos);
        p.sendOverlayMessage(Component.translatable(pinned ? "ioverseer.msg.pinned" : "ioverseer.msg.unpinned"));
        Sub s = SUBS.get(p.getUUID());
        if (s != null) send(p, s);
    }

    /** @return true if now pinned */
    public static boolean togglePin(ItemStack tablet, GlobalPos pos) {
        List<GlobalPos> pins = new ArrayList<>(tablet.getOrDefault(Registry.PINS.get(), List.of()));
        boolean now;
        if (pins.remove(pos)) now = false;
        else {
            if (pins.size() >= Config.MAX_PINS.get()) pins.remove(0);
            pins.add(pos);
            now = true;
        }
        tablet.set(Registry.PINS.get(), List.copyOf(pins));
        return now;
    }

    public static ItemStack findTablet(ServerPlayer p) {
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack s = p.getItemInHand(h);
            if (s.is(Registry.TABLET.get())) return s;
        }
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (s.is(Registry.TABLET.get())) return s;
        return ItemStack.EMPTY;
    }

    public static List<GlobalPos> pins(ServerPlayer p) {
        ItemStack t = findTablet(p);
        return t.isEmpty() ? List.of() : t.getOrDefault(Registry.PINS.get(), List.of());
    }

    private static void send(ServerPlayer p, Sub s) {
        ServerLevel level = (ServerLevel) p.level();
        int r = Config.SCAN_RADIUS.get();
        BlockPos center = s.screen.orElse(p.blockPosition());
        List<MachineEntry> list = Machines.scan(level, center, r, pins(p));
        PacketDistributor.sendToPlayer(p, new Net.Snapshot(list, r));
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (Iterator<Map.Entry<UUID, Sub>> it = SUBS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Sub> en = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p == null) { it.remove(); continue; }
            Sub s = en.getValue();
            if (s.screen.isPresent() && p.distanceToSqr(s.screen.get().getCenter()) > 144) { it.remove(); continue; }
            if (s.screen.isEmpty() && findTablet(p).isEmpty()) { it.remove(); continue; }
            send(p, s);
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            alerts(p);
            preview(p);
        }
    }

    public static boolean previewOn(ItemStack tablet) {
        return tablet.getOrDefault(Registry.PREVIEW.get(), true);
    }

    public static void togglePreview(ServerPlayer p) {
        ItemStack tablet = findTablet(p);
        if (tablet.isEmpty()) return;
        tablet.set(Registry.PREVIEW.get(), !previewOn(tablet));
    }

    /** Players holding a tablet with the preview on get a summary for its screen every second. */
    private static void preview(ServerPlayer p) {
        ItemStack held = ItemStack.EMPTY;
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack s = p.getItemInHand(h);
            if (s.is(Registry.TABLET.get())) {
                held = s;
                break;
            }
        }
        if (held.isEmpty() || !previewOn(held)) return;
        ServerLevel level = (ServerLevel) p.level();
        List<MachineEntry> list = Machines.scan(level, p.blockPosition(), Config.SCAN_RADIUS.get(), List.of());
        int[] n = new int[4];
        long e = 0, em = 0, draw = 0;
        for (MachineEntry m : list) {
            switch (m.status()) {
                case WORKING -> n[0]++;
                case IDLE, CHECKING -> n[1]++;
                case OUTPUT_FULL -> n[2]++;
                case NO_POWER -> n[3]++;
                default -> {}
            }
            e += m.energy();
            em += m.energyMax();
            draw += Machines.draw(level, m);
        }
        PacketDistributor.sendToPlayer(p, new Net.Preview(n[0], n[1], n[2], n[3], e, em, draw));
    }

    private static void alerts(ServerPlayer p) {
        List<GlobalPos> pins = pins(p);
        if (pins.isEmpty()) { LAST.remove(p.getUUID()); return; }
        Map<GlobalPos, MachineStatus> last = LAST.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        last.keySet().retainAll(pins);
        for (GlobalPos gp : pins) {
            ServerLevel lvl = p.level().getServer().getLevel(gp.dimension());
            if (lvl == null || !lvl.isLoaded(gp.pos())) continue;
            var be = lvl.getBlockEntity(gp.pos());
            if (be == null) continue;
            MachineEntry e = Machines.sample(lvl, be).withPinned(true);
            MachineStatus prev = last.put(gp, e.status());
            if (e.status().isProblem() && prev != null && prev != e.status()) {
                PacketDistributor.sendToPlayer(p, new Net.Alert(e));
            }
        }
    }
}
