package com.lach.ioverseer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Live power readout on the held tablet's screen, drawn over the flat item model while the preview is on. */
public record TabletRenderer() implements SpecialModelRenderer<Boolean> {
    private static final int FULL = LightCoordsUtil.FULL_BRIGHT;

    private record Quad(float x0, float y0, float x1, float y1, int argb, int layer) {}

    private record Text(String s, float x, float y, float scale, int argb) {}

    @Override
    public @Nullable Boolean extractArgument(ItemStack stack) {
        return TabletPreview.enabled(stack) ? Boolean.TRUE : null;
    }

    @Override
    public void submit(@Nullable Boolean on, PoseStack pose, SubmitNodeCollector out, int light, int overlay, boolean foil, int outline) {
        if (on == null) return;
        Font font = Minecraft.getInstance().font;
        List<Quad> quads = new ArrayList<>();
        List<Text> texts = new ArrayList<>();
        PreviewLayout.paint(new PreviewLayout.Painter() {
            @Override
            public void quad(float x0, float y0, float x1, float y1, int argb, int layer) {
                quads.add(new Quad(x0, y0, x1, y1, argb, layer));
            }

            @Override
            public void text(String s, float x, float y, float scale, int argb) {
                texts.add(new Text(s, x, y, scale, argb));
            }

            @Override
            public int width(String s) {
                return font.width(s);
            }
        });
        // front (+z) face, then the back face mirrored so it reads the right way round
        face(pose, out, quads, texts, PreviewLayout.SX / 16f, 8.5f / 16f, 1);
        face(pose, out, quads, texts, (PreviewLayout.SX + PreviewLayout.W) / 16f, 7.5f / 16f, -1);
    }

    private static void face(PoseStack pose, SubmitNodeCollector out, List<Quad> quads, List<Text> texts, float x, float z, int dir) {
        pose.pushPose();
        pose.translate(x, 1 - PreviewLayout.SY / 16f, z);
        pose.scale(dir / 16f, -1 / 16f, dir);
        out.submitCustomGeometry(pose, RenderTypes.textBackground(), (p, vc) -> {
            Matrix4f m = p.pose();
            for (Quad q : quads) {
                float zz = 0.002f + q.layer * 0.0015f;
                vc.addVertex(m, q.x0, q.y0, zz).setColor(q.argb).setLight(FULL);
                vc.addVertex(m, q.x0, q.y1, zz).setColor(q.argb).setLight(FULL);
                vc.addVertex(m, q.x1, q.y1, zz).setColor(q.argb).setLight(FULL);
                vc.addVertex(m, q.x1, q.y0, zz).setColor(q.argb).setLight(FULL);
            }
        });
        for (Text t : texts) {
            pose.pushPose();
            pose.translate(t.x, t.y, 0.008f);
            pose.scale(t.scale, t.scale, 1);
            out.submitText(pose, 0, 0, FormattedCharSequence.forward(t.s, Style.EMPTY), false, Font.DisplayMode.POLYGON_OFFSET, FULL, t.argb, 0, 0);
            pose.popPose();
        }
        pose.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(0, 0, 0));
        output.accept(new Vector3f(1, 1, 1));
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<Boolean> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<Boolean> bake(BakingContext context) {
            return new TabletRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
