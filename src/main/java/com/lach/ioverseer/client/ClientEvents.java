package com.lach.ioverseer.client;

import com.lach.ioverseer.IOverseer;
import com.lach.ioverseer.Registry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@EventBusSubscriber(modid = IOverseer.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    // ---------- mod bus ----------

    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(Registry.SCREEN_BE.get(), MonitorScreenRenderer::new);
    }

    @SubscribeEvent
    static void layers(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, IOverseer.id("highlights"), ClientEvents::hud);
    }

    @SubscribeEvent
    static void specialRenderers(net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent e) {
        e.register(IOverseer.id("tablet_preview"), TabletRenderer.Unbaked.MAP_CODEC);
    }

    @SubscribeEvent
    static void pipelines(RegisterRenderPipelinesEvent e) {
        e.registerPipeline(OverseerRenderTypes.XRAY_LINES_PIPELINE);
    }

    // ---------- game bus ----------

    @SubscribeEvent
    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            Highlights.clear();
            return;
        }
        for (Iterator<Highlights.Target> it = Highlights.TARGETS.values().iterator(); it.hasNext(); ) {
            Highlights.Target t = it.next();
            if (!t.pos().dimension().equals(mc.level.dimension())) continue;
            if (mc.player.position().distanceToSqr(t.pos().pos().getCenter()) < 3.5 * 3.5) {
                it.remove();
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4f));
            }
        }
    }

    @SubscribeEvent
    static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        Highlights.clear();
    }

    @SubscribeEvent
    static void submit(SubmitCustomGeometryEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || Highlights.TARGETS.isEmpty()) return;
        Vec3 cam = e.getLevelRenderState().cameraRenderState.pos;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float pulse = 0.65f + 0.35f * Mth.sin((mc.level.getGameTime() + pt) * 0.25f);
        List<BlockPos> targets = new ArrayList<>();
        for (Highlights.Target t : Highlights.TARGETS.values())
            if (t.pos().dimension().equals(mc.level.dimension())) targets.add(t.pos().pos());
        if (targets.isEmpty()) return;
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        e.getSubmitNodeCollector().submitCustomGeometry(ps, RenderTypes.debugQuads(), (pose, vc) -> {
            for (BlockPos p : targets) beam(vc, pose, p, pulse);
        });
        int a = (int) (255 * pulse);
        int color = (a << 24) | 0xFFC233;
        e.getSubmitNodeCollector().submitCustomGeometry(ps, OverseerRenderTypes.XRAY_LINES, (pose, vc) -> {
            for (BlockPos p : targets) lineBox(vc, pose, p, color);
        });
        ps.popPose();
    }

    private static void beam(VertexConsumer vc, PoseStack.Pose pose, BlockPos p, float pulse) {
        float x0 = p.getX() + 0.38f, x1 = p.getX() + 0.62f, z0 = p.getZ() + 0.38f, z1 = p.getZ() + 0.62f;
        float y0 = p.getY() + 1.02f, y1 = p.getY() + 96f;
        int a0 = (int) (170 * pulse);
        quad(vc, pose, x0, z0, x1, z0, y0, y1, a0);
        quad(vc, pose, x1, z0, x1, z1, y0, y1, a0);
        quad(vc, pose, x1, z1, x0, z1, y0, y1, a0);
        quad(vc, pose, x0, z1, x0, z0, y0, y1, a0);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float xa, float za, float xb, float zb, float y0, float y1, int a0) {
        vc.addVertex(pose, xa, y0, za).setColor(255, 178, 40, a0);
        vc.addVertex(pose, xb, y0, zb).setColor(255, 178, 40, a0);
        vc.addVertex(pose, xb, y1, zb).setColor(255, 178, 40, 0);
        vc.addVertex(pose, xa, y1, za).setColor(255, 178, 40, 0);
    }

    private static void lineBox(VertexConsumer vc, PoseStack.Pose pose, BlockPos p, int color) {
        float e = 0.02f;
        float x0 = p.getX() - e, y0 = p.getY() - e, z0 = p.getZ() - e;
        float x1 = p.getX() + 1 + e, y1 = p.getY() + 1 + e, z1 = p.getZ() + 1 + e;
        float[][] edges = {
                {x0, y0, z0, x1, y0, z0}, {x0, y1, z0, x1, y1, z0}, {x0, y0, z1, x1, y0, z1}, {x0, y1, z1, x1, y1, z1},
                {x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0}, {x0, y0, z1, x0, y1, z1}, {x1, y0, z1, x1, y1, z1},
                {x0, y0, z0, x0, y0, z1}, {x1, y0, z0, x1, y0, z1}, {x0, y1, z0, x0, y1, z1}, {x1, y1, z0, x1, y1, z1}};
        for (float[] ed : edges) {
            float nx = Math.signum(ed[3] - ed[0]), ny = Math.signum(ed[4] - ed[1]), nz = Math.signum(ed[5] - ed[2]);
            vc.addVertex(pose, ed[0], ed[1], ed[2]).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(3f);
            vc.addVertex(pose, ed[3], ed[4], ed[5]).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(3f);
        }
    }

    // ---------- HUD ----------

    /** Gold arrow pointing toward the target, 0 = straight ahead. */
    private static void arrow(GuiGraphicsExtractor g, int cx, int cy, float deg) {
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().rotate((float) Math.toRadians(deg));
        g.fill(-1, -1, 1, 6, Theme.GOLD);
        for (int i = 0; i < 4; i++) g.fill(-4 + i, -2 - i, 4 - i, -1 - i, i == 3 ? Theme.GOLD_HI : Theme.GOLD);
        g.pose().popMatrix();
    }

    private static void hud(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || Highlights.TARGETS.isEmpty() || mc.options.hideGui) return;
        Font font = mc.font;
        int cx = g.guiWidth() / 2;
        int y = 4;
        int shown = 0;
        Vec3 me = mc.player.position();
        for (Highlights.Target t : Highlights.TARGETS.values()) {
            if (shown >= 4) break;
            if (!t.pos().dimension().equals(mc.level.dimension())) continue;
            Vec3 c = t.pos().pos().getCenter();
            double dx = c.x - me.x, dz = c.z - me.z, dy = c.y - me.y;
            double target = Math.toDegrees(Math.atan2(-dx, dz));
            double rel = Mth.wrapDegrees(target - mc.player.getYRot());
            int dist = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
            String vert = dy > 2 ? " ▲" : dy < -2 ? " ▼" : "";
            Block b = BuiltInRegistries.BLOCK.getValue(t.block());
            Component name = b.getName();
            String text = name.getString() + "  " + dist + "m" + vert;
            int w = font.width(text) + 38;
            int x = cx - w / 2;
            g.fill(x, y, x + w, y + 18, 0xC0141210);
            g.fill(x, y, x + w, y + 1, Theme.GOLD);
            g.fill(x, y + 17, x + w, y + 18, Theme.GOLD_DIM);
            g.item(new ItemStack(b.asItem()), x + 3, y + 1);
            arrow(g, x + 27, y + 9, (float) rel);
            g.text(font, text, x + 35, y + 5, Theme.AMBER, false);
            y += 21;
            shown++;
        }
    }
}
