package com.lach.overseer.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Mekanism-style bevelled steel panels with AE2-style circuit lines, in gold. */
public final class Theme {
    public static final int STEEL_DARK = 0xFF1B1D21;
    public static final int STEEL = 0xFF2B2E34;
    public static final int STEEL_LIGHT = 0xFF464A52;
    public static final int STEEL_HI = 0xFF5E636D;
    public static final int GOLD = 0xFFE8B23A;
    public static final int GOLD_DIM = 0xFF8C6418;
    public static final int GOLD_HI = 0xFFFFD877;
    public static final int SCREEN_BG = 0xFF0C0B08;
    public static final int AMBER = 0xFFFFC24A;
    public static final int AMBER_DIM = 0xFFA77A26;
    public static final int TEXT = 0xFFE9E2D0;

    private Theme() {}

    /** Outer machine panel: dark rim, raised bevel, gold trim, corner bolts. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, STEEL_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, STEEL);
        // bevel
        g.fill(x + 1, y + 1, x + w - 1, y + 2, STEEL_HI);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, STEEL_HI);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, STEEL_DARK);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, STEEL_DARK);
        // gold trim inset
        outline(g, x + 3, y + 3, w - 6, h - 6, GOLD_DIM);
        // corner bolts
        bolt(g, x + 5, y + 5);
        bolt(g, x + w - 8, y + 5);
        bolt(g, x + 5, y + h - 8);
        bolt(g, x + w - 8, y + h - 8);
    }

    /** Recessed screen well with faint gold grid, like an AE2 terminal. */
    public static void screen(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, STEEL_DARK);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, STEEL_HI);
        g.fill(x + w, y - 1, x + w + 1, y + h + 1, STEEL_HI);
        g.fill(x, y, x + w, y + h, SCREEN_BG);
        int grid = 0x0EE8B23A;
        for (int gx = x + 8; gx < x + w; gx += 8) g.fill(gx, y, gx + 1, y + h, grid);
        for (int gy = y + 8; gy < y + h; gy += 8) g.fill(x, gy, x + w, gy + 1, grid);
    }

    public static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    public static void bolt(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 3, y + 3, GOLD_DIM);
        g.fill(x, y, x + 2, y + 2, GOLD);
        g.fill(x, y, x + 1, y + 1, GOLD_HI);
    }

    /** Small AE2-ish trace line with a node at the end. */
    public static void trace(GuiGraphicsExtractor g, int x0, int y, int x1) {
        g.fill(x0, y, x1, y + 1, GOLD_DIM);
        g.fill(x1 - 1, y - 1, x1 + 2, y + 2, GOLD);
    }

    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float frac, int color) {
        g.fill(x, y, x + w, y + h, STEEL_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF14120E);
        int fw = Math.round((w - 2) * Math.max(0, Math.min(1, frac)));
        if (fw > 0) {
            g.fill(x + 1, y + 1, x + 1 + fw, y + h - 1, color);
            g.fill(x + 1, y + 1, x + 1 + fw, y + 2, 0x40FFFFFF);
        }
    }

    public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean active, boolean hover) {
        g.fill(x, y, x + w, y + h, STEEL_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, active ? 0xFF4A3A14 : hover ? STEEL_LIGHT : STEEL);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, active ? GOLD : STEEL_HI);
        if (active) outline(g, x, y, w, h, GOLD);
    }
}
