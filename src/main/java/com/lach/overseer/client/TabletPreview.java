package com.lach.overseer.client;

import com.lach.overseer.Registry;
import com.lach.overseer.net.Net;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Latest summary for the held tablet's screen, plus a short history of the power draw for its graph. */
public final class TabletPreview {
    public static final int HISTORY = 16;
    private static final long[] DRAW = new long[HISTORY];
    private static int count;
    private static Net.Preview latest;
    private static long receivedAt;

    private TabletPreview() {}

    public static void accept(Net.Preview p) {
        System.arraycopy(DRAW, 1, DRAW, 0, HISTORY - 1);
        DRAW[HISTORY - 1] = p.draw();
        count = Math.min(HISTORY, count + 1);
        latest = p;
        receivedAt = System.currentTimeMillis();
    }

    /** Null when nothing arrived for a few seconds. */
    public static Net.Preview live() {
        if (latest == null || System.currentTimeMillis() - receivedAt > 3500) {
            count = 0;
            return null;
        }
        return latest;
    }

    public static long[] history() {
        return DRAW;
    }

    public static int count() {
        return count;
    }

    /** Fraction of the way to the next sample, for smooth scrolling. */
    public static float phase() {
        return Math.min(1f, (System.currentTimeMillis() - receivedAt) / 1000f);
    }

    public static boolean enabled(ItemStack tablet) {
        return tablet.getOrDefault(Registry.PREVIEW.get(), true);
    }

    private static Boolean pending;

    public static boolean enabled(Player p) {
        if (pending != null) {
            if (enabled(find(p)) == pending) pending = null;
            else return pending;
        }
        return enabled(find(p));
    }

    public static void toggle(Player p) {
        pending = !enabled(p);
        PacketDistributor.sendToServer(new Net.TogglePreview());
    }

    private static ItemStack find(Player p) {
        for (InteractionHand h : InteractionHand.values()) if (p.getItemInHand(h).is(Registry.TABLET.get())) return p.getItemInHand(h);
        for (ItemStack s : p.getInventory().items) if (s.is(Registry.TABLET.get())) return s;
        return ItemStack.EMPTY;
    }

    public static String fmt(long v) {
        if (v >= 1_000_000_000) return String.format(java.util.Locale.ROOT, "%.1fG", v / 1e9);
        if (v >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", v / 1e6);
        if (v >= 10_000) return (v / 1000) + "k";
        if (v >= 1_000) return String.format(java.util.Locale.ROOT, "%.1fk", v / 1e3);
        return Long.toString(v);
    }
}
