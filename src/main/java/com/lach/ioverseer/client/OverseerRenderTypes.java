package com.lach.ioverseer.client;

import com.lach.ioverseer.IOverseer;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class OverseerRenderTypes {
    /** Lines with no depth test so the outline shows through walls. */
    public static final RenderPipeline XRAY_LINES_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(IOverseer.id("pipeline/xray_lines"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build();

    public static final RenderType XRAY_LINES = RenderType.create("ioverseer_xray_lines",
            RenderSetup.builder(XRAY_LINES_PIPELINE).setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING).createRenderSetup());

    private OverseerRenderTypes() {}
}
