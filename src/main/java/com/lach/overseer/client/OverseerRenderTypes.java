package com.lach.overseer.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

import java.util.OptionalDouble;

public final class OverseerRenderTypes extends RenderType {
    /** Lines drawn on top of everything so the outline shows through walls. */
    public static final RenderType XRAY_LINES = create("overseer_xray_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES, 1024, false, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new LineStateShard(OptionalDouble.of(3.0)))
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .createCompositeState(false));

    /** Translucent beam quads, depth-tested but not depth-written. */
    public static final RenderType BEAM = create("overseer_beam", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1024, false, true,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private OverseerRenderTypes(String n, VertexFormat f, VertexFormat.Mode m, int s, boolean a, boolean b, Runnable x, Runnable y) {
        super(n, f, m, s, a, b, x, y);
    }
}
