package com.lach.ioverseer.client;

import com.lach.ioverseer.IOverseer;
import com.lach.ioverseer.Registry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Matrix4f;

import java.util.Iterator;

public final class ClientEvents {

    private ClientEvents() {}

    @EventBusSubscriber(modid = IOverseer.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Mod {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerBlockEntityRenderer(Registry.SCREEN_BE.get(), MonitorScreenRenderer::new);
        }

        @SubscribeEvent
        public static void models(ModelEvent.RegisterAdditional e) {
            e.register(TabletRenderer.BASE);
        }

        @SubscribeEvent
        public static void extensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent e) {
            e.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
                private TabletRenderer renderer;

                @Override
                public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (renderer == null) renderer = new TabletRenderer();
                    return renderer;
                }
            }, Registry.TABLET.get());
        }

        @SubscribeEvent
        public static void layers(RegisterGuiLayersEvent e) {
            e.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, IOverseer.id("highlights"), ClientEvents::hud);
        }
    }

    @EventBusSubscriber(modid = IOverseer.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static final class Game {
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
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
        public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
            Highlights.clear();
        }

        @SubscribeEvent
        public static void render(RenderLevelStageEvent e) {
            if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || Highlights.TARGETS.isEmpty()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            Vec3 cam = e.getCamera().getPosition();
            PoseStack ps = e.getPoseStack();
            MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
            float pulse = 0.65f + 0.35f * Mth.sin((mc.level.getGameTime() + e.getPartialTick().getGameTimeDeltaPartialTick(false)) * 0.25f);
            ps.pushPose();
            ps.translate(-cam.x, -cam.y, -cam.z);
            VertexConsumer beam = buf.getBuffer(OverseerRenderTypes.BEAM);
            for (Highlights.Target t : Highlights.TARGETS.values()) {
                if (!t.pos().dimension().equals(mc.level.dimension())) continue;
                BlockPos p = t.pos().pos();
                drawBeam(ps, beam, p, pulse);
            }
            buf.endBatch(OverseerRenderTypes.BEAM);
            VertexConsumer lines = buf.getBuffer(OverseerRenderTypes.XRAY_LINES);
            for (Highlights.Target t : Highlights.TARGETS.values()) {
                if (!t.pos().dimension().equals(mc.level.dimension())) continue;
                AABB box = new AABB(t.pos().pos()).inflate(0.02);
                LevelRenderer.renderLineBox(ps, lines, box, 1f, 0.76f, 0.2f, pulse);
            }
            buf.endBatch(OverseerRenderTypes.XRAY_LINES);
            ps.popPose();
        }
    }

    private static void drawBeam(PoseStack ps, VertexConsumer vc, BlockPos p, float pulse) {
        Matrix4f m = ps.last().pose();
        float x0 = p.getX() + 0.38f, x1 = p.getX() + 0.62f, z0 = p.getZ() + 0.38f, z1 = p.getZ() + 0.62f;
        float y0 = p.getY() + 1.02f, y1 = p.getY() + 96f;
        int a0 = (int) (170 * pulse), a1 = 0;
        int r = 255, g = 178, b = 40;
        // four faces, fading upward
        quad(vc, m, x0, z0, x1, z0, y0, y1, r, g, b, a0, a1);
        quad(vc, m, x1, z0, x1, z1, y0, y1, r, g, b, a0, a1);
        quad(vc, m, x1, z1, x0, z1, y0, y1, r, g, b, a0, a1);
        quad(vc, m, x0, z1, x0, z0, y0, y1, r, g, b, a0, a1);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float xa, float za, float xb, float zb, float y0, float y1, int r, int g, int b, int a0, int a1) {
        vc.addVertex(m, xa, y0, za).setColor(r, g, b, a0);
        vc.addVertex(m, xb, y0, zb).setColor(r, g, b, a0);
        vc.addVertex(m, xb, y1, zb).setColor(r, g, b, a1);
        vc.addVertex(m, xa, y1, za).setColor(r, g, b, a1);
    }

    /** Gold arrow pointing toward the target, 0 = straight ahead. */
    private static void arrow(GuiGraphics g, int cx, int cy, float deg) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(deg));
        g.fill(-1, -1, 1, 6, Theme.GOLD);
        for (int i = 0; i < 4; i++) g.fill(-4 + i, -2 - i, 4 - i, -1 - i, i == 3 ? Theme.GOLD_HI : Theme.GOLD);
        g.pose().popPose();
    }

    private static void hud(GuiGraphics g, net.minecraft.client.DeltaTracker dt) {
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
            Component name = BuiltInRegistries.BLOCK.get(t.block()).getName();
            String text = name.getString() + "  " + dist + "m" + vert;
            int w = font.width(text) + 38;
            int x = cx - w / 2;
            g.fill(x, y, x + w, y + 18, 0xC0141210);
            g.fill(x, y, x + w, y + 1, Theme.GOLD);
            g.fill(x, y + 17, x + w, y + 18, Theme.GOLD_DIM);
            g.renderItem(new ItemStack(BuiltInRegistries.BLOCK.get(t.block()).asItem()), x + 3, y + 1);
            arrow(g, x + 27, y + 9, (float) rel);
            g.drawString(font, text, x + 35, y + 5, Theme.AMBER, false);
            y += 21;
            shown++;
        }
    }
}
