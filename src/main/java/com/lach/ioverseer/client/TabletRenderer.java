package com.lach.ioverseer.client;

import com.lach.ioverseer.IOverseer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/** Tablet item: the flat model, plus a live power readout on its screen while held with the preview on. */
public class TabletRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ModelResourceLocation BASE = ModelResourceLocation.standalone(IOverseer.id("item/monitor_tablet_base"));

    public TabletRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getModelManager().getModel(BASE);
        VertexConsumer vc = ItemRenderer.getFoilBufferDirect(buffers, Sheets.translucentItemSheet(), true, stack.hasFoil());
        mc.getItemRenderer().renderModelLists(model, stack, light, overlay, pose, vc);
        if (ctx == ItemDisplayContext.GUI || ctx == ItemDisplayContext.GROUND || ctx == ItemDisplayContext.FIXED) return;
        if (!TabletPreview.enabled(stack)) return;
        // front (+z) face, then the back face mirrored so it reads the right way round
        face(pose, buffers, mc.font, PreviewLayout.SX / 16f, 8.5f / 16f, 1);
        face(pose, buffers, mc.font, (PreviewLayout.SX + PreviewLayout.W) / 16f, 7.5f / 16f, -1);
    }

    private static void face(PoseStack pose, MultiBufferSource buffers, Font font, float x, float z, int dir) {
        pose.pushPose();
        pose.translate(x, 1 - PreviewLayout.SY / 16f, z);
        pose.scale(dir / 16f, -1 / 16f, dir);
        Matrix4f m = pose.last().pose();
        // quads first, text after: asking the buffer source for the text type ends the quad buffer
        VertexConsumer quads = buffers.getBuffer(RenderType.textBackground());
        java.util.List<Runnable> texts = new java.util.ArrayList<>();
        PreviewLayout.paint(new PreviewLayout.Painter() {
            @Override
            public void quad(float x0, float y0, float x1, float y1, int argb, int layer) {
                float zz = 0.002f + layer * 0.0015f;
                quads.addVertex(m, x0, y0, zz).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
                quads.addVertex(m, x0, y1, zz).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
                quads.addVertex(m, x1, y1, zz).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
                quads.addVertex(m, x1, y0, zz).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
            }

            @Override
            public void text(String s, float tx, float ty, float scale, int argb) {
                Matrix4f tm = new Matrix4f(m).translate(tx, ty, 0.008f).scale(scale, scale, 1);
                texts.add(() -> font.drawInBatch(s, 0, 0, argb, false, tm, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT));
            }

            @Override
            public int width(String s) {
                return font.width(s);
            }
        });
        texts.forEach(Runnable::run);
        pose.popPose();
    }
}
