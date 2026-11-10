package com.lach.ioverseer.client;

import com.lach.ioverseer.machine.MachineStatus;
import com.lach.ioverseer.net.Net;

/**
 * Lays out the held tablet's live screen in texture pixels (8 x 7, y down). Version-specific renderers supply the
 * quad and text drawing.
 */
public final class PreviewLayout {
    public static final float W = 8, H = 7;
    /** Screen rectangle on the 16 px tablet texture. */
    public static final float SX = 4, SY = 4;

    public interface Painter {
        void quad(float x0, float y0, float x1, float y1, int argb, int layer);

        /** Text at (x, y) with one glyph pixel = scale texture pixels. */
        void text(String s, float x, float y, float scale, int argb);

        int width(String s);
    }

    private PreviewLayout() {}

    public static void paint(Painter p) {
        long ms = System.currentTimeMillis();
        p.quad(0, 0, W, H, Theme.SCREEN_BG, 0);
        Net.Preview d = TabletPreview.live();
        if (d == null) {
            String s = "NO SIGNAL";
            float sc = 0.11f;
            p.text(s, (W - p.width(s) * sc) / 2, H / 2 - 0.5f, sc, (ms / 500) % 2 == 0 ? Theme.AMBER : Theme.AMBER_DIM);
            float sweep = (ms % 2000) / 2000f * H;
            p.quad(0, sweep, W, Math.min(H, sweep + 0.25f), 0x30FFC24A, 1);
            return;
        }
        // power draw
        String v = TabletPreview.fmt(d.draw()), unit = " FE/t";
        float sc = Math.min(0.17f, (W - 1.2f) / (p.width(v + unit)));
        p.text(v, 0.4f, 0.35f, sc, Theme.AMBER);
        p.text(unit, 0.4f + p.width(v) * sc, 0.35f, sc, Theme.AMBER_DIM);
        // live blink
        if ((ms / 500) % 2 == 0) p.quad(W - 0.75f, 0.45f, W - 0.35f, 0.85f, 0xFF46C46A, 1);

        // draw graph, newest on the right
        float gx0 = 0.4f, gx1 = W - 0.4f, gy0 = 2.0f, gy1 = 4.7f;
        for (float gy = gy0; gy < gy1; gy += 0.9f) p.quad(gx0, gy, gx1, gy + 0.05f, 0xFF1E1708, 1);
        long[] h = TabletPreview.history();
        int n = TabletPreview.count();
        long max = 1;
        for (long x : h) max = Math.max(max, x);
        max = max + max / 3; // headroom so a steady draw still reads as a graph
        float bw = (gx1 - gx0) / TabletPreview.HISTORY;
        for (int i = TabletPreview.HISTORY - n; i < TabletPreview.HISTORY; i++) {
            float bh = h[i] <= 0 ? 0.06f : Math.max(0.12f, h[i] / (float) max * (gy1 - gy0));
            float bx = gx0 + i * bw;
            int c = i == TabletPreview.HISTORY - 1 ? Theme.GOLD_HI : Theme.GOLD;
            p.quad(bx + 0.04f, gy1 - bh, bx + bw - 0.04f, gy1, c, 2);
        }
        p.quad(gx0, gy1, gx1, gy1 + 0.08f, Theme.AMBER_DIM, 2);

        // stored energy
        float ey0 = 5.1f, ey1 = 5.6f;
        p.quad(gx0, ey0, gx1, ey1, 0xFF2A2D33, 1);
        if (d.energyMax() > 0) {
            float f = Math.min(1f, d.energy() / (float) d.energyMax());
            p.quad(gx0, ey0, gx0 + (gx1 - gx0) * f, ey1, 0xFFE5484D, 2);
        }

        // status mix
        int total = d.working() + d.idle() + d.full() + d.noPower();
        float sy0 = 6.05f, sy1 = 6.55f;
        if (total == 0) {
            p.quad(gx0, sy0, gx1, sy1, 0xFF3A3E46, 1);
            return;
        }
        int[] n4 = {d.working(), d.idle(), d.full(), d.noPower()};
        int[] col = {MachineStatus.WORKING.color, MachineStatus.IDLE.color, MachineStatus.OUTPUT_FULL.color, MachineStatus.NO_POWER.color};
        float x = gx0;
        for (int i = 0; i < 4; i++) {
            if (n4[i] == 0) continue;
            float w = (gx1 - gx0) * n4[i] / total;
            // a problem segment pulses so it catches the eye
            int c = col[i];
            if (i >= 2 && (ms / 400) % 2 == 0) c = (c & 0x00FFFFFF) | 0x99000000;
            p.quad(x, sy0, x + w, sy1, c, 2);
            x += w;
        }
    }
}
