package com.lach.overseer.client;

import com.lach.overseer.machine.MachineEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class AlertToast implements Toast {
    private final MachineEntry entry;
    private Visibility visibility = Visibility.SHOW;

    public AlertToast(MachineEntry entry) {
        this.entry = entry;
    }

    @Override
    public Visibility getWantedVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager manager, long ms) {
        visibility = ms >= 6000L ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, Font font, long ms) {
        int w = width(), h = height();
        g.fill(0, 0, w, h, Theme.STEEL_DARK);
        g.fill(1, 1, w - 1, h - 1, Theme.STEEL);
        g.fill(1, 1, w - 1, 2, Theme.STEEL_HI);
        g.fill(0, 0, 3, h, entry.status().color);
        Theme.outline(g, 0, 0, w, h, Theme.GOLD_DIM);
        Block b = BuiltInRegistries.BLOCK.getValue(entry.block());
        g.item(new ItemStack(b.asItem()), 8, 8);
        g.text(font, font.plainSubstrByWidth(b.getName().getString(), w - 34), 30, 7, Theme.AMBER, false);
        BlockPos p = entry.pos();
        Component st = Component.translatable("overseer.status." + entry.status().key);
        g.text(font, st, 30, 18, entry.status().color, false);
        g.text(font, p.getX() + " " + p.getY() + " " + p.getZ(), 34 + font.width(st), 18, Theme.AMBER_DIM, false);
    }
}
