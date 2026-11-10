package com.lach.overseer.client;

import com.lach.overseer.block.MonitorScreenBlock;
import com.lach.overseer.block.MonitorScreenBlockEntity;
import com.lach.overseer.machine.MachineEntry;
import com.lach.overseer.machine.MachineStatus;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Draws a live status list on the front of the Monitor Screen. */
public class MonitorScreenRenderer implements BlockEntityRenderer<MonitorScreenBlockEntity> {
    private static final int PX = 100; // font pixels across the usable face
    private static final float SCALE = (10.5f / 16f) / PX;
    private final Font font;

    public MonitorScreenRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(MonitorScreenBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        Direction facing = be.getBlockState().getValue(MonitorScreenBlock.FACING);
        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        ps.translate(0, 0, 0.5005);
        ps.scale(SCALE, -SCALE, SCALE);
        Matrix4f m = ps.last().pose();
        int half = PX / 2;
        int x0 = -half, y = -half;

        List<MachineEntry> list = be.entries();
        text(m, buf, "I/O", x0, y, Theme.IO);
        text(m, buf, "VERSEER", x0 + font.width("I/O"), y, Theme.GOLD);
        Map<MachineStatus, Integer> counts = new EnumMap<>(MachineStatus.class);
        for (MachineEntry e : list) counts.merge(e.status(), 1, Integer::sum);
        String total = Integer.toString(list.size());
        text(m, buf, total, half - font.width(total), y, Theme.AMBER_DIM);
        y += 11;
        int cx = x0;
        for (MachineStatus s : new MachineStatus[]{MachineStatus.WORKING, MachineStatus.IDLE, MachineStatus.OUTPUT_FULL, MachineStatus.NO_POWER}) {
            String t = "■" + counts.getOrDefault(s, 0);
            text(m, buf, t, cx, y, s.color);
            cx += 25;
        }
        y += 12;
        int rows = 0;
        for (MachineEntry e : list) {
            if (rows >= 7) break;
            String name = BuiltInRegistries.BLOCK.get(e.block()).getName().getString();
            text(m, buf, "■", x0, y, e.status().color);
            text(m, buf, font.plainSubstrByWidth(name, PX - 9), x0 + 8, y, Theme.TEXT);
            y += 10;
            rows++;
        }
        if (list.isEmpty()) {
            text(m, buf, Component.translatable("overseer.gui.none").getString(), x0, y, Theme.AMBER_DIM);
        }
        ps.popPose();
    }

    private void text(Matrix4f m, MultiBufferSource buf, String s, float x, float y, int color) {
        font.drawInBatch(s, x, y, color, false, m, buf, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }
}
