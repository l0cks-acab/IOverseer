package com.lach.overseer.client;

import com.lach.overseer.machine.MachineEntry;
import com.lach.overseer.machine.MachineStatus;
import com.lach.overseer.net.Net;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.*;

public class OverseerScreen extends Screen {
    private static final int W = 340, H = 214;
    private static final int LX = 10, LY = 40, LW = 206, LH = 152;
    private static final int DX = 224, DY = 24, DW = 106, DH = 168;
    private static final int ROW = 18, HEAD = 11;

    /** null = all */
    private static final MachineStatus[] FILTERS = {null, MachineStatus.WORKING, MachineStatus.IDLE, MachineStatus.OUTPUT_FULL, MachineStatus.NO_POWER};
    private static int filter = 0;

    private final Optional<BlockPos> source;
    private List<MachineEntry> entries = List.of();
    private boolean received;
    private int radius;
    private final List<Row> rows = new ArrayList<>();
    private double scroll;
    private GlobalPos selected;
    private int left, top;

    private record Row(String header, MachineEntry entry) {}

    public OverseerScreen(Optional<BlockPos> source) {
        super(Theme.brand());
        this.source = source;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        ClientPacketDistributor.sendToServer(new Net.Watch(source));
    }

    @Override
    public void removed() {
        ClientPacketDistributor.sendToServer(new Net.Unwatch());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void accept(Net.Snapshot s) {
        entries = s.entries();
        radius = s.radius();
        received = true;
        rebuild();
    }

    private static GlobalPos key(MachineEntry e) {
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, e.dim()), e.pos());
    }

    private void rebuild() {
        rows.clear();
        MachineStatus f = FILTERS[filter];
        BlockPos me = minecraft.player.blockPosition();
        Map<String, List<MachineEntry>> byMod = new TreeMap<>();
        for (MachineEntry e : entries) {
            if (f != null && e.status() != f) continue;
            byMod.computeIfAbsent(modName(e.block().getNamespace()), k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<String, List<MachineEntry>> g : byMod.entrySet()) {
            rows.add(new Row(g.getKey(), null));
            g.getValue().sort(Comparator.comparingInt((MachineEntry e) -> e.status().ordinal()).thenComparingDouble(e -> e.pos().distSqr(me)));
            for (MachineEntry e : g.getValue()) rows.add(new Row(null, e));
        }
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private static String modName(String ns) {
        if (ns.equals("minecraft")) return "Minecraft";
        return ModList.get().getModContainerById(ns).map(c -> c.getModInfo().getDisplayName()).orElse(ns);
    }

    private int contentHeight() {
        int h = 0;
        for (Row r : rows) h += r.header != null ? HEAD : ROW;
        return h;
    }

    private double maxScroll() {
        return Math.max(0, contentHeight() - LH);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractBackground(g, mx, my, pt);
        Theme.panel(g, left, top, W, H);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);
        int x = left, y = top;
        // title
        g.text(font, title, x + 12, y + 9, Theme.GOLD, false);
        int tw = font.width(title);
        Theme.trace(g, x + 16 + tw, y + 13, x + DX - 8);
        Component src = source.isPresent() ? Component.translatable("overseer.gui.source_screen", radius)
                : Component.translatable("overseer.gui.source_tablet", radius);
        g.text(font, src, x + DX, y + 9, Theme.AMBER_DIM, false);

        // filter tabs
        int tabW = LW / FILTERS.length;
        for (int i = 0; i < FILTERS.length; i++) {
            int bx = x + LX + i * tabW, by = y + 24;
            boolean hover = mx >= bx && mx < bx + tabW - 2 && my >= by && my < by + 13;
            Theme.button(g, bx, by, tabW - 2, 13, filter == i, hover);
            MachineStatus s = FILTERS[i];
            int count = s == null ? entries.size() : (int) entries.stream().filter(e -> e.status() == s).count();
            String label = (s == null ? Component.translatable("overseer.filter.all").getString() : Component.translatable("overseer.filter." + s.key).getString()) + " " + count;
            int color = s == null ? Theme.TEXT : s.color;
            g.pose().pushMatrix();
            float sc = Math.min(1f, (tabW - 6) / (float) font.width(label));
            g.pose().translate(bx + (tabW - 2) / 2f, by + 3.5f);
            g.pose().scale(sc, sc);
            g.text(font, label, -font.width(label) / 2, 0, color, false);
            g.pose().popMatrix();
        }

        // list
        Theme.screen(g, x + LX, y + LY, LW, LH);
        g.enableScissor(x + LX, y + LY, x + LX + LW, y + LY + LH);
        if (!received) {
            g.centeredText(font, Component.translatable("overseer.gui.scanning"), x + LX + LW / 2, y + LY + LH / 2 - 4, Theme.AMBER_DIM);
        } else if (rows.isEmpty()) {
            g.centeredText(font, Component.translatable("overseer.gui.none"), x + LX + LW / 2, y + LY + LH / 2 - 4, Theme.AMBER_DIM);
        }
        int ry = y + LY - (int) scroll;
        Row hoverRow = null;
        BlockPos me = minecraft.player.blockPosition();
        for (Row r : rows) {
            int rh = r.header != null ? HEAD : ROW;
            if (ry + rh >= y + LY && ry <= y + LY + LH) {
                if (r.header != null) {
                    g.fill(x + LX, ry, x + LX + LW, ry + HEAD, 0x40E8B23A);
                    g.text(font, r.header.toUpperCase(Locale.ROOT), x + LX + 4, ry + 2, Theme.GOLD, false);
                } else {
                    MachineEntry e = r.entry;
                    boolean sel = key(e).equals(selected);
                    boolean hov = mx >= x + LX && mx < x + LX + LW && my >= Math.max(ry, y + LY) && my < Math.min(ry + ROW, y + LY + LH);
                    if (hov) hoverRow = r;
                    if (sel) g.fill(x + LX, ry, x + LX + LW, ry + ROW, 0x50E8B23A);
                    else if (hov) g.fill(x + LX, ry, x + LX + LW, ry + ROW, 0x22FFFFFF);
                    if (Highlights.has(key(e))) g.fill(x + LX, ry, x + LX + 2, ry + ROW, Theme.GOLD_HI);
                    Block b = BuiltInRegistries.BLOCK.getValue(e.block());
                    ItemStack icon = new ItemStack(b.asItem());
                    g.item(icon, x + LX + 4, ry + 1);
                    String name = b.getName().getString();
                    if (e.pinned()) name = "★ " + name;
                    int nameW = LW - 24 - 66;
                    g.text(font, font.plainSubstrByWidth(name, nameW), x + LX + 23, ry + 2, Theme.TEXT, false);
                    String where = sameDim(e) ? (int) Math.sqrt(e.pos().distSqr(me)) + "m" : e.dim().getPath();
                    g.text(font, where, x + LX + 23, ry + 10, Theme.AMBER_DIM, false);
                    // status pill
                    int px = x + LX + LW - 64;
                    g.fill(px, ry + 4, px + 6, ry + 10, e.status().color);
                    g.text(font, font.plainSubstrByWidth(Component.translatable("overseer.status." + e.status().key).getString(), 56), px + 9, ry + 3, e.status().color, false);
                }
            }
            ry += rh;
        }
        g.disableScissor();
        // scrollbar
        double max = maxScroll();
        if (max > 0) {
            int sbH = Math.max(12, (int) (LH * (LH / (double) contentHeight())));
            int sbY = y + LY + (int) ((LH - sbH) * (scroll / max));
            g.fill(x + LX + LW - 3, sbY, x + LX + LW - 1, sbY + sbH, Theme.GOLD_DIM);
        }

        renderDetails(g, mx, my);

        // footer
        g.text(font, Component.translatable("overseer.gui.hint"), x + 12, y + H - 15, Theme.AMBER_DIM, false);
        if (source.isEmpty()) {
            boolean on = TabletPreview.enabled(minecraft.player);
            drawBtn(g, x + DX + 5, y + H - 18, DW - 10, Component.translatable(on ? "overseer.gui.preview_on" : "overseer.gui.preview_off"), on, mx, my);
        }

        if (hoverRow != null && minecraft.hasControlDown()) {
            g.setTooltipForNextFrame(font, Component.translatable(Highlights.has(key(hoverRow.entry)) ? "overseer.gui.unhighlight" : "overseer.gui.highlight"), mx, my);
        }
    }

    private boolean sameDim(MachineEntry e) {
        return minecraft.level != null && minecraft.level.dimension().identifier().equals(e.dim());
    }

    private MachineEntry selectedEntry() {
        if (selected == null) return null;
        for (MachineEntry e : entries) if (key(e).equals(selected)) return e;
        return null;
    }

    private void renderDetails(GuiGraphicsExtractor g, int mx, int my) {
        int x = left + DX, y = top + DY;
        Theme.screen(g, x, y, DW, DH);
        MachineEntry e = selectedEntry();
        if (e == null) {
            List<FormattedCharSequence> l = font.split(Component.translatable("overseer.gui.pick"), DW - 10);
            int yy = y + DH / 2 - l.size() * 5;
            for (FormattedCharSequence s : l) { g.centeredText(font, s, x + DW / 2, yy, Theme.AMBER_DIM); yy += 10; }
            return;
        }
        Block b = BuiltInRegistries.BLOCK.getValue(e.block());
        g.pose().pushMatrix();
        g.pose().translate(x + DW / 2f - 12, y + 5);
        g.pose().scale(1.5f, 1.5f);
        g.item(new ItemStack(b.asItem()), 0, 0);
        g.pose().popMatrix();
        int yy = y + 32;
        for (FormattedCharSequence s : font.split(b.getName(), DW - 8)) {
            g.centeredText(font, s, x + DW / 2, yy, Theme.TEXT);
            yy += 10;
        }
        g.centeredText(font, modName(e.block().getNamespace()), x + DW / 2, yy, Theme.GOLD_DIM);
        yy += 13;
        g.fill(x + 5, yy, x + 11, yy + 6, e.status().color);
        g.text(font, Component.translatable("overseer.status." + e.status().key), x + 14, yy - 1, e.status().color, false);
        yy += 11;
        BlockPos p = e.pos();
        g.text(font, p.getX() + " " + p.getY() + " " + p.getZ(), x + 5, yy, Theme.AMBER, false);
        yy += 10;
        if (!sameDim(e)) { g.text(font, e.dim().getPath(), x + 5, yy, Theme.AMBER_DIM, false); yy += 10; }
        if (e.hasEnergy()) {
            g.text(font, fmt(e.energy()) + " / " + fmt(e.energyMax()) + " FE", x + 5, yy, Theme.TEXT, false);
            Theme.bar(g, x + 5, yy + 9, DW - 10, 5, e.energy() / (float) e.energyMax(), 0xFFE5484D);
            yy += 17;
        }
        if (e.hasFluid()) {
            g.text(font, fmt(e.fluid()) + " / " + fmt(e.fluidMax()) + " mB", x + 5, yy, Theme.TEXT, false);
            Theme.bar(g, x + 5, yy + 9, DW - 10, 5, e.fluid() / (float) e.fluidMax(), 0xFF3E8FE0);
            yy += 17;
        }
        int gaugeBottom = y + DH - 34;
        for (com.lach.overseer.machine.Gauge ga : e.gauges()) {
            if (yy + 15 > gaugeBottom) break;
            String amt = compact(ga.amount()) + "/" + compact(ga.max());
            int aw = font.width(amt);
            String label = font.plainSubstrByWidth(Component.translatable(ga.label()).getString(), DW - 14 - aw);
            g.text(font, label, x + 5, yy, Theme.TEXT, false);
            g.text(font, amt, x + DW - 5 - aw, yy, Theme.AMBER_DIM, false);
            Theme.bar(g, x + 5, yy + 9, DW - 10, 5, ga.amount() / (float) ga.max(), ga.color());
            yy += 17;
        }
        if (e.hasItems() && yy + 9 <= gaugeBottom) {
            g.text(font, Component.translatable("overseer.gui.slots", e.slotsUsed(), e.slots()), x + 5, yy, Theme.TEXT, false);
        }
        // buttons
        int by = y + DH - 32;
        boolean hl = Highlights.has(key(e));
        drawBtn(g, x + 5, by, DW - 10, Component.translatable(hl ? "overseer.gui.unhighlight" : "overseer.gui.highlight"), hl, mx, my);
        drawBtn(g, x + 5, by + 15, DW - 10, Component.translatable(e.pinned() ? "overseer.gui.unpin" : "overseer.gui.pin"), e.pinned(), mx, my);
    }

    private void drawBtn(GuiGraphicsExtractor g, int x, int y, int w, Component label, boolean active, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 13;
        Theme.button(g, x, y, w, 13, active, hover);
        g.centeredText(font, label, x + w / 2, y + 3, active ? Theme.GOLD_HI : Theme.TEXT);
    }

    /** Short number for tight spots: 1200 -> 1.2k, 10000 -> 10k. */
    private static String compact(long v) {
        String[] u = {"", "k", "M", "G", "T"};
        double d = v;
        int i = 0;
        while (d >= 1000 && i < u.length - 1) { d /= 1000; i++; }
        if (i == 0) return Long.toString(v);
        String s = d >= 10 ? Long.toString(Math.round(d)) : String.format(Locale.ROOT, "%.1f", d);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s + u[i];
    }

    private static String fmt(long v) {
        if (v >= 1_000_000_000) return String.format(Locale.ROOT, "%.1fG", v / 1e9);
        if (v >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", v / 1e6);
        if (v >= 10_000) return String.format(Locale.ROOT, "%.1fk", v / 1e3);
        return Long.toString(v);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean dbl) {
        double mx = ev.x(), my = ev.y();
        if (ev.button() != 0) return super.mouseClicked(ev, dbl);
        int x = left, y = top;
        int tabW = LW / FILTERS.length;
        for (int i = 0; i < FILTERS.length; i++) {
            int bx = x + LX + i * tabW, by = y + 24;
            if (mx >= bx && mx < bx + tabW - 2 && my >= by && my < by + 13) {
                filter = i;
                click();
                rebuild();
                return true;
            }
        }
        if (mx >= x + LX && mx < x + LX + LW && my >= y + LY && my < y + LY + LH) {
            int ry = y + LY - (int) scroll;
            for (Row r : rows) {
                int rh = r.header != null ? HEAD : ROW;
                if (r.entry != null && my >= ry && my < ry + rh) {
                    selected = key(r.entry);
                    if (ev.hasControlDown()) {
                        boolean on = Highlights.toggle(r.entry);
                        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(on ? SoundEvents.NOTE_BLOCK_CHIME.value() : SoundEvents.NOTE_BLOCK_BASS.value(), on ? 1.5f : 0.8f));
                    } else click();
                    return true;
                }
                ry += rh;
            }
        }
        if (source.isEmpty() && mx >= x + DX + 5 && mx < x + DX + DW - 5 && my >= y + H - 18 && my < y + H - 5) {
            TabletPreview.toggle(minecraft.player);
            click();
            return true;
        }
        MachineEntry e = selectedEntry();
        if (e != null) {
            int bx = x + DX + 5, by = y + DY + DH - 32, bw = DW - 10;
            if (mx >= bx && mx < bx + bw && my >= by && my < by + 13) {
                Highlights.toggle(e);
                click();
                return true;
            }
            if (mx >= bx && mx < bx + bw && my >= by + 15 && my < by + 28) {
                ClientPacketDistributor.sendToServer(new Net.TogglePin(key(e)));
                click();
                return true;
            }
        }
        return super.mouseClicked(ev, dbl);
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Mth.clamp(scroll - sy * ROW, 0, maxScroll());
        return true;
    }
}
