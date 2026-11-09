package com.lach.overseer.client;

import com.lach.overseer.machine.MachineEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class AlertToast implements Toast {
    private final MachineEntry entry;

    public AlertToast(MachineEntry entry) {
        this.entry = entry;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent tc, long time) {
        int w = width(), h = height();
        g.fill(0, 0, w, h, Theme.STEEL_DARK);
        g.fill(1, 1, w - 1, h - 1, Theme.STEEL);
        g.fill(1, 1, w - 1, 2, Theme.STEEL_HI);
        g.fill(0, 0, 3, h, entry.status().color);
        Theme.outline(g, 0, 0, w, h, Theme.GOLD_DIM);
        Block b = BuiltInRegistries.BLOCK.get(entry.block());
        g.renderItem(new ItemStack(b.asItem()), 8, 8);
        var font = tc.getMinecraft().font;
        Component title = Component.translatable("overseer.toast." + entry.status().key, b.getName());
        g.drawString(font, font.plainSubstrByWidth(title.getString(), w - 34), 30, 7, entry.status().color, false);
        BlockPos p = entry.pos();
        g.drawString(font, p.getX() + " " + p.getY() + " " + p.getZ(), 30, 18, Theme.AMBER_DIM, false);
        return time >= 6000L ? Visibility.HIDE : Visibility.SHOW;
    }
}
