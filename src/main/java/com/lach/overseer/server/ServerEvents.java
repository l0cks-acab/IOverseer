package com.lach.overseer.server;

import com.lach.overseer.machine.Machines;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        Subscriptions.tick(e.getServer());
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        Subscriptions.forget(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent e) {
        Machines.clear();
    }
}
