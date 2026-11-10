package com.lach.overseer.client;

import com.lach.overseer.block.MonitorScreenBlock;
import com.lach.overseer.block.MonitorScreenBlockEntity;
import com.lach.overseer.machine.MachineEntry;
import com.lach.overseer.machine.MachineStatus;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Draws a live status list on the front of the Overseer Screen. */
public class MonitorScreenRenderer implements BlockEntityRenderer<MonitorScreenBlockEntity, MonitorScreenRenderer.State> {
    private static final int PX = 100; // font pixels across the usable face
    private static final float SCALE = (10.5f / 16f) / PX;

    record Line(String text, float x, float y, int color) {}

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        List<Line> lines = List.of();
    }

    public MonitorScreenRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MonitorScreenBlockEntity be, State state, float pt, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, pt, camera, crumbling);
        state.facing = be.getBlockState().getValue(MonitorScreenBlock.FACING);
        Font font = Minecraft.getInstance().font;
        List<Line> out = new ArrayList<>();
        int half = PX / 2;
        int x0 = -half, y = -half;
        List<MachineEntry> list = be.entries();
        out.add(new Line("I/O", x0, y, Theme.IO));
        out.add(new Line("VERSEER", x0 + font.width("I/O"), y, Theme.GOLD));
        String total = Integer.toString(list.size());
        out.add(new Line(total, half - font.width(total), y, Theme.AMBER_DIM));
        y += 11;
        Map<MachineStatus, Integer> counts = new EnumMap<>(MachineStatus.class);
        for (MachineEntry e : list) counts.merge(e.status(), 1, Integer::sum);
        int cx = x0;
        for (MachineStatus s : new MachineStatus[]{MachineStatus.WORKING, MachineStatus.IDLE, MachineStatus.OUTPUT_FULL, MachineStatus.NO_POWER}) {
            out.add(new Line("■" + counts.getOrDefault(s, 0), cx, y, s.color));
            cx += 25;
        }
        y += 12;
        int rows = 0;
        for (MachineEntry e : list) {
            if (rows++ >= 7) break;
            String name = BuiltInRegistries.BLOCK.getValue(e.block()).getName().getString();
            out.add(new Line("■", x0, y, e.status().color));
            out.add(new Line(font.plainSubstrByWidth(name, PX - 9), x0 + 8, y, Theme.TEXT));
            y += 10;
        }
        if (list.isEmpty()) out.add(new Line(Component.translatable("overseer.gui.none").getString(), x0, y, Theme.AMBER_DIM));
        state.lines = out;
    }

    @Override
    public void submit(State state, PoseStack ps, SubmitNodeCollector out, CameraRenderState camera) {
        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));
        ps.translate(0, 0, 0.5005);
        ps.scale(SCALE, -SCALE, SCALE);
        for (Line l : state.lines) {
            out.submitText(ps, l.x(), l.y(), FormattedCharSequence.forward(l.text(), Style.EMPTY), false,
                    Font.DisplayMode.POLYGON_OFFSET, LightCoordsUtil.FULL_BRIGHT, l.color(), 0, 0);
        }
        ps.popPose();
    }
}
